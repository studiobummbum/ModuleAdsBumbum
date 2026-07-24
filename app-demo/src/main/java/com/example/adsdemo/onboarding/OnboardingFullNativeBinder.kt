package com.example.adsdemo.onboarding

import android.view.View
import android.view.ViewGroup
import com.example.adsmodule.core.storage.StoredAdView

data class NativeFullBoundViews(
    val root: View,
    val media: View,
    val cta: View,
    val clickableAssets: List<View>,
)

interface OnboardingFullNativeBinder {
    fun bind(
        container: ViewGroup,
        storedAd: StoredAdView?,
        title: String,
        fullIndex: Int = 1,
    ): NativeFullBoundViews

    fun clear(container: ViewGroup)
}
