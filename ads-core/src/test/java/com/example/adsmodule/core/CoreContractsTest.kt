package com.example.adsmodule.core

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreContractsTest {
    @Test
    fun typedIds_compareByTypeAndValue() {
        assertEquals(ConfigKey("config-1"), ConfigKey("config-1"))
        assertNotEquals(ConfigKey("config-1"), ConfigKey("config-2"))
        assertNotEquals(ConfigKey("shared"), ObjectId("shared"))
    }

    @Test
    fun onboardingPages_haveFourDistinctScreenInstances() {
        val screenIds = (1..4).map { ScreenInstanceId("ONBOARD_NATIVE#$it") }

        assertEquals(4, screenIds.toSet().size)
    }

    @Test
    fun originalConfig_serializesWithOriginalRemoteConfigFields() {
        val config = validConfig()

        val json = Json.encodeToString(config)
        val decoded = Json.decodeFromString<OriginalAdsConfig>(json)

        assertTrue(json.contains("\"list_ads\""))
        assertTrue(json.contains("\"enable_ad\""))
        assertTrue(json.contains("\"timeout_total\""))
        assertTrue(json.contains("\"type_layout\""))
        assertFalse(json.contains("candidates"))
        assertEquals(1, "\"list_ads\"".toRegex().findAll(json).count())
        assertEquals(config, decoded)
    }

    @Test
    fun storedAd_preservesSourceMetadataAndHandleIdentity() {
        val handle = TestHandle()
        val storedAd = StoredAd(
            objectId = ObjectId("object-1"),
            sourceConfigKey = ConfigKey("native_onboarding_1"),
            sourceListIndex = 2,
            sourceType = AdFormat.NATIVE,
            sourceAdunit = "native-unit",
            sourceWeight = 90,
            screenInstanceId = ScreenInstanceId("ONBOARD_NATIVE#1"),
            loadedAt = 123_456L,
            state = AdSlotState.READY,
            sdkHandle = handle,
        )

        assertEquals(ObjectId("object-1"), storedAd.objectId)
        assertEquals(ConfigKey("native_onboarding_1"), storedAd.sourceConfigKey)
        assertEquals(2, storedAd.sourceListIndex)
        assertEquals(AdFormat.NATIVE, storedAd.sourceType)
        assertEquals("native-unit", storedAd.sourceAdunit)
        assertEquals(90, storedAd.sourceWeight)
        assertEquals(ScreenInstanceId("ONBOARD_NATIVE#1"), storedAd.screenInstanceId)
        assertEquals(123_456L, storedAd.loadedAt)
        assertEquals(AdSlotState.READY, storedAd.state)
        assertSame(handle, storedAd.sdkHandle)
    }

    private fun validConfig(): OriginalAdsConfig = OriginalAdsConfig(
        enable = true,
        isOrganic = false,
        timeoutTotalMillis = 10_000L,
        typeLayout = "medium",
        listAds = listOf(
            OriginalAdItem(
                enableAd = true,
                weight = 100,
                timeoutMillis = 2_000L,
                type = "native",
                adunit = "native-unit",
            ),
        ),
    )

    private class TestHandle : SdkLoadedAdHandle {
        override val format: AdFormat = AdFormat.NATIVE
        override val adUnit: String = "native-unit"

        override fun destroy() = Unit
    }
}
