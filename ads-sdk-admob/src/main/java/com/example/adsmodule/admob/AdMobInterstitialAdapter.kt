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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine

public class AdMobInterstitialAdapter(
    context: Context,
    private val resolver: AdMobAdUnitResolver,
) : AdSdkAdapter {
    private val appContext = context.applicationContext

    override val supportedFormats: Set<AdFormat> = setOf(AdFormat.INTERSTITIAL)

    override suspend fun load(request: AdLoadRequest): AdLoadResult {
        if (request.format != AdFormat.INTERSTITIAL) {
            return AdLoadResult.Failure("Adapter for INTERSTITIAL cannot load ${request.format}")
        }
        AdMobSdkInitializer.ensureInitialized(appContext)
        val resolved = resolver.resolve(AdFormat.INTERSTITIAL, request.adUnit)
        android.util.Log.i(
            "AdMobInterstitial",
            "load request=${request.adUnit} resolved=${resolved.adUnit} remapped=${resolved.remappedFrom}",
        )
        return AdMobMainThread.onMain {
            suspendCancellableCoroutine { continuation ->
                InterstitialAd.load(
                    appContext,
                    resolved.adUnit,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            android.util.Log.i(
                                "AdMobInterstitial",
                                "onAdLoaded unit=${resolved.adUnit}",
                            )
                            if (!continuation.isActive) {
                                ad.fullScreenContentCallback = null
                                return
                            }
                            continuation.resume(
                                AdLoadResult.Success(
                                    AdMobInterstitialLoadedAd(
                                        adUnit = resolved.adUnit,
                                        adRef = AtomicReference(ad),
                                    ),
                                ),
                            )
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            android.util.Log.w(
                                "AdMobInterstitial",
                                "onAdFailedToLoad code=${error.code} msg=${error.message} domain=${error.domain}",
                            )
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
        val handle = request.handle as? AdMobInterstitialLoadedAd
            ?: return kotlinx.coroutines.flow.flowOf(
                AdShowEvent.Fail(request.showRequestId, "Invalid interstitial handle"),
            )
        val ad = handle.takeAd()
            ?: return kotlinx.coroutines.flow.flowOf(
                AdShowEvent.Fail(request.showRequestId, "Interstitial already consumed or destroyed"),
            )
        return AdMobFullscreenShowHelper.show(
            showRequestId = request.showRequestId,
            activity = request.host.requireActivityOrNull(),
            attachCallback = { ad.fullScreenContentCallback = it },
            present = { activity -> ad.show(activity) },
        )
    }
}
