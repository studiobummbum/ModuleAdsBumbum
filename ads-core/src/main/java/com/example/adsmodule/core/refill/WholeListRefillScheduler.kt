package com.example.adsmodule.core.refill

import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.load.WeightedListLoader
import com.example.adsmodule.core.load.WeightedLoadRequest
import com.example.adsmodule.core.load.WeightedLoadResult
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.PutResult
import com.example.adsmodule.core.storage.StorageSlotKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides the current immutable ads config snapshot for refill cycles.
 */
public fun interface AdsConfigSnapshotProvider {
    public fun current(): AdsConfigSnapshot?
}

/**
 * Schedules whole-list refill cycles for deficient storage slots.
 *
 * Invariants:
 * - refill reloads the entire source `list_ads`, never an exact ad unit
 * - at most one refill job per [StorageSlotKey]
 * - SDK load runs outside AdStorage locks
 * - inactive / stale-generation successes destroy the handle
 */
public class WholeListRefillScheduler(
    private val scope: CoroutineScope,
    private val loader: WeightedListLoader,
    private val storage: AdStorage,
    private val deficitStore: RefillDeficitStore,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val idGenerator: IdGenerator,
) {
    private val jobs = ConcurrentHashMap<StorageSlotKey, Job>()
    private val slotMutexes = ConcurrentHashMap<StorageSlotKey, Mutex>()
    private val closed = AtomicBoolean(false)

    /**
     * Non-blocking enqueue. Safe to call from an AdStorage critical-section callback.
     */
    public fun requestRefill(slot: StorageSlotKey): Boolean {
        if (closed.get()) {
            return false
        }
        if (!deficitStore.isActive(slot)) {
            return false
        }
        val existing = jobs[slot]
        if (existing != null && existing.isActive) {
            return true
        }
        val readyCount = storage.readyCount(slot)
        when (val begin = deficitStore.tryBeginInFlight(slot, readyCount)) {
            is BeginInFlightResult.AlreadyInFlight -> return true
            is BeginInFlightResult.Rejected -> return false
            is BeginInFlightResult.Started -> {
                val job = scope.launch {
                    runSlot(slot = slot, generation = begin.generation)
                }
                jobs[slot] = job
                job.invokeOnCompletion {
                    jobs.remove(slot, job)
                }
                return true
            }
        }
    }

    public fun activate(
        slot: StorageSlotKey,
        targetReadyCount: Int,
        refillIfDeficit: Boolean = true,
    ) {
        deficitStore.activate(slot, targetReadyCount)
        if (refillIfDeficit) {
            requestRefill(slot)
        }
    }

    public suspend fun deactivate(slot: StorageSlotKey) {
        deficitStore.deactivate(slot)
        val job = jobs.remove(slot)
        job?.cancel()
        job?.join()
    }

    public fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    public fun isJobActive(slot: StorageSlotKey): Boolean {
        val job = jobs[slot]
        return job != null && job.isActive
    }

    private suspend fun runSlot(
        slot: StorageSlotKey,
        generation: Long,
    ) {
        val mutex = slotMutexes.getOrPut(slot) { Mutex() }
        mutex.withLock {
            try {
                while (!closed.get() && deficitStore.matchesGeneration(slot, generation)) {
                    val readyCount = storage.readyCount(slot)
                    val target = deficitStore.targetReadyCount(slot)
                    if (target <= 0 || readyCount >= target) {
                        break
                    }
                    val snapshot = snapshotProvider.current()
                    if (snapshot == null) {
                        break
                    }
                    val cycleId = LoadCycleId(idGenerator.nextId())
                    val result = try {
                        loader.load(
                            WeightedLoadRequest(
                                cycleId = cycleId,
                                configKey = slot.configKey,
                                screenInstanceId = slot.screenInstanceId,
                                snapshot = snapshot,
                            ),
                        )
                    } catch (_: Throwable) {
                        loader.deactivate(cycleId)
                        break
                    }

                    if (!deficitStore.matchesGeneration(slot, generation)) {
                        destroySuccessHandle(result)
                        break
                    }

                    when (result) {
                        is WeightedLoadResult.Success -> {
                            val currentReady = storage.readyCount(slot)
                            val currentTarget = deficitStore.targetReadyCount(slot)
                            if (currentReady >= currentTarget) {
                                result.storedAd.sdkHandle.destroy()
                                break
                            }
                            when (storage.putReady(result.storedAd)) {
                                is PutResult.Accepted -> Unit
                                is PutResult.Rejected -> result.storedAd.sdkHandle.destroy()
                            }
                        }
                        is WeightedLoadResult.Cancelled,
                        is WeightedLoadResult.Disabled,
                        is WeightedLoadResult.Exhausted,
                        is WeightedLoadResult.MissingConfig,
                        is WeightedLoadResult.TotalTimeout,
                        -> break
                    }
                }
            } finally {
                deficitStore.endInFlight(slot, generation)
            }
        }
    }

    private fun destroySuccessHandle(result: WeightedLoadResult) {
        if (result is WeightedLoadResult.Success) {
            result.storedAd.sdkHandle.destroy()
        }
    }
}
