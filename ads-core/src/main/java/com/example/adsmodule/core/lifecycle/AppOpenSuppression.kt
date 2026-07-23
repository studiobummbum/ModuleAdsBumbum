package com.example.adsmodule.core.lifecycle

/**
 * Pure App Open Resume suppression evaluator.
 *
 * Splash-active suppresses resume App Open only; Splash-owned App Open still goes through
 * the global fullscreen lock separately.
 */
public object AppOpenSuppression {
    public fun evaluate(input: AppOpenSuppressionInput): AppOpenSuppressionResult {
        val reasons = buildList {
            if (input.fullscreenLockBusy) {
                add(AppOpenSuppressionReason.FULLSCREEN_LOCK_BUSY)
            }
            if (input.splashActive) {
                add(AppOpenSuppressionReason.SPLASH_ACTIVE)
            }
            if (input.hasValidClickToken) {
                add(AppOpenSuppressionReason.CLICK_TOKEN_PRESENT)
            }
            if (input.turnbackPending) {
                add(AppOpenSuppressionReason.TURNBACK_PENDING)
            }
            if (!input.activityValid) {
                add(AppOpenSuppressionReason.ACTIVITY_INVALID)
            }
        }
        return AppOpenSuppressionResult(
            suppressed = reasons.isNotEmpty(),
            reasons = reasons,
        )
    }

    public fun canShow(input: AppOpenSuppressionInput): Boolean = !evaluate(input).suppressed
}

public data class AppOpenSuppressionInput(
    val fullscreenLockBusy: Boolean,
    val splashActive: Boolean,
    val hasValidClickToken: Boolean,
    val turnbackPending: Boolean,
    val activityValid: Boolean,
)

public enum class AppOpenSuppressionReason {
    FULLSCREEN_LOCK_BUSY,
    SPLASH_ACTIVE,
    CLICK_TOKEN_PRESENT,
    TURNBACK_PENDING,
    ACTIVITY_INVALID,
}

public data class AppOpenSuppressionResult(
    val suppressed: Boolean,
    val reasons: List<AppOpenSuppressionReason>,
)
