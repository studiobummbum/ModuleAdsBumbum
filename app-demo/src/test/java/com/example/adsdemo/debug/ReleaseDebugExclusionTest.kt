package com.example.adsdemo.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDebugExclusionTest {
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

    @Test
    fun mainActivity_hasNoDebugNamedMethods() {
        val mainActivity = Class.forName("com.example.adsdemo.MainActivity")
        val hasDebugMethod = mainActivity.declaredMethods.any { method ->
            method.name.contains("Debug", ignoreCase = true)
        }
        assertFalse(hasDebugMethod)
    }
}
