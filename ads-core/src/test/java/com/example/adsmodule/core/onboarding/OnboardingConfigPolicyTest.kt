package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.OnboardAdsConfig
import com.example.adsmodule.core.config.OnboardAdsEntry
import com.example.adsmodule.core.config.OnboardScreenConfig
import com.example.adsmodule.core.config.OnboardScreenEntry
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingConfigPolicyTest {
    @Test
    fun defaultAssets_allScreensActive_paidAudience() {
        val policy = OnboardingConfigPolicy.resolve(bundledLikeSnapshot(), AudienceType.PAID)
        assertEquals(listOf(1, 2, 3, 4), policy.activePages)
        assertTrue(policy.isAdsEnabled(1))
        assertTrue(policy.isAdsEnabled(2))
        assertEquals(OnboardingScreenInstances.page1, policy.screenInstanceId(1))
    }

    @Test
    fun organicAudience_disablesAdsWithIsOrganicFalse() {
        val policy = OnboardingConfigPolicy.resolve(bundledLikeSnapshot(), AudienceType.ORGANIC)
        assertEquals(listOf(1, 2, 3, 4), policy.activePages)
        assertTrue(policy.isAdsEnabled(1))
        assertFalse(policy.isAdsEnabled(2))
        assertFalse(policy.isAdsEnabled(3))
        assertTrue(policy.isAdsEnabled(4))
    }

    @Test
    fun screenOff_removesPagerButKeepsLogicalNumbers() {
        val snapshot = snapshot(
            screens = listOf(
                OnboardScreenEntry(screenOnboard1 = true, isOrganic = true),
                OnboardScreenEntry(screenOnboard2 = true, isOrganic = true),
                OnboardScreenEntry(screenOnboard3 = false, isOrganic = true),
                OnboardScreenEntry(screenOnboard4 = true, isOrganic = true),
            ),
            ads = defaultAds(),
        )
        val policy = OnboardingConfigPolicy.resolve(snapshot, AudienceType.PAID)
        assertEquals(listOf(1, 2, 4), policy.activePages)
        assertFalse(policy.isScreenEnabled(3))
        assertFalse(policy.isAdsEnabled(3))
        assertTrue(policy.isAdsEnabled(4))
        assertEquals(OnboardingScreenInstances.page4, policy.page(4).screenInstanceId)
    }

    @Test
    fun adsOff_keepsPagerVisible() {
        val snapshot = snapshot(
            screens = defaultScreens(),
            ads = listOf(
                OnboardAdsEntry(adsOnboard1 = true, isOrganic = true),
                OnboardAdsEntry(adsOnboard2 = false, isOrganic = true),
                OnboardAdsEntry(adsOnboard3 = true, isOrganic = true),
                OnboardAdsEntry(adsOnboard4 = true, isOrganic = true),
            ),
        )
        val policy = OnboardingConfigPolicy.resolve(snapshot, AudienceType.PAID)
        assertEquals(listOf(1, 2, 3, 4), policy.activePages)
        assertTrue(policy.isScreenEnabled(2))
        assertFalse(policy.isAdsEnabled(2))
    }

    private fun bundledLikeSnapshot(): AdsConfigSnapshot = snapshot(
        screens = defaultScreens(),
        ads = defaultAds(),
    )

    private fun defaultScreens(): List<OnboardScreenEntry> = listOf(
        OnboardScreenEntry(screenOnboard1 = true, isOrganic = true),
        OnboardScreenEntry(screenOnboard2 = true, isOrganic = true),
        OnboardScreenEntry(screenOnboard3 = true, isOrganic = true),
        OnboardScreenEntry(screenOnboard4 = true, isOrganic = true),
    )

    private fun defaultAds(): List<OnboardAdsEntry> = listOf(
        OnboardAdsEntry(adsOnboard1 = true, isOrganic = true),
        OnboardAdsEntry(adsOnboard2 = true, isOrganic = false),
        OnboardAdsEntry(adsOnboard3 = true, isOrganic = false),
        OnboardAdsEntry(adsOnboard4 = true, isOrganic = true),
    )

    private fun snapshot(
        screens: List<OnboardScreenEntry>,
        ads: List<OnboardAdsEntry>,
    ): AdsConfigSnapshot {
        val native = OriginalAdsConfig(
            enable = true,
            isOrganic = true,
            listAds = listOf(
                OriginalAdItem(enableAd = true, weight = 100, adunit = "onb-100"),
            ),
        )
        return AdsConfigSnapshot.create(
            version = 1L,
            configs = mapOf(
                OnboardingConfigKeys.NATIVE to ResolvedConfig(
                    value = AdsConfigValue(native),
                    canonicalJson = """{"enable":true}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                ConfigKey("onboard_screen_config") to ResolvedConfig(
                    value = OnboardScreenConfig(screens),
                    canonicalJson = """[]""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                ConfigKey("onboard_ads_config") to ResolvedConfig(
                    value = OnboardAdsConfig(ads),
                    canonicalJson = """[]""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
            ),
        )
    }
}
