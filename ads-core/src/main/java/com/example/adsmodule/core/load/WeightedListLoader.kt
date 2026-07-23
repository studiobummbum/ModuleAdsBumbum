package com.example.adsmodule.core.load

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.LoadRequestId
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.config.ConfigKeyRegistry
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdRequestMetadata
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Sequential weighted loader over one original `list_ads`.
 *
 * Invariants:
 * - one SDK request per [ConfigKey] at a time (cycles for the same key are serialized)
 * - never hold the config mutex while calling into an adapter
 * - first accepted success stops the cycle
 * - stale successes destroy the handle and never insert storage
 */
public class WeightedListLoader(
    private val adapterRegistry: AdSdkAdapterRegistry,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    private val configMutexes = ConcurrentHashMap<ConfigKey, Mutex>()
    private val activeCycles = ConcurrentHashMap<LoadCycleId, CycleRuntime>()
    private val mutableDebugStates =
        MutableStateFlow<Map<LoadCycleId, WeightedLoadDebugState>>(emptyMap())
    private val mutableStaleEvents = MutableSharedFlow<WeightedLoadStaleEvent>(
        replay = 0,
        extraBufferCapacity = STALE_EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    public val debugStates: StateFlow<Map<LoadCycleId, WeightedLoadDebugState>> =
        mutableDebugStates.asStateFlow()

    public val staleEvents: SharedFlow<WeightedLoadStaleEvent> =
        mutableStaleEvents.asSharedFlow()

    public suspend fun deactivate(cycleId: LoadCycleId): Boolean {
        val runtime = activeCycles[cycleId] ?: return false
        if (!runtime.deactivated.compareAndSet(false, true)) {
            return false
        }
        val loadJob = runtime.currentLoadJob.get()
        val callerJob = currentCoroutineContext()[Job]
        if (loadJob != null && loadJob != callerJob) {
            loadJob.cancel(CancellationException("Weighted load cycle deactivated"))
        }
        publishDebug(runtime) { state ->
            state.copy(
                elapsedMillis = elapsed(runtime.cycleStartedAt),
                isActive = false,
                terminalReason = state.terminalReason ?: WeightedLoadTerminalReason.CANCELLED,
            )
        }
        return true
    }

    public suspend fun load(request: WeightedLoadRequest): WeightedLoadResult {
        val existing = activeCycles.putIfAbsent(
            request.cycleId,
            CycleRuntime(
                cycleId = request.cycleId,
                configKey = request.configKey,
                screenInstanceId = request.screenInstanceId,
                snapshotVersion = request.snapshot.version,
                snapshotContentHash = request.snapshot.contentHash,
                cycleStartedAt = clock.nowMillis(),
            ),
        )
        require(existing == null) {
            "LoadCycleId ${request.cycleId.value} is already active"
        }
        val runtime = checkNotNull(activeCycles[request.cycleId])

        return try {
            mutexFor(request.configKey).withLock {
                runCycleLocked(request, runtime)
            }
        } catch (cancellation: CancellationException) {
            if (runtime.deactivated.get()) {
                finalizeTerminal(
                    runtime = runtime,
                    result = WeightedLoadResult.Cancelled(
                        cycleId = request.cycleId,
                        configKey = request.configKey,
                        attempts = runtime.attempts.toList(),
                    ),
                )
            } else {
                publishDebug(runtime) { state ->
                    state.copy(
                        elapsedMillis = elapsed(runtime.cycleStartedAt),
                        isActive = false,
                        terminalReason = WeightedLoadTerminalReason.CANCELLED,
                        activeRequest = null,
                        currentRuntimeIndex = null,
                        currentOriginalIndex = null,
                    )
                }
                throw cancellation
            }
        } finally {
            activeCycles.remove(request.cycleId, runtime)
        }
    }

    private suspend fun runCycleLocked(
        request: WeightedLoadRequest,
        runtime: CycleRuntime,
    ): WeightedLoadResult {
        val config = request.snapshot.adsConfig(request.configKey)
        if (config == null) {
            publishInitial(runtime, orderedItems = emptyList())
            return finalizeTerminal(
                runtime = runtime,
                result = WeightedLoadResult.MissingConfig(
                    cycleId = request.cycleId,
                    configKey = request.configKey,
                ),
            )
        }
        if (!config.enable) {
            publishInitial(runtime, orderedItems = emptyList())
            return finalizeTerminal(
                runtime = runtime,
                result = WeightedLoadResult.Disabled(
                    cycleId = request.cycleId,
                    configKey = request.configKey,
                ),
            )
        }

        val orderedItems = buildOrderedItems(request.configKey, config)
        publishInitial(runtime, orderedItems)
        if (orderedItems.isEmpty()) {
            return finalizeTerminal(
                runtime = runtime,
                result = WeightedLoadResult.Exhausted(
                    cycleId = request.cycleId,
                    configKey = request.configKey,
                    attempts = emptyList(),
                ),
            )
        }

        val totalTimeoutMillis = config.timeoutTotalMillis
        val cycleDeadline = totalTimeoutMillis?.let { runtime.cycleStartedAt + it }

        orderedItems.forEachIndexed { runtimeIndex, item ->
            if (runtime.deactivated.get()) {
                return finalizeTerminal(
                    runtime = runtime,
                    result = WeightedLoadResult.Cancelled(
                        cycleId = request.cycleId,
                        configKey = request.configKey,
                        attempts = runtime.attempts.toList(),
                    ),
                )
            }

            val nowBeforeItem = clock.nowMillis()
            if (cycleDeadline != null && nowBeforeItem >= cycleDeadline) {
                return finalizeTerminal(
                    runtime = runtime,
                    result = WeightedLoadResult.TotalTimeout(
                        cycleId = request.cycleId,
                        configKey = request.configKey,
                        attempts = runtime.attempts.toList(),
                    ),
                )
            }

            val remainingTotal = cycleDeadline?.let { deadline ->
                (deadline - nowBeforeItem).coerceAtLeast(0L)
            }
            val effectiveTimeout = effectiveTimeout(
                itemTimeoutMillis = item.timeoutMillis,
                remainingTotalMillis = remainingTotal,
            )
            val totalBoundRequest = remainingTotal != null &&
                (item.timeoutMillis == null || remainingTotal <= item.timeoutMillis)

            val requestId = LoadRequestId(idGenerator.nextId())
            val context = WeightedRequestContext(
                cycleId = request.cycleId,
                requestId = requestId,
                configKey = request.configKey,
                screenInstanceId = request.screenInstanceId,
                itemIndex = item.originalIndex,
                runtimeIndex = runtimeIndex,
                type = item.type,
                format = item.resolvedFormat,
                adunit = item.adunit,
                weight = item.weight,
                snapshotVersion = runtime.snapshotVersion,
                snapshotContentHash = runtime.snapshotContentHash,
                startedAt = nowBeforeItem,
            )
            runtime.activeRequestId.set(requestId)
            publishDebug(runtime) { state ->
                state.copy(
                    currentRuntimeIndex = runtimeIndex,
                    currentOriginalIndex = item.originalIndex,
                    activeRequest = context,
                    elapsedMillis = elapsed(runtime.cycleStartedAt),
                )
            }

            val adapter = adapterRegistry.adapterFor(item.resolvedFormat)
            if (adapter == null) {
                recordAttempt(
                    runtime = runtime,
                    runtimeIndex = runtimeIndex,
                    item = item,
                    outcome = WeightedItemAttemptOutcome.MISSING_ADAPTER,
                    reason = "No adapter for ${item.resolvedFormat}",
                    startedAt = nowBeforeItem,
                )
                clearActiveRequest(runtime)
                return@forEachIndexed
            }

            val loadResult = try {
                invokeAdapter(
                    runtime = runtime,
                    timeoutMillis = effectiveTimeout,
                ) {
                    adapter.load(
                        AdLoadRequest(
                            loadRequestId = requestId.value,
                            format = item.resolvedFormat,
                            adUnit = item.adunit,
                            timeoutMillis = item.timeoutMillis,
                            metadata = AdRequestMetadata(
                                sourceConfigKey = request.configKey.value,
                                sourceListIndex = item.originalIndex,
                            ),
                        ),
                    )
                }
            } catch (timeout: TimeoutCancellationException) {
                if (runtime.deactivated.get()) {
                    recordAttempt(
                        runtime = runtime,
                        runtimeIndex = runtimeIndex,
                        item = item,
                        outcome = WeightedItemAttemptOutcome.CANCELLED,
                        reason = "Cycle deactivated during timeout",
                        startedAt = nowBeforeItem,
                    )
                    clearActiveRequest(runtime)
                    return finalizeTerminal(
                        runtime = runtime,
                        result = WeightedLoadResult.Cancelled(
                            cycleId = request.cycleId,
                            configKey = request.configKey,
                            attempts = runtime.attempts.toList(),
                        ),
                    )
                }
                if (totalBoundRequest ||
                    (cycleDeadline != null && clock.nowMillis() >= cycleDeadline)
                ) {
                    recordAttempt(
                        runtime = runtime,
                        runtimeIndex = runtimeIndex,
                        item = item,
                        outcome = WeightedItemAttemptOutcome.TIMEOUT,
                        reason = "timeout_total exceeded",
                        startedAt = nowBeforeItem,
                    )
                    clearActiveRequest(runtime)
                    return finalizeTerminal(
                        runtime = runtime,
                        result = WeightedLoadResult.TotalTimeout(
                            cycleId = request.cycleId,
                            configKey = request.configKey,
                            attempts = runtime.attempts.toList(),
                        ),
                    )
                }
                recordAttempt(
                    runtime = runtime,
                    runtimeIndex = runtimeIndex,
                    item = item,
                    outcome = WeightedItemAttemptOutcome.TIMEOUT,
                    reason = "item timeout exceeded",
                    startedAt = nowBeforeItem,
                )
                clearActiveRequest(runtime)
                return@forEachIndexed
            } catch (cancellation: CancellationException) {
                recordAttempt(
                    runtime = runtime,
                    runtimeIndex = runtimeIndex,
                    item = item,
                    outcome = WeightedItemAttemptOutcome.CANCELLED,
                    reason = cancellation.message,
                    startedAt = nowBeforeItem,
                )
                clearActiveRequest(runtime)
                if (runtime.deactivated.get()) {
                    return finalizeTerminal(
                        runtime = runtime,
                        result = WeightedLoadResult.Cancelled(
                            cycleId = request.cycleId,
                            configKey = request.configKey,
                            attempts = runtime.attempts.toList(),
                        ),
                    )
                }
                throw cancellation
            }

            when (loadResult) {
                is AdLoadResult.Success -> {
                    val acceptance = acceptSuccess(
                        runtime = runtime,
                        context = context,
                        handle = loadResult.handle,
                    )
                    when (acceptance) {
                        is SuccessAcceptance.Accepted -> {
                            recordAttempt(
                                runtime = runtime,
                                runtimeIndex = runtimeIndex,
                                item = item,
                                outcome = WeightedItemAttemptOutcome.SUCCESS,
                                startedAt = nowBeforeItem,
                            )
                            clearActiveRequest(runtime)
                            return finalizeTerminal(
                                runtime = runtime,
                                result = WeightedLoadResult.Success(
                                    cycleId = request.cycleId,
                                    configKey = request.configKey,
                                    storedAd = acceptance.storedAd,
                                    context = context,
                                ),
                            )
                        }
                        is SuccessAcceptance.Stale -> {
                            recordAttempt(
                                runtime = runtime,
                                runtimeIndex = runtimeIndex,
                                item = item,
                                outcome = WeightedItemAttemptOutcome.STALE,
                                reason = acceptance.mismatch,
                                startedAt = nowBeforeItem,
                            )
                            clearActiveRequest(runtime)
                            if (runtime.deactivated.get()) {
                                return finalizeTerminal(
                                    runtime = runtime,
                                    result = WeightedLoadResult.Cancelled(
                                        cycleId = request.cycleId,
                                        configKey = request.configKey,
                                        attempts = runtime.attempts.toList(),
                                    ),
                                )
                            }
                            return@forEachIndexed
                        }
                    }
                }
                is AdLoadResult.Failure -> {
                    recordAttempt(
                        runtime = runtime,
                        runtimeIndex = runtimeIndex,
                        item = item,
                        outcome = WeightedItemAttemptOutcome.FAILURE,
                        reason = loadResult.reason,
                        startedAt = nowBeforeItem,
                    )
                    clearActiveRequest(runtime)
                }
                AdLoadResult.Timeout -> {
                    if (cycleDeadline != null && clock.nowMillis() >= cycleDeadline) {
                        recordAttempt(
                            runtime = runtime,
                            runtimeIndex = runtimeIndex,
                            item = item,
                            outcome = WeightedItemAttemptOutcome.TIMEOUT,
                            reason = "adapter timeout after timeout_total",
                            startedAt = nowBeforeItem,
                        )
                        clearActiveRequest(runtime)
                        return finalizeTerminal(
                            runtime = runtime,
                            result = WeightedLoadResult.TotalTimeout(
                                cycleId = request.cycleId,
                                configKey = request.configKey,
                                attempts = runtime.attempts.toList(),
                            ),
                        )
                    }
                    recordAttempt(
                        runtime = runtime,
                        runtimeIndex = runtimeIndex,
                        item = item,
                        outcome = WeightedItemAttemptOutcome.TIMEOUT,
                        reason = "adapter timeout",
                        startedAt = nowBeforeItem,
                    )
                    clearActiveRequest(runtime)
                }
            }
            yield()
        }

        if (runtime.deactivated.get()) {
            return finalizeTerminal(
                runtime = runtime,
                result = WeightedLoadResult.Cancelled(
                    cycleId = request.cycleId,
                    configKey = request.configKey,
                    attempts = runtime.attempts.toList(),
                ),
            )
        }
        if (cycleDeadline != null && clock.nowMillis() >= cycleDeadline) {
            return finalizeTerminal(
                runtime = runtime,
                result = WeightedLoadResult.TotalTimeout(
                    cycleId = request.cycleId,
                    configKey = request.configKey,
                    attempts = runtime.attempts.toList(),
                ),
            )
        }
        return finalizeTerminal(
            runtime = runtime,
            result = WeightedLoadResult.Exhausted(
                cycleId = request.cycleId,
                configKey = request.configKey,
                attempts = runtime.attempts.toList(),
            ),
        )
    }

    private suspend fun invokeAdapter(
        runtime: CycleRuntime,
        timeoutMillis: Long?,
        block: suspend () -> AdLoadResult,
    ): AdLoadResult = coroutineScope {
        val deferred = async { block() }
        runtime.currentLoadJob.set(deferred)
        try {
            if (timeoutMillis == null) {
                deferred.await()
            } else {
                withTimeout(timeoutMillis) { deferred.await() }
            }
        } finally {
            runtime.currentLoadJob.compareAndSet(deferred, null)
            if (deferred.isActive) {
                deferred.cancel(CancellationException("Weighted load request finished"))
            }
        }
    }

    private fun acceptSuccess(
        runtime: CycleRuntime,
        context: WeightedRequestContext,
        handle: SdkLoadedAdHandle,
    ): SuccessAcceptance {
        if (runtime.deactivated.get()) {
            return rejectStale(
                runtime = runtime,
                context = context,
                handle = handle,
                mismatch = "cycle deactivated",
            )
        }
        if (runtime.activeRequestId.get() != context.requestId) {
            return rejectStale(
                runtime = runtime,
                context = context,
                handle = handle,
                mismatch = "requestId mismatch",
            )
        }
        if (runtime.configKey != context.configKey) {
            return rejectStale(
                runtime = runtime,
                context = context,
                handle = handle,
                mismatch = "configKey mismatch",
            )
        }
        if (runtime.screenInstanceId != context.screenInstanceId) {
            return rejectStale(
                runtime = runtime,
                context = context,
                handle = handle,
                mismatch = "screenInstanceId mismatch",
            )
        }
        if (
            runtime.snapshotVersion != context.snapshotVersion ||
            runtime.snapshotContentHash != context.snapshotContentHash
        ) {
            return rejectStale(
                runtime = runtime,
                context = context,
                handle = handle,
                mismatch = "snapshot identity mismatch",
            )
        }
        if (!activeCycles.containsKey(runtime.cycleId)) {
            return rejectStale(
                runtime = runtime,
                context = context,
                handle = handle,
                mismatch = "cycle no longer active",
            )
        }

        val loadedAt = clock.nowMillis()
        val storedAd = StoredAd(
            objectId = ObjectId(idGenerator.nextId()),
            sourceConfigKey = context.configKey,
            sourceListIndex = context.itemIndex,
            sourceType = context.format,
            sourceAdunit = context.adunit,
            sourceWeight = context.weight,
            screenInstanceId = context.screenInstanceId,
            loadedAt = loadedAt,
            state = AdSlotState.READY,
            sdkHandle = handle,
        )
        return SuccessAcceptance.Accepted(storedAd)
    }

    private fun rejectStale(
        runtime: CycleRuntime,
        context: WeightedRequestContext,
        handle: SdkLoadedAdHandle,
        mismatch: String,
    ): SuccessAcceptance.Stale {
        handle.destroy()
        val event = WeightedLoadStaleEvent(
            cycleId = context.cycleId,
            requestId = context.requestId,
            configKey = context.configKey,
            screenInstanceId = context.screenInstanceId,
            snapshotVersion = context.snapshotVersion,
            mismatch = mismatch,
            occurredAt = clock.nowMillis(),
        )
        mutableStaleEvents.tryEmit(event)
        publishDebug(runtime) { state ->
            state.copy(elapsedMillis = elapsed(runtime.cycleStartedAt))
        }
        return SuccessAcceptance.Stale(mismatch)
    }

    private fun publishInitial(
        runtime: CycleRuntime,
        orderedItems: List<RuntimeAdItem>,
    ) {
        mutableDebugStates.update { current ->
            current + (
                runtime.cycleId to WeightedLoadDebugState(
                    cycleId = runtime.cycleId,
                    configKey = runtime.configKey,
                    screenInstanceId = runtime.screenInstanceId,
                    snapshotVersion = runtime.snapshotVersion,
                    snapshotContentHash = runtime.snapshotContentHash,
                    cycleStartedAt = runtime.cycleStartedAt,
                    orderedItems = orderedItems,
                    currentRuntimeIndex = null,
                    currentOriginalIndex = null,
                    activeRequest = null,
                    elapsedMillis = 0L,
                    attempts = emptyList(),
                    terminalReason = null,
                    isActive = true,
                )
                )
        }
    }

    private fun recordAttempt(
        runtime: CycleRuntime,
        runtimeIndex: Int,
        item: RuntimeAdItem,
        outcome: WeightedItemAttemptOutcome,
        reason: String? = null,
        startedAt: Long,
    ) {
        val attempt = WeightedItemAttemptResult(
            runtimeIndex = runtimeIndex,
            originalIndex = item.originalIndex,
            format = item.resolvedFormat,
            adunit = item.adunit,
            weight = item.weight,
            outcome = outcome,
            reason = reason,
            elapsedMillis = (clock.nowMillis() - startedAt).coerceAtLeast(0L),
        )
        runtime.attempts += attempt
        publishDebug(runtime) { state ->
            state.copy(
                attempts = runtime.attempts.toList(),
                elapsedMillis = elapsed(runtime.cycleStartedAt),
            )
        }
    }

    private fun clearActiveRequest(runtime: CycleRuntime) {
        runtime.activeRequestId.set(null)
        publishDebug(runtime) { state ->
            state.copy(
                activeRequest = null,
                elapsedMillis = elapsed(runtime.cycleStartedAt),
            )
        }
    }

    private fun finalizeTerminal(
        runtime: CycleRuntime,
        result: WeightedLoadResult,
    ): WeightedLoadResult {
        publishDebug(runtime) { state ->
            state.copy(
                elapsedMillis = elapsed(runtime.cycleStartedAt),
                attempts = runtime.attempts.toList(),
                terminalReason = result.reason,
                isActive = false,
                activeRequest = null,
                currentRuntimeIndex = null,
                currentOriginalIndex = null,
            )
        }
        return result
    }

    private fun publishDebug(
        runtime: CycleRuntime,
        transform: (WeightedLoadDebugState) -> WeightedLoadDebugState,
    ) {
        mutableDebugStates.update { current ->
            val existing = current[runtime.cycleId] ?: WeightedLoadDebugState(
                cycleId = runtime.cycleId,
                configKey = runtime.configKey,
                screenInstanceId = runtime.screenInstanceId,
                snapshotVersion = runtime.snapshotVersion,
                snapshotContentHash = runtime.snapshotContentHash,
                cycleStartedAt = runtime.cycleStartedAt,
                orderedItems = emptyList(),
                currentRuntimeIndex = null,
                currentOriginalIndex = null,
                activeRequest = null,
                elapsedMillis = elapsed(runtime.cycleStartedAt),
                attempts = runtime.attempts.toList(),
                terminalReason = null,
                isActive = !runtime.deactivated.get(),
            )
            current + (runtime.cycleId to transform(existing))
        }
    }

    private fun elapsed(cycleStartedAt: Long): Long =
        (clock.nowMillis() - cycleStartedAt).coerceAtLeast(0L)

    private fun mutexFor(configKey: ConfigKey): Mutex =
        configMutexes.getOrPut(configKey) { Mutex() }

    private companion object {
        private const val STALE_EVENT_BUFFER: Int = 64

        private fun buildOrderedItems(
            configKey: ConfigKey,
            config: OriginalAdsConfig,
        ): List<RuntimeAdItem> =
            config.listAds
                .mapIndexed { index, item -> toRuntimeItem(configKey, index, item) }
                .filter { it.enableAd }
                .sortedWith(
                    compareByDescending<RuntimeAdItem> { it.weight }
                        .thenBy { it.originalIndex },
                )

        private fun toRuntimeItem(
            configKey: ConfigKey,
            mappedIndex: Int,
            item: OriginalAdItem,
        ): RuntimeAdItem {
            val originalIndex = if (item.sourceListIndex >= 0) {
                item.sourceListIndex
            } else {
                mappedIndex
            }
            return RuntimeAdItem(
                originalIndex = originalIndex,
                enableAd = item.enableAd,
                weight = item.weight,
                timeoutMillis = item.timeoutMillis,
                type = item.type,
                adunit = item.adunit,
                resolvedFormat = ConfigKeyRegistry.resolveAdFormat(configKey, item.type),
            )
        }

        private fun effectiveTimeout(
            itemTimeoutMillis: Long?,
            remainingTotalMillis: Long?,
        ): Long? = when {
            itemTimeoutMillis == null && remainingTotalMillis == null -> null
            itemTimeoutMillis == null -> remainingTotalMillis
            remainingTotalMillis == null -> itemTimeoutMillis
            else -> minOf(itemTimeoutMillis, remainingTotalMillis)
        }
    }

    private class CycleRuntime(
        val cycleId: LoadCycleId,
        val configKey: ConfigKey,
        val screenInstanceId: ScreenInstanceId?,
        val snapshotVersion: Long,
        val snapshotContentHash: String,
        val cycleStartedAt: Long,
    ) {
        val deactivated: AtomicBoolean = AtomicBoolean(false)
        val activeRequestId: AtomicReference<LoadRequestId?> = AtomicReference(null)
        val currentLoadJob: AtomicReference<Job?> = AtomicReference(null)
        val attempts: MutableList<WeightedItemAttemptResult> = mutableListOf()
    }

    private sealed interface SuccessAcceptance {
        data class Accepted(val storedAd: StoredAd) : SuccessAcceptance
        data class Stale(val mismatch: String) : SuccessAcceptance
    }
}
