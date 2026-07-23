package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OnboardingSessionId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure onboarding navigation state machine.
 *
 * Forward swipe and Next share [requestForward]. Backward never launches Full.
 * Full1 is tied to logical page 2; Full2 to logical page 3. Disabled pages
 * skip their Full boundary and advance to the next active logical page.
 */
public class OnboardingBoundaryCoordinator(
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    private val mutableSnapshot = MutableStateFlow<OnboardingFlowSnapshot?>(null)
    private val mutableEvents = MutableSharedFlow<OnboardingFlowEvent>(extraBufferCapacity = 32)
    private val claimedEffects =
        AtomicReference(emptySet<OnboardingNavigationEffect>())

    public val snapshot: StateFlow<OnboardingFlowSnapshot?> = mutableSnapshot.asStateFlow()
    public val events: SharedFlow<OnboardingFlowEvent> = mutableEvents.asSharedFlow()

    public fun startOrRestore(
        policy: OnboardingPagePolicy,
        existing: OnboardingSavedState? = null,
    ): OnboardingSessionId {
        if (existing != null) {
            return restore(existing)
        }
        val active = policy.activePages
        require(active.isNotEmpty()) { "No active onboarding pages" }
        val sessionId = OnboardingSessionId(idGenerator.nextId())
        claimedEffects.set(emptySet())
        mutableSnapshot.value = OnboardingFlowSnapshot(
            sessionId = sessionId,
            activePages = active,
            currentLogicalPage = active.first(),
        )
        emitPage(sessionId, active.first())
        return sessionId
    }

    public fun restore(saved: OnboardingSavedState): OnboardingSessionId {
        claimedEffects.set(saved.claimedEffects)
        mutableSnapshot.value = OnboardingFlowSnapshot(
            sessionId = saved.sessionId,
            activePages = saved.activePages,
            currentLogicalPage = saved.currentLogicalPage,
            pendingTargetLogicalPage = saved.pendingTargetLogicalPage,
            full1Completed = saved.full1Completed,
            full2Completed = saved.full2Completed,
            pendingFull = saved.pendingFull,
            pendingEffect = saved.pendingEffect,
        )
        return saved.sessionId
    }

    public fun exportSavedState(): OnboardingSavedState? {
        val snap = mutableSnapshot.value ?: return null
        return snap.toSavedState(claimedEffects.get())
    }

    public fun claimEffect(
        sessionId: OnboardingSessionId,
        effect: OnboardingNavigationEffect,
    ): Boolean {
        val snap = mutableSnapshot.value ?: return false
        if (snap.sessionId != sessionId) return false
        if (snap.pendingEffect != effect) return false
        while (true) {
            val claimed = claimedEffects.get()
            if (effect in claimed) return false
            if (claimedEffects.compareAndSet(claimed, claimed + effect)) {
                publish {
                    if (it.sessionId == sessionId && it.pendingEffect == effect) {
                        it.copy(pendingEffect = null)
                    } else {
                        it
                    }
                }
                return true
            }
        }
    }

    public fun requestForward(sessionId: OnboardingSessionId): OnboardingForwardResult {
        val snap = mutableSnapshot.value
            ?: return OnboardingForwardResult.Ignored("no session")
        if (snap.sessionId != sessionId) {
            return OnboardingForwardResult.Ignored("session mismatch")
        }
        if (snap.pendingFull != null || snap.pendingEffect != null) {
            return OnboardingForwardResult.Ignored("navigation already pending")
        }

        val current = snap.currentLogicalPage
        val next = nextActiveAfter(snap.activePages, current)

        if (current == 2 && !snap.full1Completed) {
            val fullSessionId = FullSessionId(idGenerator.nextId())
            val pending = OnboardingPendingFull(
                fullIndex = 1,
                fullSessionId = fullSessionId,
                targetLogicalPage = next,
            )
            publish {
                it.copy(
                    pendingTargetLogicalPage = next,
                    pendingFull = pending,
                    pendingEffect = OnboardingNavigationEffect.OPEN_FULL1,
                )
            }
            emitEffect(sessionId, OnboardingNavigationEffect.OPEN_FULL1)
            return OnboardingForwardResult.LaunchFull(
                effect = OnboardingNavigationEffect.OPEN_FULL1,
                fullSessionId = fullSessionId,
                targetLogicalPage = next,
            )
        }

        if (current == 3 && !snap.full2Completed) {
            val fullSessionId = FullSessionId(idGenerator.nextId())
            val pending = OnboardingPendingFull(
                fullIndex = 2,
                fullSessionId = fullSessionId,
                targetLogicalPage = next,
            )
            publish {
                it.copy(
                    pendingTargetLogicalPage = next,
                    pendingFull = pending,
                    pendingEffect = OnboardingNavigationEffect.OPEN_FULL2,
                )
            }
            emitEffect(sessionId, OnboardingNavigationEffect.OPEN_FULL2)
            return OnboardingForwardResult.LaunchFull(
                effect = OnboardingNavigationEffect.OPEN_FULL2,
                fullSessionId = fullSessionId,
                targetLogicalPage = next,
            )
        }

        if (next == null) {
            publish { it.copy(pendingEffect = OnboardingNavigationEffect.OPEN_HOME) }
            emitEffect(sessionId, OnboardingNavigationEffect.OPEN_HOME)
            return OnboardingForwardResult.OpenHome()
        }

        publish { it.copy(currentLogicalPage = next) }
        emitPage(sessionId, next)
        return OnboardingForwardResult.MovedToPage(next)
    }

    public fun requestBackward(sessionId: OnboardingSessionId): OnboardingBackwardResult {
        val snap = mutableSnapshot.value
            ?: return OnboardingBackwardResult.Ignored("no session")
        if (snap.sessionId != sessionId) {
            return OnboardingBackwardResult.Ignored("session mismatch")
        }
        if (snap.pendingFull != null) {
            return OnboardingBackwardResult.Ignored("full pending")
        }
        val previous = previousActiveBefore(snap.activePages, snap.currentLogicalPage)
            ?: return OnboardingBackwardResult.Ignored("already first")
        publish { it.copy(currentLogicalPage = previous) }
        emitPage(sessionId, previous)
        return OnboardingBackwardResult.MovedToPage(previous)
    }

    /**
     * Applies a Full Activity result. Target may be null when no pager remains
     * after the Full boundary (OPEN_HOME).
     */
    public fun onFullResult(result: OnboardingFullResult): Boolean {
        val snap = mutableSnapshot.value ?: return false
        if (snap.sessionId != result.sessionId) return false
        val pending = snap.pendingFull ?: return false
        if (pending.fullSessionId != result.fullSessionId) return false
        if (pending.fullIndex != result.fullIndex) return false
        if (pending.targetLogicalPage != result.targetLogicalPage) return false

        val target = pending.targetLogicalPage
        if (target == null) {
            publish {
                it.copy(
                    pendingFull = null,
                    pendingTargetLogicalPage = null,
                    full1Completed = it.full1Completed || result.fullIndex == 1,
                    full2Completed = it.full2Completed || result.fullIndex == 2,
                    pendingEffect = OnboardingNavigationEffect.OPEN_HOME,
                )
            }
            emitEffect(snap.sessionId, OnboardingNavigationEffect.OPEN_HOME)
            return true
        }
        if (target !in snap.activePages) return false

        publish {
            it.copy(
                currentLogicalPage = target,
                pendingFull = null,
                pendingTargetLogicalPage = null,
                full1Completed = it.full1Completed || result.fullIndex == 1,
                full2Completed = it.full2Completed || result.fullIndex == 2,
                pendingEffect = null,
            )
        }
        emitPage(snap.sessionId, target)
        return true
    }

    public fun updatePageAds(pageAds: Map<Int, com.example.adsmodule.core.normal.NormalScreenSlotState>) {
        publish { it.copy(pageAds = pageAds) }
    }

    public fun adapterIndexFor(logicalPage: Int): Int {
        val snap = mutableSnapshot.value ?: return -1
        return snap.activePages.indexOf(logicalPage)
    }

    public fun logicalPageAt(adapterIndex: Int): Int? {
        val snap = mutableSnapshot.value ?: return null
        return snap.activePages.getOrNull(adapterIndex)
    }

    private fun nextActiveAfter(active: List<Int>, current: Int): Int? {
        val index = active.indexOf(current)
        if (index < 0) return null
        return active.getOrNull(index + 1)
    }

    private fun previousActiveBefore(active: List<Int>, current: Int): Int? {
        val index = active.indexOf(current)
        if (index <= 0) return null
        return active.getOrNull(index - 1)
    }

    private fun publish(transform: (OnboardingFlowSnapshot) -> OnboardingFlowSnapshot) {
        mutableSnapshot.update { current -> current?.let(transform) }
    }

    private fun emitPage(sessionId: OnboardingSessionId, page: Int) {
        mutableEvents.tryEmit(
            OnboardingFlowEvent.PageChanged(
                sessionId = sessionId,
                logicalPage = page,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private fun emitEffect(
        sessionId: OnboardingSessionId,
        effect: OnboardingNavigationEffect,
    ) {
        mutableEvents.tryEmit(
            OnboardingFlowEvent.EffectRequested(
                sessionId = sessionId,
                effect = effect,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }
}
