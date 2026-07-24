package com.example.adsdemo.onboarding

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.viewpager2.widget.ViewPager2
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsmodule.core.onboarding.OnboardingConfigPolicy
import com.example.adsmodule.core.onboarding.OnboardingFullResult
import com.example.adsmodule.core.onboarding.OnboardingNavigationEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class OnboardingActivityTest {
    @Before
    fun resetOnboarding() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        app.graph.onboardingCoordinator.startOrRestore(
            OnboardingConfigPolicy.defaultAllEnabled(),
        )
    }

    @Test
    fun pagerCount_isFour_withDefaultConfig() {
        launchOnboarding().use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.onboarding_pager)
                assertEquals(4, pager.adapter?.itemCount)
                assertNotNull(activity.findViewById(R.id.onboarding_next_link))
            }
        }
    }

    @Test
    fun nextFromPage1_movesToPage2() {
        launchOnboarding().use { scenario ->
            clickNext(scenario)
            scenario.onActivity { activity ->
                val app = activity.application as AdsDemoApplication
                assertEquals(2, app.graph.onboardingCoordinator.snapshot.value?.currentLogicalPage)
            }
        }
    }

    @Test
    fun nextFromPage2_keepsPager2_andCreatesFullPending() {
        launchOnboarding().use { scenario ->
            clickNext(scenario)
            clickNext(scenario)
            scenario.onActivity { activity ->
                val app = activity.application as AdsDemoApplication
                val snap = app.graph.onboardingCoordinator.snapshot.value
                assertNotNull(snap)
                assertEquals(2, snap!!.currentLogicalPage)
                assertNotNull(snap.pendingFull)
                assertEquals(1, snap.pendingFull!!.fullIndex)
                assertEquals(3, snap.pendingTargetLogicalPage)
            }
        }
    }

    @Test
    fun full1Result_restoresPager3() {
        launchOnboarding().use { scenario ->
            clickNext(scenario)
            clickNext(scenario)
            scenario.onActivity { activity ->
                val app = activity.application as AdsDemoApplication
                val snap = app.graph.onboardingCoordinator.snapshot.value!!
                val pending = snap.pendingFull!!
                // Clear launch effect if Activity already claimed it.
                app.graph.onboardingCoordinator.claimEffect(
                    snap.sessionId,
                    OnboardingNavigationEffect.OPEN_FULL1,
                )
                assertTrue(
                    app.graph.onboardingCoordinator.onFullResult(
                        OnboardingFullResult(
                            sessionId = snap.sessionId,
                            fullSessionId = pending.fullSessionId,
                            fullIndex = 1,
                            targetLogicalPage = pending.targetLogicalPage,
                        ),
                    ),
                )
            }
            scenario.onActivity { activity ->
                val app = activity.application as AdsDemoApplication
                assertEquals(3, app.graph.onboardingCoordinator.snapshot.value?.currentLogicalPage)
            }
        }
    }

    @Test
    fun recreation_keepsCurrentPager() {
        launchOnboarding().use { scenario ->
            clickNext(scenario)
            scenario.recreate()
            scenario.onActivity { activity ->
                val app = activity.application as AdsDemoApplication
                assertEquals(2, app.graph.onboardingCoordinator.snapshot.value?.currentLogicalPage)
                assertTrue(
                    app.graph.onboardingCoordinator.snapshot.value!!.activePages.size in 1..4,
                )
                assertNotNull(activity.findViewById(R.id.onboarding_pager))
            }
        }
    }

    private fun launchOnboarding(): ActivityScenario<OnboardingActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            OnboardingActivity::class.java,
        )
        return ActivityScenario.launch(intent)
    }

    private fun clickNext(scenario: ActivityScenario<OnboardingActivity>) {
        scenario.onActivity { activity ->
            activity.forwardFromFragment()
        }
        Thread.sleep(100)
    }
}
