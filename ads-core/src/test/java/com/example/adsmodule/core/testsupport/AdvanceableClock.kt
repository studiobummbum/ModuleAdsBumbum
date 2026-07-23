package com.example.adsmodule.core.testsupport

import com.example.adsmodule.core.Clock
import java.util.concurrent.atomic.AtomicLong

/**
 * Mutable [Clock] for tests that do not use a coroutine TestScheduler.
 */
internal class AdvanceableClock(
    startMillis: Long = 0L,
) : Clock {
    private val now = AtomicLong(startMillis)

    override fun nowMillis(): Long = now.get()

    fun advance(deltaMillis: Long) {
        require(deltaMillis >= 0L) { "deltaMillis must not be negative" }
        now.addAndGet(deltaMillis)
    }

    fun set(millis: Long) {
        require(millis >= 0L) { "millis must not be negative" }
        now.set(millis)
    }
}
