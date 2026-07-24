package com.example.adsmodule.admob

import com.example.adsmodule.sdk.AdFormat

public enum class AdMobRuntimeMode {
    /**
     * Remap every load request to official Google test ad units.
     * Used by the demo "AdMob Test" selector.
     */
    TEST,

    /**
     * Use the Remote Config ad unit as-is (production or otherwise).
     */
    PRODUCTION,
}

/**
 * Resolves the ad unit that will be sent to Google Mobile Ads.
 *
 * In [AdMobRuntimeMode.TEST], always returns an official Google sample unit so
 * smoke tests never hit production inventory.
 */
public class AdMobAdUnitResolver(
    private val mode: AdMobRuntimeMode,
) {
    public fun resolve(format: AdFormat, requestedAdUnit: String): ResolvedAdUnit {
        require(requestedAdUnit.isNotBlank()) { "requestedAdUnit must not be blank" }
        return when (mode) {
            AdMobRuntimeMode.TEST -> ResolvedAdUnit(
                adUnit = AdMobTestAdUnits.forFormat(format),
                remappedFrom = requestedAdUnit.takeUnless {
                    AdMobTestAdUnits.isOfficialTestUnit(it)
                },
                usedTestUnit = true,
            )
            AdMobRuntimeMode.PRODUCTION -> ResolvedAdUnit(
                adUnit = requestedAdUnit,
                remappedFrom = null,
                usedTestUnit = AdMobTestAdUnits.isOfficialTestUnit(requestedAdUnit),
            )
        }
    }
}

public data class ResolvedAdUnit(
    val adUnit: String,
    val remappedFrom: String?,
    val usedTestUnit: Boolean,
)
