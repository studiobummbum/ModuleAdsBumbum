package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeLoadedAdTest {
    @Test
    fun loadedAd_keepsDeterministicIdentityTimeAndItemMetadata() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-metadata",
            format = AdFormat.NATIVE,
            adUnit = "native-metadata-unit",
            sourceConfigKey = "native_language_config",
            sourceListIndex = 4,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(
                fakeNetworkName = "network-a",
                fakeRevenueMicros = 7_500L,
            ),
        )
        advanceTimeBy(123L)

        val handle = environment.adapter(AdFormat.NATIVE)
            .load(request)
            .requireFakeLoadedAd()

        assertEquals("test-object-1", handle.objectId)
        assertEquals("load-metadata", handle.loadRequestId)
        assertEquals("native_language_config", handle.sourceConfigKey)
        assertEquals(4, handle.sourceListIndex)
        assertEquals(123L, handle.createdAt)
        assertEquals("network-a", handle.fakeNetworkName)
        assertEquals(7_500L, handle.fakeRevenueMicros)
        assertFalse(handle.consumed)
        assertFalse(handle.destroyed)
    }

    @Test
    fun consume_isAtomicAndCanSucceedOnlyOnce() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-consume",
            format = AdFormat.BANNER,
        )
        val handle = environment.adapter(AdFormat.BANNER)
            .load(request)
            .requireFakeLoadedAd()

        val first = async { handle.tryConsume() }
        val second = async { handle.tryConsume() }
        runCurrent()

        assertEquals(1, listOf(first.await(), second.await()).count { it })
        assertTrue(handle.consumed)
    }

    @Test
    fun destroy_isIdempotentAndObservableOnce() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-destroy",
            format = AdFormat.INTERSTITIAL,
        )
        val handle = environment.adapter(AdFormat.INTERSTITIAL)
            .load(request)
            .requireFakeLoadedAd()

        handle.destroy()
        handle.destroy()

        assertTrue(handle.destroyed)
        assertEquals(
            1,
            environment.controller.eventsSnapshot()
                .count { it is FakeSdkEvent.Destroyed && it.objectId == handle.objectId },
        )
    }
}
