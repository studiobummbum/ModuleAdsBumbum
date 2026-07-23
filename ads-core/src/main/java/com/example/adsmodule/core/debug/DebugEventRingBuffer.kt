package com.example.adsmodule.core.debug

import java.util.ArrayDeque
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded in-memory event log retained for unit tests and legacy callers.
 * Production/debug telemetry should use [com.example.adsmodule.core.analytics.AdsAnalytics].
 */
public class DebugEventRingBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val lock = Any()
    private val deque = ArrayDeque<DebugEvent>(capacity.coerceAtLeast(1))
    private val mutableEvents = MutableSharedFlow<DebugEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutableSnapshot = MutableStateFlow<List<DebugEvent>>(emptyList())

    public val events: SharedFlow<DebugEvent> = mutableEvents.asSharedFlow()
    public val snapshot: StateFlow<List<DebugEvent>> = mutableSnapshot.asStateFlow()

    public fun append(event: DebugEvent) {
        synchronized(lock) {
            while (deque.size >= capacity) {
                deque.removeFirst()
            }
            deque.addLast(event)
            mutableSnapshot.value = deque.toList()
        }
        mutableEvents.tryEmit(event)
    }

    public fun append(
        category: String,
        message: String,
        timestampMillis: Long,
        details: Map<String, String> = emptyMap(),
    ): DebugEvent {
        val event = DebugEvent(
            id = nextId(),
            category = category,
            message = message,
            timestampMillis = timestampMillis,
            details = details,
        )
        append(event)
        return event
    }

    public fun clear() {
        synchronized(lock) {
            deque.clear()
            mutableSnapshot.value = emptyList()
        }
    }

    public fun latest(): DebugEvent? = synchronized(lock) { deque.lastOrNull() }

    private fun nextId(): Long = synchronized(lock) {
        (deque.lastOrNull()?.id ?: 0L) + 1L
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 200
    }
}
