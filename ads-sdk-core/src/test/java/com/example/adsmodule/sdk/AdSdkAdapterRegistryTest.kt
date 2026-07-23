package com.example.adsmodule.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSdkAdapterRegistryTest {
    @Test
    fun create_mapsEachFormatToASingleAdapter() {
        val native = TestAdapter(setOf(AdFormat.NATIVE, AdFormat.NATIVE_FULLSCREEN))
        val inter = TestAdapter(setOf(AdFormat.INTERSTITIAL))
        val registry = AdSdkAdapterRegistry.create(listOf(native, inter))

        assertSame(native, registry.adapterFor(AdFormat.NATIVE))
        assertSame(native, registry.adapterFor(AdFormat.NATIVE_FULLSCREEN))
        assertSame(inter, registry.requireAdapter(AdFormat.INTERSTITIAL))
        assertNull(registry.adapterFor(AdFormat.BANNER))
        assertEquals(
            setOf(AdFormat.NATIVE, AdFormat.NATIVE_FULLSCREEN, AdFormat.INTERSTITIAL),
            registry.registeredFormats,
        )
    }

    @Test
    fun create_rejectsDuplicateFormatOwnership() {
        val first = TestAdapter(setOf(AdFormat.BANNER))
        val second = TestAdapter(setOf(AdFormat.BANNER, AdFormat.APP_OPEN))

        val error = assertThrows(IllegalArgumentException::class.java) {
            AdSdkAdapterRegistry.create(listOf(first, second))
        }
        assertTrue(error.message!!.contains(AdFormat.BANNER.name))
    }

    @Test
    fun requireAdapter_failsWhenMissing() {
        val registry = AdSdkAdapterRegistry.create(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            registry.requireAdapter(AdFormat.APP_OPEN)
        }
    }

    private class TestAdapter(
        override val supportedFormats: Set<AdFormat>,
    ) : AdSdkAdapter {
        override suspend fun load(request: AdLoadRequest): AdLoadResult =
            AdLoadResult.Failure(reason = "unused")

        override fun show(request: AdShowRequest): Flow<AdShowEvent> = emptyFlow()
    }
}
