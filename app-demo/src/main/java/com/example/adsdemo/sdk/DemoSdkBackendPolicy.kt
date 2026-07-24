package com.example.adsdemo.sdk

/**
 * Resolves which [DemoSdkBackend] is allowed for the current build.
 * Release never runs Fake; always AdMob (RC units as-is).
 */
object DemoSdkBackendPolicy {
    fun effective(
        debuggable: Boolean,
        stored: DemoSdkBackend?,
    ): DemoSdkBackend {
        if (!debuggable) return DemoSdkBackend.AdMob
        return stored ?: DemoSdkBackend.AdMobTest
    }

    fun sanitizeWrite(
        debuggable: Boolean,
        requested: DemoSdkBackend,
    ): DemoSdkBackend? {
        if (!debuggable) {
            return if (requested == DemoSdkBackend.Fake) null else DemoSdkBackend.AdMob
        }
        return requested
    }
}
