package com.example.adsdemo.sdk

/**
 * Demo SDK backend selection. Persisted; applied at process start.
 *
 * Release builds never run [Fake] — [DemoSdkBackendStore] forces [AdMob].
 */
enum class DemoSdkBackend {
    /** Deterministic Fake adapters (debug only). */
    Fake,

    /** AdMob with official Google test unit remapping. */
    AdMobTest,

    /**
     * AdMob using Remote Config `adunit` as-is ([com.example.adsmodule.admob.AdMobRuntimeMode.PRODUCTION]).
     * Bundled demo RC uses Google sample units until real inventory is provisioned.
     */
    AdMob,
    ;

    companion object {
        fun fromStorage(value: String?): DemoSdkBackend = when (value) {
            Fake.name -> Fake
            AdMobTest.name -> AdMobTest
            AdMob.name -> AdMob
            else -> AdMobTest
        }
    }
}
