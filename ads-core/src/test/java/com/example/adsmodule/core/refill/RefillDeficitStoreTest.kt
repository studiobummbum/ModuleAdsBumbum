package com.example.adsmodule.core.refill

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.storage.StorageSlotKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillDeficitStoreTest {
    private val slot = StorageSlotKey(
        ConfigKey("native_language_config_1"),
        ScreenInstanceId("screen-1"),
    )

    @Test
    fun deficit_usesReadyAndInFlightOnly() {
        val store = RefillDeficitStore()
        store.activate(slot, targetReadyCount = 2)
        assertEquals(2, store.deficit(slot, readyCount = 0))
        val began = store.tryBeginInFlight(slot, readyCount = 0) as BeginInFlightResult.Started
        assertEquals(1, store.deficit(slot, readyCount = 0))
        assertEquals(0, store.deficit(slot, readyCount = 1))
        assertTrue(store.endInFlight(slot, began.generation))
        assertEquals(1, store.deficit(slot, readyCount = 1))
    }

    @Test
    fun tryBeginInFlight_dedupesWhileInFlight() {
        val store = RefillDeficitStore()
        store.activate(slot, targetReadyCount = 1)
        assertTrue(store.tryBeginInFlight(slot, readyCount = 0) is BeginInFlightResult.Started)
        assertTrue(store.tryBeginInFlight(slot, readyCount = 0) is BeginInFlightResult.AlreadyInFlight)
    }

    @Test
    fun deactivate_bumpsGenerationAndClearsInFlight() {
        val store = RefillDeficitStore()
        val activated = store.activate(slot, targetReadyCount = 1)
        store.tryBeginInFlight(slot, readyCount = 0)
        val deactivated = store.deactivate(slot)!!
        assertFalse(deactivated.active)
        assertEquals(0, deactivated.inFlightCount)
        assertTrue(deactivated.generation > activated.generation)
        assertFalse(store.matchesGeneration(slot, activated.generation))
    }

    @Test
    fun tryBeginInFlight_rejectsWhenNoDeficitOrInactive() {
        val store = RefillDeficitStore()
        store.activate(slot, targetReadyCount = 1)
        assertTrue(store.tryBeginInFlight(slot, readyCount = 1) is BeginInFlightResult.Rejected)
        store.deactivate(slot)
        assertTrue(store.tryBeginInFlight(slot, readyCount = 0) is BeginInFlightResult.Rejected)
    }
}
