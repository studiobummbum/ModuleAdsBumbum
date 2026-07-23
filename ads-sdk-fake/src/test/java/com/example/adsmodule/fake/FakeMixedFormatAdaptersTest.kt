package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeMixedFormatAdaptersTest {
    @Test
    fun moduleFactory_exposesExactlyOneAdapterPerFormat() = runTest {
        val environment = FakeTestEnvironment(this)

        assertEquals(AdFormat.entries.size, environment.sdk.adapters.size)
        assertEquals(
            AdFormat.entries.toSet(),
            environment.sdk.adapters.flatMap { it.supportedFormats }.toSet(),
        )
        AdFormat.entries.forEach { format ->
            assertEquals(setOf(format), environment.sdk.adapterFor(format).supportedFormats)
        }
    }

    @Test
    fun mixedFormats_routeIndependentItemsThroughSharedController() = runTest {
        val environment = FakeTestEnvironment(this)
        val formats = listOf(
            AdFormat.INTERSTITIAL,
            AdFormat.APP_OPEN,
            AdFormat.NATIVE,
            AdFormat.BANNER,
            AdFormat.NATIVE_FULLSCREEN,
        )
        val requests = formats.mapIndexed { index, format ->
            environment.request(
                loadRequestId = "mixed-$index",
                format = format,
                adUnit = "mixed-${format.name.lowercase()}",
                sourceConfigKey = "inter_splash_config_1",
                sourceListIndex = index,
            )
        }
        environment.controller.setScenario(
            FakeAdItemKey.from(requests[1]),
            FakeScenarioConfig(scenario = FakeScenario.FAIL),
        )

        val results = requests.map { request ->
            environment.adapter(request.format).load(request)
        }

        assertTrue(results[0] is AdLoadResult.Success)
        assertTrue(results[1] is AdLoadResult.Failure)
        assertTrue(results[2] is AdLoadResult.Success)
        assertTrue(results[3] is AdLoadResult.Success)
        assertTrue(results[4] is AdLoadResult.Success)
        requests.forEach { request ->
            assertEquals(
                1,
                environment.controller.requestCount(FakeAdItemKey.from(request)),
            )
        }
    }

    @Test
    fun adapter_rejectsMismatchedRequestFormatBeforeSdkRequest() = runTest {
        val environment = FakeTestEnvironment(this)
        val request = environment.request(
            loadRequestId = "wrong-format",
            format = AdFormat.NATIVE,
        )

        val result = environment.sdk.interstitialAdapter.load(request)

        assertTrue(result is AdLoadResult.Failure)
        assertEquals(0, environment.controller.requestCount(FakeAdItemKey.from(request)))
        assertTrue(environment.controller.eventsSnapshot().isEmpty())
    }
}
