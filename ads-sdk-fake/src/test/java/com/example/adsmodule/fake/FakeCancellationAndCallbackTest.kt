package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeCancellationAndCallbackTest {
    @Test
    fun lateCallback_afterCancellationIsIgnoredAndDiscarded() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-late",
            format = AdFormat.INTERSTITIAL,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(
                scenario = FakeScenario.LATE_CALLBACK,
                loadDelayMillis = 100L,
                callbackAfterCancel = true,
            ),
        )
        val result = async {
            environment.adapter(AdFormat.INTERSTITIAL).load(request)
        }
        runCurrent()

        result.cancelAndJoin()
        advanceTimeBy(100L)
        runCurrent()

        val events = environment.controller.eventsSnapshot()
        assertTrue(events.any { it is FakeSdkEvent.LoadCancelled })
        assertTrue(events.any { it is FakeSdkEvent.LoadCallbackAttempt })
        assertTrue(
            events.any {
                it is FakeSdkEvent.LoadCallbackIgnored &&
                    it.reason == FakeIgnoredCallbackReason.CANCELLED
            },
        )
        assertEquals(1, events.count { it is FakeSdkEvent.Destroyed })
        assertTrue(environment.controller.handlesSnapshot().isEmpty())
    }

    @Test
    fun callbackAfterCancelFalse_cancelsPendingCallback() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-cancelled",
            format = AdFormat.APP_OPEN,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(
                scenario = FakeScenario.LATE_CALLBACK,
                loadDelayMillis = 100L,
                callbackAfterCancel = false,
            ),
        )
        val result = async {
            environment.adapter(AdFormat.APP_OPEN).load(request)
        }
        runCurrent()

        result.cancelAndJoin()
        advanceTimeBy(100L)
        runCurrent()

        assertFalse(
            environment.controller.eventsSnapshot()
                .any { it is FakeSdkEvent.LoadCallbackAttempt },
        )
    }

    @Test
    fun duplicateCallback_acceptsOnlyFirstAndDestroysSecondHandle() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-duplicate",
            format = AdFormat.NATIVE,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(scenario = FakeScenario.DUPLICATE_CALLBACK),
        )

        val result = async {
            environment.adapter(AdFormat.NATIVE).load(request)
        }
        runCurrent()
        val accepted = result.await().requireFakeLoadedAd()
        runCurrent()

        val events = environment.controller.eventsSnapshot()
        assertEquals(2, events.count { it is FakeSdkEvent.LoadCallbackAttempt })
        assertEquals(1, events.count { it is FakeSdkEvent.LoadCallbackAccepted })
        assertTrue(
            events.any {
                it is FakeSdkEvent.LoadCallbackIgnored &&
                    it.reason == FakeIgnoredCallbackReason.DUPLICATE
            },
        )
        assertFalse(accepted.destroyed)
        assertEquals(listOf(accepted), environment.controller.handlesSnapshot())
    }

    @Test
    fun reset_cancelsPendingWorkAndClearsMutableState() = runTest {
        val environment = FakeTestEnvironment(this)
        val acceptedRequest = environment.request(
            loadRequestId = "load-accepted",
            format = AdFormat.BANNER,
        )
        val accepted = environment.adapter(AdFormat.BANNER)
            .load(acceptedRequest)
            .requireFakeLoadedAd()
        val pendingRequest = environment.request(
            loadRequestId = "load-pending",
            format = AdFormat.NATIVE_FULLSCREEN,
            sourceListIndex = 1,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(pendingRequest),
            FakeScenarioConfig(
                scenario = FakeScenario.LATE_CALLBACK,
                loadDelayMillis = 1_000L,
                callbackAfterCancel = true,
            ),
        )
        val pending = async {
            environment.adapter(AdFormat.NATIVE_FULLSCREEN).load(pendingRequest)
        }
        runCurrent()

        environment.controller.reset()
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(pending.isCancelled)
        assertTrue(accepted.destroyed)
        assertTrue(environment.controller.handlesSnapshot().isEmpty())
        assertTrue(environment.controller.eventsSnapshot().isEmpty())
        assertEquals(0, environment.controller.requestCount(FakeAdItemKey.from(pendingRequest)))
        assertEquals(
            FakeScenarioConfig(),
            environment.controller.scenarioFor(FakeAdItemKey.from(pendingRequest)),
        )
    }

    @Test
    fun failureCallback_isTerminalWithoutCreatingHandle() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-failure",
            format = AdFormat.BANNER,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(scenario = FakeScenario.FAIL),
        )

        val result = environment.adapter(AdFormat.BANNER).load(request)

        assertTrue(result is AdLoadResult.Failure)
        assertTrue(environment.controller.handlesSnapshot().isEmpty())
    }
}
