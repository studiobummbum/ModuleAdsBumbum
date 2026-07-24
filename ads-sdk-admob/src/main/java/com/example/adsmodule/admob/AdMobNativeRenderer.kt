package com.example.adsmodule.admob

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

public enum class AdMobNativeLayoutStyle {
    DEFAULT,
    MEDIUM_BOTTOM,
    FULL,
}

/**
 * Binds an [AdMobNativeLoadedAd] into a container using the module layout.
 */
public class AdMobNativeRenderer {
    public fun bind(
        container: ViewGroup,
        handle: SdkLoadedAdHandle,
        style: AdMobNativeLayoutStyle = AdMobNativeLayoutStyle.DEFAULT,
    ): Boolean {
        val nativeHandle = handle as? AdMobNativeLoadedAd ?: return false
        val nativeAd = nativeHandle.peekNativeAd() ?: return false
        container.removeAllViews()
        val layoutRes = when (style) {
            AdMobNativeLayoutStyle.DEFAULT -> R.layout.view_admob_native
            AdMobNativeLayoutStyle.MEDIUM_BOTTOM -> R.layout.view_admob_native_medium
            AdMobNativeLayoutStyle.FULL -> R.layout.view_admob_native_full
        }
        val adView = LayoutInflater.from(container.context)
            .inflate(layoutRes, container, false) as NativeAdView
        populate(adView, nativeAd)
        val layoutParams = if (style == AdMobNativeLayoutStyle.FULL) {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        container.addView(adView, layoutParams)
        container.visibility = View.VISIBLE
        return true
    }

    public fun clear(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = View.GONE
    }

    private fun populate(adView: NativeAdView, nativeAd: NativeAd) {
        val headline = adView.findViewById<TextView>(R.id.admob_native_headline)
        val body = adView.findViewById<TextView>(R.id.admob_native_body)
        val cta = adView.findViewById<Button>(R.id.admob_native_cta)
        val icon = adView.findViewById<ImageView>(R.id.admob_native_icon)
        val advertiser = adView.findViewById<TextView>(R.id.admob_native_advertiser)
        val media = adView.findViewById<MediaView>(R.id.admob_native_media)

        headline.text = nativeAd.headline
        adView.headlineView = headline

        body.text = nativeAd.body
        adView.bodyView = body

        val ctaLabel = nativeAd.callToAction?.takeIf { it.isNotBlank() }
            ?: cta.context.getString(R.string.admob_native_continue)
        cta.text = ctaLabel
        adView.callToActionView = cta

        val iconDrawable = nativeAd.icon?.drawable
        if (iconDrawable == null || icon.visibility == View.GONE) {
            icon.visibility = View.GONE
        } else {
            icon.visibility = View.VISIBLE
            icon.setImageDrawable(iconDrawable)
            adView.iconView = icon
        }

        if (advertiser.visibility != View.GONE) {
            advertiser.text = nativeAd.advertiser
            adView.advertiserView = advertiser
        }

        adView.mediaView = media
        adView.setNativeAd(nativeAd)
    }
}
