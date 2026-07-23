package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
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
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AtomicBorrowServiceTest {
    @Test
    fun noToken_rejectsBorrow() = runTest {
        val env = Env(this)
        env.putNative("n1", weight = 100)
        val result = env.borrowService.borrow(env.session, env.tokenStore.issue(env.session, 1_000L).let {
            env.tokenStore.invalidate(it)
            it
        })
        assertTrue(result is ReserveResult.Rejected)
        assertEquals(1, env.storage.readyCount(env.slot))
    }

    @Test
    fun formatNotEligible_rejectsAndReleasesToken() = runTest {
        val env = Env(this)
        env.storage.putReady(
            env.storedAd(
                objectId = "inter",
                format = AdFormat.INTERSTITIAL,
                weight = 100,
                configKey = ConfigKey("inter_splash_config_1"),
                screen = null,
            ),
        )
        val token = env.tokenStore.issue(env.session, 1_000L)
        val result = env.borrowService.borrow(env.session, token)
        assertTrue(result is ReserveResult.Rejected)
        // Token claim was released, so a later claim can succeed if inventory appears.
        assertTrue(env.tokenStore.claim(token, env.session) is ClaimResult.Accepted)
    }

    @Test
    fun highestWeight_isBorrowedAtomically() = runTest {
        val env = Env(this)
        env.putNative("low", weight = 10)
        env.putNative("high", weight = 90, screen = ScreenInstanceId("other"))
        env.activate(env.slot)
        env.activate(StorageSlotKey(env.config, ScreenInstanceId("other")))
        val token = env.tokenStore.issue(env.session, 1_000L)
        val accepted = env.borrowService.borrow(env.session, token) as ReserveResult.Accepted
        assertEquals(ObjectId("high"), accepted.storedAd.objectId)
        assertEquals(90, accepted.storedAd.sourceWeight)
        assertEquals(AdSlotState.RESERVED, accepted.storedAd.state)
        assertNull(env.storage.get(ObjectId("high"))?.takeIf { it.state == AdSlotState.READY })
        assertTrue(env.scheduler.isJobActive(StorageSlotKey(env.config, ScreenInstanceId("other"))))
    }

    @Test
    fun concurrentBorrow_onlyOneWinner() = runBlocking {
        val env = Env(TestScope())
        env.putNative("only", weight = 100)
        env.activate(env.slot)
        val tokens = (1..20).map { env.tokenStore.issue(env.session, 5_000L) }
        val results = tokens.map { token ->
            async(Dispatchers.Default) {
                env.borrowService.borrow(env.session, token)
            }
        }.awaitAll()
        assertEquals(1, results.filterIsInstance<ReserveResult.Accepted>().size)
        assertEquals(19, results.filterIsInstance<ReserveResult.Rejected>().size)
    }

    @Test
    fun normalReserve_doesNotUseGlobalTurnbackSelection() = runTest {
        val env = Env(this)
        val otherConfig = ConfigKey("native_language_dup_config_1")
        env.storage.putReady(
            env.storedAd(
                objectId = "other-high",
                configKey = otherConfig,
                screen = ScreenInstanceId("dup"),
                weight = 100,
            ),
        )
        env.putNative("local", weight = 10)
        val reserved = env.storage.reserveNormal(env.config, env.screen) as ReserveResult.Accepted
        assertEquals(ObjectId("local"), reserved.storedAd.objectId)
        assertEquals(1, env.storage.readyCount(otherConfig, ScreenInstanceId("dup")))
    }

    @Test
    fun raceNormalVsTurnback_singleWinner() = runBlocking {
        val env = Env(TestScope())
        env.putNative("only", weight = 100)
        env.activate(env.slot)
        val token = env.tokenStore.issue(env.session, 5_000L)
        val normal = async(Dispatchers.Default) {
            env.storage.reserveNormal(env.config, env.screen)
        }
        val turnback = async(Dispatchers.Default) {
            env.borrowService.borrow(env.session, token)
        }
        val results = listOf(normal.await(), turnback.await())
        assertEquals(1, results.filterIsInstance<ReserveResult.Accepted>().size)
        assertEquals(1, results.filterIsInstance<ReserveResult.Rejected>().size)
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
        val clock = object : Clock {
            override fun nowMillis(): Long = scope.testScheduler.currentTime
        }
        val tokenStore = AdClickTokenStore(
            clock = clock,
            idGenerator = IdGenerator { "token-${idSeq.incrementAndGet()}" },
        )
        val storage = AdStorage(
            clock = clock,
            idGenerator = IdGenerator { "res-${idSeq.incrementAndGet()}" },
        )
        val deficitStore = RefillDeficitStore()
        private val controller = FakeAdsSdkController(
            clock = FakeClock { scope.testScheduler.currentTime },
            dispatcher = dispatcher,
            objectIdGenerator = SequentialFakeObjectIdGenerator(),
        )
        private val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(FakeAdsSdkModule.create(controller).adapters),
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
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
                                OriginalAdsConfig(
                                    enable = true,
                                    listAds = listOf(
                                        OriginalAdItem(
                                            enableAd = true,
                                            weight = 1,
                                            adunit = "refill-unit",
                                            sourceListIndex = 0,
                                        ),
                                    ),
                                ),
                            ),
                            canonicalJson = """{"enable":true,"list_ads":[]}""",
                            origin = ConfigValueOrigin.BUNDLED,
                        ),
                    ),
                )
            },
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        val borrowService = AtomicBorrowService(
            tokenStore = tokenStore,
            storage = storage,
            refillScheduler = scheduler,
        )

        fun activate(slotKey: StorageSlotKey, target: Int = 1) {
            scheduler.activate(slotKey, targetReadyCount = target, refillIfDeficit = false)
        }

        fun putNative(
            objectId: String,
            weight: Int,
            screen: ScreenInstanceId = this.screen,
            configKey: ConfigKey = config,
        ) {
            val put = storage.putReady(
                storedAd(
                    objectId = objectId,
                    configKey = configKey,
                    screen = screen,
                    weight = weight,
                ),
            )
            assertTrue(put is PutResult.Accepted)
        }

        fun storedAd(
            objectId: String,
            configKey: ConfigKey = config,
            screen: ScreenInstanceId? = this.screen,
            format: AdFormat = AdFormat.NATIVE,
            weight: Int = 100,
        ): StoredAd = StoredAd(
            objectId = ObjectId(objectId),
            sourceConfigKey = configKey,
            sourceListIndex = 0,
            sourceType = format,
            sourceAdunit = "unit-$objectId",
            sourceWeight = weight,
            screenInstanceId = screen,
            loadedAt = clock.nowMillis(),
            state = AdSlotState.READY,
            sdkHandle = TrackingHandle(format, "unit-$objectId"),
        )
    }

    private class TrackingHandle(
        override val format: AdFormat,
        override val adUnit: String,
    ) : SdkLoadedAdHandle {
        private val destroyed = AtomicBoolean(false)
        override fun destroy() {
            destroyed.set(true)
        }
    }
}
