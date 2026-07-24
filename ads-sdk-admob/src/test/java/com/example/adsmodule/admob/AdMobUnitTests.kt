package com.example.adsmodule.admob

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AdMobAdUnitResolverTest {
    @Test
    fun testMode_remapsProductionLookingUnitToOfficialSample() {
        val resolver = AdMobAdUnitResolver(AdMobRuntimeMode.TEST)
        val resolved = resolver.resolve(
            AdFormat.INTERSTITIAL,
            "ca-app-pub-1111111111111111/2222222222",
        )
        assertEquals(AdMobTestAdUnits.INTERSTITIAL, resolved.adUnit)
        assertTrue(resolved.usedTestUnit)
        assertEquals("ca-app-pub-1111111111111111/2222222222", resolved.remappedFrom)
    }

    @Test
    fun testMode_allFormats_useOfficialSamplesOnly() {
        val resolver = AdMobAdUnitResolver(AdMobRuntimeMode.TEST)
        AdFormat.entries.forEach { format ->
            val resolved = resolver.resolve(format, "production-looking-unit")
            assertTrue(AdMobTestAdUnits.isOfficialTestUnit(resolved.adUnit))
            assertEquals(AdMobTestAdUnits.forFormat(format), resolved.adUnit)
        }
    }

    @Test
    fun productionMode_keepsRequestedUnit() {
        val resolver = AdMobAdUnitResolver(AdMobRuntimeMode.PRODUCTION)
        val resolved = resolver.resolve(AdFormat.BANNER, "ca-app-pub-999/888")
        assertEquals("ca-app-pub-999/888", resolved.adUnit)
        assertFalse(resolved.usedTestUnit)
    }
}

class AdMobAdsSdkModuleTest {
    @Test
    fun registry_registersFiveFormatsWithoutDuplicates() {
        val adapters = AdFormat.entries.map { format ->
            object : AdSdkAdapter {
                override val supportedFormats: Set<AdFormat> = setOf(format)
                override suspend fun load(request: AdLoadRequest): AdLoadResult =
                    AdLoadResult.Failure("unused")
                override fun show(request: AdShowRequest): Flow<AdShowEvent> = emptyFlow()
            }
        }
        val registry = AdSdkAdapterRegistry.create(adapters)
        assertEquals(AdFormat.entries.toSet(), registry.registeredFormats)
    }
}

class AdMobHandleDestroyTest {
    @Test
    fun interstitialHandle_destroyIsIdempotent() {
        val destroyedCallbacks = AtomicBoolean(false)
        val adRef = AtomicReference<Any?>(Any())
        val handle = object : SdkLoadedAdHandle {
            override val format = AdFormat.INTERSTITIAL
            override val adUnit = AdMobTestAdUnits.INTERSTITIAL
            private val destroyed = AtomicBoolean(false)
            override fun destroy() {
                if (!destroyed.compareAndSet(false, true)) return
                adRef.getAndSet(null)
                destroyedCallbacks.set(true)
            }
        }
        handle.destroy()
        handle.destroy()
        assertTrue(destroyedCallbacks.get())
        assertEquals(null, adRef.get())
    }

    @Test
    fun admobInterstitialLoadedAd_takeAdAfterNullIsNull() {
        val handle = AdMobInterstitialLoadedAd(
            adUnit = AdMobTestAdUnits.INTERSTITIAL,
            adRef = AtomicReference(null),
        )
        assertEquals(null, handle.takeAd())
        handle.destroy()
        handle.destroy()
    }
}

class AdMobInterstitialShowHostTest {
    @Test
    fun show_withoutHost_emitsFail() = runBlocking {
        val events = AdMobFullscreenShowHelper.show(
            showRequestId = "show-1",
            activity = null,
            attachCallback = { },
            present = { error("should not present") },
        ).toList()
        assertEquals(1, events.size)
        val fail = events.single() as AdShowEvent.Fail
        assertEquals("show-1", fail.showRequestId)
        assertTrue(fail.reason.contains("Activity"))
    }
}

class AdMobTestAdUnitsSmokeTest {
    @Test
    fun sampleApplicationId_isOfficialGoogleSample() {
        assertTrue(
            AdMobTestAdUnits.isOfficialTestApplicationId(AdMobTestAdUnits.SAMPLE_APPLICATION_ID),
        )
        assertFalse(
            AdMobTestAdUnits.isOfficialTestApplicationId("ca-app-pub-0000000000000000~0000000000"),
        )
    }

    @Test
    fun noProductionId_inOfficialTestCatalog() {
        val units = listOf(
            AdMobTestAdUnits.INTERSTITIAL,
            AdMobTestAdUnits.APP_OPEN,
            AdMobTestAdUnits.NATIVE,
            AdMobTestAdUnits.BANNER,
        )
        units.forEach { unit ->
            assertTrue(unit.startsWith("ca-app-pub-3940256097505524/"))
            assertTrue(AdMobTestAdUnits.isOfficialTestUnit(unit))
        }
    }
}
