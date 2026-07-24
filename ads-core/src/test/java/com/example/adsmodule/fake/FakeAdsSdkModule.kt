package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdsSdkModule

/**
 * Factory for a controller and the five format-specific fake adapters that share it.
 */
public object FakeAdsSdkModule {
    public val status: String = "${AdsSdkModule.NAME} fake adapter ready"

    public fun create(
        controller: FakeAdsSdkController = FakeAdsSdkController(),
    ): FakeAdsSdk = FakeAdsSdk(controller)
}

public class FakeAdsSdk internal constructor(
    public val controller: FakeAdsSdkController,
) {
    public val interstitialAdapter: FakeInterstitialAdapter =
        FakeInterstitialAdapter(controller)
    public val appOpenAdapter: FakeAppOpenAdapter = FakeAppOpenAdapter(controller)
    public val nativeAdapter: FakeNativeAdapter = FakeNativeAdapter(controller)
    public val bannerAdapter: FakeBannerAdapter = FakeBannerAdapter(controller)
    public val nativeFullscreenAdapter: FakeNativeFullscreenAdapter =
        FakeNativeFullscreenAdapter(controller)

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
