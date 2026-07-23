package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdRequestMetadata
import com.example.adsmodule.sdk.AdSdkAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeLoadScenariosTest {
    @Test
    fun successAndFail_returnConfiguredResultsAndMetadata() = runTest {
        val environment = FakeTestEnvironment(this)
        val successRequest = environment.request(
            loadRequestId = "load-success",
            format = AdFormat.NATIVE,
            adUnit = "native-unit",
            sourceListIndex = 2,
        )
        val failRequest = environment.request(
            loadRequestId = "load-fail",
            format = AdFormat.NATIVE,
            adUnit = "fail-unit",
            sourceListIndex = 3,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(failRequest),
            FakeScenarioConfig(scenario = FakeScenario.FAIL),
        )

        val loadedAd = environment.adapter(AdFormat.NATIVE)
            .load(successRequest)
            .requireFakeLoadedAd()
        val failed = environment.adapter(AdFormat.NATIVE).load(failRequest)

        assertEquals("test-object-1", loadedAd.objectId)
        assertEquals(successRequest.metadata, loadedAd.metadata)
        assertEquals(successRequest.adUnit, loadedAd.adUnit)
        assertEquals(AdFormat.NATIVE, loadedAd.format)
        assertTrue(failed is AdLoadResult.Failure)
    }

    @Test
    fun delayedSuccess_usesVirtualTime() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-delayed",
            format = AdFormat.INTERSTITIAL,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 1_000L,
            ),
        )

        val result = async {
            environment.adapter(AdFormat.INTERSTITIAL).load(request)
        }
        runCurrent()
        assertFalse(result.isCompleted)

        advanceTimeBy(999L)
        runCurrent()
        assertFalse(result.isCompleted)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(result.await() is AdLoadResult.Success)
        assertEquals(1_000L, result.await().requireFakeLoadedAd().createdAt)
    }

    @Test
    fun neverCallback_waitsUntilCallerCancels() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "load-never",
            format = AdFormat.APP_OPEN,
        )
        environment.controller.setScenario(
            FakeAdItemKey.from(request),
            FakeScenarioConfig(scenario = FakeScenario.NEVER_CALLBACK),
        )

        val result = async {
            environment.adapter(AdFormat.APP_OPEN).load(request)
        }
        runCurrent()
        assertFalse(result.isCompleted)

        result.cancelAndJoin()

        assertTrue(result.isCancelled)
        assertTrue(
            environment.controller.eventsSnapshot()
                .any { it is FakeSdkEvent.LoadCancelled },
        )
    }

    @Test
    fun requestCounters_areIsolatedByConfigIndexAndAdUnit() = runTest {
        val environment = FakeTestEnvironment(this)
        val first = environment.request(
            loadRequestId = "load-1",
            format = AdFormat.BANNER,
            adUnit = "shared-unit",
            sourceListIndex = 0,
        )
        val second = first.copy(loadRequestId = "load-2")
        val otherItem = environment.request(
            loadRequestId = "load-3",
            format = AdFormat.BANNER,
            adUnit = "shared-unit",
            sourceListIndex = 1,
        )

        environment.adapter(AdFormat.BANNER).load(first)
        environment.adapter(AdFormat.BANNER).load(second)
        environment.adapter(AdFormat.BANNER).load(otherItem)

        assertEquals(2, environment.controller.requestCount(FakeAdItemKey.from(first)))
        assertEquals(1, environment.controller.requestCount(FakeAdItemKey.from(otherItem)))
    }

    @Test
    fun eventStream_exposesDeterministicLoadEvents() = runTest {
        val environment = FakeTestEnvironment(this)
        val observed = mutableListOf<FakeSdkEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            environment.controller.events.toList(observed)
        }
        val request = environment.request(
            loadRequestId = "load-observed",
            format = AdFormat.NATIVE_FULLSCREEN,
        )

        environment.adapter(AdFormat.NATIVE_FULLSCREEN).load(request)

        assertEquals(
            listOf(
                FakeSdkEvent.LoadRequested::class,
                FakeSdkEvent.LoadCallbackAttempt::class,
                FakeSdkEvent.LoadCallbackAccepted::class,
            ),
            observed.map { it::class },
        )
        collector.cancelAndJoin()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class FakeTestEnvironment(
    scope: TestScope,
) {
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)

    val controller: FakeAdsSdkController = FakeAdsSdkController(
        clock = FakeClock { scope.testScheduler.currentTime },
        dispatcher = dispatcher,
        objectIdGenerator = SequentialFakeObjectIdGenerator(prefix = "test-object"),
    )
    val sdk: FakeAdsSdk = FakeAdsSdkModule.create(controller)

    fun adapter(format: AdFormat): AdSdkAdapter = sdk.adapterFor(format)

    fun request(
        loadRequestId: String,
        format: AdFormat,
        adUnit: String = "${format.name.lowercase()}-unit",
        sourceConfigKey: String = "test_config",
        sourceListIndex: Int = 0,
    ): AdLoadRequest = AdLoadRequest(
        loadRequestId = loadRequestId,
        format = format,
        adUnit = adUnit,
        timeoutMillis = 5_000L,
        metadata = AdRequestMetadata(
            sourceConfigKey = sourceConfigKey,
            sourceListIndex = sourceListIndex,
        ),
    )
}

internal fun AdLoadResult.requireFakeLoadedAd(): FakeLoadedAd =
    (this as AdLoadResult.Success).handle as FakeLoadedAd
