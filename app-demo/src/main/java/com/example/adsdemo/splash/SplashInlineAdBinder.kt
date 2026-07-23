package com.example.adsdemo.splash

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.adsdemo.R
import com.example.adsmodule.core.storage.StoredAdView

interface SplashInlineAdBinder {
    fun bindNative(container: ViewGroup, storedAd: StoredAdView)

    fun bindBanner(container: ViewGroup, storedAd: StoredAdView)

    fun clear(container: ViewGroup)
}

class FakeSplashInlineAdBinder : SplashInlineAdBinder {
    override fun bindNative(container: ViewGroup, storedAd: StoredAdView) {
        container.removeAllViews()
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.view_fake_native_splash, container, false)
        view.findViewById<TextView>(R.id.fake_native_title).text =
            "Native Splash"
        view.findViewById<TextView>(R.id.fake_native_body).text =
            "${storedAd.sourceConfigKey.value} #${storedAd.sourceListIndex} w=${storedAd.sourceWeight}"
        view.findViewById<TextView>(R.id.fake_native_cta).text =
            storedAd.sourceAdunit
        container.addView(view)
        container.visibility = View.VISIBLE
    }

    override fun bindBanner(container: ViewGroup, storedAd: StoredAdView) {
        container.removeAllViews()
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.view_fake_banner_ufo, container, false)
        view.findViewById<TextView>(R.id.fake_banner_text).text =
            "Banner UFO ${storedAd.sourceConfigKey.value} #${storedAd.sourceListIndex}"
        container.addView(view)
        container.visibility = View.VISIBLE
    }

    override fun clear(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = View.GONE
    }
}
