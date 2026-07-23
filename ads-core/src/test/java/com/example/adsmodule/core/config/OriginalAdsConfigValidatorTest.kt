package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalAdsConfigValidatorTest {
    private val parser = OriginalAdsConfigParser()
    private val adDescriptor =
        ConfigKeyRegistry.requireDescriptor(ConfigKey("inter_splash_config_1"))

    @Test
    fun missingStringNullDecimalOverflowAndNegativeWeightsAreErrors() {
        val weightFields = listOf(
            "",
            """"weight":"100",""",
            """"weight":null,""",
            """"weight":1.5,""",
            """"weight":2147483648,""",
            """"weight":-1,""",
        )

        weightFields.forEach { weightField ->
            val result = parser.parse(
                adDescriptor,
                """{"enable":true,"list_ads":[{"enable_ad":true,$weightField"adunit":"unit"}]}""",
            )

            assertTrue("Expected failure for $weightField", result is OriginalConfigParseResult.Failure)
            assertTrue(result.issues.any { it.path == "$.list_ads[0].weight" })
            assertEquals(0, result.issues.first { it.path == "$.list_ads[0].weight" }.sourceListIndex)
        }
    }

    @Test
    fun listAndRequiredItemFieldsKeepTheirOriginalTypes() {
        val invalidValues = listOf(
            """{"enable":true,"list_ads":{}}""",
            """{"enable":true,"list_ads":[{"enable_ad":"true","weight":1,"adunit":"unit"}]}""",
            """{"enable":true,"list_ads":[{"enable_ad":true,"weight":1,"adunit":null}]}""",
        )

        invalidValues.forEach { raw ->
            assertTrue(parser.parse(adDescriptor, raw) is OriginalConfigParseResult.Failure)
        }
    }

    @Test
    fun itemTypeOnlyAllowsInterAppopenOrNative() {
        val result = parser.parse(
            adDescriptor,
            """{"enable":true,"list_ads":[{"enable_ad":true,"weight":1,"type":"banner","adunit":"unit"}]}""",
        )

        assertTrue(result is OriginalConfigParseResult.Failure)
        assertTrue(result.issues.any { it.code == ConfigIssueCode.INVALID_AD_TYPE })
    }

    @Test
    fun declaredPhaseTimingFieldsRejectNegativeValues() {
        val invalidCases = listOf(
            adDescriptor to
                """{"enable":true,"list_ads":[{"enable_ad":true,"weight":1,"timeout":-1,"adunit":"unit"}]}""",
            ConfigKeyRegistry.requireDescriptor(ConfigKey("splash_skip_ads")) to
                """{"enable":true,"isOrganic":false,"time_skip":-1}""",
            ConfigKeyRegistry.requireDescriptor(ConfigKey("native_splash_full_config_2")) to
                """{"time_delay_X_button":-1,"auto_skip":3000}""",
            ConfigKeyRegistry.requireDescriptor(ConfigKey("native_onb_full_1_config_2")) to
                """{"time_delay_X_button":2000,"auto_skip":-1}""",
        )

        invalidCases.forEach { (descriptor, raw) ->
            val result = parser.parse(descriptor, raw)
            assertTrue(result is OriginalConfigParseResult.Failure)
            assertTrue(result.issues.any { it.code == ConfigIssueCode.INVALID_RANGE })
        }
    }

    @Test
    fun replacementSchemaAndPlacementWeightAreForbidden() {
        val aliases = listOf(
            """"schema_version":1,""",
            """"candidates":[],""",
            """"candidate_sets":[],""",
            """"ad_unit_id":"renamed",""",
            """"weight":10,""",
        )

        aliases.forEach { alias ->
            val result = parser.parse(
                adDescriptor,
                """{"enable":true,$alias"list_ads":[{"enable_ad":true,"weight":1,"adunit":"unit"}]}""",
            )
            assertTrue(result is OriginalConfigParseResult.Failure)
            assertTrue(result.issues.any { it.code == ConfigIssueCode.FORBIDDEN_FIELD })
        }
    }

    @Test
    fun placeholderAndDuplicateWeightProduceWarningsWithoutFallbackErrors() {
        val result = parser.parse(
            adDescriptor,
            """
            {
              "enable": true,
              "list_ads": [
                {"enable_ad":true,"weight":100,"adunit":"{{PLACEHOLDER_0}}"},
                {"enable_ad":true,"weight":100,"adunit":""}
              ]
            }
            """.trimIndent(),
        )

        assertTrue(result is OriginalConfigParseResult.Success)
        assertFalse(result.issues.any { it.severity == ConfigIssueSeverity.ERROR })
        assertEquals(
            2,
            result.issues.count { it.code == ConfigIssueCode.PLACEHOLDER_ADUNIT },
        )
        assertTrue(result.issues.any { it.code == ConfigIssueCode.DUPLICATE_WEIGHT })
    }
}
