package com.example.adsmodule.admob

import android.content.Context
import android.view.ViewGroup
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine

public class AdMobBannerAdapter(
    context: Context,
    private val resolver: AdMobAdUnitResolver,
) : AdSdkAdapter {
    private val appContext = context.applicationContext

    override val supportedFormats: Set<AdFormat> = setOf(AdFormat.BANNER)

    override suspend fun load(request: AdLoadRequest): AdLoadResult {
        if (request.format != AdFormat.BANNER) {
            return AdLoadResult.Failure("Adapter for BANNER cannot load ${request.format}")
        }
        AdMobSdkInitializer.ensureInitialized(appContext)
        val resolved = resolver.resolve(AdFormat.BANNER, request.adUnit)
        return AdMobMainThread.onMain {
            suspendCancellableCoroutine { continuation ->
                val adView = AdView(appContext)
                adView.setAdSize(AdSize.BANNER)
                adView.adUnitId = resolved.adUnit
                adView.adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        if (!continuation.isActive) {
                            adView.destroy()
                            return
                        }
                        continuation.resume(
                            AdLoadResult.Success(
                                AdMobBannerLoadedAd(
                                    adUnit = resolved.adUnit,
                                    adViewRef = AtomicReference(adView),
                                ),
                            ),
                        )
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        adView.destroy()
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
                }
                continuation.invokeOnCancellation {
                    adView.destroy()
                }
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    }

    override fun show(request: AdShowRequest): Flow<AdShowEvent> {
        val handle = request.handle as? AdMobBannerLoadedAd
            ?: return flowOf(AdShowEvent.Fail(request.showRequestId, "Invalid banner handle"))
        if (handle.peekAdView() == null) {
            return flowOf(AdShowEvent.Fail(request.showRequestId, "Banner destroyed"))
        }
        return kotlinx.coroutines.flow.flow {
            emit(AdShowEvent.Shown(request.showRequestId))
            emit(AdShowEvent.Impression(request.showRequestId))
        }
    }
}

/**
 * Attaches a loaded banner [AdView] into a container. Caller owns destroy via handle.
 */
public object AdMobBannerBinder {
    public fun bind(container: ViewGroup, handle: AdMobBannerLoadedAd): Boolean {
        val adView = handle.peekAdView() ?: return false
        container.removeAllViews()
        (adView.parent as? ViewGroup)?.removeView(adView)
        container.addView(
            adView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.visibility = android.view.View.VISIBLE
        return true
    }

    public fun clear(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = android.view.View.GONE
    }
}
