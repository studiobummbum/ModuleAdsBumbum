package com.example.adsmodule.core.state

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ObjectId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsStateStoreTest {
    @Test
    fun apply_recordsHistoryForAcceptedTransitions() {
        val clock = FakeClock(1_000L)
        val store = AdsStateStore(clock = clock)
        store.ensureSubject("slot-1", AdSlotState.DISABLED)

        val enabled = store.apply("slot-1", AdsStateEvent.Enable)
        val loading = store.apply("slot-1", AdsStateEvent.StartLoad)
        val ready = store.apply("slot-1", AdsStateEvent.MarkReady, ObjectId("obj-1"))

        assertTrue(enabled is ApplyTransitionResult.Accepted)
        assertTrue(loading is ApplyTransitionResult.Accepted)
        assertTrue(ready is ApplyTransitionResult.Accepted)
        assertEquals(AdSlotState.READY, store.currentState("slot-1"))

        val history = store.historySnapshot()
        assertEquals(3, history.size)
        assertEquals(AdSlotState.DISABLED, history[0].from)
        assertEquals(AdSlotState.IDLE, history[0].to)
        assertEquals(ObjectId("obj-1"), history[2].objectId)
    }

    @Test
    fun apply_rejectsInvalidAndDuplicateCallbacks() {
        val store = AdsStateStore(clock = FakeClock())
        store.ensureSubject("slot-1", AdSlotState.READY)

        val invalid = store.apply("slot-1", AdsStateEvent.MarkShowing)
        assertTrue(invalid is ApplyTransitionResult.Rejected)
        assertEquals(AdSlotState.READY, store.currentState("slot-1"))
        assertTrue(store.historySnapshot().isEmpty())

        store.apply("slot-1", AdsStateEvent.Reserve)
        val duplicateReserve = store.apply("slot-1", AdsStateEvent.Reserve)
        assertTrue(duplicateReserve is ApplyTransitionResult.Rejected)
        assertEquals(AdSlotState.RESERVED, store.currentState("slot-1"))
    }

    @Test
    fun applyIf_onlyOneConcurrentReserveWins() = runBlocking {
        val store = AdsStateStore(clock = FakeClock())
        store.ensureSubject("obj-1", AdSlotState.READY)

        val results = (1..20).map {
            async(Dispatchers.Default) {
                store.applyIf(
                    subjectId = "obj-1",
                    expected = AdSlotState.READY,
                    event = AdsStateEvent.Reserve,
                    objectId = ObjectId("obj-1"),
                )
            }
        }.awaitAll()

        val accepted = results.filterIsInstance<ApplyTransitionResult.Accepted>()
        val rejected = results.filterIsInstance<ApplyTransitionResult.Rejected>()
        assertEquals(1, accepted.size)
        assertEquals(19, rejected.size)
        assertEquals(AdSlotState.RESERVED, store.currentState("obj-1"))
    }

    private class FakeClock(start: Long = 0L) : Clock {
        private val now = AtomicLong(start)
        override fun nowMillis(): Long = now.get()
        fun advance(delta: Long) {
            now.addAndGet(delta)
        }
    }
}
