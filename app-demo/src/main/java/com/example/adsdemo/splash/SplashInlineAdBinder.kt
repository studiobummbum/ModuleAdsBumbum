package com.example.adsdemo.splash

import android.view.ViewGroup
import com.example.adsmodule.core.storage.StoredAdView

interface SplashInlineAdBinder {
    fun bindNative(container: ViewGroup, storedAd: StoredAdView)

    fun bindBanner(container: ViewGroup, storedAd: StoredAdView)

    fun clear(container: ViewGroup)
}
