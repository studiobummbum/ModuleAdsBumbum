package com.example.adsmodule.core.onboarding.full

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.config.FullScreenTimingConfig
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.HostedFullscreenBeginResult
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.fullscreen.HostedFullscreenOutcome
import com.example.adsmodule.core.fullscreen.HostedFullscreenSession
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.onboarding.OnboardingFullResult
import com.example.adsmodule.core.refill.AdsConfigSnapshotProvider
import com.example.adsmodule.core.splash.AudienceEligibility
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Full 1/2 preload, hosted Fake Native Full presentation, close-delay,
 * auto-skip and the shared exit gate.
 *
 * auto_skip starts only after Close X becomes visible/enabled.
 */
public class OnboardingFullCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val normalAds: NormalScreenAdCoordinator,
    private val hosted: HostedFullscreenCoordinator,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val audience: AudienceType,
) {
    private val lock = Any()
    private val mutableSnapshot = MutableStateFlow<OnboardingFullSnapshot?>(null)
    private val active = AtomicReference<ActiveFull?>(null)
    private val resultClaimed = AtomicBoolean(false)

    public val snapshot: StateFlow<OnboardingFullSnapshot?> = mutableSnapshot.asStateFlow()

    public fun ensurePreloaded(fullIndex: Int) {
        require(fullIndex == 1 || fullIndex == 2)
        val configKey = OnboardingFullConfigKeys.adsKey(fullIndex)
        val screen = OnboardingFullScreenInstances.forIndex(fullIndex)
        val snapshot = snapshotProvider.current()
        val ads = snapshot?.adsConfig(configKey)
        if (ads == null || !ads.enable) return
        if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) return
        normalAds.ensureLoadedAsync(configKey, screen)
    }

    public fun startOrAttach(
        fullSessionId: FullSessionId,
        onboardingSessionId: OnboardingSessionId,
        fullIndex: Int,
        targetLogicalPage: Int?,
    ): OnboardingFullStartResult {
        require(fullIndex == 1 || fullIndex == 2)
        synchronized(lock) {
            val existing = active.get()
            if (existing != null && existing.fullSessionId == fullSessionId) {
                reattachTimers(existing)
                publish(existing)
                return OnboardingFullStartResult.Attached(existing.toSnapshot())
            }
            if (existing != null && !existing.exitGate.hasExited()) {
                // Different session requested while previous still active — cancel prior.
                cancelActiveLocked(existing, finishHosted = true)
            }
            resultClaimed.set(false)

            val timing = resolveTiming(fullIndex)
            val begin = hosted.begin(
                configKey = OnboardingFullConfigKeys.adsKey(fullIndex),
                screenInstanceId = OnboardingFullScreenInstances.forIndex(fullIndex),
                kind = FullscreenAdKind.NATIVE_FULL_ONBOARDING,
            )
            val controller = ActiveFull(
                fullSessionId = fullSessionId,
                onboardingSessionId = onboardingSessionId,
                fullIndex = fullIndex,
                targetLogicalPage = targetLogicalPage,
                timeDelayXButtonMillis = timing.timeDelayXButtonMillis,
                autoSkipMillis = timing.autoSkipMillis,
                closeDelay = CloseDelayController(scope, clock),
                autoSkip = AutoSkipController(scope, clock),
                exitGate = FullExitGate(),
            )
            when (begin) {
                is HostedFullscreenBeginResult.Rejected -> {
                    controller.adUnavailable = true
                    controller.phase = FullActivityPhase.WAITING_CLOSE_DELAY
                    controller.debugMessage = begin.reason
                    active.set(controller)
                    // Still allow swipe / X / auto-skip so navigation is never blocked.
                    startTimers(controller)
                    publish(controller)
                    return OnboardingFullStartResult.Attached(controller.toSnapshot())
                }
                is HostedFullscreenBeginResult.Started -> {
                    controller.hostedSession = begin.session
                    controller.phase = FullActivityPhase.WAITING_CLOSE_DELAY
                    active.set(controller)
                    startTimers(controller)
                    publish(controller)
                    return OnboardingFullStartResult.Attached(controller.toSnapshot())
                }
            }
        }
    }

    public fun onCloseClicked(fullSessionId: FullSessionId): Boolean {
        val controller = active.get() ?: return false
        if (controller.fullSessionId != fullSessionId) return false
        if (!controller.closeDelay.isReady()) return false
        return finishAndContinueOnce(controller, FullExitSource.CLOSE_X)
    }

    public fun onSwipeForward(fullSessionId: FullSessionId): Boolean {
        val controller = active.get() ?: return false
        if (controller.fullSessionId != fullSessionId) return false
        return finishAndContinueOnce(controller, FullExitSource.SWIPE_FORWARD)
    }

    public fun onAutoSkip(fullSessionId: FullSessionId): Boolean {
        val controller = active.get() ?: return false
        if (controller.fullSessionId != fullSessionId) return false
        return finishAndContinueOnce(controller, FullExitSource.AUTO_SKIP)
    }

    /** Debug / race simulation helper. */
    public fun requestExit(fullSessionId: FullSessionId, source: FullExitSource): Boolean {
        val controller = active.get() ?: return false
        if (controller.fullSessionId != fullSessionId) return false
        if (source == FullExitSource.CLOSE_X && !controller.closeDelay.isReady()) {
            return false
        }
        return finishAndContinueOnce(controller, source)
    }

    public fun consumeExitResult(fullSessionId: FullSessionId): OnboardingFullResult? {
        val controller = active.get() ?: return null
        if (controller.fullSessionId != fullSessionId) return null
        val source = controller.exitGate.winningSource() ?: return null
        if (!resultClaimed.compareAndSet(false, true)) return null
        return OnboardingFullResult(
            sessionId = controller.onboardingSessionId,
            fullSessionId = controller.fullSessionId,
            fullIndex = controller.fullIndex,
            targetLogicalPage = controller.targetLogicalPage,
            exitSource = source,
        )
    }

    public fun cancel(fullSessionId: FullSessionId) {
        synchronized(lock) {
            val controller = active.get() ?: return
            if (controller.fullSessionId != fullSessionId) return
            cancelActiveLocked(controller, finishHosted = true)
        }
    }

    public fun peekActive(): OnboardingFullSnapshot? = mutableSnapshot.value

    private fun startTimers(controller: ActiveFull) {
        controller.closeDelay.start(controller.timeDelayXButtonMillis) {
            onCloseReady(controller)
        }
        publish(controller)
        // Tick remaining times for debug overlay.
        scope.launch {
            while (active.get() === controller && !controller.exitGate.hasExited()) {
                publish(controller)
                kotlinx.coroutines.delay(100L)
            }
        }
    }

    private fun reattachTimers(controller: ActiveFull) {
        if (controller.exitGate.hasExited()) {
            publish(controller)
            return
        }
        val enabledAt = controller.closeDelay.enabledAtMillis()
            ?: (clock.nowMillis() + controller.timeDelayXButtonMillis)
        val closeReady = controller.closeVisible || clock.nowMillis() >= enabledAt
        if (closeReady) {
            controller.closeVisible = true
            controller.phase = FullActivityPhase.ACTIVE
            controller.closeDelay.attach(
                enabledAtMillis = enabledAt,
                alreadyReady = true,
            ) { /* already ready */ }
            val autoDeadline = controller.autoSkip.deadlineMillis()
            if (autoDeadline != null) {
                controller.autoSkip.attach(autoDeadline) {
                    finishAndContinueOnce(controller, FullExitSource.AUTO_SKIP)
                }
            } else if (!controller.autoSkip.isStarted()) {
                controller.autoSkip.start(controller.autoSkipMillis) {
                    finishAndContinueOnce(controller, FullExitSource.AUTO_SKIP)
                }
            }
        } else {
            controller.closeDelay.attach(
                enabledAtMillis = enabledAt,
                alreadyReady = false,
            ) {
                onCloseReady(controller)
            }
        }
    }

    private fun onCloseReady(controller: ActiveFull) {
        if (active.get() !== controller) return
        if (controller.exitGate.hasExited()) return
        controller.closeVisible = true
        controller.phase = FullActivityPhase.ACTIVE
        if (!controller.autoSkip.isStarted()) {
            controller.autoSkip.start(controller.autoSkipMillis) {
                finishAndContinueOnce(controller, FullExitSource.AUTO_SKIP)
            }
        }
        publish(controller)
    }

    private fun finishAndContinueOnce(
        controller: ActiveFull,
        source: FullExitSource,
    ): Boolean {
        return controller.exitGate.tryExit(source) {
            controller.phase = FullActivityPhase.EXITING
            controller.closeDelay.cancel()
            controller.autoSkip.cancel()
            controller.hostedSession?.let { session ->
                hosted.finish(session, HostedFullscreenOutcome.COMPLETED)
                controller.hostedSession = null
            }
            controller.phase = FullActivityPhase.COMPLETED
            controller.winningExitSource = source
            publish(controller)
        }
    }

    private fun cancelActiveLocked(controller: ActiveFull, finishHosted: Boolean) {
        controller.closeDelay.cancel()
        controller.autoSkip.cancel()
        if (finishHosted) {
            controller.hostedSession?.let { session ->
                hosted.finish(session, HostedFullscreenOutcome.FAILED)
                controller.hostedSession = null
            }
        }
        if (active.compareAndSet(controller, null)) {
            mutableSnapshot.value = null
        }
    }

    private fun resolveTiming(fullIndex: Int): FullScreenTimingConfig {
        val snapshot = snapshotProvider.current()
        val timing = snapshot?.get(OnboardingFullConfigKeys.timingKey(fullIndex))?.value
            as? FullScreenTimingConfig
        return timing ?: FullScreenTimingConfig(
            timeDelayXButtonMillis = 2_000L,
            autoSkipMillis = 3_000L,
        )
    }

    private fun publish(controller: ActiveFull) {
        mutableSnapshot.value = controller.toSnapshot()
    }

    private fun ActiveFull.toSnapshot(): OnboardingFullSnapshot {
        val remainingClose = if (closeVisible) {
            0L
        } else {
            closeDelay.remainingMillis()
        }
        val remainingAuto = if (closeVisible) {
            autoSkip.remainingMillis()
        } else {
            null
        }
        return OnboardingFullSnapshot(
            fullSessionId = fullSessionId,
            onboardingSessionId = onboardingSessionId,
            fullIndex = fullIndex,
            targetLogicalPage = targetLogicalPage,
            phase = phase,
            gateState = when {
                exitGate.hasExited() && phase == FullActivityPhase.COMPLETED ->
                    FullGateState.COMPLETED
                exitGate.hasExited() -> FullGateState.EXITING
                else -> FullGateState.OPEN
            },
            closeVisible = closeVisible,
            closeEnabledAtMillis = closeDelay.enabledAtMillis(),
            autoSkipDeadlineMillis = autoSkip.deadlineMillis(),
            remainingCloseDelayMillis = remainingClose,
            remainingAutoSkipMillis = remainingAuto,
            showRequestId = hostedSession?.showRequestId,
            objectId = hostedSession?.objectId,
            storedAd = hostedSession?.storedAd,
            winningExitSource = winningExitSource ?: exitGate.winningSource(),
            debugMessage = debugMessage,
            adUnavailable = adUnavailable,
        )
    }

    private class ActiveFull(
        val fullSessionId: FullSessionId,
        val onboardingSessionId: OnboardingSessionId,
        val fullIndex: Int,
        val targetLogicalPage: Int?,
        val timeDelayXButtonMillis: Long,
        val autoSkipMillis: Long,
        val closeDelay: CloseDelayController,
        val autoSkip: AutoSkipController,
        val exitGate: FullExitGate,
        var hostedSession: HostedFullscreenSession? = null,
        var phase: FullActivityPhase = FullActivityPhase.IDLE,
        var closeVisible: Boolean = false,
        var winningExitSource: FullExitSource? = null,
        var debugMessage: String? = null,
        var adUnavailable: Boolean = false,
    )
}
