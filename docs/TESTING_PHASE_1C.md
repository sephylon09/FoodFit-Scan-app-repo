# Phase 1C Manual Testing Checklist

## Setup

1. Install a fresh build: `./gradlew assembleDebug` then sideload or run from Android Studio.
2. Clear app data if upgrading from Phase 1B (Room version bumped to 2, destructive migration).

---

## Cache behaviour

- [ ] **First scan**: Scan or manually enter a known barcode (e.g. 5449000000996 — Coca-Cola).
  - Product should load from the Open Food Facts API.
  - Logcat shows Retrofit HTTP request.
- [ ] **Second scan (same barcode, within 7 days)**: Navigate back, scan the same barcode again.
  - Product should load instantly without an API call.
  - Logcat should show **no** new HTTP request.
- [ ] **Stale cache (optional)**: Manually set device clock forward 8 days, re-scan same barcode.
  - App should make an API call to refresh.
  - If offline, the cached product should still show with a yellow banner:
    *"Showing saved product data. Check packaging for latest information."*

---

## History screen

- [ ] Open **History** from the home screen.
- [ ] After scanning one product, the history screen lists it with:
  - Product name and brand.
  - Barcode.
  - Green **Found** badge.
  - Scanned date/time.
  - Thumbnail (if the product has an image).
- [ ] **Manual barcode entry** also appears in history.
- [ ] Scan a barcode that does not exist (e.g. 00000000001).
  - History shows entry with grey **Not found** badge.
- [ ] Scan while offline (or turn off Wi-Fi/mobile data).
  - History shows entry with orange **Network error** badge.
  - ProductDetailScreen shows a network error, not a crash.

---

## Navigation from history

- [ ] Tap a **Found** history item.
  - App navigates to `ProductDetailScreen` for that barcode.
  - Product loads from cache (no API call if fresh).
- [ ] Tap a **Not found** history item.
  - Item should not be tappable / does nothing (no navigation).

---

## Clear history

- [ ] With at least one history entry, tap the **delete sweep** icon (top right of History screen).
  - Confirmation dialog appears: "Remove all scan history entries?"
- [ ] Tap **Clear**.
  - History list is now empty; empty state is shown.
- [ ] Tap **Cancel** in the dialog.
  - History list is unchanged.

---

## App stability

- [ ] App builds without errors: `./gradlew assembleDebug`
- [ ] Unit tests pass: `./gradlew test`
- [ ] CameraX scanner still works after Phase 1C changes.
- [ ] ProductDetailScreen still shows all product fields (nutrition, allergens, badges).
- [ ] No crashes on cold start.
- [ ] No crashes when rotating the screen on ProductDetailScreen or HistoryScreen.

---

## Known limitations / deferred to Phase 1D

- Stale cache is shown without a visible indicator in most flows; banner only appears on stale+offline fallback.
- History entries are not paginated (limited to 100 most recent).
- No per-item delete swipe gesture (only full clear is supported).
- Retry on ProductDetailScreen re-runs the full cache check rather than forcing a network bypass.
