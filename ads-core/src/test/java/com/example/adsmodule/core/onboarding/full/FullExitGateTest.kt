package com.example.adsmodule.core.onboarding.full

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullExitGateTest {
    @Test
    fun firstWins_secondIgnored() {
        val gate = FullExitGate()
        val wins = AtomicInteger(0)
        assertTrue(
            gate.tryExit(FullExitSource.SWIPE_FORWARD) {
                wins.incrementAndGet()
                assertEquals(FullExitSource.SWIPE_FORWARD, it)
            },
        )
        assertFalse(
            gate.tryExit(FullExitSource.CLOSE_X) {
                wins.incrementAndGet()
            },
        )
        assertFalse(
            gate.tryExit(FullExitSource.AUTO_SKIP) {
                wins.incrementAndGet()
            },
        )
        assertEquals(1, wins.get())
        assertEquals(FullExitSource.SWIPE_FORWARD, gate.winningSource())
        assertEquals(FullGateState.COMPLETED, gate.gateState())
    }
}
