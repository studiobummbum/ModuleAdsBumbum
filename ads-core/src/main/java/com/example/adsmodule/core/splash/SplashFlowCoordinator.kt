package com.example.adsmodule.core.splash

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.BooleanConfigValue
import com.example.adsmodule.core.config.FullScreenTimingConfig
import com.example.adsmodule.core.config.SplashScreenConfig
import com.example.adsmodule.core.config.SplashSkipConfig
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.FullscreenShowEvent
import com.example.adsmodule.core.fullscreen.FullscreenShowResult
import com.example.adsmodule.core.fullscreen.HostedFullscreenBeginResult
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.fullscreen.HostedFullscreenOutcome
import com.example.adsmodule.core.lifecycle.AdsLifecycleCoordinator
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.load.WeightedLoadRequest
import com.example.adsmodule.core.load.WeightedLoadResult
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StorageSlotKey
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdPresentationHost
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-scoped Splash orchestration.
 *
 * Primary Inter/App Open → Native Full (via time_skip or dismiss) → LanguageLoading.
 * Mixed `type=native` goes directly to Native Full.
 */
public class SplashFlowCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val loader: WeightedListLoader,
    private val storage: AdStorage,
    private val fullscreen: FullscreenShowCoordinator,
    private val hostedFullscreen: HostedFullscreenCoordinator,
    private val nativeFullController: NativeFullSplashController,
    private val lifecycle: AdsLifecycleCoordinator,
    private val refillScheduler: WholeListRefillScheduler? = null,
    private val audience: AudienceType = AudienceType.PAID,
    private val presentationHostProvider: () -> AdPresentationHost? = { null },
) {
    private val startMutex = Mutex()
    private val effectClaimed = ConcurrentClaimSet()
    private val languageGate = AtomicBoolean(false)
    private val nativeFullGate = AtomicBoolean(false)
    private val primaryAdvanceGate = AtomicBoolean(false)

    private var sessionJob: Job? = null
    private var skipJob: Job? = null
    private var screenTimeoutJob: Job? = null
    private var primaryShowJob: Job? = null
    private var activeSessionId: SplashSessionId? = null
    private var snapshotConfig: AdsConfigSnapshot? = null
    private var primaryShowRequestId: ShowRequestId? = null
    private var pendingNativeFullLaunch: SplashNativeFullLaunch? = null
    private var pendingPrimaryShow: PendingPrimaryShow? = null

    private val mutableSnapshot = MutableStateFlow<SplashFlowSnapshot?>(null)
    private val mutableEvents = MutableSharedFlow<SplashFlowEvent>(extraBufferCapacity = 32)

    public val snapshot: StateFlow<SplashFlowSnapshot?> = mutableSnapshot.asStateFlow()
    public val events: SharedFlow<SplashFlowEvent> = mutableEvents.asSharedFlow()

    public suspend fun startOrAttach(
        configSnapshot: AdsConfigSnapshot,
        existingSessionId: SplashSessionId? = null,
    ): SplashFlowSnapshot = startMutex.withLock {
        val current = mutableSnapshot.value
        if (
            existingSessionId != null &&
            current != null &&
            current.sessionId == existingSessionId &&
            current.stage != SplashStage.TERMINAL
        ) {
            return current
        }
        if (
            current != null &&
            current.stage != SplashStage.TERMINAL &&
            current.stage != SplashStage.LANGUAGE_LOADING
        ) {
            return current
        }

        cancelInternal(keepSnapshot = false)
        languageGate.set(false)
        nativeFullGate.set(false)
        primaryAdvanceGate.set(false)
        effectClaimed.clear()
        pendingNativeFullLaunch = null
        pendingPrimaryShow = null
        primaryShowRequestId = null

        val sessionId = SplashSessionId(idGenerator.nextId())
        val screenInstanceId = ScreenInstanceId("splash-${sessionId.value}")
        activeSessionId = sessionId
        snapshotConfig = configSnapshot
        lifecycle.setSplashActive(true)

        val initial = SplashFlowSnapshot(
            sessionId = sessionId,
            screenInstanceId = screenInstanceId,
            stage = SplashStage.BOOTSTRAP,
            placements = defaultPlacements(),
            splashScreenSkippedFlag = splashScreenConfig(configSnapshot)?.skipped == true,
        )
        publish(initial)
        emitStage(sessionId, SplashStage.BOOTSTRAP)

        sessionJob = scope.launch {
            runSession(sessionId, screenInstanceId, configSnapshot)
        }
        return checkNotNull(mutableSnapshot.value)
    }

    public fun claimEffect(
        sessionId: SplashSessionId,
        effect: SplashNavigationEffect,
    ): Boolean {
        val current = mutableSnapshot.value ?: return false
        if (current.sessionId != sessionId) return false
        if (current.pendingEffect != effect) return false
        return effectClaimed.claim("${sessionId.value}:${effect.name}")
    }

    public fun consumeNativeFullLaunch(sessionId: SplashSessionId): SplashNativeFullLaunch? {
        val launch = pendingNativeFullLaunch ?: return null
        if (launch.sessionId != sessionId) return null
        pendingNativeFullLaunch = null
        return launch
    }

    public fun onNativeFullCloseClicked(sessionId: SplashSessionId, showRequestId: ShowRequestId) {
        nativeFullController.onCloseClicked(sessionId, showRequestId)
    }

    public fun onLanguageLoadingOpened(sessionId: SplashSessionId) {
        val current = mutableSnapshot.value ?: return
        if (current.sessionId != sessionId) return
        update {
            it.copy(
                stage = SplashStage.LANGUAGE_LOADING,
                languageLoadingOpened = true,
                pendingEffect = null,
            )
        }
        lifecycle.setSplashActive(false)
        emitStage(sessionId, SplashStage.LANGUAGE_LOADING)
    }

    /**
     * UI confirms the pre-show dialog and asks the coordinator to present the primary fullscreen ad.
     * No-op unless [SplashStage.PRIMARY_PRESHOW] is active for [sessionId].
     */
    public fun confirmPrimaryShow(sessionId: SplashSessionId): Boolean {
        val pending = pendingPrimaryShow ?: return false
        if (pending.sessionId != sessionId) return false
        val current = mutableSnapshot.value ?: return false
        if (current.sessionId != sessionId) return false
        if (current.stage != SplashStage.PRIMARY_PRESHOW) return false
        pendingPrimaryShow = null
        executePrimaryShow(
            sessionId = pending.sessionId,
            screenInstanceId = pending.screenInstanceId,
            primary = pending.primary,
            kind = pending.kind,
        )
        return true
    }

    public fun cancelNow() {
        cancelInternal(keepSnapshot = true)
        update {
            it.copy(stage = SplashStage.TERMINAL, pendingEffect = null, debugMessage = "cancelled")
        }
        lifecycle.setSplashActive(false)
    }

    public fun cancel() {
        scope.launch {
            startMutex.withLock {
                cancelNow()
            }
        }
    }

    private suspend fun runSession(
        sessionId: SplashSessionId,
        screenInstanceId: ScreenInstanceId,
        configSnapshot: AdsConfigSnapshot,
    ) {
        if (!adsEnabled(configSnapshot)) {
            failOpenToLanguage(sessionId, "enable_ads_app=false")
            return
        }

        update { it.copy(stage = SplashStage.LOADING) }
        emitStage(sessionId, SplashStage.LOADING)

        val splashScreen = splashScreenConfig(configSnapshot)
        val showPrimaryLfo = splashScreen != null &&
            splashScreen.showLfo &&
            splashScreen.showPosition.equals("splash", ignoreCase = true)

        // Arm timeout/progress as soon as LOADING starts — not after awaitAll loads.
        if (showPrimaryLfo) {
            armScreenTimeout(sessionId, splashScreen?.timeoutScreenMillis ?: 30_000L)
        }

        activateRefillSlots(screenInstanceId)
        loadAllPlacements(sessionId, screenInstanceId, configSnapshot)

        if (activeSessionId != sessionId) return

        if (showPrimaryLfo) {
            val primary = mutableSnapshot.value?.placements?.get(SplashPlacement.INTER_SPLASH)
            when {
                primary?.status == SplashLoadStatus.READY &&
                    primary.storedAd != null -> {
                    when (primary.storedAd.sourceType) {
                        AdFormat.NATIVE,
                        AdFormat.NATIVE_FULLSCREEN,
                        -> advanceToNativeFull(
                            sessionId = sessionId,
                            reason = "mixed-native-primary",
                            supersedeShowRequestId = null,
                            preferPrimaryNative = true,
                        )
                        else -> showPrimaryFullscreen(sessionId, screenInstanceId, primary)
                    }
                }
                else -> {
                    // Wait until loads settle or screen timeout / READY appears.
                    waitForPrimaryOrTimeout(sessionId, screenInstanceId)
                }
            }
        } else {
            advanceToNativeFull(
                sessionId = sessionId,
                reason = "show_LFO disabled",
                supersedeShowRequestId = null,
                preferPrimaryNative = false,
            )
        }
    }

    private suspend fun waitForPrimaryOrTimeout(
        sessionId: SplashSessionId,
        screenInstanceId: ScreenInstanceId,
    ) {
        val ready = mutableSnapshot.first { snap ->
            snap?.sessionId == sessionId && (
                snap.placements[SplashPlacement.INTER_SPLASH]?.status == SplashLoadStatus.READY ||
                    snap.placements[SplashPlacement.INTER_SPLASH]?.status?.isTerminalLoad() == true ||
                    snap.stage == SplashStage.NATIVE_FULL ||
                    snap.stage == SplashStage.LANGUAGE_LOADING ||
                    snap.stage == SplashStage.TERMINAL
                )
        }
        if (ready?.sessionId != sessionId) return
        if (ready.stage == SplashStage.NATIVE_FULL ||
            ready.stage == SplashStage.LANGUAGE_LOADING ||
            ready.stage == SplashStage.TERMINAL
        ) {
            return
        }
        val primary = ready.placements[SplashPlacement.INTER_SPLASH]
        if (primary?.status == SplashLoadStatus.READY && primary.storedAd != null) {
            when (primary.storedAd.sourceType) {
                AdFormat.NATIVE,
                AdFormat.NATIVE_FULLSCREEN,
                -> advanceToNativeFull(
                    sessionId = sessionId,
                    reason = "mixed-native-primary",
                    supersedeShowRequestId = null,
                    preferPrimaryNative = true,
                )
                else -> showPrimaryFullscreen(sessionId, screenInstanceId, primary)
            }
        } else {
            advanceToNativeFull(
                sessionId = sessionId,
                reason = "primary unavailable",
                supersedeShowRequestId = null,
                preferPrimaryNative = false,
            )
        }
    }

    private suspend fun loadAllPlacements(
        sessionId: SplashSessionId,
        screenInstanceId: ScreenInstanceId,
        configSnapshot: AdsConfigSnapshot,
    ) = coroutineScope {
        val jobs = listOf(
            SplashPlacement.INTER_SPLASH to INTER_SPLASH_KEY,
            SplashPlacement.NATIVE_SPLASH to NATIVE_SPLASH_KEY,
            SplashPlacement.BANNER_UFO to BANNER_UFO_KEY,
            SplashPlacement.NATIVE_FULL_SPLASH to NATIVE_FULL_KEY,
        ).map { (placement, key) ->
            async {
                loadPlacement(
                    sessionId = sessionId,
                    screenInstanceId = screenInstanceId,
                    placement = placement,
                    configKey = key,
                    configSnapshot = configSnapshot,
                )
            }
        }
        jobs.awaitAll()
    }

    private suspend fun loadPlacement(
        sessionId: SplashSessionId,
        screenInstanceId: ScreenInstanceId,
        placement: SplashPlacement,
        configKey: ConfigKey,
        configSnapshot: AdsConfigSnapshot,
    ) {
        if (activeSessionId != sessionId) return
        val ads = configSnapshot.adsConfig(configKey)
        if (ads == null) {
            setPlacement(
                placement,
                SplashPlacementState(
                    placement = placement,
                    configKey = configKey,
                    status = SplashLoadStatus.FAILED,
                    reason = "missing config",
                ),
            )
            return
        }
        if (!ads.enable) {
            setPlacement(
                placement,
                SplashPlacementState(
                    placement = placement,
                    configKey = configKey,
                    status = SplashLoadStatus.DISABLED,
                    reason = "enable=false",
                ),
            )
            return
        }
        if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) {
            setPlacement(
                placement,
                SplashPlacementState(
                    placement = placement,
                    configKey = configKey,
                    status = SplashLoadStatus.INELIGIBLE,
                    reason = "audience ineligible",
                ),
            )
            return
        }

        val cycleId = LoadCycleId(idGenerator.nextId())
        setPlacement(
            placement,
            SplashPlacementState(
                placement = placement,
                configKey = configKey,
                status = SplashLoadStatus.LOADING,
                cycleId = cycleId,
            ),
        )

        val result = loader.load(
            WeightedLoadRequest(
                cycleId = cycleId,
                configKey = configKey,
                screenInstanceId = screenInstanceId,
                snapshot = configSnapshot,
            ),
        )
        if (activeSessionId != sessionId) {
            if (result is WeightedLoadResult.Success) {
                result.storedAd.sdkHandle.destroy()
            }
            return
        }

        when (result) {
            is WeightedLoadResult.Success -> {
                when (val put = storage.putReady(result.storedAd)) {
                    is PutResult.Accepted -> {
                        val reserved = if (
                            placement == SplashPlacement.NATIVE_SPLASH ||
                            placement == SplashPlacement.BANNER_UFO
                        ) {
                            storage.reserveNormal(configKey, screenInstanceId)
                        } else {
                            null
                        }
                        val reservationId = (reserved as? ReserveResult.Accepted)?.reservation?.reservationId
                        if (reserved is ReserveResult.Accepted) {
                            refillScheduler?.requestRefill(
                                StorageSlotKey(configKey, screenInstanceId),
                            )
                        }
                        setPlacement(
                            placement,
                            SplashPlacementState(
                                placement = placement,
                                configKey = configKey,
                                status = SplashLoadStatus.READY,
                                cycleId = cycleId,
                                storedAd = put.storedAd,
                                reservationId = reservationId,
                            ),
                        )
                    }
                    is PutResult.Rejected -> {
                        result.storedAd.sdkHandle.destroy()
                        setPlacement(
                            placement,
                            SplashPlacementState(
                                placement = placement,
                                configKey = configKey,
                                status = SplashLoadStatus.FAILED,
                                cycleId = cycleId,
                                reason = put.reason,
                            ),
                        )
                    }
                }
            }
            is WeightedLoadResult.Disabled -> setPlacement(
                placement,
                SplashPlacementState(
                    placement = placement,
                    configKey = configKey,
                    status = SplashLoadStatus.DISABLED,
                    cycleId = cycleId,
                    terminalReason = result.reason,
                ),
            )
            is WeightedLoadResult.Cancelled -> setPlacement(
                placement,
                SplashPlacementState(
                    placement = placement,
                    configKey = configKey,
                    status = SplashLoadStatus.CANCELLED,
                    cycleId = cycleId,
                    terminalReason = result.reason,
                ),
            )
            is WeightedLoadResult.Exhausted,
            is WeightedLoadResult.TotalTimeout,
            is WeightedLoadResult.MissingConfig,
            -> setPlacement(
                placement,
                SplashPlacementState(
                    placement = placement,
                    configKey = configKey,
                    status = if (result is WeightedLoadResult.Exhausted) {
                        SplashLoadStatus.EXHAUSTED
                    } else {
                        SplashLoadStatus.FAILED
                    },
                    cycleId = cycleId,
                    terminalReason = result.reason,
                    reason = result.reason.name,
                ),
            )
        }
    }

    private fun showPrimaryFullscreen(
        sessionId: SplashSessionId,
        screenInstanceId: ScreenInstanceId,
        primary: SplashPlacementState,
    ) {
        val stored = primary.storedAd ?: run {
            advanceToNativeFull(sessionId, "primary stored missing", null, false)
            return
        }
        val kind = when (stored.sourceType) {
            AdFormat.INTERSTITIAL -> FullscreenAdKind.INTERSTITIAL
            AdFormat.APP_OPEN -> FullscreenAdKind.APP_OPEN
            else -> {
                advanceToNativeFull(sessionId, "unexpected primary format", null, true)
                return
            }
        }
        pendingPrimaryShow = PendingPrimaryShow(
            sessionId = sessionId,
            screenInstanceId = screenInstanceId,
            primary = primary,
            kind = kind,
        )
        update {
            it.copy(
                stage = SplashStage.PRIMARY_PRESHOW,
                primaryKind = kind,
                primaryFormat = stored.sourceType,
                primaryObjectId = stored.objectId,
                debugMessage = "awaiting_primary_show_confirm",
            )
        }
        emitStage(sessionId, SplashStage.PRIMARY_PRESHOW)
    }

    private fun executePrimaryShow(
        sessionId: SplashSessionId,
        screenInstanceId: ScreenInstanceId,
        primary: SplashPlacementState,
        kind: FullscreenAdKind,
    ) {
        val stored = primary.storedAd ?: run {
            advanceToNativeFull(sessionId, "primary stored missing", null, false)
            return
        }
        primaryShowJob?.cancel()
        primaryShowJob = scope.launch {
            val reserved = storage.reserveNormal(INTER_SPLASH_KEY, screenInstanceId)
            if (reserved !is ReserveResult.Accepted) {
                advanceToNativeFull(sessionId, "primary reserve failed", null, false)
                return@launch
            }
            refillScheduler?.requestRefill(StorageSlotKey(INTER_SPLASH_KEY, screenInstanceId))

            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                fullscreen.events.collect { event ->
                    if (activeSessionId != sessionId) return@collect
                    handlePrimaryEvent(sessionId, event)
                }
            }
            try {
                update {
                    it.copy(
                        stage = SplashStage.PRIMARY_SHOWING,
                        primaryKind = kind,
                        primaryFormat = stored.sourceType,
                        primaryObjectId = stored.objectId,
                    )
                }
                emitStage(sessionId, SplashStage.PRIMARY_SHOWING)
                val result = fullscreen.show(
                    reserved.reservation.reservationId,
                    kind,
                    presentationHostProvider(),
                )
                if (result is FullscreenShowResult.Rejected || result is FullscreenShowResult.Failed) {
                    if (!primaryAdvanceGate.get()) {
                        advanceToNativeFull(sessionId, "primary show failed", null, false)
                    }
                }
            } finally {
                collectJob.cancel()
            }
        }
    }

    private fun handlePrimaryEvent(sessionId: SplashSessionId, event: FullscreenShowEvent) {
        when (event) {
            is FullscreenShowEvent.Started -> {
                primaryShowRequestId = event.showRequestId
                update {
                    it.copy(primaryShowRequestId = event.showRequestId)
                }
            }
            is FullscreenShowEvent.Shown -> {
                screenTimeoutJob?.cancel()
                screenTimeoutJob = null
                primaryShowRequestId = event.showRequestId
                update {
                    it.copy(
                        primaryShowRequestId = event.showRequestId,
                        primaryKind = event.kind,
                        primaryFormat = event.format,
                        primaryObjectId = event.objectId,
                    )
                }
                maybeStartSkipTimer(sessionId, event.showRequestId)
            }
            is FullscreenShowEvent.Dismissed -> {
                skipJob?.cancel()
                skipJob = null
                update {
                    it.copy(
                        skipTimer = it.skipTimer.copy(
                            state = if (it.skipTimer.state == SplashSkipTimerState.RUNNING) {
                                SplashSkipTimerState.CANCELLED
                            } else {
                                it.skipTimer.state
                            },
                        ),
                    )
                }
                advanceToNativeFull(
                    sessionId = sessionId,
                    reason = "primary dismissed",
                    supersedeShowRequestId = null,
                    preferPrimaryNative = false,
                )
            }
            is FullscreenShowEvent.Failed -> {
                skipJob?.cancel()
                skipJob = null
                advanceToNativeFull(
                    sessionId = sessionId,
                    reason = "primary failed: ${event.reason}",
                    supersedeShowRequestId = null,
                    preferPrimaryNative = false,
                )
            }
            else -> Unit
        }
    }

    private fun maybeStartSkipTimer(sessionId: SplashSessionId, showRequestId: ShowRequestId) {
        val config = snapshotConfig ?: return
        val skip = splashSkipConfig(config) ?: return
        if (!skip.enable) return
        if (!AudienceEligibility.isEligible(audience, skip.isOrganic)) return
        val current = mutableSnapshot.value ?: return
        if (current.sessionId != sessionId) return
        if (current.skipTimer.state != SplashSkipTimerState.NOT_STARTED) return

        val now = clock.nowMillis()
        val deadline = now + skip.timeSkipMillis
        update {
            it.copy(
                skipTimer = SplashSkipTimerSnapshot(
                    state = SplashSkipTimerState.RUNNING,
                    showRequestId = showRequestId,
                    startedAtMillis = now,
                    deadlineMillis = deadline,
                    remainingMillis = skip.timeSkipMillis,
                ),
            )
        }
        skipJob?.cancel()
        skipJob = scope.launch {
            delay(skip.timeSkipMillis)
            val snap = mutableSnapshot.value ?: return@launch
            if (snap.sessionId != sessionId) return@launch
            if (snap.skipTimer.showRequestId != showRequestId) return@launch
            if (snap.skipTimer.state != SplashSkipTimerState.RUNNING) return@launch
            update {
                it.copy(
                    skipTimer = it.skipTimer.copy(
                        state = SplashSkipTimerState.COMPLETED,
                        remainingMillis = 0L,
                    ),
                )
            }
            advanceToNativeFull(
                sessionId = sessionId,
                reason = "time_skip",
                supersedeShowRequestId = showRequestId,
                preferPrimaryNative = false,
            )
        }
    }

    private fun advanceToNativeFull(
        sessionId: SplashSessionId,
        reason: String,
        supersedeShowRequestId: ShowRequestId?,
        preferPrimaryNative: Boolean,
    ) {
        if (activeSessionId != sessionId) return
        if (!primaryAdvanceGate.compareAndSet(false, true)) return

        skipJob?.cancel()
        skipJob = null
        screenTimeoutJob?.cancel()
        screenTimeoutJob = null
        pendingPrimaryShow = null

        val current = mutableSnapshot.value ?: return
        if (current.sessionId != sessionId) return
        if (current.stage == SplashStage.LANGUAGE_LOADING || current.stage == SplashStage.TERMINAL) {
            return
        }

        val config = snapshotConfig
        val timing = config?.let { fullTimingConfig(it) }
        val screenInstanceId = current.screenInstanceId

        val begin = when {
            preferPrimaryNative -> hostedFullscreen.begin(
                configKey = INTER_SPLASH_KEY,
                screenInstanceId = screenInstanceId,
                kind = FullscreenAdKind.NATIVE_FULL_SPLASH,
                supersedeShowRequestId = null,
            )
            else -> hostedFullscreen.begin(
                configKey = NATIVE_FULL_KEY,
                screenInstanceId = screenInstanceId,
                kind = FullscreenAdKind.NATIVE_FULL_SPLASH,
                supersedeShowRequestId = supersedeShowRequestId,
            )
        }

        when (begin) {
            is HostedFullscreenBeginResult.Rejected -> {
                failOpenToLanguage(sessionId, "native full unavailable: ${begin.reason} ($reason)")
            }
            is HostedFullscreenBeginResult.Started -> {
                if (!nativeFullGate.compareAndSet(false, true)) {
                    hostedFullscreen.finish(begin.session, HostedFullscreenOutcome.FAILED)
                    return
                }
                val delayX = timing?.timeDelayXButtonMillis ?: 2_000L
                val autoSkip = timing?.autoSkipMillis ?: 3_000L
                pendingNativeFullLaunch = SplashNativeFullLaunch(
                    sessionId = sessionId,
                    hostedSession = begin.session,
                    timeDelayXButtonMillis = delayX,
                    autoSkipMillis = autoSkip,
                )
                update {
                    it.copy(
                        stage = SplashStage.NATIVE_FULL,
                        nativeFullOpened = true,
                        pendingEffect = SplashNavigationEffect.OPEN_NATIVE_FULL,
                        debugMessage = reason,
                        primaryShowRequestId = supersedeShowRequestId ?: it.primaryShowRequestId,
                    )
                }
                emitStage(sessionId, SplashStage.NATIVE_FULL)
                mutableEvents.tryEmit(
                    SplashFlowEvent.EffectRequested(
                        sessionId = sessionId,
                        effect = SplashNavigationEffect.OPEN_NATIVE_FULL,
                        occurredAtMillis = clock.nowMillis(),
                    ),
                )
                nativeFullController.start(
                    sessionId = sessionId,
                    hostedSession = begin.session,
                    timeDelayXButtonMillis = delayX,
                    autoSkipMillis = autoSkip,
                    onSnapshot = { control ->
                        update { snap -> snap.copy(nativeFull = control) }
                    },
                    onExit = { exitSource ->
                        nativeFullController.finishHosted(
                            sessionId = sessionId,
                            showRequestId = begin.session.showRequestId,
                            outcome = HostedFullscreenOutcome.COMPLETED,
                        )
                        navigateToLanguage(sessionId, "native-full-$exitSource")
                    },
                )
            }
        }
    }

    private fun failOpenToLanguage(sessionId: SplashSessionId, reason: String) {
        navigateToLanguage(sessionId, reason)
    }

    private fun navigateToLanguage(sessionId: SplashSessionId, reason: String) {
        if (activeSessionId != sessionId) return
        if (!languageGate.compareAndSet(false, true)) return
        nativeFullController.cancel(sessionId)
        update {
            it.copy(
                stage = SplashStage.LANGUAGE_LOADING,
                pendingEffect = SplashNavigationEffect.OPEN_LANGUAGE_LOADING,
                debugMessage = reason,
            )
        }
        mutableEvents.tryEmit(
            SplashFlowEvent.EffectRequested(
                sessionId = sessionId,
                effect = SplashNavigationEffect.OPEN_LANGUAGE_LOADING,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private fun armScreenTimeout(sessionId: SplashSessionId, timeoutMillis: Long) {
        val deadline = clock.nowMillis() + timeoutMillis
        update {
            it.copy(
                screenTimeoutTotalMillis = timeoutMillis,
                screenTimeoutDeadlineMillis = deadline,
            )
        }
        screenTimeoutJob?.cancel()
        screenTimeoutJob = scope.launch {
            delay(timeoutMillis)
            val snap = mutableSnapshot.value ?: return@launch
            if (snap.sessionId != sessionId) return@launch
            if (snap.stage == SplashStage.PRIMARY_SHOWING && snap.primaryShowRequestId != null) {
                return@launch
            }
            if (snap.stage == SplashStage.NATIVE_FULL ||
                snap.stage == SplashStage.LANGUAGE_LOADING ||
                snap.stage == SplashStage.TERMINAL
            ) {
                return@launch
            }
            pendingPrimaryShow = null
            advanceToNativeFull(
                sessionId = sessionId,
                reason = "timeout_screen",
                supersedeShowRequestId = null,
                preferPrimaryNative = false,
            )
        }
    }

    private fun activateRefillSlots(screenInstanceId: ScreenInstanceId) {
        val scheduler = refillScheduler ?: return
        listOf(INTER_SPLASH_KEY, NATIVE_SPLASH_KEY, BANNER_UFO_KEY, NATIVE_FULL_KEY).forEach { key ->
            scheduler.activate(StorageSlotKey(key, screenInstanceId), targetReadyCount = 1)
        }
    }

    private fun cancelInternal(keepSnapshot: Boolean) {
        sessionJob?.cancel()
        skipJob?.cancel()
        screenTimeoutJob?.cancel()
        primaryShowJob?.cancel()
        sessionJob = null
        skipJob = null
        screenTimeoutJob = null
        primaryShowJob = null
        pendingPrimaryShow = null
        activeSessionId?.let { nativeFullController.cancel(it) }
        activeSessionId = null
        if (!keepSnapshot) {
            mutableSnapshot.value = null
        }
    }

    private data class PendingPrimaryShow(
        val sessionId: SplashSessionId,
        val screenInstanceId: ScreenInstanceId,
        val primary: SplashPlacementState,
        val kind: FullscreenAdKind,
    )

    private fun setPlacement(placement: SplashPlacement, state: SplashPlacementState) {
        update { snap ->
            snap.copy(placements = snap.placements + (placement to state))
        }
    }

    private fun update(transform: (SplashFlowSnapshot) -> SplashFlowSnapshot) {
        val current = mutableSnapshot.value ?: return
        mutableSnapshot.value = transform(current)
    }

    private fun publish(snapshot: SplashFlowSnapshot) {
        mutableSnapshot.value = snapshot
    }

    private fun emitStage(sessionId: SplashSessionId, stage: SplashStage) {
        mutableEvents.tryEmit(
            SplashFlowEvent.StageChanged(
                sessionId = sessionId,
                stage = stage,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private fun defaultPlacements(): Map<SplashPlacement, SplashPlacementState> =
        SplashPlacement.entries.associateWith { placement ->
            SplashPlacementState(
                placement = placement,
                configKey = when (placement) {
                    SplashPlacement.INTER_SPLASH -> INTER_SPLASH_KEY
                    SplashPlacement.NATIVE_SPLASH -> NATIVE_SPLASH_KEY
                    SplashPlacement.BANNER_UFO -> BANNER_UFO_KEY
                    SplashPlacement.NATIVE_FULL_SPLASH -> NATIVE_FULL_KEY
                },
                status = SplashLoadStatus.IDLE,
            )
        }

    private fun adsEnabled(snapshot: AdsConfigSnapshot): Boolean {
        val value = snapshot[ENABLE_ADS_KEY]?.value as? BooleanConfigValue ?: return true
        return value.value
    }

    private fun splashSkipConfig(snapshot: AdsConfigSnapshot): SplashSkipConfig? =
        snapshot[SPLASH_SKIP_KEY]?.value as? SplashSkipConfig

    private fun splashScreenConfig(snapshot: AdsConfigSnapshot): SplashScreenConfig? =
        snapshot[SPLASH_SCREEN_KEY]?.value as? SplashScreenConfig

    private fun fullTimingConfig(snapshot: AdsConfigSnapshot): FullScreenTimingConfig? =
        snapshot[NATIVE_FULL_TIMING_KEY]?.value as? FullScreenTimingConfig

    private fun SplashLoadStatus.isTerminalLoad(): Boolean =
        this == SplashLoadStatus.DISABLED ||
            this == SplashLoadStatus.INELIGIBLE ||
            this == SplashLoadStatus.FAILED ||
            this == SplashLoadStatus.EXHAUSTED ||
            this == SplashLoadStatus.CANCELLED

    private companion object {
        val INTER_SPLASH_KEY = ConfigKey("inter_splash_config_1")
        val NATIVE_SPLASH_KEY = ConfigKey("native_splash_config_1")
        val BANNER_UFO_KEY = ConfigKey("banner_ufo_config_1")
        val NATIVE_FULL_KEY = ConfigKey("native_splash_full_config_1")
        val NATIVE_FULL_TIMING_KEY = ConfigKey("native_splash_full_config_2")
        val SPLASH_SKIP_KEY = ConfigKey("splash_skip_ads")
        val SPLASH_SCREEN_KEY = ConfigKey("splash_screen_config")
        val ENABLE_ADS_KEY = ConfigKey("enable_ads_app")
    }
}

private class ConcurrentClaimSet {
    private val claimed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun claim(key: String): Boolean = claimed.add(key)

    fun clear() {
        claimed.clear()
    }
}
