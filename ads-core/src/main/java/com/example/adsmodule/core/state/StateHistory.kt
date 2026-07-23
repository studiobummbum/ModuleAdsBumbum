package com.example.adsmodule.core.state

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.ObjectId
import java.util.ArrayDeque

public data class StateHistoryEntry(
    val subjectId: String,
    val objectId: ObjectId?,
    val from: AdSlotState,
    val to: AdSlotState,
    val event: AdsStateEvent,
    val atMillis: Long,
)

/**
 * Bounded ring buffer of accepted transitions for debug/inspector.
 */
public class StateHistory(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val entries = ArrayDeque<StateHistoryEntry>(capacity)

    public fun append(entry: StateHistoryEntry) {
        if (entries.size >= capacity) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    public fun snapshot(): List<StateHistoryEntry> = entries.toList()

    public fun clear() {
        entries.clear()
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 256
    }
}
