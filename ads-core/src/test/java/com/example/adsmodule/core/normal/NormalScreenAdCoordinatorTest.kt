package com.example.adsmodule.core.normal

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
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.StorageSlotKey
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NormalScreenAdCoordinatorTest {
    @Test
    fun ensureLoaded_preservesSourceMetadataAndExactSlot() = runTest {
        val env = Env(this)
        env.success(env.languageKey, 0, "lang-100")
        val result = env.coordinator.ensureLoaded(env.languageKey, env.languageScreen)
        val ready = result as NormalScreenEnsureResult.Ready
        assertEquals(NormalScreenLoadStatus.READY, ready.state.status)
        assertEquals(env.languageKey, ready.state.storedAd!!.sourceConfigKey)
        assertEquals(env.languageScreen, ready.state.storedAd!!.screenInstanceId)
        assertEquals(0, ready.state.storedAd!!.sourceListIndex)
        assertEquals(100, ready.state.storedAd!!.sourceWeight)
        assertEquals("lang-100", ready.state.storedAd!!.sourceAdunit)
    }

    @Test
    fun bind_reservesExactSlot_andRequestsRefill() = runTest {
        val env = Env(this)
        env.success(env.languageKey, 0, "lang-100")
        env.success(env.languageKey, 1, "lang-90")
        val bound = env.coordinator.bind(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        assertEquals(NormalScreenLoadStatus.BOUND, bound.state.status)
        assertEquals(env.languageKey, bound.session.storedAd.sourceConfigKey)
        assertEquals(env.languageScreen, bound.session.storedAd.screenInstanceId)
        assertEquals(0, env.storage.readyCount(env.languageKey, env.languageScreen))
        advanceUntilIdle()
        assertEquals(1, env.storage.readyCount(env.languageKey, env.languageScreen))
        val refilled = env.storage.peekReady(env.languageKey, env.languageScreen)
        assertNotNull(refilled)
        assertNotEquals(bound.session.objectId, refilled!!.objectId)
    }

    @Test
    fun bind_doesNotBorrowOtherConfigOrScreen() = runTest {
        val env = Env(this)
        env.success(env.languageKey, 0, "lang-100")
        env.success(env.dupKey, 0, "dup-100")
        env.coordinator.ensureLoaded(env.languageKey, env.languageScreen)
        env.coordinator.ensureLoaded(env.dupKey, env.dupScreen)

        val bound = env.coordinator.bind(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        assertEquals(env.languageKey, bound.session.configKey)
        assertEquals(env.languageScreen, bound.session.screenInstanceId)
        assertEquals("lang-100", bound.session.storedAd.sourceAdunit)
        assertNotNull(env.storage.peekReady(env.dupKey, env.dupScreen))

        val dupBound = env.coordinator.bind(env.dupKey, env.dupScreen)
            as NormalScreenBindResult.Bound
        assertEquals("dup-100", dupBound.session.storedAd.sourceAdunit)
        assertEquals(env.dupScreen, dupBound.session.screenInstanceId)
    }

    @Test
    fun ensureActivated_isIdempotent() = runTest {
        val env = Env(this)
        assertTrue(env.coordinator.ensureActivated(env.languageKey, env.languageScreen))
        assertFalse(env.coordinator.ensureActivated(env.languageKey, env.languageScreen))
        val gen1 = env.deficitStore.generation(
            StorageSlotKey(env.languageKey, env.languageScreen),
        )
        assertFalse(env.coordinator.ensureActivated(env.languageKey, env.languageScreen))
        val gen2 = env.deficitStore.generation(
            StorageSlotKey(env.languageKey, env.languageScreen),
        )
        assertEquals(gen1, gen2)
    }

    @Test
    fun unbindConsume_finishesOnce() = runTest {
        val env = Env(this)
        env.success(env.languageKey, 0, "lang-100")
        val bound = env.coordinator.bind(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        val first = env.coordinator.unbind(bound.session, NormalScreenUnbindMode.CONSUME)
        val second = env.coordinator.unbind(bound.session, NormalScreenUnbindMode.CONSUME)
        assertTrue(first is NormalScreenUnbindResult.Consumed)
        assertTrue(second is NormalScreenUnbindResult.AlreadyFinished)
        assertEquals(
            com.example.adsmodule.core.AdSlotState.CONSUMED,
            env.storage.get(bound.session.objectId)?.state,
        )
    }

    @Test
    fun organicAudience_marksLanguageSelectIneligible() = runTest {
        val env = Env(this, audience = AudienceType.ORGANIC)
        val result = env.coordinator.ensureLoaded(env.languageKey, env.languageScreen)
        val terminal = result as NormalScreenEnsureResult.Terminal
        assertEquals(NormalScreenLoadStatus.INELIGIBLE, terminal.state.status)
    }

    @Test
    fun failureFallsBackToNextItem() = runTest {
        val env = Env(this)
        env.fail(env.languageKey, 0, "lang-100")
        env.success(env.languageKey, 1, "lang-90")
        val result = env.coordinator.ensureLoaded(env.languageKey, env.languageScreen)
            as NormalScreenEnsureResult.Ready
        assertEquals("lang-90", result.state.storedAd!!.sourceAdunit)
        assertEquals(90, result.state.storedAd!!.sourceWeight)
        assertEquals(1, result.state.storedAd!!.sourceListIndex)
    }

    @Test
    fun replaceBoundIfReady_keepsOldShowingUntilConsumedAfterSwap() = runTest {
        val env = Env(this)
        env.success(env.languageKey, 0, "lang-100")
        env.success(env.languageKey, 1, "lang-90")
        val first = env.coordinator.bind(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        advanceUntilIdle()
        assertNotNull(env.storage.peekReady(env.languageKey, env.languageScreen))

        val replaced = env.coordinator.replaceBoundIfReady(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        assertNotNull(replaced.previousSession)
        assertEquals(first.session.objectId, replaced.previousSession!!.objectId)
        assertNotEquals(first.session.objectId, replaced.session.objectId)
        // Old object stays SHOWING until caller consumes after UI swap.
        assertEquals(
            com.example.adsmodule.core.AdSlotState.SHOWING,
            env.storage.get(first.session.objectId)?.state,
        )
        assertEquals(
            com.example.adsmodule.core.AdSlotState.SHOWING,
            env.storage.get(replaced.session.objectId)?.state,
        )

        val consumed = env.coordinator.unbind(
            replaced.previousSession!!,
            NormalScreenUnbindMode.CONSUME,
        )
        assertTrue(consumed is NormalScreenUnbindResult.Consumed)
        assertEquals(
            com.example.adsmodule.core.AdSlotState.CONSUMED,
            env.storage.get(first.session.objectId)?.state,
        )
    }

    @Test
    fun replaceBoundIfReady_withoutNewerReady_returnsCurrentBound() = runTest {
        val env = Env(this)
        env.success(env.languageKey, 0, "lang-100")
        val first = env.coordinator.bind(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        // Call before refill settles so no newer READY exists yet.
        val again = env.coordinator.replaceBoundIfReady(env.languageKey, env.languageScreen)
            as NormalScreenBindResult.Bound
        assertEquals(first.session.objectId, again.session.objectId)
        assertEquals(null, again.previousSession)
    }

    private class Env(
        scope: TestScope,
        audience: AudienceType = AudienceType.PAID,
    ) {
        val languageKey = ConfigKey("native_language_config_1")
        val dupKey = ConfigKey("native_language_dup_config_1")
        val loadingKey = ConfigKey("native_language_loading_config_1")
        val languageScreen = ScreenInstanceId("language-select-1")
        val dupScreen = ScreenInstanceId("language-dup-1")
        val loadingScreen = ScreenInstanceId("language-loading-1")

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
            adapterRegistry = AdSdkAdapterRegistry.create(
                FakeAdsSdkModule.create(controller).adapters,
            ),
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
        )
        private val snapshotConfigs = AtomicReference(
            mapOf(
                languageKey to ads(isOrganic = false, units = listOf("lang-100", "lang-90")),
                dupKey to ads(isOrganic = true, units = listOf("dup-100", "dup-90")),
                loadingKey to ads(isOrganic = true, units = listOf("load-100", "load-90")),
            ),
        )
        val scheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = {
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
            },
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        val coordinator = NormalScreenAdCoordinator(
            scope = scope,
            clock = clock,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
            loader = loader,
            storage = storage,
            refillScheduler = scheduler,
            snapshotProvider = {
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
            },
            audience = audience,
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
        ): OriginalAdsConfig = OriginalAdsConfig(
            enable = true,
            isOrganic = isOrganic,
            listAds = units.mapIndexed { index, unit ->
                OriginalAdItem(
                    enableAd = true,
                    weight = 100 - index * 10,
                    adunit = unit,
                )
            },
        )
    }
}
