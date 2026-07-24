package com.example.adsmodule.core.normal

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.debug.AdsModuleLog
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.load.WeightedLoadRequest
import com.example.adsmodule.core.load.WeightedLoadResult
import com.example.adsmodule.core.refill.AdsConfigSnapshotProvider
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.splash.AudienceEligibility
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StorageSlotKey
import com.example.adsmodule.core.storage.toView
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lifecycle-safe normal-screen Native ownership without Android View types.
 *
 * Sequence: activate slot once → load whole list → putReady → reserve exact slot →
 * markShowing → consume/release + requestRefill.
 */
public class NormalScreenAdCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val loader: WeightedListLoader,
    private val storage: AdStorage,
    private val refillScheduler: WholeListRefillScheduler,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val audience: AudienceType,
) {
    private val slotMutexes = ConcurrentHashMap<StorageSlotKey, Mutex>()
    private val activatedSlots = ConcurrentHashMap.newKeySet<StorageSlotKey>()
    private val loadJobs = ConcurrentHashMap<StorageSlotKey, Job>()
    private val states = ConcurrentHashMap<StorageSlotKey, NormalScreenSlotState>()
    private val bindSessions = ConcurrentHashMap<StorageSlotKey, NormalScreenBindSession>()

    public fun slotState(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ): NormalScreenSlotState {
        val slot = StorageSlotKey(configKey, screenInstanceId)
        return states[slot] ?: NormalScreenSlotState(
            configKey = configKey,
            screenInstanceId = screenInstanceId,
        )
    }

    /**
     * Activates refill bookkeeping once per slot. Safe to call repeatedly.
     */
    public fun ensureActivated(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
        targetReadyCount: Int = 1,
        refillIfDeficit: Boolean = true,
    ): Boolean {
        val slot = StorageSlotKey(configKey, screenInstanceId)
        if (!activatedSlots.add(slot)) {
            return false
        }
        refillScheduler.activate(
            slot = slot,
            targetReadyCount = targetReadyCount,
            refillIfDeficit = refillIfDeficit,
        )
        return true
    }

    /**
     * Ensures the slot is activated and a load cycle is running or complete.
     * Does not block navigation callers that only need fire-and-forget preload.
     */
    public fun ensureLoadedAsync(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ) {
        ensureActivated(configKey, screenInstanceId, refillIfDeficit = false)
        val slot = StorageSlotKey(configKey, screenInstanceId)
        val existing = loadJobs[slot]
        if (existing != null && existing.isActive) {
            AdsModuleLog.preloadStart(configKey, screenInstanceId, alreadyInFlight = true)
            return
        }
        AdsModuleLog.preloadStart(configKey, screenInstanceId, alreadyInFlight = false)
        val job = scope.launch {
            ensureLoaded(configKey, screenInstanceId)
        }
        loadJobs[slot] = job
        job.invokeOnCompletion { loadJobs.remove(slot, job) }
    }

    /** True when storage already holds a READY object for this exact slot. */
    public fun hasReadyObject(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ): Boolean = storage.peekReady(configKey, screenInstanceId) != null

    /**
     * Requests a whole-list refill for [configKey]/[screenInstanceId] without unbinding
     * the currently SHOWING object — used when returning to a previous screen.
     *
     * Does not call [ensureLoaded] (that early-returns while BOUND); refill alone loads
     * a parallel READY that [replaceBoundIfReady] can swap in.
     */
    public fun requestBackgroundReload(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ) {
        ensureActivated(configKey, screenInstanceId, refillIfDeficit = true)
        refillScheduler.requestRefill(StorageSlotKey(configKey, screenInstanceId))
    }
    public suspend fun ensureLoaded(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ): NormalScreenEnsureResult {
        ensureActivated(configKey, screenInstanceId, refillIfDeficit = false)
        val slot = StorageSlotKey(configKey, screenInstanceId)
        return mutexFor(slot).withLock {
            val existingBound = bindSessions[slot]
            if (existingBound != null && !existingBound.finished.get()) {
                val state = states[slot] ?: NormalScreenSlotState(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    status = NormalScreenLoadStatus.BOUND,
                    storedAd = existingBound.storedAd,
                    reservationId = existingBound.reservationId,
                )
                AdsModuleLog.readyOk(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    objectId = existingBound.objectId.value,
                    elapsedMs = 0L,
                    cacheHit = true,
                )
                return@withLock NormalScreenEnsureResult.Ready(state)
            }

            val peeked = storage.peekReady(configKey, screenInstanceId)
            if (peeked != null) {
                val ready = NormalScreenSlotState(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    status = NormalScreenLoadStatus.READY,
                    storedAd = peeked.toView(),
                )
                states[slot] = ready
                AdsModuleLog.readyOk(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    objectId = peeked.objectId.value,
                    elapsedMs = 0L,
                    cacheHit = true,
                )
                return@withLock NormalScreenEnsureResult.Ready(ready)
            }

            val snapshot = snapshotProvider.current()
            if (snapshot == null) {
                val failed = NormalScreenSlotState(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    status = NormalScreenLoadStatus.FAILED,
                    reason = "missing snapshot",
                )
                states[slot] = failed
                AdsModuleLog.readyFail(
                    configKey, screenInstanceId, failed.status.name, 0L, failed.reason,
                )
                return@withLock NormalScreenEnsureResult.Terminal(failed)
            }

            val ads = snapshot.adsConfig(configKey)
            if (ads == null) {
                val failed = NormalScreenSlotState(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    status = NormalScreenLoadStatus.FAILED,
                    reason = "missing config",
                )
                states[slot] = failed
                AdsModuleLog.readyFail(
                    configKey, screenInstanceId, failed.status.name, 0L, failed.reason,
                )
                return@withLock NormalScreenEnsureResult.Terminal(failed)
            }
            if (!ads.enable) {
                val disabled = NormalScreenSlotState(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    status = NormalScreenLoadStatus.DISABLED,
                    reason = "enable=false",
                )
                states[slot] = disabled
                AdsModuleLog.readyFail(
                    configKey, screenInstanceId, disabled.status.name, 0L, disabled.reason,
                )
                return@withLock NormalScreenEnsureResult.Terminal(disabled)
            }
            if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) {
                val ineligible = NormalScreenSlotState(
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    status = NormalScreenLoadStatus.INELIGIBLE,
                    reason = "audience ineligible",
                )
                states[slot] = ineligible
                AdsModuleLog.readyFail(
                    configKey, screenInstanceId, ineligible.status.name, 0L, ineligible.reason,
                )
                return@withLock NormalScreenEnsureResult.Terminal(ineligible)
            }

            val cycleId = LoadCycleId(idGenerator.nextId())
            states[slot] = NormalScreenSlotState(
                configKey = configKey,
                screenInstanceId = screenInstanceId,
                status = NormalScreenLoadStatus.LOADING,
                cycleId = cycleId,
            )

            val loadStartedAt = clock.nowMillis()
            val result = loader.load(
                WeightedLoadRequest(
                    cycleId = cycleId,
                    configKey = configKey,
                    screenInstanceId = screenInstanceId,
                    snapshot = snapshot,
                ),
            )
            val elapsedMs = (clock.nowMillis() - loadStartedAt).coerceAtLeast(0L)

            when (result) {
                is WeightedLoadResult.Success -> {
                    when (val put = storage.putReady(result.storedAd)) {
                        is PutResult.Accepted -> {
                            val ready = NormalScreenSlotState(
                                configKey = configKey,
                                screenInstanceId = screenInstanceId,
                                status = NormalScreenLoadStatus.READY,
                                cycleId = cycleId,
                                storedAd = put.storedAd,
                            )
                            states[slot] = ready
                            AdsModuleLog.readyOk(
                                configKey = configKey,
                                screenInstanceId = screenInstanceId,
                                objectId = put.storedAd.objectId.value,
                                elapsedMs = elapsedMs,
                                cacheHit = false,
                            )
                            NormalScreenEnsureResult.Ready(ready)
                        }
                        is PutResult.Rejected -> {
                            result.storedAd.sdkHandle.destroy()
                            val failed = NormalScreenSlotState(
                                configKey = configKey,
                                screenInstanceId = screenInstanceId,
                                status = NormalScreenLoadStatus.FAILED,
                                cycleId = cycleId,
                                reason = put.reason,
                                terminalReason = result.reason,
                            )
                            states[slot] = failed
                            AdsModuleLog.readyFail(
                                configKey, screenInstanceId, failed.status.name, elapsedMs, put.reason,
                            )
                            NormalScreenEnsureResult.Terminal(failed)
                        }
                    }
                }
                is WeightedLoadResult.Disabled -> {
                    val disabled = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.DISABLED,
                        cycleId = cycleId,
                        terminalReason = result.reason,
                    )
                    states[slot] = disabled
                    AdsModuleLog.readyFail(
                        configKey, screenInstanceId, disabled.status.name, elapsedMs, result.reason.name,
                    )
                    NormalScreenEnsureResult.Terminal(disabled)
                }
                is WeightedLoadResult.Exhausted -> {
                    val exhausted = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.EXHAUSTED,
                        cycleId = cycleId,
                        terminalReason = result.reason,
                    )
                    states[slot] = exhausted
                    AdsModuleLog.readyFail(
                        configKey, screenInstanceId, exhausted.status.name, elapsedMs, result.reason.name,
                    )
                    NormalScreenEnsureResult.Terminal(exhausted)
                }
                is WeightedLoadResult.TotalTimeout -> {
                    val failed = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.FAILED,
                        cycleId = cycleId,
                        reason = "timeout_total",
                        terminalReason = result.reason,
                    )
                    states[slot] = failed
                    AdsModuleLog.readyFail(
                        configKey, screenInstanceId, failed.status.name, elapsedMs, failed.reason,
                    )
                    NormalScreenEnsureResult.Terminal(failed)
                }
                is WeightedLoadResult.Cancelled -> {
                    val cancelled = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.CANCELLED,
                        cycleId = cycleId,
                        terminalReason = result.reason,
                    )
                    states[slot] = cancelled
                    AdsModuleLog.readyFail(
                        configKey, screenInstanceId, cancelled.status.name, elapsedMs, result.reason.name,
                    )
                    NormalScreenEnsureResult.Terminal(cancelled)
                }
                is WeightedLoadResult.MissingConfig -> {
                    val failed = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.FAILED,
                        cycleId = cycleId,
                        reason = "missing config",
                        terminalReason = result.reason,
                    )
                    states[slot] = failed
                    AdsModuleLog.readyFail(
                        configKey, screenInstanceId, failed.status.name, elapsedMs, failed.reason,
                    )
                    NormalScreenEnsureResult.Terminal(failed)
                }
            }
        }
    }

    /**
     * Reserves the exact config/screen slot and marks it showing.
     * Wrong-placement callers must pass their own keys; this never turnback-borrows.
     */
    public suspend fun bind(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ): NormalScreenBindResult {
        ensureLoaded(configKey, screenInstanceId)
        val slot = StorageSlotKey(configKey, screenInstanceId)
        val result = mutexFor(slot).withLock {
            val existing = bindSessions[slot]
            if (existing != null && !existing.finished.get()) {
                return@withLock NormalScreenBindResult.Bound(
                    session = existing,
                    state = states[slot] ?: NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.BOUND,
                        storedAd = existing.storedAd,
                        reservationId = existing.reservationId,
                    ),
                )
            }

            when (val reserved = storage.reserveNormal(configKey, screenInstanceId)) {
                is ReserveResult.Rejected -> {
                    val current = states[slot] ?: NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.FAILED,
                        reason = reserved.reason,
                    )
                    NormalScreenBindResult.Rejected(reserved.reason, current)
                }
                is ReserveResult.Accepted -> {
                    if (reserved.storedAd.sourceConfigKey != configKey ||
                        reserved.storedAd.screenInstanceId != screenInstanceId
                    ) {
                        storage.release(reserved.reservation.reservationId)
                        return@withLock NormalScreenBindResult.Rejected(
                            reason = "reserved object placement mismatch",
                            state = states[slot],
                        )
                    }
                    if (!storage.markShowing(reserved.reservation.reservationId)) {
                        storage.release(reserved.reservation.reservationId)
                        return@withLock NormalScreenBindResult.Rejected(
                            reason = "unable to markShowing",
                            state = states[slot],
                        )
                    }
                    refillScheduler.requestRefill(slot)
                    val session = NormalScreenBindSession(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        reservationId = reserved.reservation.reservationId,
                        objectId = reserved.storedAd.objectId,
                        storedAd = reserved.storedAd,
                        boundAtMillis = clock.nowMillis(),
                    )
                    bindSessions[slot] = session
                    val bound = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.BOUND,
                        storedAd = reserved.storedAd,
                        reservationId = reserved.reservation.reservationId,
                    )
                    states[slot] = bound
                    NormalScreenBindResult.Bound(session, bound)
                }
            }
        }
        logBindResult(configKey, screenInstanceId, result, replace = false)
        return result
    }

    /**
     * If a newer READY object exists for the slot while one is already SHOWING,
     * reserves and marks the new object SHOWING and returns it as [NormalScreenBindResult.Bound]
     * with [NormalScreenBindResult.Bound.previousSession] set.
     *
     * Caller must swap the UI to the new session first, then [unbind] the previous
     * session with [NormalScreenUnbindMode.CONSUME] so the old sdkHandle is destroyed
     * only after the swap (no blank gap).
     *
     * If nothing newer is READY, returns the existing bound session (or Rejected).
     */
    public suspend fun replaceBoundIfReady(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
    ): NormalScreenBindResult {
        val slot = StorageSlotKey(configKey, screenInstanceId)
        val result = mutexFor(slot).withLock {
            val current = bindSessions[slot]?.takeIf { !it.finished.get() }
            val peeked = storage.peekReady(configKey, screenInstanceId)
            if (peeked == null) {
                return@withLock if (current != null) {
                    NormalScreenBindResult.Bound(
                        session = current,
                        state = states[slot] ?: NormalScreenSlotState(
                            configKey = configKey,
                            screenInstanceId = screenInstanceId,
                            status = NormalScreenLoadStatus.BOUND,
                            storedAd = current.storedAd,
                            reservationId = current.reservationId,
                        ),
                    )
                } else {
                    NormalScreenBindResult.Rejected(
                        reason = "no ready replacement",
                        state = states[slot],
                    )
                }
            }
            if (current != null && peeked.objectId == current.objectId) {
                return@withLock NormalScreenBindResult.Bound(
                    session = current,
                    state = states[slot] ?: NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.BOUND,
                        storedAd = current.storedAd,
                        reservationId = current.reservationId,
                    ),
                )
            }

            when (val reserved = storage.reserveNormal(configKey, screenInstanceId)) {
                is ReserveResult.Rejected -> {
                    if (current != null) {
                        NormalScreenBindResult.Bound(
                            session = current,
                            state = states[slot] ?: NormalScreenSlotState(
                                configKey = configKey,
                                screenInstanceId = screenInstanceId,
                                status = NormalScreenLoadStatus.BOUND,
                                storedAd = current.storedAd,
                                reservationId = current.reservationId,
                            ),
                        )
                    } else {
                        NormalScreenBindResult.Rejected(reserved.reason, states[slot])
                    }
                }
                is ReserveResult.Accepted -> {
                    if (reserved.storedAd.sourceConfigKey != configKey ||
                        reserved.storedAd.screenInstanceId != screenInstanceId
                    ) {
                        storage.release(reserved.reservation.reservationId)
                        return@withLock if (current != null) {
                            NormalScreenBindResult.Bound(
                                session = current,
                                state = states[slot] ?: NormalScreenSlotState(
                                    configKey = configKey,
                                    screenInstanceId = screenInstanceId,
                                    status = NormalScreenLoadStatus.BOUND,
                                    storedAd = current.storedAd,
                                    reservationId = current.reservationId,
                                ),
                            )
                        } else {
                            NormalScreenBindResult.Rejected(
                                reason = "reserved object placement mismatch",
                                state = states[slot],
                            )
                        }
                    }
                    if (!storage.markShowing(reserved.reservation.reservationId)) {
                        storage.release(reserved.reservation.reservationId)
                        return@withLock if (current != null) {
                            NormalScreenBindResult.Bound(
                                session = current,
                                state = states[slot] ?: NormalScreenSlotState(
                                    configKey = configKey,
                                    screenInstanceId = screenInstanceId,
                                    status = NormalScreenLoadStatus.BOUND,
                                    storedAd = current.storedAd,
                                    reservationId = current.reservationId,
                                ),
                            )
                        } else {
                            NormalScreenBindResult.Rejected(
                                reason = "unable to markShowing",
                                state = states[slot],
                            )
                        }
                    }
                    refillScheduler.requestRefill(slot)
                    val previous = current?.also {
                        // Detach from slot map but leave finished=false so caller can CONSUME.
                        bindSessions.remove(slot, it)
                    }
                    val session = NormalScreenBindSession(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        reservationId = reserved.reservation.reservationId,
                        objectId = reserved.storedAd.objectId,
                        storedAd = reserved.storedAd,
                        boundAtMillis = clock.nowMillis(),
                    )
                    bindSessions[slot] = session
                    val bound = NormalScreenSlotState(
                        configKey = configKey,
                        screenInstanceId = screenInstanceId,
                        status = NormalScreenLoadStatus.BOUND,
                        storedAd = reserved.storedAd,
                        reservationId = reserved.reservation.reservationId,
                    )
                    states[slot] = bound
                    NormalScreenBindResult.Bound(
                        session = session,
                        state = bound,
                        previousSession = previous,
                    )
                }
            }
        }
        if (result is NormalScreenBindResult.Bound && result.previousSession != null) {
            logBindResult(configKey, screenInstanceId, result, replace = true)
        }
        return result
    }

    private fun logBindResult(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId,
        result: NormalScreenBindResult,
        replace: Boolean,
    ) {
        when (result) {
            is NormalScreenBindResult.Bound -> AdsModuleLog.bind(
                configKey = configKey,
                screenInstanceId = screenInstanceId,
                obtained = true,
                objectId = result.session.objectId.value,
                replace = replace,
            )
            is NormalScreenBindResult.Rejected -> AdsModuleLog.bind(
                configKey = configKey,
                screenInstanceId = screenInstanceId,
                obtained = false,
                reason = result.reason,
                replace = replace,
            )
        }
    }

    public fun unbind(
        session: NormalScreenBindSession,
        mode: NormalScreenUnbindMode,
    ): NormalScreenUnbindResult {
        if (!session.finished.compareAndSet(false, true)) {
            return NormalScreenUnbindResult.AlreadyFinished(session.objectId)
        }
        val slot = StorageSlotKey(session.configKey, session.screenInstanceId)
        bindSessions.remove(slot, session)
        return when (mode) {
            NormalScreenUnbindMode.CONSUME -> {
                val consumed =
                    storage.consume(session.reservationId) || storage.consume(session.objectId)
                refillScheduler.requestRefill(slot)
                states[slot] = NormalScreenSlotState(
                    configKey = session.configKey,
                    screenInstanceId = session.screenInstanceId,
                    status = if (consumed) {
                        NormalScreenLoadStatus.IDLE
                    } else {
                        NormalScreenLoadStatus.FAILED
                    },
                    reason = if (consumed) null else "consume failed",
                )
                NormalScreenUnbindResult.Consumed(session.objectId)
            }
            NormalScreenUnbindMode.RELEASE -> {
                storage.release(session.reservationId)
                refillScheduler.requestRefill(slot)
                val peeked = storage.peekReady(session.configKey, session.screenInstanceId)
                states[slot] = NormalScreenSlotState(
                    configKey = session.configKey,
                    screenInstanceId = session.screenInstanceId,
                    status = if (peeked != null) {
                        NormalScreenLoadStatus.READY
                    } else {
                        NormalScreenLoadStatus.IDLE
                    },
                    storedAd = peeked?.toView(),
                )
                NormalScreenUnbindResult.Released(session.objectId)
            }
        }
    }

    private fun mutexFor(slot: StorageSlotKey): Mutex =
        slotMutexes.getOrPut(slot) { Mutex() }
}
