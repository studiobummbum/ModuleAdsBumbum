package com.example.adsmodule.admob

import android.content.Context
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdsSdkModule

/**
 * Factory for AdMob format adapters sharing one runtime mode / resolver.
 */
public object AdMobAdsSdkModule {
    public val status: String = "${AdsSdkModule.NAME} admob adapter ready"

    public fun create(
        context: Context,
        mode: AdMobRuntimeMode = AdMobRuntimeMode.TEST,
    ): AdMobAdsSdk {
        AdMobSdkInitializer.ensureInitialized(context)
        return AdMobAdsSdk(
            context = context.applicationContext,
            resolver = AdMobAdUnitResolver(mode),
            mode = mode,
        )
    }
}

public class AdMobAdsSdk internal constructor(
    context: Context,
    public val resolver: AdMobAdUnitResolver,
    public val mode: AdMobRuntimeMode,
) {
    public val interstitialAdapter: AdMobInterstitialAdapter =
        AdMobInterstitialAdapter(context, resolver)
    public val appOpenAdapter: AdMobAppOpenAdapter =
        AdMobAppOpenAdapter(context, resolver)
    public val nativeAdapter: AdMobNativeAdapter =
        AdMobNativeAdapter(context, resolver)
    public val bannerAdapter: AdMobBannerAdapter =
        AdMobBannerAdapter(context, resolver)
    public val nativeFullscreenAdapter: AdMobNativeFullscreenAdapter =
        AdMobNativeFullscreenAdapter(context, resolver)

    public val adapters: List<AdSdkAdapter> = listOf(
        interstitialAdapter,
        appOpenAdapter,
        nativeAdapter,
        bannerAdapter,
        nativeFullscreenAdapter,
    )

    public fun adapterFor(format: AdFormat): AdSdkAdapter =
        adapters.single { format in it.supportedFormats }
}
