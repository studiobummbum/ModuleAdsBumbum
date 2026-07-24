package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.FullscreenShowResult
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.load.WeightedLoadRequest
import com.example.adsmodule.core.load.WeightedLoadResult
import com.example.adsmodule.core.refill.AdsConfigSnapshotProvider
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.splash.AudienceEligibility
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StorageSlotKey
import com.example.adsmodule.sdk.AdPresentationHost
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * First-wins Onboarding terminal: try `inter_onboarding_config_1`, then open Home once.
 */
public class OnboardingFinishInterCoordinator(
    private val scope: CoroutineScope,
    private val idGenerator: IdGenerator,
    private val loader: WeightedListLoader,
    private val storage: AdStorage,
    private val fullscreen: FullscreenShowCoordinator,
    private val refillScheduler: WholeListRefillScheduler,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val audience: AudienceType,
    private val screenInstanceId: ScreenInstanceId = ScreenInstanceId("onboarding-finish-1"),
    private val presentationHostProvider: () -> AdPresentationHost? = { null },
) {
    private val mutex = Mutex()
    private val finishedOnce = AtomicBoolean(false)

    private val mutableResults = MutableSharedFlow<OnboardingFinishResult>(
        extraBufferCapacity = 1,
        replay = 0,
    )
    public val results: SharedFlow<OnboardingFinishResult> = mutableResults.asSharedFlow()

    public fun finishAsync(onComplete: (OnboardingFinishResult) -> Unit) {
        scope.launch {
            val result = finish()
            onComplete(result)
        }
    }

    public suspend fun finish(): OnboardingFinishResult {
        if (!finishedOnce.compareAndSet(false, true)) {
            val fallback = OnboardingFinishResult.HomeFallback("already finished")
            mutableResults.tryEmit(fallback)
            return fallback
        }
        return mutex.withLock { finishLocked() }
    }

    public fun resetForTests() {
        finishedOnce.set(false)
    }

    private suspend fun finishLocked(): OnboardingFinishResult {
        val snapshot = snapshotProvider.current()
        if (snapshot == null) {
            return emitFallback("missing snapshot")
        }
        val ads = snapshot.adsConfig(OnboardingFinishKeys.INTER_ONBOARDING)
        if (ads == null) {
            return emitFallback("missing inter_onboarding config")
        }
        if (!ads.enable) {
            return emitFallback("inter_onboarding enable=false")
        }
        if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) {
            return emitFallback("audience ineligible")
        }

        if (storage.peekReady(OnboardingFinishKeys.INTER_ONBOARDING, screenInstanceId) == null) {
            val cycleId = LoadCycleId(idGenerator.nextId())
            when (
                val load = loader.load(
                    WeightedLoadRequest(
                        cycleId = cycleId,
                        configKey = OnboardingFinishKeys.INTER_ONBOARDING,
                        screenInstanceId = screenInstanceId,
                        snapshot = snapshot,
                    ),
                )
            ) {
                is WeightedLoadResult.Success -> {
                    when (val put = storage.putReady(load.storedAd)) {
                        is PutResult.Accepted -> Unit
                        is PutResult.Rejected -> {
                            load.storedAd.sdkHandle.destroy()
                            return emitFallback("put rejected: ${put.reason}")
                        }
                    }
                }
                else -> return emitFallback("load terminal: ${load::class.simpleName}")
            }
        }

        refillScheduler.activate(
            slot = StorageSlotKey(OnboardingFinishKeys.INTER_ONBOARDING, screenInstanceId),
            targetReadyCount = 1,
            refillIfDeficit = true,
        )

        val reserved = storage.reserveNormal(
            OnboardingFinishKeys.INTER_ONBOARDING,
            screenInstanceId,
        )
        if (reserved !is ReserveResult.Accepted) {
            return emitFallback((reserved as ReserveResult.Rejected).reason)
        }
        refillScheduler.requestRefill(
            StorageSlotKey(OnboardingFinishKeys.INTER_ONBOARDING, screenInstanceId),
        )

        return when (
            val result = fullscreen.show(
                reserved.reservation.reservationId,
                FullscreenAdKind.INTER_ONBOARDING,
                presentationHostProvider(),
            )
        ) {
            is FullscreenShowResult.Dismissed -> {
                val shown = OnboardingFinishResult.InterShownThenHome(
                    showRequestId = result.showRequestId,
                    objectId = reserved.storedAd.objectId,
                    storedAd = reserved.storedAd,
                )
                mutableResults.tryEmit(shown)
                shown
            }
            is FullscreenShowResult.Failed -> {
                emitFallback("show failed: ${result.reason}")
            }
            is FullscreenShowResult.Rejected -> {
                storage.release(reserved.reservation.reservationId)
                emitFallback("show rejected: ${result.reason}")
            }
        }
    }

    private fun emitFallback(reason: String): OnboardingFinishResult {
        val fallback = OnboardingFinishResult.HomeFallback(reason)
        mutableResults.tryEmit(fallback)
        return fallback
    }
}
