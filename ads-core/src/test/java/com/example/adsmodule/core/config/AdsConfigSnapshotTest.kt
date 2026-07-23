package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdsConfigSnapshotTest {
    private val key = ConfigKey("inter_splash_config_1")

    @Test
    fun snapshotDefensivelyCopiesMapsAndNestedLists() {
        val mutableItems = mutableListOf(
            OriginalAdItem(
                enableAd = true,
                weight = 100,
                adunit = "unit",
                sourceListIndex = 0,
            ),
        )
        val input = mutableMapOf(
            key to ResolvedConfig(
                value = AdsConfigValue(
                    OriginalAdsConfig(
                        enable = true,
                        listAds = mutableItems,
                    ),
                ),
                canonicalJson = validCanonical("unit"),
                origin = ConfigValueOrigin.CURRENT,
            ),
        )

        val snapshot = AdsConfigSnapshot.create(version = 1, configs = input)
        input.clear()
        mutableItems += mutableItems.single().copy(adunit = "later")

        assertEquals(1, snapshot.configs.size)
        assertEquals(1, snapshot.adsConfig(key)?.listAds?.size)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.configs as MutableMap<ConfigKey, ResolvedConfig>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.adsConfig(key)?.listAds as MutableList<OriginalAdItem>).clear()
        }
    }

    @Test
    fun hashUsesCanonicalContentAndIgnoresVersion() {
        val first = AdsConfigSnapshot.create(
            version = 1,
            configs = mapOf(key to resolved(validCanonical("unit"))),
        )
        val sameContentNewVersion = AdsConfigSnapshot.create(
            version = 2,
            configs = mapOf(key to resolved(validCanonical("unit"))),
        )
        val changed = AdsConfigSnapshot.create(
            version = 2,
            configs = mapOf(key to resolved(validCanonical("changed"))),
        )

        assertEquals(first.contentHash, sameContentNewVersion.contentHash)
        assertNotEquals(first.contentHash, changed.contentHash)
    }

    private fun resolved(canonicalJson: String): ResolvedConfig = ResolvedConfig(
        value = AdsConfigValue(
            OriginalAdsConfig(
                enable = true,
                listAds = listOf(
                    OriginalAdItem(
                        enableAd = true,
                        weight = 100,
                        adunit = "unit",
                        sourceListIndex = 0,
                    ),
                ),
            ),
        ),
        canonicalJson = canonicalJson,
        origin = ConfigValueOrigin.CURRENT,
    )

    private fun validCanonical(adunit: String): String =
        """{"enable":true,"list_ads":[{"adunit":"$adunit","enable_ad":true,"weight":100}]}"""
}
