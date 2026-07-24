package com.example.adsdemo.sdk

/**
 * Demo SDK backend selection. Persisted; applied at process start.
 */
enum class DemoSdkBackend {
    Fake,
    AdMobTest,
    ;

    companion object {
        fun fromStorage(value: String?): DemoSdkBackend = when (value) {
            Fake.name -> Fake
            AdMobTest.name -> AdMobTest
            else -> AdMobTest
        }
    }
}
