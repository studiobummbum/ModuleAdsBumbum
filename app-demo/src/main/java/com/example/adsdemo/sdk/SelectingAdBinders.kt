package com.example.adsdemo.sdk

import android.view.View
import android.view.ViewGroup
import com.example.adsdemo.home.FakeHomeBannerBinder
import com.example.adsdemo.home.HomeBannerBinder
import com.example.adsdemo.language.FakeNormalNativeAdBinder
import com.example.adsdemo.language.NormalNativeAdBinder
import com.example.adsdemo.onboarding.FakeNativeFullBoundViews
import com.example.adsdemo.onboarding.FakeOnboardingFullNativeBinder
import com.example.adsdemo.onboarding.OnboardingFullNativeBinder
import com.example.adsdemo.splash.FakeSplashInlineAdBinder
import com.example.adsdemo.splash.SplashInlineAdBinder
import com.example.adsmodule.admob.AdMobBannerBinder
import com.example.adsmodule.admob.AdMobBannerLoadedAd
import com.example.adsmodule.admob.AdMobNativeLoadedAd
import com.example.adsmodule.admob.AdMobNativeRenderer
import com.example.adsmodule.core.storage.StoredAdView

private fun DemoSdkBackend.usesAdMobBinders(): Boolean =
    this == DemoSdkBackend.AdMobTest || this == DemoSdkBackend.AdMob

class SelectingSplashInlineAdBinder(
    private val backend: DemoSdkBackend,
) : SplashInlineAdBinder {
    private val fake = FakeSplashInlineAdBinder()
    private val nativeRenderer = AdMobNativeRenderer()

    override fun bindNative(container: ViewGroup, storedAd: StoredAdView) {
        if (!backend.usesAdMobBinders()) {
            fake.bindNative(container, storedAd)
            return
        }
        val handle = storedAd.sdkHandle as? AdMobNativeLoadedAd
        if (handle == null || !nativeRenderer.bind(container, handle)) {
            fake.bindNative(container, storedAd)
        }
    }

    override fun bindBanner(container: ViewGroup, storedAd: StoredAdView) {
        if (!backend.usesAdMobBinders()) {
            fake.bindBanner(container, storedAd)
            return
        }
        val handle = storedAd.sdkHandle as? AdMobBannerLoadedAd
        if (handle == null || !AdMobBannerBinder.bind(container, handle)) {
            fake.bindBanner(container, storedAd)
        }
    }

    override fun clear(container: ViewGroup) {
        if (backend.usesAdMobBinders()) {
            nativeRenderer.clear(container)
            AdMobBannerBinder.clear(container)
        } else {
            fake.clear(container)
        }
    }
}

class SelectingNormalNativeAdBinder(
    private val backend: DemoSdkBackend,
) : NormalNativeAdBinder {
    private val fake = FakeNormalNativeAdBinder()
    private val nativeRenderer = AdMobNativeRenderer()

    override fun bindNative(container: ViewGroup, storedAd: StoredAdView, title: String) {
        if (!backend.usesAdMobBinders()) {
            fake.bindNative(container, storedAd, title)
            return
        }
        val handle = storedAd.sdkHandle as? AdMobNativeLoadedAd
        if (handle == null || !nativeRenderer.bind(container, handle)) {
            fake.bindNative(container, storedAd, title)
        }
    }

    override fun clear(container: ViewGroup) {
        if (backend.usesAdMobBinders()) {
            nativeRenderer.clear(container)
        } else {
            fake.clear(container)
        }
    }
}

class SelectingHomeBannerBinder(
    private val backend: DemoSdkBackend,
) : HomeBannerBinder {
    private val fake = FakeHomeBannerBinder()

    override fun bindBanner(container: ViewGroup, storedAd: StoredAdView) {
        if (!backend.usesAdMobBinders()) {
            fake.bindBanner(container, storedAd)
            return
        }
        val handle = storedAd.sdkHandle as? AdMobBannerLoadedAd
        if (handle == null || !AdMobBannerBinder.bind(container, handle)) {
            fake.bindBanner(container, storedAd)
        }
    }

    override fun clear(container: ViewGroup) {
        if (backend.usesAdMobBinders()) {
            AdMobBannerBinder.clear(container)
        } else {
            fake.clear(container)
        }
    }
}

class SelectingOnboardingFullNativeBinder(
    private val backend: DemoSdkBackend,
) : OnboardingFullNativeBinder {
    private val fake = FakeOnboardingFullNativeBinder()
    private val nativeRenderer = AdMobNativeRenderer()

    override fun bind(
        container: ViewGroup,
        storedAd: StoredAdView?,
        title: String,
    ): FakeNativeFullBoundViews {
        if (!backend.usesAdMobBinders() || storedAd == null) {
            return fake.bind(container, storedAd, title)
        }
        val handle = storedAd.sdkHandle as? AdMobNativeLoadedAd
        if (handle == null || !nativeRenderer.bind(container, handle)) {
            return fake.bind(container, storedAd, title)
        }
        val root = container.getChildAt(0) ?: container
        val media = root.findViewById<View>(com.example.adsmodule.admob.R.id.admob_native_media)
        val cta = root.findViewById<View>(com.example.adsmodule.admob.R.id.admob_native_cta)
        return FakeNativeFullBoundViews(
            root = root,
            media = media ?: root,
            cta = cta ?: root,
            clickableAssets = listOfNotNull(media, cta),
        )
    }

    override fun clear(container: ViewGroup) {
        if (backend.usesAdMobBinders()) {
            nativeRenderer.clear(container)
        } else {
            fake.clear(container)
        }
    }
}
