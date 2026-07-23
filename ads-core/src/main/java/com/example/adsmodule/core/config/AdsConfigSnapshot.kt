package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.OriginalAdsConfig
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import kotlinx.serialization.json.JsonPrimitive

public enum class ConfigValueOrigin {
    CURRENT,
    LAST_KNOWN_GOOD,
    BUNDLED,
}

public data class ResolvedConfig(
    val value: OriginalConfigValue,
    val canonicalJson: String,
    val origin: ConfigValueOrigin,
    val warnings: List<ConfigValidationIssue> = emptyList(),
)

public class AdsConfigSnapshot private constructor(
    public val version: Long,
    public val contentHash: String,
    private val resolvedConfigs: Map<ConfigKey, ResolvedConfig>,
) {
    public val configs: Map<ConfigKey, ResolvedConfig>
        get() = resolvedConfigs

    public operator fun get(key: ConfigKey): ResolvedConfig? = resolvedConfigs[key]

    public fun adsConfig(key: ConfigKey): OriginalAdsConfig? =
        (resolvedConfigs[key]?.value as? AdsConfigValue)?.config

    public companion object {
        public fun create(
            version: Long,
            configs: Map<ConfigKey, ResolvedConfig>,
        ): AdsConfigSnapshot {
            require(version > 0) { "Snapshot version must be positive" }
            val frozen = configs.entries.associateTo(LinkedHashMap()) { (key, resolved) ->
                key to resolved.freeze()
            }.toUnmodifiableMap()
            return AdsConfigSnapshot(
                version = version,
                contentHash = calculateHash(frozen),
                resolvedConfigs = frozen,
            )
        }

        private fun calculateHash(configs: Map<ConfigKey, ResolvedConfig>): String {
            val canonicalSnapshot = configs.entries
                .sortedBy { it.key.value }
                .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                    "${JsonPrimitive(key.value)}:${value.canonicalJson}"
                }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalSnapshot.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }
}

private fun ResolvedConfig.freeze(): ResolvedConfig = copy(
    value = value.freeze(),
    warnings = warnings.toUnmodifiableList(),
)

private fun OriginalConfigValue.freeze(): OriginalConfigValue = when (this) {
    is AdsConfigValue -> copy(
        config = config.copy(
            listAds = config.listAds.toUnmodifiableList(),
        ),
    )
    is OnboardAdsConfig -> copy(entries = entries.toUnmodifiableList())
    is OnboardScreenConfig -> copy(entries = entries.toUnmodifiableList())
    is BooleanConfigValue,
    is FullScreenTimingConfig,
    is SplashScreenConfig,
    is SplashSkipConfig,
    is TurnbackConfig,
    -> this
}

private fun <T> Collection<T>.toUnmodifiableList(): List<T> =
    Collections.unmodifiableList(ArrayList(this))

private fun <K, V> Map<K, V>.toUnmodifiableMap(): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(this))
