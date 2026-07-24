package com.example.adsdemo.sdk

/**
 * Demo SDK backend selection. Persisted; applied at process start.
 *
 * Debug defaults to [AdMobTest] (Google sample / test ad units).
 * Release builds always use [AdMob] (Remote Config `adunit` as-is — swap to publisher units there).
 */
enum class DemoSdkBackend {
    /** AdMob with official Google test unit remapping. */
    AdMobTest,

    /**
     * AdMob using Remote Config `adunit` as-is ([com.example.adsmodule.admob.AdMobRuntimeMode.PRODUCTION]).
     * Replace bundled sample units with publisher units for production.
     */
    AdMob,
    ;

    companion object {
        fun fromStorage(value: String?): DemoSdkBackend = when (value) {
            AdMob.name -> AdMob
            AdMobTest.name -> AdMobTest
            // Legacy Fake preference → AdMob Test.
            "Fake" -> AdMobTest
            else -> AdMobTest
        }
    }
}
