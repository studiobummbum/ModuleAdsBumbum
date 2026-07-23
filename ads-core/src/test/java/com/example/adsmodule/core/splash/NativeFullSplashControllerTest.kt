package com.example.adsmodule.core.splash

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.fullscreen.HostedFullscreenSession
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.StoredAdView
import com.example.adsmodule.core.testsupport.SequentialIdGenerator
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeFullSplashControllerTest {
    @Test
    fun closeX_beforeDelay_rejected() = runTest {
        val env = Env(this)
        val snaps = mutableListOf<SplashNativeFullControlSnapshot>()
        val exits = mutableListOf<String>()
        env.controller.start(
            sessionId = env.sessionId,
            hostedSession = env.hostedSession,
            timeDelayXButtonMillis = 2_000L,
            autoSkipMillis = 3_000L,
            onSnapshot = { snaps += it },
            onExit = { exits += it },
        )
        advanceTimeBy(1_000L)
        runCurrent()
        assertFalse(env.controller.onCloseClicked(env.sessionId, env.showRequestId))
        assertTrue(exits.isEmpty())
        assertFalse(snaps.last().closeVisible)
    }

    @Test
    fun closeX_afterDelay_exitsOnce() = runTest {
        val env = Env(this)
        val exits = mutableListOf<String>()
        env.controller.start(
            sessionId = env.sessionId,
            hostedSession = env.hostedSession,
            timeDelayXButtonMillis = 2_000L,
            autoSkipMillis = 3_000L,
            onSnapshot = {},
            onExit = { exits += it },
        )
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(env.controller.onCloseClicked(env.sessionId, env.showRequestId))
        assertEquals(listOf("CLOSE_X"), exits)
        assertFalse(env.controller.onCloseClicked(env.sessionId, env.showRequestId))
        assertEquals(1, exits.size)
    }

    @Test
    fun autoSkip_startsAfterCloseVisible() = runTest {
        val env = Env(this)
        val snaps = mutableListOf<SplashNativeFullControlSnapshot>()
        val exits = mutableListOf<String>()
        env.controller.start(
            sessionId = env.sessionId,
            hostedSession = env.hostedSession,
            timeDelayXButtonMillis = 2_000L,
            autoSkipMillis = 3_000L,
            onSnapshot = { snaps += it },
            onExit = { exits += it },
        )
        advanceTimeBy(1_999L)
        runCurrent()
        assertTrue(exits.isEmpty())
        assertNull(snaps.last().autoSkipDeadlineMillis)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(snaps.last().closeVisible)
        assertEquals(5_000L, snaps.last().autoSkipDeadlineMillis)

        advanceTimeBy(2_999L)
        runCurrent()
        assertTrue(exits.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf("AUTO_SKIP"), exits)
    }

    @Test
    fun closeXAndAutoSkip_race_onlyOneWins() = runTest {
        val env = Env(this)
        val exits = mutableListOf<String>()
        env.controller.start(
            sessionId = env.sessionId,
            hostedSession = env.hostedSession,
            timeDelayXButtonMillis = 1_000L,
            autoSkipMillis = 1_000L,
            onSnapshot = {},
            onExit = { exits += it },
        )
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(env.controller.onCloseClicked(env.sessionId, env.showRequestId))
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, exits.size)
        assertEquals("CLOSE_X", exits.single())
    }

    @Test
    fun staleCloseAfterExit_ignored() = runTest {
        val env = Env(this)
        val exits = mutableListOf<String>()
        env.controller.start(
            sessionId = env.sessionId,
            hostedSession = env.hostedSession,
            timeDelayXButtonMillis = 500L,
            autoSkipMillis = 5_000L,
            onSnapshot = {},
            onExit = { exits += it },
        )
        advanceTimeBy(500L)
        runCurrent()
        assertTrue(env.controller.onCloseClicked(env.sessionId, env.showRequestId))
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, exits.size)
        assertFalse(env.controller.onCloseClicked(env.sessionId, env.showRequestId))
    }

    private class Env(scope: kotlinx.coroutines.test.TestScope) {
        val sessionId = SplashSessionId("splash-nf-1")
        val showRequestId = ShowRequestId("show-nf-1")
        private val ids = SequentialIdGenerator()
        val clock = Clock { scope.testScheduler.currentTime }
        private val storage = AdStorage(clock = clock, idGenerator = ids)
        private val lock = GlobalFullscreenLock(clock = clock)
        private val hosted = HostedFullscreenCoordinator(
            storage = storage,
            lock = lock,
            clock = clock,
            idGenerator = ids,
        )
        val controller = NativeFullSplashController(
            scope = scope,
            clock = clock,
            hosted = hosted,
        )
        val hostedSession = HostedFullscreenSession(
            showRequestId = showRequestId,
            reservationId = ReservationId("res-nf-1"),
            objectId = ObjectId("obj-nf-1"),
            kind = FullscreenAdKind.NATIVE_FULL_SPLASH,
            format = AdFormat.NATIVE_FULLSCREEN,
            storedAd = StoredAdView(
                objectId = ObjectId("obj-nf-1"),
                sourceConfigKey = ConfigKey("native_splash_full_config_1"),
                sourceListIndex = 0,
                sourceType = AdFormat.NATIVE_FULLSCREEN,
                sourceAdunit = "full-0",
                sourceWeight = 100,
                screenInstanceId = null,
                loadedAt = 0L,
                state = AdSlotState.SHOWING,
                sdkHandle = NoOpHandle,
            ),
            coveredShowRequestId = null,
            startedAtMillis = 0L,
        )
    }

    private object NoOpHandle : SdkLoadedAdHandle {
        override val format: AdFormat = AdFormat.NATIVE_FULLSCREEN
        override val adUnit: String = "full-0"
        private val destroyed = AtomicBoolean(false)
        override fun destroy() {
            destroyed.set(true)
        }
    }
}
