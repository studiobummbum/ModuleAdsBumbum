package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigKeyRegistryTest {
    @Test
    fun registry_containsExactlyTheOriginalTwentyFourKeys() {
        val descriptors = ConfigKeyRegistry.descriptors

        assertEquals(24, descriptors.size)
        assertEquals(24, descriptors.map { it.key }.toSet().size)
        assertEquals(14, descriptors.count { it.kind == ConfigValueKind.ADS })
        assertEquals(2, descriptors.count { it.kind == ConfigValueKind.BOOLEAN })
        assertEquals(8, descriptors.count { it.kind !in setOf(ConfigValueKind.ADS, ConfigValueKind.BOOLEAN) })
        assertNotNull(ConfigKeyRegistry.descriptor(ConfigKey("native_onb_full_config_1")))
        assertNotNull(ConfigKeyRegistry.descriptor(ConfigKey("native_onb_full_2_config_1")))
    }

    @Test
    fun registry_neverIntroducesReplacementSchemaKeys() {
        val registeredNames = ConfigKeyRegistry.keys.map { it.value }

        assertTrue(registeredNames.none { it in FORBIDDEN_NAMES })
    }

    private companion object {
        private val FORBIDDEN_NAMES: Set<String> =
            setOf("schema_version", "candidates", "candidate_sets", "ad_unit_id")
    }
}
