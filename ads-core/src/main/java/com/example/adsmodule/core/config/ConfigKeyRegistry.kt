package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey

public enum class ConfigValueKind {
    ADS,
    FULL_SCREEN_TIMING,
    SPLASH_SCREEN,
    ONBOARD_ADS,
    ONBOARD_SCREEN,
    SPLASH_SKIP,
    TURNBACK,
    BOOLEAN,
}

public data class ConfigDescriptor(
    val key: ConfigKey,
    val kind: ConfigValueKind,
    val assetFileName: String = "${key.value}.json",
)

public object ConfigKeyRegistry {
    public val descriptors: List<ConfigDescriptor> = listOf(
        ads("inter_splash_config_1"),
        ads("native_splash_full_config_1"),
        ads("inter_onboarding_config_1"),
        ads("appopen_resume_config_1"),
        ads("native_splash_config_1"),
        ads("native_language_loading_config_1"),
        ads("native_language_config_1"),
        ads("native_language_dup_config_1"),
        ads("native_onboarding_config_1"),
        ads("native_onb_full_config_1"),
        ads("native_onb_full_2_config_1"),
        ads("banner_ufo_config_1"),
        ads("banner_home_config_1"),
        ads("inter_all_config_1"),
        descriptor("native_splash_full_config_2", ConfigValueKind.FULL_SCREEN_TIMING),
        descriptor("native_onb_full_1_config_2", ConfigValueKind.FULL_SCREEN_TIMING),
        descriptor("native_onb_full_2_config_2", ConfigValueKind.FULL_SCREEN_TIMING),
        descriptor("splash_screen_config", ConfigValueKind.SPLASH_SCREEN),
        descriptor("onboard_ads_config", ConfigValueKind.ONBOARD_ADS),
        descriptor("onboard_screen_config", ConfigValueKind.ONBOARD_SCREEN),
        descriptor("splash_skip_ads", ConfigValueKind.SPLASH_SKIP),
        descriptor("turnback_ads", ConfigValueKind.TURNBACK),
        descriptor("reopen_language", ConfigValueKind.BOOLEAN),
        descriptor("enable_ads_app", ConfigValueKind.BOOLEAN),
    )

    private val descriptorsByKey: Map<ConfigKey, ConfigDescriptor> =
        descriptors.associateBy(ConfigDescriptor::key)

    init {
        require(descriptorsByKey.size == descriptors.size) {
            "ConfigKeyRegistry contains duplicate keys"
        }
    }

    public val keys: Set<ConfigKey> = descriptorsByKey.keys

    public fun descriptor(key: ConfigKey): ConfigDescriptor? = descriptorsByKey[key]

    public fun requireDescriptor(key: ConfigKey): ConfigDescriptor =
        requireNotNull(descriptor(key)) { "Unknown Remote Config key: ${key.value}" }

    private fun ads(key: String): ConfigDescriptor = descriptor(key, ConfigValueKind.ADS)

    private fun descriptor(
        key: String,
        kind: ConfigValueKind,
    ): ConfigDescriptor = ConfigDescriptor(
        key = ConfigKey(key),
        kind = kind,
    )
}
