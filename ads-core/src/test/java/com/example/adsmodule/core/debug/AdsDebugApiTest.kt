package com.example.adsmodule.core.debug

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.config.InMemoryConfigDataSource
import com.example.adsmodule.core.config.InMemoryLastKnownGoodConfigStore
import com.example.adsmodule.core.config.OriginalRemoteConfigRepository
import com.example.adsmodule.core.config.bundledDataSource
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.lifecycle.ForegroundSessionTracker
import com.example.adsmodule.core.lifecycle.LifecycleSimulatorApi
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.core.turnback.AtomicBorrowService
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsDebugApiTest {
    @Test
    fun dashboard_updatesWhenNavigationAndStorageChange() = runTest {
        val env = Env(this)
        try {
            env.api.reportNavigation(
                activityName = "SplashActivity",
                screenLabel = "Splash",
            )
            assertEquals("SplashActivity", env.api.dashboard.value.navigation.activityName)

            assertTrue(
                env.storage.putReady(
                    StoredAd(
                        objectId = ObjectId("ready-1"),
                        sourceConfigKey = ConfigKey("native_language_config_1"),
                        sourceListIndex = 0,
                        sourceType = AdFormat.NATIVE,
                        sourceAdunit = "unit-a",
                        sourceWeight = 100,
                        screenInstanceId = ScreenInstanceId("LANG#1"),
                        loadedAt = 1L,
                        state = AdSlotState.READY,
                        sdkHandle = Handle(),
                    ),
                ) is PutResult.Accepted,
            )
            env.api.log("storage", "put ready")
            env.api.refreshDashboard()
            assertEquals(1, env.api.dashboard.value.readyObjectCount)
            assertNotNull(env.api.dashboard.value.latestEvent)
        } finally {
            env.api.stop()
        }
    }

    @Test
    fun configOverride_andRefresh_goThroughPublicApi() = runTest {
        val env = Env(this)
        try {
            env.repository.refresh()

            val key = ConfigKey("native_language_config_1")
            env.api.writeConfigOverride(
                key,
                """
                {
                  "enable": true,
                  "isOrganic": true,
                  "list_ads": [
                    {"enable_ad": true, "weight": 77, "adunit": "debug-unit"}
                  ]
                }
                """.trimIndent(),
            )
            env.api.refreshConfig()
            val placements = env.api.placements()
            val language = placements.first { it.configKey == key }
            assertEquals(77, language.originalItems.single().weight)
            assertEquals("debug-unit", language.runtimeOrder.single().adunit)
        } finally {
            env.api.stop()
        }
    }

    @Test
    fun turnbackPreview_usesEligibleReadyNative() = runTest {
        val env = Env(this)
        try {
            env.storage.putReady(
                StoredAd(
                    objectId = ObjectId("tb-1"),
                    sourceConfigKey = ConfigKey("native_language_config_1"),
                    sourceListIndex = 0,
                    sourceType = AdFormat.NATIVE,
                    sourceAdunit = "unit-a",
                    sourceWeight = 40,
                    screenInstanceId = ScreenInstanceId("LANG#1"),
                    loadedAt = 1L,
                    state = AdSlotState.READY,
                    sdkHandle = Handle(),
                ),
            )
            env.storage.putReady(
                StoredAd(
                    objectId = ObjectId("tb-2"),
                    sourceConfigKey = ConfigKey("native_language_dup_config_1"),
                    sourceListIndex = 0,
                    sourceType = AdFormat.NATIVE,
                    sourceAdunit = "unit-b",
                    sourceWeight = 90,
                    screenInstanceId = ScreenInstanceId("LANG_DUP#1"),
                    loadedAt = 2L,
                    state = AdSlotState.READY,
                    sdkHandle = Handle(),
                ),
            )
            val winner = env.api.previewTurnback().first()
            assertEquals("tb-2", winner.objectId.value)
            assertEquals(90, winner.sourceWeight)
        } finally {
            env.api.stop()
        }
    }

    private class Env(scope: TestScope) {
        private val idSeq = AtomicLong(0L)
        val clock = Clock { 1_000L }
        val storage = AdStorage(
            clock = clock,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
        )
        val current = InMemoryConfigDataSource()
        val repository = OriginalRemoteConfigRepository(
            currentDataSource = current,
            bundledDataSource = bundledDataSource(),
            lastKnownGoodStore = InMemoryLastKnownGoodConfigStore(),
        )
        private val adapters = AdSdkAdapterRegistry.create(
            FakeAdsSdkModule.create().adapters,
        )
        val loader = WeightedListLoader(
            adapterRegistry = adapters,
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
        )
        val deficitStore = RefillDeficitStore()
        val refillScheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = { repository.snapshots.value },
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        val lock = GlobalFullscreenLock(clock = clock)
        val tokenStore = AdClickTokenStore(clock = clock, idGenerator = IdGenerator {
            "tok-${idSeq.incrementAndGet()}"
        })
        val lifecycle = AdsLifecycleCoordinator(
            sessionTracker = ForegroundSessionTracker(clock = clock),
            tokenStore = tokenStore,
            fullscreenLock = lock,
            clock = clock,
            defaultClickTokenTtlMillis = 30_000L,
            scope = scope,
        ).also { it.bindSession(SessionId("debug-session")) }
        val lifecycleSimulator = LifecycleSimulatorApi(
            lifecycle = lifecycle,
            fullscreenLock = lock,
            tokenStore = tokenStore,
            clock = clock,
        )
        val borrow = AtomicBorrowService(
            tokenStore = tokenStore,
            storage = storage,
            refillScheduler = refillScheduler,
        )
        val api = AdsDebugApi(
            scope = scope.backgroundScope,
            clock = clock,
            storage = storage,
            loader = loader,
            deficitStore = deficitStore,
            refillScheduler = refillScheduler,
            fullscreenLock = lock,
            lifecycleSimulator = lifecycleSimulator,
            configRepository = repository,
            currentConfigDataSource = current,
            tokenStore = tokenStore,
            borrowService = borrow,
        )
    }

    private class Handle : SdkLoadedAdHandle {
        private val destroyed = AtomicBoolean(false)
        override val format: AdFormat = AdFormat.NATIVE
        override val adUnit: String = "unit"
        override fun destroy() {
            destroyed.set(true)
        }
    }
}
