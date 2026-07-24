package com.example.adsmodule.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AdSdkContractsTest {
    @Test
    fun adapter_returnsConfiguredLoadResults() = runBlocking {
        val handle = TestHandle()
        val metadata = AdRequestMetadata(
            sourceConfigKey = "native_language_config",
            sourceListIndex = 2,
        )
        val request = AdLoadRequest(
            loadRequestId = "load-1",
            format = AdFormat.NATIVE,
            adUnit = "native-unit",
            timeoutMillis = 1_000L,
            metadata = metadata,
        )

        assertEquals(metadata, request.metadata)
        assertSame(
            handle,
            TestAdapter(AdLoadResult.Success(handle)).load(request).successHandle(),
        )
        assertEquals(
            AdLoadResult.Failure(reason = "network"),
            TestAdapter(AdLoadResult.Failure(reason = "network")).load(request),
        )
        assertEquals(
            AdLoadResult.Timeout,
            TestAdapter(AdLoadResult.Timeout).load(request),
        )
    }

    @Test
    fun requestMetadata_rejectsInvalidSourceIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            AdRequestMetadata(
                sourceConfigKey = " ",
                sourceListIndex = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdRequestMetadata(
                sourceConfigKey = "native_language_config",
                sourceListIndex = -1,
            )
        }
    }

    @Test
    fun show_exposesOrderedEventsThroughFlow() = runBlocking {
        val handle = TestHandle()
        val request = AdShowRequest(
            showRequestId = "show-1",
            handle = handle,
        )
        val events = listOf(
            AdShowEvent.Impression("show-1"),
            AdShowEvent.Click("show-1"),
            AdShowEvent.Dismiss("show-1"),
        )

        assertEquals(
            events,
            TestAdapter(
                loadResult = AdLoadResult.Success(handle),
                showEvents = events,
            ).show(request).toList(),
        )
    }

    @Test
    fun showRequest_defaultsHostToNullAndAcceptsOpaqueHost() {
        val handle = TestHandle()
        val withoutHost = AdShowRequest(showRequestId = "show-1", handle = handle)
        assertEquals(null, withoutHost.host)

        val host = object : AdPresentationHost {}
        val withHost = AdShowRequest(
            showRequestId = "show-2",
            handle = handle,
            host = host,
        )
        assertSame(host, withHost.host)
    }

    private fun AdLoadResult.successHandle(): SdkLoadedAdHandle =
        (this as AdLoadResult.Success).handle

    private class TestAdapter(
        private val loadResult: AdLoadResult,
        private val showEvents: List<AdShowEvent> = emptyList(),
    ) : AdSdkAdapter {
        override val supportedFormats: Set<AdFormat> = setOf(AdFormat.NATIVE)

        override suspend fun load(request: AdLoadRequest): AdLoadResult = loadResult

        override fun show(request: AdShowRequest): Flow<AdShowEvent> =
            flowOf(*showEvents.toTypedArray())
    }

    private class TestHandle : SdkLoadedAdHandle {
        override val format: AdFormat = AdFormat.NATIVE
        override val adUnit: String = "native-unit"

        override fun destroy() = Unit
    }
}
