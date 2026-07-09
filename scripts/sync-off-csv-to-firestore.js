const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const https = require("https");
const zlib = require("zlib");
const readline = require("readline");

const COLLECTION_NAME = "product_search_index";
const SYNC_STATE_COLLECTION = "sync_state";
const SYNC_STATE_DOC = "open_food_facts_country_rotation";

const CSV_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz";

// Priority rotation. Many products sold in Singapore supermarkets / convenience
// stores are tagged under a neighbouring or major-import country rather than
// (or in addition to) en:singapore, so the index is built country by country.
const COUNTRY_ROTATION = [
  "en:singapore",
  "en:malaysia",
  "en:indonesia",
  "en:thailand",
  "en:japan",
  "en:south-korea",
  "en:china",
  "en:taiwan",
  "en:hong-kong",
  "en:australia",
  "en:new-zealand",
  "en:united-states",
  "en:united-kingdom",
  "en:india",
  "en:france",
  "en:germany",
  "en:italy",
];

// Per-run limits. A run stops once it has written this many matching products,
// scanned this many CSV rows, or finished a full CSV pass for the current
// country — whichever comes first.
const DEFAULT_MAX_WRITES = 500;
const MAX_ROWS_TO_SCAN_PER_RUN = 250000;
const BATCH_SIZE = 450;
const HEARTBEAT_ROW_INTERVAL = 25000;
const CURSOR_SKIP_LOG_INTERVAL = 250000;

// The Open Food Facts CSV has ~200 columns; only these are read. Positions
// come from the header row, so upstream column reordering is harmless.
const REQUIRED_COLUMNS = ["code", "product_name", "countries_tags"];
const OPTIONAL_COLUMNS = ["brands", "image_front_url", "categories_tags", "last_modified_t"];

function requireEnv(name) {
  const value = process.env[name];
  if (!value || value.trim().length === 0) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

// Missing → default 500. Set higher than 500 → capped to 500 for hourly safety.
function getMaxWrites() {
  const raw = process.env.MAX_FIRESTORE_WRITES_PER_RUN;
  const parsed = Number.parseInt(raw || String(DEFAULT_MAX_WRITES), 10);

  if (!Number.isFinite(parsed) || parsed <= 0) {
    return DEFAULT_MAX_WRITES;
  }

  return Math.min(parsed, DEFAULT_MAX_WRITES);
}

function normalizeCountryIndex(rawIndex) {
  const length = COUNTRY_ROTATION.length;
  const parsed = Number(rawIndex);

  if (!Number.isFinite(parsed)) {
    return 0;
  }

  const floored = Math.trunc(parsed);
  // Wrap into range so an index that ran past the end loops back to 0.
  return ((floored % length) + length) % length;
}

function openUrlWithRedirects(url, userAgent, redirectCount = 0) {
  const maxRedirects = 5;

  return new Promise((resolve, reject) => {
    const request = https.get(
      url,
      {
        headers: {
          "User-Agent": userAgent,
          Accept: "text/csv, text/plain, */*",
        },
      },
      (response) => {
        const statusCode = response.statusCode || 0;

        if ([301, 302, 303, 307, 308].includes(statusCode)) {
          const location = response.headers.location;

          if (!location) {
            reject(new Error(`CSV download redirected with HTTP ${statusCode} but no Location header`));
            return;
          }

          if (redirectCount >= maxRedirects) {
            reject(new Error(`CSV download exceeded ${maxRedirects} redirects`));
            return;
          }

          const redirectedUrl = new URL(location, url).toString();
          console.log(`CSV download redirected: ${statusCode} → ${redirectedUrl}`);

          response.resume();

          openUrlWithRedirects(redirectedUrl, userAgent, redirectCount + 1)
            .then(resolve)
            .catch(reject);

          return;
        }

        if (statusCode !== 200) {
          reject(new Error(`CSV download failed with HTTP ${statusCode}`));
          return;
        }

        resolve(response);
      }
    );

    request.on("error", reject);
  });
}

function initializeFirebase() {
  const serviceAccountJson = requireEnv("FIREBASE_SERVICE_ACCOUNT");
  const serviceAccount = JSON.parse(serviceAccountJson);

  initializeApp({
    credential: cert(serviceAccount),
  });

  return getFirestore();
}

function normalizeText(value) {
  return (value || "")
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function buildPrefixes(name, brand) {
  const text = normalizeText(`${name || ""} ${brand || ""}`);
  const words = text.split(" ").filter(Boolean);
  const prefixes = new Set();

  for (const word of words) {
    if (word.length < 3) continue;

    const maxLength = Math.min(word.length, 24);
    for (let i = 3; i <= maxLength; i++) {
      prefixes.add(word.slice(0, i));
    }
  }

  // Keep the array small so documents stay light and the whereArrayContains
  // index does not blow up.
  return Array.from(prefixes).slice(0, 100);
}

function splitTags(value) {
  if (!value) return [];

  return String(value)
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function rowMatchesCountry(row, countryTag) {
  return splitTags(row.countries_tags).includes(countryTag);
}

function compactProduct(row, matchedCountryTag) {
  const barcode = String(row.code || "").trim();
  const name = String(row.product_name || "").trim();
  const brand = row.brands ? String(row.brands).trim() : null;

  if (!barcode || !name) {
    return null;
  }

  const countryTags = splitTags(row.countries_tags);
  const categoriesTags = splitTags(row.categories_tags);

  return {
    barcode,
    name,
    brand,
    imageUrl: row.image_front_url || null,
    countryTags,
    categoriesTags,
    searchName: normalizeText(`${name} ${brand || ""}`),
    searchPrefixes: buildPrefixes(name, brand),
    source: "openfoodfacts_csv",
    matchedCountryTag,
    lastModifiedFromApi: row.last_modified_t ? Number(row.last_modified_t) : null,
    updatedAt: Date.now(),
  };
}

// The OFF export is tab-separated with no quoting, so lines are split on
// "\t" directly. csv-parser was dropped because its quote handling treated a
// stray '"' in a field as the start of a quoted section and buffered input
// until the next '"', which grew the heap without bound on this file.
function buildColumnIndexes(headerLine) {
  const bomFreeHeaderLine =
    headerLine.charCodeAt(0) === 0xfeff ? headerLine.slice(1) : headerLine;

  const headers = bomFreeHeaderLine
    .split("\t")
    .map((header) => header.trim());

  const indexes = new Map();
  headers.forEach((header, index) => {
    if (!indexes.has(header)) {
      indexes.set(header, index);
    }
  });

  const missingRequired = REQUIRED_COLUMNS.filter((column) => !indexes.has(column));
  if (missingRequired.length > 0) {
    const error = new Error(`CSV header is missing required columns: ${missingRequired.join(", ")}`);
    error.isFatalHeaderError = true;
    throw error;
  }

  const missingOptional = OPTIONAL_COLUMNS.filter((column) => !indexes.has(column));
  if (missingOptional.length > 0) {
    console.log(`Warning: CSV header is missing optional columns: ${missingOptional.join(", ")}`);
  }

  return indexes;
}

function parseRow(line, columnIndexes) {
  const fields = line.split("\t");

  const field = (column) => {
    const index = columnIndexes.get(column);
    return index === undefined ? undefined : fields[index];
  };

  return {
    code: field("code"),
    product_name: field("product_name"),
    brands: field("brands"),
    image_front_url: field("image_front_url"),
    countries_tags: field("countries_tags"),
    categories_tags: field("categories_tags"),
    last_modified_t: field("last_modified_t"),
  };
}

async function getRotationState(db) {
  const ref = db.collection(SYNC_STATE_COLLECTION).doc(SYNC_STATE_DOC);
  const snap = await ref.get();
  const data = snap.exists ? snap.data() || {} : {};

  const countryProgress =
    data.countryProgress && typeof data.countryProgress === "object"
      ? data.countryProgress
      : {};

  return {
    currentCountryIndex: normalizeCountryIndex(data.currentCountryIndex || 0),
    totalSavedAllCountries: Number(data.totalSavedAllCountries || 0),
    countryProgress,
  };
}

// Writes the rotation state with a deep merge so per-country progress for other
// countries is preserved. `progressTag` may differ from `currentCountryTag`:
// when a country finishes its full pass we record that country's progress while
// the top-level pointer already advances to the next country.
async function saveRotationState(db, fields) {
  const {
    currentCountryIndex,
    currentCountryTag,
    currentCountryLastProcessedRow,
    totalSavedAllCountries,
    progressTag,
    progressLastProcessedRow,
    progressTotalSaved,
    progressCompletedFullPass,
    lastResult,
    lastWritten,
    lastScannedRows,
    lastMatchedRows,
    lastSkipped,
    completedCountryThisRun,
    lastError,
  } = fields;

  const payload = {
    currentCountryIndex,
    currentCountryTag,
    currentCountryLastProcessedRow,
    totalSavedAllCountries,
    countryProgress: {
      [progressTag]: {
        lastProcessedRow: progressLastProcessedRow,
        totalSaved: progressTotalSaved,
        completedFullPass: progressCompletedFullPass,
      },
    },
    lastRunAt: Date.now(),
    lastResult,
    lastWritten,
    lastScannedRows,
    lastMatchedRows,
    lastSkipped,
    completedCountryThisRun,
    sourceUrl: CSV_URL,
  };

  if (lastError) {
    payload.lastError = lastError;
  }

  const ref = db.collection(SYNC_STATE_COLLECTION).doc(SYNC_STATE_DOC);
  await ref.set(payload, { merge: true });
}

async function runCsvSync() {
  const db = initializeFirebase();
  const userAgent = requireEnv("OPEN_FOOD_FACTS_USER_AGENT");
  const maxWrites = getMaxWrites();

  const rotationState = await getRotationState(db);
  const currentCountryIndex = rotationState.currentCountryIndex;
  const currentCountryTag = COUNTRY_ROTATION[currentCountryIndex];

  const currentProgress = rotationState.countryProgress[currentCountryTag] || {};
  const startAfterRow = Number(currentProgress.lastProcessedRow || 0);
  const countrySavedBefore = Number(currentProgress.totalSaved || 0);
  const totalSavedAllBefore = Number(rotationState.totalSavedAllCountries || 0);

  console.log("Starting Open Food Facts CSV → Firestore country-rotation sync");
  console.log(`CSV URL: ${CSV_URL}`);
  console.log(`Collection: ${COLLECTION_NAME}`);
  console.log(`Current country index: ${currentCountryIndex} of ${COUNTRY_ROTATION.length}`);
  console.log(`Current country tag: ${currentCountryTag}`);
  console.log(`Start after row: ${startAfterRow}`);
  console.log(`Max writes this run: ${maxWrites}`);
  console.log(`Max rows to scan this run: ${MAX_ROWS_TO_SCAN_PER_RUN}`);

  let rowNumber = 0;
  let scannedThisRun = 0;
  let matched = 0;
  let written = 0;
  let skipped = 0;
  let batchesCommitted = 0;
  let stoppedBecauseWriteLimit = false;
  let stoppedBecauseRowLimit = false;
  let streamError = null;

  let batch = db.batch();
  let batchCount = 0;

  // Swaps in a fresh batch before awaiting the commit, so no code path can
  // ever call set() on — or re-commit — a batch whose commit has started.
  async function commitCurrentBatch(label) {
    const committingBatch = batch;
    const committingCount = batchCount;

    batch = db.batch();
    batchCount = 0;

    if (committingCount === 0) {
      return;
    }

    try {
      await committingBatch.commit();
    } catch (error) {
      error.isPersistenceFailure = true;
      throw error;
    }

    batchesCommitted++;
    console.log(
      `Committed ${label} batch #${batchesCommitted}: country=${currentCountryTag}, writes=${committingCount}, row=${rowNumber}, scanned=${scannedThisRun}, matched=${matched}, written=${written}`
    );

    // Persist the cursor after every successful commit so a crash later in the
    // run resumes here instead of at the previous run's cursor.
    try {
      await saveRotationState(db, {
        currentCountryIndex,
        currentCountryTag,
        currentCountryLastProcessedRow: rowNumber,
        totalSavedAllCountries: totalSavedAllBefore + written,
        progressTag: currentCountryTag,
        progressLastProcessedRow: rowNumber,
        progressTotalSaved: countrySavedBefore + written,
        progressCompletedFullPass: false,
        lastResult: "partial_progress",
        lastWritten: written,
        lastScannedRows: scannedThisRun,
        lastMatchedRows: matched,
        lastSkipped: skipped,
        completedCountryThisRun: false,
      });
    } catch (error) {
      error.isPersistenceFailure = true;
      throw error;
    }

    console.log(`Saved cursor after batch #${batchesCommitted}: lastProcessedRow=${rowNumber}`);
  }

  const response = await openUrlWithRedirects(CSV_URL, userAgent);
  const gunzip = zlib.createGunzip();

  const lines = readline.createInterface({
    input: response.pipe(gunzip),
    crlfDelay: Infinity,
  });

  // .pipe() does not forward errors: without this wiring a download or gzip
  // failure would leave the line reader waiting forever. Recording the error
  // and closing the reader lets the run finish cleanly as a partial pass.
  let inputError = null;
  let closingStreams = false;
  const failInput = (error) => {
    if (closingStreams) return;
    if (!inputError) inputError = error;
    lines.close();
  };
  response.on("error", failInput);
  gunzip.on("error", failInput);

  let columnIndexes = null;

  try {
    // Lines are processed strictly one at a time: `for await` does not pull
    // the next line until this iteration (including commits) finishes, and
    // readline pauses the download while the loop is behind.
    for await (const line of lines) {
      if (columnIndexes === null) {
        columnIndexes = buildColumnIndexes(line);
        continue;
      }

      rowNumber++;

      if (rowNumber <= startAfterRow) {
        if (rowNumber % CURSOR_SKIP_LOG_INTERVAL === 0) {
          console.log(`Skipping to cursor: row=${rowNumber} of ${startAfterRow}`);
        }
        continue;
      }

      scannedThisRun++;

      if (scannedThisRun % HEARTBEAT_ROW_INTERVAL === 0) {
        console.log(
          `Still scanning: country=${currentCountryTag}, row=${rowNumber}, scanned=${scannedThisRun}, matched=${matched}, written=${written}, skipped=${skipped}`
        );
      }

      // Cheap substring pre-filter; rowMatchesCountry on the parsed
      // countries_tags column stays the authoritative check.
      if (line.includes(currentCountryTag)) {
        const row = parseRow(line, columnIndexes);

        if (rowMatchesCountry(row, currentCountryTag)) {
          matched++;

          const compact = compactProduct(row, currentCountryTag);

          if (!compact) {
            skipped++;
          } else {
            const docRef = db.collection(COLLECTION_NAME).doc(compact.barcode);
            batch.set(docRef, compact, { merge: true });
            batchCount++;
            written++;

            if (batchCount >= BATCH_SIZE) {
              await commitCurrentBatch("progress");
            }
          }
        }
      }

      if (written >= maxWrites) {
        stoppedBecauseWriteLimit = true;
        console.log(`Stopping: reached max Firestore writes for this run (${maxWrites}) at row=${rowNumber}.`);
        break;
      }

      if (scannedThisRun >= MAX_ROWS_TO_SCAN_PER_RUN) {
        stoppedBecauseRowLimit = true;
        console.log(`Stopping: reached max rows to scan for this run (${MAX_ROWS_TO_SCAN_PER_RUN}) at row=${rowNumber}.`);
        break;
      }
    }
  } catch (error) {
    if (error && (error.isPersistenceFailure || error.isFatalHeaderError)) {
      // A failed commit or cursor save means progress was not durably
      // recorded; fail the run so those rows are re-scanned next time. A bad
      // header must also fail loudly instead of ending as a "clean" pass.
      throw error;
    }

    streamError = error;
  } finally {
    // Breaking out of `for await` leaves the download running; close the
    // reader and abort both stream stages. No-ops if the stream ended.
    closingStreams = true;
    lines.close();
    response.destroy();
    gunzip.destroy();
  }

  if (!streamError && inputError) {
    streamError = inputError;
  }

  await commitCurrentBatch("final");

  // A full pass only happens when the stream drained on its own without hitting
  // either per-run limit and without a stream error.
  const completedCountryThisRun =
    !stoppedBecauseWriteLimit && !stoppedBecauseRowLimit && streamError === null;

  // Never move a country's cursor backward: an error during the skip-to-cursor
  // phase would otherwise rewind progress recorded by earlier runs.
  const partialNextRow = Math.max(rowNumber, startAfterRow);

  let savedCountryRow;
  let nextCountryIndex;

  if (completedCountryThisRun) {
    // Country finished: reset its cursor, mark the pass complete, and advance
    // the top-level pointer to the next country (looping back at the end).
    savedCountryRow = 0;
    nextCountryIndex = (currentCountryIndex + 1) % COUNTRY_ROTATION.length;
  } else {
    savedCountryRow = partialNextRow;
    nextCountryIndex = currentCountryIndex;
  }

  const nextCountryTag = COUNTRY_ROTATION[nextCountryIndex];
  const nextCountryStartRow = completedCountryThisRun
    ? Number((rotationState.countryProgress[nextCountryTag] || {}).lastProcessedRow || 0)
    : savedCountryRow;

  await saveRotationState(db, {
    currentCountryIndex: nextCountryIndex,
    currentCountryTag: nextCountryTag,
    currentCountryLastProcessedRow: nextCountryStartRow,
    totalSavedAllCountries: totalSavedAllBefore + written,
    progressTag: currentCountryTag,
    progressLastProcessedRow: savedCountryRow,
    progressTotalSaved: countrySavedBefore + written,
    progressCompletedFullPass: completedCountryThisRun,
    lastResult: streamError ? "stopped_or_error" : "success",
    lastWritten: written,
    lastScannedRows: scannedThisRun,
    lastMatchedRows: matched,
    lastSkipped: skipped,
    completedCountryThisRun,
    lastError: streamError ? streamError.message : undefined,
  });

  if (streamError) {
    console.log(`CSV stream stopped: ${streamError.message}`);
  }

  console.log("CSV sync complete.");
  console.log(`Country: ${currentCountryTag}`);
  console.log(`Last processed row saved: ${savedCountryRow}`);
  console.log(`Scanned rows this run: ${scannedThisRun}`);
  console.log(`Matched rows: ${matched}`);
  console.log(`Written: ${written}`);
  console.log(`Skipped: ${skipped}`);
  console.log(`Batches committed: ${batchesCommitted}`);
  console.log(`Completed country full pass: ${completedCountryThisRun}`);
  console.log(`Next country: ${nextCountryTag}`);
}

module.exports = {
  COUNTRY_ROTATION,
  normalizeCountryIndex,
  openUrlWithRedirects,
  normalizeText,
  buildPrefixes,
  splitTags,
  rowMatchesCountry,
  compactProduct,
  buildColumnIndexes,
  parseRow,
};

if (require.main === module) {
  runCsvSync().catch((error) => {
    console.error("CSV sync failed:", error);
    process.exit(1);
  });
}
