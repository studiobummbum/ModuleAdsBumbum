package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.storage.StoredAdView

public object OnboardingFinishKeys {
    public val INTER_ONBOARDING: ConfigKey = ConfigKey("inter_onboarding_config_1")
}

public sealed class OnboardingFinishResult {
    public data class InterShownThenHome(
        val showRequestId: ShowRequestId,
        val objectId: ObjectId,
        val storedAd: StoredAdView,
    ) : OnboardingFinishResult()

    public data class HomeFallback(
        val reason: String,
    ) : OnboardingFinishResult()
}
