const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const COLLECTION_NAME = "product_search_index";
const DEFAULT_MAX_WRITES = 18000;
const PAGE_SIZE = 100;
const MAX_PAGES = 180; // 180 x 100 = up to 18,000 product attempts
const COUNTRY_TAG = "en:singapore";

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

function compactProduct(product) {
  const barcode = String(product.code || "").trim();
  const name = String(product.product_name || "").trim();
  const brand = product.brands ? String(product.brands).trim() : null;

  if (!barcode || !name) {
    return null;
  }

  return {
    barcode,
    name,
    brand,
    imageUrl: product.image_front_url || null,
    countryTags: Array.isArray(product.countries_tags) ? product.countries_tags : [],
    categoriesTags: Array.isArray(product.categories_tags) ? product.categories_tags : [],
    searchName: normalizeText(`${name} ${brand || ""}`),
    searchPrefixes: buildPrefixes(name, brand),
    source: "openfoodfacts",
    lastModifiedFromApi: product.last_modified_t || null,
    updatedAt: Date.now(),
  };
}

function initializeFirebase() {
  const serviceAccountJson = requireEnv("FIREBASE_SERVICE_ACCOUNT");
  const serviceAccount = JSON.parse(serviceAccountJson);

  initializeApp({
    credential: cert(serviceAccount),
  });

  return getFirestore();
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchOpenFoodFactsPage(page, userAgent) {
  const url = new URL("https://world.openfoodfacts.org/api/v2/search");

  url.searchParams.set("countries_tags", COUNTRY_TAG);
  url.searchParams.set(
    "fields",
    [
      "code",
      "product_name",
      "brands",
      "image_front_url",
      "countries_tags",
      "categories_tags",
      "last_modified_t",
    ].join(",")
  );
  url.searchParams.set("page_size", "50");
  url.searchParams.set("page", String(page));

  const maxAttempts = 4;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const response = await fetch(url, {
      headers: {
        "User-Agent": userAgent,
        "Accept": "application/json",
      },
    });

    if (response.ok) {
      return response.json();
    }

    const retryable = response.status === 429 || response.status === 500 || response.status === 502 || response.status === 503 || response.status === 504;

    console.log(
      `Open Food Facts page ${page} attempt ${attempt}/${maxAttempts} failed with HTTP ${response.status}`
    );

    if (!retryable || attempt === maxAttempts) {
      throw new Error(`Open Food Facts request failed on page ${page}: ${response.status}`);
    }

    const waitMs = attempt * 5000;
    console.log(`Waiting ${waitMs}ms before retry...`);
    await sleep(waitMs);
  }
}

async function commitBatch(batch, count) {
  if (count > 0) {
    await batch.commit();
  }
}

async function upsertProducts(db, products, remainingWrites) {
  let batch = db.batch();
  let batchCount = 0;
  let written = 0;
  let skipped = 0;

  for (const product of products) {
    if (written >= remainingWrites) {
      break;
    }

    const compact = compactProduct(product);
    if (!compact) {
      skipped++;
      continue;
    }

    const docRef = db.collection(COLLECTION_NAME).doc(compact.barcode);

    batch.set(docRef, compact, { merge: true });
    batchCount++;
    written++;

    // Firestore batch limit is 500 writes. Keep below it.
    if (batchCount >= 450) {
      await commitBatch(batch, batchCount);
      batch = db.batch();
      batchCount = 0;
    }
  }

  await commitBatch(batch, batchCount);

  return { written, skipped };
}

async function main() {
  const db = initializeFirebase();
  const userAgent = requireEnv("OPEN_FOOD_FACTS_USER_AGENT");
  const maxWrites = getMaxWrites();

  console.log(`Starting Open Food Facts → Firestore sync`);
  console.log(`Collection: ${COLLECTION_NAME}`);
  console.log(`Country filter: ${COUNTRY_TAG}`);
  console.log(`Max writes this run: ${maxWrites}`);

  let totalWritten = 0;
  let totalSkipped = 0;

  for (let page = 1; page <= MAX_PAGES; page++) {
    if (totalWritten >= maxWrites) {
      console.log(`Write cap reached: ${totalWritten}/${maxWrites}`);
      break;
    }

    let data;

    try {
      data = await fetchOpenFoodFactsPage(page, userAgent);
    } catch (error) {
      console.log(`Stopping sync early on page ${page}: ${error.message}`);
      console.log(`Products already written this run: ${totalWritten}`);
      break;
    }

    const products = Array.isArray(data.products) ? data.products : [];

    if (products.length === 0) {
      console.log(`No more products on page ${page}. Stopping.`);
      break;
    }

    const remainingWrites = maxWrites - totalWritten;
    const result = await upsertProducts(db, products, remainingWrites);

    totalWritten += result.written;
    totalSkipped += result.skipped;

    console.log(
      `Page ${page}: written=${result.written}, skipped=${result.skipped}, totalWritten=${totalWritten}`
    );

    // Small delay to be polite to Open Food Facts.
    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  console.log(`Sync complete.`);
  console.log(`Total written: ${totalWritten}`);
  console.log(`Total skipped: ${totalSkipped}`);
}

main().catch((error) => {
  console.error("Sync failed:", error);
  process.exit(1);
});