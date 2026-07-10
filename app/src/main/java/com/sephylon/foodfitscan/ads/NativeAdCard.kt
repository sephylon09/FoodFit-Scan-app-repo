package com.sephylon.foodfitscan.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.sephylon.foodfitscan.R

/**
 * Renders a loaded [NativeAd] as a card that matches the app's product-result styling while
 * being unmistakably labelled "Sponsored". The [NativeAd] lifecycle (destroy) is owned by
 * [NativeAdLoader]; this composable only displays it.
 */
@Composable
fun NativeAdCard(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            // Inflate against a throwaway parent so the root layout params resolve properly.
            val adView = LayoutInflater.from(context)
                .inflate(R.layout.view_native_ad_card, FrameLayout(context), false) as NativeAdView
            // Register asset views once; binding happens in update.
            adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline)
            adView.advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
            adView.bodyView = adView.findViewById<TextView>(R.id.ad_body)
            adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
            adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
            adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)
            // Rounded corners for the media area (clipToOutline is code-only below API 31).
            adView.findViewById<FrameLayout>(R.id.ad_media_container).clipToOutline = true
            adView
        },
        update = { adView ->
            if (adView.tag !== nativeAd) {
                adView.tag = nativeAd
                bind(adView, nativeAd)
            }
        },
    )
}

private fun bind(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as TextView).text = ad.headline

    val advertiserText = ad.advertiser ?: ad.store
    (adView.advertiserView as TextView).apply {
        text = advertiserText
        visibility = if (advertiserText.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    (adView.bodyView as TextView).apply {
        text = ad.body
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    (adView.iconView as ImageView).apply {
        val iconDrawable = ad.icon?.drawable
        setImageDrawable(iconDrawable)
        visibility = if (iconDrawable == null) View.GONE else View.VISIBLE
    }

    (adView.callToActionView as Button).apply {
        text = ad.callToAction
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    adView.mediaView?.let { mediaView ->
        ad.mediaContent?.let { mediaView.mediaContent = it }
        mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
    }

    // Must be last: registers the ad with all views above (impression + click tracking).
    adView.setNativeAd(ad)
}
