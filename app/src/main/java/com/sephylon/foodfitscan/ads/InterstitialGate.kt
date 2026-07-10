package com.sephylon.foodfitscan.ads

/**
 * Frequency-cap policy for the interstitial ad. Pure logic (no Android types) so it is
 * unit-testable.
 *
 * Rule: an interstitial may show only after [viewsPerAd] successful product-detail views
 * since the last interstitial (or since app start — the counter is in-memory, so a fresh
 * launch never starts at the threshold), and never more often than [minIntervalMillis].
 */
class InterstitialGate(
    private val viewsPerAd: Int = DEFAULT_VIEWS_PER_AD,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {

    private var productViewsSinceLastAd = 0

    /** Number of successful product-detail views recorded since the last shown ad. */
    val pendingViews: Int get() = productViewsSinceLastAd

    fun recordProductView() {
        productViewsSinceLastAd++
    }

    /** True when enough views have accumulated that the ad should be loaded ahead of time. */
    fun shouldPreload(): Boolean = productViewsSinceLastAd >= viewsPerAd - PRELOAD_LEAD_VIEWS

    /**
     * True when an interstitial may be shown right now: the view threshold is reached AND
     * at least [minIntervalMillis] has passed since [lastShownAtMillis] (null = never shown).
     */
    fun shouldShow(lastShownAtMillis: Long?, nowMillis: Long): Boolean =
        productViewsSinceLastAd >= viewsPerAd &&
            (lastShownAtMillis == null || nowMillis - lastShownAtMillis >= minIntervalMillis)

    /** Resets the view counter after an interstitial was actually displayed. */
    fun onAdShown() {
        productViewsSinceLastAd = 0
    }

    companion object {
        /** Within the intended "after 8–12 product detail views" window. */
        const val DEFAULT_VIEWS_PER_AD = 10

        /** Never two interstitials within 3 minutes, even across app restarts. */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 3 * 60 * 1000L

        /** Start loading the ad this many views before it is needed, so it is ready. */
        const val PRELOAD_LEAD_VIEWS = 2
    }
}
