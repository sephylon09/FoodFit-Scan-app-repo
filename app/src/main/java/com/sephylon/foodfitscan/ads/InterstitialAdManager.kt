package com.sephylon.foodfitscan.ads

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Loads and shows the single interstitial placement, guarded by [InterstitialGate].
 *
 * The ONLY trigger is navigating back to Home from a product detail screen. It never shows
 * on app launch, when opening the scanner, right after a scan, before product details, or
 * on back-press out of the app — none of those paths call [maybeShowOnReturnToHome].
 *
 * Frequency cap: the product-view counter lives in memory (resets on process death, so a
 * fresh launch can never immediately show an ad); the last-shown timestamp is persisted in
 * DataStore so the minimum interval also holds across restarts.
 *
 * All public methods must be called from the main thread (they are — from ViewModel
 * coroutines on Main and from the navigation listener).
 */
class InterstitialAdManager(
    context: Context,
    private val adUnitId: String,
    private val dataStore: DataStore<Preferences>,
    private val gate: InterstitialGate = InterstitialGate(),
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    @Volatile
    private var lastShownAtMillis: Long? = null

    init {
        scope.launch {
            lastShownAtMillis = runCatching {
                dataStore.data.first()[LAST_SHOWN_AT_KEY]
            }.getOrNull()
        }
    }

    /** Call once per successful product-detail view. Preloads the ad shortly before needed. */
    fun recordProductDetailView() {
        gate.recordProductView()
        if (gate.shouldPreload()) preload()
    }

    /**
     * Call when navigation returns to Home. Shows the interstitial only if the frequency
     * gate allows it AND an ad is already loaded; otherwise it stays silent (no blocking,
     * no spinner) and the next return to Home gets another chance.
     */
    fun maybeShowOnReturnToHome(activity: Activity) {
        if (!gate.shouldShow(lastShownAtMillis, System.currentTimeMillis())) return
        val ad = interstitialAd
        if (ad == null) {
            preload()
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                markShown()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Gate not consumed; a later return to Home may try again with a fresh ad.
            }
        }
        ad.show(activity)
    }

    private fun markShown() {
        gate.onAdShown()
        val now = System.currentTimeMillis()
        lastShownAtMillis = now
        scope.launch {
            runCatching { dataStore.edit { it[LAST_SHOWN_AT_KEY] = now } }
        }
    }

    private fun preload() {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // No retry loop: the next recorded product view triggers another attempt.
                    isLoading = false
                    interstitialAd = null
                }
            },
        )
    }

    companion object {
        internal val LAST_SHOWN_AT_KEY = longPreferencesKey("interstitial_last_shown_at")
    }
}
