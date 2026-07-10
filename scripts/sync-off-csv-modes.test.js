const test = require("node:test");
const assert = require("node:assert/strict");

const {
  COLLECTION_NAME,
  COUNTRY_ROTATION,
  SYNC_STATE_COLLECTION,
  SYNC_STATE_DOC,
  IMAGE_BACKFILL_STATE_DOC,
  MODE_NORMAL,
  MODE_RESET_SINGAPORE,
  MODE_IMAGE_BACKFILL,
  MODES,
  RESET_CONFIRMATION,
  MAX_CHUNKS_PER_RUN,
  parseMode,
  isResetConfirmed,
  getChunksPerRun,
  deleteCollectionInBatches,
  buildSingaporeResetState,
  runResetToSingapore,
  buildImageBackfillUpdate,
  getImageBackfillState,
  rowMatchesAnyRotationCountry,
  lineMayMatchRotationCountry,
} = require("./sync-off-csv-to-firestore");

// ── Fake Firestore ──────────────────────────────────────────────────────────
//
// Enough of the firebase-admin surface for the reset path: paginated collection reads,
// write batches, and document get/set/delete. Records every batch so tests can assert the
// deletion was actually chunked rather than done in one giant commit.

function createFakeDb({ docs = {}, batchLimit = Infinity } = {}) {
  const collections = {};
  for (const [name, values] of Object.entries(docs)) {
    collections[name] = new Map(Object.entries(values));
  }

  const committedBatches = [];
  const queryLimits = [];

  const docRef = (collectionName, id) => ({
    id,
    _collection: collectionName,
    async get() {
      const store = collections[collectionName];
      const data = store ? store.get(id) : undefined;
      return {
        exists: data !== undefined,
        data: () => data,
        get: (field) => (data === undefined ? undefined : data[field]),
      };
    },
    async set(value, options) {
      collections[collectionName] = collections[collectionName] || new Map();
      const store = collections[collectionName];
      const previous = store.get(id);
      store.set(id, options && options.merge ? { ...(previous || {}), ...value } : value);
    },
    async delete() {
      const store = collections[collectionName];
      if (store) store.delete(id);
    },
  });

  return {
    _collections: collections,
    committedBatches,
    queryLimits,
    collection(collectionName) {
      return {
        doc: (id) => docRef(collectionName, id),
        limit(n) {
          queryLimits.push(n);
          return {
            async get() {
              const store = collections[collectionName] || new Map();
              const ids = Array.from(store.keys()).slice(0, n);
              return {
                size: ids.length,
                empty: ids.length === 0,
                docs: ids.map((id) => ({ id, ref: docRef(collectionName, id) })),
              };
            },
          };
        },
      };
    },
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push({ type: "delete", ref });
        },
        set(ref, value, options) {
          ops.push({ type: "set", ref, value, options });
        },
        async commit() {
          if (ops.length > batchLimit) {
            throw new Error(`Batch too large: ${ops.length} > ${batchLimit}`);
          }
          for (const op of ops) {
            if (op.type === "delete") {
              const store = collections[op.ref._collection];
              if (store) store.delete(op.ref.id);
            } else {
              await op.ref.set(op.value, op.options);
            }
          }
          committedBatches.push(ops.map((op) => ({ type: op.type, id: op.ref.id })));
        },
      };
    },
  };
}

function seedProducts(count) {
  const products = {};
  for (let i = 0; i < count; i++) {
    products[`barcode-${i}`] = { barcode: `barcode-${i}`, name: `Product ${i}` };
  }
  return products;
}

// ── parseMode ───────────────────────────────────────────────────────────────

test("parseMode defaults to normal when the mode input is absent or blank", () => {
  assert.equal(parseMode(undefined), MODE_NORMAL);
  assert.equal(parseMode(null), MODE_NORMAL);
  assert.equal(parseMode(""), MODE_NORMAL);
  assert.equal(parseMode("   "), MODE_NORMAL);
});

test("parseMode accepts each documented mode", () => {
  assert.deepEqual(MODES, [MODE_NORMAL, MODE_RESET_SINGAPORE, MODE_IMAGE_BACKFILL]);
  for (const mode of MODES) {
    assert.equal(parseMode(mode), mode);
  }
});

test("parseMode tolerates surrounding whitespace and casing", () => {
  assert.equal(parseMode("  Reset-Singapore "), MODE_RESET_SINGAPORE);
  assert.equal(parseMode("IMAGE-BACKFILL"), MODE_IMAGE_BACKFILL);
});

test("parseMode throws on an unknown mode instead of silently syncing", () => {
  assert.throws(() => parseMode("delete-everything"), /Unknown sync mode/);
  assert.throws(() => parseMode("reset"), /Unknown sync mode/);
});

// ── isResetConfirmed ────────────────────────────────────────────────────────

test("isResetConfirmed only accepts the exact string RESET", () => {
  assert.equal(RESET_CONFIRMATION, "RESET");
  assert.equal(isResetConfirmed("RESET"), true);
});

test("isResetConfirmed rejects near-misses, blanks, and missing values", () => {
  for (const value of ["reset", "Reset", " RESET", "RESET ", "", "  ", "yes", undefined, null]) {
    assert.equal(isResetConfirmed(value), false, `expected ${JSON.stringify(value)} to be rejected`);
  }
});

// ── chunk cap ───────────────────────────────────────────────────────────────

test("chunks cap still clamps to MAX_CHUNKS_PER_RUN and defaults to 1", () => {
  const original = process.env.CSV_SYNC_CHUNKS_PER_RUN;
  try {
    delete process.env.CSV_SYNC_CHUNKS_PER_RUN;
    assert.equal(getChunksPerRun(), 1);

    process.env.CSV_SYNC_CHUNKS_PER_RUN = "6";
    assert.equal(getChunksPerRun(), 6);

    process.env.CSV_SYNC_CHUNKS_PER_RUN = "999";
    assert.equal(getChunksPerRun(), MAX_CHUNKS_PER_RUN);

    process.env.CSV_SYNC_CHUNKS_PER_RUN = "0";
    assert.equal(getChunksPerRun(), 1);

    process.env.CSV_SYNC_CHUNKS_PER_RUN = "not-a-number";
    assert.equal(getChunksPerRun(), 1);
  } finally {
    if (original === undefined) delete process.env.CSV_SYNC_CHUNKS_PER_RUN;
    else process.env.CSV_SYNC_CHUNKS_PER_RUN = original;
  }
});

// ── buildSingaporeResetState ────────────────────────────────────────────────

test("buildSingaporeResetState points the rotation at Singapore, row 0", () => {
  const state = buildSingaporeResetState(1700000000000);

  assert.equal(state.currentCountryIndex, 0);
  assert.equal(state.currentCountryTag, "en:singapore");
  assert.equal(state.currentCountryTag, COUNTRY_ROTATION[0]);
  assert.equal(state.currentCountryLastProcessedRow, 0);
  assert.equal(state.totalSavedAllCountries, 0);
  assert.deepEqual(state.countryProgress, {
    "en:singapore": { lastProcessedRow: 0, totalSaved: 0, completedFullPass: false },
  });
  assert.equal(state.lastResult, "reset_to_singapore");
  assert.equal(state.lastRunAt, 1700000000000);
});

test("buildSingaporeResetState carries no progress for any other country", () => {
  const state = buildSingaporeResetState();
  assert.deepEqual(Object.keys(state.countryProgress), ["en:singapore"]);
});

// ── deleteCollectionInBatches ───────────────────────────────────────────────

test("deleteCollectionInBatches empties the collection and reports the count", async () => {
  const db = createFakeDb({ docs: { [COLLECTION_NAME]: seedProducts(7) } });

  const deleted = await deleteCollectionInBatches(db, COLLECTION_NAME, 3);

  assert.equal(deleted, 7);
  assert.equal(db._collections[COLLECTION_NAME].size, 0);
});

test("deleteCollectionInBatches paginates: bounded pages, never one huge commit", async () => {
  const batchSize = 3;
  // batchLimit makes an oversized commit throw, so a non-paginated implementation fails here.
  const db = createFakeDb({ docs: { [COLLECTION_NAME]: seedProducts(10) }, batchLimit: batchSize });

  const deleted = await deleteCollectionInBatches(db, COLLECTION_NAME, batchSize);

  assert.equal(deleted, 10);
  assert.deepEqual(db.queryLimits, [3, 3, 3, 3]); // 3 + 3 + 3 + 1
  assert.deepEqual(
    db.committedBatches.map((ops) => ops.length),
    [3, 3, 3, 1]
  );
  assert.ok(db.committedBatches.every((ops) => ops.every((op) => op.type === "delete")));
});

test("deleteCollectionInBatches is a no-op on an empty collection", async () => {
  const db = createFakeDb({ docs: { [COLLECTION_NAME]: {} } });

  const deleted = await deleteCollectionInBatches(db, COLLECTION_NAME, 300);

  assert.equal(deleted, 0);
  assert.equal(db.committedBatches.length, 0);
});

// ── runResetToSingapore ─────────────────────────────────────────────────────

test("reset-singapore refuses to run without confirm_reset = RESET and deletes nothing", async () => {
  for (const confirmation of ["", "reset", "Reset", "RESET ", undefined]) {
    const db = createFakeDb({
      docs: {
        [COLLECTION_NAME]: seedProducts(4),
        [SYNC_STATE_COLLECTION]: { [SYNC_STATE_DOC]: { currentCountryIndex: 5 } },
      },
    });

    await assert.rejects(
      () => runResetToSingapore(db, confirmation),
      /Refusing to reset/,
      `expected ${JSON.stringify(confirmation)} to be refused`
    );

    assert.equal(db._collections[COLLECTION_NAME].size, 4, "no product was deleted");
    assert.equal(db.committedBatches.length, 0, "no batch was committed");
    assert.deepEqual(db._collections[SYNC_STATE_COLLECTION].get(SYNC_STATE_DOC), {
      currentCountryIndex: 5,
    });
  }
});

test("reset-singapore deletes every product and rewrites the rotation cursor", async () => {
  const db = createFakeDb({
    docs: {
      [COLLECTION_NAME]: seedProducts(5),
      [SYNC_STATE_COLLECTION]: {
        [SYNC_STATE_DOC]: {
          currentCountryIndex: 4,
          currentCountryTag: "en:japan",
          totalSavedAllCountries: 1234,
          countryProgress: {
            "en:singapore": { lastProcessedRow: 900000, totalSaved: 300, completedFullPass: true },
            "en:japan": { lastProcessedRow: 42, totalSaved: 10, completedFullPass: false },
          },
        },
      },
    },
  });

  const { deletedCount } = await runResetToSingapore(db, "RESET");

  assert.equal(deletedCount, 5);
  assert.equal(db._collections[COLLECTION_NAME].size, 0);

  const state = db._collections[SYNC_STATE_COLLECTION].get(SYNC_STATE_DOC);
  assert.equal(state.currentCountryIndex, 0);
  assert.equal(state.currentCountryTag, "en:singapore");
  assert.equal(state.currentCountryLastProcessedRow, 0);
  assert.equal(state.totalSavedAllCountries, 0);
  assert.equal(state.lastResult, "reset_to_singapore");
  assert.equal(typeof state.lastRunAt, "number");

  // Written without merge: the old Japan cursor and the old Singapore row are gone.
  assert.deepEqual(Object.keys(state.countryProgress), ["en:singapore"]);
  assert.deepEqual(state.countryProgress["en:singapore"], {
    lastProcessedRow: 0,
    totalSaved: 0,
    completedFullPass: false,
  });
});

test("reset-singapore clears the image-backfill state doc too", async () => {
  const db = createFakeDb({
    docs: {
      [COLLECTION_NAME]: seedProducts(2),
      [SYNC_STATE_COLLECTION]: {
        [SYNC_STATE_DOC]: { currentCountryIndex: 2 },
        [IMAGE_BACKFILL_STATE_DOC]: { lastProcessedRow: 500000, totalUpdated: 40 },
      },
    },
  });

  await runResetToSingapore(db, "RESET");

  assert.equal(db._collections[SYNC_STATE_COLLECTION].has(IMAGE_BACKFILL_STATE_DOC), false);
});

test("reset-singapore tolerates an already-empty collection and a missing backfill doc", async () => {
  const db = createFakeDb({ docs: { [COLLECTION_NAME]: {}, [SYNC_STATE_COLLECTION]: {} } });

  const { deletedCount } = await runResetToSingapore(db, "RESET");

  assert.equal(deletedCount, 0);
  const state = db._collections[SYNC_STATE_COLLECTION].get(SYNC_STATE_DOC);
  assert.equal(state.currentCountryTag, "en:singapore");
});

// ── image-backfill state isolation ──────────────────────────────────────────

test("the image-backfill cursor lives in its own state doc, not the rotation doc", () => {
  assert.equal(SYNC_STATE_DOC, "open_food_facts_country_rotation");
  assert.equal(IMAGE_BACKFILL_STATE_DOC, "open_food_facts_image_backfill");
  assert.notEqual(SYNC_STATE_DOC, IMAGE_BACKFILL_STATE_DOC);
});

test("image-backfill starts from row 0 when no backfill state exists", async () => {
  const db = createFakeDb({ docs: { [SYNC_STATE_COLLECTION]: {} } });

  const state = await getImageBackfillState(db);

  assert.deepEqual(state, { lastProcessedRow: 0, totalUpdated: 0, completedFullPass: false });
});

test("image-backfill resumes from its saved cursor", async () => {
  const db = createFakeDb({
    docs: {
      [SYNC_STATE_COLLECTION]: {
        [IMAGE_BACKFILL_STATE_DOC]: {
          lastProcessedRow: 750000,
          totalUpdated: 120,
          completedFullPass: false,
        },
      },
    },
  });

  const state = await getImageBackfillState(db);

  assert.equal(state.lastProcessedRow, 750000);
  assert.equal(state.totalUpdated, 120);
});

test("image-backfill ignores the country-rotation cursor entirely", async () => {
  const db = createFakeDb({
    docs: {
      [SYNC_STATE_COLLECTION]: {
        [SYNC_STATE_DOC]: { currentCountryIndex: 6, countryProgress: { "en:china": { lastProcessedRow: 99 } } },
      },
    },
  });

  const state = await getImageBackfillState(db);

  assert.equal(state.lastProcessedRow, 0, "rotation progress must not seed the backfill cursor");
});

// ── buildImageBackfillUpdate ────────────────────────────────────────────────

test("image-backfill update carries the image, timestamps, and source only", () => {
  const update = buildImageBackfillUpdate("https://img/front.jpg", 1700000000000);

  assert.deepEqual(update, {
    imageUrl: "https://img/front.jpg",
    updatedAt: 1700000000000,
    imageBackfilledAt: 1700000000000,
    imageSource: "openfoodfacts-csv",
  });
});

test("image-backfill update never carries a null imageUrl", () => {
  // The caller only builds an update once getBestImageUrl returned a URL, so imageUrl is
  // always a non-empty string; assert the shape can never degrade into a null write.
  const update = buildImageBackfillUpdate("https://img/small.jpg");
  assert.equal(typeof update.imageUrl, "string");
  assert.ok(update.imageUrl.length > 0);
  assert.equal(update.imageUrl === null, false);
});

// ── rotation-country pre-filters (image-backfill read budget) ───────────────

test("rowMatchesAnyRotationCountry accepts a row tagged with any indexed country", () => {
  assert.equal(rowMatchesAnyRotationCountry({ countries_tags: "en:france,en:belgium" }), true);
  assert.equal(rowMatchesAnyRotationCountry({ countries_tags: "en:singapore" }), true);
});

test("rowMatchesAnyRotationCountry rejects rows no country in the rotation covers", () => {
  assert.equal(rowMatchesAnyRotationCountry({ countries_tags: "en:belgium,en:spain" }), false);
  assert.equal(rowMatchesAnyRotationCountry({ countries_tags: "" }), false);
  assert.equal(rowMatchesAnyRotationCountry({}), false);
});

test("lineMayMatchRotationCountry is a pre-filter with no false negatives", () => {
  assert.equal(lineMayMatchRotationCountry("123\tSnack\ten:singapore"), true);
  assert.equal(lineMayMatchRotationCountry("123\tSnack\ten:belgium"), false);
  // False positives are allowed: rowMatchesAnyRotationCountry is the authoritative check.
  assert.equal(lineMayMatchRotationCountry("123\ten:japanese-cuisine\ten:belgium"), true);
  assert.equal(rowMatchesAnyRotationCountry({ countries_tags: "en:belgium" }), false);
});
