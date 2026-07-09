# Phase 3C — Manual Testing Checklist

Firebase Firestore-backed product-name search on the Home screen. Search runs **only**
after the user presses the search icon or the keyboard Search/Enter action — never while
typing.

## Setup

- Firestore collection `product_search_index` is already populated by the daily GitHub
  Actions sync (Open Food Facts → Firestore).
- `app/google-services.json` is present in the app module (project `foodfitscan`).
- Suggested Firestore rules (apply in the Firebase console, NOT from the app):
  ```
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /product_search_index/{doc} {
        allow read: if true;    // public read of the lightweight search index
        allow write: if false;  // clients never write; only the trusted sync job does
      }
    }
  }
  ```
- Build the debug APK: `./gradlew assembleDebug`
- Install on a device/emulator with internet access.

---

## Build & layout

| # | Step | Expected result | Pass/Fail |
|---|------|-----------------|-----------|
| 1 | Run `./gradlew test` | All unit tests pass | |
| 2 | Run `./gradlew assembleDebug` | BUILD SUCCESSFUL, APK produced | |
| 3 | Launch the app (onboarding already completed) | Home shows the search-first layout: small logo + name top-left, About (info) icon top-right | |
| 4 | Look near the top | Prominent rounded search bar with a scanner icon on the **left** and a search icon on the **right** | |
| 5 | Look at the screen center | Centered "FoodFit Scan" + "Search or scan packaged food to check how it fits your nutrition goals" | |
| 6 | Look at the bottom-right | Settings floating action button is shown | |

---

## Navigation buttons

| # | Step | Expected result | Pass/Fail |
|---|------|-----------------|-----------|
| 7 | Tap the barcode scanner icon (left of the search bar) | ScannerScreen (camera) opens | |
| 8 | Return to Home, tap the Settings FAB | SettingsScreen opens | |
| 9 | Return to Home, tap the About (info) icon | AboutScreen opens | |

---

## Input validation

| # | Step | Expected result | Pass/Fail |
|---|------|-----------------|-----------|
| 10 | Leave the field blank and press the search icon | Inline message: "Enter a product name or barcode." No Firebase call | |
| 11 | Type `ab` (2 chars) and press search | Inline message: "Enter at least 3 characters to search." No Firebase call | |
| 12 | Type a valid barcode (e.g. `5449000000996`) and press search | Navigates straight to ProductDetailScreen; Open Food Facts full lookup loads | |
| 13 | Edit the query after a validation message | The message disappears (returns to intro) | |

---

## Product-name search (Firestore)

| # | Step | Expected result | Pass/Fail |
|---|------|-----------------|-----------|
| 14 | Type `nutella` and press the search icon | Brief loading spinner, then a list of matching products | |
| 15 | Inspect a result card | Shows image thumbnail (or placeholder), product name, brand (if available), and barcode in small text | |
| 16 | Type `nutella` and press keyboard **Search/Enter** (not the icon) | Same search runs; keyboard hides | |
| 17 | Type slowly (`n`, `nu`, `nut`, …) WITHOUT pressing search | No search runs while typing; intro stays visible | |
| 18 | Tap a product result | ProductDetailScreen opens for that barcode; Open Food Facts full details load | |
| 19 | Search for gibberish (e.g. `zzzxxqq`) and press search | "No matching products found." | |

---

## Error handling

| # | Step | Expected result | Pass/Fail |
|---|------|-----------------|-----------|
| 20 | Enable airplane mode, type `nutella`, press search | "Could not search products. Check your connection." with a **Retry** button | |
| 21 | Disable airplane mode, tap **Retry** | The same query re-runs and results appear | |

---

## Regression (existing features still work)

| # | Step | Expected result | Pass/Fail |
|---|------|-----------------|-----------|
| 22 | Scan a barcode from the scanner | ProductDetail loads as before | |
| 23 | Open a product and review suitability / alternatives | Works as before | |
| 24 | Open Settings and change preferences | Saved and applied as before | |
| 25 | History backend (route retained) | Unaffected by search changes | |
| 26 | Re-run onboarding from Settings | Works as before | |

---

## Notes

- Search uses a single Firestore query:
  `collection("product_search_index").whereArrayContains("searchPrefixes", <firstNormalizedWord>).limit(20)`,
  then ranks results client-side (contains-match first, shorter names, image present,
  alphabetical).
- The app performs **read-only** access to `product_search_index`; it never writes to
  Firestore and holds no service-account key.
