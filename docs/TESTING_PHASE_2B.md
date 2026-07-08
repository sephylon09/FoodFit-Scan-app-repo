# Phase 2B Manual Testing Checklist — Healthier Alternatives MVP

## Prerequisites

- Run `./gradlew assembleDebug` to build.
- Run `./gradlew test` to verify all unit tests pass.
- Install on a physical device or emulator with an active internet connection (use Wi-Fi).

---

## Core Alternatives Flow

### 1. Product rated CAUTION or AVOID

- [ ] Scan (or manually enter) a product barcode for a product that receives a **CAUTION** or **AVOID** rating.
- [ ] Verify the **Alternatives** card appears below the product information.
- [ ] Verify the card shows a **"Find better options"** button.

### 2. Alternatives do not load automatically

- [ ] Open a CAUTION/AVOID product detail screen.
- [ ] Verify that **no alternative products appear automatically** before tapping the button.
- [ ] Check that the alternatives state is idle (button visible, no spinner, no list).

### 3. Tapping "Find better options"

- [ ] Tap the **"Find better options"** button.
- [ ] Verify a loading spinner appears with text "Searching for alternatives…".
- [ ] Wait for results to load.
- [ ] Verify up to 5 alternative product cards appear.
- [ ] Each card should show: product name, brand (if available), Nutri-Score badge, NOVA badge, and suitability indicator.
- [ ] Verify the disclaimer text "Potential better options based on available Open Food Facts data." is shown.

### 4. Navigate to an alternative

- [ ] Tap an alternative product card.
- [ ] Verify the app navigates to **ProductDetailScreen** for that alternative product.
- [ ] Verify the product detail loads correctly (name, nutrition, etc.).
- [ ] Verify the **Back** button returns to the previous product detail screen.

---

## Edge Cases

### 5. Product with no category data

- [ ] Find a product that has no `categories_tags` in Open Food Facts.
- [ ] If suitability is CAUTION/AVOID, verify the Alternatives card shows:
  "No product category available to find similar options."
- [ ] Verify no button is shown (or it is disabled).

### 6. Airplane mode / network error

- [ ] Enable airplane mode on the device.
- [ ] Open a CAUTION/AVOID product (may load from cache).
- [ ] Tap "Find better options".
- [ ] Verify the alternatives card shows "Could not load alternatives." with a **Retry** button.
- [ ] Re-enable internet. Tap **Retry**.
- [ ] Verify alternatives load successfully.

### 7. Product rated GOOD_MATCH

- [ ] Scan or open a product that receives a **GOOD_MATCH** rating.
- [ ] Verify the Alternatives card shows: "This already looks like a good match for your preferences."
- [ ] Verify **no "Find better options" button** appears.

### 8. Product rated UNKNOWN (no preferences set)

- [ ] Clear all food preferences in Settings.
- [ ] Open any product (it should be rated UNKNOWN).
- [ ] Verify the Alternatives card shows: "Set food preferences to get personalised alternatives."

### 9. Empty results

- [ ] For a very niche product (unusual category), tap "Find better options".
- [ ] If no suitable alternatives are found, verify the message:
  "No better options found for this category yet."

### 10. Alternatives don't include the current product

- [ ] When alternatives load, verify the **currently viewed product** does not appear in the results list.

---

## Regression Checks

- [ ] **Scanner**: Scan a new product from the scanner screen. Verify it navigates to ProductDetailScreen normally.
- [ ] **Manual entry**: Enter a barcode manually from the Home screen. Verify ProductDetailScreen loads.
- [ ] **History**: Open Scan History. Verify previously scanned products still appear. Tap one to navigate to ProductDetailScreen.
- [ ] **Settings**: Navigate to Settings. Change food preferences. Return to a product detail screen and verify the suitability card updates live.
- [ ] **Stale cache banner**: If a product is loaded from stale cache, verify the banner still appears at the top.
- [ ] **App crash check**: Ensure the app does not crash if search returns products with missing or null fields (name, barcode, nutriments, etc.).

---

## Notes

- Alternatives are scored using the same `SuitabilityScorer` as the main product, so results respect your current food preferences.
- Search uses the OFF `/api/v2/search` endpoint with the `categories_tags` filter from the product's own category data.
- The page size is 20 candidates; after filtering and ranking, at most 5 alternatives are shown.
- AVOID-rated alternatives are excluded from the list even if they appear in search results.
- Data accuracy depends on Open Food Facts community contributions. Always verify against product packaging.
