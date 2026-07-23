package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledConfigDataSourceTest {
    private val parser = OriginalAdsConfigParser()

    @Test
    fun allTwentyFourBundledValuesExistAndParse() = runBlocking {
        val source = bundledDataSource()
        val files = requireNotNull(bundledAssetRoot().listFiles())
            .filter { it.extension == "json" }

        assertEquals(24, files.size)
        ConfigKeyRegistry.descriptors.forEach { descriptor ->
            val raw = source.read(descriptor.key)
            assertNotNull("Missing bundled ${descriptor.key.value}", raw)
            val result = parser.parse(descriptor, requireNotNull(raw))
            assertTrue(
                "${descriptor.key.value} failed: ${result.issues}",
                result is OriginalConfigParseResult.Success,
            )
        }
    }

    @Test
    fun everyBundledAdConfigKeepsOneOriginalListAndItemWeights() = runBlocking {
        val source = bundledDataSource()

        ConfigKeyRegistry.descriptors
            .filter { it.kind == ConfigValueKind.ADS }
            .forEach { descriptor ->
                val result = parser.parse(descriptor, requireNotNull(source.read(descriptor.key)))
                    as OriginalConfigParseResult.Success
                val config = (result.value as AdsConfigValue).config

                assertEquals(2, config.listAds.size)
                assertEquals(listOf(0, 1), config.listAds.map { it.sourceListIndex })
                assertEquals(listOf(100, 90), config.listAds.map { it.weight })
                assertTrue(result.canonicalJson.contains("\"list_ads\""))
                assertTrue(result.issues.any { it.code == ConfigIssueCode.PLACEHOLDER_ADUNIT })
            }
    }

    @Test
    fun splashConfigsRemainSeparateRegistryEntries() {
        val splashKeys = listOf(
            "inter_splash_config_1",
            "native_splash_config_1",
            "banner_ufo_config_1",
        )

        splashKeys.forEach { key ->
            assertNotNull(ConfigKeyRegistry.descriptor(ConfigKey(key)))
        }
        assertEquals(3, splashKeys.toSet().size)
    }
}
