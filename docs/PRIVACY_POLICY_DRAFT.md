# FoodFit Scan — Privacy Policy (Draft)

_Last updated: June 2026. This is a draft. Publish at a hosted URL before submitting to the Play Store._

---

## Who we are

FoodFit Scan is a personal food label helper app for Android. It is not affiliated with any food manufacturer, retailer, or Open Food Facts.

---

## What data we collect and where it goes

**We do not collect personal data.**

| Data | Where it goes | How long |
|---|---|---|
| Barcode numbers you scan | Sent to Open Food Facts API (HTTPS GET request only) to retrieve product information. Not stored on any server by us. | Not stored |
| Product data returned by Open Food Facts | Cached on your device only (7-day cache) | Up to 7 days |
| Your food preferences (allergens, NOVA setting, nutrition caps) | Stored on your device only (Android DataStore) | Until you reset or uninstall |
| Scan history | Stored on your device only (Room database) | Until you clear history or uninstall |

---

## Camera

The app uses the device camera solely to scan product barcodes. Camera frames are processed on-device by the ML Kit barcode scanner. No images or video are stored or transmitted.

---

## Internet access

The app sends barcode numbers to the Open Food Facts API (`https://world.openfoodfacts.org`) to retrieve product information. This is the only network request the app makes. No other data is sent over the internet.

Open Food Facts has its own privacy policy available at openfoodfacts.org.

---

## Analytics and advertising

This version of FoodFit Scan contains no analytics, crash reporting, or advertising SDKs.

---

## Data sharing

We do not share any user data with third parties. The only external communication is the Open Food Facts API lookup described above.

---

## Data deletion

- **Scan history**: Delete individual items by swiping, or clear all via History screen > clear icon.
- **Preferences**: Reset via Settings > Reset all preferences.
- **Product cache**: Automatically expires after 7 days. Cleared on app uninstall.
- **All data**: Uninstalling the app removes all locally stored data.

---

## Children

This app is not directed at children under 13. It does not knowingly collect data from children.

---

## Changes to this policy

If this policy changes before or after Play Store submission, we will update this document and note the change date at the top.

---

## Contact

Questions about this privacy policy: yuhongbin124@gmail.com
