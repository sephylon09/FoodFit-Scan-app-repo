const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const COLLECTION_NAME = "product_search_index";
const SYNC_STATE_COLLECTION = "sync_state";
const SYNC_STATE_DOC = "open_food_facts_country_rotation";

// Same priority rotation the CSV sync walks, in the same order, so the two
// scripts report on the exact set of countries the index is built from.
const PRIORITY_COUNTRIES = [
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

function requireEnv(name) {
  const value = process.env[name];
  if (!value || value.trim().length === 0) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function initializeFirebase() {
  const serviceAccountJson = requireEnv("FIREBASE_SERVICE_ACCOUNT");
  const serviceAccount = JSON.parse(serviceAccountJson);

  initializeApp({
    credential: cert(serviceAccount),
  });

  return getFirestore();
}

// Server-side count aggregation: Firestore tallies matching documents without
// streaming them back, so a multi-hundred-thousand document collection costs a
// single lightweight query instead of a full download.
async function countQuery(query) {
  const snapshot = await query.count().get();
  return snapshot.data().count;
}

// A failed count for one country (e.g. a transient error) should not abort the
// whole report, so failures are logged and rendered as "error" in that row.
async function safeCount(query, label) {
  try {
    return await countQuery(query);
  } catch (error) {
    console.error(`Count failed for ${label}: ${error.message}`);
    return null;
  }
}

function formatCount(value) {
  return value === null ? "error" : String(value);
}

function formatTimestamp(value) {
  if (value === null || value === undefined) {
    return "(none)";
  }
  if (typeof value === "number") {
    return `${value} (${new Date(value).toISOString()})`;
  }
  // Firestore Timestamp values expose toDate().
  if (typeof value.toDate === "function") {
    const date = value.toDate();
    return `${date.getTime()} (${date.toISOString()})`;
  }
  return String(value);
}

async function countByCountryTags(collection) {
  const counts = await Promise.all(
    PRIORITY_COUNTRIES.map((tag) =>
      safeCount(
        collection.where("countryTags", "array-contains", tag),
        `countryTags array-contains ${tag}`
      )
    )
  );
  return PRIORITY_COUNTRIES.map((tag, index) => [tag, counts[index]]);
}

async function countByMatchedCountryTag(collection) {
  const counts = await Promise.all(
    PRIORITY_COUNTRIES.map((tag) =>
      safeCount(
        collection.where("matchedCountryTag", "==", tag),
        `matchedCountryTag == ${tag}`
      )
    )
  );
  return PRIORITY_COUNTRIES.map((tag, index) => [tag, counts[index]]);
}

async function printSyncState(db) {
  console.log("Sync state:");

  const ref = db.collection(SYNC_STATE_COLLECTION).doc(SYNC_STATE_DOC);
  const snap = await ref.get();

  if (!snap.exists) {
    console.log(`(${SYNC_STATE_COLLECTION}/${SYNC_STATE_DOC} not found)`);
    return;
  }

  const data = snap.data() || {};

  console.log(`currentCountryTag: ${data.currentCountryTag ?? "(none)"}`);
  console.log(`currentCountryIndex: ${data.currentCountryIndex ?? "(none)"}`);
  console.log(`lastRunAt: ${formatTimestamp(data.lastRunAt)}`);
  console.log(`lastResult: ${data.lastResult ?? "(none)"}`);
  console.log(`lastWritten: ${data.lastWritten ?? "(none)"}`);
  console.log(`lastScannedRows: ${data.lastScannedRows ?? "(none)"}`);
  console.log(`lastMatchedRows: ${data.lastMatchedRows ?? "(none)"}`);

  const countryProgress =
    data.countryProgress && typeof data.countryProgress === "object"
      ? data.countryProgress
      : null;

  if (!countryProgress) {
    return;
  }

  console.log("");
  console.log("countryProgress summary (totalSaved / lastProcessedRow / completedFullPass):");

  // Report the priority countries in rotation order first, then any extra tags
  // the document happens to carry, so the summary stays readable.
  const seen = new Set();
  const orderedTags = [
    ...PRIORITY_COUNTRIES.filter((tag) => tag in countryProgress),
    ...Object.keys(countryProgress).filter((tag) => !PRIORITY_COUNTRIES.includes(tag)),
  ];

  for (const tag of orderedTags) {
    if (seen.has(tag)) continue;
    seen.add(tag);

    const progress = countryProgress[tag] || {};
    const totalSaved = progress.totalSaved ?? 0;
    const lastProcessedRow = progress.lastProcessedRow ?? 0;
    const completedFullPass = progress.completedFullPass ?? false;
    console.log(`${tag}: ${totalSaved} / ${lastProcessedRow} / ${completedFullPass}`);
  }
}

async function main() {
  const db = initializeFirebase();
  const collection = db.collection(COLLECTION_NAME);

  console.log("FoodFit Scan Firestore Product Stats");

  const total = await safeCount(collection, `total ${COLLECTION_NAME}`);
  console.log(`Total ${COLLECTION_NAME} docs: ${formatCount(total)}`);

  const byCountryTags = await countByCountryTags(collection);
  console.log("");
  console.log("Counts by countryTags:");
  for (const [tag, count] of byCountryTags) {
    console.log(`${tag}: ${formatCount(count)}`);
  }

  const byMatchedCountryTag = await countByMatchedCountryTag(collection);
  console.log("");
  console.log("Counts by matchedCountryTag:");
  for (const [tag, count] of byMatchedCountryTag) {
    console.log(`${tag}: ${formatCount(count)}`);
  }

  console.log("");
  await printSyncState(db);
}

main().catch((error) => {
  console.error("Firestore product stats failed:", error);
  process.exit(1);
});
