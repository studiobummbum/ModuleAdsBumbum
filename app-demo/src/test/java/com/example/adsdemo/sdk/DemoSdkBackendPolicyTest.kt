package com.example.adsdemo.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoSdkBackendPolicyTest {
    @Test
    fun release_forcesAdMob_evenIfFakeStored() {
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = DemoSdkBackend.Fake),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = DemoSdkBackend.AdMobTest),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = false, stored = null),
        )
    }

    @Test
    fun debug_defaultsToAdMobTest_andAllowsFake() {
        assertEquals(
            DemoSdkBackend.AdMobTest,
            DemoSdkBackendPolicy.effective(debuggable = true, stored = null),
        )
        assertEquals(
            DemoSdkBackend.Fake,
            DemoSdkBackendPolicy.effective(debuggable = true, stored = DemoSdkBackend.Fake),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = true, stored = DemoSdkBackend.AdMob),
        )
    }

    @Test
    fun sanitizeWrite_releaseRejectsFake() {
        assertNull(
            DemoSdkBackendPolicy.sanitizeWrite(debuggable = false, requested = DemoSdkBackend.Fake),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.sanitizeWrite(
                debuggable = false,
                requested = DemoSdkBackend.AdMobTest,
            ),
        )
    }
}
