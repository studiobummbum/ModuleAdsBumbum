package com.example.adsmodule.core.debug

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugEventRingBufferTest {
    @Test
    fun append_emitsAndAppearsInSnapshot() = runTest {
        val buffer = DebugEventRingBuffer(capacity = 10)
        val collected = mutableListOf<DebugEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            buffer.events.collect { collected.add(it) }
        }

        val event = buffer.append(
            category = "load",
            message = "success",
            timestampMillis = 100L,
            details = mapOf("key" to "value"),
        )

        assertEquals(1, buffer.snapshot.value.size)
        assertEquals(event, buffer.latest())
        assertEquals("load", collected.last().category)
    }

    @Test
    fun append_evictsOldestWhenOverCapacity() {
        val buffer = DebugEventRingBuffer(capacity = 3)
        repeat(5) { index ->
            buffer.append(
                category = "c",
                message = "m$index",
                timestampMillis = index.toLong(),
            )
        }
        assertEquals(3, buffer.snapshot.value.size)
        assertEquals("m2", buffer.snapshot.value.first().message)
        assertEquals("m4", buffer.latest()?.message)
    }

    @Test
    fun clear_emptiesSnapshot() {
        val buffer = DebugEventRingBuffer()
        buffer.append(category = "x", message = "y", timestampMillis = 1L)
        buffer.clear()
        assertTrue(buffer.snapshot.value.isEmpty())
        assertNull(buffer.latest())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationDebugTrackerTest {
    @Test
    fun report_updatesStateFlow() = runTest {
        val tracker = NavigationDebugTracker()
        tracker.report(
            activityName = "HomeActivity",
            fragmentName = null,
            pagerIndex = 2,
            screenLabel = "Home",
        )
        val state = tracker.state.first()
        assertEquals("HomeActivity", state.activityName)
        assertEquals(2, state.pagerIndex)
        assertEquals("Home", state.screenLabel)
        assertTrue(state.updatedAtMillis > 0L)
    }
}

class PlacementDebugInspectorTest {
    @Test
    fun runtimeOrder_filtersDisabledAndSortsByWeightThenIndex() {
        val config = com.example.adsmodule.core.OriginalAdsConfig(
            enable = true,
            listAds = listOf(
                com.example.adsmodule.core.OriginalAdItem(
                    enableAd = true,
                    weight = 10,
                    adunit = "a",
                    sourceListIndex = 0,
                ),
                com.example.adsmodule.core.OriginalAdItem(
                    enableAd = false,
                    weight = 99,
                    adunit = "b",
                    sourceListIndex = 1,
                ),
                com.example.adsmodule.core.OriginalAdItem(
                    enableAd = true,
                    weight = 10,
                    adunit = "c",
                    sourceListIndex = 2,
                ),
                com.example.adsmodule.core.OriginalAdItem(
                    enableAd = true,
                    weight = 50,
                    adunit = "d",
                    sourceListIndex = 3,
                ),
            ),
        )
        val ordered = PlacementDebugInspector.runtimeOrder(
            configKey = com.example.adsmodule.core.ConfigKey("native_language_config_1"),
            config = config,
        )
        assertEquals(listOf("d", "a", "c"), ordered.map { it.adunit })
        assertNotNull(ordered.first().resolvedFormat)
    }
}
