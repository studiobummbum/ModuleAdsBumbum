package com.example.adsmodule.core.home

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
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
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenUnbindResult
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAdsCoordinatorTest {
    @Test
    fun interAll_loadsHighestWeightFirst() = runTest {
        val env = Env(this)
        env.fail(env.interKey, 0, "inter-100")
        env.success(env.interKey, 1, "inter-90")
        val result = env.home.triggerHomeAction()
        advanceUntilIdle()
        val shown = result as HomeInterShowResult.Shown
        assertEquals("inter-90", shown.storedAd.sourceAdunit)
        assertEquals(90, shown.storedAd.sourceWeight)
        assertEquals(1, shown.storedAd.sourceListIndex)
    }

    @Test
    fun interval_blocksSecondShowUntilElapsed() = runTest {
        val env = Env(this, intervalMillis = 30_000L)
        env.success(env.interKey, 0, "inter-100")
        env.success(env.interKey, 1, "inter-90")

        val first = env.home.triggerHomeAction()
        advanceUntilIdle()
        assertTrue(first is HomeInterShowResult.Shown)

        val blocked = env.home.triggerHomeAction()
        assertTrue(blocked is HomeInterShowResult.IntervalBlocked)

        advanceTimeBy(30_000L)
        val second = env.home.triggerHomeAction()
        advanceUntilIdle()
        assertTrue(second is HomeInterShowResult.Shown)
    }

    @Test
    fun banner_bindThenDestroyConsumes() = runTest {
        val env = Env(this)
        env.success(env.bannerKey, 0, "banner-100")
        val bound = env.home.bindBanner() as NormalScreenBindResult.Bound
        assertEquals(env.bannerKey, bound.session.configKey)
        val destroyed = env.home.destroyBanner()
        assertTrue(destroyed is NormalScreenUnbindResult.Consumed)
        assertEquals(AdSlotState.CONSUMED, env.storage.get(bound.session.objectId)?.state)
        val again = env.home.destroyBanner()
        assertEquals(null, again)
    }

    private class Env(
        scope: TestScope,
        intervalMillis: Long? = 30_000L,
        audience: AudienceType = AudienceType.PAID,
    ) {
        val bannerKey = HomeAdsKeys.BANNER_HOME
        val interKey = HomeAdsKeys.INTER_ALL
        val screen = ScreenInstanceId("home-1")

        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val idSeq = AtomicLong(0L)
        val clock = Clock { scope.testScheduler.currentTime }
        val storage = AdStorage(
            clock = clock,
            idGenerator = IdGenerator { "res-${idSeq.incrementAndGet()}" },
        )
        val deficitStore = RefillDeficitStore()
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
                bannerKey to ads(
                    isOrganic = true,
                    units = listOf("banner-100", "banner-90"),
                    type = null,
                    intervalMillis = null,
                ),
                interKey to ads(
                    isOrganic = true,
                    units = listOf("inter-100", "inter-90"),
                    type = "inter",
                    intervalMillis = intervalMillis,
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
        private val refillScheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = snapshotProvider,
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        private val normalAds = NormalScreenAdCoordinator(
            scope = scope,
            clock = clock,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
            loader = loader,
            storage = storage,
            refillScheduler = refillScheduler,
            snapshotProvider = snapshotProvider,
            audience = audience,
        )
        private val lock = GlobalFullscreenLock(clock = clock)
        private val fullscreen = FullscreenShowCoordinator(
            storage = storage,
            lock = lock,
            adapters = adapters,
            clock = clock,
            idGenerator = IdGenerator { "show-${idSeq.incrementAndGet()}" },
        )
        val home = HomeAdsCoordinator(
            scope = scope,
            clock = clock,
            normalAds = normalAds,
            storage = storage,
            fullscreen = fullscreen,
            refillScheduler = refillScheduler,
            snapshotProvider = snapshotProvider,
            audience = audience,
            screenInstanceId = screen,
        )

        fun success(config: ConfigKey, index: Int, adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(config.value, index, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
        }

        fun fail(config: ConfigKey, index: Int, adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(config.value, index, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.FAIL),
            )
        }

        private fun ads(
            isOrganic: Boolean,
            units: List<String>,
            type: String?,
            intervalMillis: Long?,
        ): OriginalAdsConfig = OriginalAdsConfig(
            enable = true,
            isOrganic = isOrganic,
            intervalMillis = intervalMillis,
            listAds = units.mapIndexed { index, unit ->
                OriginalAdItem(
                    enableAd = true,
                    weight = 100 - index * 10,
                    type = type,
                    adunit = unit,
                )
            },
        )
    }
}
