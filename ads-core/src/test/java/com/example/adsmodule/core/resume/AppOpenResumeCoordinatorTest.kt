package com.example.adsmodule.core.resume

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.FullscreenLockAcquireRequest
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.lifecycle.AppOpenSuppressionReason
import com.example.adsmodule.core.lifecycle.ForegroundSessionTracker
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenario
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdFormat
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
class AppOpenResumeCoordinatorTest {
    @Test
    fun clearForeground_showsAppOpen() = runTest {
        val env = Env(this)
        env.success("appopen-100")
        env.lifecycle.bindSession(env.session)
        env.lifecycle.onBackground()
        val result = env.resume.tryShowResume()
        advanceUntilIdle()
        assertTrue(result is AppOpenResumeResult.Shown)
        assertEquals("appopen-100", (result as AppOpenResumeResult.Shown).storedAd.sourceAdunit)
    }

    @Test
    fun splashActive_suppresses() = runTest {
        val env = Env(this)
        env.success("appopen-100")
        env.lifecycle.bindSession(env.session)
        env.lifecycle.setSplashActive(true)
        val result = env.resume.tryShowResume()
        val suppressed = result as AppOpenResumeResult.Suppressed
        assertTrue(
            suppressed.suppression.reasons.contains(AppOpenSuppressionReason.SPLASH_ACTIVE),
        )
    }

    @Test
    fun clickToken_suppresses() = runTest {
        val env = Env(this)
        env.success("appopen-100")
        env.lifecycle.bindSession(env.session)
        env.lifecycle.onAdClick(ShowRequestId("click-1"))
        val result = env.resume.tryShowResume()
        val suppressed = result as AppOpenResumeResult.Suppressed
        assertTrue(
            suppressed.suppression.reasons.contains(AppOpenSuppressionReason.CLICK_TOKEN_PRESENT),
        )
    }

    @Test
    fun turnbackPending_suppresses() = runTest {
        val env = Env(this)
        env.success("appopen-100")
        env.lifecycle.bindSession(env.session)
        env.lifecycle.onAdClick(ShowRequestId("click-1"))
        env.lifecycle.onBackground()
        env.lifecycle.onForeground()
        val result = env.resume.tryShowResume()
        val suppressed = result as AppOpenResumeResult.Suppressed
        assertTrue(
            suppressed.suppression.reasons.contains(AppOpenSuppressionReason.TURNBACK_PENDING),
        )
    }

    @Test
    fun fullscreenLockBusy_suppressesOrRejects() = runTest {
        val env = Env(this)
        env.success("appopen-100")
        env.lifecycle.bindSession(env.session)
        env.lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("holder"),
                objectId = ObjectId("holder-obj"),
                sourceConfigKey = ConfigKey("inter_all_config_1"),
                screenInstanceId = null,
                format = AdFormat.INTERSTITIAL,
                kind = FullscreenAdKind.INTERSTITIAL,
            ),
        )
        val result = env.resume.tryShowResume()
        assertTrue(
            result is AppOpenResumeResult.Suppressed || result is AppOpenResumeResult.Rejected,
        )
        if (result is AppOpenResumeResult.Suppressed) {
            assertTrue(
                result.suppression.reasons.contains(AppOpenSuppressionReason.FULLSCREEN_LOCK_BUSY),
            )
        }
    }

    @Test
    fun onProcessForeground_afterBackground_showsWhenClear() = runTest {
        val env = Env(this)
        env.success("appopen-100")
        env.lifecycle.bindSession(env.session)
        env.resume.onProcessBackground()
        env.resume.onProcessForeground()
        advanceUntilIdle()
        val last = env.resume.lastShowResult.value
        assertTrue(last is AppOpenResumeResult.Shown)
    }

    private class Env(scope: TestScope) {
        val session = SessionId("resume-session")
        val screen = ScreenInstanceId("appopen-resume-1")
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
        private val key = AppOpenResumeKeys.APPOPEN_RESUME
        private val snapshotConfigs = AtomicReference(
            mapOf(
                key to OriginalAdsConfig(
                    enable = true,
                    isOrganic = true,
                    listAds = listOf(
                        OriginalAdItem(
                            enableAd = true,
                            weight = 100,
                            adunit = "appopen-100",
                        ),
                        OriginalAdItem(
                            enableAd = true,
                            weight = 90,
                            adunit = "appopen-90",
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
        val lock = GlobalFullscreenLock(clock = clock)
        private val fullscreen = FullscreenShowCoordinator(
            storage = storage,
            lock = lock,
            adapters = adapters,
            clock = clock,
            idGenerator = IdGenerator { "show-${idSeq.incrementAndGet()}" },
        )
        private val tokenStore = AdClickTokenStore(
            clock = clock,
            idGenerator = IdGenerator { "tok-${idSeq.incrementAndGet()}" },
        )
        val lifecycle = AdsLifecycleCoordinator(
            sessionTracker = ForegroundSessionTracker(clock = clock),
            tokenStore = tokenStore,
            fullscreenLock = lock,
            clock = clock,
            defaultClickTokenTtlMillis = 5_000L,
            scope = scope,
        )
        val resume = AppOpenResumeCoordinator(
            scope = scope,
            clock = clock,
            idGenerator = IdGenerator { "id-${idSeq.incrementAndGet()}" },
            lifecycle = lifecycle,
            loader = loader,
            storage = storage,
            fullscreen = fullscreen,
            refillScheduler = refillScheduler,
            snapshotProvider = snapshotProvider,
            audience = AudienceType.PAID,
            screenInstanceId = screen,
        )

        fun success(adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(key.value, 0, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
            controller.setScenario(
                FakeAdItemKey(key.value, 1, "appopen-90"),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
        }
    }
}
