package com.example.adsmodule.core.analytics

import java.util.ArrayDeque
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Bounded in-memory analytics store used by debug Event Log and local telemetry.
 */
public class InMemoryAdsAnalytics(
    private val capacity: Int = DEFAULT_CAPACITY,
) : AdsAnalytics {
    private val lock = Any()
    private val deque = ArrayDeque<AdsAnalyticsEvent>(capacity.coerceAtLeast(1))
    private val mutableEvents = MutableSharedFlow<AdsAnalyticsEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutableSnapshot = MutableStateFlow<List<AdsAnalyticsEvent>>(emptyList())
    private var nextId = 1L

    override val events: SharedFlow<AdsAnalyticsEvent> = mutableEvents.asSharedFlow()
    override val snapshot: StateFlow<List<AdsAnalyticsEvent>> = mutableSnapshot.asStateFlow()

    override fun track(event: AdsAnalyticsEvent): AdsAnalyticsEvent? {
        return try {
            if (!AdsAnalyticsEvent.isValid(event)) return null
            val sanitized = AdsAnalyticsSanitizer.sanitize(event)
            val stored = synchronized(lock) {
                val withId = sanitized.copy(id = nextId++)
                while (deque.size >= capacity) {
                    deque.removeFirst()
                }
                deque.addLast(withId)
                mutableSnapshot.value = deque.toList()
                withId
            }
            mutableEvents.tryEmit(stored)
            stored
        } catch (_: Throwable) {
            null
        }
    }

    override fun clear() {
        synchronized(lock) {
            deque.clear()
            mutableSnapshot.value = emptyList()
        }
    }

    override fun latest(): AdsAnalyticsEvent? = synchronized(lock) { deque.lastOrNull() }

    override fun filter(
        category: AdsEventCategory?,
        query: String?,
        markersOnly: Boolean,
    ): List<AdsAnalyticsEvent> {
        val needle = query?.trim()?.lowercase().orEmpty()
        return synchronized(lock) {
            deque.filter { event ->
                (category == null || event.category == category) &&
                    (!markersOnly || event.markers.isNotEmpty()) &&
                    (needle.isEmpty() || event.matchesQuery(needle))
            }
        }
    }

    override fun exportJson(
        category: AdsEventCategory?,
        query: String?,
        markersOnly: Boolean,
    ): String {
        val filtered = filter(category = category, query = query, markersOnly = markersOnly)
        return try {
            Json.encodeToString(JsonArray(filtered.map { it.toJsonObject() }))
        } catch (_: Throwable) {
            "[]"
        }
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 200
    }
}

private fun AdsAnalyticsEvent.matchesQuery(needle: String): Boolean {
    if (name.lowercase().contains(needle)) return true
    if (category.wireName.contains(needle)) return true
    if (sessionId.lowercase().contains(needle)) return true
    if (configKey?.lowercase()?.contains(needle) == true) return true
    if (objectId?.lowercase()?.contains(needle) == true) return true
    if (result?.lowercase()?.contains(needle) == true) return true
    if (error?.lowercase()?.contains(needle) == true) return true
    if (exitSource?.lowercase()?.contains(needle) == true) return true
    if (showRequestId?.lowercase()?.contains(needle) == true) return true
    if (fullSessionId?.lowercase()?.contains(needle) == true) return true
    if (adunitAlias?.lowercase()?.contains(needle) == true) return true
    if (markers.any { it.name.lowercase().contains(needle) }) return true
    if (details.values.any { it.lowercase().contains(needle) }) return true
    return false
}

private fun AdsAnalyticsEvent.toJsonObject(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("category", JsonPrimitive(category.wireName))
    put("name", JsonPrimitive(name))
    put("timestamp", JsonPrimitive(timestamp))
    put("sessionId", JsonPrimitive(sessionId))
    putNullable("snapshotVersion", snapshotVersion)
    putNullable("configKey", configKey)
    putNullable("screenInstanceId", screenInstanceId)
    putNullable("cycleId", cycleId)
    putNullable("requestId", requestId)
    putNullable("itemIndex", itemIndex)
    putNullable("weight", weight)
    putNullable("type", type)
    putNullable("adunitAlias", adunitAlias)
    putNullable("objectId", objectId)
    putNullable("showRequestId", showRequestId)
    putNullable("fullSessionId", fullSessionId)
    putNullable("targetPager", targetPager)
    putNullable("exitSource", exitSource)
    putNullable("stateBefore", stateBefore)
    putNullable("stateAfter", stateAfter)
    putNullable("elapsed", elapsed)
    putNullable("result", result)
    putNullable("error", error)
    put(
        "markers",
        buildJsonArray {
            markers.forEach { add(JsonPrimitive(it.name)) }
        },
    )
    if (details.isNotEmpty()) {
        put(
            "details",
            buildJsonObject {
                details.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
            },
        )
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Long?) {
    if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Int?) {
    if (value != null) put(key, JsonPrimitive(value)) else put(key, JsonNull)
}
