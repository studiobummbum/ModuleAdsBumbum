package com.example.adsdemo.debug

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Release classpath must exclude debug-only entry points (debugImplementation / debug source set).
 */
class ReleaseDebugExclusionTest {
    @Test
    fun releaseClasspath_excludesDebugEntryPoints() {
        val installer = runCatching {
            Class.forName("com.example.adsdemo.debug.DebugNavInstaller")
        }.isSuccess
        val dashboard = runCatching {
            Class.forName("com.example.adsmodule.debug.AdsDebugDashboardActivity")
        }.isSuccess
        assertFalse(installer)
        assertFalse(dashboard)
    }

    @Test
    fun mainActivity_hasNoDebugNamedMethods() {
        val mainActivity = Class.forName("com.example.adsdemo.MainActivity")
        val hasDebugMethod = mainActivity.declaredMethods.any { method ->
            method.name.contains("Debug", ignoreCase = true)
        }
        assertFalse(hasDebugMethod)
    }
}
