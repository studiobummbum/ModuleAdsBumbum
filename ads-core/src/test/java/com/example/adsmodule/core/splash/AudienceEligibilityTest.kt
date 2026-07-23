package com.example.adsmodule.core.splash

import com.example.adsmodule.core.AudienceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudienceEligibilityTest {
    @Test
    fun paid_isAlwaysEligible() {
        assertTrue(AudienceEligibility.isEligible(AudienceType.PAID, isOrganic = false))
        assertTrue(AudienceEligibility.isEligible(AudienceType.PAID, isOrganic = true))
        assertTrue(AudienceEligibility.isEligible(AudienceType.PAID, isOrganic = null))
    }

    @Test
    fun organic_requiresIsOrganicTrue() {
        assertTrue(AudienceEligibility.isEligible(AudienceType.ORGANIC, isOrganic = true))
        assertFalse(AudienceEligibility.isEligible(AudienceType.ORGANIC, isOrganic = false))
        assertFalse(AudienceEligibility.isEligible(AudienceType.ORGANIC, isOrganic = null))
    }

    @Test
    fun unknown_isFailClosed() {
        assertFalse(AudienceEligibility.isEligible(AudienceType.UNKNOWN, isOrganic = true))
        assertFalse(AudienceEligibility.isEligible(AudienceType.UNKNOWN, isOrganic = false))
    }
}
