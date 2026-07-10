package com.sephylon.foodfitscan.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

sealed interface NativeAdSlotState {
    data object Loading : NativeAdSlotState
    data class Loaded(val ad: NativeAd) : NativeAdSlotState
    data object Failed : NativeAdSlotState
}

/**
 * Loads and caches one [NativeAd] per placement slot for the lifetime of the results list,
 * so scrolling does not re-request ads. Slot states are backed by Compose state, so a row
 * appears automatically once its ad finishes loading (and never appears if it fails).
 *
 * Always create via [rememberNativeAdLoader]; that ties [destroyAll] to composition exit
 * so every loaded [NativeAd] is destroyed and cannot leak.
 */
@Stable
class NativeAdLoader(
    private val context: Context,
    private val adUnitId: String,
) {
    private val slots = mutableStateMapOf<Int, NativeAdSlotState>()
    private var destroyed = false

    fun stateFor(slot: Int): NativeAdSlotState = slots[slot] ?: NativeAdSlotState.Loading

    /** Starts loading the slot's ad if it has not been requested yet. Main thread only. */
    fun ensureLoaded(slot: Int) {
        if (destroyed || slots.containsKey(slot)) return
        slots[slot] = NativeAdSlotState.Loading
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                if (destroyed) {
                    // Composition already left; free the ad immediately.
                    ad.destroy()
                } else {
                    slots[slot] = NativeAdSlotState.Loaded(ad)
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    slots[slot] = NativeAdSlotState.Failed
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                    .build(),
            )
            .build()
        // Plain request — never any user data or custom targeting.
        loader.loadAd(AdRequest.Builder().build())
    }

    fun destroyAll() {
        destroyed = true
        slots.values.filterIsInstance<NativeAdSlotState.Loaded>().forEach { it.ad.destroy() }
        slots.clear()
    }
}

@Composable
fun rememberNativeAdLoader(adUnitId: String): NativeAdLoader {
    val context = LocalContext.current
    val loader = remember(adUnitId) { NativeAdLoader(context, adUnitId) }
    DisposableEffect(loader) {
        onDispose { loader.destroyAll() }
    }
    return loader
}
