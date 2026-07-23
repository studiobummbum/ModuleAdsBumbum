package com.example.adsmodule.core.debug

import com.example.adsmodule.core.AdClickTokenId
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.ConfigRefreshResult
import com.example.adsmodule.core.config.InMemoryConfigDataSource
import com.example.adsmodule.core.config.OriginalRemoteConfigRepository
import com.example.adsmodule.core.fullscreen.FullscreenLockSnapshot
import com.example.adsmodule.core.fullscreen.GlobalFullscreenLock
import com.example.adsmodule.core.lifecycle.LifecycleSimulatorApi
import com.example.adsmodule.core.lifecycle.LifecycleSimulatorSnapshot
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.load.WeightedLoadDebugState
import com.example.adsmodule.core.onboarding.OnboardingBackwardResult
import com.example.adsmodule.core.onboarding.OnboardingBoundaryCoordinator
import com.example.adsmodule.core.onboarding.OnboardingFlowSnapshot
import com.example.adsmodule.core.onboarding.OnboardingForwardResult
import com.example.adsmodule.core.onboarding.full.FullExitSource
import com.example.adsmodule.core.onboarding.full.OnboardingFullCoordinator
import com.example.adsmodule.core.onboarding.full.OnboardingFullSnapshot
import com.example.adsmodule.core.refill.RefillDeficitStore
import com.example.adsmodule.core.refill.RefillSchedulerInspectorSnapshot
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StorageInspectorSnapshot
import com.example.adsmodule.core.storage.StoredAdView
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.core.turnback.AtomicBorrowService
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.LoadCycleId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Public façade for Phase 14 Debug Control Center.
 * UI must only call through this API (and nested public action helpers).
 */
public class AdsDebugApi(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val storage: AdStorage,
    private val loader: WeightedListLoader,
    private val deficitStore: RefillDeficitStore,
    private val refillScheduler: WholeListRefillScheduler,
    private val fullscreenLock: GlobalFullscreenLock,
    private val lifecycleSimulator: LifecycleSimulatorApi,
    private val configRepository: OriginalRemoteConfigRepository,
    currentConfigDataSource: InMemoryConfigDataSource,
    tokenStore: AdClickTokenStore,
    borrowService: AtomicBorrowService,
    private val onboardingBoundary: OnboardingBoundaryCoordinator? = null,
    private val onboardingFull: OnboardingFullCoordinator? = null,
    public val navigation: NavigationDebugTracker = NavigationDebugTracker(),
    public val eventLog: DebugEventRingBuffer = DebugEventRingBuffer(),
) {
    public val configActions: ConfigDebugActions =
        ConfigDebugActions(currentConfigDataSource, configRepository)

    public val turnbackActions: TurnbackDebugActions =
        TurnbackDebugActions(storage, tokenStore, borrowService)

    public val lifecycle: LifecycleSimulatorApi = lifecycleSimulator

    private val mutableDashboard = MutableStateFlow(buildDashboardSnapshot())
    public val dashboard: StateFlow<AdsDebugDashboardSnapshot> = mutableDashboard.asStateFlow()

    private var collectorJob: Job? = null

    public fun start() {
        if (collectorJob != null) return
        collectorJob = scope.launch {
            launch {
                navigation.state.collectLatest { refreshDashboard() }
            }
            launch {
                fullscreenLock.snapshot.collectLatest { refreshDashboard() }
            }
            launch {
                loader.debugStates.collectLatest { refreshDashboard() }
            }
            launch {
                configRepository.snapshots.collectLatest { refreshDashboard() }
            }
            launch {
                lifecycleSimulator.lifecycleSnapshot.collectLatest { refreshDashboard() }
            }
            launch {
                eventLog.snapshot.collectLatest { refreshDashboard() }
            }
            launch {
                onboardingBoundary?.snapshot?.collectLatest { refreshDashboard() }
            }
            launch {
                onboardingFull?.snapshot?.collectLatest { refreshDashboard() }
            }
        }
        refreshDashboard()
        log("debug", "AdsDebugApi started")
    }

    public fun stop() {
        collectorJob?.cancel()
        collectorJob = null
    }

    public fun refreshDashboard(): AdsDebugDashboardSnapshot {
        val snap = buildDashboardSnapshot()
        mutableDashboard.value = snap
        return snap
    }

    public fun storageSnapshot(): StorageInspectorSnapshot = storage.inspector()

    public fun refillSnapshot(): RefillSchedulerInspectorSnapshot =
        refillScheduler.inspectorSnapshot()

    public fun deficitSnapshot() =
        deficitStore.snapshot(
            readyCountBySlot = storage.inspector().readySlots.keys.associateWith {
                storage.readyCount(it)
            },
        )

    public fun weightedLoadStates(): StateFlow<Map<LoadCycleId, WeightedLoadDebugState>> =
        loader.debugStates

    public fun placements(): List<PlacementDebugView> =
        PlacementDebugInspector.placements(configRepository.snapshots.value)

    public fun fullscreenLockSnapshot(): StateFlow<FullscreenLockSnapshot> =
        fullscreenLock.snapshot

    public fun configSnapshot(): StateFlow<AdsConfigSnapshot?> = configRepository.snapshots

    public fun lifecycleInspector(): LifecycleSimulatorSnapshot =
        lifecycleSimulator.inspectorSnapshot()

    public fun onboardingSnapshot(): OnboardingFlowSnapshot? =
        onboardingBoundary?.snapshot?.value

    public fun onboardingFullSnapshot(): OnboardingFullSnapshot? =
        onboardingFull?.snapshot?.value

    public fun requestOnboardingForward(
        sessionId: OnboardingSessionId,
    ): OnboardingForwardResult? = onboardingBoundary?.requestForward(sessionId)

    public fun requestOnboardingBackward(
        sessionId: OnboardingSessionId,
    ): OnboardingBackwardResult? = onboardingBoundary?.requestBackward(sessionId)

    public fun simulateFullExit(
        fullSessionId: FullSessionId,
        source: FullExitSource,
    ): Boolean = onboardingFull?.requestExit(fullSessionId, source) == true

    public fun simulateFullSwipe(fullSessionId: FullSessionId): Boolean =
        onboardingFull?.onSwipeForward(fullSessionId) == true

    public fun simulateFullCloseX(fullSessionId: FullSessionId): Boolean =
        onboardingFull?.onCloseClicked(fullSessionId) == true

    public fun simulateFullAutoSkip(fullSessionId: FullSessionId): Boolean =
        onboardingFull?.onAutoSkip(fullSessionId) == true

    public suspend fun refreshConfig(): ConfigRefreshResult {
        val result = configActions.refresh()
        log(
            category = "config",
            message = "refresh=${result::class.simpleName}",
        )
        refreshDashboard()
        return result
    }

    public fun writeConfigOverride(key: ConfigKey, rawJson: String) {
        configActions.writeRaw(key, rawJson)
        log("config", "override ${key.value}")
    }

    public fun previewTurnback(): List<StoredAdView> = turnbackActions.previewEligible()

    public fun simulateTurnbackBorrow(
        sessionId: SessionId,
    ): Pair<AdClickTokenId, ReserveResult> {
        val result = turnbackActions.borrowWithFreshToken(sessionId)
        log(
            category = "turnback",
            message = "borrow token=${result.first.value} result=${result.second::class.simpleName}",
        )
        refreshDashboard()
        return result
    }

    public fun reportNavigation(
        activityName: String? = null,
        fragmentName: String? = null,
        pagerIndex: Int? = null,
        screenLabel: String? = null,
    ) {
        val current = navigation.state.value
        navigation.report(
            activityName = activityName ?: current.activityName,
            fragmentName = fragmentName ?: current.fragmentName,
            pagerIndex = pagerIndex ?: current.pagerIndex,
            screenLabel = screenLabel ?: current.screenLabel,
        )
        refreshDashboard()
    }

    public fun log(
        category: String,
        message: String,
        details: Map<String, String> = emptyMap(),
    ): DebugEvent =
        eventLog.append(
            category = category,
            message = message,
            timestampMillis = clock.nowMillis(),
            details = details,
        )

    public val events: SharedFlow<DebugEvent> = eventLog.events

    private fun buildDashboardSnapshot(): AdsDebugDashboardSnapshot {
        val config = configRepository.snapshots.value
        val storageSnap = storage.inspector()
        val deficit = deficitStore.snapshot(
            readyCountBySlot = storageSnap.readySlots.keys.associateWith { storage.readyCount(it) },
        )
        val life = lifecycleSimulator.lifecycleSnapshot.value
        return AdsDebugDashboardSnapshot(
            sessionId = life.sessionId,
            configVersion = config?.version,
            configContentHash = config?.contentHash,
            navigation = navigation.state.value,
            fullscreenLock = fullscreenLock.snapshot.value,
            clickTokens = lifecycleSimulator.tokenSnapshot(),
            readyObjectCount = storageSnap.objects.count {
                it.state == com.example.adsmodule.core.AdSlotState.READY
            },
            refillInFlightCount = deficit.slots.sumOf { it.inFlightCount },
            activeLoadCycleCount = loader.debugStates.value.values.count { it.isActive },
            latestEvent = eventLog.latest(),
            capturedAtMillis = clock.nowMillis(),
        )
    }
}
