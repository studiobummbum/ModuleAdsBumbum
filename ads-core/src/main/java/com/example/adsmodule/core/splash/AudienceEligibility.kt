package com.example.adsmodule.core.splash

import com.example.adsmodule.core.AudienceType

/**
 * Signed-off Organic / Paid / Unknown policy for all ads placements and
 * auxiliary flags (`splash_skip_ads`, turnback, onboarding page ads, etc.).
 *
 * Applies uniformly — no per-placement overrides in this module:
 * - [AudienceType.PAID]: always eligible when the config/feature is otherwise enabled
 *   (`isOrganic` is ignored for PAID).
 * - [AudienceType.ORGANIC]: eligible only when Remote Config `isOrganic == true`.
 * - [AudienceType.UNKNOWN]: fail-closed (never eligible) until attribution resolves.
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
