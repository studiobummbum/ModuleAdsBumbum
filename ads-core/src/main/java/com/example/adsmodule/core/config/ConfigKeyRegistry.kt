package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.sdk.AdFormat

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
    /**
     * Fixed/default SDK format for ads configs when `list_ads[].type` is absent.
     * Non-ads configs keep this null.
     */
    val defaultAdFormat: AdFormat? = null,
    /**
     * Format used when an item declares `type=native`.
     * For mixed fullscreen configs this is [AdFormat.NATIVE_FULLSCREEN].
     */
    val nativeAdFormat: AdFormat? = null,
) {
    init {
        if (kind == ConfigValueKind.ADS) {
            requireNotNull(defaultAdFormat) {
                "ADS config ${key.value} must declare defaultAdFormat"
            }
            requireNotNull(nativeAdFormat) {
                "ADS config ${key.value} must declare nativeAdFormat"
            }
            require(
                nativeAdFormat == AdFormat.NATIVE ||
                    nativeAdFormat == AdFormat.NATIVE_FULLSCREEN,
            ) {
                "nativeAdFormat for ${key.value} must be NATIVE or NATIVE_FULLSCREEN"
            }
        } else {
            require(defaultAdFormat == null && nativeAdFormat == null) {
                "Non-ADS config ${key.value} must not declare ad formats"
            }
        }
    }
}

public object ConfigKeyRegistry {
    public val descriptors: List<ConfigDescriptor> = listOf(
        ads(
            key = "inter_splash_config_1",
            defaultAdFormat = AdFormat.INTERSTITIAL,
            nativeAdFormat = AdFormat.NATIVE_FULLSCREEN,
        ),
        ads(
            key = "native_splash_full_config_1",
            defaultAdFormat = AdFormat.NATIVE_FULLSCREEN,
            nativeAdFormat = AdFormat.NATIVE_FULLSCREEN,
        ),
        ads(
            key = "inter_onboarding_config_1",
            defaultAdFormat = AdFormat.INTERSTITIAL,
            nativeAdFormat = AdFormat.NATIVE_FULLSCREEN,
        ),
        ads(
            key = "appopen_resume_config_1",
            defaultAdFormat = AdFormat.APP_OPEN,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "native_splash_config_1",
            defaultAdFormat = AdFormat.NATIVE,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "native_language_loading_config_1",
            defaultAdFormat = AdFormat.NATIVE,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "native_language_config_1",
            defaultAdFormat = AdFormat.NATIVE,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "native_language_dup_config_1",
            defaultAdFormat = AdFormat.NATIVE,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "native_onboarding_config_1",
            defaultAdFormat = AdFormat.NATIVE,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "native_onb_full_config_1",
            defaultAdFormat = AdFormat.NATIVE_FULLSCREEN,
            nativeAdFormat = AdFormat.NATIVE_FULLSCREEN,
        ),
        ads(
            key = "native_onb_full_2_config_1",
            defaultAdFormat = AdFormat.NATIVE_FULLSCREEN,
            nativeAdFormat = AdFormat.NATIVE_FULLSCREEN,
        ),
        ads(
            key = "banner_ufo_config_1",
            defaultAdFormat = AdFormat.BANNER,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "banner_home_config_1",
            defaultAdFormat = AdFormat.BANNER,
            nativeAdFormat = AdFormat.NATIVE,
        ),
        ads(
            key = "inter_all_config_1",
            defaultAdFormat = AdFormat.INTERSTITIAL,
            nativeAdFormat = AdFormat.NATIVE_FULLSCREEN,
        ),
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

    /**
     * Resolves the SDK format for one `list_ads` item.
     *
     * - missing `type` → descriptor [ConfigDescriptor.defaultAdFormat]
     * - `inter` → [AdFormat.INTERSTITIAL]
     * - `appopen` → [AdFormat.APP_OPEN]
     * - `native` → descriptor [ConfigDescriptor.nativeAdFormat]
     */
    public fun resolveAdFormat(
        key: ConfigKey,
        itemType: String?,
    ): AdFormat {
        val descriptor = requireDescriptor(key)
        require(descriptor.kind == ConfigValueKind.ADS) {
            "Config ${key.value} is not an ads config"
        }
        val defaultFormat = requireNotNull(descriptor.defaultAdFormat)
        val nativeFormat = requireNotNull(descriptor.nativeAdFormat)
        return when (itemType) {
            null -> defaultFormat
            "inter" -> AdFormat.INTERSTITIAL
            "appopen" -> AdFormat.APP_OPEN
            "native" -> nativeFormat
            else -> error("Unsupported list_ads type '$itemType' for ${key.value}")
        }
    }

    private fun ads(
        key: String,
        defaultAdFormat: AdFormat,
        nativeAdFormat: AdFormat,
    ): ConfigDescriptor = ConfigDescriptor(
        key = ConfigKey(key),
        kind = ConfigValueKind.ADS,
        defaultAdFormat = defaultAdFormat,
        nativeAdFormat = nativeAdFormat,
    )

    private fun descriptor(
        key: String,
        kind: ConfigValueKind,
    ): ConfigDescriptor = ConfigDescriptor(
        key = ConfigKey(key),
        kind = kind,
    )
}
