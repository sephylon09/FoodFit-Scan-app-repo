# Phase 2C — Manual Testing Checklist: First-time Onboarding

## Setup

- Use a fresh install (uninstall the app or clear app data before testing).
- Build the debug APK: `./gradlew assembleDebug`

---

## Onboarding flow — fresh install

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 1 | Fresh install — open app | Onboarding screen appears (not Home screen) | |
| 2 | Screen 1 (Welcome) | Title "FoodFit Scan" visible, description text visible, "Get started" button visible, "Skip" button visible in top bar | |
| 3 | Tap "Get started" | Screen 2 (Important notice) slides in | |
| 4 | Screen 2 (Disclaimer) | Warning card with Open Food Facts disclaimer text, "I understand" button, back arrow, "Skip" in top bar | |
| 5 | Tap back arrow on Screen 2 | Returns to Screen 1 (Welcome) with back-slide animation | |
| 6 | Tap "Get started" again, then "I understand" | Screen 3 (Preferences) slides in | |
| 7 | Screen 3 (Preferences) | Allergen list (9 items with checkboxes) and NOVA 4 toggle visible | |
| 8 | Tap one allergen checkbox (e.g. Milk) | Checkbox becomes checked | |
| 9 | Enable NOVA 4 toggle | Toggle turns on | |
| 10 | Tap "Continue" | Screen 4 (Ready) slides in | |
| 11 | Screen 4 (Ready) | "You're ready!" heading, "Your preferences can be changed anytime in Settings." text, "Start scanning" button | |
| 12 | Tap "Start scanning" | Navigates to Home screen, onboarding is gone | |
| 13 | Press back on Home screen | App exits (or does nothing), does NOT return to onboarding | |

---

## Skip for now

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 14 | Fresh install — tap "Skip" on Screen 1 | Navigates to Home screen immediately | |
| 15 | Check preferences after skip | All preferences remain at defaults (can verify in Settings) | |
| 16 | Relaunch app after skip | Opens Home screen directly (no onboarding repeat) | |

---

## Completing onboarding saves preferences

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 17 | Complete onboarding with Milk and Eggs checked, NOVA 4 on | Home screen opens | |
| 18 | Open Settings | Milk and Eggs are checked, NOVA 4 is toggled on | |
| 19 | Scan or enter a product barcode | Suitability card reflects the allergen and NOVA 4 settings | |

---

## Relaunch after completing onboarding

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 20 | Kill and relaunch app after completing onboarding | Home screen opens directly (no onboarding) | |
| 21 | Kill and relaunch app after skipping onboarding | Home screen opens directly (no onboarding) | |

---

## Home screen preference helper card

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 22 | Skip onboarding (all defaults) — arrive at Home | Helper card "Set preferences for personalized results" is visible | |
| 23 | Tap "Open Settings" on helper card | Settings screen opens | |
| 24 | In Settings, check any allergen — return to Home | Helper card is no longer visible | |

---

## Review onboarding from Settings

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 25 | Complete onboarding — open Settings | "Review onboarding" button visible near bottom | |
| 26 | Tap "Review onboarding" | Onboarding screen opens from Screen 1 | |
| 27 | Go through all pages and tap "Start scanning" | Navigates to Home; preferences selected in review are saved | |
| 28 | Press back on Screen 1 of review onboarding | Returns to Settings screen | |

---

## Back button behaviour

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 29 | On any onboarding page > 1, press hardware back | Goes to previous onboarding page | |
| 30 | On onboarding Screen 1 (first-time), press hardware back | App exits or does nothing | |
| 31 | After completing onboarding, press back on Home | App exits, NOT back to onboarding | |

---

## Scan flow still works

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 32 | After onboarding complete, tap "Scan Barcode" | Camera scanner opens | |
| 33 | Scan a barcode | Product detail screen opens with suitability card | |
| 34 | Suitability card reflects onboarding preferences | Allergens and NOVA 4 flag are applied | |

---

## Settings still works normally

| # | Step | Expected result | Pass/Fail |
|---|------|----------------|-----------|
| 35 | Open Settings from Home | Settings screen opens | |
| 36 | Change preferences in Settings | Changes persist on relaunch | |
| 37 | Reset all preferences in Settings | All preferences clear, helper card reappears on Home | |

---

## Known limitations

- Onboarding does not set nutrition caps (sugar/salt/fat). Those remain in Settings only.
- Onboarding does not set additive preferences. Those remain in Settings only.
- "Review onboarding" navigates to the full onboarding screen; pressing Skip from review sets onboarding complete and goes back to Home.
