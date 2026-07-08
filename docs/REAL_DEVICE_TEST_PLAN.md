# FoodFit Scan — Real Device Test Plan

_Run this plan on at least one physical Android device before internal/closed testing._

---

## Test Environment

| Field | Value |
|---|---|
| App version | 1.0 (versionCode 1) |
| Build type | Debug or Release |
| Device 1 | (fill in: make, model, Android version) |
| Device 2 | (fill in: make, model, Android version — different manufacturer) |
| Network | Real Wi-Fi or mobile data |

---

## 1. Fresh Install & Onboarding

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 1.1 | Install app on device with no previous install | App installs | |
| 1.2 | Launch app for the first time | Onboarding welcome screen appears | |
| 1.3 | Tap "Skip" on welcome page | App navigates to Home screen | |
| 1.4 | Reinstall; go through all onboarding pages | All 4 pages appear in order | |
| 1.5 | On preferences page, select allergens and NOVA toggle | Selections visible | |
| 1.6 | Tap "Start scanning" | Navigates to Home screen | |
| 1.7 | Launch app again | Home screen appears (not onboarding) | |

---

## 2. Camera Permission

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 2.1 | Tap "Scan Barcode" on Home | System permission dialog appears | |
| 2.2 | Deny permission | Permission denied screen shown with retry option | |
| 2.3 | Tap "Grant Camera Permission" | System dialog appears again | |
| 2.4 | Grant permission | Camera viewfinder appears | |

---

## 3. Scan 10 Supermarket Products

Scan the following product types. Record barcode and result.

| # | Product Type | Barcode | Found in OFF | Suitability | Image shown | Notes |
|---|---|---|---|---|---|---|
| 3.1 | Bread | | | | | |
| 3.2 | Milk or dairy | | | | | |
| 3.3 | Chocolate bar | | | | | |
| 3.4 | Breakfast cereal | | | | | |
| 3.5 | Tinned vegetable | | | | | |
| 3.6 | Juice or soft drink | | | | | |
| 3.7 | Crisps or snacks | | | | | |
| 3.8 | Ready meal | | | | | |
| 3.9 | Nut butter | | | | | |
| 3.10 | Gluten-free product | | | | | |

**For each found product, verify:**
- [ ] Product name and brand shown
- [ ] Suitability card shows (Good match / Caution / Avoid / Unknown)
- [ ] Suitability reasons are readable
- [ ] Nutrition table shows (or "not available" message)
- [ ] Ingredients shown (or "not available" message)
- [ ] Allergens section shown (including "None listed" if empty)
- [ ] Open Food Facts attribution visible at bottom
- [ ] Disclaimer visible at bottom

---

## 4. Manual Barcode Entry

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 4.1 | On Home, tap "Enter Barcode Manually" | Dialog appears | |
| 4.2 | Enter a valid 13-digit barcode (e.g. 5449000000996) | Look up button enabled | |
| 4.3 | Tap "Look up" | Navigates to product detail | |
| 4.4 | Enter an invalid barcode (e.g. "abc") | Error message shown in dialog | |
| 4.5 | Enter barcode with fewer than 8 digits | Error message shown | |
| 4.6 | On Scanner screen, tap "Enter manually" | Dialog appears | |

---

## 5. No Internet / Error States

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 5.1 | Disable Wi-Fi and mobile data | | |
| 5.2 | Scan or enter a barcode | Network error state shown | |
| 5.3 | Tap "Retry" | Attempts lookup again | |
| 5.4 | Re-enable internet, tap "Retry" | Product loads if found | |
| 5.5 | Scan a barcode for a product not in OFF database | "Product not found" state shown | |
| 5.6 | Tap "Scan another product" from not-found screen | Navigates to Scanner | |

---

## 6. Scan History & Cache

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 6.1 | Scan 3+ products | Items appear in History | |
| 6.2 | Open History screen | Items listed, most recent first | |
| 6.3 | Tap a "Found" item | Navigates to product detail | |
| 6.4 | Swipe a history item left | Item is deleted | |
| 6.5 | Tap clear icon (top-right) | Confirmation dialog appears | |
| 6.6 | Confirm clear | All history removed, empty state shown | |
| 6.7 | Scan same product twice | Shows in history twice (both entries) | |
| 6.8 | Turn off internet, scan a previously cached product | Shows cached data with stale cache banner | |

---

## 7. Settings Persistence

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 7.1 | Open Settings, toggle allergens and NOVA | Selections visible | |
| 7.2 | Set a sugar cap (e.g. 10) | Value saved | |
| 7.3 | Force-stop app and relaunch | Settings still show selected allergens and cap | |
| 7.4 | Tap "Reset all preferences" → confirm | All preferences cleared | |
| 7.5 | Relaunch — home screen shows preferences helper card | | |

---

## 8. Suitability Warnings

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 8.1 | Set allergen to "Milk" | | |
| 8.2 | Scan a product containing milk | Suitability shows AVOID with reason | |
| 8.3 | Set sugar cap to a very low value (e.g. 1 g) | | |
| 8.4 | Scan a sugary product | Suitability shows CAUTION or AVOID | |
| 8.5 | Enable "Avoid NOVA 4" and scan a ready meal | NOVA 4 warning shown if applicable | |

---

## 9. Alternatives

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 9.1 | Open a CAUTION or AVOID product detail | Alternatives card visible | |
| 9.2 | Tap "Find better options" | Loading indicator appears | |
| 9.3 | Wait for results | Alternatives listed (or "none found" message) | |
| 9.4 | Tap an alternative product card | Navigates to its product detail | |
| 9.5 | Open a GOOD MATCH product | "Already a good match" message shown | |

---

## 10. Small Phone Layout

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 10.1 | Test on a device with screen width ≤ 360 dp | | |
| 10.2 | Scroll through all product detail sections | No layout overflow or clipping | |
| 10.3 | Settings screen fully scrollable | All sections reachable | |
| 10.4 | History screen with many items | Scrolls correctly | |
| 10.5 | Onboarding pages scrollable on small screen | Content not cut off | |

---

## 11. Dark Mode

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 11.1 | Enable dark mode in system settings | | |
| 11.2 | Launch app | Dark theme applied consistently | |
| 11.3 | Navigate to all main screens | Text readable, cards visible | |
| 11.4 | Check suitability card colours | Colours remain distinguishable | |

---

## 12. App Restart & Background

| # | Step | Expected | Pass/Fail |
|---|---|---|---|
| 12.1 | Open product detail, press home, reopen app | Same screen shown | |
| 12.2 | Background app for 10+ minutes, resume | App restores state correctly | |
| 12.3 | Force-stop app, relaunch | Home screen (or onboarding if fresh) appears | |
| 12.4 | Rotate device on product detail screen | Layout adjusts, no crash | |

---

## Test Sign-off

| Field | Value |
|---|---|
| Tester | |
| Date | |
| Build tested | |
| Devices tested | |
| Blockers found | |
| Approved for internal testing? | Yes / No |
