package com.example.adsdemo.onboarding

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.adsdemo.AdsDemoApplication
import com.example.adsmodule.core.onboarding.OnboardingConfigPolicy
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class OnboardingFragmentLifecycleTest {
    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        app.graph.onboardingCoordinator.startOrRestore(
            OnboardingConfigPolicy.defaultAllEnabled(),
        )
        app.graph.onboardingAds.refreshPolicy()
    }

    @Test
    fun fragmentArgs_mapToDistinctScreenIds() {
        assertNotEquals(
            OnboardingScreenInstances.page1,
            OnboardingScreenInstances.page2,
        )
        val fragment = OnboardingFragment.newInstance(3)
        assertEquals(3, fragment.arguments?.getInt("logical_page"))
        assertEquals("ONBOARD_NATIVE#3", OnboardingScreenInstances.page(3).value)
    }

    @Test
    fun destroyView_releasesBoundAd() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        app.graph.onboardingAds.preloadEligible(listOf(1))
        app.graph.onboardingAds.bindPage(1)
        assertEquals(
            OnboardingScreenInstances.page1,
            app.graph.onboardingAds.boundAd(1)?.session?.screenInstanceId,
        )
        app.graph.onboardingAds.unbindPage(1)
        assertNull(app.graph.onboardingAds.boundAd(1))
    }
}
