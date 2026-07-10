package com.sephylon.foodfitscan.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private enum class BannerState { Loading, Loaded, Failed }

/**
 * Adaptive anchored banner ad. Takes zero height until an ad actually loads, and collapses
 * back to zero if loading fails — the surrounding layout never shows an empty grey box.
 *
 * The [AdView] is paused/resumed with the host lifecycle and destroyed when this leaves
 * composition, so it cannot leak.
 */
@Composable
fun AdaptiveBannerAd(
    adUnitId: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val adWidthDp = maxWidth.value.toInt()
        if (adWidthDp <= 0) return@BoxWithConstraints

        // A new width (e.g. window resize) needs a fresh AdView with a recomputed ad size.
        key(adWidthDp) {
            val context = LocalContext.current
            var bannerState by remember { mutableStateOf(BannerState.Loading) }
            val adSize = remember {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
            }
            val adView = remember {
                AdView(context).apply {
                    setAdSize(adSize)
                    this.adUnitId = adUnitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            bannerState = BannerState.Loaded
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            // Hide gracefully; no retry loop.
                            bannerState = BannerState.Failed
                        }
                    }
                    // Plain request — never any user data or custom targeting.
                    loadAd(AdRequest.Builder().build())
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(adView, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> adView.pause()
                        Lifecycle.Event.ON_RESUME -> adView.resume()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    adView.destroy()
                }
            }

            AndroidView(
                factory = { adView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (bannerState == BannerState.Loaded) adSize.height.dp else 0.dp),
            )
        }
    }
}
