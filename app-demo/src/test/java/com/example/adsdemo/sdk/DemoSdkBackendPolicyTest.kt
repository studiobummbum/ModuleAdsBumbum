package com.example.adsdemo.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoSdkBackendPolicyTest {
    @Test
    fun release_forcesAdMob() {
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
    fun debug_defaultsToAdMobTest() {
        assertEquals(
            DemoSdkBackend.AdMobTest,
            DemoSdkBackendPolicy.effective(debuggable = true, stored = null),
        )
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.effective(debuggable = true, stored = DemoSdkBackend.AdMob),
        )
    }

    @Test
    fun sanitizeWrite_releaseForcesAdMob() {
        assertEquals(
            DemoSdkBackend.AdMob,
            DemoSdkBackendPolicy.sanitizeWrite(
                debuggable = false,
                requested = DemoSdkBackend.AdMobTest,
            ),
        )
    }

    @Test
    fun fromStorage_mapsLegacyFakeToAdMobTest() {
        assertEquals(DemoSdkBackend.AdMobTest, DemoSdkBackend.fromStorage("Fake"))
        assertEquals(DemoSdkBackend.AdMobTest, DemoSdkBackend.fromStorage(null))
        assertEquals(DemoSdkBackend.AdMob, DemoSdkBackend.fromStorage("AdMob"))
    }
}
