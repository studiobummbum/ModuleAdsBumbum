package com.example.adsdemo.onboarding

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.onboarding.full.FullExitSource
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class OnboardingFullActivityTest {
    @Before
    fun preloadFullAds() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        app.graph.onboardingFullCoordinator.ensurePreloaded(1)
        app.graph.onboardingFullCoordinator.ensurePreloaded(2)
        Thread.sleep(1_000)
    }

    @Test
    fun full1_swipe_finishesWithSwipeSource_andTarget3() {
        val fullSession = "full-session-1-${System.nanoTime()}"
        launchFull(1, target = 3, fullSessionId = fullSession).use { scenario ->
            Thread.sleep(400)
            scenario.onActivity { activity ->
                dispatchForwardSwipe(activity, excluded = false)
            }
            Thread.sleep(500)
            val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
            val snap = app.graph.onboardingFullCoordinator.snapshot.value
            assertNotNull(snap)
            assertEquals(FullExitSource.SWIPE_FORWARD, snap!!.winningExitSource)
            assertEquals(3, snap.targetLogicalPage)
        }
    }

    @Test
    fun full2_swipe_targetsPager4() {
        val fullSession = "full-session-2-${System.nanoTime()}"
        launchFull(2, target = 4, fullSessionId = fullSession).use { scenario ->
            Thread.sleep(400)
            scenario.onActivity { activity ->
                dispatchForwardSwipe(activity, excluded = false)
            }
            Thread.sleep(500)
            val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
            val snap = app.graph.onboardingFullCoordinator.snapshot.value
            assertEquals(FullExitSource.SWIPE_FORWARD, snap!!.winningExitSource)
            assertEquals(4, snap.targetLogicalPage)
        }
    }

    @Test
    fun closeX_beforeDelay_doesNotFinish() {
        launchFull(1, target = 3).use { scenario ->
            Thread.sleep(100)
            scenario.onActivity { activity ->
                val close = activity.findViewById<ImageButton>(R.id.onboarding_full_close)
                assertEquals(View.INVISIBLE, close.visibility)
                close.isEnabled = true
                close.visibility = View.VISIBLE
                close.performClick()
            }
            Thread.sleep(200)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun closeX_afterDelay_exitsWithCloseSource() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        val session = OnboardingSessionId("test-onb-x")
        val fullSession = FullSessionId("test-full-x-${System.nanoTime()}")
        app.graph.onboardingFullCoordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = session,
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        Thread.sleep(2_200)
        assertTrue(app.graph.onboardingFullCoordinator.snapshot.value!!.closeVisible)
        assertTrue(app.graph.onboardingFullCoordinator.onCloseClicked(fullSession))
        val result = app.graph.onboardingFullCoordinator.consumeExitResult(fullSession)
        assertNotNull(result)
        assertEquals(FullExitSource.CLOSE_X, result!!.exitSource)
        assertEquals(3, result.targetLogicalPage)
    }

    @Test
    fun shortSwipe_doesNotExit() {
        launchFull(1, target = 3).use { scenario ->
            Thread.sleep(200)
            scenario.onActivity { activity ->
                dispatchForwardSwipe(activity, excluded = false, distancePx = 24f)
            }
            Thread.sleep(200)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
            val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
            assertEquals(null, app.graph.onboardingFullCoordinator.snapshot.value?.winningExitSource)
        }
    }

    @Test
    fun mediaGesture_doesNotExit() {
        launchFull(1, target = 3).use { scenario ->
            Thread.sleep(400)
            scenario.onActivity { activity ->
                val media = activity.findViewById<View>(R.id.fake_native_full_media)
                val loc = IntArray(2)
                media.getLocationOnScreen(loc)
                dispatchForwardSwipe(
                    activity,
                    excluded = false,
                    startX = loc[0] + media.width / 2f,
                    startY = loc[1] + media.height / 2f,
                )
            }
            Thread.sleep(300)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
            val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
            assertEquals(null, app.graph.onboardingFullCoordinator.snapshot.value?.winningExitSource)
        }
    }

    @Test
    fun systemBack_beforeCloseDelay_doesNotExit() {
        launchFull(1, target = 3).use { scenario ->
            Thread.sleep(200)
            scenario.onActivity { activity ->
                (activity as OnboardingFullActivity).onBackPressedDispatcher.onBackPressed()
            }
            Thread.sleep(200)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun systemBack_afterCloseDelay_exitsLikeCloseX() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        val session = OnboardingSessionId("test-onb-back")
        val fullSession = FullSessionId("test-full-back-${System.nanoTime()}")
        app.graph.onboardingFullCoordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = session,
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        Thread.sleep(2_200)
        assertTrue(app.graph.onboardingFullCoordinator.snapshot.value!!.closeVisible)
        assertTrue(app.graph.onboardingFullCoordinator.onSystemBack(fullSession))
        val result = app.graph.onboardingFullCoordinator.consumeExitResult(fullSession)
        assertNotNull(result)
        assertEquals(FullExitSource.CLOSE_X, result!!.exitSource)
        assertEquals(3, result.targetLogicalPage)
    }

    @Test
    fun recreation_keepsTargetAndSession() {
        launchFull(2, target = 4).use { scenario ->
            Thread.sleep(200)
            scenario.recreate()
            Thread.sleep(400)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val app = activity.application as AdsDemoApplication
                val snap = app.graph.onboardingFullCoordinator.snapshot.value
                assertNotNull(snap)
                assertEquals(2, snap!!.fullIndex)
                assertEquals(4, snap.targetLogicalPage)
            }
        }
    }

    @Test
    fun coordinatorSwipe_full1_toPager3_andFull2_toPager4() {
        val app = ApplicationProvider.getApplicationContext<AdsDemoApplication>()
        val s1 = FullSessionId("coord-full-1")
        app.graph.onboardingFullCoordinator.startOrAttach(
            fullSessionId = s1,
            onboardingSessionId = OnboardingSessionId("onb"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        assertTrue(app.graph.onboardingFullCoordinator.onSwipeForward(s1))
        assertEquals(3, app.graph.onboardingFullCoordinator.consumeExitResult(s1)!!.targetLogicalPage)

        val s2 = FullSessionId("coord-full-2")
        app.graph.onboardingFullCoordinator.startOrAttach(
            fullSessionId = s2,
            onboardingSessionId = OnboardingSessionId("onb"),
            fullIndex = 2,
            targetLogicalPage = 4,
        )
        assertTrue(app.graph.onboardingFullCoordinator.onSwipeForward(s2))
        assertEquals(4, app.graph.onboardingFullCoordinator.consumeExitResult(s2)!!.targetLogicalPage)
    }

    @Test
    fun fullGestureDetector_excludesMediaAndAcceptsForwardSwipe() {
        val host = View(ApplicationProvider.getApplicationContext())
        val fired = AtomicBoolean(false)
        val detector = FullGestureDetector(
            host = host,
            isForwardDx = { dx -> dx < 0f },
            isExcluded = { event -> event.rawX in 100f..200f && event.rawY in 100f..200f },
            onSwipeForward = { fired.set(true) },
        )
        // Excluded region — should not fire.
        feedSwipe(detector, startX = 150f, startY = 150f, dx = -500f)
        assertFalse(fired.get())
        // Short swipe — should not fire.
        feedSwipe(detector, startX = 400f, startY = 400f, dx = -20f)
        assertFalse(fired.get())
        // Valid forward swipe — should fire.
        feedSwipe(detector, startX = 700f, startY = 400f, dx = -500f)
        assertTrue(fired.get())
    }

    private fun launchFull(
        fullIndex: Int,
        target: Int,
        fullSessionId: String = "full-session-$fullIndex-${System.nanoTime()}",
    ): ActivityScenario<out Activity> {
        val clazz = if (fullIndex == 1) {
            OnboardingFull1Activity::class.java
        } else {
            OnboardingFull2Activity::class.java
        }
        val intent = Intent(ApplicationProvider.getApplicationContext(), clazz)
            .putExtra(OnboardingFullContract.EXTRA_SESSION_ID, "onb-session")
            .putExtra(OnboardingFullContract.EXTRA_FULL_SESSION_ID, fullSessionId)
            .putExtra(OnboardingFullContract.EXTRA_FULL_INDEX, fullIndex)
            .putExtra(OnboardingFullContract.EXTRA_HAS_TARGET, true)
            .putExtra(OnboardingFullContract.EXTRA_TARGET_PAGE, target)
        return ActivityScenario.launch(intent)
    }

    private fun dispatchForwardSwipe(
        activity: Activity,
        excluded: Boolean,
        distancePx: Float = 600f,
        startX: Float = activity.resources.displayMetrics.widthPixels * 0.85f,
        startY: Float = activity.resources.displayMetrics.heightPixels * 0.12f,
    ) {
        // Top band avoids Fake Native media/CTA; raw coords set for exclusion hit-tests.
        @Suppress("UNUSED_PARAMETER")
        val ignore = excluded
        val downTime = SystemClock.uptimeMillis()
        var t = downTime
        val endX = startX - distancePx
        activity.dispatchTouchEvent(motion(downTime, t, MotionEvent.ACTION_DOWN, startX, startY))
        for (i in 1..10) {
            t += 16
            val x = startX + (endX - startX) * (i / 10f)
            activity.dispatchTouchEvent(motion(downTime, t, MotionEvent.ACTION_MOVE, x, startY))
        }
        activity.dispatchTouchEvent(motion(downTime, t + 16, MotionEvent.ACTION_UP, endX, startY))
    }

    private fun feedSwipe(
        detector: FullGestureDetector,
        startX: Float,
        startY: Float,
        dx: Float,
    ) {
        val downTime = SystemClock.uptimeMillis()
        var t = downTime
        val endX = startX + dx
        detector.onTouchEvent(motion(downTime, t, MotionEvent.ACTION_DOWN, startX, startY))
        for (i in 1..10) {
            t += 16
            val x = startX + dx * (i / 10f)
            detector.onTouchEvent(motion(downTime, t, MotionEvent.ACTION_MOVE, x, startY))
        }
        detector.onTouchEvent(motion(downTime, t + 16, MotionEvent.ACTION_UP, endX, startY))
    }

    private fun motion(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ): MotionEvent {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            val setRawX = MotionEvent::class.java.getMethod("setRawX", Float::class.javaPrimitiveType)
            val setRawY = MotionEvent::class.java.getMethod("setRawY", Float::class.javaPrimitiveType)
            setRawX.invoke(event, x)
            setRawY.invoke(event, y)
        } catch (_: Throwable) {
            // Devices without setters still receive window x/y; exclusion uses raw when available.
        }
        return event
    }
}
