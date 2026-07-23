package com.example.adsdemo.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.adsdemo.R
import com.example.adsmodule.core.storage.StoredAdView

interface NormalNativeAdBinder {
    fun bindNative(container: ViewGroup, storedAd: StoredAdView, title: String)

    fun clear(container: ViewGroup)
}

class FakeNormalNativeAdBinder : NormalNativeAdBinder {
    override fun bindNative(container: ViewGroup, storedAd: StoredAdView, title: String) {
        container.removeAllViews()
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.view_fake_native_inline, container, false)
        view.findViewById<TextView>(R.id.fake_native_title).text = title
        view.findViewById<TextView>(R.id.fake_native_body).text =
            "${storedAd.sourceConfigKey.value} #${storedAd.sourceListIndex} w=${storedAd.sourceWeight}"
        view.findViewById<TextView>(R.id.fake_native_cta).text = storedAd.sourceAdunit
        container.addView(view)
        container.visibility = View.VISIBLE
    }

    override fun clear(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = View.GONE
    }
}
