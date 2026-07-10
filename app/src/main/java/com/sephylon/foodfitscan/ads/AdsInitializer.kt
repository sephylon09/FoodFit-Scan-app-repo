package com.sephylon.foodfitscan.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * One-time Google Mobile Ads SDK startup, called from [com.sephylon.foodfitscan.FoodFitScanApp].
 *
 * Privacy: this app never attaches user data to ad requests. Food preferences, allergens,
 * scan history, and searched product names stay local (DataStore/Room) — every ad request
 * in the app is a plain `AdRequest.Builder().build()` with no custom targeting.
 *
 * TODO(ads-consent): Before serving real ads to EEA/UK users in production, integrate the
 * Google User Messaging Platform (UMP) consent flow:
 *   1. Add `implementation("com.google.android.ump:user-messaging-platform:<latest>")`.
 *   2. In MainActivity, call `ConsentInformation.requestConsentInfoUpdate(...)` and show the
 *      consent form via `UserMessagingPlatform.loadAndShowConsentFormIfRequired(...)` BEFORE
 *      the first ad request, then gate ad loading on `canRequestAds()`.
 *   3. Create the GDPR consent message in AdMob Console -> Privacy & messaging.
 * Closed testing with Google sample ad units does not require this yet.
 */
object AdsInitializer {

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        // Initialize off the main thread; the OPTIMIZE_INITIALIZATION / OPTIMIZE_AD_LOADING
        // manifest flags make this safe and keep app startup unblocked.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(appContext) { /* per-adapter status not needed */ }
        }
    }
}
