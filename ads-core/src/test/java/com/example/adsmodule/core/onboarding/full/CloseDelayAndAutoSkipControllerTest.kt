package com.example.adsmodule.core.onboarding.full

import com.example.adsmodule.core.Clock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloseDelayAndAutoSkipControllerTest {
    @Test
    fun closeDelay_firesAfterDelay() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val close = CloseDelayController(this, clock)
        val ready = AtomicBoolean(false)
        close.start(2_000L) { ready.set(true) }
        advanceTimeBy(1_999L)
        runCurrent()
        assertFalse(ready.get())
        advanceTimeBy(1L)
        runCurrent()
        assertTrue(ready.get())
        assertEquals(0L, close.remainingMillis())
    }

    @Test
    fun autoSkip_cancelPreventsFire() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val auto = AutoSkipController(this, clock)
        val fired = AtomicInteger(0)
        auto.start(3_000L) { fired.incrementAndGet() }
        advanceTimeBy(1_000L)
        runCurrent()
        auto.cancel()
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(0, fired.get())
    }

    @Test
    fun autoSkip_attachAfterRecreation_firesAtOriginalDeadline() = runTest {
        val clock = Clock { testScheduler.currentTime }
        val auto = AutoSkipController(this, clock)
        val fired = AtomicBoolean(false)
        auto.start(3_000L) { fired.set(true) }
        advanceTimeBy(1_000L)
        runCurrent()
        val deadline = auto.deadlineMillis()!!
        auto.cancel()
        auto.attach(deadline) { fired.set(true) }
        advanceTimeBy(1_999L)
        runCurrent()
        assertFalse(fired.get())
        advanceTimeBy(1L)
        runCurrent()
        assertTrue(fired.get())
    }
}
