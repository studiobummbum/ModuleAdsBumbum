package com.example.adsmodule.admob

import android.content.Context
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine

public class AdMobAppOpenAdapter(
    context: Context,
    private val resolver: AdMobAdUnitResolver,
) : AdSdkAdapter {
    private val appContext = context.applicationContext

    override val supportedFormats: Set<AdFormat> = setOf(AdFormat.APP_OPEN)

    override suspend fun load(request: AdLoadRequest): AdLoadResult {
        if (request.format != AdFormat.APP_OPEN) {
            return AdLoadResult.Failure("Adapter for APP_OPEN cannot load ${request.format}")
        }
        AdMobSdkInitializer.awaitInitialized(appContext)
        val resolved = resolver.resolve(AdFormat.APP_OPEN, request.adUnit)
        return AdMobMainThread.onMain {
            suspendCancellableCoroutine { continuation ->
                AppOpenAd.load(
                    appContext,
                    resolved.adUnit,
                    AdRequest.Builder().build(),
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            if (!continuation.isActive) {
                                ad.fullScreenContentCallback = null
                                return
                            }
                            continuation.resume(
                                AdLoadResult.Success(
                                    AdMobAppOpenLoadedAd(
                                        adUnit = resolved.adUnit,
                                        adRef = AtomicReference(ad),
                                    ),
                                ),
                            )
                        }

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
            }
        }
    }

    override fun show(request: AdShowRequest): Flow<AdShowEvent> {
        val handle = request.handle as? AdMobAppOpenLoadedAd
            ?: return kotlinx.coroutines.flow.flowOf(
                AdShowEvent.Fail(request.showRequestId, "Invalid app open handle"),
            )
        val ad = handle.takeAd()
            ?: return kotlinx.coroutines.flow.flowOf(
                AdShowEvent.Fail(request.showRequestId, "App open already consumed or destroyed"),
            )
        return AdMobFullscreenShowHelper.show(
            showRequestId = request.showRequestId,
            activity = request.host.requireActivityOrNull(),
            attachCallback = { ad.fullScreenContentCallback = it },
            present = { activity -> ad.show(activity) },
        )
    }
}
