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

/**
 * Binds an [AdMobNativeLoadedAd] into a container using the module layout.
 */
public class AdMobNativeRenderer {
    public fun bind(container: ViewGroup, handle: SdkLoadedAdHandle): Boolean {
        val nativeHandle = handle as? AdMobNativeLoadedAd ?: return false
        val nativeAd = nativeHandle.peekNativeAd() ?: return false
        container.removeAllViews()
        val adView = LayoutInflater.from(container.context)
            .inflate(R.layout.view_admob_native, container, false) as NativeAdView
        populate(adView, nativeAd)
        container.addView(adView)
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

        cta.text = nativeAd.callToAction
        adView.callToActionView = cta

        val iconDrawable = nativeAd.icon?.drawable
        if (iconDrawable == null) {
            icon.visibility = View.GONE
        } else {
            icon.visibility = View.VISIBLE
            icon.setImageDrawable(iconDrawable)
            adView.iconView = icon
        }

        advertiser.text = nativeAd.advertiser
        adView.advertiserView = advertiser

        adView.mediaView = media
        adView.setNativeAd(nativeAd)
    }
}
