package com.example.adsmodule.core.state

import com.example.adsmodule.core.AdSlotState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTransitionValidatorTest {
    @Test
    fun validTransitions_fromExcelSheetAreAccepted() {
        assertTrue(StateTransitionValidator.isValid(AdSlotState.DISABLED, AdSlotState.IDLE))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.IDLE, AdSlotState.LOADING))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.LOADING, AdSlotState.READY))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.LOADING, AdSlotState.LOADING))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.LOADING, AdSlotState.FAILED))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.READY, AdSlotState.RESERVED))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.READY, AdSlotState.CONSUMED))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.READY, AdSlotState.EXPIRED))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.RESERVED, AdSlotState.SHOWING))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.RESERVED, AdSlotState.READY))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.SHOWING, AdSlotState.CONSUMED))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.SHOWING, AdSlotState.FAILED))
        // Park shown Full natives: SHOWING → READY via Release.
        assertTrue(StateTransitionValidator.isValid(AdSlotState.SHOWING, AdSlotState.READY))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.CONSUMED, AdSlotState.LOADING))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.CONSUMED, AdSlotState.IDLE))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.FAILED, AdSlotState.LOADING))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.FAILED, AdSlotState.IDLE))
        assertTrue(StateTransitionValidator.isValid(AdSlotState.EXPIRED, AdSlotState.LOADING))
    }

    @Test
    fun invalidTransitions_areRejected() {
        assertFalse(StateTransitionValidator.isValid(AdSlotState.READY, AdSlotState.SHOWING))
        assertFalse(StateTransitionValidator.isValid(AdSlotState.READY, AdSlotState.LOADING))
        assertFalse(StateTransitionValidator.isValid(AdSlotState.RESERVED, AdSlotState.CONSUMED))
        assertFalse(StateTransitionValidator.isValid(AdSlotState.EXPIRED, AdSlotState.READY))
        assertFalse(StateTransitionValidator.isValid(AdSlotState.DISABLED, AdSlotState.LOADING))
        assertFalse(StateTransitionValidator.isValid(AdSlotState.IDLE, AdSlotState.READY))
        assertFalse(StateTransitionValidator.isValid(AdSlotState.CONSUMED, AdSlotState.READY))
    }

    @Test
    fun reducer_mapsEventsAndRejectsDuplicates() {
        val ready = AdsStateReducer.reduce(AdSlotState.READY, AdsStateEvent.Reserve)
        assertTrue(ready is TransitionResult.Accepted)
        assertTrue((ready as TransitionResult.Accepted).to == AdSlotState.RESERVED)

        val duplicate = AdsStateReducer.reduce(AdSlotState.RESERVED, AdsStateEvent.Reserve)
        assertTrue(duplicate is TransitionResult.Rejected)

        val expireReserved = AdsStateReducer.reduce(AdSlotState.RESERVED, AdsStateEvent.Expire)
        assertTrue(expireReserved is TransitionResult.Rejected)

        val parkShowing = AdsStateReducer.reduce(AdSlotState.SHOWING, AdsStateEvent.Release)
        assertTrue(parkShowing is TransitionResult.Accepted)
        assertTrue((parkShowing as TransitionResult.Accepted).to == AdSlotState.READY)
    }
}
