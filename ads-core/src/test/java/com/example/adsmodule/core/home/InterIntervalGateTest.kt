package com.example.adsmodule.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterIntervalGateTest {
    @Test
    fun nullInterval_alwaysAllows() {
        val gate = InterIntervalGate()
        gate.markImpressed(1_000L)
        val decision = gate.canShow(nowMillis = 1_001L, intervalMillis = null)
        assertTrue(decision is InterIntervalDecision.Allowed)
    }

    @Test
    fun noPriorImpression_allows() {
        val gate = InterIntervalGate()
        val decision = gate.canShow(nowMillis = 5_000L, intervalMillis = 30_000L)
        assertTrue(decision is InterIntervalDecision.Allowed)
    }

    @Test
    fun blocked_beforeIntervalElapses() {
        val gate = InterIntervalGate()
        gate.markImpressed(0L)
        val decision = gate.canShow(nowMillis = 10_000L, intervalMillis = 30_000L)
        val blocked = decision as InterIntervalDecision.Blocked
        assertEquals(20_000L, blocked.remainingMillis)
    }

    @Test
    fun allowed_afterIntervalElapses() {
        val gate = InterIntervalGate()
        gate.markImpressed(0L)
        val decision = gate.canShow(nowMillis = 30_000L, intervalMillis = 30_000L)
        assertTrue(decision is InterIntervalDecision.Allowed)
    }

    @Test
    fun clear_resetsGate() {
        val gate = InterIntervalGate()
        gate.markImpressed(0L)
        gate.clear()
        assertTrue(gate.canShow(1L, 30_000L) is InterIntervalDecision.Allowed)
        assertEquals(null, gate.lastImpressionAtMillis())
    }
}
