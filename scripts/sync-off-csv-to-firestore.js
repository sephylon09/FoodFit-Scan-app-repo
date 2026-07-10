const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const https = require("https");
const zlib = require("zlib");
const readline = require("readline");

const COLLECTION_NAME = "product_search_index";
const SYNC_STATE_COLLECTION = "sync_state";
const SYNC_STATE_DOC = "open_food_facts_country_rotation";
// Image backfill keeps its own cursor so it can re-pass the CSV from row 0 without
// disturbing the country-rotation cursor a normal sync depends on.
const IMAGE_BACKFILL_STATE_DOC = "open_food_facts_image_backfill";

const CSV_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz";

// Run modes, selected with CSV_SYNC_MODE. Anything unrecognised is a hard error rather
// than a silent fallback, so a typo in the workflow input can never run the wrong mode.
const MODE_NORMAL = "normal";
const MODE_RESET_SINGAPORE = "reset-singapore";
const MODE_IMAGE_BACKFILL = "image-backfill";
const MODES = [MODE_NORMAL, MODE_RESET_SINGAPORE, MODE_IMAGE_BACKFILL];

// reset-singapore is destructive; it only runs when this exact string is supplied.
const RESET_CONFIRMATION = "RESET";

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

// Deletion is paginated: each round reads at most this many document refs, deletes them,
// then re-queries. The full collection is never held in memory.
const DELETE_BATCH_SIZE = 300;

// Image backfill resolves candidate barcodes in groups so it can skip products that are
// not in the index (and those that already have an image) without a read per row.
const IMAGE_BACKFILL_LOOKUP_GROUP = 300;

// Each index lookup is a billed Firestore read, so a backfill chunk ends after this many
// candidates even if it has written nothing. Without it, one chunk could read the whole
// index looking for the handful of documents that still lack an image.
const MAX_IMAGE_BACKFILL_LOOKUPS_PER_CHUNK = 5000;

// Manual bulk sync. Scheduled cron has been unreliable, so one execution can
// process up to MAX_CHUNKS_PER_RUN sequential chunks — advancing the importer
// as far as ~24 separate runs would — instead of re-triggering the workflow by
// hand. Chunks always run one after another, never concurrently.
const DEFAULT_CHUNKS_PER_RUN = 1;
const MAX_CHUNKS_PER_RUN = 24;

// The Open Food Facts CSV has ~200 columns; only these are read. Positions
// come from the header row, so upstream column reordering is harmless.
const REQUIRED_COLUMNS = ["code", "product_name", "countries_tags"];
const OPTIONAL_COLUMNS = ["brands", "categories_tags", "last_modified_t"];

// Image columns vary between OFF CSV exports (some exports drop
// image_front_url entirely). imageUrl is taken from the first non-empty column
// in this priority order; none of them is required.
const IMAGE_COLUMN_PRIORITY = [
  "image_front_url",
  "image_url",
  "image_front_small_url",
  "image_small_url",
  "image_front_thumb_url",
  "image_thumb_url",
];

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

// Missing / blank → "normal". Case and surrounding whitespace are forgiven; an
// unrecognised value throws so the run fails instead of quietly syncing.
function parseMode(rawMode) {
  const value = String(rawMode === undefined || rawMode === null ? "" : rawMode)
    .trim()
    .toLowerCase();

  if (value.length === 0) {
    return MODE_NORMAL;
  }

  if (!MODES.includes(value)) {
    throw new Error(`Unknown sync mode: "${rawMode}". Expected one of: ${MODES.join(", ")}`);
  }

  return value;
}

// Deliberately exact: no trimming, no case folding. "reset", " RESET " and "Reset" all
// fail, so the destructive path cannot be reached by an approximate answer.
function isResetConfirmed(rawConfirmation) {
  return rawConfirmation === RESET_CONFIRMATION;
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

// The index only ever contains products from COUNTRY_ROTATION, so image backfill can
// ignore every other row without a Firestore read.
function rowMatchesAnyRotationCountry(row) {
  const tags = splitTags(row.countries_tags);
  return COUNTRY_ROTATION.some((countryTag) => tags.includes(countryTag));
}

// Substring pre-filter for the above: cheap, may produce false positives (which
// rowMatchesAnyRotationCountry then rejects), never false negatives.
function lineMayMatchRotationCountry(line) {
  return COUNTRY_ROTATION.some((countryTag) => line.includes(countryTag));
}

// First non-empty image column by IMAGE_COLUMN_PRIORITY, trimmed. Values that
// are not http(s) URLs are skipped so a stray CSV value cannot become an
// imageUrl the app would then fail to load silently. Null when nothing usable.
function getBestImageUrl(row) {
  for (const column of IMAGE_COLUMN_PRIORITY) {
    const raw = row[column];
    if (raw === undefined || raw === null) continue;

    const trimmed = String(raw).trim();
    if (!trimmed) continue;
    if (!/^https?:\/\//i.test(trimmed)) continue;

    return trimmed;
  }
  return null;
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
    imageUrl: getBestImageUrl(row),
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

// Firestore write payload for a compact product document. imageUrl is dropped
// when empty so a merge:true write can never overwrite an image an earlier
// pass (or the API sync) already stored with null; every other field is
// written as-is.
function buildWritePayload(compact) {
  const payload = { ...compact };
  if (payload.imageUrl === null || payload.imageUrl === undefined) {
    delete payload.imageUrl;
  }
  return payload;
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

  const availableImageColumns = IMAGE_COLUMN_PRIORITY.filter((column) => indexes.has(column));
  if (availableImageColumns.length > 0) {
    console.log(`Available image columns: ${availableImageColumns.join(", ")}`);
  } else {
    console.log("Warning: CSV header has no known image columns; imageUrl will stay empty this pass.");
  }
  console.log(`Using image priority: ${IMAGE_COLUMN_PRIORITY.join(" > ")}`);

  return indexes;
}

function parseRow(line, columnIndexes) {
  const fields = line.split("\t");

  const field = (column) => {
    const index = columnIndexes.get(column);
    return index === undefined ? undefined : fields[index];
  };

  const row = {
    code: field("code"),
    product_name: field("product_name"),
    brands: field("brands"),
    countries_tags: field("countries_tags"),
    categories_tags: field("categories_tags"),
    last_modified_t: field("last_modified_t"),
  };

  for (const column of IMAGE_COLUMN_PRIORITY) {
    row[column] = field(column);
  }

  return row;
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
            // merge:true updates existing docs in place, so imageUrl backfills
            // as rows are re-passed; buildWritePayload keeps a missing image
            // from clobbering a stored one.
            batch.set(docRef, buildWritePayload(compact), { merge: true });
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

// ── reset-singapore ─────────────────────────────────────────────────────────
//
// Deletes every document in the collection, one bounded page at a time. Each round
// re-queries the collection head: the previous page's documents are gone, so the query
// naturally advances without a cursor and the process never holds more than
// `batchSize` refs. Returns the number of documents deleted.
async function deleteCollectionInBatches(db, collectionName, batchSize = DELETE_BATCH_SIZE) {
  let totalDeleted = 0;
  let round = 0;

  for (;;) {
    const snapshot = await db.collection(collectionName).limit(batchSize).get();
    const docs = snapshot.docs || [];

    if (docs.length === 0) {
      break;
    }

    const batch = db.batch();
    for (const doc of docs) {
      batch.delete(doc.ref);
    }
    await batch.commit();

    totalDeleted += docs.length;
    round++;
    console.log(
      `Deleted page #${round} from ${collectionName}: ${docs.length} docs (total deleted: ${totalDeleted})`
    );

    // A short page means the collection head is exhausted.
    if (docs.length < batchSize) {
      break;
    }
  }

  return totalDeleted;
}

// The exact rotation state a fresh Singapore-first import starts from. Written with
// set() and NO merge, so stale per-country progress from the previous dataset cannot
// survive the reset.
function buildSingaporeResetState(now = Date.now()) {
  const singapore = COUNTRY_ROTATION[0];

  return {
    currentCountryIndex: 0,
    currentCountryTag: singapore,
    currentCountryLastProcessedRow: 0,
    totalSavedAllCountries: 0,
    countryProgress: {
      [singapore]: {
        lastProcessedRow: 0,
        totalSaved: 0,
        completedFullPass: false,
      },
    },
    lastResult: "reset_to_singapore",
    lastRunAt: now,
    lastWritten: 0,
    lastScannedRows: 0,
    lastMatchedRows: 0,
    lastSkipped: 0,
    completedCountryThisRun: false,
    sourceUrl: CSV_URL,
  };
}

// Destructive. Empties product_search_index, clears both sync_state cursors, and rewrites
// the rotation cursor so the NEXT normal run starts at Singapore, row 0 (i.e. the first
// row it reads is CSV row 1). Imports nothing itself — reset and import stay separate so
// a reset run cannot half-succeed.
async function runResetToSingapore(db, confirmation) {
  if (!isResetConfirmed(confirmation)) {
    throw new Error(
      `Refusing to reset: confirm_reset must be exactly "${RESET_CONFIRMATION}". ` +
        "Nothing was deleted."
    );
  }

  console.log(`Confirmation accepted. Deleting every document in ${COLLECTION_NAME}…`);

  const deletedCount = await deleteCollectionInBatches(db, COLLECTION_NAME);
  console.log(`Deletes committed: ${deletedCount} documents removed from ${COLLECTION_NAME}.`);

  // Remove the optional backfill cursor so a later image-backfill run starts from row 0
  // against the rebuilt index. delete() on a missing document is a no-op.
  await db.collection(SYNC_STATE_COLLECTION).doc(IMAGE_BACKFILL_STATE_DOC).delete();
  console.log(`Cleared sync_state/${IMAGE_BACKFILL_STATE_DOC} (if it existed).`);

  const resetState = buildSingaporeResetState();
  await db.collection(SYNC_STATE_COLLECTION).doc(SYNC_STATE_DOC).set(resetState);

  console.log(`Reset sync_state/${SYNC_STATE_DOC}:`);
  console.log(`  currentCountryIndex     = ${resetState.currentCountryIndex}`);
  console.log(`  currentCountryTag       = ${resetState.currentCountryTag}`);
  console.log(`  ${resetState.currentCountryTag}.lastProcessedRow  = 0`);
  console.log(`  ${resetState.currentCountryTag}.totalSaved        = 0`);
  console.log(`  ${resetState.currentCountryTag}.completedFullPass = false`);
  console.log(`  totalSavedAllCountries  = 0`);
  console.log(`  lastResult              = ${resetState.lastResult}`);
  console.log(`  lastRunAt               = ${new Date(resetState.lastRunAt).toISOString()}`);
  console.log(
    `\nReset complete. No products were imported this run. Run the workflow again with ` +
      `mode=normal to rebuild the index from ${resetState.currentCountryTag}, row 1.`
  );

  return { deletedCount, resetState };
}

// ── image-backfill ──────────────────────────────────────────────────────────

// Merge payload for one already-indexed product. Only ever called with a non-empty
// imageUrl, so a merge write can never replace a stored image with null.
function buildImageBackfillUpdate(imageUrl, now = Date.now()) {
  return {
    imageUrl,
    updatedAt: now,
    imageBackfilledAt: now,
    imageSource: "openfoodfacts-csv",
  };
}

async function getImageBackfillState(db) {
  const ref = db.collection(SYNC_STATE_COLLECTION).doc(IMAGE_BACKFILL_STATE_DOC);
  const snap = await ref.get();
  const data = snap.exists ? snap.data() || {} : {};

  const lastProcessedRow = Number(data.lastProcessedRow || 0);

  return {
    lastProcessedRow: Number.isFinite(lastProcessedRow) && lastProcessedRow > 0 ? lastProcessedRow : 0,
    totalUpdated: Number(data.totalUpdated || 0),
    completedFullPass: data.completedFullPass === true,
  };
}

async function saveImageBackfillState(db, fields) {
  const payload = {
    lastProcessedRow: fields.lastProcessedRow,
    totalUpdated: fields.totalUpdated,
    completedFullPass: fields.completedFullPass,
    lastResult: fields.lastResult,
    lastRunAt: Date.now(),
    lastScannedRows: fields.lastScannedRows,
    lastUpdated: fields.lastUpdated,
    lastSkipped: fields.lastSkipped,
    sourceUrl: CSV_URL,
  };

  if (fields.lastError) {
    payload.lastError = fields.lastError;
  }

  await db
    .collection(SYNC_STATE_COLLECTION)
    .doc(IMAGE_BACKFILL_STATE_DOC)
    .set(payload, { merge: true });
}

// Streams the CSV once and merges imageUrl into documents that already exist in
// product_search_index and do not have one yet. Never creates or deletes documents, and
// never touches the country-rotation cursor. Candidate barcodes are resolved in groups
// (one getAll per group) rather than one read per row.
async function runImageBackfill(db, options) {
  const { userAgent, maxUpdates, maxRows, chunksPerRun } = options;

  const state = await getImageBackfillState(db);
  const startAfterRow = state.lastProcessedRow;

  console.log(`State doc: sync_state/${IMAGE_BACKFILL_STATE_DOC}`);
  console.log(`Start row: ${startAfterRow}`);
  console.log(`Max image updates per chunk: ${maxUpdates}`);
  console.log(`Max rows to scan per chunk: ${maxRows}`);
  if (state.completedFullPass) {
    console.log("Previous backfill pass completed; this run re-passes the CSV from row 0.");
  }

  let rowNumber = 0;
  let scannedThisRun = 0;
  let updatedThisRun = 0;
  let skippedThisRun = 0;
  let scannedThisChunk = 0;
  let updatedThisChunk = 0;
  let lookupsThisChunk = 0;
  let chunksCompletedHere = 0;
  let batchesCommitted = 0;
  let streamError = null;
  let stopReason = null;

  // barcode -> imageUrl, awaiting an existence/imageUrl check. A Map (not an array) so a
  // barcode repeated inside one group cannot send duplicate refs to getAll().
  let candidates = new Map();
  let batch = db.batch();
  let batchCount = 0;

  async function commitCurrentBatch(label) {
    const committingBatch = batch;
    const committingCount = batchCount;

    batch = db.batch();
    batchCount = 0;

    if (committingCount === 0) return;

    try {
      await committingBatch.commit();
    } catch (error) {
      error.isPersistenceFailure = true;
      throw error;
    }

    batchesCommitted++;
    console.log(
      `Committed ${label} batch #${batchesCommitted}: writes=${committingCount}, row=${rowNumber}, scanned=${scannedThisRun}, updated=${updatedThisRun}, skipped=${skippedThisRun}`
    );

    try {
      await saveImageBackfillState(db, {
        lastProcessedRow: rowNumber,
        totalUpdated: state.totalUpdated + updatedThisRun,
        completedFullPass: false,
        lastResult: "partial_progress",
        lastScannedRows: scannedThisRun,
        lastUpdated: updatedThisRun,
        lastSkipped: skippedThisRun,
      });
    } catch (error) {
      error.isPersistenceFailure = true;
      throw error;
    }
  }

  // Resolves a group of candidate barcodes against the index and queues merge updates
  // for the ones that exist and are missing an image. Documents that are absent from the
  // index are skipped: backfill must never create new documents.
  async function flushCandidates() {
    if (candidates.size === 0) return;

    const group = Array.from(candidates.entries());
    candidates = new Map();

    const refs = group.map(([barcode]) => db.collection(COLLECTION_NAME).doc(barcode));
    const snapshots = await db.getAll(...refs, { fieldMask: ["imageUrl"] });

    for (let i = 0; i < snapshots.length; i++) {
      const snapshot = snapshots[i];
      const [, imageUrl] = group[i];

      const existingImageUrl = snapshot.exists ? snapshot.get("imageUrl") : undefined;
      const alreadyHasImage =
        typeof existingImageUrl === "string" && existingImageUrl.trim().length > 0;

      if (!snapshot.exists || alreadyHasImage) {
        skippedThisRun++;
        continue;
      }

      batch.set(refs[i], buildImageBackfillUpdate(imageUrl), { merge: true });
      batchCount++;
      updatedThisRun++;
      updatedThisChunk++;

      if (batchCount >= BATCH_SIZE) {
        await commitCurrentBatch("progress");
      }
    }
  }

  async function saveChunkBoundary({ completed, error }) {
    const chunkNumber = chunksCompletedHere + 1;
    const cursorRow = completed ? 0 : Math.max(rowNumber, startAfterRow);

    await saveImageBackfillState(db, {
      lastProcessedRow: cursorRow,
      totalUpdated: state.totalUpdated + updatedThisRun,
      completedFullPass: completed === true,
      lastResult: error ? "stopped_or_error" : completed ? "success" : "partial_progress",
      lastScannedRows: scannedThisChunk,
      lastUpdated: updatedThisChunk,
      lastSkipped: skippedThisRun,
      lastError: error ? error.message : undefined,
    });

    console.log(
      `Chunk ${chunkNumber} ${error ? "stopped" : "complete"}: updated=${updatedThisChunk}, scanned=${scannedThisChunk}, savedCursor=${cursorRow}${
        completed ? " (full CSV pass done)" : ""
      }`
    );
  }

  const response = await openUrlWithRedirects(CSV_URL, userAgent);
  const gunzip = zlib.createGunzip();

  const lines = readline.createInterface({
    input: response.pipe(gunzip),
    crlfDelay: Infinity,
  });

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
      scannedThisRun++;

      if (scannedThisRun % HEARTBEAT_ROW_INTERVAL === 0) {
        console.log(
          `Still scanning: row=${rowNumber}, scannedChunk=${scannedThisChunk}, updated=${updatedThisRun}, skipped=${skippedThisRun}`
        );
      }

      // Cheap pre-filters: a row can only contribute an image if it carries a URL, and
      // it can only be in the index if it is tagged with a rotation country. Skipping
      // everything else keeps the read bill proportional to the index, not to the CSV.
      if (line.includes("http") && lineMayMatchRotationCountry(line)) {
        const row = parseRow(line, columnIndexes);
        const barcode = String(row.code || "").trim();
        const imageUrl = getBestImageUrl(row);

        if (barcode && imageUrl && rowMatchesAnyRotationCountry(row)) {
          candidates.set(barcode, imageUrl);
          lookupsThisChunk++;

          if (candidates.size >= IMAGE_BACKFILL_LOOKUP_GROUP) {
            await flushCandidates();
          }
        }
      }

      const hitUpdateCap = updatedThisChunk >= maxUpdates;
      const hitLookupCap = lookupsThisChunk >= MAX_IMAGE_BACKFILL_LOOKUPS_PER_CHUNK;
      const hitRowCap = scannedThisChunk >= maxRows;

      if (hitUpdateCap || hitLookupCap || hitRowCap) {
        console.log(
          hitUpdateCap
            ? `Chunk hit max image updates (${maxUpdates}) at row=${rowNumber}.`
            : hitLookupCap
              ? `Chunk hit max index lookups (${MAX_IMAGE_BACKFILL_LOOKUPS_PER_CHUNK}) at row=${rowNumber}.`
              : `Chunk hit max scanned rows (${maxRows}) at row=${rowNumber}.`
        );

        await flushCandidates();
        await commitCurrentBatch("chunk");
        await saveChunkBoundary({ completed: false });
        chunksCompletedHere++;

        if (chunksCompletedHere >= chunksPerRun) {
          stopReason = "budget";
          break;
        }

        scannedThisChunk = 0;
        updatedThisChunk = 0;
        lookupsThisChunk = 0;
      }
    }
  } catch (error) {
    if (error && (error.isPersistenceFailure || error.isFatalHeaderError)) {
      throw error;
    }
    streamError = error;
  } finally {
    closingStreams = true;
    lines.close();
    response.destroy();
    gunzip.destroy();
  }

  if (!streamError && inputError) {
    streamError = inputError;
  }

  if (streamError) {
    await flushCandidates();
    await commitCurrentBatch("final");
    await saveChunkBoundary({ completed: false, error: streamError });
    chunksCompletedHere++;
    stopReason = "error";
    console.log(`CSV stream stopped: ${streamError.message}`);
  } else if (stopReason === "budget") {
    // The chunk that exhausted the budget already saved its boundary above.
  } else {
    await flushCandidates();
    await commitCurrentBatch("final");
    await saveChunkBoundary({ completed: true });
    chunksCompletedHere++;
    stopReason = "completed";
  }

  const completedFullPass = stopReason === "completed";
  const finalCursorRow = completedFullPass ? 0 : Math.max(rowNumber, startAfterRow);

  console.log("\nImage backfill complete.");
  console.log(`Chunks completed: ${chunksCompletedHere} of ${chunksPerRun} requested`);
  console.log(`Rows scanned: ${scannedThisRun}`);
  console.log(`Image updates committed: ${updatedThisRun} (batches: ${batchesCommitted})`);
  console.log(`Rows skipped (not indexed / already had an image): ${skippedThisRun}`);
  console.log(`Final cursor row: ${finalCursorRow}`);
  console.log(`Completed full pass: ${completedFullPass}`);

  return {
    chunksCompletedHere,
    scannedThisRun,
    updatedThisRun,
    skippedThisRun,
    finalCursorRow,
    completedFullPass,
    streamError,
    batchesCommitted,
  };
}

// ── normal ──────────────────────────────────────────────────────────────────

async function runNormalSync(db, options) {
  const { userAgent, maxWrites, maxRows, chunksPerRun } = options;

  console.log(`State doc: sync_state/${SYNC_STATE_DOC}`);
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

  return { chunksCompleted, totalWrittenAllChunks, finalCountryTag, finalCursorRow };
}

// ── entry point ─────────────────────────────────────────────────────────────

async function runCsvSync() {
  const mode = parseMode(process.env.CSV_SYNC_MODE);
  const db = initializeFirebase();

  console.log("Starting Open Food Facts CSV → Firestore job");
  console.log(`Selected mode: ${mode}`);
  console.log(`CSV URL: ${CSV_URL}`);
  console.log(`Collection: ${COLLECTION_NAME}`);

  if (mode === MODE_RESET_SINGAPORE) {
    // Never streams the CSV: reset only clears Firestore and rewrites the cursor.
    await runResetToSingapore(db, process.env.CSV_SYNC_CONFIRM_RESET);
    return;
  }

  const userAgent = requireEnv("OPEN_FOOD_FACTS_USER_AGENT");
  const maxWrites = getMaxWrites();
  const maxRows = MAX_ROWS_TO_SCAN_PER_CHUNK;
  const chunksPerRun = getChunksPerRun();

  const rawChunksRequested = Number.parseInt(
    process.env.CSV_SYNC_CHUNKS_PER_RUN || String(DEFAULT_CHUNKS_PER_RUN),
    10
  );
  const chunksRequested =
    Number.isFinite(rawChunksRequested) && rawChunksRequested > 0
      ? rawChunksRequested
      : DEFAULT_CHUNKS_PER_RUN;

  console.log(`Chunks requested: ${chunksRequested}`);
  console.log(`Chunks capped to: ${chunksPerRun} (max ${MAX_CHUNKS_PER_RUN})`);

  if (mode === MODE_IMAGE_BACKFILL) {
    await runImageBackfill(db, {
      userAgent,
      maxUpdates: maxWrites,
      maxRows,
      chunksPerRun,
    });
    return;
  }

  await runNormalSync(db, { userAgent, maxWrites, maxRows, chunksPerRun });
}

module.exports = {
  COLLECTION_NAME,
  COUNTRY_ROTATION,
  IMAGE_COLUMN_PRIORITY,
  SYNC_STATE_COLLECTION,
  SYNC_STATE_DOC,
  IMAGE_BACKFILL_STATE_DOC,
  MODE_NORMAL,
  MODE_RESET_SINGAPORE,
  MODE_IMAGE_BACKFILL,
  MODES,
  RESET_CONFIRMATION,
  DELETE_BATCH_SIZE,
  MAX_CHUNKS_PER_RUN,
  parseMode,
  isResetConfirmed,
  normalizeCountryIndex,
  getChunksPerRun,
  openUrlWithRedirects,
  normalizeText,
  buildPrefixes,
  splitTags,
  rowMatchesCountry,
  rowMatchesAnyRotationCountry,
  lineMayMatchRotationCountry,
  getBestImageUrl,
  compactProduct,
  buildWritePayload,
  buildColumnIndexes,
  parseRow,
  deleteCollectionInBatches,
  buildSingaporeResetState,
  runResetToSingapore,
  buildImageBackfillUpdate,
  getImageBackfillState,
  processCountryStream,
  runNormalSync,
  runCsvSync,
};

if (require.main === module) {
  runCsvSync().catch((error) => {
    console.error("CSV sync failed:", error);
    process.exit(1);
  });
}
