package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.ReserveResult
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TurnbackRefillIntegrationTest {
    @Test
    fun turnbackBorrow_enqueuesImmediateWholeListRefill() = runTest {
        val env = Env(this)
        env.setList(
            env.item(true, 100, "old-100", 0),
            env.item(true, 90, "new-90", 1),
        )
        env.fail(0, "old-100")
        env.success(1, "new-90")
        env.putReady(
            objectId = "seed",
            weight = 100,
            adunit = "old-100",
            listIndex = 0,
        )
        env.activate()
        val token = env.tokenStore.issue(env.session, 1_000L)
        val borrowed = env.borrowService.borrow(env.session, token) as ReserveResult.Accepted
        assertEquals(ObjectId("seed"), borrowed.storedAd.objectId)
        assertEquals(100, borrowed.storedAd.sourceWeight)
        assertTrue(env.scheduler.isJobActive(env.slot))

        advanceUntilIdle()
        val refilled = env.storage.peekReady(env.config, env.screen)
        assertNotNull(refilled)
        assertEquals("new-90", refilled!!.sourceAdunit)
        assertEquals(90, refilled.sourceWeight)
        assertEquals(1, refilled.sourceListIndex)
        assertEquals(env.config, refilled.sourceConfigKey)
        assertEquals(env.screen, refilled.screenInstanceId)
        assertEquals(AdSlotState.READY, refilled.state)
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(env.config.value, 0, "old-100")))
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(env.config.value, 1, "new-90")))
    }

    @Test
    fun tieBreak_andMetadataSurviveBorrow() = runTest {
        val env = Env(this)
        env.setList(env.item(true, 10, "refill", 0))
        env.putReady(objectId = "later", weight = 50, loadedAt = 20L, adunit = "a")
        env.putReady(
            objectId = "earlier",
            weight = 50,
            loadedAt = 10L,
            adunit = "b",
            screen = ScreenInstanceId("other"),
        )
        env.activate()
        env.activate(StorageSlotKey(env.config, ScreenInstanceId("other")))
        env.success(0, "refill")
        val token = env.tokenStore.issue(env.session, 1_000L)
        val borrowed = env.borrowService.borrow(env.session, token) as ReserveResult.Accepted
        assertEquals(ObjectId("earlier"), borrowed.storedAd.objectId)
        assertEquals(50, borrowed.storedAd.sourceWeight)
        assertEquals(ScreenInstanceId("other"), borrowed.reservation.screenInstanceId)
        advanceUntilIdle()
    }

    @Test
    fun targetReadyCountGreaterThanOne_afterBorrow() = runTest {
        val env = Env(this)
        env.setList(env.item(true, 10, "unit", 0))
        env.success(0, "unit")
        env.putReady(objectId = "seed-1", weight = 10, adunit = "unit")
        env.putReady(objectId = "seed-2", weight = 5, adunit = "unit-2")
        env.activate(target = 2)
        env.storage.consume(ObjectId("seed-2"))
        assertEquals(1, env.storage.readyCount(env.slot))
        val token = env.tokenStore.issue(env.session, 1_000L)
        val borrowed = env.borrowService.borrow(env.session, token) as ReserveResult.Accepted
        assertEquals(ObjectId("seed-1"), borrowed.storedAd.objectId)
        advanceUntilIdle()
        assertEquals(2, env.storage.readyCount(env.slot))
    }

    private class Env(
        scope: TestScope,
    ) {
        val session = SessionId("session-1")
        val config = ConfigKey("native_language_config_1")
        val screen = ScreenInstanceId("screen-1")
        val slot = StorageSlotKey(config, screen)
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val idSeq = AtomicLong(0L)
        val clock = Clock { scope.testScheduler.currentTime }
        val tokenStore = AdClickTokenStore(
            clock = clock,
            idGenerator = IdGenerator { "token-${idSeq.incrementAndGet()}" },
        )
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
        private val listAds = AtomicReference(
            listOf(item(true, 100, "old-100", 0), item(true, 90, "new-90", 1)),
        )
        val scheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = {
                AdsConfigSnapshot.create(
                    version = 1L,
                    configs = mapOf(
                        config to ResolvedConfig(
                            value = AdsConfigValue(
                                OriginalAdsConfig(enable = true, listAds = listAds.get()),
                            ),
                            canonicalJson = """{"enable":true,"list_ads":[]}""",
                            origin = ConfigValueOrigin.BUNDLED,
                        ),
                    ),
                )
            },
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        val borrowService = AtomicBorrowService(tokenStore, storage, scheduler)

        fun setList(vararg items: OriginalAdItem) {
            listAds.set(items.toList())
        }

        fun activate(
            slotKey: StorageSlotKey = slot,
            target: Int = 1,
        ) {
            scheduler.activate(slotKey, targetReadyCount = target, refillIfDeficit = false)
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

        fun putReady(
            objectId: String,
            weight: Int,
            adunit: String,
            listIndex: Int = 0,
            loadedAt: Long = clock.nowMillis(),
            screen: ScreenInstanceId = this.screen,
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
                    loadedAt = loadedAt,
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
    }
}
