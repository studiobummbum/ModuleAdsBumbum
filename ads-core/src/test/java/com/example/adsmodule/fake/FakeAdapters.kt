package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import kotlinx.coroutines.flow.Flow

public class FakeInterstitialAdapter(
    controller: FakeAdsSdkController,
) : AdSdkAdapter by FakeFormatAdapter(AdFormat.INTERSTITIAL, controller)

public class FakeAppOpenAdapter(
    controller: FakeAdsSdkController,
) : AdSdkAdapter by FakeFormatAdapter(AdFormat.APP_OPEN, controller)

public class FakeNativeAdapter(
    controller: FakeAdsSdkController,
) : AdSdkAdapter by FakeFormatAdapter(AdFormat.NATIVE, controller)

public class FakeBannerAdapter(
    controller: FakeAdsSdkController,
) : AdSdkAdapter by FakeFormatAdapter(AdFormat.BANNER, controller)

public class FakeNativeFullscreenAdapter(
    controller: FakeAdsSdkController,
) : AdSdkAdapter by FakeFormatAdapter(AdFormat.NATIVE_FULLSCREEN, controller)

private class FakeFormatAdapter(
    private val format: AdFormat,
    private val controller: FakeAdsSdkController,
) : AdSdkAdapter {
    override val supportedFormats: Set<AdFormat> = setOf(format)

    override suspend fun load(request: AdLoadRequest): AdLoadResult =
        controller.engine.load(format, request)

    override fun show(request: AdShowRequest): Flow<AdShowEvent> =
        controller.engine.show(format, request)
}
