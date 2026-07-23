package com.example.adsmodule.core.analytics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class InMemoryAdsAnalyticsTest {
    @Test
    fun track_requiresSessionNameAndTimestamp() {
        val analytics = InMemoryAdsAnalytics()
        assertNull(
            analytics.track(
                AdsAnalyticsEvent(
                    category = AdsEventCategory.LOAD,
                    name = "",
                    timestamp = 1L,
                    sessionId = "s1",
                ),
            ),
        )
        assertNull(
            analytics.track(
                AdsAnalyticsEvent(
                    category = AdsEventCategory.LOAD,
                    name = "ok",
                    timestamp = 1L,
                    sessionId = " ",
                ),
            ),
        )
        assertNull(
            analytics.track(
                AdsAnalyticsEvent(
                    category = AdsEventCategory.LOAD,
                    name = "ok",
                    timestamp = -1L,
                    sessionId = "s1",
                ),
            ),
        )
        assertTrue(analytics.snapshot.value.isEmpty())

        val stored = analytics.track(
            AdsAnalyticsEvent(
                category = AdsEventCategory.LOAD,
                name = "ads_candidate_load_success",
                timestamp = 10L,
                sessionId = "s1",
                snapshotVersion = 3L,
                configKey = "native_language_config_1",
                itemIndex = 0,
                weight = 100,
                adunitAlias = "hf-native",
            ),
        )
        assertNotNull(stored)
        assertEquals(1, analytics.snapshot.value.size)
        assertEquals("s1", stored!!.sessionId)
        assertEquals(3L, stored.snapshotVersion)
    }

    @Test
    fun exportJson_containsTrackedFields() {
        val analytics = InMemoryAdsAnalytics()
        analytics.track(
            AdsAnalyticsEvent(
                category = AdsEventCategory.SHOW,
                name = "ads_show_success",
                timestamp = 42L,
                sessionId = "session-a",
                showRequestId = "show-1",
                objectId = "obj-1",
                result = "success",
            ),
        )
        val json = analytics.exportJson()
        val array = Json.parseToJsonElement(json) as JsonArray
        assertEquals(1, array.size)
        val obj = array.first().jsonObject
        assertEquals("show", obj["category"]!!.jsonPrimitive.content)
        assertEquals("ads_show_success", obj["name"]!!.jsonPrimitive.content)
        assertEquals("session-a", obj["sessionId"]!!.jsonPrimitive.content)
        assertEquals("show-1", obj["showRequestId"]!!.jsonPrimitive.content)
        assertEquals("obj-1", obj["objectId"]!!.jsonPrimitive.content)
    }

    @Test
    fun filter_searchAndMarkersOnly() {
        val analytics = InMemoryAdsAnalytics()
        analytics.track(
            AdsAnalyticsEvent(
                category = AdsEventCategory.LOAD,
                name = "load_ok",
                timestamp = 1L,
                sessionId = "s",
            ),
        )
        analytics.track(
            AdsAnalyticsEvent(
                category = AdsEventCategory.FULL_EXIT,
                name = "exit_ignored",
                timestamp = 2L,
                sessionId = "s",
                markers = setOf(AdsEventMarker.STALE),
                exitSource = "CLOSE_X",
            ),
        )
        assertEquals(1, analytics.filter(category = AdsEventCategory.FULL_EXIT).size)
        assertEquals(1, analytics.filter(query = "stale").size)
        assertEquals(1, analytics.filter(markersOnly = true).size)
        assertEquals(1, analytics.filter(query = "CLOSE_X").size)
    }

    @Test
    fun sanitizer_redactsPiiAndSecrets() {
        val analytics = InMemoryAdsAnalytics()
        val stored = analytics.track(
            AdsAnalyticsEvent(
                category = AdsEventCategory.CONFIG,
                name = "parse_fail",
                timestamp = 1L,
                sessionId = "s",
                error = "user user@example.com phone +1 555-123-4567 api_key=supersecret",
                adunitAlias = "ca-app-pub-1234567890123456/1234567890",
                details = mapOf(
                    "note" to "gaid=550e8400-e29b-41d4-a716-446655440000",
                ),
            ),
        )
        assertNotNull(stored)
        val event = stored!!
        val error = checkNotNull(event.error)
        assertFalse(error.contains("user@example.com"))
        assertTrue(error.contains(AdsAnalyticsSanitizer.REDACTED))
        assertEquals(AdsAnalyticsSanitizer.REDACTED, event.adunitAlias)
        assertTrue(event.details.getValue("note").contains(AdsAnalyticsSanitizer.REDACTED))
    }

    @Test
    fun composite_remoteFailureDoesNotDropLocalOrThrow() {
        val local = InMemoryAdsAnalytics()
        val remoteCalls = AtomicInteger(0)
        val composite = CompositeAdsAnalytics(
            local = local,
            remote = object : AdsAnalyticsRemoteAdapter {
                override fun send(event: AdsAnalyticsEvent) {
                    remoteCalls.incrementAndGet()
                    error("remote down")
                }
            },
        )
        val stored = composite.track(
            AdsAnalyticsEvent(
                category = AdsEventCategory.STORAGE,
                name = "atomic_pop",
                timestamp = 5L,
                sessionId = "s",
                objectId = "o1",
            ),
        )
        assertNotNull(stored)
        assertEquals(1, local.snapshot.value.size)
        assertEquals(1, remoteCalls.get())
        assertEquals("atomic_pop", composite.latest()?.name)
        val exported = composite.exportJson()
        assertTrue(exported.contains("atomic_pop"))
    }

    @Test
    fun raceEvents_preserveMarkersUnderConcurrentTrack() {
        val analytics = InMemoryAdsAnalytics(capacity = 500)
        val threads = 8
        val perThread = 25
        val latch = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) { threadIndex ->
            pool.execute {
                try {
                    repeat(perThread) { i ->
                        val markers = when {
                            i % 5 == 0 -> setOf(AdsEventMarker.STALE)
                            i % 7 == 0 -> setOf(AdsEventMarker.DUPLICATE)
                            else -> emptySet()
                        }
                        analytics.track(
                            AdsAnalyticsEvent(
                                category = AdsEventCategory.FULL_EXIT,
                                name = "finishAndContinueOnce",
                                timestamp = (threadIndex * 1_000L) + i,
                                sessionId = "race-session",
                                fullSessionId = "full-$threadIndex",
                                markers = markers,
                                result = if (markers.isEmpty()) "accepted" else "ignored",
                            ),
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(threads * perThread, analytics.snapshot.value.size)
        val marked = analytics.filter(markersOnly = true)
        assertTrue(marked.isNotEmpty())
        assertTrue(marked.all { it.markers.isNotEmpty() })
        assertTrue(marked.any { AdsEventMarker.STALE in it.markers })
        assertTrue(marked.any { AdsEventMarker.DUPLICATE in it.markers })
    }

    @Test
    fun track_neverThrowsWhenInternalStateCorruptible() {
        val analytics = InMemoryAdsAnalytics()
        // Valid event after capacity eviction still succeeds.
        repeat(250) { index ->
            assertNotNull(
                analytics.track(
                    AdsAnalyticsEvent(
                        category = AdsEventCategory.NAVIGATION,
                        name = "nav_$index",
                        timestamp = index.toLong(),
                        sessionId = "s",
                    ),
                ),
            )
        }
        assertEquals(InMemoryAdsAnalytics.DEFAULT_CAPACITY, analytics.snapshot.value.size)
        assertEquals("nav_249", analytics.latest()?.name)
    }
}
