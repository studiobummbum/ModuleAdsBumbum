package com.example.adsmodule.core.splash

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.BooleanConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.FullScreenTimingConfig
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.core.config.SplashScreenConfig
import com.example.adsmodule.core.config.SplashSkipConfig
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.lifecycle.ForegroundSessionTracker
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenario
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashFlowCoordinatorTest {
    @Test
    fun readyDoesNotStartSkipTimer() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "inter-0"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 5_000L,
                dismissDelayMillis = 60_000L,
            ),
        )
        env.coordinator.startOrAttach(env.snapshot())
        advanceTimeBy(100L)
        runCurrent()
        val snap = env.coordinator.snapshot.value
        assertNotNull(snap)
        assertEquals(SplashSkipTimerState.NOT_STARTED, snap!!.skipTimer.state)
        env.coordinator.cancelNow()
        advanceUntilIdle()
    }

    @Test
    fun interstitialShownStartsSkipTimerAndOpensNativeFull() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "inter-0"),
            FakeScenarioConfig(dismissDelayMillis = 60_000L),
        )
        env.coordinator.startOrAttach(env.snapshot())
        var guard = 0
        while (
            env.coordinator.snapshot.value?.skipTimer?.state != SplashSkipTimerState.RUNNING &&
            guard < 200
        ) {
            runCurrent()
            advanceTimeBy(1L)
            guard++
        }
        val afterShow = env.coordinator.snapshot.value
        assertEquals(SplashSkipTimerState.RUNNING, afterShow!!.skipTimer.state)

        advanceTimeBy(8_000L)
        runCurrent()
        val afterSkip = env.coordinator.snapshot.value
        assertEquals(SplashStage.NATIVE_FULL, afterSkip!!.stage)
        assertTrue(
            env.coordinator.claimEffect(
                afterSkip.sessionId,
                SplashNavigationEffect.OPEN_NATIVE_FULL,
            ),
        )
        assertFalse(
            env.coordinator.claimEffect(
                afterSkip.sessionId,
                SplashNavigationEffect.OPEN_NATIVE_FULL,
            ),
        )
        env.coordinator.cancelNow()
        runCurrent()
    }

    @Test
    fun timerAndDismissRace_nativeFullOnce() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "inter-0"),
            FakeScenarioConfig(dismissDelayMillis = 8_000L),
        )
        env.coordinator.startOrAttach(env.snapshot())
        var guard = 0
        while (
            env.coordinator.snapshot.value?.skipTimer?.state != SplashSkipTimerState.RUNNING &&
            guard < 200
        ) {
            runCurrent()
            advanceTimeBy(1L)
            guard++
        }
        assertEquals(SplashSkipTimerState.RUNNING, env.coordinator.snapshot.value!!.skipTimer.state)
        advanceTimeBy(8_000L)
        runCurrent()
        val snap = env.coordinator.snapshot.value
        assertEquals(SplashStage.NATIVE_FULL, snap!!.stage)
        assertTrue(env.coordinator.claimEffect(snap.sessionId, SplashNavigationEffect.OPEN_NATIVE_FULL))
        assertFalse(env.coordinator.claimEffect(snap.sessionId, SplashNavigationEffect.OPEN_NATIVE_FULL))
        env.coordinator.cancelNow()
        runCurrent()
    }

    @Test
    fun organicMismatch_skipsIneligiblePlacements() = runTest {
        val env = Env(this, audience = AudienceType.ORGANIC)
        env.coordinator.startOrAttach(env.snapshot(bannerOrganic = false, skipOrganic = false))
        advanceUntilIdle()
        val snap = env.coordinator.snapshot.value!!
        assertEquals(
            SplashLoadStatus.INELIGIBLE,
            snap.placements[SplashPlacement.BANNER_UFO]?.status,
        )
        env.coordinator.cancelNow()
        advanceUntilIdle()
    }

    @Test
    fun showFailFallsBackWithoutSkipTimer() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "inter-0"),
            FakeScenarioConfig(scenario = FakeScenario.SHOW_FAIL),
        )
        env.coordinator.startOrAttach(env.snapshot())
        advanceUntilIdle()
        val snap = env.coordinator.snapshot.first {
            it?.stage == SplashStage.NATIVE_FULL ||
                it?.pendingEffect == SplashNavigationEffect.OPEN_LANGUAGE_LOADING ||
                it?.stage == SplashStage.LANGUAGE_LOADING
        }
        assertTrue(
            snap!!.stage == SplashStage.NATIVE_FULL ||
                snap.pendingEffect == SplashNavigationEffect.OPEN_LANGUAGE_LOADING ||
                snap.stage == SplashStage.LANGUAGE_LOADING,
        )
        assertTrue(
            snap.skipTimer.state == SplashSkipTimerState.NOT_STARTED ||
                snap.skipTimer.state == SplashSkipTimerState.CANCELLED,
        )
        env.coordinator.cancelNow()
        advanceUntilIdle()
    }

    @Test
    fun languageLoadingClaimedExactlyOnce() = runTest {
        val env = Env(this)
        env.controller.setScenario(
            FakeAdItemKey("inter_splash_config_1", 0, "inter-0"),
            FakeScenarioConfig(scenario = FakeScenario.FAIL),
        )
        env.controller.setScenario(
            FakeAdItemKey("native_splash_full_config_1", 0, "full-0"),
            FakeScenarioConfig(scenario = FakeScenario.FAIL),
        )
        env.coordinator.startOrAttach(env.snapshot())
        advanceUntilIdle()
        val snap = env.coordinator.snapshot.first {
            it?.pendingEffect == SplashNavigationEffect.OPEN_LANGUAGE_LOADING
        }
        assertTrue(
            env.coordinator.claimEffect(
                snap!!.sessionId,
                SplashNavigationEffect.OPEN_LANGUAGE_LOADING,
            ),
        )
        assertFalse(
            env.coordinator.claimEffect(
                snap.sessionId,
                SplashNavigationEffect.OPEN_LANGUAGE_LOADING,
            ),
        )
        env.coordinator.cancelNow()
        advanceUntilIdle()
    }

    private class Env(
        scope: TestScope,
        audience: AudienceType = AudienceType.PAID,
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val ids = SequentialIdGenerator()
        val clock = Clock { scope.testScheduler.currentTime }
        val controller = FakeAdsSdkController(
            clock = FakeClock { scope.testScheduler.currentTime },
            dispatcher = dispatcher,
            objectIdGenerator = SequentialFakeObjectIdGenerator(prefix = "fake-object"),
        )
        private val sdk = FakeAdsSdkModule.create(controller)
        val storage = AdStorage(clock = clock, idGenerator = ids)
        val lock = GlobalFullscreenLock(clock = clock)
        val fullscreen = FullscreenShowCoordinator(
            storage = storage,
            lock = lock,
            adapters = AdSdkAdapterRegistry.create(sdk.adapters),
            clock = clock,
            idGenerator = ids,
        )
        val hosted = HostedFullscreenCoordinator(
            storage = storage,
            lock = lock,
            clock = clock,
            idGenerator = ids,
        )
        private val lifecycleScope = scope
        val lifecycle = AdsLifecycleCoordinator(
            sessionTracker = ForegroundSessionTracker(clock = clock),
            tokenStore = AdClickTokenStore(clock = clock, idGenerator = ids),
            fullscreenLock = lock,
            clock = clock,
            defaultClickTokenTtlMillis = 5_000L,
            scope = lifecycleScope,
        ).also { it.bindSession(SessionId("splash-session")) }
        private val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(sdk.adapters),
            clock = clock,
            idGenerator = ids,
        )
        private val nativeFull = NativeFullSplashController(
            scope = lifecycleScope,
            clock = clock,
            hosted = hosted,
        )
        val coordinator = SplashFlowCoordinator(
            scope = lifecycleScope,
            clock = clock,
            idGenerator = ids,
            loader = loader,
            storage = storage,
            fullscreen = fullscreen,
            hostedFullscreen = hosted,
            nativeFullController = nativeFull,
            lifecycle = lifecycle,
            audience = audience,
        )

        fun snapshot(
            bannerOrganic: Boolean = false,
            skipOrganic: Boolean = false,
        ): AdsConfigSnapshot {
            fun ads(
                enable: Boolean = true,
                isOrganic: Boolean? = true,
                type: String? = null,
                adunit: String,
            ) = OriginalAdsConfig(
                enable = enable,
                isOrganic = isOrganic,
                timeoutTotalMillis = 30_000L,
                listAds = listOf(
                    OriginalAdItem(
                        enableAd = true,
                        weight = 100,
                        timeoutMillis = 15_000L,
                        type = type,
                        adunit = adunit,
                    ),
                ),
            )

            fun resolved(value: com.example.adsmodule.core.config.OriginalConfigValue, json: String) =
                ResolvedConfig(
                    value = value,
                    canonicalJson = json,
                    origin = ConfigValueOrigin.BUNDLED,
                )

            val configs = linkedMapOf(
                ConfigKey("inter_splash_config_1") to resolved(
                    AdsConfigValue(ads(type = "inter", adunit = "inter-0")),
                    """{"k":"inter"}""",
                ),
                ConfigKey("native_splash_config_1") to resolved(
                    AdsConfigValue(ads(adunit = "native-0")),
                    """{"k":"native"}""",
                ),
                ConfigKey("banner_ufo_config_1") to resolved(
                    AdsConfigValue(ads(isOrganic = bannerOrganic, adunit = "banner-0")),
                    """{"k":"banner"}""",
                ),
                ConfigKey("native_splash_full_config_1") to resolved(
                    AdsConfigValue(ads(isOrganic = false, adunit = "full-0")),
                    """{"k":"full"}""",
                ),
                ConfigKey("native_splash_full_config_2") to resolved(
                    FullScreenTimingConfig(2_000L, 3_000L),
                    """{"k":"timing"}""",
                ),
                ConfigKey("splash_screen_config") to resolved(
                    SplashScreenConfig(
                        showLfo = true,
                        showPosition = "splash",
                        skipped = false,
                        timeoutScreenMillis = 30_000L,
                    ),
                    """{"k":"screen"}""",
                ),
                ConfigKey("splash_skip_ads") to resolved(
                    SplashSkipConfig(
                        enable = true,
                        isOrganic = skipOrganic,
                        timeSkipMillis = 8_000L,
                    ),
                    """{"k":"skip"}""",
                ),
                ConfigKey("enable_ads_app") to resolved(
                    BooleanConfigValue(true),
                    "true",
                ),
            )
            // Fill remaining registry keys with harmless placeholders for snapshot completeness
            // is not required by AdsConfigSnapshot.create — only provided keys are used.
            return AdsConfigSnapshot.create(version = 1L, configs = configs)
        }
    }

    private class SequentialIdGenerator(
        private val prefix: String = "id",
    ) : IdGenerator {
        private val next = AtomicLong(0L)
        override fun nextId(): String = "$prefix-${next.incrementAndGet()}"
    }
}
