package com.example.adsmodule.core.refill

import com.example.adsmodule.core.storage.StorageSlotKey

/**
 * Per-slot refill deficit bookkeeping.
 *
 * Formula: `max(targetReadyCount - readyCount - inFlightCount, 0)`.
 * RESERVED/SHOWING objects are intentionally excluded from the ready count.
 */
public class RefillDeficitStore {
    private val lock = Any()
    private val slots = LinkedHashMap<StorageSlotKey, SlotDeficitState>()

    public fun activate(
        slot: StorageSlotKey,
        targetReadyCount: Int,
    ): SlotDeficitView = synchronized(lock) {
        require(targetReadyCount > 0) { "targetReadyCount must be positive" }
        val previous = slots[slot]
        val nextGeneration = (previous?.generation ?: 0L) + 1L
        val state = SlotDeficitState(
            slot = slot,
            targetReadyCount = targetReadyCount,
            inFlightCount = 0,
            active = true,
            generation = nextGeneration,
        )
        slots[slot] = state
        state.toView(readyCount = 0)
    }

    public fun deactivate(slot: StorageSlotKey): SlotDeficitView? = synchronized(lock) {
        val previous = slots[slot] ?: return null
        val state = previous.copy(
            active = false,
            inFlightCount = 0,
            generation = previous.generation + 1L,
        )
        slots[slot] = state
        state.toView(readyCount = 0)
    }

    public fun isActive(slot: StorageSlotKey): Boolean = synchronized(lock) {
        slots[slot]?.active == true
    }

    public fun generation(slot: StorageSlotKey): Long = synchronized(lock) {
        slots[slot]?.generation ?: 0L
    }

    public fun targetReadyCount(slot: StorageSlotKey): Int = synchronized(lock) {
        slots[slot]?.takeIf { it.active }?.targetReadyCount ?: 0
    }

    public fun inFlightCount(slot: StorageSlotKey): Int = synchronized(lock) {
        slots[slot]?.inFlightCount ?: 0
    }

    public fun deficit(slot: StorageSlotKey, readyCount: Int): Int = synchronized(lock) {
        deficitLocked(slot, readyCount)
    }

    /**
     * Marks one in-flight whole-list cycle when the slot is active and still deficient.
     * Returns null when no cycle should start (inactive, already in-flight, or no deficit).
     */
    public fun tryBeginInFlight(
        slot: StorageSlotKey,
        readyCount: Int,
    ): BeginInFlightResult = synchronized(lock) {
        val state = slots[slot]
            ?: return BeginInFlightResult.Rejected("Slot not registered")
        if (!state.active) {
            return BeginInFlightResult.Rejected("Slot inactive")
        }
        if (state.inFlightCount > 0) {
            return BeginInFlightResult.AlreadyInFlight(state.generation)
        }
        if (deficitLocked(slot, readyCount) <= 0) {
            return BeginInFlightResult.Rejected("No deficit")
        }
        state.inFlightCount = 1
        BeginInFlightResult.Started(
            generation = state.generation,
            targetReadyCount = state.targetReadyCount,
        )
    }

    public fun endInFlight(
        slot: StorageSlotKey,
        generation: Long,
    ): Boolean = synchronized(lock) {
        val state = slots[slot] ?: return false
        if (state.generation != generation) {
            return false
        }
        if (state.inFlightCount <= 0) {
            return false
        }
        state.inFlightCount = 0
        true
    }

    public fun matchesGeneration(
        slot: StorageSlotKey,
        generation: Long,
    ): Boolean = synchronized(lock) {
        val state = slots[slot] ?: return false
        state.active && state.generation == generation
    }

    public fun snapshot(readyCountBySlot: Map<StorageSlotKey, Int> = emptyMap()): RefillDeficitSnapshot =
        synchronized(lock) {
            RefillDeficitSnapshot(
                slots = slots.values.map { state ->
                    state.toView(readyCount = readyCountBySlot[state.slot] ?: 0)
                },
            )
        }

    private fun deficitLocked(slot: StorageSlotKey, readyCount: Int): Int {
        val state = slots[slot] ?: return 0
        if (!state.active) {
            return 0
        }
        return (state.targetReadyCount - readyCount.coerceAtLeast(0) - state.inFlightCount)
            .coerceAtLeast(0)
    }

    private data class SlotDeficitState(
        val slot: StorageSlotKey,
        val targetReadyCount: Int,
        var inFlightCount: Int,
        var active: Boolean,
        val generation: Long,
    ) {
        fun toView(readyCount: Int): SlotDeficitView = SlotDeficitView(
            slot = slot,
            targetReadyCount = targetReadyCount,
            readyCount = readyCount,
            inFlightCount = inFlightCount,
            active = active,
            generation = generation,
            deficit = if (!active) {
                0
            } else {
                (targetReadyCount - readyCount.coerceAtLeast(0) - inFlightCount).coerceAtLeast(0)
            },
        )
    }
}

public data class SlotDeficitView(
    val slot: StorageSlotKey,
    val targetReadyCount: Int,
    val readyCount: Int,
    val inFlightCount: Int,
    val active: Boolean,
    val generation: Long,
    val deficit: Int,
)

public data class RefillDeficitSnapshot(
    val slots: List<SlotDeficitView>,
)

public sealed class BeginInFlightResult {
    public data class Started(
        val generation: Long,
        val targetReadyCount: Int,
    ) : BeginInFlightResult()

    public data class AlreadyInFlight(
        val generation: Long,
    ) : BeginInFlightResult()

    public data class Rejected(
        val reason: String,
    ) : BeginInFlightResult()
}
