# Phase 2A Manual Testing Checklist

Suitability scoring engine and basic product result integration.

## Pre-conditions

- Install the debug build (`./gradlew assembleDebug`)
- Have a device/emulator with camera access

---

## Suitability Card

- [ ] Product detail screen shows a **suitability card** at the top of the product content
- [ ] Card title is one of: "Good match", "Caution", "Avoid", "Unknown"
- [ ] Card uses appropriate colour: green (Good match), amber (Caution), red (Avoid), grey (Unknown)
- [ ] Card shows up to 3 bullet-point reasons
- [ ] Card shows "+X more" label if there are more than 3 reasons
- [ ] Card always shows: "Always check the packaging, especially for allergens."

---

## Allergen Scenarios

- [ ] Go to **Settings**, enable **Milk** allergen. Scan or look up a product that lists milk in its allergens (e.g., a dairy product). Verify card shows **Avoid** with "Contains Milk".
- [ ] Enable **Peanuts**. Scan a product with peanut traces (not direct allergens). Verify card shows **Caution** with "May contain traces of Peanuts".
- [ ] Enable an allergen (e.g., Sesame). Scan a product where Open Food Facts has no allergen data at all. Verify card shows **Unknown** or **Caution** with "Allergen data is incomplete. Check the packaging."
- [ ] Enable an allergen. Scan a product that has allergen data but does not contain the avoided allergen. Verify card shows **Good match** or no allergen reason.

---

## Additive Scenarios

- [ ] Go to Settings, enable **Flavour enhancers**. Scan a product that contains E621 (monosodium glutamate). Verify card shows **Caution** mentioning flavour enhancers.
- [ ] Enable **Preservatives**. Scan a product with E211 (sodium benzoate). Verify card shows **Caution**.
- [ ] Enable **Artificial sweeteners**. Scan a product with E951 (aspartame). Verify card shows **Caution**.
- [ ] Enable an additive category. Scan a product where Open Food Facts has no additive data. Verify card shows **Unknown** with "Additive data is incomplete."

---

## NOVA / Ultra-processed Scenarios

- [ ] Go to Settings, enable **Avoid ultra-processed (NOVA 4)**. Scan a product reported as NOVA group 4. Verify card shows **Caution** with "NOVA 4: ultra-processed food".
- [ ] Enable Avoid ultra-processed. Scan a product with no NOVA data. Verify card shows **Unknown** with "NOVA processing level is unavailable."
- [ ] Enable Avoid ultra-processed. Scan a product reported as NOVA 1 or 2. Verify no NOVA caution appears.

---

## Nutrition Cap Scenarios

- [ ] Go to Settings, set **Max sugar** to 5g. Scan a product with >5g sugar per 100g. Verify card shows **Caution** with "Sugar is above your limit: X.Xg per 100g".
- [ ] Set **Max salt** to 0.5g. Scan a product with >0.5g salt per 100g. Verify card shows **Caution**.
- [ ] Set **Max saturated fat** to 2g. Scan a product with >2g saturated fat per 100g. Verify card shows **Caution**.
- [ ] Set a nutrition cap. Scan a product where that nutrition value is missing from Open Food Facts. Verify card shows **Unknown** or **Caution** with "X value is unavailable."

---

## Priority / Combined Scenarios

- [ ] Set milk allergen + enable NOVA 4 avoidance. Scan a NOVA 4 product that contains milk. Verify card shows **Avoid** (allergen beats caution).
- [ ] Set a nutrition cap + enable an additive category. Scan a product that exceeds the cap AND has an avoided additive. Verify card shows **Caution** with both reasons listed.

---

## No Preferences

- [ ] Go to Settings. Clear/reset all preferences. Scan any product. Verify card shows **Unknown** with "Set your food preferences to get personalised guidance."
- [ ] On that Unknown card, verify a **"Set preferences"** button appears.
- [ ] Tap "Set preferences" and verify it navigates to Settings screen.

---

## Settings Change Reactivity

- [ ] While viewing a product detail screen, press back, go to Settings, add or remove an allergen, then navigate back to the same product. Verify the suitability card updates to reflect the new preferences.

---

## Regression Checks

- [ ] Product detail still loads from fresh API call
- [ ] Product detail loads from Room cache (airplane mode) with stale-cache banner if stale
- [ ] Scan history still shows recent scans
- [ ] History items navigate to product detail
- [ ] Delete history item works
- [ ] Settings save and persist across app restarts
- [ ] Scanner still opens and scans barcodes
- [ ] Manual barcode entry still works from home screen
- [ ] About screen opens correctly
