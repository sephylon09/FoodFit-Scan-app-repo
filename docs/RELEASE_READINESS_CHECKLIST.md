# FoodFit Scan — Release Readiness Checklist

## App Identity

- App name: FoodFit Scan
- Package: com.sephylon.foodfitscan
- Version: 1.0 (versionCode 1)
- Min SDK: 26 (Android 8.0)
- Target SDK: 37

---

## Permissions Used

| Permission | Purpose |
|---|---|
| `android.permission.CAMERA` | Barcode scanning via CameraX |
| `android.permission.INTERNET` | Product lookup via Open Food Facts API |

No location, contacts, SMS, or microphone permissions.

---

## Data Safety Form (Google Play)

| Category | Answer |
|---|---|
| Data collected | None — no account, no cloud sync, no analytics |
| Data shared with third parties | Barcodes sent to Open Food Facts API (GET request only) |
| Data stored locally | Preferences, scan history, product cache — all on device only |
| Encryption in transit | Yes — HTTPS to Open Food Facts API |
| User deletion | Scan history: clear in app. Preferences: reset in app. Cache: indirectly cleared on uninstall. |

---

## Internal Testing Checklist

- [ ] App installs cleanly on fresh device / emulator
- [ ] Onboarding displays on first launch
- [ ] Skip and complete onboarding paths both work
- [ ] Review onboarding works from Settings
- [ ] Home screen loads correctly after onboarding
- [ ] Camera permission grant flow works
- [ ] Camera permission denied state shows correct UI
- [ ] Barcode scan navigates to ProductDetailScreen
- [ ] Manual barcode entry navigates to ProductDetailScreen
- [ ] Product found with all data displays correctly
- [ ] Product found with missing fields displays "Unknown" / "Not available"
- [ ] Product not found shows correct error state
- [ ] Network error shows correct error state
- [ ] Retry works from error states
- [ ] Scan history updates after each scan
- [ ] History items tap to product detail (FOUND items only)
- [ ] Swipe to delete individual history item
- [ ] Clear all history works with confirmation dialog
- [ ] Settings changes persist across app restart
- [ ] Reset preferences works with confirmation dialog
- [ ] Suitability scoring responds to preference changes
- [ ] Alternatives load for CAUTION/AVOID products
- [ ] Alternative product card taps to its product detail
- [ ] About screen shows all required legal content
- [ ] Back navigation works consistently on all screens

---

## Closed Testing Checklist

- [ ] All internal testing items above pass
- [ ] Tested on at least 3 physical devices (different manufacturers)
- [ ] Tested on small screen (< 5 inch) and large screen
- [ ] Tested in landscape orientation
- [ ] Tested with dark mode (system setting)
- [ ] Tested offline (no internet) — all error states graceful
- [ ] Scanned 10 real supermarket products
- [ ] Scanned product with no image
- [ ] Scanned product with no nutrition data
- [ ] Scanned product with no allergen data
- [ ] Scanned product not in Open Food Facts database
- [ ] Performance: ProductDetailScreen loads within 3 seconds on typical connection
- [ ] App does not crash on back press from any screen
- [ ] App recovers correctly after being backgrounded and resumed

---

## Store Screenshots Needed

1. Home screen with scan button visible
2. Scanner with scanning frame overlay
3. Product Detail — good match suitability card
4. Product Detail — CAUTION/AVOID suitability card
5. Product Detail — nutrition table
6. History screen with items
7. Settings screen
8. Onboarding welcome screen

Recommended: include at least 2 phone + 1 tablet (7-inch) screenshots.

---

## Known Disclaimers

The following disclaimers must be visible in the app before release:

1. **Data disclaimer** (About screen + product detail footer):
   "Food data may be incomplete or inaccurate. Always check the product packaging, especially for allergens."

2. **Medical disclaimer** (About screen):
   "FoodFit Scan is an informational food label helper, not medical or dietary advice."

3. **Suitability disclaimer** (suitability card):
   "Always check the packaging, especially for allergens."

4. **Settings disclaimer**:
   "Preferences are used for informational scoring only. Food data from Open Food Facts may be incomplete."

---

## Open Food Facts Attribution

Required attribution text (About screen):
- "Product data and images are provided by Open Food Facts (openfoodfacts.org)."
- "Open Food Facts data is available under the Open Database Licence (ODbL). Product images are available under Creative Commons Attribution ShareAlike."
- "This app is not affiliated with or endorsed by Open Food Facts."

---

## Privacy Policy

- Status: Draft exists at `docs/PRIVACY_POLICY_DRAFT.md`
- Required before Play Store release: hosted URL for privacy policy field
- Recommended hosting: GitHub Pages or simple static page

---

## Known Release Blockers

- [ ] Privacy policy URL needed (hosted)
- [ ] Store listing copy finalized (see `PLAY_STORE_LISTING_DRAFT.md`)
- [ ] Store screenshots captured on real devices
- [ ] App signing keystore created and stored securely
- [ ] ProGuard/R8 rules reviewed if minification enabled
- [ ] Release build tested (`./gradlew assembleRelease`)

---

## Real Supermarket Scan Test List

Suggested product types to scan before release:

1. Bread (common allergens: gluten, sesame)
2. Milk / dairy product (allergen: milk)
3. Chocolate bar (allergens: milk, nuts)
4. Breakfast cereal (NOVA 4 candidate)
5. Tinned vegetables (minimal ingredients)
6. Juice drink (sugar cap test)
7. Crisps/snacks (salt cap test)
8. Ready meal (NOVA 4, additives test)
9. Peanut butter (allergen: peanuts)
10. Gluten-free product (alternatives test)

Record: barcode, product name, whether found in OFF, suitability result, any UI issues.
