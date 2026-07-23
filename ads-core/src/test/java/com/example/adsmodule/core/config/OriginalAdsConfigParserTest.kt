package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalAdsConfigParserTest {
    private val parser = OriginalAdsConfigParser()
    private val splashDescriptor =
        ConfigKeyRegistry.requireDescriptor(ConfigKey("inter_splash_config_1"))

    @Test
    fun mixedSplashTypesStayInOneListAndRetainOriginalIndexes() {
        val result = parser.parse(
            splashDescriptor,
            """
            {
              "enable": true,
              "isOrganic": true,
              "timeout_total": 30000,
              "list_ads": [
                {"enable_ad":true,"weight":120,"timeout":1000,"type":"inter","adunit":"unit-0"},
                {"enable_ad":true,"weight":110,"timeout":1000,"type":"appopen","adunit":"unit-1"},
                {"enable_ad":true,"weight":100,"timeout":1000,"type":"native","adunit":"unit-2"}
              ]
            }
            """.trimIndent(),
        ) as OriginalConfigParseResult.Success

        val items = (result.value as AdsConfigValue).config.listAds
        assertEquals(listOf("inter", "appopen", "native"), items.map { it.type })
        assertEquals(listOf(0, 1, 2), items.map { it.sourceListIndex })
    }

    @Test
    fun malformedJsonReturnsTypedFailure() {
        val result = parser.parse(
            splashDescriptor,
            """{"enable":true,"list_ads":[}""",
        )

        assertTrue(result is OriginalConfigParseResult.Failure)
        assertTrue(result.issues.any { it.code == ConfigIssueCode.INVALID_JSON })
    }

    @Test
    fun canonicalJsonIgnoresWhitespaceAndObjectKeyOrderButKeepsArrayOrder() {
        val first = parser.parse(
            splashDescriptor,
            """{"enable":true,"list_ads":[{"enable_ad":true,"weight":2,"adunit":"a"},{"enable_ad":true,"weight":1,"adunit":"b"}]}""",
        ) as OriginalConfigParseResult.Success
        val reordered = parser.parse(
            splashDescriptor,
            """
            {
              "list_ads": [
                {"adunit":"a","weight":2,"enable_ad":true},
                {"weight":1,"enable_ad":true,"adunit":"b"}
              ],
              "enable": true
            }
            """.trimIndent(),
        ) as OriginalConfigParseResult.Success
        val reversedArray = parser.parse(
            splashDescriptor,
            """{"enable":true,"list_ads":[{"enable_ad":true,"weight":1,"adunit":"b"},{"enable_ad":true,"weight":2,"adunit":"a"}]}""",
        ) as OriginalConfigParseResult.Success

        assertEquals(first.canonicalJson, reordered.canonicalJson)
        assertTrue(first.canonicalJson != reversedArray.canonicalJson)
    }

    @Test
    fun auxiliaryArrayAndBooleanValuesUseTheirOriginalRootShapes() {
        val onboardResult = parser.parse(
            ConfigKeyRegistry.requireDescriptor(ConfigKey("onboard_ads_config")),
            bundledRaw(ConfigKey("onboard_ads_config")),
        ) as OriginalConfigParseResult.Success
        val booleanResult = parser.parse(
            ConfigKeyRegistry.requireDescriptor(ConfigKey("enable_ads_app")),
            "true",
        ) as OriginalConfigParseResult.Success

        assertEquals(4, (onboardResult.value as OnboardAdsConfig).entries.size)
        assertTrue((booleanResult.value as BooleanConfigValue).value)
    }

    @Test
    fun curlyQuoteJson_failsWithInvalidJson() {
        val result = parser.parse(splashDescriptor, FIXTURE_CURLY_QUOTE_JSON)
        assertTrue(result is OriginalConfigParseResult.Failure)
        assertTrue(result.issues.any { it.code == ConfigIssueCode.INVALID_JSON })
    }

    @Test
    fun missingCommaJson_failsWithInvalidJson() {
        val result = parser.parse(splashDescriptor, FIXTURE_MISSING_COMMA_JSON)
        assertTrue(result is OriginalConfigParseResult.Failure)
        assertTrue(result.issues.any { it.code == ConfigIssueCode.INVALID_JSON })
    }

    @Test
    fun mixedInterAppopenNativeFixture_keepsSingleListAds() {
        val result = parser.parse(
            splashDescriptor,
            FIXTURE_MIXED_INTER_APPOPEN_NATIVE_JSON,
        ) as OriginalConfigParseResult.Success
        val items = (result.value as AdsConfigValue).config.listAds
        assertEquals(listOf("inter", "appopen", "native"), items.map { it.type })
        assertEquals(listOf(100, 90, 80), items.map { it.weight })
    }
}
