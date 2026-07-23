package com.example.adsmodule.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    @Transient
    val sourceListIndex: Int = -1,
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
    @SerialName("collapsible")
    val collapsible: String? = null,
    @SerialName("refresh_time")
    val refreshTime: String? = null,
    @SerialName("interval")
    val intervalMillis: Long? = null,
    @SerialName("list_ads")
    val listAds: List<OriginalAdItem>,
)
