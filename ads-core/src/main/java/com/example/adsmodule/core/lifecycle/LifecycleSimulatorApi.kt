package com.example.adsmodule.core.lifecycle

import com.example.adsmodule.core.AdClickTokenId
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.fullscreen.FullscreenLockSnapshot
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.turnback.AdClickTokenSnapshot
import com.example.adsmodule.core.turnback.AdClickTokenStore
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public debug façade for Phase 14 LifecycleSimulatorFragment.
 *
 * Exposes only public snapshots/actions; does not reach into private coordinator fields.
 */
public class LifecycleSimulatorApi(
    private val lifecycle: AdsLifecycleCoordinator,
    private val fullscreenLock: GlobalFullscreenLock,
    private val tokenStore: AdClickTokenStore,
    private val clock: Clock,
) {
    public val lifecycleSnapshot: StateFlow<AdsLifecycleSnapshot> = lifecycle.snapshot
    public val lifecycleEvents: SharedFlow<AdsLifecycleEvent> = lifecycle.events
    public val fullscreenLockSnapshot: StateFlow<FullscreenLockSnapshot> = fullscreenLock.snapshot

    public fun bindSession(sessionId: SessionId): AdsLifecycleSnapshot =
        lifecycle.bindSession(sessionId)

    public fun simulateAdClick(
        showRequestId: ShowRequestId = ShowRequestId("sim-click-${clock.nowMillis()}"),
        ttlMillis: Long? = null,
    ): AdClickTokenId? {
        return if (ttlMillis == null) {
            lifecycle.onAdClick(showRequestId)
        } else {
            lifecycle.onAdClick(showRequestId, ttlMillis)
        }
    }

    public fun simulateHomeBackground(): AdsLifecycleTransitionResult =
        lifecycle.onBackground(hint = BackgroundReason.USER_BACKGROUND)

    public fun simulateAdClickBackground(): AdsLifecycleTransitionResult =
        lifecycle.onBackground(hint = BackgroundReason.AD_CLICK)

    public fun simulateSystemInterruption(): AdsLifecycleTransitionResult =
        lifecycle.onBackground(hint = BackgroundReason.SYSTEM_INTERRUPTION)

    public fun simulateUnknownBackground(): AdsLifecycleTransitionResult =
        lifecycle.onBackground(hint = BackgroundReason.UNKNOWN)

    public fun simulateForeground(): AdsLifecycleTransitionResult = lifecycle.onForeground()

    public fun setSplashActive(active: Boolean): AdsLifecycleSnapshot =
        lifecycle.setSplashActive(active)

    public fun setActivityValid(valid: Boolean): AdsLifecycleSnapshot =
        lifecycle.setActivityValid(valid)

    public fun setTurnbackPending(pending: Boolean): AdsLifecycleSnapshot =
        lifecycle.setTurnbackPending(pending)

    public fun clearTurnbackPending(): AdsLifecycleSnapshot = lifecycle.clearTurnbackPending()

    public fun expireTokensByAdvancingClock(advanceMillis: Long): AdClickTokenSnapshot {
        require(advanceMillis >= 0L) { "advanceMillis must not be negative" }
        // Clock advancement is owned by the inject FakeClock in tests/debug hosts.
        // This API only re-evaluates suppression after the host advances time.
        return tokenSnapshot()
    }

    public fun reevaluateAfterClockChange(): AdsLifecycleSnapshot = lifecycle.refreshSnapshot()

    public fun evaluateAppOpenSuppression(): AppOpenSuppressionResult =
        lifecycle.evaluateAppOpenSuppression()

    public fun tokenSnapshot(): AdClickTokenSnapshot = tokenStore.snapshot()

    public fun hasValidClickToken(sessionId: SessionId): Boolean =
        tokenStore.hasValidToken(sessionId)

    public fun inspectorSnapshot(): LifecycleSimulatorSnapshot {
        val life = lifecycle.refreshSnapshot()
        return LifecycleSimulatorSnapshot(
            lifecycle = life,
            fullscreenLock = fullscreenLock.snapshot.value,
            tokens = tokenStore.snapshot(),
            capturedAtMillis = clock.nowMillis(),
        )
    }
}

public data class LifecycleSimulatorSnapshot(
    val lifecycle: AdsLifecycleSnapshot,
    val fullscreenLock: FullscreenLockSnapshot,
    val tokens: AdClickTokenSnapshot,
    val capturedAtMillis: Long,
)
