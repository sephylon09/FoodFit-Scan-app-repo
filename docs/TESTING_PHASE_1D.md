# Phase 1D Manual Test Checklist — DataStore Preferences & Settings Screen

## Prerequisites
- App installed on device or emulator (minSdk 26)
- Fresh install or cleared app data recommended for a clean test

---

## Settings Screen — Basic Navigation

- [ ] Open the app and tap **Settings** from the home screen
- [ ] The Settings screen appears with sections: Allergens, Additives, Ultra-Processed, Nutrition Caps, and a Reset button
- [ ] Tap the back arrow — returns to home screen

---

## Allergens

- [ ] Open Settings → scroll to **Allergens to Avoid**
- [ ] Select **Milk** and **Peanuts** by tapping their checkboxes
- [ ] Leave Settings (back) and return to Settings
- [ ] Confirm **Milk** and **Peanuts** are still checked after returning
- [ ] Uncheck **Milk** — deselection persists after re-entering Settings
- [ ] Helper text "Allergen data may be incomplete. Always check packaging." is visible

---

## Additives

- [ ] Open Settings → scroll to **Additives to Avoid**
- [ ] Select **Preservatives** and **Artificial colours**
- [ ] Leave and re-enter Settings
- [ ] Confirm selections persist
- [ ] Deselect one additive and confirm deselection persists

---

## Ultra-Processed Foods

- [ ] Open Settings → find **Ultra-Processed Foods** section
- [ ] Toggle **Avoid NOVA 4 ultra-processed foods** to ON
- [ ] Leave and re-enter Settings
- [ ] Confirm toggle is still ON
- [ ] Toggle OFF — confirm OFF state persists after re-entering Settings
- [ ] Helper text "Used later to warn when a scanned product is NOVA 4." is visible

---

## Nutrition Caps

- [ ] Open Settings → scroll to **Nutrition Caps (per 100g)**
- [ ] Enter **10** in Max sugar field — no error shown
- [ ] Enter **1.5** in Max salt field — no error shown
- [ ] Enter **3.0** in Max saturated fat field — no error shown
- [ ] Leave and re-enter Settings
- [ ] Confirm all three values persisted

### Invalid input validation
- [ ] Clear Max sugar and enter **abc** — inline error "Enter a valid positive number" appears
- [ ] Enter **-5** — inline error appears (negative value rejected)
- [ ] Enter **0** — no error (zero is a valid limit)

### Clearing a field
- [ ] Clear Max sugar entirely (empty) — no error shown, field shows placeholder
- [ ] Leave and re-enter Settings
- [ ] Confirm Max sugar field is empty (no limit applied)

---

## Reset Preferences

- [ ] Set at least one allergen, one additive, NOVA toggle ON, and a nutrition cap
- [ ] Scroll to bottom of Settings and tap **Reset all preferences**
- [ ] A confirmation dialog appears: "Reset preferences?" with Reset and Cancel buttons
- [ ] Tap **Cancel** — dialog closes, preferences unchanged
- [ ] Tap **Reset all preferences** again, then tap **Reset** in the dialog
- [ ] All selections cleared: no allergens checked, no additives checked, NOVA toggle OFF, all nutrition fields empty
- [ ] Leave and re-enter Settings — defaults persist

---

## Regression Tests

### Scanning still works
- [ ] Go back to home screen
- [ ] Tap **Scan** and scan a barcode — product detail screen appears correctly
- [ ] Or use **Enter barcode manually** — product detail screen appears

### Product Detail still works
- [ ] View a product detail screen
- [ ] Nutrition facts, name, brand, and image load correctly
- [ ] No crash or regression from preference changes

### Scan History still works
- [ ] Tap **History** from home screen
- [ ] Previously scanned items appear in the list
- [ ] Tap an item — navigates to product detail screen

---

## Build Verification

Run before shipping:

```
./gradlew test
./gradlew assembleDebug
```

All unit tests should pass, including the new `SettingsViewModelTest`.
