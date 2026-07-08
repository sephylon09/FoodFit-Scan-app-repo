# Phase 1A Manual Test Checklist

Barcode scanner and manual barcode entry.

## Checklist

- [ ] App launches without crash
- [ ] Home screen opens and displays all buttons
- [ ] Manual barcode entry dialog opens from Home screen "Enter Barcode Manually" button
- [ ] Manual barcode entry accepts a valid 13-digit barcode (e.g. `5449000000996`) and navigates to Product Detail
- [ ] Manual barcode entry accepts a valid 8-digit barcode (e.g. `01234565`) and navigates to Product Detail
- [ ] Manual barcode entry rejects an empty input with an error message
- [ ] Manual barcode entry rejects letters with an error message
- [ ] Manual barcode entry rejects an unsupported length (e.g. 10 digits) with an error message
- [ ] Scanner screen asks for camera permission on first open
- [ ] Denying camera permission shows the permission explanation screen
- [ ] "Grant Camera Permission" button on permission screen re-requests the permission
- [ ] "Go Back" button on permission screen returns to Home
- [ ] Granting camera permission shows the live camera preview
- [ ] Scanning a real product barcode (EAN-13 / UPC-A on a packaged food item) navigates to Product Detail with the correct barcode
- [ ] Product Detail screen displays the scanned or entered barcode
- [ ] Scanning the same barcode rapidly does not cause duplicate navigation
- [ ] Back navigation from Product Detail returns to the Scanner (or Home if entered manually)
- [ ] Torch/flashlight button toggles the camera flash
- [ ] "Enter manually" button inside the Scanner screen opens the barcode entry dialog
- [ ] Manual entry from Scanner screen navigates to Product Detail correctly

## Notes

- Open Food Facts API lookup is not implemented yet (placeholder shown on Product Detail)
- History screen shows empty state (Room DAOs not yet implemented)
- Settings screen shows preference UI but does not persist (DataStore not yet wired)
