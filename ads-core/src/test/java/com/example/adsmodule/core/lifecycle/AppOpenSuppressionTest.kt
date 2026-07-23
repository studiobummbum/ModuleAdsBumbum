package com.example.adsmodule.core.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenSuppressionTest {
    @Test
    fun fullscreenLockBusy_suppresses() {
        val result = AppOpenSuppression.evaluate(clear().copy(fullscreenLockBusy = true))
        assertTrue(result.suppressed)
        assertEquals(listOf(AppOpenSuppressionReason.FULLSCREEN_LOCK_BUSY), result.reasons)
    }

    @Test
    fun splashActive_suppressesResumeAppOpen() {
        val result = AppOpenSuppression.evaluate(clear().copy(splashActive = true))
        assertTrue(result.suppressed)
        assertEquals(listOf(AppOpenSuppressionReason.SPLASH_ACTIVE), result.reasons)
    }

    @Test
    fun clickToken_suppresses() {
        val result = AppOpenSuppression.evaluate(clear().copy(hasValidClickToken = true))
        assertTrue(result.suppressed)
        assertEquals(listOf(AppOpenSuppressionReason.CLICK_TOKEN_PRESENT), result.reasons)
    }

    @Test
    fun turnbackPending_suppresses() {
        val result = AppOpenSuppression.evaluate(clear().copy(turnbackPending = true))
        assertTrue(result.suppressed)
        assertEquals(listOf(AppOpenSuppressionReason.TURNBACK_PENDING), result.reasons)
    }

    @Test
    fun activityInvalid_suppresses() {
        val result = AppOpenSuppression.evaluate(clear().copy(activityValid = false))
        assertTrue(result.suppressed)
        assertEquals(listOf(AppOpenSuppressionReason.ACTIVITY_INVALID), result.reasons)
    }

    @Test
    fun allConditionsClear_allowsAppOpen() {
        val result = AppOpenSuppression.evaluate(clear())
        assertFalse(result.suppressed)
        assertTrue(result.reasons.isEmpty())
        assertTrue(AppOpenSuppression.canShow(clear()))
    }

    @Test
    fun combinedReasons_areStableOrdered() {
        val result = AppOpenSuppression.evaluate(
            AppOpenSuppressionInput(
                fullscreenLockBusy = true,
                splashActive = true,
                hasValidClickToken = true,
                turnbackPending = true,
                activityValid = false,
            ),
        )
        assertEquals(
            listOf(
                AppOpenSuppressionReason.FULLSCREEN_LOCK_BUSY,
                AppOpenSuppressionReason.SPLASH_ACTIVE,
                AppOpenSuppressionReason.CLICK_TOKEN_PRESENT,
                AppOpenSuppressionReason.TURNBACK_PENDING,
                AppOpenSuppressionReason.ACTIVITY_INVALID,
            ),
            result.reasons,
        )
    }

    private fun clear(): AppOpenSuppressionInput = AppOpenSuppressionInput(
        fullscreenLockBusy = false,
        splashActive = false,
        hasValidClickToken = false,
        turnbackPending = false,
        activityValid = true,
    )
}
