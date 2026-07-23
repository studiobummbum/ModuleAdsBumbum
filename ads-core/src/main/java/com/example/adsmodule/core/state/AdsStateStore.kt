package com.example.adsmodule.core.state

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ObjectId
import java.util.concurrent.ConcurrentHashMap

public data class SlotStateKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SlotStateKey must not be blank" }
    }
}

public sealed class ApplyTransitionResult {
    public data class Accepted(
        val subjectId: String,
        val from: AdSlotState,
        val to: AdSlotState,
        val event: AdsStateEvent,
        val atMillis: Long,
    ) : ApplyTransitionResult()

    public data class Rejected(
        val subjectId: String,
        val from: AdSlotState?,
        val event: AdsStateEvent,
        val reason: String,
    ) : ApplyTransitionResult()
}

/**
 * Atomic slot/object state store.
 *
 * Critical sections only update maps + history. Callers must not invoke SDK code
 * while holding [lock].
 */
public class AdsStateStore(
    private val clock: Clock,
    private val history: StateHistory = StateHistory(),
    private val initialState: AdSlotState = AdSlotState.DISABLED,
    private val lock: Any = Any(),
) {
    private val states = ConcurrentHashMap<String, AdSlotState>()

    public fun lock(): Any = lock

    public fun historySnapshot(): List<StateHistoryEntry> = synchronized(lock) {
        history.snapshot()
    }

    public fun currentState(subjectId: String): AdSlotState? = states[subjectId]

    public fun currentState(objectId: ObjectId): AdSlotState? = states[objectId.value]

    public fun ensureSubject(
        subjectId: String,
        state: AdSlotState = initialState,
    ): AdSlotState = synchronized(lock) {
        states.putIfAbsent(subjectId, state) ?: state
        checkNotNull(states[subjectId])
    }

    public fun apply(
        subjectId: String,
        event: AdsStateEvent,
        objectId: ObjectId? = null,
    ): ApplyTransitionResult = synchronized(lock) {
        applyLocked(subjectId = subjectId, event = event, objectId = objectId)
    }

    public fun applyForObject(
        objectId: ObjectId,
        event: AdsStateEvent,
    ): ApplyTransitionResult = apply(
        subjectId = objectId.value,
        event = event,
        objectId = objectId,
    )

    /**
     * Applies [event] only when current state equals [expected]. Used for CAS-style reserve.
     */
    public fun applyIf(
        subjectId: String,
        expected: AdSlotState,
        event: AdsStateEvent,
        objectId: ObjectId? = null,
    ): ApplyTransitionResult = synchronized(lock) {
        val current = states[subjectId]
        if (current != expected) {
            return@synchronized ApplyTransitionResult.Rejected(
                subjectId = subjectId,
                from = current,
                event = event,
                reason = "Expected $expected but was $current",
            )
        }
        applyLocked(subjectId = subjectId, event = event, objectId = objectId)
    }

    public fun setStateUnchecked(
        subjectId: String,
        state: AdSlotState,
    ) {
        synchronized(lock) {
            states[subjectId] = state
        }
    }

    public fun removeSubject(subjectId: String): AdSlotState? = synchronized(lock) {
        states.remove(subjectId)
    }

    public fun snapshotStates(): Map<String, AdSlotState> = synchronized(lock) {
        states.toMap()
    }

    /** Applies a transition while the caller already holds [lock]. */
    internal fun applyAlreadyLocked(
        subjectId: String,
        event: AdsStateEvent,
        objectId: ObjectId? = null,
    ): ApplyTransitionResult = applyLocked(subjectId, event, objectId)

    internal fun applyIfAlreadyLocked(
        subjectId: String,
        expected: AdSlotState,
        event: AdsStateEvent,
        objectId: ObjectId? = null,
    ): ApplyTransitionResult {
        val current = states[subjectId]
        if (current != expected) {
            return ApplyTransitionResult.Rejected(
                subjectId = subjectId,
                from = current,
                event = event,
                reason = "Expected $expected but was $current",
            )
        }
        return applyLocked(subjectId, event, objectId)
    }

    internal fun ensureSubjectAlreadyLocked(
        subjectId: String,
        state: AdSlotState,
    ): AdSlotState {
        states.putIfAbsent(subjectId, state) ?: state
        return checkNotNull(states[subjectId])
    }

    internal fun setStateUncheckedAlreadyLocked(
        subjectId: String,
        state: AdSlotState,
    ) {
        states[subjectId] = state
    }

    internal fun removeSubjectAlreadyLocked(subjectId: String): AdSlotState? =
        states.remove(subjectId)

    internal fun snapshotStatesAlreadyLocked(): Map<String, AdSlotState> = states.toMap()

    internal fun historySnapshotAlreadyLocked(): List<StateHistoryEntry> = history.snapshot()

    private fun applyLocked(
        subjectId: String,
        event: AdsStateEvent,
        objectId: ObjectId?,
    ): ApplyTransitionResult {
        val from = states[subjectId] ?: initialState.also { states[subjectId] = it }
        return when (val result = AdsStateReducer.reduce(from, event)) {
            is TransitionResult.Accepted -> {
                states[subjectId] = result.to
                val at = clock.nowMillis()
                history.append(
                    StateHistoryEntry(
                        subjectId = subjectId,
                        objectId = objectId,
                        from = result.from,
                        to = result.to,
                        event = result.event,
                        atMillis = at,
                    ),
                )
                ApplyTransitionResult.Accepted(
                    subjectId = subjectId,
                    from = result.from,
                    to = result.to,
                    event = result.event,
                    atMillis = at,
                )
            }
            is TransitionResult.Rejected -> ApplyTransitionResult.Rejected(
                subjectId = subjectId,
                from = result.from,
                event = result.event,
                reason = result.reason,
            )
        }
    }
}
