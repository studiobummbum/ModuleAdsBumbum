package com.example.adsmodule.core.lifecycle

/**
 * Classification for process background transitions.
 *
 * - [AD_CLICK]: user left the app after an ad click while a valid click token exists
 * - [USER_BACKGROUND]: ordinary Home / user background without turnback
 * - [SYSTEM_INTERRUPTION]: phone call, permission dialog, or similar system overlay
 * - [UNKNOWN]: uncorrelated or unclassified transition
 */
public enum class BackgroundReason {
    AD_CLICK,
    USER_BACKGROUND,
    SYSTEM_INTERRUPTION,
    UNKNOWN,
}
