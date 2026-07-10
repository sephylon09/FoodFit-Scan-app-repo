package com.sephylon.foodfitscan.ads

import com.sephylon.foodfitscan.BuildConfig

/**
 * Central AdMob configuration. All ad unit IDs come from BuildConfig fields wired per
 * build type in app/build.gradle.kts:
 *
 * - debug         -> Google sample app ID + sample ad units (always test ads)
 * - closedTesting -> real app ID + sample ad units (safe for Play closed testing)
 * - release       -> real app ID + real FoodFit Scan ad units
 *
 * Never hardcode ad unit IDs at call sites — always read them from here.
 */
object AdConfig {

    /** True when this build requests Google sample (test) ads instead of real ads. */
    val useTestAds: Boolean = BuildConfig.ADS_USE_TEST_UNITS

    val bannerAdUnitId: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    val interstitialAdUnitId: String = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
    val nativeAdUnitId: String = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID

    /** Publisher prefix of Google's sample (test) ad units. */
    const val GOOGLE_SAMPLE_PUBLISHER = "ca-app-pub-3940256099942544"

    /** Publisher prefix of the real FoodFit Scan ad units. */
    const val FOODFIT_PUBLISHER = "ca-app-pub-6675028272966387"
}
