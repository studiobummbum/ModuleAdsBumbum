package com.example.adsmodule.core

import com.example.adsmodule.sdk.AdsSdkModule

/**
 * Minimal bootstrap status. Ads behavior is introduced in later phases.
 */
public object AdsCoreStatus {
    public val description: String = "ads-core ready with ${AdsSdkModule.NAME}"
}
