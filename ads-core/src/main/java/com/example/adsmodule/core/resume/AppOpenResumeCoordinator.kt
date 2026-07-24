package com.example.adsmodule.core.resume

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.FullscreenShowResult
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.lifecycle.AdsLifecycleTransitionResult
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
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.sdk.AdPresentationHost
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shows App Open Resume after a process foreground transition when suppression allows it.
 *
 * Does not invent timeout or min_background fields absent from `appopen_resume_config_1`.
 */
public class AppOpenResumeCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val lifecycle: AdsLifecycleCoordinator,
    private val loader: WeightedListLoader,
    private val storage: AdStorage,
    private val fullscreen: FullscreenShowCoordinator,
    private val refillScheduler: WholeListRefillScheduler,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val audience: AudienceType,
    private val screenInstanceId: ScreenInstanceId = ScreenInstanceId("appopen-resume-1"),
    private val presentationHostProvider: () -> AdPresentationHost? = { null },
) {
    private val mutex = Mutex()
    private val showInFlight = AtomicBoolean(false)
    private val lastResult = AtomicReference<AppOpenResumeResult?>(null)
    private var showJob: Job? = null

    private val mutableLastResult = MutableStateFlow<AppOpenResumeResult?>(null)
    public val lastShowResult: StateFlow<AppOpenResumeResult?> = mutableLastResult.asStateFlow()

    public fun onProcessBackground() {
        lifecycle.onBackground()
    }

    public fun onProcessForeground() {
        when (val transition = lifecycle.onForeground()) {
            is AdsLifecycleTransitionResult.Ignored -> {
                publish(AppOpenResumeResult.Skipped("foreground ignored: ${transition.reason}"))
            }
            is AdsLifecycleTransitionResult.Accepted -> {
                showJob?.cancel()
                showJob = scope.launch {
                    tryShowResume()
                }
            }
        }
    }

    /**
     * Explicit attempt used by Home debug buttons after simulated foreground.
     */
    public fun tryShowResumeAsync() {
        showJob?.cancel()
        showJob = scope.launch { tryShowResume() }
    }

    public suspend fun tryShowResume(): AppOpenResumeResult {
        if (!showInFlight.compareAndSet(false, true)) {
            return AppOpenResumeResult.Rejected("app open resume already in flight").also(::publish)
        }
        return try {
            mutex.withLock { tryShowResumeLocked() }
        } finally {
            showInFlight.set(false)
        }
    }

    private suspend fun tryShowResumeLocked(): AppOpenResumeResult {
        val suppression = lifecycle.evaluateAppOpenSuppression()
        if (suppression.suppressed) {
            return AppOpenResumeResult.Suppressed(suppression).also(::publish)
        }

        val snapshot = snapshotProvider.current()
            ?: return AppOpenResumeResult.Skipped("missing snapshot").also(::publish)
        val ads = snapshot.adsConfig(AppOpenResumeKeys.APPOPEN_RESUME)
            ?: return AppOpenResumeResult.Skipped("missing appopen_resume config").also(::publish)
        if (!ads.enable) {
            return AppOpenResumeResult.Skipped("appopen_resume enable=false").also(::publish)
        }
        if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) {
            return AppOpenResumeResult.Skipped("audience ineligible").also(::publish)
        }

        val peek = storage.peekReady(AppOpenResumeKeys.APPOPEN_RESUME, screenInstanceId)
        if (peek == null) {
            val cycleId = LoadCycleId(idGenerator.nextId())
            when (
                val load = loader.load(
                    WeightedLoadRequest(
                        cycleId = cycleId,
                        configKey = AppOpenResumeKeys.APPOPEN_RESUME,
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
                            return AppOpenResumeResult.Failed(put.reason).also(::publish)
                        }
                    }
                }
                else -> {
                    return AppOpenResumeResult.Skipped(
                        "load terminal: ${load::class.simpleName}",
                    ).also(::publish)
                }
            }
        }

        refillScheduler.activate(
            slot = StorageSlotKey(AppOpenResumeKeys.APPOPEN_RESUME, screenInstanceId),
            targetReadyCount = 1,
            refillIfDeficit = true,
        )

        // Re-check suppression immediately before lock acquire.
        val suppressionAgain = lifecycle.evaluateAppOpenSuppression()
        if (suppressionAgain.suppressed) {
            return AppOpenResumeResult.Suppressed(suppressionAgain).also(::publish)
        }

        val reserved = storage.reserveNormal(AppOpenResumeKeys.APPOPEN_RESUME, screenInstanceId)
        if (reserved !is ReserveResult.Accepted) {
            return AppOpenResumeResult.Rejected((reserved as ReserveResult.Rejected).reason)
                .also(::publish)
        }
        refillScheduler.requestRefill(
            StorageSlotKey(AppOpenResumeKeys.APPOPEN_RESUME, screenInstanceId),
        )

        return when (
            val result = fullscreen.show(
                reserved.reservation.reservationId,
                FullscreenAdKind.APP_OPEN,
                presentationHostProvider(),
            )
        ) {
            is FullscreenShowResult.Dismissed -> {
                AppOpenResumeResult.Shown(
                    showRequestId = result.showRequestId,
                    objectId = reserved.storedAd.objectId,
                    storedAd = reserved.storedAd,
                ).also(::publish)
            }
            is FullscreenShowResult.Failed -> {
                AppOpenResumeResult.Failed(
                    reason = result.reason,
                    showRequestId = result.showRequestId,
                ).also(::publish)
            }
            is FullscreenShowResult.Rejected -> {
                storage.release(reserved.reservation.reservationId)
                AppOpenResumeResult.Rejected(result.reason).also(::publish)
            }
        }
    }

    public fun ensurePreloaded() {
        scope.launch {
            val snapshot = snapshotProvider.current() ?: return@launch
            val ads = snapshot.adsConfig(AppOpenResumeKeys.APPOPEN_RESUME) ?: return@launch
            if (!ads.enable) return@launch
            if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) return@launch
            if (storage.peekReady(AppOpenResumeKeys.APPOPEN_RESUME, screenInstanceId) != null) {
                return@launch
            }
            refillScheduler.activate(
                slot = StorageSlotKey(AppOpenResumeKeys.APPOPEN_RESUME, screenInstanceId),
                targetReadyCount = 1,
                refillIfDeficit = true,
            )
            val cycleId = LoadCycleId(idGenerator.nextId())
            when (
                val load = loader.load(
                    WeightedLoadRequest(
                        cycleId = cycleId,
                        configKey = AppOpenResumeKeys.APPOPEN_RESUME,
                        screenInstanceId = screenInstanceId,
                        snapshot = snapshot,
                    ),
                )
            ) {
                is WeightedLoadResult.Success -> {
                    when (val put = storage.putReady(load.storedAd)) {
                        is PutResult.Accepted -> Unit
                        is PutResult.Rejected -> load.storedAd.sdkHandle.destroy()
                    }
                }
                else -> Unit
            }
        }
    }

    private fun publish(result: AppOpenResumeResult) {
        lastResult.set(result)
        mutableLastResult.value = result
    }
}
