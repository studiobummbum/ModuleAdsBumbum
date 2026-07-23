package com.example.adsmodule.core.lifecycle

import com.example.adsmodule.core.AdClickTokenId
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.FullscreenShowEvent
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.turnback.AdClickTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Correlates fullscreen clicks, background/foreground transitions, click tokens and
 * turnback-pending state for App Open Resume suppression.
 *
 * Policy:
 * - AD_CLICK only when a valid click token exists for the session
 * - USER_BACKGROUND / Home never creates turnback-pending
 * - SYSTEM_INTERRUPTION and UNKNOWN do not create turnback-pending
 * - turnback-pending is set only when returning from an AD_CLICK background
 */
public class AdsLifecycleCoordinator(
    private val sessionTracker: ForegroundSessionTracker,
    private val tokenStore: AdClickTokenStore,
    private val fullscreenLock: GlobalFullscreenLock,
    private val clock: Clock,
    private val defaultClickTokenTtlMillis: Long,
    private val scope: CoroutineScope,
) {
    init {
        require(defaultClickTokenTtlMillis > 0L) {
            "defaultClickTokenTtlMillis must be positive"
        }
    }

    private val lock = Any()
    private var clickCollectJob: Job? = null
    private val mutableSnapshot = MutableStateFlow(
        AdsLifecycleSnapshot(
            sessionId = null,
            splashActive = false,
            turnbackPending = false,
            activityValid = true,
            lastIssuedTokenId = null,
            lastBackgroundReason = null,
            foreground = sessionTracker.snapshot.value,
            appOpenSuppression = evaluateSuppressionLocked(
                sessionId = null,
                splashActive = false,
                turnbackPending = false,
                activityValid = true,
            ),
        ),
    )
    private val mutableEvents = MutableSharedFlow<AdsLifecycleEvent>(
        extraBufferCapacity = 64,
        replay = 0,
    )

    public val snapshot: StateFlow<AdsLifecycleSnapshot> = mutableSnapshot.asStateFlow()
    public val events: SharedFlow<AdsLifecycleEvent> = mutableEvents.asSharedFlow()

    public fun bindSession(sessionId: SessionId): AdsLifecycleSnapshot = synchronized(lock) {
        val foreground = sessionTracker.bindSession(sessionId)
        publishLocked(
            mutableSnapshot.value.copy(
                sessionId = sessionId,
                turnbackPending = false,
                lastIssuedTokenId = null,
                lastBackgroundReason = null,
                foreground = foreground,
            ),
        )
    }

    public fun attachFullscreenClicks(coordinator: FullscreenShowCoordinator) {
        clickCollectJob?.cancel()
        clickCollectJob = scope.launch {
            coordinator.events.collect { event ->
                if (event is FullscreenShowEvent.Click) {
                    onAdClick(event.showRequestId)
                }
            }
        }
    }

    public fun onAdClick(
        showRequestId: ShowRequestId,
        ttlMillis: Long = defaultClickTokenTtlMillis,
    ): AdClickTokenId? = synchronized(lock) {
        val sessionId = mutableSnapshot.value.sessionId ?: return null
        val tokenId = tokenStore.issue(sessionId = sessionId, ttlMillis = ttlMillis)
        emit(
            AdsLifecycleEvent.ClickTokenIssued(
                sessionId = sessionId,
                tokenId = tokenId,
                showRequestId = showRequestId,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
        publishLocked(
            mutableSnapshot.value.copy(lastIssuedTokenId = tokenId),
        )
        tokenId
    }

    public fun setSplashActive(active: Boolean): AdsLifecycleSnapshot = synchronized(lock) {
        publishLocked(mutableSnapshot.value.copy(splashActive = active))
    }

    public fun setActivityValid(valid: Boolean): AdsLifecycleSnapshot = synchronized(lock) {
        publishLocked(mutableSnapshot.value.copy(activityValid = valid))
    }

    public fun setTurnbackPending(pending: Boolean): AdsLifecycleSnapshot = synchronized(lock) {
        publishLocked(mutableSnapshot.value.copy(turnbackPending = pending))
    }

    public fun clearTurnbackPending(): AdsLifecycleSnapshot = setTurnbackPending(false)

    /**
     * Classifies a background transition.
     *
     * When [hint] is null, presence of a valid click token selects [BackgroundReason.AD_CLICK];
     * otherwise the transition is [BackgroundReason.USER_BACKGROUND].
     */
    public fun onBackground(
        hint: BackgroundReason? = null,
        generation: Long? = null,
    ): AdsLifecycleTransitionResult = synchronized(lock) {
        val current = mutableSnapshot.value
        val sessionId = current.sessionId
            ?: return AdsLifecycleTransitionResult.Ignored("No session bound")
        val reason = when (hint) {
            BackgroundReason.SYSTEM_INTERRUPTION,
            BackgroundReason.UNKNOWN,
            BackgroundReason.USER_BACKGROUND,
            -> hint
            BackgroundReason.AD_CLICK -> {
                if (tokenStore.hasValidToken(sessionId)) {
                    BackgroundReason.AD_CLICK
                } else {
                    BackgroundReason.USER_BACKGROUND
                }
            }
            null -> {
                if (tokenStore.hasValidToken(sessionId)) {
                    BackgroundReason.AD_CLICK
                } else {
                    BackgroundReason.USER_BACKGROUND
                }
            }
        }
        return when (
            val transition = sessionTracker.onBackground(
                sessionId = sessionId,
                reason = reason,
                generation = generation,
            )
        ) {
            is ForegroundTransitionResult.Ignored ->
                AdsLifecycleTransitionResult.Ignored(transition.reason)
            is ForegroundTransitionResult.Accepted -> {
                emit(
                    AdsLifecycleEvent.Background(
                        sessionId = sessionId,
                        reason = reason,
                        occurredAtMillis = clock.nowMillis(),
                    ),
                )
                val updated = publishLocked(
                    current.copy(
                        lastBackgroundReason = reason,
                        foreground = transition.snapshot,
                    ),
                )
                AdsLifecycleTransitionResult.Accepted(updated)
            }
        }
    }

    public fun onForeground(
        generation: Long? = null,
    ): AdsLifecycleTransitionResult = synchronized(lock) {
        val current = mutableSnapshot.value
        val sessionId = current.sessionId
            ?: return AdsLifecycleTransitionResult.Ignored("No session bound")
        return when (
            val transition = sessionTracker.onForeground(
                sessionId = sessionId,
                generation = generation,
            )
        ) {
            is ForegroundTransitionResult.Ignored ->
                AdsLifecycleTransitionResult.Ignored(transition.reason)
            is ForegroundTransitionResult.Accepted -> {
                val previousReason = current.lastBackgroundReason
                val turnbackPending = previousReason == BackgroundReason.AD_CLICK &&
                    tokenStore.hasValidToken(sessionId)
                emit(
                    AdsLifecycleEvent.Foreground(
                        sessionId = sessionId,
                        previousBackgroundReason = previousReason,
                        turnbackPending = turnbackPending,
                        occurredAtMillis = clock.nowMillis(),
                    ),
                )
                val updated = publishLocked(
                    current.copy(
                        turnbackPending = turnbackPending,
                        foreground = transition.snapshot,
                    ),
                )
                AdsLifecycleTransitionResult.Accepted(updated)
            }
        }
    }

    public fun evaluateAppOpenSuppression(
        nowMillis: Long = clock.nowMillis(),
    ): AppOpenSuppressionResult = synchronized(lock) {
        evaluateSuppressionLocked(
            sessionId = mutableSnapshot.value.sessionId,
            splashActive = mutableSnapshot.value.splashActive,
            turnbackPending = mutableSnapshot.value.turnbackPending,
            activityValid = mutableSnapshot.value.activityValid,
            nowMillis = nowMillis,
        ).also { result ->
            mutableSnapshot.value = mutableSnapshot.value.copy(appOpenSuppression = result)
        }
    }

    public fun refreshSnapshot(nowMillis: Long = clock.nowMillis()): AdsLifecycleSnapshot =
        synchronized(lock) {
            publishLocked(
                mutableSnapshot.value.copy(
                    foreground = sessionTracker.snapshot.value,
                ),
                nowMillis = nowMillis,
            )
        }

    private fun publishLocked(
        next: AdsLifecycleSnapshot,
        nowMillis: Long = clock.nowMillis(),
    ): AdsLifecycleSnapshot {
        val suppression = evaluateSuppressionLocked(
            sessionId = next.sessionId,
            splashActive = next.splashActive,
            turnbackPending = next.turnbackPending,
            activityValid = next.activityValid,
            nowMillis = nowMillis,
        )
        val published = next.copy(
            foreground = sessionTracker.snapshot.value,
            appOpenSuppression = suppression,
        )
        mutableSnapshot.value = published
        return published
    }

    private fun evaluateSuppressionLocked(
        sessionId: SessionId?,
        splashActive: Boolean,
        turnbackPending: Boolean,
        activityValid: Boolean,
        nowMillis: Long = clock.nowMillis(),
    ): AppOpenSuppressionResult {
        val hasToken = sessionId != null && tokenStore.hasValidToken(sessionId, nowMillis)
        return AppOpenSuppression.evaluate(
            AppOpenSuppressionInput(
                fullscreenLockBusy = fullscreenLock.isBusy(),
                splashActive = splashActive,
                hasValidClickToken = hasToken,
                turnbackPending = turnbackPending,
                activityValid = activityValid,
            ),
        )
    }

    private fun emit(event: AdsLifecycleEvent) {
        mutableEvents.tryEmit(event)
    }
}

public data class AdsLifecycleSnapshot(
    val sessionId: SessionId?,
    val splashActive: Boolean,
    val turnbackPending: Boolean,
    val activityValid: Boolean,
    val lastIssuedTokenId: AdClickTokenId?,
    val lastBackgroundReason: BackgroundReason?,
    val foreground: ForegroundSessionSnapshot,
    val appOpenSuppression: AppOpenSuppressionResult,
)

public sealed class AdsLifecycleTransitionResult {
    public data class Accepted(
        val snapshot: AdsLifecycleSnapshot,
    ) : AdsLifecycleTransitionResult()

    public data class Ignored(
        val reason: String,
    ) : AdsLifecycleTransitionResult()
}

public sealed class AdsLifecycleEvent {
    public abstract val occurredAtMillis: Long

    public data class ClickTokenIssued(
        val sessionId: SessionId,
        val tokenId: AdClickTokenId,
        val showRequestId: ShowRequestId,
        override val occurredAtMillis: Long,
    ) : AdsLifecycleEvent()

    public data class Background(
        val sessionId: SessionId,
        val reason: BackgroundReason,
        override val occurredAtMillis: Long,
    ) : AdsLifecycleEvent()

    public data class Foreground(
        val sessionId: SessionId,
        val previousBackgroundReason: BackgroundReason?,
        val turnbackPending: Boolean,
        override val occurredAtMillis: Long,
    ) : AdsLifecycleEvent()
}
