package com.example.adsmodule.admob

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

public class AdMobInterstitialLoadedAd internal constructor(
    override val adUnit: String,
    private val adRef: AtomicReference<InterstitialAd?>,
) : SdkLoadedAdHandle {
    override val format: AdFormat = AdFormat.INTERSTITIAL
    private val destroyed = AtomicBoolean(false)

    internal fun takeAd(): InterstitialAd? = adRef.getAndSet(null)

    internal fun peekAd(): InterstitialAd? = adRef.get()

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        adRef.getAndSet(null)?.fullScreenContentCallback = null
    }
}

public class AdMobAppOpenLoadedAd internal constructor(
    override val adUnit: String,
    private val adRef: AtomicReference<AppOpenAd?>,
) : SdkLoadedAdHandle {
    override val format: AdFormat = AdFormat.APP_OPEN
    private val destroyed = AtomicBoolean(false)

    internal fun takeAd(): AppOpenAd? = adRef.getAndSet(null)

    internal fun peekAd(): AppOpenAd? = adRef.get()

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        adRef.getAndSet(null)?.fullScreenContentCallback = null
    }
}

public class AdMobNativeLoadedAd internal constructor(
    override val format: AdFormat,
    override val adUnit: String,
    private val adRef: AtomicReference<NativeAd?>,
) : SdkLoadedAdHandle {
    private val destroyed = AtomicBoolean(false)

    public fun peekNativeAd(): NativeAd? = adRef.get()

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        adRef.getAndSet(null)?.destroy()
    }
}

public class AdMobBannerLoadedAd internal constructor(
    override val adUnit: String,
    private val adViewRef: AtomicReference<AdView?>,
) : SdkLoadedAdHandle {
    override val format: AdFormat = AdFormat.BANNER
    private val destroyed = AtomicBoolean(false)

    public fun peekAdView(): AdView? = adViewRef.get()

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        val view = adViewRef.getAndSet(null) ?: return
        view.destroy()
    }
}
