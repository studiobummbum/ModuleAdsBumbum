package com.example.adsmodule.core.fullscreen

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.lifecycle.AdsLifecycleTransitionResult
import com.example.adsmodule.core.lifecycle.AppOpenSuppressionReason
import com.example.adsmodule.core.lifecycle.BackgroundReason
import com.example.adsmodule.core.lifecycle.ForegroundSessionTracker
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdRequestMetadata
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FullscreenLifecycleIntegrationTest {
    @Test
    fun clickBackgroundForeground_issuesTokenAndSuppressesAppOpen() = runTest {
        val env = Env(this)
        env.lifecycle.bindSession(env.session)
        env.lifecycle.attachFullscreenClicks(env.coordinator)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "unit-inter"),
            FakeScenarioConfig(
                impressionDelayMillis = 0L,
                clickDelayMillis = 20L,
                dismissDelayMillis = 100L,
            ),
        )
        val reservation = env.loadReserve(
            format = AdFormat.INTERSTITIAL,
            configKey = ConfigKey("inter_splash_config_1"),
            adUnit = "unit-inter",
            objectId = "object-1",
        )

        val show = async {
            env.coordinator.show(reservation, FullscreenAdKind.INTERSTITIAL)
        }
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()
        assertTrue(env.tokenStore.hasValidToken(env.session))

        val background = env.lifecycle.onBackground() as AdsLifecycleTransitionResult.Accepted
        assertEquals(BackgroundReason.AD_CLICK, background.snapshot.lastBackgroundReason)
        assertTrue(
            env.lifecycle.evaluateAppOpenSuppression().reasons.contains(
                AppOpenSuppressionReason.FULLSCREEN_LOCK_BUSY,
            ),
        )

        advanceTimeBy(100L)
        advanceUntilIdle()
        assertTrue(show.await() is FullscreenShowResult.Dismissed)
        assertFalse(env.lock.isBusy())

        val foreground = env.lifecycle.onForeground() as AdsLifecycleTransitionResult.Accepted
        assertTrue(foreground.snapshot.turnbackPending)
        assertTrue(
            foreground.snapshot.appOpenSuppression.reasons.contains(
                AppOpenSuppressionReason.TURNBACK_PENDING,
            ),
        )
    }

    @Test
    fun showFailure_releasesOnceAndAllowsNextShow() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "fail-unit"),
            FakeScenarioConfig(
                scenario = com.example.adsmodule.fake.FakeScenario.SHOW_FAIL,
            ),
        )
        val first = env.loadReserve(
            format = AdFormat.INTERSTITIAL,
            configKey = ConfigKey("inter_splash_config_1"),
            adUnit = "fail-unit",
            objectId = "object-fail",
        )
        val failed = env.coordinator.show(first, FullscreenAdKind.INTERSTITIAL)
        advanceUntilIdle()
        assertTrue(failed is FullscreenShowResult.Failed)
        assertFalse(env.lock.isBusy())

        val second = env.loadReserve(
            format = AdFormat.INTERSTITIAL,
            configKey = ConfigKey("inter_splash_config_1"),
            adUnit = "ok-unit",
            objectId = "object-ok",
            listIndex = 1,
        )
        val dismissed = env.coordinator.show(second, FullscreenAdKind.INTERSTITIAL)
        advanceUntilIdle()
        assertTrue(dismissed is FullscreenShowResult.Dismissed)
        assertEquals(AdSlotState.CONSUMED, env.storage.get(ObjectId("object-ok"))?.state)
    }

    @Test
    fun staleFakeCallback_cannotUnlockLaterShow() = runTest {
        val env = Env(this)
        env.lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("new-owner"),
                objectId = ObjectId("new-obj"),
                sourceConfigKey = ConfigKey("inter_splash_config_1"),
                screenInstanceId = null,
                format = AdFormat.INTERSTITIAL,
                kind = FullscreenAdKind.INTERSTITIAL,
            ),
        )
        val stale = env.lock.release(ShowRequestId("old-owner"))
        assertTrue(stale is FullscreenLockReleaseResult.Stale)
        assertEquals(ShowRequestId("new-owner"), env.lock.currentOwner()?.showRequestId)
    }

    private class Env(
        scope: TestScope,
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val clock = MutableClock(scope)
        private val ids = SequentialIdGenerator()
        val session = SessionId("session-1")
        val controller = FakeAdsSdkController(
            clock = FakeClock { scope.testScheduler.currentTime },
            dispatcher = dispatcher,
            objectIdGenerator = SequentialFakeObjectIdGenerator(prefix = "fake-object"),
        )
        private val sdk = FakeAdsSdkModule.create(controller)
        val storage = AdStorage(clock = clock, idGenerator = ids)
        val lock = GlobalFullscreenLock(clock = clock)
        val coordinator = FullscreenShowCoordinator(
            storage = storage,
            lock = lock,
            adapters = AdSdkAdapterRegistry.create(sdk.adapters),
            clock = clock,
            idGenerator = ids,
        )
        val tokenStore = AdClickTokenStore(clock = clock, idGenerator = ids)
        val lifecycle = AdsLifecycleCoordinator(
            sessionTracker = ForegroundSessionTracker(clock = clock),
            tokenStore = tokenStore,
            fullscreenLock = lock,
            clock = clock,
            defaultClickTokenTtlMillis = 5_000L,
            scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
        )

        suspend fun loadReserve(
            format: AdFormat,
            configKey: ConfigKey,
            adUnit: String,
            objectId: String,
            listIndex: Int = 0,
        ): com.example.adsmodule.core.ReservationId {
            val loaded = sdk.adapterFor(format).load(
                AdLoadRequest(
                    loadRequestId = "load-$objectId",
                    format = format,
                    adUnit = adUnit,
                    timeoutMillis = null,
                    metadata = AdRequestMetadata(configKey.value, listIndex),
                ),
            )
            val handle = (loaded as AdLoadResult.Success).handle
            val put = storage.putReady(
                StoredAd(
                    objectId = ObjectId(objectId),
                    sourceConfigKey = configKey,
                    sourceListIndex = listIndex,
                    sourceType = format,
                    sourceAdunit = adUnit,
                    sourceWeight = 10,
                    screenInstanceId = null,
                    loadedAt = clock.nowMillis(),
                    state = AdSlotState.READY,
                    sdkHandle = handle,
                ),
            )
            assertTrue(put is PutResult.Accepted)
            return (storage.reserveNormal(configKey, null) as ReserveResult.Accepted)
                .reservation.reservationId
        }
    }

    private class MutableClock(
        private val scope: TestScope,
    ) : Clock {
        override fun nowMillis(): Long = scope.testScheduler.currentTime
    }

    private class SequentialIdGenerator(
        private val prefix: String = "id",
    ) : IdGenerator {
        private val next = AtomicLong(0L)
        override fun nextId(): String = "$prefix-${next.incrementAndGet()}"
    }
}
