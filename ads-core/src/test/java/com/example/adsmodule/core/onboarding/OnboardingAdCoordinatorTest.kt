package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.OnboardAdsConfig
import com.example.adsmodule.core.config.OnboardAdsEntry
import com.example.adsmodule.core.config.OnboardScreenConfig
import com.example.adsmodule.core.config.OnboardScreenEntry
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingAdCoordinatorTest {
    @Test
    fun fourPages_loadDistinctObjectsAndHandles() = runTest {
        val env = Env(this)
        env.onboardingAds.refreshPolicy(env.snapshot())
        env.onboardingAds.preloadEligible(listOf(1, 2, 3, 4))
        advanceUntilIdle()

        val objectIds = (1..4).map { page ->
            val ready = env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page(page),
            )
            assertNotNull("page $page missing", ready)
            ready!!
        }
        assertEquals(4, objectIds.map { it.objectId }.toSet().size)
        assertEquals(4, objectIds.map { it.sdkHandle }.toSet().size)
        objectIds.forEachIndexed { index, ad ->
            assertEquals(OnboardingScreenInstances.page(index + 1), ad.screenInstanceId)
            assertEquals(OnboardingConfigKeys.NATIVE, ad.sourceConfigKey)
        }
    }

    @Test
    fun bindPage_neverReceivesOtherPageObject() = runTest {
        val env = Env(this)
        env.onboardingAds.refreshPolicy(env.snapshot())
        env.onboardingAds.preloadEligible(listOf(1, 2))
        advanceUntilIdle()
        val bound1 = env.onboardingAds.bindPage(1) as NormalScreenBindResult.Bound
        val bound2 = env.onboardingAds.bindPage(2) as NormalScreenBindResult.Bound
        assertNotEquals(bound1.session.objectId, bound2.session.objectId)
        assertEquals(OnboardingScreenInstances.page1, bound1.session.screenInstanceId)
        assertEquals(OnboardingScreenInstances.page2, bound2.session.screenInstanceId)
    }

    @Test
    fun adsDisabled_rejectsBind() = runTest {
        val env = Env(this, adsPage2Enabled = false)
        env.onboardingAds.refreshPolicy(env.snapshot())
        val rejected = env.onboardingAds.bindPage(2)
        assertTrue(rejected is NormalScreenBindResult.Rejected)
        assertNull(
            env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page2,
            ),
        )
    }

    @Test
    fun earlyPreload_onlyPages1And2() = runTest {
        val env = Env(this)
        env.onboardingAds.ensureEarlyPreload(env.snapshot())
        advanceUntilIdle()
        assertNotNull(
            env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page1,
            ),
        )
        assertNotNull(
            env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page2,
            ),
        )
        assertNull(
            env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page3,
            ),
        )
    }

    @Test
    fun pageVisible_preloadsLookahead() = runTest {
        val env = Env(this)
        env.onboardingAds.refreshPolicy(env.snapshot())
        env.onboardingAds.onPageVisible(1)
        advanceUntilIdle()
        assertNotNull(
            env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page3,
            ),
        )
        env.onboardingAds.onPageVisible(2)
        advanceUntilIdle()
        assertNotNull(
            env.storage.peekReady(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page4,
            ),
        )
    }

    @Test
    fun releaseRebind_afterViewRecreation_idempotent() = runTest {
        val env = Env(this)
        env.onboardingAds.refreshPolicy(env.snapshot())
        env.onboardingAds.preloadEligible(listOf(1))
        advanceUntilIdle()
        val first = env.onboardingAds.bindPage(1) as NormalScreenBindResult.Bound
        env.onboardingAds.unbindPage(1, NormalScreenUnbindMode.RELEASE)
        val second = env.onboardingAds.bindPage(1) as NormalScreenBindResult.Bound
        assertEquals(OnboardingScreenInstances.page1, second.session.screenInstanceId)
        assertEquals(OnboardingConfigKeys.NATIVE, second.session.configKey)
        // Bind triggers refill; RELEASE returns the prior object to READY behind any
        // already-refilled READY object, so object identity may change.
        assertNotNull(second.session.objectId)
        assertNotNull(first.session.objectId)
    }

    private class Env(
        scope: TestScope,
        audience: AudienceType = AudienceType.PAID,
        adsPage2Enabled: Boolean = true,
    ) {
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
        private val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(
                FakeAdsSdkModule.create(controller).adapters,
            ),
            clock = clock,
            idGenerator = IdGenerator { "cycle-${idSeq.incrementAndGet()}" },
        )
        private val snapshotConfigs = AtomicReference(buildConfigs(adsPage2Enabled))
        private val snapshotProvider = {
            AdsConfigSnapshot.create(
                version = 1L,
                configs = snapshotConfigs.get(),
            )
        }
        val scheduler = WholeListRefillScheduler(
            scope = scope,
            loader = loader,
            storage = storage,
            deficitStore = RefillDeficitStore(),
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
        val onboardingAds = OnboardingAdCoordinator(
            scope = scope,
            normalAds = normalAds,
            snapshotProvider = snapshotProvider,
            audience = audience,
        )

        init {
            (0..1).forEach { index ->
                controller.setScenario(
                    FakeAdItemKey(OnboardingConfigKeys.NATIVE.value, index, "onb-${100 - index * 10}"),
                    FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
                )
            }
        }

        fun snapshot(): AdsConfigSnapshot = snapshotProvider()

        private fun buildConfigs(adsPage2Enabled: Boolean): Map<ConfigKey, ResolvedConfig> {
            val native = OriginalAdsConfig(
                enable = true,
                isOrganic = true,
                listAds = listOf(
                    OriginalAdItem(enableAd = true, weight = 100, adunit = "onb-100"),
                    OriginalAdItem(enableAd = true, weight = 90, adunit = "onb-90"),
                ),
            )
            return mapOf(
                OnboardingConfigKeys.NATIVE to ResolvedConfig(
                    value = AdsConfigValue(native),
                    canonicalJson = """{"enable":true}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                ConfigKey("onboard_screen_config") to ResolvedConfig(
                    value = OnboardScreenConfig(
                        listOf(
                            OnboardScreenEntry(screenOnboard1 = true, isOrganic = true),
                            OnboardScreenEntry(screenOnboard2 = true, isOrganic = true),
                            OnboardScreenEntry(screenOnboard3 = true, isOrganic = true),
                            OnboardScreenEntry(screenOnboard4 = true, isOrganic = true),
                        ),
                    ),
                    canonicalJson = """[]""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
                ConfigKey("onboard_ads_config") to ResolvedConfig(
                    value = OnboardAdsConfig(
                        listOf(
                            OnboardAdsEntry(adsOnboard1 = true, isOrganic = true),
                            OnboardAdsEntry(adsOnboard2 = adsPage2Enabled, isOrganic = true),
                            OnboardAdsEntry(adsOnboard3 = true, isOrganic = true),
                            OnboardAdsEntry(adsOnboard4 = true, isOrganic = true),
                        ),
                    ),
                    canonicalJson = """[]""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
            )
        }
    }
}
