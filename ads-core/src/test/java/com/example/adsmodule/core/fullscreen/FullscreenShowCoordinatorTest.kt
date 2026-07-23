package com.example.adsmodule.core.fullscreen

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenario
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdRequestMetadata
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FullscreenShowCoordinatorTest {
    @Test
    fun dismiss_releasesMatchingLockAndConsumes() = runTest {
        val env = Env(this)
        val reservation = env.loadReserve(
            format = AdFormat.INTERSTITIAL,
            configKey = ConfigKey("inter_splash_config_1"),
            adUnit = "unit-inter",
            objectId = "object-1",
        )
        val events = mutableListOf<FullscreenShowEvent>()
        val collect = launch { env.coordinator.events.collect { events += it } }

        val result = env.coordinator.show(reservation, FullscreenAdKind.INTERSTITIAL)
        advanceUntilIdle()
        collect.cancel()

        assertTrue(result is FullscreenShowResult.Dismissed)
        assertFalse(env.lock.isBusy())
        assertEquals(AdSlotState.CONSUMED, env.storage.get(ObjectId("object-1"))?.state)
        assertTrue(events.any { it is FullscreenShowEvent.Shown })
        assertTrue(events.any { it is FullscreenShowEvent.Dismissed })
    }

    @Test
    fun showFail_releasesMatchingLockAndMarksFailed() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "unit-inter"),
            FakeScenarioConfig(scenario = FakeScenario.SHOW_FAIL),
        )
        val reservation = env.loadReserve(
            format = AdFormat.INTERSTITIAL,
            configKey = ConfigKey("inter_splash_config_1"),
            adUnit = "unit-inter",
            objectId = "object-1",
        )

        val result = env.coordinator.show(reservation, FullscreenAdKind.INTERSTITIAL)
        advanceUntilIdle()

        assertTrue(result is FullscreenShowResult.Failed)
        assertFalse(env.lock.isBusy())
        assertEquals(AdSlotState.FAILED, env.storage.get(ObjectId("object-1"))?.state)
    }

    @Test
    fun click_doesNotPrematurelyReleaseLock() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "unit-inter"),
            FakeScenarioConfig(
                impressionDelayMillis = 0L,
                clickDelayMillis = 50L,
                dismissDelayMillis = 200L,
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
        advanceTimeBy(50L)
        runCurrent()
        assertTrue(env.lock.isBusy())
        advanceTimeBy(200L)
        advanceUntilIdle()
        assertTrue(show.await() is FullscreenShowResult.Dismissed)
        assertFalse(env.lock.isBusy())
    }

    @Test
    fun busyLock_rejectsSecondFullscreen() = runTest {
        val env = Env(this)
        env.lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("holder"),
                objectId = ObjectId("holder-obj"),
                sourceConfigKey = ConfigKey("inter_splash_config_1"),
                screenInstanceId = null,
                format = AdFormat.INTERSTITIAL,
                kind = FullscreenAdKind.INTERSTITIAL,
            ),
        )
        val reservation = env.loadReserve(
            format = AdFormat.APP_OPEN,
            configKey = ConfigKey("appopen_resume_config_1"),
            adUnit = "unit-appopen",
            objectId = "object-2",
        )

        val result = env.coordinator.show(reservation, FullscreenAdKind.APP_OPEN)
        assertTrue(result is FullscreenShowResult.Rejected)
        assertEquals(AdSlotState.READY, env.storage.get(ObjectId("object-2"))?.state)
        assertEquals(ShowRequestId("holder"), env.lock.currentOwner()?.showRequestId)
    }

    @Test
    fun supportedKinds_acceptMatchingFormats() = runTest {
        val cases = listOf(
            Triple(AdFormat.INTERSTITIAL, FullscreenAdKind.INTERSTITIAL, "inter_splash_config_1"),
            Triple(AdFormat.APP_OPEN, FullscreenAdKind.APP_OPEN, "appopen_resume_config_1"),
            Triple(
                AdFormat.NATIVE_FULLSCREEN,
                FullscreenAdKind.NATIVE_FULL_SPLASH,
                "native_splash_full_config_1",
            ),
            Triple(
                AdFormat.NATIVE_FULLSCREEN,
                FullscreenAdKind.NATIVE_FULL_ONBOARDING,
                "native_full1_config_1",
            ),
            Triple(
                AdFormat.INTERSTITIAL,
                FullscreenAdKind.INTER_ONBOARDING,
                "inter_onboarding_config_1",
            ),
        )
        cases.forEachIndexed { index, (format, kind, config) ->
            val env = Env(this)
            val reservation = env.loadReserve(
                format = format,
                configKey = ConfigKey(config),
                adUnit = "unit-$index",
                objectId = "object-$index",
                listIndex = index,
            )
            val result = env.coordinator.show(reservation, kind)
            advanceUntilIdle()
            assertTrue("kind=$kind result=$result", result is FullscreenShowResult.Dismissed)
            assertFalse(env.lock.isBusy())
        }
    }

    @Test
    fun staleRelease_doesNotUnlockNewerOwner() {
        val clock = Clock { 0L }
        val lock = GlobalFullscreenLock(clock)
        lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("old"),
                objectId = ObjectId("old-obj"),
                sourceConfigKey = ConfigKey("inter_splash_config_1"),
                screenInstanceId = null,
                format = AdFormat.INTERSTITIAL,
                kind = FullscreenAdKind.INTERSTITIAL,
            ),
        )
        lock.release(ShowRequestId("old"))
        lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("new"),
                objectId = ObjectId("new-obj"),
                sourceConfigKey = ConfigKey("inter_splash_config_1"),
                screenInstanceId = null,
                format = AdFormat.INTERSTITIAL,
                kind = FullscreenAdKind.INTERSTITIAL,
            ),
        )
        assertTrue(lock.release(ShowRequestId("old")) is FullscreenLockReleaseResult.Stale)
        assertEquals(ShowRequestId("new"), lock.currentOwner()?.showRequestId)
    }

    @Test
    fun abnormalFlowEnd_marksFailedAndReleases() = runTest {
        val emptyAdapter = object : AdSdkAdapter {
            override val supportedFormats: Set<AdFormat> = setOf(AdFormat.APP_OPEN)
            override suspend fun load(request: AdLoadRequest): AdLoadResult = error("not used")
            override fun show(request: AdShowRequest): Flow<AdShowEvent> = flow {
                emit(AdShowEvent.Shown(request.showRequestId))
            }
        }
        val env = Env(this, adapters = listOf(emptyAdapter))
        val reservation = env.putReserve(
            format = AdFormat.APP_OPEN,
            configKey = ConfigKey("appopen_resume_config_1"),
            objectId = "object-appopen",
            handle = ManualHandle(AdFormat.APP_OPEN, "unit-appopen"),
        )

        val result = env.coordinator.show(reservation, FullscreenAdKind.APP_OPEN)
        advanceUntilIdle()

        assertTrue(result is FullscreenShowResult.Failed)
        assertFalse(env.lock.isBusy())
        assertEquals(AdSlotState.FAILED, env.storage.get(ObjectId("object-appopen"))?.state)
    }

    @Test
    fun wrongKind_rejectsAndReturnsReservationToReady() = runTest {
        val env = Env(this)
        val reservation = env.loadReserve(
            format = AdFormat.INTERSTITIAL,
            configKey = ConfigKey("inter_splash_config_1"),
            adUnit = "unit-inter",
            objectId = "object-1",
        )
        val result = env.coordinator.show(reservation, FullscreenAdKind.APP_OPEN)
        assertTrue(result is FullscreenShowResult.Rejected)
        assertFalse(env.lock.isBusy())
        assertEquals(AdSlotState.READY, env.storage.get(ObjectId("object-1"))?.state)
    }

    private class Env(
        scope: TestScope,
        adapters: List<AdSdkAdapter>? = null,
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val clock = Clock { scope.testScheduler.currentTime }
        private val ids = SequentialIdGenerator()
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
            adapters = AdSdkAdapterRegistry.create(adapters ?: sdk.adapters),
            clock = clock,
            idGenerator = ids,
        )

        suspend fun loadReserve(
            format: AdFormat,
            configKey: ConfigKey,
            adUnit: String,
            objectId: String,
            listIndex: Int = 0,
        ): ReservationId {
            val loadResult = sdk.adapterFor(format).load(
                AdLoadRequest(
                    loadRequestId = "load-$objectId",
                    format = format,
                    adUnit = adUnit,
                    timeoutMillis = null,
                    metadata = AdRequestMetadata(configKey.value, listIndex),
                ),
            )
            val handle = (loadResult as AdLoadResult.Success).handle
            return putReserve(
                format = format,
                configKey = configKey,
                objectId = objectId,
                handle = handle,
                listIndex = listIndex,
                adUnit = adUnit,
            )
        }

        fun putReserve(
            format: AdFormat,
            configKey: ConfigKey,
            objectId: String,
            handle: SdkLoadedAdHandle,
            listIndex: Int = 0,
            adUnit: String = handle.adUnit,
        ): ReservationId {
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
            val reserved = storage.reserveNormal(configKey, null) as ReserveResult.Accepted
            return reserved.reservation.reservationId
        }
    }

    private class SequentialIdGenerator(
        private val prefix: String = "id",
    ) : IdGenerator {
        private val next = AtomicLong(0L)
        override fun nextId(): String = "$prefix-${next.incrementAndGet()}"
    }

    private class ManualHandle(
        override val format: AdFormat,
        override val adUnit: String,
    ) : SdkLoadedAdHandle {
        private val destroyed = AtomicInteger(0)
        override fun destroy() {
            destroyed.incrementAndGet()
        }
    }
}
