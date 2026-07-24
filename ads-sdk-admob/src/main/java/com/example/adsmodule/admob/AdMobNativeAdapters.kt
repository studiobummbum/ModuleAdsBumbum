package com.example.adsmodule.admob

import android.content.Context
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine

public class AdMobNativeAdapter(
    context: Context,
    private val resolver: AdMobAdUnitResolver,
) : AdSdkAdapter {
    private val appContext = context.applicationContext

    override val supportedFormats: Set<AdFormat> = setOf(AdFormat.NATIVE)

    override suspend fun load(request: AdLoadRequest): AdLoadResult =
        loadNative(request, AdFormat.NATIVE)

    override fun show(request: AdShowRequest): Flow<AdShowEvent> =
        showNative(request, AdFormat.NATIVE)

    internal suspend fun loadNative(request: AdLoadRequest, format: AdFormat): AdLoadResult {
        if (request.format != format) {
            return AdLoadResult.Failure("Adapter for $format cannot load ${request.format}")
        }
        AdMobSdkInitializer.ensureInitialized(appContext)
        val resolved = resolver.resolve(format, request.adUnit)
        return AdMobMainThread.onMain {
            suspendCancellableCoroutine { continuation ->
                val loader = AdLoader.Builder(appContext, resolved.adUnit)
                    .forNativeAd { nativeAd: NativeAd ->
                        if (!continuation.isActive) {
                            nativeAd.destroy()
                            return@forNativeAd
                        }
                        continuation.resume(
                            AdLoadResult.Success(
                                AdMobNativeLoadedAd(
                                    format = format,
                                    adUnit = resolved.adUnit,
                                    adRef = AtomicReference(nativeAd),
                                ),
                            ),
                        )
                    }
                    .withAdListener(
                        object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                if (!continuation.isActive) return
                                continuation.resume(
                                    AdLoadResult.Failure(
                                        reason = error.message.ifBlank {
                                            "load failed code=${error.code}"
                                        },
                                        retryable = true,
                                    ),
                                )
                            }
                        },
                    )
                    .withNativeAdOptions(NativeAdOptions.Builder().build())
                    .build()
                loader.loadAd(AdRequest.Builder().build())
            }
        }
    }

    internal fun showNative(request: AdShowRequest, expectedFormat: AdFormat): Flow<AdShowEvent> {
        val handle = request.handle as? AdMobNativeLoadedAd
        if (handle == null || handle.format != expectedFormat) {
            return flowOf(AdShowEvent.Fail(request.showRequestId, "Invalid native handle"))
        }
        if (handle.peekNativeAd() == null) {
            return flowOf(AdShowEvent.Fail(request.showRequestId, "Native ad destroyed"))
        }
        // Presentation is Activity-hosted via AdMobNativeRenderer. Emit bind-time
        // presentation events and complete; HostedFullscreenCoordinator owns dismiss.
        return kotlinx.coroutines.flow.flow {
            emit(AdShowEvent.Shown(request.showRequestId))
            emit(AdShowEvent.Impression(request.showRequestId))
        }
    }
}

public class AdMobNativeFullscreenAdapter(
    context: Context,
    private val resolver: AdMobAdUnitResolver,
) : AdSdkAdapter {
    private val delegate = AdMobNativeAdapter(context, resolver)

    override val supportedFormats: Set<AdFormat> = setOf(AdFormat.NATIVE_FULLSCREEN)

    override suspend fun load(request: AdLoadRequest): AdLoadResult =
        delegate.loadNative(request.copy(format = AdFormat.NATIVE_FULLSCREEN), AdFormat.NATIVE_FULLSCREEN)

    override fun show(request: AdShowRequest): Flow<AdShowEvent> =
        delegate.showNative(request, AdFormat.NATIVE_FULLSCREEN)
}
