package com.example.adsmodule.core.refill

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.StorageSlotKey
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenario
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WholeListRefillSchedulerTest {
    @Test
    fun immediateRefill_loadsWholeListAndKeepsNewItemWeight() = runTest {
        val env = Env(this)
        env.fail(index = 0, adUnit = "old-100")
        env.success(index = 1, adUnit = "new-90")
        env.activate()
        env.putSeed(objectId = "seed", weight = 100, adunit = "old-100", listIndex = 0)
        env.storage.reserveNormal(env.config, env.screen)
        assertTrue(env.scheduler.requestRefill(env.slot))
        advanceUntilIdle()

        val ready = env.storage.peekReady(env.config, env.screen)
        assertEquals("new-90", ready?.sourceAdunit)
        assertEquals(90, ready?.sourceWeight)
        assertEquals(1, ready?.sourceListIndex)
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(env.config.value, 0, "old-100")))
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(env.config.value, 1, "new-90")))
    }

    @Test
    fun dedupe_doesNotStartSecondJobWhileInFlight() = runTest {
        val env = Env(this)
        env.snapshotConfigs.set(
            mapOf(env.config to env.configWith(listOf(env.item(true, 10, "slow", 0)))),
        )
        env.controller.setScenario(
            FakeAdItemKey(env.config.value, 0, "slow"),
            FakeScenarioConfig(scenario = FakeScenario.DELAYED_SUCCESS, loadDelayMillis = 1_000L),
        )
        env.activate()
        assertTrue(env.scheduler.requestRefill(env.slot))
        runCurrent()
        assertTrue(env.scheduler.isJobActive(env.slot))
        assertEquals(1, env.deficitStore.inFlightCount(env.slot))
        assertTrue(env.scheduler.requestRefill(env.slot))
        assertEquals(1, env.deficitStore.inFlightCount(env.slot))
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(env.config.value, 0, "slow")))
    }

    @Test
    fun inactiveScreen_cancelsAndDestroysLateSuccess() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey(env.config.value, 0, "slow"),
            FakeScenarioConfig(scenario = FakeScenario.DELAYED_SUCCESS, loadDelayMillis = 500L),
        )
        env.snapshotConfigs.set(
            mapOf(env.config to env.configWith(listOf(env.item(true, 10, "slow", 0)))),
        )
        env.activate()
        assertTrue(env.scheduler.requestRefill(env.slot))
        runCurrent()
        env.scheduler.deactivate(env.slot)
        advanceUntilIdle()
        assertEquals(0, env.storage.readyCount(env.slot))
        assertFalse(env.deficitStore.isActive(env.slot))
    }

    @Test
    fun targetReadyCountGreaterThanOne_loadsSequentially() = runTest {
        val env = Env(this)
        env.snapshotConfigs.set(
            mapOf(env.config to env.configWith(listOf(env.item(true, 10, "unit-a", 0)))),
        )
        env.success(index = 0, adUnit = "unit-a")
        env.activate(target = 2)
        assertTrue(env.scheduler.requestRefill(env.slot))
        advanceUntilIdle()
        assertEquals(2, env.storage.readyCount(env.slot))
        assertEquals(2, env.controller.requestCount(FakeAdItemKey(env.config.value, 0, "unit-a")))
    }

    @Test
    fun sharedAdunit_refillsCorrectSourceSlot() = runTest {
        val env = Env(this)
        val other = StorageSlotKey(ConfigKey("native_language_dup_config_1"), ScreenInstanceId("dup"))
        val sharedUnit = "shared-native"
        env.snapshotConfigs.set(
            mapOf(
                env.config to env.configWith(
                    listOf(env.item(true, 50, sharedUnit, 0)),
                ),
                other.configKey to env.configWith(
                    listOf(env.item(true, 50, sharedUnit, 0)),
                ),
            ),
        )
        env.controller.setScenario(
            FakeAdItemKey(env.config.value, 0, sharedUnit),
            FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
        )
        env.controller.setScenario(
            FakeAdItemKey(other.configKey.value, 0, sharedUnit),
            FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
        )
        env.activate()
        env.scheduler.activate(other, targetReadyCount = 1, refillIfDeficit = false)
        assertTrue(env.scheduler.requestRefill(env.slot))
        advanceUntilIdle()
        val ready = env.storage.peekReady(env.config, env.screen)
        assertEquals(env.config, ready?.sourceConfigKey)
        assertEquals(env.screen, ready?.screenInstanceId)
        assertEquals(0, env.storage.readyCount(other))
    }

    private class Env(
        scope: TestScope,
    ) {
        val config = ConfigKey("native_language_config_1")
        val screen = ScreenInstanceId("screen-1")
        val slot = StorageSlotKey(config, screen)
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
        private val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(FakeAdsSdkModule.create(controller).adapters),
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
        )
        val snapshotConfigs = AtomicReference(
            mapOf(
                config to configWith(
                    listOf(
                        item(true, 100, "old-100", 0),
                        item(true, 90, "new-90", 1),
                    ),
                ),
            ),
        )
        val scheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = {
                val configs = snapshotConfigs.get()
                AdsConfigSnapshot.create(
                    version = 1L,
                    configs = configs.mapValues { (_, adsConfig) ->
                        ResolvedConfig(
                            value = AdsConfigValue(adsConfig),
                            canonicalJson = """{"enable":true,"list_ads":[]}""",
                            origin = ConfigValueOrigin.BUNDLED,
                        )
                    },
                )
            },
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )

        fun activate(target: Int = 1) {
            scheduler.activate(slot, targetReadyCount = target, refillIfDeficit = false)
        }

        fun success(index: Int, adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(config.value, index, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
        }

        fun fail(index: Int, adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(config.value, index, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.FAIL),
            )
        }

        fun putSeed(
            objectId: String,
            weight: Int,
            adunit: String,
            listIndex: Int,
        ) {
            val put = storage.putReady(
                StoredAd(
                    objectId = ObjectId(objectId),
                    sourceConfigKey = config,
                    sourceListIndex = listIndex,
                    sourceType = AdFormat.NATIVE,
                    sourceAdunit = adunit,
                    sourceWeight = weight,
                    screenInstanceId = screen,
                    loadedAt = clock.nowMillis(),
                    state = AdSlotState.READY,
                    sdkHandle = object : SdkLoadedAdHandle {
                        override val format = AdFormat.NATIVE
                        override val adUnit = adunit
                        private val destroyed = AtomicBoolean(false)
                        override fun destroy() {
                            destroyed.set(true)
                        }
                    },
                ),
            )
            assertTrue(put is PutResult.Accepted)
        }

        fun configWith(items: List<OriginalAdItem>): OriginalAdsConfig =
            OriginalAdsConfig(enable = true, listAds = items)

        fun item(
            enableAd: Boolean,
            weight: Int,
            adunit: String,
            index: Int,
        ): OriginalAdItem = OriginalAdItem(
            enableAd = enableAd,
            weight = weight,
            adunit = adunit,
            sourceListIndex = index,
        )
    }
}
