package com.example.adsmodule.core.config

import com.example.adsmodule.core.OriginalAdsConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

public sealed interface OriginalConfigValue

public data class AdsConfigValue(
    val config: OriginalAdsConfig,
) : OriginalConfigValue

@Serializable
public data class FullScreenTimingConfig(
    @SerialName("time_delay_X_button")
    val timeDelayXButtonMillis: Long,
    @SerialName("auto_skip")
    val autoSkipMillis: Long,
) : OriginalConfigValue

@Serializable
public data class SplashScreenConfig(
    @SerialName("show_LFO")
    val showLfo: Boolean,
    @SerialName("show_position")
    val showPosition: String,
    @SerialName("skipped")
    val skipped: Boolean,
    @SerialName("timeout_screen")
    val timeoutScreenMillis: Long,
) : OriginalConfigValue

@Serializable
public data class OnboardAdsEntry(
    @SerialName("ads_onboard1")
    val adsOnboard1: Boolean? = null,
    @SerialName("ads_onboard2")
    val adsOnboard2: Boolean? = null,
    @SerialName("ads_onboard3")
    val adsOnboard3: Boolean? = null,
    @SerialName("ads_onboard4")
    val adsOnboard4: Boolean? = null,
    @SerialName("isOrganic")
    val isOrganic: Boolean,
)

public data class OnboardAdsConfig(
    val entries: List<OnboardAdsEntry>,
) : OriginalConfigValue

@Serializable
public data class OnboardScreenEntry(
    @SerialName("screen_onboard1")
    val screenOnboard1: Boolean? = null,
    @SerialName("screen_onboard2")
    val screenOnboard2: Boolean? = null,
    @SerialName("screen_onboard3")
    val screenOnboard3: Boolean? = null,
    @SerialName("screen_onboard4")
    val screenOnboard4: Boolean? = null,
    @SerialName("isOrganic")
    val isOrganic: Boolean,
)

public data class OnboardScreenConfig(
    val entries: List<OnboardScreenEntry>,
) : OriginalConfigValue

@Serializable
public data class SplashSkipConfig(
    @SerialName("enable")
    val enable: Boolean,
    @SerialName("isOrganic")
    val isOrganic: Boolean,
    @SerialName("time_skip")
    val timeSkipMillis: Long,
) : OriginalConfigValue

@Serializable
public data class TurnbackConfig(
    @SerialName("enable")
    val enable: Boolean,
    @SerialName("isOrganic")
    val isOrganic: Boolean,
    @SerialName("time_turnback")
    val timeTurnbackMillis: Long,
    @SerialName("time_delay")
    val timeDelayMillis: Long,
) : OriginalConfigValue

public data class BooleanConfigValue(
    val value: Boolean,
) : OriginalConfigValue
