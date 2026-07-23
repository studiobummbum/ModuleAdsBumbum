package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.sdk.AdFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun adsDescriptors_exposeFixedAndNativeFormats() {
        val splash = ConfigKeyRegistry.requireDescriptor(ConfigKey("inter_splash_config_1"))
        val language = ConfigKeyRegistry.requireDescriptor(ConfigKey("native_language_config_1"))
        val banner = ConfigKeyRegistry.requireDescriptor(ConfigKey("banner_home_config_1"))
        val skip = ConfigKeyRegistry.requireDescriptor(ConfigKey("splash_skip_ads"))

        assertEquals(AdFormat.INTERSTITIAL, splash.defaultAdFormat)
        assertEquals(AdFormat.NATIVE_FULLSCREEN, splash.nativeAdFormat)
        assertEquals(AdFormat.NATIVE, language.defaultAdFormat)
        assertEquals(AdFormat.NATIVE, language.nativeAdFormat)
        assertEquals(AdFormat.BANNER, banner.defaultAdFormat)
        assertNull(skip.defaultAdFormat)
    }

    @Test
    fun resolveAdFormat_usesItemTypeAndKeySpecificNativeMapping() {
        val splash = ConfigKey("inter_splash_config_1")
        val onboarding = ConfigKey("inter_onboarding_config_1")
        val all = ConfigKey("inter_all_config_1")
        val nativeFull = ConfigKey("native_onb_full_config_1")
        val appOpen = ConfigKey("appopen_resume_config_1")

        assertEquals(AdFormat.INTERSTITIAL, ConfigKeyRegistry.resolveAdFormat(splash, null))
        assertEquals(AdFormat.INTERSTITIAL, ConfigKeyRegistry.resolveAdFormat(splash, "inter"))
        assertEquals(AdFormat.APP_OPEN, ConfigKeyRegistry.resolveAdFormat(splash, "appopen"))
        assertEquals(
            AdFormat.NATIVE_FULLSCREEN,
            ConfigKeyRegistry.resolveAdFormat(splash, "native"),
        )
        assertEquals(
            AdFormat.NATIVE_FULLSCREEN,
            ConfigKeyRegistry.resolveAdFormat(onboarding, "native"),
        )
        assertEquals(
            AdFormat.NATIVE_FULLSCREEN,
            ConfigKeyRegistry.resolveAdFormat(all, "native"),
        )
        assertEquals(
            AdFormat.NATIVE_FULLSCREEN,
            ConfigKeyRegistry.resolveAdFormat(nativeFull, null),
        )
        assertEquals(AdFormat.APP_OPEN, ConfigKeyRegistry.resolveAdFormat(appOpen, null))
        assertEquals(
            AdFormat.NATIVE,
            ConfigKeyRegistry.resolveAdFormat(ConfigKey("native_splash_config_1"), null),
        )
        assertEquals(
            AdFormat.BANNER,
            ConfigKeyRegistry.resolveAdFormat(ConfigKey("banner_ufo_config_1"), null),
        )
    }

    private companion object {
        private val FORBIDDEN_NAMES: Set<String> =
            setOf("schema_version", "candidates", "candidate_sets", "ad_unit_id")
    }
}
