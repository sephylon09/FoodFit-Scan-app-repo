const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const https = require("https");
const zlib = require("zlib");
const csv = require("csv-parser");

const COLLECTION_NAME = "product_search_index";
const SYNC_STATE_COLLECTION = "sync_state";
const SYNC_STATE_DOC = "open_food_facts_csv";

const CSV_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz";

const COUNTRY_TAG = "en:singapore";
const DEFAULT_MAX_WRITES = 18000;

// Safety limit so the script does not run forever in GitHub Actions.
const MAX_ROWS_TO_SCAN_PER_RUN = 1500000;

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

async function commitBatch(batch, count) {
  if (count > 0) {
    await batch.commit();
  }
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
  let batch = db.batch();
  let batchCount = 0;
  let stoppedBecauseWriteLimit = false;
  let stoppedBecauseRowLimit = false;

  return new Promise((resolve, reject) => {
    openUrlWithRedirects(CSV_URL, userAgent)
      .then((response) => {
        if (response.statusCode !== 200) {
          reject(new Error(`CSV download failed with HTTP ${response.statusCode}`));
          return;
        }

        response
          .pipe(zlib.createGunzip())
          .pipe(
            csv({
              separator: "\t",
              mapHeaders: ({ header }) => header.trim(),
            })
          )
          .on("data", async (row) => {
            response.pause();

            try {
              rowNumber++;

              if (rowNumber <= startAfterRow) {
                response.resume();
                return;
              }

              scannedThisRun++;

              if (scannedThisRun > MAX_ROWS_TO_SCAN_PER_RUN) {
                stoppedBecauseRowLimit = true;
                response.destroy();
                return;
              }

              if (!rowContainsSingapore(row)) {
                response.resume();
                return;
              }

              matchedSingapore++;

              const compact = compactProduct(row);
              if (!compact) {
                skipped++;
                response.resume();
                return;
              }

              const docRef = db.collection(COLLECTION_NAME).doc(compact.barcode);
              batch.set(docRef, compact, { merge: true });
              batchCount++;
              written++;

              if (batchCount >= 450) {
                await commitBatch(batch, batchCount);
                batch = db.batch();
                batchCount = 0;

                console.log(
                  `Progress: row=${rowNumber}, scanned=${scannedThisRun}, singapore=${matchedSingapore}, written=${written}`
                );
              }

              if (written >= maxWrites) {
                stoppedBecauseWriteLimit = true;
                response.destroy();
                return;
              }

              response.resume();
            } catch (error) {
              reject(error);
            }
          })
          .on("end", async () => {
            try {
              await commitBatch(batch, batchCount);

              const completedFullPass = !stoppedBecauseWriteLimit && !stoppedBecauseRowLimit;
              const nextRow = completedFullPass ? 0 : rowNumber;

              await saveSyncState(db, {
                lastProcessedRow: nextRow,
                totalSaved: Number(initialState.totalSaved || 0) + written,
                completedFullPass,
                lastResult: "success",
                lastWritten: written,
                lastSkipped: skipped,
                lastMatchedSingapore: matchedSingapore,
                lastScannedRows: scannedThisRun,
              });

              console.log("CSV sync complete.");
              console.log(`Last processed row saved: ${nextRow}`);
              console.log(`Scanned rows this run: ${scannedThisRun}`);
              console.log(`Singapore rows matched: ${matchedSingapore}`);
              console.log(`Written: ${written}`);
              console.log(`Skipped: ${skipped}`);
              console.log(`Completed full pass: ${completedFullPass}`);

              resolve();
            } catch (error) {
              reject(error);
            }
          })
          .on("error", async (error) => {
            try {
              await commitBatch(batch, batchCount);

              await saveSyncState(db, {
                lastProcessedRow: rowNumber,
                totalSaved: Number(initialState.totalSaved || 0) + written,
                completedFullPass: false,
                lastResult: "stopped_or_error",
                lastError: error.message,
                lastWritten: written,
                lastSkipped: skipped,
                lastMatchedSingapore: matchedSingapore,
                lastScannedRows: scannedThisRun,
              });

              console.log(`CSV stream stopped: ${error.message}`);
              console.log(`Saved cursor at row: ${rowNumber}`);
              console.log(`Written before stop: ${written}`);

              resolve();
            } catch (saveError) {
              reject(saveError);
            }
          });
      })
      .catch(reject);
  });
}

runCsvSync().catch((error) => {
  console.error("CSV sync failed:", error);
  process.exit(1);
});