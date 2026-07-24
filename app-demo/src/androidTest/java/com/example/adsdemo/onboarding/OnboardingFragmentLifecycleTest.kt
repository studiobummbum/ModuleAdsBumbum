package com.example.adsdemo.onboarding

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.adsdemo.AdsDemoApplication
import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.onboarding.OnboardingConfigKeys
import com.example.adsmodule.core.onboarding.OnboardingConfigPolicy
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Drop any leftover bind from earlier instrumentation cases.
        app.graph.onboardingAds.unbindPage(1)
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
        // Seed READY inventory so this case does not depend on AdMob network / Fake
        // timing (default demo backend is AdMob Test).
        val seeded = seedReadyOnboardingNative(app, page = 1)
        assertTrue(seeded is PutResult.Accepted)

        val bound = app.graph.onboardingAds.bindPage(1)
        assertTrue(
            "expected Bound, got $bound",
            bound is NormalScreenBindResult.Bound,
        )
        assertEquals(
            OnboardingScreenInstances.page1,
            app.graph.onboardingAds.boundAd(1)?.session?.screenInstanceId,
        )
        app.graph.onboardingAds.unbindPage(1)
        assertNull(app.graph.onboardingAds.boundAd(1))
    }

    private fun seedReadyOnboardingNative(
        app: AdsDemoApplication,
        page: Int,
    ): PutResult {
        val objectId = ObjectId("instr-onb-${SEQ.incrementAndGet()}")
        return app.graph.storage.putReady(
            StoredAd(
                objectId = objectId,
                sourceConfigKey = OnboardingConfigKeys.NATIVE,
                sourceListIndex = 0,
                sourceType = AdFormat.NATIVE,
                sourceAdunit = "instrumentation-native",
                sourceWeight = 100,
                screenInstanceId = OnboardingScreenInstances.page(page),
                loadedAt = System.currentTimeMillis(),
                state = AdSlotState.READY,
                sdkHandle = NoOpHandle,
            ),
        )
    }

    private object NoOpHandle : SdkLoadedAdHandle {
        override val format: AdFormat = AdFormat.NATIVE
        override val adUnit: String = "instrumentation-native"

        override fun destroy() = Unit
    }

    private companion object {
        val SEQ = AtomicLong(0L)
    }
}
