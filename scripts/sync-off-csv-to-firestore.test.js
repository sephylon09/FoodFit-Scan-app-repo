const test = require("node:test");
const assert = require("node:assert/strict");

const {
  IMAGE_COLUMN_PRIORITY,
  getBestImageUrl,
  compactProduct,
  buildWritePayload,
  buildColumnIndexes,
  parseRow,
} = require("./sync-off-csv-to-firestore");

// ── getBestImageUrl ─────────────────────────────────────────────────────────

test("getBestImageUrl chooses image_front_url first", () => {
  const row = {
    image_front_url: "https://img/front.jpg",
    image_url: "https://img/plain.jpg",
    image_small_url: "https://img/small.jpg",
  };
  assert.equal(getBestImageUrl(row), "https://img/front.jpg");
});

test("getBestImageUrl falls back to image_url when image_front_url is missing", () => {
  const row = {
    image_url: "https://img/plain.jpg",
    image_small_url: "https://img/small.jpg",
  };
  assert.equal(getBestImageUrl(row), "https://img/plain.jpg");
});

test("getBestImageUrl falls back to image_small_url", () => {
  const row = {
    image_front_url: "",
    image_url: "   ",
    image_front_small_url: undefined,
    image_small_url: "https://img/small.jpg",
  };
  assert.equal(getBestImageUrl(row), "https://img/small.jpg");
});

test("getBestImageUrl prefers front thumb over plain thumb", () => {
  const row = {
    image_front_thumb_url: "https://img/front-thumb.jpg",
    image_thumb_url: "https://img/thumb.jpg",
  };
  assert.equal(getBestImageUrl(row), "https://img/front-thumb.jpg");
});

test("getBestImageUrl returns null when no image columns exist", () => {
  assert.equal(getBestImageUrl({}), null);
  assert.equal(getBestImageUrl({ product_name: "Thing" }), null);
});

test("getBestImageUrl treats blank values as missing and returns null", () => {
  const row = { image_front_url: "  ", image_url: "" };
  assert.equal(getBestImageUrl(row), null);
});

test("getBestImageUrl trims surrounding whitespace", () => {
  const row = { image_front_url: "  https://img/front.jpg  " };
  assert.equal(getBestImageUrl(row), "https://img/front.jpg");
});

test("getBestImageUrl skips values that are not http(s) URLs", () => {
  const row = {
    image_front_url: "not-a-url",
    image_url: "https://img/ok.jpg",
  };
  assert.equal(getBestImageUrl(row), "https://img/ok.jpg");
});

// ── compactProduct image mapping ────────────────────────────────────────────

const baseRow = {
  code: "5000159407236",
  product_name: "KitKat",
  brands: "Nestle",
  countries_tags: "en:singapore,en:malaysia",
  categories_tags: "en:snacks,en:biscuits",
  last_modified_t: "1700000000",
};

test("compactProduct sets imageUrl from the best available image column", () => {
  const product = compactProduct(
    { ...baseRow, image_url: " https://img/plain.jpg " },
    "en:singapore"
  );
  assert.equal(product.imageUrl, "https://img/plain.jpg");
});

test("compactProduct sets imageUrl null when no image data exists", () => {
  const product = compactProduct({ ...baseRow }, "en:singapore");
  assert.equal(product.imageUrl, null);
});

test("compactProduct keeps the expected document structure", () => {
  const product = compactProduct(
    { ...baseRow, image_front_url: "https://img/front.jpg" },
    "en:singapore"
  );
  assert.deepEqual(Object.keys(product).sort(), [
    "barcode",
    "brand",
    "categoriesTags",
    "countryTags",
    "imageUrl",
    "lastModifiedFromApi",
    "matchedCountryTag",
    "name",
    "searchName",
    "searchPrefixes",
    "source",
    "updatedAt",
  ]);
  assert.equal(product.barcode, "5000159407236");
  assert.equal(product.imageUrl, "https://img/front.jpg");
});

// ── buildWritePayload: never overwrite an existing image with null ─────────

test("write payload omits imageUrl when the row has no image, so merge keeps existing images", () => {
  const compact = compactProduct({ ...baseRow }, "en:singapore");
  assert.equal(compact.imageUrl, null);

  const payload = buildWritePayload(compact);

  assert.equal("imageUrl" in payload, false);
});

test("write payload includes imageUrl when the row has one", () => {
  const compact = compactProduct(
    { ...baseRow, image_url: "https://img/plain.jpg" },
    "en:singapore"
  );

  const payload = buildWritePayload(compact);

  assert.equal(payload.imageUrl, "https://img/plain.jpg");
});

test("write payload leaves all other document fields intact", () => {
  const compact = compactProduct({ ...baseRow }, "en:singapore");

  const payload = buildWritePayload(compact);

  const { imageUrl, ...compactWithoutImage } = compact;
  assert.deepEqual(payload, compactWithoutImage);
  // The source object is not mutated.
  assert.equal("imageUrl" in compact, true);
});

// ── header/parse integration for alternate image columns ───────────────────

test("parseRow reads whichever image columns the header provides", () => {
  const header = ["code", "product_name", "countries_tags", "image_url", "image_small_url"].join("\t");
  const indexes = buildColumnIndexes(header);

  const line = ["111", "Rice Crackers", "en:singapore", "https://img/plain.jpg", "https://img/small.jpg"].join("\t");
  const row = parseRow(line, indexes);

  assert.equal(row.image_front_url, undefined);
  assert.equal(row.image_url, "https://img/plain.jpg");
  assert.equal(row.image_small_url, "https://img/small.jpg");
  assert.equal(getBestImageUrl(row), "https://img/plain.jpg");
});

test("buildColumnIndexes tolerates a header with no image columns", () => {
  const header = ["code", "product_name", "countries_tags"].join("\t");
  const indexes = buildColumnIndexes(header);

  const row = parseRow(["222", "Plain Tea", "en:singapore"].join("\t"), indexes);
  assert.equal(getBestImageUrl(row), null);
});

test("IMAGE_COLUMN_PRIORITY matches the documented order", () => {
  assert.deepEqual(IMAGE_COLUMN_PRIORITY, [
    "image_front_url",
    "image_url",
    "image_front_small_url",
    "image_small_url",
    "image_front_thumb_url",
    "image_thumb_url",
  ]);
});
