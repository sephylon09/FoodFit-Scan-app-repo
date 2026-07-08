# Phase 3B — Manual Testing Checklist

This phase has two parts:
- **Part A — Home redesign + search entry UI** (below)
- **Part B — Nutrition display preferences** (further down)

---

# Part A — Home Redesign + Search Entry

## Setup

- Build the debug APK: `./gradlew assembleDebug`
- Install on a device/emulator. Internet is needed only when opening a product.

---

## Home layout

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| A1 | Launch the app (onboarding already completed) | Home shows the new layout: small logo + name top-left, an info (About) icon top-right | |
| A2 | Look near the top | A prominent, rounded search bar is shown | |
| A3 | Check the search bar placeholder | Reads "Search food or barcode" | |
| A4 | Check the search bar icons | Barcode scanner icon on the **left**, search icon on the **right** | |
| A5 | Look at the screen center | Centered "FoodFit Scan" and "Search or scan packaged food to check how it fits your nutrition goals" | |
| A6 | Look at the bottom-right | A **Settings** floating action button is shown | |
| A7 | Confirm removals | No "Enter Barcode Manually" button, no "History" button/card, no old large "Scan Barcode" button | |

---

## Scanner icon

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| A8 | Tap the barcode scanner icon on the left of the search bar | ScannerScreen opens (camera) | |
| A9 | Return to Home | Home layout is intact | |

---

## Search behaviour (search only triggers on press / keyboard action)

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| A10 | Leave the search field blank and tap the search icon | Inline message "Enter a product name or barcode." appears | |
| A11 | Type a few letters (do NOT press search) | No search happens while typing; no navigation, no message | |
| A12 | Type a valid barcode (e.g. `5449000000996`) and tap the search icon | ProductDetailScreen opens for that barcode | |
| A13 | Return Home, type a valid barcode, press the keyboard **Search** action | ProductDetailScreen opens for that barcode | |
| A14 | Type a non-barcode product name (e.g. `chocolate`) and tap search | Message "Product name search will be added in a later phase." appears; no network call | |
| A15 | After a message is shown, edit the query text | The message disappears as you edit | |

---

## Settings FAB

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| A16 | Tap the Settings floating button (bottom-right) | SettingsScreen opens | |
| A17 | Go back to Home | Home layout is intact | |

---

## Accessibility & small screens

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| A18 | Enable TalkBack and focus the search bar icons and FAB | Content descriptions read: "Scan barcode", "Search", "Open settings", "About FoodFit Scan" | |
| A19 | Open the keyboard by tapping the search field on a small screen | Search bar stays visible; keyboard does not cover the search input | |

---

## Regression — other screens still work

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| A20 | Open the About icon (top-right) | AboutScreen opens | |
| A21 | Complete a scan from the scanner | ProductDetailScreen opens with suitability + nutrition cards | |
| A22 | Manual barcode entry inside ScannerScreen | Still available and works (manual entry was only removed from Home) | |
| A23 | Onboarding (fresh install) | Still runs before Home as before | |
| A24 | History screen/backend | Route still exists (no longer linked from Home; reachable if navigated elsewhere) | |

---

# Part B — Nutrition Display Preferences

## Setup

- Build the debug APK: `./gradlew assembleDebug`
- Install on a device/emulator with internet access (needed to look up a product).
- No fresh install required — this feature lives entirely in **Settings**.

---

## Nutrition fields section in Settings

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 1 | Open the app and go to **Settings** | Settings screen opens | |
| 2 | Scroll to **"Nutrition fields to show"** | Section is visible below "Nutrition Caps (per 100g)" | |
| 3 | Read the helper text | Shows "Choose which nutrition values appear on product result screens." | |
| 4 | Check the list of fields | 9 rows: Energy / calories, Fat, Saturated fat, Carbohydrates, Sugars, Fiber, Protein, Salt, Sodium | |
| 5 | Check default selection (fresh install / after reset) | Energy / calories, Saturated fat, Sugars, Protein, Salt are checked | |

---

## Selecting a subset and confirming the product screen

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 6 | Deselect everything except **Protein** and **Carbohydrates** | Only Protein and Carbohydrates remain checked | |
| 7 | Go back, scan or manually enter a product barcode (e.g. `5449000000996`) | Product detail screen opens | |
| 8 | Look at the **Nutrition per 100 g** card | Only **Protein** and **Carbohydrates** rows are shown | |
| 9 | Confirm the small caption | Card shows "Showing your selected nutrition fields." | |
| 10 | Confirm the shortcut | Card shows an **"Edit nutrition fields"** button | |
| 11 | Tap **"Edit nutrition fields"** | Navigates to the Settings screen | |

---

## Changing selection updates the product screen

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 12 | In Settings, select **Sugars**, **Salt**, and **Saturated fat** (in addition to current) | Checkboxes update | |
| 13 | Return to the product detail screen (re-open the product) | Nutrition card now also shows Sugars, Salt, and Saturated fat | |
| 14 | Fields always appear in a consistent order | Order follows the canonical list (Energy, Fat, Saturated fat, Carbohydrates, Sugars, Fiber, Protein, Salt, Sodium) regardless of selection order | |

---

## Missing values

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 15 | Open a product whose data is missing one of your selected fields (e.g. Fiber often missing) | Selected field row still appears | |
| 16 | Confirm missing value text | Missing value shows **"Not available"** | |
| 17 | Open a product with no nutrition data at all | Nutrition card is still shown with all selected fields as "Not available" | |

---

## At-least-one-field validation

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 18 | In Settings, deselect fields down to a single remaining field | One field stays checked | |
| 19 | Try to deselect the **last** remaining field | Field stays checked; message **"Select at least one nutrition field."** appears | |
| 20 | Select any other field | Warning message disappears | |

---

## Reset to default

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 21 | Change the selection to something non-default | Selection changes | |
| 22 | Tap **"Reset to default"** under the section | Selection returns to Energy / calories, Saturated fat, Sugars, Protein, Salt | |
| 23 | Re-open a product | Nutrition card reflects the default 5 fields | |

---

## Persistence

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 24 | Set a custom selection, kill and relaunch the app | Selection is remembered in Settings | |
| 25 | Re-open a product after relaunch | Nutrition card still reflects the saved selection | |

---

## Suitability scoring is unaffected

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 26 | Note the suitability card verdict (Good match / Caution / Avoid) for a product | Record the level and reasons | |
| 27 | Change nutrition display fields in Settings | — | |
| 28 | Re-open the same product | Suitability level and reasons are **unchanged** by the display selection | |
| 29 | Confirm nutrition caps still drive scoring | Changing a nutrition **cap** (not a display field) still changes suitability as before | |

---

## Regression — other screens still work

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 30 | Open **History** | History list loads, swipe-to-delete works | |
| 31 | Open **Scanner** | Camera scanner works and scans a barcode | |
| 32 | Open **Settings** | Allergens, additives, NOVA 4, and nutrition caps still save correctly | |
| 33 | Trigger **Alternatives** on a Caution/Avoid product | Alternatives still load and open when tapped | |

---

## Known limitations

- The nutrition display selection controls **display only** and never affects suitability scoring, allergen/additive/NOVA evaluation, or alternatives ranking.
- Alternative product cards are not customised by this selection in Phase 3B (kept simple by design).
- Available fields are limited to the Open Food Facts nutriments already mapped: energy (kcal), fat, saturated fat, carbohydrates, sugars, fiber, protein, salt, sodium.
- Not added in this phase: charts, favourites, onboarding step for nutrition fields.
