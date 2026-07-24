package com.example.adsdemo.language

import android.view.ViewGroup
import com.example.adsmodule.core.storage.StoredAdView

interface NormalNativeAdBinder {
    fun bindNative(container: ViewGroup, storedAd: StoredAdView, title: String)

    fun clear(container: ViewGroup)
}
