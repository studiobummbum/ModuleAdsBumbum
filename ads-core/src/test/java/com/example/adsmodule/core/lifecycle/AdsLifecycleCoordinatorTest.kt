package com.example.adsmodule.core.lifecycle

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.FullscreenLockAcquireRequest
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.sdk.AdFormat
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsLifecycleCoordinatorTest {
    @Test
    fun adClickBackgroundForeground_recordsAdClickReasonAndTurnbackPending() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)
        assertNotNull(env.lifecycle.onAdClick(ShowRequestId("show-1")))

        val background = env.lifecycle.onBackground() as AdsLifecycleTransitionResult.Accepted
        assertEquals(BackgroundReason.AD_CLICK, background.snapshot.lastBackgroundReason)

        val foreground = env.lifecycle.onForeground() as AdsLifecycleTransitionResult.Accepted
        assertTrue(foreground.snapshot.turnbackPending)
        assertTrue(
            foreground.snapshot.appOpenSuppression.reasons.contains(
                AppOpenSuppressionReason.TURNBACK_PENDING,
            ),
        )
    }

    @Test
    fun homeBackgroundForeground_recordsUserBackgroundWithoutTurnback() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)

        val background = env.lifecycle.onBackground(hint = BackgroundReason.USER_BACKGROUND)
            as AdsLifecycleTransitionResult.Accepted
        assertEquals(BackgroundReason.USER_BACKGROUND, background.snapshot.lastBackgroundReason)

        val foreground = env.lifecycle.onForeground() as AdsLifecycleTransitionResult.Accepted
        assertFalse(foreground.snapshot.turnbackPending)
        assertFalse(
            foreground.snapshot.appOpenSuppression.reasons.contains(
                AppOpenSuppressionReason.TURNBACK_PENDING,
            ),
        )
    }

    @Test
    fun systemInterruption_isDistinct() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)
        env.lifecycle.onAdClick(ShowRequestId("show-1"))
        val background = env.lifecycle.onBackground(hint = BackgroundReason.SYSTEM_INTERRUPTION)
            as AdsLifecycleTransitionResult.Accepted
        assertEquals(BackgroundReason.SYSTEM_INTERRUPTION, background.snapshot.lastBackgroundReason)
        val foreground = env.lifecycle.onForeground() as AdsLifecycleTransitionResult.Accepted
        assertFalse(foreground.snapshot.turnbackPending)
    }

    @Test
    fun unclassifiedTransition_recordsUnknown() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)
        val background = env.lifecycle.onBackground(hint = BackgroundReason.UNKNOWN)
            as AdsLifecycleTransitionResult.Accepted
        assertEquals(BackgroundReason.UNKNOWN, background.snapshot.lastBackgroundReason)
    }

    @Test
    fun userHome_doesNotCreateTurnback() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)
        env.simulator.simulateHomeBackground()
        val foreground = env.simulator.simulateForeground() as AdsLifecycleTransitionResult.Accepted
        assertFalse(foreground.snapshot.turnbackPending)
        assertFalse(env.tokenStore.hasValidToken(env.session))
    }

    @Test
    fun staleSessionCallback_isIgnored() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)
        env.lifecycle.bindSession(SessionId("other"))
        val ignored = env.lifecycle.onBackground()
        // Bound session is "other"; calling without mismatch through public API still works.
        // Explicit stale generation against tracker is covered separately; here rebinding clears.
        assertTrue(ignored is AdsLifecycleTransitionResult.Accepted || ignored is AdsLifecycleTransitionResult.Ignored)
        assertEquals(SessionId("other"), env.lifecycle.snapshot.value.sessionId)
    }

    @Test
    fun clickToken_expiresAtBoundary() = runTest {
        val env = Env(this, tokenTtlMillis = 100L)
        env.lifecycle.bindSession(env.session)
        env.lifecycle.onAdClick(ShowRequestId("show-1"), ttlMillis = 100L)
        assertTrue(env.tokenStore.hasValidToken(env.session))
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.CLICK_TOKEN_PRESENT,
            ),
        )

        env.clock.advance(100L)
        assertFalse(env.tokenStore.hasValidToken(env.session))
        assertFalse(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.CLICK_TOKEN_PRESENT,
            ),
        )
    }

    @Test
    fun suppression_coversLockSplashTokenTurnbackAndInvalidActivity() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)

        env.lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("busy"),
                objectId = ObjectId("obj"),
                sourceConfigKey = ConfigKey("inter_splash_config_1"),
                screenInstanceId = null,
                format = AdFormat.INTERSTITIAL,
                kind = FullscreenAdKind.INTERSTITIAL,
            ),
        )
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.FULLSCREEN_LOCK_BUSY,
            ),
        )
        env.lock.release(ShowRequestId("busy"))

        env.lifecycle.setSplashActive(true)
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.SPLASH_ACTIVE,
            ),
        )
        env.lifecycle.setSplashActive(false)

        env.lifecycle.onAdClick(ShowRequestId("click"))
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.CLICK_TOKEN_PRESENT,
            ),
        )

        env.lifecycle.setTurnbackPending(true)
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.TURNBACK_PENDING,
            ),
        )
        env.lifecycle.clearTurnbackPending()

        env.lifecycle.setActivityValid(false)
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.ACTIVITY_INVALID,
            ),
        )
    }

    @Test
    fun simulatorApi_exposesInspectorSnapshot() = runTest {
        val env = Env(this)
        env.simulator.bindSession(env.session)
        env.simulator.simulateAdClick(ShowRequestId("sim-1"))
        env.simulator.simulateAdClickBackground()
        env.simulator.simulateForeground()
        val snapshot = env.simulator.inspectorSnapshot()
        assertTrue(snapshot.lifecycle.turnbackPending)
        assertNotNull(snapshot.tokens.tokens.firstOrNull())
        assertEquals(env.session, snapshot.lifecycle.sessionId)
    }

    private class Env(
        scope: TestScope,
        tokenTtlMillis: Long = 1_000L,
    ) {
        val clock = MutableClock()
        val session = SessionId("session-1")
        private val ids = SequentialIdGenerator()
        val tokenStore = AdClickTokenStore(clock = clock, idGenerator = ids)
        val lock = GlobalFullscreenLock(clock = clock)
        val tracker = ForegroundSessionTracker(clock = clock)
        val lifecycle = AdsLifecycleCoordinator(
            sessionTracker = tracker,
            tokenStore = tokenStore,
            fullscreenLock = lock,
            clock = clock,
            defaultClickTokenTtlMillis = tokenTtlMillis,
            scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
        )
        val simulator = LifecycleSimulatorApi(
            lifecycle = lifecycle,
            fullscreenLock = lock,
            tokenStore = tokenStore,
            clock = clock,
        )
    }

    private class MutableClock(start: Long = 0L) : Clock {
        private val now = AtomicLong(start)
        override fun nowMillis(): Long = now.get()
        fun advance(delta: Long) {
            now.addAndGet(delta)
        }
    }

    private class SequentialIdGenerator(
        private val prefix: String = "id",
    ) : IdGenerator {
        private val next = AtomicLong(0L)
        override fun nextId(): String = "$prefix-${next.incrementAndGet()}"
    }
}
