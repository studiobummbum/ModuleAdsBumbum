package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.OnboardAdsConfig
import com.example.adsmodule.core.config.OnboardAdsEntry
import com.example.adsmodule.core.config.OnboardScreenConfig
import com.example.adsmodule.core.config.OnboardScreenEntry
import com.example.adsmodule.core.splash.AudienceEligibility
import com.example.adsmodule.core.storage.OnboardingScreenInstances

/**
 * Resolves per-page screen and ad eligibility from original Remote Config.
 *
 * - [onboard_screen_config]: page OFF removes the pager from the adapter.
 * - [onboard_ads_config]: ads OFF keeps the pager but skips Native load/bind.
 * - Screen-OFF pages never load ads.
 */
public object OnboardingConfigPolicy {
    public fun resolve(
        snapshot: AdsConfigSnapshot,
        audience: AudienceType,
    ): OnboardingPagePolicy {
        val screenConfig = snapshot.onboardScreenConfig()
        val adsConfig = snapshot.onboardAdsConfig()
        val nativeConfig = snapshot.adsConfig(OnboardingConfigKeys.NATIVE)
        val nativeAdsEligible = nativeConfig != null &&
            nativeConfig.enable &&
            AudienceEligibility.isEligible(audience, nativeConfig.isOrganic)

        val pages = OnboardingPages.ALL.map { page ->
            val screenFlag = resolveScreenFlag(screenConfig, page)
            val adsFlag = resolveAdsFlag(adsConfig, page)
            val screenEnabled = screenFlag.enabled &&
                AudienceEligibility.isEligible(audience, screenFlag.isOrganic)
            val pageAdsEnabled = adsFlag.enabled &&
                AudienceEligibility.isEligible(audience, adsFlag.isOrganic)
            OnboardingPageModel(
                logicalPage = page,
                screenInstanceId = OnboardingScreenInstances.page(page),
                screenEnabled = screenEnabled,
                adsEnabled = screenEnabled && pageAdsEnabled && nativeAdsEligible,
            )
        }
        val active = pages.filter { it.isActive }
        require(active.isNotEmpty()) {
            "At least one onboarding screen must remain enabled"
        }
        return OnboardingPagePolicy(pages = pages)
    }

    public fun resolveOrDefault(
        snapshot: AdsConfigSnapshot?,
        audience: AudienceType,
    ): OnboardingPagePolicy {
        if (snapshot == null) {
            return defaultAllEnabled()
        }
        return resolve(snapshot, audience)
    }

    public fun defaultAllEnabled(): OnboardingPagePolicy =
        OnboardingPagePolicy(
            pages = OnboardingPages.ALL.map { page ->
                OnboardingPageModel(
                    logicalPage = page,
                    screenInstanceId = OnboardingScreenInstances.page(page),
                    screenEnabled = true,
                    adsEnabled = true,
                )
            },
        )

    private data class Flag(
        val enabled: Boolean,
        val isOrganic: Boolean,
    )

    private fun resolveScreenFlag(
        config: OnboardScreenConfig?,
        page: Int,
    ): Flag {
        if (config == null) return Flag(enabled = true, isOrganic = true)
        val entry = config.entries.getOrNull(page - 1)
            ?: return Flag(enabled = true, isOrganic = true)
        return Flag(enabled = screenEnabled(entry, page), isOrganic = entry.isOrganic)
    }

    private fun resolveAdsFlag(
        config: OnboardAdsConfig?,
        page: Int,
    ): Flag {
        if (config == null) return Flag(enabled = true, isOrganic = true)
        val entry = config.entries.getOrNull(page - 1)
            ?: return Flag(enabled = true, isOrganic = true)
        return Flag(enabled = adsEnabled(entry, page), isOrganic = entry.isOrganic)
    }

    private fun screenEnabled(entry: OnboardScreenEntry, page: Int): Boolean =
        when (page) {
            1 -> entry.screenOnboard1
            2 -> entry.screenOnboard2
            3 -> entry.screenOnboard3
            4 -> entry.screenOnboard4
            else -> null
        } ?: false

    private fun adsEnabled(entry: OnboardAdsEntry, page: Int): Boolean =
        when (page) {
            1 -> entry.adsOnboard1
            2 -> entry.adsOnboard2
            3 -> entry.adsOnboard3
            4 -> entry.adsOnboard4
            else -> null
        } ?: false
}
