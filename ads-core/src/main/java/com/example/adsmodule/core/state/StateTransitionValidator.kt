package com.example.adsmodule.core.state

import com.example.adsmodule.core.AdSlotState

/**
 * Pure allow-list for [AdSlotState] transitions (Excel sheet `04 Máy trạng thái`).
 */
public object StateTransitionValidator {
    private val allowed: Map<AdSlotState, Set<AdSlotState>> = mapOf(
        AdSlotState.DISABLED to setOf(AdSlotState.IDLE),
        AdSlotState.IDLE to setOf(AdSlotState.LOADING),
        AdSlotState.LOADING to setOf(
            AdSlotState.READY,
            AdSlotState.LOADING,
            AdSlotState.FAILED,
        ),
        AdSlotState.READY to setOf(
            AdSlotState.RESERVED,
            AdSlotState.CONSUMED,
            AdSlotState.EXPIRED,
        ),
        AdSlotState.RESERVED to setOf(
            AdSlotState.SHOWING,
            AdSlotState.READY,
        ),
        AdSlotState.SHOWING to setOf(
            AdSlotState.CONSUMED,
            AdSlotState.FAILED,
            AdSlotState.READY,
        ),
        AdSlotState.CONSUMED to setOf(
            AdSlotState.LOADING,
            AdSlotState.IDLE,
        ),
        AdSlotState.FAILED to setOf(
            AdSlotState.LOADING,
            AdSlotState.IDLE,
        ),
        AdSlotState.EXPIRED to setOf(AdSlotState.LOADING),
    )

    public fun isValid(from: AdSlotState, to: AdSlotState): Boolean {
        if (from == to && from == AdSlotState.LOADING) {
            return true
        }
        return allowed[from]?.contains(to) == true
    }

    public fun allowedTargets(from: AdSlotState): Set<AdSlotState> =
        allowed[from].orEmpty()
}
