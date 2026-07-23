package com.example.adsmodule.core.language

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LanguageSessionId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenBindSession
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-scoped Language Activity flow.
 *
 * Language Loading uses a fixed 2s UI timer that never waits on ads.
 * Apply Language uses a minimum 2s deadline and continues with the current
 * locale when [LocaleApplier] fails.
 */
public class LanguageFlowCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val normalAds: NormalScreenAdCoordinator,
    private val localeApplier: LocaleApplier,
) {
    private val mutex = Mutex()
    private val mutableSnapshot = MutableStateFlow<LanguageFlowSnapshot?>(null)
    private val mutableEvents = MutableSharedFlow<LanguageFlowEvent>(extraBufferCapacity = 32)
    private val claimedEffects = AtomicReference(emptySet<LanguageNavigationEffect>())
    private val boundAds = AtomicReference(emptyMap<LanguagePlacement, NormalScreenBindSession>())
    private val loadingTimerJob = AtomicReference<Job?>(null)
    private val applyTimerJob = AtomicReference<Job?>(null)
    private val loadingTimerStarted = AtomicBoolean(false)
    private val applyStarted = AtomicBoolean(false)
    private val onboardingPreloadStarted = AtomicBoolean(false)
    private val languagePreloadStarted = AtomicBoolean(false)
    private val selectNavigated = AtomicBoolean(false)
    private val dupNavigated = AtomicBoolean(false)

    public val snapshot: StateFlow<LanguageFlowSnapshot?> = mutableSnapshot.asStateFlow()
    public val events: SharedFlow<LanguageFlowEvent> = mutableEvents.asSharedFlow()

    /**
     * Creates or reattaches the Language session and starts the Language Loading timer.
     */
    public fun startOrAttach(
        configSnapshot: AdsConfigSnapshot,
        existingSessionId: LanguageSessionId? = null,
    ): LanguageSessionId {
        val sessionId = attach(configSnapshot, existingSessionId)
        if (loadingTimerStarted.compareAndSet(false, true)) {
            val now = clock.nowMillis()
            val deadline = now + LANGUAGE_LOADING_DELAY_MILLIS
            publish {
                it.copy(
                    stage = LanguageStage.LANGUAGE_LOADING,
                    loadingTimer = LanguageTimerSnapshot(
                        startedAtMillis = now,
                        deadlineMillis = deadline,
                        remainingMillis = LANGUAGE_LOADING_DELAY_MILLIS,
                        completed = false,
                    ),
                )
            }
            emitStage(sessionId, LanguageStage.LANGUAGE_LOADING)
            val snap = checkNotNull(mutableSnapshot.value)
            bindPlacementAsync(
                LanguagePlacement.LOADING,
                LanguageConfigKeys.LOADING,
                snap.loadingScreenId,
            )
            startLoadingTimer(sessionId, deadline)
        } else {
            refreshTimers()
            val snap = checkNotNull(mutableSnapshot.value)
            if (snap.stage == LanguageStage.LANGUAGE_LOADING) {
                bindPlacementAsync(
                    LanguagePlacement.LOADING,
                    LanguageConfigKeys.LOADING,
                    snap.loadingScreenId,
                )
            }
        }
        return sessionId
    }

    /**
     * Reattaches an existing Language session without starting the loading timer.
     */
    public fun attach(
        configSnapshot: AdsConfigSnapshot,
        existingSessionId: LanguageSessionId? = null,
    ): LanguageSessionId {
        val sessionId = ensureSession(existingSessionId)
        ensureLanguagePreload(configSnapshot)
        refreshTimers()
        return sessionId
    }

    /**
     * Early Language preload after Splash reaches READY/show. Idempotent.
     * Allocates a Language session so preload and UI share the same screen IDs.
     */
    public fun ensureLanguagePreload(configSnapshot: AdsConfigSnapshot): LanguageSessionId {
        @Suppress("UNUSED_VARIABLE")
        val ignored = configSnapshot
        val sessionId = ensureSession(existingSessionId = null)
        val snap = checkNotNull(mutableSnapshot.value)
        if (!languagePreloadStarted.compareAndSet(false, true)) {
            publish {
                it.copy(
                    languagePreloadStarted = true,
                    placements = currentPlacements(it),
                )
            }
            return sessionId
        }
        normalAds.ensureLoadedAsync(LanguageConfigKeys.LOADING, snap.loadingScreenId)
        normalAds.ensureLoadedAsync(LanguageConfigKeys.SELECT, snap.selectScreenId)
        normalAds.ensureLoadedAsync(LanguageConfigKeys.DUP, snap.dupScreenId)
        publish {
            it.copy(
                languagePreloadStarted = true,
                placements = currentPlacements(it),
            )
        }
        return sessionId
    }

    public fun claimEffect(
        sessionId: LanguageSessionId,
        effect: LanguageNavigationEffect,
    ): Boolean {
        val snap = mutableSnapshot.value ?: return false
        if (snap.sessionId != sessionId) return false
        if (snap.pendingEffect != effect) return false
        while (true) {
            val claimed = claimedEffects.get()
            if (effect in claimed) return false
            if (claimedEffects.compareAndSet(claimed, claimed + effect)) {
                publish {
                    if (it.sessionId == sessionId && it.pendingEffect == effect) {
                        it.copy(pendingEffect = null)
                    } else {
                        it
                    }
                }
                return true
            }
        }
    }

    public fun onLanguageSelectOpened(sessionId: LanguageSessionId) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        unbindPlacement(LanguagePlacement.LOADING, NormalScreenUnbindMode.CONSUME)
        publish {
            it.copy(
                stage = LanguageStage.LANGUAGE_SELECT,
                placements = currentPlacements(it),
            )
        }
        emitStage(sessionId, LanguageStage.LANGUAGE_SELECT)
        bindPlacementAsync(
            LanguagePlacement.SELECT,
            LanguageConfigKeys.SELECT,
            snap.selectScreenId,
        )
    }

    public fun selectLanguage(
        sessionId: LanguageSessionId,
        language: DemoLanguage,
    ): Boolean {
        val snap = mutableSnapshot.value ?: return false
        if (snap.sessionId != sessionId) return false
        if (snap.stage != LanguageStage.LANGUAGE_SELECT &&
            snap.stage != LanguageStage.LANGUAGE_DUP &&
            snap.stage != LanguageStage.APPLY_LANGUAGE
        ) {
            return false
        }
        if (snap.stage == LanguageStage.LANGUAGE_SELECT) {
            if (!selectNavigated.compareAndSet(false, true)) {
                return false
            }
            publish { it.copy(selectedLanguage = language) }
            requestEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_DUP)
            return true
        }
        publish { it.copy(selectedLanguage = language) }
        return true
    }

    public fun restoreSelectedLanguage(
        sessionId: LanguageSessionId,
        languageTag: String?,
    ) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        if (languageTag.isNullOrBlank()) return
        val language = DemoLanguages.find(languageTag) ?: return
        publish { it.copy(selectedLanguage = language) }
    }

    public fun onLanguageDupOpened(sessionId: LanguageSessionId) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        unbindPlacement(LanguagePlacement.SELECT, NormalScreenUnbindMode.CONSUME)
        publish {
            it.copy(
                stage = LanguageStage.LANGUAGE_DUP,
                placements = currentPlacements(it),
            )
        }
        emitStage(sessionId, LanguageStage.LANGUAGE_DUP)
        bindPlacementAsync(
            LanguagePlacement.DUP,
            LanguageConfigKeys.DUP,
            snap.dupScreenId,
        )
        ensureOnboardingPreload()
    }

    public fun onLanguageDupNext(sessionId: LanguageSessionId): Boolean {
        val snap = mutableSnapshot.value ?: return false
        if (snap.sessionId != sessionId) return false
        if (snap.stage != LanguageStage.LANGUAGE_DUP) return false
        if (snap.selectedLanguage == null) return false
        if (!dupNavigated.compareAndSet(false, true)) {
            return false
        }
        ensureOnboardingPreload()
        requestEffect(sessionId, LanguageNavigationEffect.OPEN_APPLY_LANGUAGE)
        return true
    }

    public fun onApplyLanguageOpened(sessionId: LanguageSessionId) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        unbindPlacement(LanguagePlacement.DUP, NormalScreenUnbindMode.CONSUME)
        val language = snap.selectedLanguage ?: return
        publish {
            it.copy(
                stage = LanguageStage.APPLY_LANGUAGE,
                placements = currentPlacements(it),
            )
        }
        emitStage(sessionId, LanguageStage.APPLY_LANGUAGE)
        ensureOnboardingPreload()
        if (!applyStarted.compareAndSet(false, true)) {
            refreshTimers()
            maybeCompleteApply(sessionId)
            return
        }
        val now = clock.nowMillis()
        val deadline = now + APPLY_LANGUAGE_DELAY_MILLIS
        publish {
            it.copy(
                applyTimer = LanguageTimerSnapshot(
                    startedAtMillis = now,
                    deadlineMillis = deadline,
                    remainingMillis = APPLY_LANGUAGE_DELAY_MILLIS,
                    completed = false,
                ),
                localeStatus = LocaleApplyStatus.APPLYING,
            )
        }
        startApplyWork(sessionId, language.tag, deadline)
    }

    public fun onOnboardingOpened(sessionId: LanguageSessionId) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        publish {
            it.copy(
                stage = LanguageStage.ONBOARDING,
                pendingEffect = null,
            )
        }
        emitStage(sessionId, LanguageStage.ONBOARDING)
    }

    public fun boundAd(placement: LanguagePlacement): LanguageBoundAd? {
        val session = boundAds.get()[placement] ?: return null
        return LanguageBoundAd(placement = placement, session = session)
    }

    private fun ensureSession(existingSessionId: LanguageSessionId?): LanguageSessionId {
        val current = mutableSnapshot.value
        if (current != null) {
            if (existingSessionId == null || existingSessionId == current.sessionId) {
                return current.sessionId
            }
        }
        val sessionId = existingSessionId ?: LanguageSessionId(idGenerator.nextId())
        if (current != null && current.sessionId == sessionId) {
            return sessionId
        }
        val snap = LanguageFlowSnapshot(
            sessionId = sessionId,
            stage = LanguageStage.BOOTSTRAP,
            loadingScreenId = LanguageScreenInstances.loading(sessionId),
            selectScreenId = LanguageScreenInstances.select(sessionId),
            dupScreenId = LanguageScreenInstances.dup(sessionId),
        )
        claimedEffects.set(emptySet())
        boundAds.set(emptyMap())
        loadingTimerStarted.set(false)
        applyStarted.set(false)
        onboardingPreloadStarted.set(false)
        languagePreloadStarted.set(false)
        selectNavigated.set(false)
        dupNavigated.set(false)
        loadingTimerJob.getAndSet(null)?.cancel()
        applyTimerJob.getAndSet(null)?.cancel()
        mutableSnapshot.value = snap
        return sessionId
    }

    private fun ensureOnboardingPreload() {
        if (!onboardingPreloadStarted.compareAndSet(false, true)) {
            publish { it.copy(onboardingPreloadStarted = true) }
            return
        }
        normalAds.ensureLoadedAsync(
            LanguageConfigKeys.ONBOARDING,
            LanguageScreenInstances.onboardingPage1,
        )
        normalAds.ensureLoadedAsync(
            LanguageConfigKeys.ONBOARDING,
            LanguageScreenInstances.onboardingPage2,
        )
        publish { it.copy(onboardingPreloadStarted = true) }
    }

    private fun startLoadingTimer(sessionId: LanguageSessionId, deadlineMillis: Long) {
        loadingTimerJob.getAndSet(null)?.cancel()
        val job = scope.launch {
            val remaining = (deadlineMillis - clock.nowMillis()).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            completeLoadingTimer(sessionId)
        }
        loadingTimerJob.set(job)
    }

    private fun completeLoadingTimer(sessionId: LanguageSessionId) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        if (snap.loadingTimer.completed) {
            if (snap.pendingEffect == null &&
                LanguageNavigationEffect.OPEN_LANGUAGE_SELECT !in claimedEffects.get()
            ) {
                requestEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
            }
            return
        }
        publish {
            it.copy(
                loadingTimer = it.loadingTimer.copy(
                    remainingMillis = 0L,
                    completed = true,
                ),
            )
        }
        requestEffect(sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
    }

    private fun startApplyWork(
        sessionId: LanguageSessionId,
        languageTag: String,
        deadlineMillis: Long,
    ) {
        applyTimerJob.getAndSet(null)?.cancel()
        val job = scope.launch {
            val applyJob = launch {
                when (val result = localeApplier.apply(languageTag)) {
                    LocaleApplyResult.Success -> {
                        publish {
                            if (it.sessionId == sessionId) {
                                it.copy(
                                    localeStatus = LocaleApplyStatus.SUCCEEDED,
                                    localeMessage = null,
                                )
                            } else {
                                it
                            }
                        }
                    }
                    is LocaleApplyResult.Failure -> {
                        publish {
                            if (it.sessionId == sessionId) {
                                it.copy(
                                    localeStatus = LocaleApplyStatus.FAILED_FALLBACK,
                                    localeMessage = result.reason,
                                )
                            } else {
                                it
                            }
                        }
                    }
                }
                maybeCompleteApply(sessionId)
            }
            val remaining = (deadlineMillis - clock.nowMillis()).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            publish {
                if (it.sessionId == sessionId) {
                    it.copy(
                        applyTimer = it.applyTimer.copy(
                            remainingMillis = 0L,
                            completed = true,
                        ),
                    )
                } else {
                    it
                }
            }
            maybeCompleteApply(sessionId)
            applyJob.join()
        }
        applyTimerJob.set(job)
    }

    private fun maybeCompleteApply(sessionId: LanguageSessionId) {
        val snap = mutableSnapshot.value ?: return
        if (snap.sessionId != sessionId) return
        if (snap.stage != LanguageStage.APPLY_LANGUAGE) return
        if (!snap.applyTimer.completed) return
        if (snap.localeStatus != LocaleApplyStatus.SUCCEEDED &&
            snap.localeStatus != LocaleApplyStatus.FAILED_FALLBACK
        ) {
            return
        }
        if (snap.pendingEffect == LanguageNavigationEffect.OPEN_ONBOARDING) return
        if (LanguageNavigationEffect.OPEN_ONBOARDING in claimedEffects.get()) return
        requestEffect(sessionId, LanguageNavigationEffect.OPEN_ONBOARDING)
    }

    private fun requestEffect(
        sessionId: LanguageSessionId,
        effect: LanguageNavigationEffect,
    ) {
        if (effect in claimedEffects.get()) return
        publish {
            if (it.sessionId != sessionId) {
                it
            } else if (it.pendingEffect == effect) {
                it
            } else {
                it.copy(pendingEffect = effect)
            }
        }
        mutableEvents.tryEmit(
            LanguageFlowEvent.EffectRequested(
                sessionId = sessionId,
                effect = effect,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private fun bindPlacementAsync(
        placement: LanguagePlacement,
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ) {
        scope.launch {
            mutex.withLock {
                when (val result = normalAds.bind(configKey, screenInstanceId)) {
                    is NormalScreenBindResult.Bound -> {
                        boundAds.updateAndGet { current -> current + (placement to result.session) }
                        publish { it.copy(placements = currentPlacements(it)) }
                    }
                    is NormalScreenBindResult.Rejected -> {
                        publish {
                            it.copy(
                                placements = currentPlacements(it),
                                debugMessage = "bind $placement rejected: ${result.reason}",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun unbindPlacement(
        placement: LanguagePlacement,
        mode: NormalScreenUnbindMode,
    ) {
        val session = boundAds.get()[placement] ?: return
        boundAds.updateAndGet { current -> current - placement }
        normalAds.unbind(session, mode)
        publish { it.copy(placements = currentPlacements(it)) }
    }

    private fun currentPlacements(snap: LanguageFlowSnapshot): LanguagePlacementSnapshot =
        LanguagePlacementSnapshot(
            loading = normalAds.slotState(LanguageConfigKeys.LOADING, snap.loadingScreenId),
            select = normalAds.slotState(LanguageConfigKeys.SELECT, snap.selectScreenId),
            dup = normalAds.slotState(LanguageConfigKeys.DUP, snap.dupScreenId),
        )

    private fun refreshTimers() {
        publish { snap ->
            val now = clock.nowMillis()
            snap.copy(
                loadingTimer = refreshTimer(snap.loadingTimer, now),
                applyTimer = refreshTimer(snap.applyTimer, now),
                placements = currentPlacements(snap),
            )
        }
        val snap = mutableSnapshot.value ?: return
        if (snap.stage == LanguageStage.LANGUAGE_LOADING &&
            snap.loadingTimer.completed &&
            snap.pendingEffect == null &&
            LanguageNavigationEffect.OPEN_LANGUAGE_SELECT !in claimedEffects.get()
        ) {
            requestEffect(snap.sessionId, LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)
        }
        if (snap.stage == LanguageStage.APPLY_LANGUAGE) {
            maybeCompleteApply(snap.sessionId)
        }
    }

    private fun refreshTimer(
        timer: LanguageTimerSnapshot,
        now: Long,
    ): LanguageTimerSnapshot {
        val deadline = timer.deadlineMillis ?: return timer
        val remaining = (deadline - now).coerceAtLeast(0L)
        return timer.copy(
            remainingMillis = remaining,
            completed = timer.completed || remaining == 0L,
        )
    }

    private fun publish(transform: (LanguageFlowSnapshot) -> LanguageFlowSnapshot) {
        mutableSnapshot.update { current ->
            current?.let(transform)
        }
    }

    private fun emitStage(sessionId: LanguageSessionId, stage: LanguageStage) {
        mutableEvents.tryEmit(
            LanguageFlowEvent.StageChanged(
                sessionId = sessionId,
                stage = stage,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    public companion object {
        public const val LANGUAGE_LOADING_DELAY_MILLIS: Long = 2_000L
        public const val APPLY_LANGUAGE_DELAY_MILLIS: Long = 2_000L
    }
}

private fun <K, V> AtomicReference<Map<K, V>>.updateAndGet(
    transform: (Map<K, V>) -> Map<K, V>,
): Map<K, V> {
    while (true) {
        val current = get()
        val next = transform(current)
        if (compareAndSet(current, next)) {
            return next
        }
    }
}
