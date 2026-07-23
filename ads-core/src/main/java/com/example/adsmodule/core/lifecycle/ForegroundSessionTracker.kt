package com.example.adsmodule.core.lifecycle

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks foreground/background transitions for a process [SessionId].
 *
 * Stale callbacks with a mismatched session or generation are ignored.
 */
public class ForegroundSessionTracker(
    private val clock: Clock,
) {
    private val lock = Any()
    private val mutableSnapshot = MutableStateFlow(
        ForegroundSessionSnapshot(
            sessionId = null,
            isInForeground = true,
            generation = 0L,
            lastBackgroundReason = null,
            lastBackgroundAtMillis = null,
            lastForegroundAtMillis = null,
        ),
    )

    public val snapshot: StateFlow<ForegroundSessionSnapshot> = mutableSnapshot.asStateFlow()

    public fun bindSession(sessionId: SessionId): ForegroundSessionSnapshot = synchronized(lock) {
        val previous = mutableSnapshot.value
        val next = previous.copy(
            sessionId = sessionId,
            isInForeground = true,
            generation = previous.generation + 1L,
            lastBackgroundReason = null,
            lastBackgroundAtMillis = null,
            lastForegroundAtMillis = clock.nowMillis(),
        )
        mutableSnapshot.value = next
        next
    }

    public fun onBackground(
        sessionId: SessionId,
        reason: BackgroundReason,
        generation: Long? = null,
    ): ForegroundTransitionResult = synchronized(lock) {
        val current = mutableSnapshot.value
        if (current.sessionId != sessionId) {
            return ForegroundTransitionResult.Ignored("Session mismatch")
        }
        if (generation != null && generation != current.generation) {
            return ForegroundTransitionResult.Ignored("Stale generation")
        }
        if (!current.isInForeground) {
            return ForegroundTransitionResult.Ignored("Already background")
        }
        val next = current.copy(
            isInForeground = false,
            lastBackgroundReason = reason,
            lastBackgroundAtMillis = clock.nowMillis(),
            generation = current.generation + 1L,
        )
        mutableSnapshot.value = next
        ForegroundTransitionResult.Accepted(next)
    }

    public fun onForeground(
        sessionId: SessionId,
        generation: Long? = null,
    ): ForegroundTransitionResult = synchronized(lock) {
        val current = mutableSnapshot.value
        if (current.sessionId != sessionId) {
            return ForegroundTransitionResult.Ignored("Session mismatch")
        }
        if (generation != null && generation != current.generation) {
            return ForegroundTransitionResult.Ignored("Stale generation")
        }
        if (current.isInForeground) {
            return ForegroundTransitionResult.Ignored("Already foreground")
        }
        val next = current.copy(
            isInForeground = true,
            lastForegroundAtMillis = clock.nowMillis(),
            generation = current.generation + 1L,
        )
        mutableSnapshot.value = next
        ForegroundTransitionResult.Accepted(next)
    }
}

public data class ForegroundSessionSnapshot(
    val sessionId: SessionId?,
    val isInForeground: Boolean,
    val generation: Long,
    val lastBackgroundReason: BackgroundReason?,
    val lastBackgroundAtMillis: Long?,
    val lastForegroundAtMillis: Long?,
)

public sealed class ForegroundTransitionResult {
    public data class Accepted(
        val snapshot: ForegroundSessionSnapshot,
    ) : ForegroundTransitionResult()

    public data class Ignored(
        val reason: String,
    ) : ForegroundTransitionResult()
}
