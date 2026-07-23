package com.example.adsmodule.core.storage

import com.example.adsmodule.core.ScreenInstanceId

/**
 * Four distinct onboarding pager screen instances sharing `native_onboarding_config_1`.
 */
public object OnboardingScreenInstances {
    public val page1: ScreenInstanceId = ScreenInstanceId("onboarding-page-1")
    public val page2: ScreenInstanceId = ScreenInstanceId("onboarding-page-2")
    public val page3: ScreenInstanceId = ScreenInstanceId("onboarding-page-3")
    public val page4: ScreenInstanceId = ScreenInstanceId("onboarding-page-4")

    public val all: List<ScreenInstanceId> = listOf(page1, page2, page3, page4)

    public fun page(index: Int): ScreenInstanceId {
        require(index in 1..4) { "Onboarding page index must be 1..4, was $index" }
        return all[index - 1]
    }
}
