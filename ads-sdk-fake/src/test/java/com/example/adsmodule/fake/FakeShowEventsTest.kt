package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeShowEventsTest {
    @Test
    fun show_emitsImpressionClickDismissAtConfiguredVirtualTimes() = runTest {
        val environment = FakeTestEnvironment(this)
        val loadRequest = environment.request(
            loadRequestId = "load-show",
            format = AdFormat.INTERSTITIAL,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(loadRequest),
            FakeScenarioConfig(
                impressionDelayMillis = 100L,
                clickDelayMillis = 200L,
                dismissDelayMillis = 300L,
                fakeNetworkName = "fake-mediation",
                fakeRevenueMicros = 42_000L,
            ),
        )
        val handle = environment.adapter(AdFormat.INTERSTITIAL)
            .load(loadRequest)
            .requireFakeLoadedAd()
        val emitted = mutableListOf<AdShowEvent>()
        val show = async {
            environment.adapter(AdFormat.INTERSTITIAL)
                .show(AdShowRequest("show-1", handle))
                .toList(emitted)
        }
        runCurrent()
        assertEquals(listOf(AdShowEvent.Shown("show-1")), emitted)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(
            listOf(
                AdShowEvent.Shown("show-1"),
                AdShowEvent.Impression("show-1"),
            ),
            emitted,
        )

        advanceTimeBy(200L)
        runCurrent()
        assertEquals(
            listOf(
                AdShowEvent.Shown("show-1"),
                AdShowEvent.Impression("show-1"),
                AdShowEvent.Click("show-1"),
            ),
            emitted,
        )

        advanceTimeBy(300L)
        runCurrent()
        show.await()
        assertEquals(
            listOf(
                AdShowEvent.Shown("show-1"),
                AdShowEvent.Impression("show-1"),
                AdShowEvent.Click("show-1"),
                AdShowEvent.Dismiss("show-1"),
            ),
            emitted,
        )
        val impression = environment.controller.eventsSnapshot()
            .filterIsInstance<FakeSdkEvent.Impression>()
            .single()
        assertEquals(100L, impression.occurredAtMillis)
        assertEquals("fake-mediation", impression.fakeNetworkName)
        assertEquals(42_000L, impression.fakeRevenueMicros)
        assertEquals(
            listOf(0L, 100L, 300L, 600L),
            environment.controller.eventsSnapshot()
                .filter {
                    it is FakeSdkEvent.Shown ||
                        it is FakeSdkEvent.Impression ||
                        it is FakeSdkEvent.Click ||
                        it is FakeSdkEvent.Dismiss
                }
                .map(FakeSdkEvent::occurredAtMillis),
        )
    }

    @Test
    fun showFail_consumesHandleAndRepeatedShowFails() = runTest {
        val environment = FakeTestEnvironment(this)
        val loadRequest = environment.request(
            loadRequestId = "load-show-fail",
            format = AdFormat.APP_OPEN,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(loadRequest),
            FakeScenarioConfig(scenario = FakeScenario.SHOW_FAIL),
        )
        val handle = environment.adapter(AdFormat.APP_OPEN)
            .load(loadRequest)
            .requireFakeLoadedAd()

        val first = async {
            environment.adapter(AdFormat.APP_OPEN)
                .show(AdShowRequest("show-fail-1", handle))
                .toList()
        }
        runCurrent()
        val second = async {
            environment.adapter(AdFormat.APP_OPEN)
                .show(AdShowRequest("show-fail-2", handle))
                .toList()
        }
        runCurrent()

        val firstEvents = first.await()
        val secondEvents = second.await()
        assertTrue(firstEvents.single() is AdShowEvent.Fail)
        assertTrue(secondEvents.single() is AdShowEvent.Fail)
        assertFalse(firstEvents.any { it is AdShowEvent.Shown })
        assertFalse(secondEvents.any { it is AdShowEvent.Shown })
        assertTrue(handle.consumed)
        assertEquals(
            2,
            environment.controller.eventsSnapshot().count { it is FakeSdkEvent.ShowFailed },
        )
        assertFalse(
            environment.controller.eventsSnapshot().any { it is FakeSdkEvent.Shown },
        )
    }

    @Test
    fun wrongAdapterAndDestroyedHandle_failWithoutConsuming() = runTest {
        val environment = FakeTestEnvironment(this)
        val loadRequest = environment.request(
            loadRequestId = "load-native",
            format = AdFormat.NATIVE,
        )
        val handle = environment.adapter(AdFormat.NATIVE)
            .load(loadRequest)
            .requireFakeLoadedAd()

        val wrongAdapter = async {
            environment.adapter(AdFormat.BANNER)
                .show(AdShowRequest("show-wrong", handle))
                .toList()
        }
        runCurrent()
        assertTrue(wrongAdapter.await().single() is AdShowEvent.Fail)
        assertFalse(handle.consumed)

        handle.destroy()
        val destroyed = async {
            environment.adapter(AdFormat.NATIVE)
                .show(AdShowRequest("show-destroyed", handle))
                .toList()
        }
        runCurrent()
        assertTrue(destroyed.await().single() is AdShowEvent.Fail)
        assertFalse(handle.consumed)
    }

    @Test
    fun destroyDuringShow_stopsLaterAdEvents() = runTest {
        val environment = FakeTestEnvironment(this)
        val loadRequest = environment.request(
            loadRequestId = "load-destroy-mid-show",
            format = AdFormat.NATIVE_FULLSCREEN,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(loadRequest),
            FakeScenarioConfig(
                impressionDelayMillis = 0L,
                clickDelayMillis = 100L,
                dismissDelayMillis = 100L,
            ),
        )
        val handle = environment.adapter(AdFormat.NATIVE_FULLSCREEN)
            .load(loadRequest)
            .requireFakeLoadedAd()
        val show = async {
            environment.adapter(AdFormat.NATIVE_FULLSCREEN)
                .show(AdShowRequest("show-destroy-mid", handle))
                .toList()
        }
        runCurrent()

        handle.destroy()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(
            listOf(
                AdShowEvent.Shown("show-destroy-mid"),
                AdShowEvent.Impression("show-destroy-mid"),
            ),
            show.await(),
        )
        assertFalse(
            environment.controller.eventsSnapshot().any {
                it is FakeSdkEvent.Click || it is FakeSdkEvent.Dismiss
            },
        )
    }
}
