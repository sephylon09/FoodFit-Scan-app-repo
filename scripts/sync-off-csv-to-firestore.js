const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const https = require("https");
const zlib = require("zlib");
const readline = require("readline");

const COLLECTION_NAME = "product_search_index";
const SYNC_STATE_COLLECTION = "sync_state";
const SYNC_STATE_DOC = "open_food_facts_csv";

const CSV_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz";

const COUNTRY_TAG = "en:singapore";
const DEFAULT_MAX_WRITES = 18000;

// Keep each GitHub Actions run short and memory-safe: stop cleanly after this
// many scanned rows and let the saved cursor resume on the next run.
const MAX_ROWS_TO_SCAN_PER_RUN = 250000;

const BATCH_COMMIT_SIZE = 450;
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

function getMaxWrites() {
  const raw = process.env.MAX_FIRESTORE_WRITES_PER_RUN;
  const parsed = Number.parseInt(raw || String(DEFAULT_MAX_WRITES), 10);

  if (!Number.isFinite(parsed) || parsed <= 0) {
    return DEFAULT_MAX_WRITES;
  }

  return Math.min(parsed, DEFAULT_MAX_WRITES);
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

  return Array.from(prefixes).slice(0, 100);
}

function splitTags(value) {
  if (!value) return [];

  return String(value)
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function rowContainsSingapore(row) {
  const countryTags = splitTags(row.countries_tags);
  return countryTags.includes(COUNTRY_TAG);
}

function compactProduct(row) {
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

async function getSyncState(db) {
  const ref = db.collection(SYNC_STATE_COLLECTION).doc(SYNC_STATE_DOC);
  const snap = await ref.get();

  if (!snap.exists) {
    return {
      lastProcessedRow: 0,
      totalSaved: 0,
      completedFullPass: false,
    };
  }

  const data = snap.data() || {};

  return {
    lastProcessedRow: Number(data.lastProcessedRow || 0),
    totalSaved: Number(data.totalSaved || 0),
    completedFullPass: Boolean(data.completedFullPass || false),
  };
}

async function saveSyncState(db, state) {
  const ref = db.collection(SYNC_STATE_COLLECTION).doc(SYNC_STATE_DOC);

  await ref.set(
    {
      ...state,
      lastRunAt: Date.now(),
      sourceUrl: CSV_URL,
      countryTag: COUNTRY_TAG,
    },
    { merge: true }
  );
}

async function runCsvSync() {
  const db = initializeFirebase();
  const userAgent = requireEnv("OPEN_FOOD_FACTS_USER_AGENT");
  const maxWrites = getMaxWrites();

  const initialState = await getSyncState(db);
  const startAfterRow = initialState.lastProcessedRow || 0;

  console.log("Starting Open Food Facts CSV → Firestore sync");
  console.log(`CSV URL: ${CSV_URL}`);
  console.log(`Country filter: ${COUNTRY_TAG}`);
  console.log(`Collection: ${COLLECTION_NAME}`);
  console.log(`Start after row: ${startAfterRow}`);
  console.log(`Max writes this run: ${maxWrites}`);
  console.log(`Max rows to scan this run: ${MAX_ROWS_TO_SCAN_PER_RUN}`);

  let rowNumber = 0;
  let scannedThisRun = 0;
  let matchedSingapore = 0;
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
      `Committed ${label} batch #${batchesCommitted} (${committingCount} writes): row=${rowNumber}, scanned=${scannedThisRun}, singapore=${matchedSingapore}, written=${written}`
    );

    // Persist the cursor after every successful commit so a crash later in
    // the run resumes here instead of at the previous run's cursor.
    try {
      await saveSyncState(db, {
        lastProcessedRow: rowNumber,
        totalSaved: Number(initialState.totalSaved || 0) + written,
        completedFullPass: false,
        lastResult: "partial_progress",
        lastWritten: written,
        lastMatchedSingapore: matchedSingapore,
        lastScannedRows: scannedThisRun,
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
          `Still scanning: row=${rowNumber}, scanned=${scannedThisRun}, singapore=${matchedSingapore}, written=${written}, skipped=${skipped}`
        );
      }

      // Cheap substring pre-filter; rowContainsSingapore on the parsed
      // countries_tags column stays the authoritative check.
      if (line.includes(COUNTRY_TAG)) {
        const row = parseRow(line, columnIndexes);

        if (rowContainsSingapore(row)) {
          matchedSingapore++;

          const compact = compactProduct(row);

          if (!compact) {
            skipped++;
          } else {
            const docRef = db.collection(COLLECTION_NAME).doc(compact.barcode);
            batch.set(docRef, compact, { merge: true });
            batchCount++;
            written++;

            if (batchCount >= BATCH_COMMIT_SIZE) {
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

  const completedFullPass =
    !stoppedBecauseWriteLimit && !stoppedBecauseRowLimit && streamError === null;

  // Never move the cursor backward: an error during the skip-to-cursor phase
  // would otherwise rewind progress recorded by earlier runs.
  const nextRow = completedFullPass ? 0 : Math.max(rowNumber, startAfterRow);

  await saveSyncState(db, {
    lastProcessedRow: nextRow,
    totalSaved: Number(initialState.totalSaved || 0) + written,
    completedFullPass,
    lastResult: streamError ? "stopped_or_error" : "success",
    ...(streamError ? { lastError: streamError.message } : {}),
    lastWritten: written,
    lastSkipped: skipped,
    lastMatchedSingapore: matchedSingapore,
    lastScannedRows: scannedThisRun,
  });

  if (streamError) {
    console.log(`CSV stream stopped: ${streamError.message}`);
  }

  console.log("CSV sync complete.");
  console.log(`Last processed row saved: ${nextRow}`);
  console.log(`Scanned rows this run: ${scannedThisRun}`);
  console.log(`Singapore rows matched: ${matchedSingapore}`);
  console.log(`Written: ${written}`);
  console.log(`Skipped: ${skipped}`);
  console.log(`Batches committed: ${batchesCommitted}`);
  console.log(`Completed full pass: ${completedFullPass}`);
}

module.exports = {
  openUrlWithRedirects,
  normalizeText,
  buildPrefixes,
  splitTags,
  rowContainsSingapore,
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
