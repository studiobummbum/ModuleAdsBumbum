package com.example.adsdemo.sdk

/**
 * Resolves which [DemoSdkBackend] is allowed for the current build.
 * Release always uses AdMob (RC units as-is — publisher units when provisioned).
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
        if (!debuggable) return DemoSdkBackend.AdMob
        return requested
    }
}
