package com.example.adsdemo.debug

import com.example.adsdemo.sdk.DemoSdkBackend
import com.example.adsdemo.sdk.DemoSdkBackendPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Release classpath policy: always AdMob (RC / publisher units as-is).
 */
class ReleaseSdkBackendPolicyTest {
    @Test
    fun release_forcesAdMob_regardlessOfStoredPreference() {
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = DemoSdkBackend.AdMobTest),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = DemoSdkBackend.AdMob),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = null),
        )
    }
}
