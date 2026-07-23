package com.example.adsmodule.core.storage

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.state.StateHistoryEntry

/**
 * Read-only storage snapshot for debug dashboards. Never mutates inventory.
 */
public data class StorageInspectorSnapshot(
    val objects: List<StoredAdView>,
    val readySlots: Map<StorageSlotKey, ObjectId>,
    val reservations: List<Reservation>,
    val slotStates: Map<String, AdSlotState>,
    val history: List<StateHistoryEntry>,
    val capturedAtMillis: Long,
)

public interface StorageInspector {
    public fun snapshot(): StorageInspectorSnapshot
}
