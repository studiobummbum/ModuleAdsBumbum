package com.example.adsmodule.core.language

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.OnboardingScreenInstances
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageFlowCoordinatorTest {
    @Test
    fun loadingTimer_doesNotOpenSelectBefore2000ms() = runTest {
        val env = Env(this)
        env.coordinator.startOrAttach(env.snapshot())
        advanceTimeBy(1_999L)
        runCurrent()
        assertEquals(
            null,
            env.coordinator.snapshot.value?.pendingEffect,
        )
        assertFalse(env.coordinator.snapshot.value!!.loadingTimer.completed)
    }

    @Test
    fun startOrAttachWithoutSession_resetsAfterFlowAdvanced() = runTest {
        val env = Env(this)
        val first = env.coordinator.startOrAttach(env.snapshot())
        advanceTimeBy(2_000L)
        runCurrent()
        env.coordinator.claimEffect(first, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
        env.coordinator.onLanguageSelectOpened(first)
        assertEquals(LanguageStage.LANGUAGE_SELECT, env.coordinator.snapshot.value!!.stage)

        val second = env.coordinator.startOrAttach(env.snapshot(), existingSessionId = null)
        assertTrue(second != first)
        assertEquals(LanguageStage.LANGUAGE_LOADING, env.coordinator.snapshot.value!!.stage)
        assertFalse(env.coordinator.snapshot.value!!.loadingTimer.completed)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(
            LanguageNavigationEffect.OPEN_LANGUAGE_SELECT,
            env.coordinator.snapshot.value!!.pendingEffect,
        )
    }

    @Test
    fun loadingTimer_opensSelectAt2000ms_evenIfAdsFail() = runTest {
        val env = Env(this)
        env.fail(LanguageConfigKeys.LOADING, 0, "load-100")
        env.fail(LanguageConfigKeys.LOADING, 1, "load-90")
        env.coordinator.startOrAttach(env.snapshot())
        advanceTimeBy(2_000L)
        runCurrent()
        val snap = env.coordinator.snapshot.value!!
        assertTrue(snap.loadingTimer.completed)
        assertEquals(LanguageNavigationEffect.OPEN_LANGUAGE_SELECT, snap.pendingEffect)
        assertTrue(
            env.coordinator.claimEffect(
                snap.sessionId,
                LanguageNavigationEffect.OPEN_LANGUAGE_SELECT,
            ),
        )
        assertFalse(
            env.coordinator.claimEffect(
                snap.sessionId,
                LanguageNavigationEffect.OPEN_LANGUAGE_SELECT,
            ),
        )
    }

    @Test
    fun exactActivityOrder_andNextExactlyOnce() = runTest {
        val env = Env(this)
        val sessionId = env.coordinator.startOrAttach(env.snapshot())
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(
            env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT),
        )
        env.coordinator.onLanguageSelectOpened(sessionId)
        assertTrue(
            env.coordinator.selectLanguage(sessionId, DemoLanguages.find("vi")!!),
        )
        assertTrue(
            env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_DUP),
        )
        assertFalse(
            env.coordinator.selectLanguage(sessionId, DemoLanguages.find("en")!!),
        )
        env.coordinator.onLanguageDupOpened(sessionId)
        assertEquals("vi", env.coordinator.snapshot.value!!.selectedLanguage!!.tag)
        assertTrue(env.coordinator.onLanguageDupNext(sessionId))
        assertTrue(
            env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_APPLY_LANGUAGE),
        )
        assertFalse(env.coordinator.onLanguageDupNext(sessionId))
        env.coordinator.onApplyLanguageOpened(sessionId)
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertTrue(
            env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_ONBOARDING),
        )
        assertFalse(
            env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_ONBOARDING),
        )
        env.coordinator.onOnboardingOpened(sessionId)
        assertEquals(LanguageStage.ONBOARDING, env.coordinator.snapshot.value!!.stage)
    }

    @Test
    fun selectionSurvivesRecreationAttach() = runTest {
        val env = Env(this)
        val sessionId = env.coordinator.startOrAttach(env.snapshot())
        advanceTimeBy(2_000L)
        runCurrent()
        env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
        env.coordinator.onLanguageSelectOpened(sessionId)
        env.coordinator.selectLanguage(sessionId, DemoLanguages.find("fr")!!)
        env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_DUP)
        env.coordinator.onLanguageDupOpened(sessionId)

        val reattached = env.coordinator.startOrAttach(env.snapshot(), sessionId)
        assertEquals(sessionId, reattached)
        env.coordinator.restoreSelectedLanguage(sessionId, "fr")
        assertEquals("fr", env.coordinator.snapshot.value!!.selectedLanguage!!.tag)
        assertEquals(LanguageStage.LANGUAGE_DUP, env.coordinator.snapshot.value!!.stage)
    }

    @Test
    fun applyDelay_requiresMinimumTwoSeconds_andLocaleSuccess() = runTest {
        val env = Env(this)
        env.localeApplier.result = LocaleApplyResult.Success
        val sessionId = env.runToApply()
        advanceTimeBy(1_999L)
        runCurrent()
        assertEquals(
            null,
            env.coordinator.snapshot.value?.pendingEffect,
        )
        advanceTimeBy(1L)
        advanceUntilIdle()
        assertEquals(
            LanguageNavigationEffect.OPEN_ONBOARDING,
            env.coordinator.snapshot.value!!.pendingEffect,
        )
        assertEquals(LocaleApplyStatus.SUCCEEDED, env.coordinator.snapshot.value!!.localeStatus)
        assertEquals(sessionId, env.coordinator.snapshot.value!!.sessionId)
    }

    @Test
    fun applyLocaleFailure_fallsBackAndStillNavigates() = runTest {
        val env = Env(this)
        env.localeApplier.result = LocaleApplyResult.Failure("boom")
        env.runToApply()
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertEquals(LocaleApplyStatus.FAILED_FALLBACK, env.coordinator.snapshot.value!!.localeStatus)
        assertEquals(
            LanguageNavigationEffect.OPEN_ONBOARDING,
            env.coordinator.snapshot.value!!.pendingEffect,
        )
    }

    @Test
    fun applyRecreation_doesNotRestartTimer() = runTest {
        val env = Env(this)
        env.localeApplier.result = LocaleApplyResult.Success
        val sessionId = env.runToApply()
        advanceTimeBy(1_000L)
        runCurrent()
        val deadline = env.coordinator.snapshot.value!!.applyTimer.deadlineMillis
        assertNotNull(deadline)
        env.coordinator.onApplyLanguageOpened(sessionId)
        assertEquals(deadline, env.coordinator.snapshot.value!!.applyTimer.deadlineMillis)
        advanceTimeBy(1_000L)
        advanceUntilIdle()
        assertEquals(
            LanguageNavigationEffect.OPEN_ONBOARDING,
            env.coordinator.snapshot.value!!.pendingEffect,
        )
    }

    @Test
    fun correctPlacementPerActivity_andDistinctOnboardingPreload() = runTest {
        val env = Env(this)
        val sessionId = env.coordinator.startOrAttach(env.snapshot())
        advanceUntilIdle()
        val loading = env.coordinator.boundAd(LanguagePlacement.LOADING)
        assertEquals(LanguageConfigKeys.LOADING, loading?.session?.configKey)

        advanceTimeBy(2_000L)
        runCurrent()
        env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
        env.coordinator.onLanguageSelectOpened(sessionId)
        advanceUntilIdle()
        assertEquals(
            LanguageConfigKeys.SELECT,
            env.coordinator.boundAd(LanguagePlacement.SELECT)?.session?.configKey,
        )

        env.coordinator.selectLanguage(sessionId, DemoLanguages.find("en")!!)
        env.coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_DUP)
        env.coordinator.onLanguageDupOpened(sessionId)
        advanceUntilIdle()
        assertEquals(
            LanguageConfigKeys.DUP,
            env.coordinator.boundAd(LanguagePlacement.DUP)?.session?.configKey,
        )
        assertTrue(env.coordinator.snapshot.value!!.onboardingPreloadStarted)
        assertNotEquals(
            OnboardingScreenInstances.page1,
            OnboardingScreenInstances.page2,
        )
        assertNotNull(
            env.storage.peekReady(
                LanguageConfigKeys.ONBOARDING,
                OnboardingScreenInstances.page1,
            ) ?: env.normalAds.slotState(
                LanguageConfigKeys.ONBOARDING,
                OnboardingScreenInstances.page1,
            ),
        )
        assertNotEquals(
            env.normalAds.slotState(
                LanguageConfigKeys.ONBOARDING,
                OnboardingScreenInstances.page1,
            ).screenInstanceId,
            env.normalAds.slotState(
                LanguageConfigKeys.ONBOARDING,
                OnboardingScreenInstances.page2,
            ).screenInstanceId,
        )
    }

    @Test
    fun earlyPreload_usesSameScreenIdsAsStartOrAttach() = runTest {
        val env = Env(this)
        val preloadSession = env.coordinator.ensureLanguagePreload(env.snapshot())
        advanceUntilIdle()
        val started = env.coordinator.startOrAttach(env.snapshot())
        assertEquals(preloadSession, started)
        val snap = env.coordinator.snapshot.value!!
        assertTrue(snap.languagePreloadStarted)
        assertTrue(
            env.storage.readyCount(LanguageConfigKeys.LOADING, snap.loadingScreenId) >= 0,
        )
    }

    private class Env(
        private val scope: TestScope,
        audience: AudienceType = AudienceType.PAID,
    ) {
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
                LanguageConfigKeys.LOADING to ads(true, listOf("load-100", "load-90")),
                LanguageConfigKeys.SELECT to ads(false, listOf("lang-100", "lang-90")),
                LanguageConfigKeys.DUP to ads(true, listOf("dup-100", "dup-90")),
                LanguageConfigKeys.ONBOARDING to ads(true, listOf("onb-100", "onb-90")),
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
        val scheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = deficitStore,
            snapshotProvider = snapshotProvider,
            idGenerator = IdGenerator { "refill-${idSeq.incrementAndGet()}" },
        )
        val normalAds = NormalScreenAdCoordinator(
            scope = scope,
            clock = clock,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
            loader = loader,
            storage = storage,
            refillScheduler = scheduler,
            snapshotProvider = snapshotProvider,
            audience = audience,
        )
        val localeApplier = ControllableLocaleApplier(scope)
        val coordinator = LanguageFlowCoordinator(
            scope = scope,
            clock = clock,
            idGenerator = IdGenerator { "lang-${idSeq.incrementAndGet()}" },
            normalAds = normalAds,
            localeApplier = localeApplier,
        )

        init {
            success(LanguageConfigKeys.LOADING, 0, "load-100")
            success(LanguageConfigKeys.SELECT, 0, "lang-100")
            success(LanguageConfigKeys.DUP, 0, "dup-100")
            success(LanguageConfigKeys.ONBOARDING, 0, "onb-100")
        }

        fun snapshot(): AdsConfigSnapshot = snapshotProvider()

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

        suspend fun runToApply(): com.example.adsmodule.core.LanguageSessionId {
            val sessionId = coordinator.startOrAttach(snapshot())
            scope.advanceTimeBy(2_000L)
            scope.runCurrent()
            coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
            coordinator.onLanguageSelectOpened(sessionId)
            coordinator.selectLanguage(sessionId, DemoLanguages.find("vi")!!)
            coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_DUP)
            coordinator.onLanguageDupOpened(sessionId)
            coordinator.onLanguageDupNext(sessionId)
            coordinator.claimEffect(sessionId, LanguageNavigationEffect.OPEN_APPLY_LANGUAGE)
            coordinator.onApplyLanguageOpened(sessionId)
            return sessionId
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

    private class ControllableLocaleApplier(
        @Suppress("unused") private val scope: TestScope,
    ) : LocaleApplier {
        var result: LocaleApplyResult = LocaleApplyResult.Success

        override suspend fun apply(languageTag: String): LocaleApplyResult = result
    }
}
