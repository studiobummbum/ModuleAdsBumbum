package com.example.adsdemo.sdk

import android.view.View
import android.view.ViewGroup
import com.example.adsdemo.home.HomeBannerBinder
import com.example.adsdemo.language.NormalNativeAdBinder
import com.example.adsdemo.onboarding.NativeFullBoundViews
import com.example.adsdemo.onboarding.OnboardingFullNativeBinder
import com.example.adsdemo.splash.SplashInlineAdBinder
import com.example.adsmodule.admob.AdMobBannerBinder
import com.example.adsmodule.admob.AdMobBannerLoadedAd
import com.example.adsmodule.admob.AdMobNativeLayoutStyle
import com.example.adsmodule.admob.AdMobNativeLoadedAd
import com.example.adsmodule.admob.AdMobNativeRenderer
import com.example.adsmodule.core.storage.StoredAdView

class AdMobSplashInlineAdBinder : SplashInlineAdBinder {
    private val nativeRenderer = AdMobNativeRenderer()

    override fun bindNative(container: ViewGroup, storedAd: StoredAdView) {
        val handle = storedAd.sdkHandle as? AdMobNativeLoadedAd
        if (handle == null ||
            !nativeRenderer.bind(container, handle, AdMobNativeLayoutStyle.MEDIUM_BOTTOM)
        ) {
            // Sticky: never blank an already-visible native on bind failure.
            if (container.childCount == 0) {
                clear(container)
            }
        }
    }

    override fun bindBanner(container: ViewGroup, storedAd: StoredAdView) {
        val handle = storedAd.sdkHandle as? AdMobBannerLoadedAd
        if (handle == null || !AdMobBannerBinder.bind(container, handle)) {
            clear(container)
        }
    }

    override fun clear(container: ViewGroup) {
        nativeRenderer.clear(container)
        AdMobBannerBinder.clear(container)
    }
}

class AdMobNormalNativeAdBinder : NormalNativeAdBinder {
    private val nativeRenderer = AdMobNativeRenderer()

    override fun bindNative(container: ViewGroup, storedAd: StoredAdView, title: String) {
        val handle = storedAd.sdkHandle as? AdMobNativeLoadedAd
        if (handle == null ||
            !nativeRenderer.bind(container, handle, AdMobNativeLayoutStyle.MEDIUM_BOTTOM)
        ) {
            if (container.childCount == 0) {
                clear(container)
            }
        }
    }

    override fun clear(container: ViewGroup) {
        nativeRenderer.clear(container)
    }
}

class AdMobHomeBannerBinder : HomeBannerBinder {
    override fun bindBanner(container: ViewGroup, storedAd: StoredAdView) {
        val handle = storedAd.sdkHandle as? AdMobBannerLoadedAd
        if (handle == null || !AdMobBannerBinder.bind(container, handle)) {
            clear(container)
        }
    }

    override fun clear(container: ViewGroup) {
        AdMobBannerBinder.clear(container)
    }
}

class AdMobOnboardingFullNativeBinder : OnboardingFullNativeBinder {
    private val nativeRenderer = AdMobNativeRenderer()

    override fun bind(
        container: ViewGroup,
        storedAd: StoredAdView?,
        title: String,
        fullIndex: Int,
    ): NativeFullBoundViews {
        // Full natives are NOT sticky mid-session — always clear then bind fresh.
        // Back/swipe-back reuse is handled by parking the object in storage (READY).
        if (storedAd == null) {
            clear(container)
            return NativeFullBoundViews(
                root = container,
                media = container,
                cta = container,
                clickableAssets = emptyList(),
            )
        }
        val handle = storedAd.sdkHandle as? AdMobNativeLoadedAd
        if (handle == null || !nativeRenderer.bind(container, handle, AdMobNativeLayoutStyle.FULL)) {
            clear(container)
            return NativeFullBoundViews(
                root = container,
                media = container,
                cta = container,
                clickableAssets = emptyList(),
            )
        }
        val root = container.getChildAt(0) ?: container
        val media = root.findViewById<View>(com.example.adsmodule.admob.R.id.admob_native_media)
        val cta = root.findViewById<View>(com.example.adsmodule.admob.R.id.admob_native_cta)
        val headline = root.findViewById<View>(com.example.adsmodule.admob.R.id.admob_native_headline)
        val body = root.findViewById<View>(com.example.adsmodule.admob.R.id.admob_native_body)
        return NativeFullBoundViews(
            root = root,
            media = media ?: root,
            cta = cta ?: root,
            clickableAssets = listOfNotNull(media, cta, headline, body),
        )
    }

    override fun clear(container: ViewGroup) {
        nativeRenderer.clear(container)
    }
}
