package com.example.adsmodule.core.splash

import com.example.adsmodule.core.AudienceType

/**
 * Audience eligibility for original Remote Config `isOrganic` flags.
 *
 * - PAID: eligible when the config/feature is enabled
 * - ORGANIC: eligible only when `isOrganic == true`
 * - UNKNOWN: fail-closed
 */
public object AudienceEligibility {
    public fun isEligible(
        audience: AudienceType,
        isOrganic: Boolean?,
    ): Boolean = when (audience) {
        AudienceType.PAID -> true
        AudienceType.ORGANIC -> isOrganic == true
        AudienceType.UNKNOWN -> false
    }
}
