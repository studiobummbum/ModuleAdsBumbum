package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnbackSelectorTest {
    @Test
    fun select_picksHighestWeightEligible() {
        val selected = TurnbackSelector.select(
            listOf(
                ad("low", weight = 10, format = AdFormat.NATIVE),
                ad("high", weight = 90, format = AdFormat.BANNER),
                ad("mid", weight = 50, format = AdFormat.NATIVE),
            ),
        )
        assertEquals(ObjectId("high"), selected?.objectId)
    }

    @Test
    fun select_tieBreakIsDeterministic() {
        val first = ad(
            id = "a",
            weight = 50,
            loadedAt = 10L,
            config = "native_language_config_1",
            screen = "s1",
            listIndex = 1,
        )
        val second = ad(
            id = "b",
            weight = 50,
            loadedAt = 5L,
            config = "native_language_dup_config_1",
            screen = "s2",
            listIndex = 0,
        )
        val third = ad(
            id = "c",
            weight = 50,
            loadedAt = 5L,
            config = "banner_home_config_1",
            screen = "s0",
            listIndex = 2,
        )
        val selected = TurnbackSelector.select(listOf(first, second, third))
        // Equal weight → earliest loadedAt, then config key, screen, index, objectId
        assertEquals(ObjectId("c"), selected?.objectId)

        val again = TurnbackSelector.select(listOf(third, first, second))
        assertEquals(ObjectId("c"), again?.objectId)
    }

    @Test
    fun select_ignoresIneligibleFormatsAndNonReady() {
        assertNull(
            TurnbackSelector.select(
                listOf(
                    ad("inter", format = AdFormat.INTERSTITIAL, weight = 100),
                    ad("appopen", format = AdFormat.APP_OPEN, weight = 100),
                    ad("full", format = AdFormat.NATIVE_FULLSCREEN, weight = 100),
                    ad("not-ready", format = AdFormat.NATIVE, weight = 100, state = AdSlotState.RESERVED),
                ),
            ),
        )
        assertFalse(TurnbackSelector.isEligibleFormat(AdFormat.NATIVE_FULLSCREEN))
        assertTrue(TurnbackSelector.isEligibleFormat(AdFormat.NATIVE))
        assertTrue(TurnbackSelector.isEligibleFormat(AdFormat.BANNER))
    }

    private fun ad(
        id: String,
        weight: Int = 100,
        format: AdFormat = AdFormat.NATIVE,
        loadedAt: Long = 0L,
        config: String = "native_language_config_1",
        screen: String? = "screen-1",
        listIndex: Int = 0,
        state: AdSlotState = AdSlotState.READY,
    ): StoredAd = StoredAd(
        objectId = ObjectId(id),
        sourceConfigKey = ConfigKey(config),
        sourceListIndex = listIndex,
        sourceType = format,
        sourceAdunit = "unit-$id",
        sourceWeight = weight,
        screenInstanceId = screen?.let { ScreenInstanceId(it) },
        loadedAt = loadedAt,
        state = state,
        sdkHandle = object : SdkLoadedAdHandle {
            override val format: AdFormat = format
            override val adUnit: String = "unit-$id"
            override fun destroy() = Unit
        },
    )
}
