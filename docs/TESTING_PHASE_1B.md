# Phase 1B Manual Testing Checklist

Open Food Facts API integration — barcode lookup and product detail screen.

## Prerequisites

- Physical or emulator device with internet access
- A real product barcode (e.g., Coca-Cola 500ml: 5449000000996, or any EAN-13 from a grocery item)
- An unknown barcode to test the not-found state (e.g., 0000000000000)

---

## Checklist

### Happy path — scan a real product

- [ ] Open the app and tap **Scan**
- [ ] Point the camera at a real product barcode
- [ ] The scanner navigates to Product Detail screen
- [ ] A loading indicator is visible briefly while the API is called
- [ ] The product name appears (not "Unknown product")
- [ ] The brand is displayed if available
- [ ] The quantity is displayed if available
- [ ] The barcode value is shown on screen
- [ ] A product image is loaded if the product has one in Open Food Facts
- [ ] Nutri-Score badge is shown if the product has a grade (a–e)
- [ ] NOVA group badge is shown if the product has a group (1–4)
- [ ] Nutrition table (per 100 g) shows at least energy and protein if available
- [ ] Ingredients text is visible if available
- [ ] Allergens/Traces/Additives are shown if available
- [ ] The attribution text "Data from Open Food Facts. Always check packaging…" is visible

### Happy path — manual entry of a known barcode

- [ ] On the Home screen, use the manual barcode entry field
- [ ] Enter a known barcode (e.g., 5449000000996)
- [ ] Confirm entry
- [ ] Product Detail screen loads with real data same as above

### Product not found

- [ ] Scan or enter an unknown barcode (e.g., 0000000000000)
- [ ] "Product not found" message is displayed
- [ ] The scanned barcode is shown
- [ ] Text explains the database is community-maintained
- [ ] **Try again** button re-triggers the lookup
- [ ] **Scan another product** button navigates to the Scanner screen
- [ ] **Back** button returns to the previous screen

### Network error (airplane mode)

- [ ] Enable airplane mode / disable Wi-Fi and mobile data
- [ ] Scan or enter any barcode
- [ ] "Could not connect to Open Food Facts" error is displayed
- [ ] **Retry** button re-triggers the lookup
- [ ] **Back** button returns to the previous screen

### Retry works correctly

- [ ] From an error state, tap **Retry** or **Try again**
- [ ] A loading indicator appears
- [ ] The result updates correctly (either product found or error again if still offline)

### Back navigation

- [ ] From Product Detail screen, tap the back arrow in the top bar
- [ ] Returns to the previous screen (Scanner or Home)

### No redundant API calls on recomposition

- [ ] With a product loaded, rotate the device
- [ ] The product data is still shown immediately (ViewModel survives rotation)
- [ ] No second network call is made (check Logcat — no duplicate OkHttp requests)

### Open Food Facts attribution

- [ ] At the bottom of any product-found screen, the attribution text is visible:
  "Data from Open Food Facts. Always check packaging, especially for allergens."

---

## Automated tests (run before testing manually)

```bash
./gradlew test
./gradlew assembleDebug
```

Both must pass with no errors.

---

## Known limitations (Phase 1B)

- No offline caching — every lookup hits the network (Phase 1C will add Room cache)
- History screen is still a placeholder
- Settings screen is still a placeholder
- Suitability scoring is not yet implemented
- Products not in Open Food Facts show a not-found state with no alternative search
