package com.example.adsmodule.core.onboarding.full

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.FullScreenTimingConfig
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.onboarding.OnboardingConfigKeys
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenario
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFullCoordinatorTest {
    @Test
    fun closeX_beforeDelay_doesNotExit() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        assertFalse(env.coordinator.onCloseClicked(fullSession))
        assertNull(env.coordinator.snapshot.value?.winningExitSource)
        assertFalse(env.coordinator.snapshot.value!!.closeVisible)
    }

    @Test
    fun closeX_afterDelay_exitsOnce() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(env.coordinator.snapshot.value!!.closeVisible)
        assertTrue(env.coordinator.onCloseClicked(fullSession))
        assertFalse(env.coordinator.onCloseClicked(fullSession))
        val result = env.coordinator.consumeExitResult(fullSession)
        assertNotNull(result)
        assertEquals(FullExitSource.CLOSE_X, result!!.exitSource)
        assertEquals(3, result.targetLogicalPage)
        assertNull(env.coordinator.consumeExitResult(fullSession))
    }

    @Test
    fun autoSkip_startsAfterCloseVisible() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(env.coordinator.snapshot.value!!.closeVisible)
        assertNull(env.coordinator.snapshot.value!!.winningExitSource)
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(FullExitSource.AUTO_SKIP, env.coordinator.snapshot.value!!.winningExitSource)
    }

    @Test
    fun swipeAndX_race_onlyOneWins() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        advanceTimeBy(2_000L)
        runCurrent()
        val swipe = env.coordinator.onSwipeForward(fullSession)
        val close = env.coordinator.onCloseClicked(fullSession)
        assertTrue(swipe xor close || (swipe && !close))
        assertTrue(swipe || close)
        assertFalse(swipe && close)
        val result = env.coordinator.consumeExitResult(fullSession)
        assertNotNull(result)
        assertTrue(
            result!!.exitSource == FullExitSource.SWIPE_FORWARD ||
                result.exitSource == FullExitSource.CLOSE_X,
        )
    }

    @Test
    fun swipeAndAuto_race_onlyOneWins() = runTest {
        val env = Env(this)
        env.preloadAndReady(2)
        val fullSession = FullSessionId("full-2")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 2,
            targetLogicalPage = 4,
        )
        advanceTimeBy(2_000L)
        runCurrent()
        // Fire auto skip concurrently with swipe by requesting both near deadline.
        advanceTimeBy(2_999L)
        runCurrent()
        val swipe = env.coordinator.onSwipeForward(fullSession)
        advanceTimeBy(1L)
        runCurrent()
        val snap = env.coordinator.snapshot.value!!
        assertNotNull(snap.winningExitSource)
        assertEquals(4, snap.targetLogicalPage)
        if (swipe) {
            assertEquals(FullExitSource.SWIPE_FORWARD, snap.winningExitSource)
        } else {
            assertEquals(FullExitSource.AUTO_SKIP, snap.winningExitSource)
        }
    }

    @Test
    fun staleAutoSkip_afterExit_ignored() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(env.coordinator.onSwipeForward(fullSession))
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(FullExitSource.SWIPE_FORWARD, env.coordinator.snapshot.value!!.winningExitSource)
        assertFalse(env.coordinator.onAutoSkip(fullSession))
    }

    @Test
    fun attach_recreation_keepsSessionAndDoesNotDuplicateExit() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        advanceTimeBy(1_000L)
        runCurrent()
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(env.coordinator.snapshot.value!!.closeVisible)
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(FullExitSource.AUTO_SKIP, env.coordinator.snapshot.value!!.winningExitSource)
        assertNotNull(env.coordinator.consumeExitResult(fullSession))
        assertNull(env.coordinator.consumeExitResult(fullSession))
    }

    @Test
    fun hostedObject_preservesSourceMetadata() = runTest {
        val env = Env(this)
        env.preloadAndReady(1)
        val fullSession = FullSessionId("full-1")
        env.coordinator.startOrAttach(
            fullSessionId = fullSession,
            onboardingSessionId = OnboardingSessionId("onb-1"),
            fullIndex = 1,
            targetLogicalPage = 3,
        )
        val ad = env.coordinator.snapshot.value!!.storedAd
        assertNotNull(ad)
        assertEquals(OnboardingConfigKeys.FULL1, ad!!.sourceConfigKey)
        assertEquals(OnboardingFullScreenInstances.full1, ad.screenInstanceId)
        assertEquals(100, ad.sourceWeight)
    }

    private class Env(private val testScope: TestScope) {
        private val dispatcher = StandardTestDispatcher(testScope.testScheduler)
        private val idSeq = AtomicLong(0L)
        val clock = Clock { testScope.testScheduler.currentTime }
        val storage = AdStorage(
            clock = clock,
            idGenerator = IdGenerator { "res-${idSeq.incrementAndGet()}" },
        )
        val controller = FakeAdsSdkController(
            clock = FakeClock { testScope.testScheduler.currentTime },
            dispatcher = dispatcher,
            objectIdGenerator = SequentialFakeObjectIdGenerator(),
        )
        private val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(
                FakeAdsSdkModule.create(controller).adapters,
            ),
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
        )
        private val snapshotProvider = {
            AdsConfigSnapshot.create(version = 1L, configs = buildConfigs())
        }
        private val scheduler = WholeListRefillScheduler(
            scope = testScope,
            loader = loader,
            storage = storage,
            deficitStore = RefillDeficitStore(),
            snapshotProvider = snapshotProvider,
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        val normalAds = NormalScreenAdCoordinator(
            scope = testScope,
            clock = clock,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
            loader = loader,
            storage = storage,
            refillScheduler = scheduler,
            snapshotProvider = snapshotProvider,
            audience = AudienceType.PAID,
        )
        private val hosted = HostedFullscreenCoordinator(
            storage = storage,
            lock = GlobalFullscreenLock(clock = clock),
            clock = clock,
            idGenerator = IdGenerator { "show-${idSeq.incrementAndGet()}" },
        )
        val coordinator = OnboardingFullCoordinator(
            scope = testScope,
            clock = clock,
            idGenerator = IdGenerator { "full-${idSeq.incrementAndGet()}" },
            normalAds = normalAds,
            hosted = hosted,
            snapshotProvider = snapshotProvider,
            audience = AudienceType.PAID,
        )

        init {
            listOf(
                OnboardingConfigKeys.FULL1.value to "full1",
                OnboardingConfigKeys.FULL2.value to "full2",
            ).forEach { (key, prefix) ->
                controller.setScenario(
                    FakeAdItemKey(key, 0, "$prefix-100"),
                    FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
                )
                controller.setScenario(
                    FakeAdItemKey(key, 1, "$prefix-90"),
                    FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
                )
            }
        }

        suspend fun preloadAndReady(fullIndex: Int) {
            coordinator.ensurePreloaded(fullIndex)
            testScope.advanceUntilIdle()
            val ready = storage.peekReady(
                OnboardingFullConfigKeys.adsKey(fullIndex),
                OnboardingFullScreenInstances.forIndex(fullIndex),
            )
            assertNotNull(ready)
        }

        private fun buildConfigs(): Map<ConfigKey, ResolvedConfig> {
            fun ads(prefix: String) = OriginalAdsConfig(
                enable = true,
                isOrganic = true,
                listAds = listOf(
                    OriginalAdItem(enableAd = true, weight = 100, adunit = "$prefix-100"),
                    OriginalAdItem(enableAd = true, weight = 90, adunit = "$prefix-90"),
                ),
            )
            return mapOf(
                OnboardingConfigKeys.FULL1 to ResolvedConfig(
                    value = AdsConfigValue(ads("full1")),
                    canonicalJson = """{"enable":true}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                OnboardingConfigKeys.FULL2 to ResolvedConfig(
                    value = AdsConfigValue(ads("full2")),
                    canonicalJson = """{"enable":true}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                OnboardingConfigKeys.FULL1_TIMING to ResolvedConfig(
                    value = FullScreenTimingConfig(2_000L, 3_000L),
                    canonicalJson = """{"time_delay_X_button":2000,"auto_skip":3000}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                OnboardingConfigKeys.FULL2_TIMING to ResolvedConfig(
                    value = FullScreenTimingConfig(2_000L, 3_000L),
                    canonicalJson = """{"time_delay_X_button":2000,"auto_skip":3000}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
            )
        }
    }
}
