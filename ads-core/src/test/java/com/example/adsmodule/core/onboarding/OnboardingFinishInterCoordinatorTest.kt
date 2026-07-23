package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.load.WeightedListLoader
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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFinishInterCoordinatorTest {
    @Test
    fun eligible_showsInterThenHome() = runTest {
        val env = Env(this, audience = AudienceType.PAID)
        env.success("onb-inter-100")
        val result = env.finish.finish()
        advanceUntilIdle()
        val shown = result as OnboardingFinishResult.InterShownThenHome
        assertEquals("onb-inter-100", shown.storedAd.sourceAdunit)
    }

    @Test
    fun organicIneligible_fallsBackHomeOnce() = runTest {
        val env = Env(this, audience = AudienceType.ORGANIC)
        env.success("onb-inter-100")
        val first = env.finish.finish()
        val second = env.finish.finish()
        assertTrue(first is OnboardingFinishResult.HomeFallback)
        assertEquals("audience ineligible", (first as OnboardingFinishResult.HomeFallback).reason)
        assertTrue(second is OnboardingFinishResult.HomeFallback)
        assertEquals("already finished", (second as OnboardingFinishResult.HomeFallback).reason)
    }

    @Test
    fun loadFail_fallsBackHome() = runTest {
        val env = Env(this, audience = AudienceType.PAID)
        env.failAll()
        val result = env.finish.finish()
        advanceUntilIdle()
        assertTrue(result is OnboardingFinishResult.HomeFallback)
    }

    @Test
    fun finishOnce_isIdempotent() = runTest {
        val env = Env(this, audience = AudienceType.PAID)
        env.success("onb-inter-100")
        val first = env.finish.finish()
        advanceUntilIdle()
        assertTrue(first is OnboardingFinishResult.InterShownThenHome)
        val second = env.finish.finish()
        assertTrue(second is OnboardingFinishResult.HomeFallback)
        assertEquals("already finished", (second as OnboardingFinishResult.HomeFallback).reason)
    }

    private class Env(
        scope: TestScope,
        audience: AudienceType,
    ) {
        private val key = OnboardingFinishKeys.INTER_ONBOARDING
        private val screen = ScreenInstanceId("onboarding-finish-1")
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val idSeq = AtomicLong(0L)
        val clock = Clock { scope.testScheduler.currentTime }
        val storage = AdStorage(
            clock = clock,
            idGenerator = IdGenerator { "res-${idSeq.incrementAndGet()}" },
        )
        val controller = FakeAdsSdkController(
            clock = FakeClock { scope.testScheduler.currentTime },
            dispatcher = dispatcher,
            objectIdGenerator = SequentialFakeObjectIdGenerator(),
        )
        private val adapters = AdSdkAdapterRegistry.create(
            FakeAdsSdkModule.create(controller).adapters,
        )
        private val loader = WeightedListLoader(
            adapterRegistry = adapters,
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
        )
        private val snapshotConfigs = AtomicReference(
            mapOf(
                key to OriginalAdsConfig(
                    enable = true,
                    isOrganic = false,
                    timeoutTotalMillis = 30_000L,
                    listAds = listOf(
                        OriginalAdItem(
                            enableAd = true,
                            weight = 100,
                            timeoutMillis = 15_000L,
                            type = "inter",
                            adunit = "onb-inter-100",
                        ),
                        OriginalAdItem(
                            enableAd = true,
                            weight = 90,
                            timeoutMillis = 15_000L,
                            type = "inter",
                            adunit = "onb-inter-90",
                        ),
                    ),
                ),
            ),
        )
        private val snapshotProvider = {
            AdsConfigSnapshot.create(
                version = 1L,
                configs = snapshotConfigs.get().mapValues { (_, config) ->
                    ResolvedConfig(
                        value = AdsConfigValue(config),
                        canonicalJson = """{"enable":true,"list_ads":[]}""",
                        origin = ConfigValueOrigin.BUNDLED,
                    )
                },
            )
        }
        private val deficitStore = RefillDeficitStore()
        private val refillScheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = snapshotProvider,
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        private val lock = GlobalFullscreenLock(clock = clock)
        private val fullscreen = FullscreenShowCoordinator(
            storage = storage,
            lock = lock,
            adapters = adapters,
            clock = clock,
            idGenerator = IdGenerator { "show-${idSeq.incrementAndGet()}" },
        )
        val finish = OnboardingFinishInterCoordinator(
            scope = scope,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
            loader = loader,
            storage = storage,
            fullscreen = fullscreen,
            refillScheduler = refillScheduler,
            snapshotProvider = snapshotProvider,
            audience = audience,
            screenInstanceId = screen,
        )

        fun success(adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(key.value, 0, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
            controller.setScenario(
                FakeAdItemKey(key.value, 1, "onb-inter-90"),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
        }

        fun failAll() {
            controller.setScenario(
                FakeAdItemKey(key.value, 0, "onb-inter-100"),
                FakeScenarioConfig(scenario = FakeScenario.FAIL),
            )
            controller.setScenario(
                FakeAdItemKey(key.value, 1, "onb-inter-90"),
                FakeScenarioConfig(scenario = FakeScenario.FAIL),
            )
        }
    }
}
