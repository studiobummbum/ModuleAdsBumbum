package com.example.adsdemo.language

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguageStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LanguageFlowActivityTest {
    @Test
    fun languageLoading_emitsSelectEffectOnceAfterTimer() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        val intent = Intent(app, LanguageLoadingActivity::class.java)
        ActivityScenario.launch<LanguageLoadingActivity>(intent).use {
            Thread.sleep(2_400)
            val snap = app.graph.languageCoordinator.snapshot.value
            assertNotNull(snap)
            assertTrue(
                snap!!.stage == LanguageStage.LANGUAGE_SELECT ||
                    snap.pendingEffect == LanguageNavigationEffect.OPEN_LANGUAGE_SELECT ||
                    (
                        snap.stage == LanguageStage.LANGUAGE_LOADING &&
                            snap.loadingTimer.completed
                        ),
            )
            if (snap.pendingEffect == LanguageNavigationEffect.OPEN_LANGUAGE_SELECT) {
                assertTrue(
                    app.graph.languageCoordinator.claimEffect(
                        snap.sessionId,
                        LanguageNavigationEffect.OPEN_LANGUAGE_SELECT,
                    ),
                )
                assertTrue(
                    !app.graph.languageCoordinator.claimEffect(
                        snap.sessionId,
                        LanguageNavigationEffect.OPEN_LANGUAGE_SELECT,
                    ),
                )
            }
        }
    }

    @Test
    fun languageSelect_recreation_keepsSession() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        val intent = Intent(app, LanguageActivity::class.java)
        ActivityScenario.launch<LanguageActivity>(intent).use { scenario ->
            Thread.sleep(500)
            val before = app.graph.languageCoordinator.snapshot.value?.sessionId
            assertNotNull(before)
            scenario.recreate()
            Thread.sleep(400)
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById(R.id.language_list))
            }
            val after = app.graph.languageCoordinator.snapshot.value?.sessionId
            assertEquals(before, after)
        }
    }
}
