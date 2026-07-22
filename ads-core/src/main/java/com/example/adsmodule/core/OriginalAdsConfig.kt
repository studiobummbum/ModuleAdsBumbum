package com.example.adsmodule.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public enum class AudienceType {
    PAID,
    ORGANIC,
    UNKNOWN,
}

@Serializable
public data class OriginalAdItem(
    @SerialName("enable_ad")
    val enableAd: Boolean,
    @SerialName("weight")
    val weight: Int,
    @SerialName("timeout")
    val timeoutMillis: Long? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("adunit")
    val adunit: String,
)

@Serializable
public data class OriginalAdsConfig(
    @SerialName("enable")
    val enable: Boolean,
    @SerialName("isOrganic")
    val isOrganic: Boolean? = null,
    @SerialName("timeout_total")
    val timeoutTotalMillis: Long? = null,
    @SerialName("type_layout")
    val typeLayout: String? = null,
    @SerialName("list_ads")
    val listAds: List<OriginalAdItem> = emptyList(),
)

public data class ConfigValidationError(
    val sourceListIndex: Int,
    val field: String,
    val message: String,
)

/**
 * Validates domain rules after Remote Config has been decoded.
 */
public class OriginalAdsConfigValidator {
    public fun validate(config: OriginalAdsConfig): List<ConfigValidationError> =
        config.listAds.mapIndexedNotNull { index, item ->
            if (item.weight < 0) {
                ConfigValidationError(
                    sourceListIndex = index,
                    field = "weight",
                    message = "list_ads[$index].weight must not be negative",
                )
            } else {
                null
            }
        }

    public fun requireValid(config: OriginalAdsConfig) {
        val errors = validate(config)
        require(errors.isEmpty()) {
            errors.joinToString(separator = "; ") { it.message }
        }
    }
}
