package com.example.adsdemo

import android.app.Application
import com.example.adsdemo.language.AppCompatLocaleApplier
import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.config.BundledConfigDataSource
import com.example.adsmodule.core.config.InMemoryConfigDataSource
import com.example.adsmodule.core.config.InMemoryLastKnownGoodConfigStore
import com.example.adsmodule.core.config.OriginalRemoteConfigRepository
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.language.LanguageFlowCoordinator
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.lifecycle.ForegroundSessionTracker
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.onboarding.OnboardingAdCoordinator
import com.example.adsmodule.core.onboarding.OnboardingBoundaryCoordinator
import com.example.adsmodule.core.onboarding.full.OnboardingFullCoordinator
import com.example.adsmodule.core.refill.AdsConfigSnapshotProvider
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.splash.NativeFullSplashController
import com.example.adsmodule.core.splash.SplashFlowCoordinator
import com.example.adsmodule.core.splash.SplashStage
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdsDemoGraph(
    application: Application,
    val audience: AudienceType = AudienceType.PAID,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = Clock { System.currentTimeMillis() }
    private val idGenerator = UuidIdGenerator()

    val fakeController: FakeAdsSdkController = FakeAdsSdkController()
    private val fakeSdk = FakeAdsSdkModule.create(fakeController)
    private val adapters = AdSdkAdapterRegistry.create(fakeSdk.adapters)

    private val currentConfig = InMemoryConfigDataSource()
    private val lastKnownGood = InMemoryLastKnownGoodConfigStore()
    private val bundled = BundledConfigDataSource.fromAssetManager(application.assets)
    val configRepository = OriginalRemoteConfigRepository(
        currentDataSource = currentConfig,
        bundledDataSource = bundled,
        lastKnownGoodStore = lastKnownGood,
    )

    val storage = AdStorage(clock = clock, idGenerator = idGenerator)
    val loader = WeightedListLoader(
        adapterRegistry = adapters,
        clock = clock,
        idGenerator = idGenerator,
    )
    val fullscreenLock = GlobalFullscreenLock(clock = clock)
    val fullscreenShowCoordinator = FullscreenShowCoordinator(
        storage = storage,
        lock = fullscreenLock,
        adapters = adapters,
        clock = clock,
        idGenerator = idGenerator,
    )
    val hostedFullscreenCoordinator = HostedFullscreenCoordinator(
        storage = storage,
        lock = fullscreenLock,
        clock = clock,
        idGenerator = idGenerator,
    )
    private val tokenStore = AdClickTokenStore(clock = clock, idGenerator = idGenerator)
    val lifecycleCoordinator = AdsLifecycleCoordinator(
        sessionTracker = ForegroundSessionTracker(clock = clock),
        tokenStore = tokenStore,
        fullscreenLock = fullscreenLock,
        clock = clock,
        defaultClickTokenTtlMillis = 30_000L,
        scope = appScope,
    ).also {
        it.bindSession(SessionId("demo-session"))
        it.attachFullscreenClicks(fullscreenShowCoordinator)
    }

    private val deficitStore = RefillDeficitStore()
    private val snapshotProvider = AdsConfigSnapshotProvider { configRepository.snapshots.value }
    val refillScheduler = WholeListRefillScheduler(
        scope = appScope,
        loader = loader,
        storage = storage,
        deficitStore = deficitStore,
        snapshotProvider = snapshotProvider,
        idGenerator = idGenerator,
    )

    val normalScreenAds = NormalScreenAdCoordinator(
        scope = appScope,
        clock = clock,
        idGenerator = idGenerator,
        loader = loader,
        storage = storage,
        refillScheduler = refillScheduler,
        snapshotProvider = snapshotProvider,
        audience = audience,
    )

    val onboardingAds = OnboardingAdCoordinator(
        scope = appScope,
        normalAds = normalScreenAds,
        snapshotProvider = snapshotProvider,
        audience = audience,
    )

    val onboardingCoordinator = OnboardingBoundaryCoordinator(
        clock = clock,
        idGenerator = idGenerator,
    )

    val onboardingFullCoordinator = OnboardingFullCoordinator(
        scope = appScope,
        clock = clock,
        idGenerator = idGenerator,
        normalAds = normalScreenAds,
        hosted = hostedFullscreenCoordinator,
        snapshotProvider = snapshotProvider,
        audience = audience,
    ).also { full ->
        onboardingAds.onFullPreload = { index -> full.ensurePreloaded(index) }
    }

    val languageCoordinator = LanguageFlowCoordinator(
        scope = appScope,
        clock = clock,
        idGenerator = idGenerator,
        normalAds = normalScreenAds,
        localeApplier = AppCompatLocaleApplier(),
        onboardingAds = onboardingAds,
    )

    private val nativeFullController = NativeFullSplashController(
        scope = appScope,
        clock = clock,
        hosted = hostedFullscreenCoordinator,
    )

    val splashCoordinator = SplashFlowCoordinator(
        scope = appScope,
        clock = clock,
        idGenerator = idGenerator,
        loader = loader,
        storage = storage,
        fullscreen = fullscreenShowCoordinator,
        hostedFullscreen = hostedFullscreenCoordinator,
        nativeFullController = nativeFullController,
        lifecycle = lifecycleCoordinator,
        refillScheduler = refillScheduler,
        audience = audience,
    )

    fun warmUp() {
        appScope.launch {
            configRepository.refresh()
            val snapshot = configRepository.snapshots.value ?: return@launch
            splashCoordinator.startOrAttach(snapshot)
        }
        appScope.launch {
            splashCoordinator.snapshot.collectLatest { snap ->
                if (snap == null) return@collectLatest
                when (snap.stage) {
                    SplashStage.PRIMARY_SHOWING,
                    SplashStage.NATIVE_FULL,
                    SplashStage.LANGUAGE_LOADING,
                    -> {
                        val config = configRepository.snapshots.value ?: return@collectLatest
                        languageCoordinator.ensureLanguagePreload(config)
                    }
                    else -> Unit
                }
            }
        }
    }

    private class UuidIdGenerator : IdGenerator {
        private val seq = AtomicLong(0L)
        override fun nextId(): String =
            "id-${seq.incrementAndGet()}-${UUID.randomUUID()}"
    }
}
