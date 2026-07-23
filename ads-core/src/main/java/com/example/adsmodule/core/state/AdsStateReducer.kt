package com.example.adsmodule.core.state

import com.example.adsmodule.core.AdSlotState

public sealed class TransitionResult {
    public data class Accepted(
        val from: AdSlotState,
        val to: AdSlotState,
        val event: AdsStateEvent,
    ) : TransitionResult()

    public data class Rejected(
        val from: AdSlotState,
        val event: AdsStateEvent,
        val reason: String,
    ) : TransitionResult()
}

/**
 * Maps [AdsStateEvent] to a target [AdSlotState] and validates via [StateTransitionValidator].
 */
public object AdsStateReducer {
    public fun reduce(from: AdSlotState, event: AdsStateEvent): TransitionResult {
        val to = targetOf(from, event)
            ?: return TransitionResult.Rejected(
                from = from,
                event = event,
                reason = "Event ${event::class.simpleName} is not applicable from $from",
            )
        if (!StateTransitionValidator.isValid(from, to)) {
            return TransitionResult.Rejected(
                from = from,
                event = event,
                reason = "Invalid transition $from → $to for ${event::class.simpleName}",
            )
        }
        return TransitionResult.Accepted(from = from, to = to, event = event)
    }

    private fun targetOf(from: AdSlotState, event: AdsStateEvent): AdSlotState? =
        when (event) {
            AdsStateEvent.Enable -> AdSlotState.IDLE.takeIf { from == AdSlotState.DISABLED }
            AdsStateEvent.StartLoad -> AdSlotState.LOADING.takeIf {
                from == AdSlotState.IDLE ||
                    from == AdSlotState.CONSUMED ||
                    from == AdSlotState.FAILED ||
                    from == AdSlotState.EXPIRED
            }
            AdsStateEvent.ContinueLoading -> AdSlotState.LOADING.takeIf { from == AdSlotState.LOADING }
            AdsStateEvent.MarkReady -> AdSlotState.READY.takeIf { from == AdSlotState.LOADING }
            AdsStateEvent.Reserve -> AdSlotState.RESERVED.takeIf { from == AdSlotState.READY }
            AdsStateEvent.Release -> AdSlotState.READY.takeIf { from == AdSlotState.RESERVED }
            AdsStateEvent.MarkShowing -> AdSlotState.SHOWING.takeIf { from == AdSlotState.RESERVED }
            AdsStateEvent.Consume -> when (from) {
                AdSlotState.READY, AdSlotState.SHOWING -> AdSlotState.CONSUMED
                else -> null
            }
            AdsStateEvent.Fail -> when (from) {
                AdSlotState.LOADING -> AdSlotState.FAILED
                AdSlotState.SHOWING -> AdSlotState.FAILED
                else -> null
            }
            AdsStateEvent.Expire -> AdSlotState.EXPIRED.takeIf { from == AdSlotState.READY }
            AdsStateEvent.ResetToIdle -> AdSlotState.IDLE.takeIf {
                from == AdSlotState.CONSUMED || from == AdSlotState.FAILED
            }
            AdsStateEvent.ResetToLoading -> AdSlotState.LOADING.takeIf {
                from == AdSlotState.CONSUMED ||
                    from == AdSlotState.FAILED ||
                    from == AdSlotState.EXPIRED
            }
        }
}
