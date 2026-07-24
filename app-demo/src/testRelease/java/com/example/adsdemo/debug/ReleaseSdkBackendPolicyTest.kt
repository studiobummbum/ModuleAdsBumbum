package com.example.adsdemo.debug

import com.example.adsdemo.sdk.DemoSdkBackend
import com.example.adsdemo.sdk.DemoSdkBackendPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Release classpath policy: Fake is never the effective backend.
 */
class ReleaseSdkBackendPolicyTest {
    @Test
    fun release_effectiveBackend_isAlwaysAdMob() {
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = DemoSdkBackend.Fake),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = DemoSdkBackend.AdMobTest),
        )
    }
}
