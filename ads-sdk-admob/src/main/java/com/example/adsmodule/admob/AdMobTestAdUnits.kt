package com.example.adsmodule.admob

import com.example.adsmodule.sdk.AdFormat

/**
 * Official Google sample / test ad units for demo and AdMob Test mode.
 *
 * Production ad units must come from Remote Config and are never hardcoded here.
 */
public object AdMobTestAdUnits {
    public const val SAMPLE_APPLICATION_ID: String = "ca-app-pub-3940256097505524~3347511713"

    public const val INTERSTITIAL: String = "ca-app-pub-3940256097505524/1033173712"
    public const val APP_OPEN: String = "ca-app-pub-3940256097505524/9257395921"
    public const val NATIVE: String = "ca-app-pub-3940256097505524/2247696110"
    public const val BANNER: String = "ca-app-pub-3940256097505524/6300978111"
    public const val NATIVE_FULLSCREEN: String = NATIVE

    public fun forFormat(format: AdFormat): String = when (format) {
        AdFormat.INTERSTITIAL -> INTERSTITIAL
        AdFormat.APP_OPEN -> APP_OPEN
        AdFormat.NATIVE -> NATIVE
        AdFormat.BANNER -> BANNER
        AdFormat.NATIVE_FULLSCREEN -> NATIVE_FULLSCREEN
    }

    public fun isOfficialTestUnit(adUnit: String): Boolean =
        adUnit == INTERSTITIAL ||
            adUnit == APP_OPEN ||
            adUnit == NATIVE ||
            adUnit == BANNER

    public fun isOfficialTestApplicationId(applicationId: String): Boolean =
        applicationId == SAMPLE_APPLICATION_ID
}
