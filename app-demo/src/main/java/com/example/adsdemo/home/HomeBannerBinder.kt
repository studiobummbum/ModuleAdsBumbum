package com.example.adsdemo.home

import android.view.ViewGroup
import com.example.adsmodule.core.storage.StoredAdView

interface HomeBannerBinder {
    fun bindBanner(container: ViewGroup, storedAd: StoredAdView)

    fun clear(container: ViewGroup)
}
