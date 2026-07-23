package com.example.adsmodule.core.analytics

/**
 * High-level analytics categories for ads module telemetry.
 */
public enum class AdsEventCategory {
    CONFIG,
    LOAD,
    STATE,
    STORAGE,
    REFILL,
    SHOW,
    LIFECYCLE,
    NAVIGATION,
    SPLASH_SKIP,
    FULL_EXIT,
    ;

    public val wireName: String
        get() = when (this) {
            CONFIG -> "config"
            LOAD -> "load"
            STATE -> "state"
            STORAGE -> "storage"
            REFILL -> "refill"
            SHOW -> "show"
            LIFECYCLE -> "lifecycle"
            NAVIGATION -> "navigation"
            SPLASH_SKIP -> "splash skip"
            FULL_EXIT -> "full exit"
        }

    public companion object {
        public fun fromWireName(value: String): AdsEventCategory? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.wireName == normalized || it.name.equals(normalized, ignoreCase = true) }
        }
    }
}
