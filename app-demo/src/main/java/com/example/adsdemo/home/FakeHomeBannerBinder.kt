package com.example.adsdemo.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.adsdemo.R
import com.example.adsmodule.core.storage.StoredAdView

interface HomeBannerBinder {
    fun bindBanner(container: ViewGroup, storedAd: StoredAdView)

    fun clear(container: ViewGroup)
}

class FakeHomeBannerBinder : HomeBannerBinder {
    override fun bindBanner(container: ViewGroup, storedAd: StoredAdView) {
        container.removeAllViews()
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.view_fake_banner_ufo, container, false)
        view.findViewById<TextView>(R.id.fake_banner_text).text =
            "Banner Home ${storedAd.sourceConfigKey.value} #${storedAd.sourceListIndex} w=${storedAd.sourceWeight}"
        container.addView(view)
        container.visibility = View.VISIBLE
    }

    override fun clear(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = View.GONE
    }
}
