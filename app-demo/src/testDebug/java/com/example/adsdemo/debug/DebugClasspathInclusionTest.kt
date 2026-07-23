package com.example.adsdemo.debug

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Debug classpath must include debug-only entry points.
 */
class DebugClasspathInclusionTest {
    @Test
    fun debugClasspath_includesDebugEntryPoints() {
        val installer = runCatching {
            Class.forName("com.example.adsdemo.debug.DebugNavInstaller")
        }.isSuccess
        val dashboard = runCatching {
            Class.forName("com.example.adsmodule.debug.AdsDebugDashboardActivity")
        }.isSuccess
        assertTrue(installer)
        assertTrue(dashboard)
    }
}
