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

// Per-chunk limits. A chunk stops once it has written this many matching
// products, scanned this many CSV rows, or finished a full CSV pass for the
// current country — whichever comes first. A single execution may process
// several chunks back to back (see getChunksPerRun).
const DEFAULT_MAX_WRITES = 500;
const MAX_ROWS_TO_SCAN_PER_CHUNK = 250000;
const BATCH_SIZE = 450;
const HEARTBEAT_ROW_INTERVAL = 25000;
const CURSOR_SKIP_LOG_INTERVAL = 250000;

// Manual bulk sync. Scheduled cron has been unreliable, so one execution can
// process up to MAX_CHUNKS_PER_RUN sequential chunks — advancing the importer
// as far as ~24 separate runs would — instead of re-triggering the workflow by
// hand. Chunks always run one after another, never concurrently.
const DEFAULT_CHUNKS_PER_RUN = 1;
const MAX_CHUNKS_PER_RUN = 24;

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

// Missing / invalid → 1. Clamped to [1, MAX_CHUNKS_PER_RUN] so a single manual
// run can advance the importer by at most 24 chunks and cannot request an
// unbounded execution time.
function getChunksPerRun() {
  const raw = process.env.CSV_SYNC_CHUNKS_PER_RUN;
  const parsed = Number.parseInt(raw || String(DEFAULT_CHUNKS_PER_RUN), 10);

  if (!Number.isFinite(parsed) || parsed <= 0) {
    return DEFAULT_CHUNKS_PER_RUN;
  }

  return Math.min(parsed, MAX_CHUNKS_PER_RUN);
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

// Streams one country's CSV pass and processes as many chunks as the remaining
// budget allows against that SINGLE open stream. A chunk that stops on the
// write cap or the row cap simply keeps reading from where it paused, so the
// CSV is only re-fetched when a country finishes its pass and the rotation
// advances to the next country — not once per chunk.
//
// Returns aggregate counters plus whether this country finished a full pass and
// any (non-fatal) stream error, so the caller can advance the rotation and its
// run-level totals.
async function processCountryStream(db, options) {
  const {
    userAgent,
    maxWrites,
    maxRows,
    currentCountryIndex,
    currentCountryTag,
    startAfterRow,
    countrySavedBefore,
    totalSavedAllBase,
    chunkBudget,
    chunkNumberBase,
    countryProgressSnapshot,
  } = options;

  let rowNumber = 0;
  // Cumulative over the whole stream (this country, this run).
  let writtenThisCountry = 0;
  let scannedThisCountry = 0;
  let matchedThisCountry = 0;
  let skippedThisCountry = 0;
  // Per-chunk counters, reset at every chunk boundary.
  let writtenThisChunk = 0;
  let scannedThisChunk = 0;
  let matchedThisChunk = 0;
  let skippedThisChunk = 0;
  let chunkStartRow = startAfterRow;

  let chunksCompletedHere = 0;
  let batchesCommitted = 0;
  let streamError = null;
  let stopReason = null; // 'budget' | 'completed' | 'error'

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
      `Committed ${label} batch #${batchesCommitted}: country=${currentCountryTag}, writes=${committingCount}, row=${rowNumber}, scanned=${scannedThisCountry}, matched=${matchedThisCountry}, written=${writtenThisCountry}`
    );

    // Persist the cursor after every successful commit so a crash mid-chunk
    // resumes here instead of at the chunk's start row.
    try {
      await saveRotationState(db, {
        currentCountryIndex,
        currentCountryTag,
        currentCountryLastProcessedRow: rowNumber,
        totalSavedAllCountries: totalSavedAllBase + writtenThisCountry,
        progressTag: currentCountryTag,
        progressLastProcessedRow: rowNumber,
        progressTotalSaved: countrySavedBefore + writtenThisCountry,
        progressCompletedFullPass: false,
        lastResult: "partial_progress",
        lastWritten: writtenThisCountry,
        lastScannedRows: scannedThisCountry,
        lastMatchedRows: matchedThisCountry,
        lastSkipped: skippedThisCountry,
        completedCountryThisRun: false,
      });
    } catch (error) {
      error.isPersistenceFailure = true;
      throw error;
    }
  }

  // Persists the durable cursor at a chunk boundary and logs the chunk summary.
  // On a completed country pass the rotation pointer advances to the next
  // country and this country's cursor resets to 0; otherwise the cursor holds
  // this country's current row so the next chunk / run resumes there.
  async function saveChunkBoundary({ completed, error }) {
    const chunkNumber = chunkNumberBase + chunksCompletedHere + 1;

    if (completed) {
      const nextCountryIndex = (currentCountryIndex + 1) % COUNTRY_ROTATION.length;
      const nextCountryTag = COUNTRY_ROTATION[nextCountryIndex];
      const nextCountryStartRow = Number(
        (countryProgressSnapshot[nextCountryTag] || {}).lastProcessedRow || 0
      );

      await saveRotationState(db, {
        currentCountryIndex: nextCountryIndex,
        currentCountryTag: nextCountryTag,
        currentCountryLastProcessedRow: nextCountryStartRow,
        totalSavedAllCountries: totalSavedAllBase + writtenThisCountry,
        progressTag: currentCountryTag,
        progressLastProcessedRow: 0,
        progressTotalSaved: countrySavedBefore + writtenThisCountry,
        progressCompletedFullPass: true,
        lastResult: "success",
        lastWritten: writtenThisChunk,
        lastScannedRows: scannedThisChunk,
        lastMatchedRows: matchedThisChunk,
        lastSkipped: skippedThisChunk,
        completedCountryThisRun: true,
      });

      console.log(
        `Chunk ${chunkNumber} complete: country=${currentCountryTag}, startRow=${chunkStartRow}, written=${writtenThisChunk}, scanned=${scannedThisChunk}, savedCursor=0 (full CSV pass done → advancing to ${nextCountryTag})`
      );
      return;
    }

    // Partial chunk (write cap, row cap, or stream error). Never rewind the
    // cursor below where earlier runs already reached: an error during the
    // skip-to-cursor phase would otherwise lose recorded progress.
    const cursorRow = Math.max(rowNumber, startAfterRow);

    await saveRotationState(db, {
      currentCountryIndex,
      currentCountryTag,
      currentCountryLastProcessedRow: cursorRow,
      totalSavedAllCountries: totalSavedAllBase + writtenThisCountry,
      progressTag: currentCountryTag,
      progressLastProcessedRow: cursorRow,
      progressTotalSaved: countrySavedBefore + writtenThisCountry,
      progressCompletedFullPass: false,
      lastResult: error ? "stopped_or_error" : "partial_progress",
      lastWritten: writtenThisChunk,
      lastScannedRows: scannedThisChunk,
      lastMatchedRows: matchedThisChunk,
      lastSkipped: skippedThisChunk,
      completedCountryThisRun: false,
      lastError: error ? error.message : undefined,
    });

    console.log(
      `Chunk ${chunkNumber} ${error ? "stopped" : "complete"}: country=${currentCountryTag}, startRow=${chunkStartRow}, written=${writtenThisChunk}, scanned=${scannedThisChunk}, savedCursor=${cursorRow}`
    );
  }

  const response = await openUrlWithRedirects(CSV_URL, userAgent);
  const gunzip = zlib.createGunzip();

  const lines = readline.createInterface({
    input: response.pipe(gunzip),
    crlfDelay: Infinity,
  });

  // .pipe() does not forward errors: without this wiring a download or gzip
  // failure would leave the line reader waiting forever. Recording the error
  // and closing the reader lets the chunk finish cleanly as a partial pass.
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

      scannedThisChunk++;
      scannedThisCountry++;

      if (scannedThisCountry % HEARTBEAT_ROW_INTERVAL === 0) {
        console.log(
          `Still scanning: country=${currentCountryTag}, row=${rowNumber}, scannedChunk=${scannedThisChunk}, matched=${matchedThisCountry}, written=${writtenThisCountry}, skipped=${skippedThisCountry}`
        );
      }

      // Cheap substring pre-filter; rowMatchesCountry on the parsed
      // countries_tags column stays the authoritative check.
      if (line.includes(currentCountryTag)) {
        const row = parseRow(line, columnIndexes);

        if (rowMatchesCountry(row, currentCountryTag)) {
          matchedThisChunk++;
          matchedThisCountry++;

          const compact = compactProduct(row, currentCountryTag);

          if (!compact) {
            skippedThisChunk++;
            skippedThisCountry++;
          } else {
            const docRef = db.collection(COLLECTION_NAME).doc(compact.barcode);
            batch.set(docRef, compact, { merge: true });
            batchCount++;
            writtenThisChunk++;
            writtenThisCountry++;

            if (batchCount >= BATCH_SIZE) {
              await commitCurrentBatch("progress");
            }
          }
        }
      }

      // Chunk boundary: a chunk ends when it hits the per-chunk write cap or
      // the per-chunk row cap. Commit any pending writes BEFORE saving the
      // cursor so the cursor never advances past uncommitted rows.
      const hitWriteCap = writtenThisChunk >= maxWrites;
      const hitRowCap = scannedThisChunk >= maxRows;

      if (hitWriteCap || hitRowCap) {
        console.log(
          hitWriteCap
            ? `Chunk hit max writes (${maxWrites}) at row=${rowNumber}.`
            : `Chunk hit max scanned rows (${maxRows}) at row=${rowNumber}.`
        );

        await commitCurrentBatch("chunk");
        await saveChunkBoundary({ completed: false });
        chunksCompletedHere++;

        if (chunksCompletedHere >= chunkBudget) {
          stopReason = "budget";
          break;
        }

        // Keep reading the SAME stream for the next chunk; only the per-chunk
        // counters reset.
        writtenThisChunk = 0;
        scannedThisChunk = 0;
        matchedThisChunk = 0;
        skippedThisChunk = 0;
        chunkStartRow = rowNumber;
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

  if (streamError) {
    // Flush whatever committed, record a partial cursor, count the partial as a
    // chunk, and let the caller stop the bulk run — re-streaming after a stream
    // error is risky, so we do not roll into more chunks.
    await commitCurrentBatch("final");
    await saveChunkBoundary({ completed: false, error: streamError });
    chunksCompletedHere++;
    stopReason = "error";
    console.log(`CSV stream stopped: ${streamError.message}`);
  } else if (stopReason === "budget") {
    // The chunk that exhausted the budget already saved its boundary above.
  } else {
    // The stream drained without hitting a cap or erroring → this country
    // finished a full CSV pass. That final (possibly small) chunk still counts.
    await commitCurrentBatch("final");
    await saveChunkBoundary({ completed: true });
    chunksCompletedHere++;
    stopReason = "completed";
  }

  const completedFullPass = stopReason === "completed";
  const finalCursorRow = completedFullPass ? 0 : Math.max(rowNumber, startAfterRow);

  return {
    chunksCompletedHere,
    writtenThisCountry,
    scannedThisCountry,
    matchedThisCountry,
    skippedThisCountry,
    finalCursorRow,
    completedFullPass,
    streamError,
    batchesCommitted,
  };
}

async function runCsvSync() {
  const db = initializeFirebase();
  const userAgent = requireEnv("OPEN_FOOD_FACTS_USER_AGENT");
  const maxWrites = getMaxWrites();
  const maxRows = MAX_ROWS_TO_SCAN_PER_CHUNK;
  const chunksPerRun = getChunksPerRun();

  const rawChunksRequested = Number.parseInt(
    process.env.CSV_SYNC_CHUNKS_PER_RUN || String(DEFAULT_CHUNKS_PER_RUN),
    10
  );
  const chunksRequested = Number.isFinite(rawChunksRequested) && rawChunksRequested > 0
    ? rawChunksRequested
    : DEFAULT_CHUNKS_PER_RUN;

  console.log("Starting Open Food Facts CSV → Firestore country-rotation sync (bulk chunk mode)");
  console.log(`CSV URL: ${CSV_URL}`);
  console.log(`Collection: ${COLLECTION_NAME}`);
  console.log(`Chunks requested: ${chunksRequested}`);
  console.log(`Chunks capped to: ${chunksPerRun} (max ${MAX_CHUNKS_PER_RUN})`);
  console.log(`Max writes per chunk: ${maxWrites}`);
  console.log(`Max rows to scan per chunk: ${maxRows}`);

  let chunksCompleted = 0;
  let totalWrittenAllChunks = 0;
  let countriesStreamed = 0;
  let finalCountryTag = null;
  let finalCursorRow = 0;
  let runStreamError = null;

  // Each iteration opens one stream for the current rotation country and runs
  // as many chunks as the remaining budget allows. State is re-read from
  // Firestore every iteration so that when a country completes its pass we pick
  // up the freshly advanced rotation pointer for the next stream. Chunks are
  // therefore strictly sequential — a stream fully finishes before the next
  // one opens, so no two chunks ever run concurrently.
  while (chunksCompleted < chunksPerRun) {
    const rotationState = await getRotationState(db);
    const currentCountryIndex = rotationState.currentCountryIndex;
    const currentCountryTag = COUNTRY_ROTATION[currentCountryIndex];
    const currentProgress = rotationState.countryProgress[currentCountryTag] || {};
    const startAfterRow = Number(currentProgress.lastProcessedRow || 0);
    const countrySavedBefore = Number(currentProgress.totalSaved || 0);
    const totalSavedAllBase = Number(rotationState.totalSavedAllCountries || 0);
    const chunkBudget = chunksPerRun - chunksCompleted;

    console.log(
      `\n=== Country stream: ${currentCountryTag} (index ${currentCountryIndex}/${COUNTRY_ROTATION.length}), startRow=${startAfterRow}, chunkBudget=${chunkBudget} ===`
    );

    const result = await processCountryStream(db, {
      userAgent,
      maxWrites,
      maxRows,
      currentCountryIndex,
      currentCountryTag,
      startAfterRow,
      countrySavedBefore,
      totalSavedAllBase,
      chunkBudget,
      chunkNumberBase: chunksCompleted,
      countryProgressSnapshot: rotationState.countryProgress,
    });

    chunksCompleted += result.chunksCompletedHere;
    totalWrittenAllChunks += result.writtenThisCountry;
    countriesStreamed++;
    finalCountryTag = currentCountryTag;
    finalCursorRow = result.finalCursorRow;

    if (result.streamError) {
      runStreamError = result.streamError;
      console.log(`Stopping bulk run early after stream error: ${result.streamError.message}`);
      break;
    }
  }

  console.log("\nCSV bulk sync complete.");
  console.log(`Total chunks completed: ${chunksCompleted} of ${chunksPerRun} requested`);
  console.log(`Total written across all chunks: ${totalWrittenAllChunks}`);
  console.log(`Country streams opened this run: ${countriesStreamed}`);
  console.log(`Final country: ${finalCountryTag}`);
  console.log(`Final cursor row: ${finalCursorRow}`);
  if (runStreamError) {
    console.log(`Ended early due to stream error: ${runStreamError.message}`);
  }
}

module.exports = {
  COUNTRY_ROTATION,
  normalizeCountryIndex,
  getChunksPerRun,
  openUrlWithRedirects,
  normalizeText,
  buildPrefixes,
  splitTags,
  rowMatchesCountry,
  compactProduct,
  buildColumnIndexes,
  parseRow,
  processCountryStream,
  runCsvSync,
};

if (require.main === module) {
  runCsvSync().catch((error) => {
    console.error("CSV sync failed:", error);
    process.exit(1);
  });
}
