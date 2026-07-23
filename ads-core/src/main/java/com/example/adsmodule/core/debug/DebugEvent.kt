package com.example.adsmodule.core.debug

import com.example.adsmodule.core.analytics.AdsAnalyticsEvent
import com.example.adsmodule.core.analytics.AdsEventCategory
import com.example.adsmodule.core.analytics.AdsEventMarker

/**
 * Phase 14 debug UI event shape. Prefer [AdsAnalyticsEvent] for new code;
 * use [toDebugEvent] when a compact dashboard row is enough.
 */
public data class DebugEvent(
    val id: Long,
    val category: String,
    val message: String,
    val timestampMillis: Long,
    val details: Map<String, String> = emptyMap(),
    val markers: Set<String> = emptySet(),
)

public fun AdsAnalyticsEvent.toDebugEvent(): DebugEvent =
    DebugEvent(
        id = id,
        category = category.wireName,
        message = name,
        timestampMillis = timestamp,
        details = buildMap {
            putAll(details)
            snapshotVersion?.let { put("snapshotVersion", it.toString()) }
            configKey?.let { put("configKey", it) }
            screenInstanceId?.let { put("screenInstanceId", it) }
            cycleId?.let { put("cycleId", it) }
            requestId?.let { put("requestId", it) }
            itemIndex?.let { put("itemIndex", it.toString()) }
            weight?.let { put("weight", it.toString()) }
            type?.let { put("type", it) }
            adunitAlias?.let { put("adunitAlias", it) }
            objectId?.let { put("objectId", it) }
            showRequestId?.let { put("showRequestId", it) }
            fullSessionId?.let { put("fullSessionId", it) }
            targetPager?.let { put("targetPager", it.toString()) }
            exitSource?.let { put("exitSource", it) }
            stateBefore?.let { put("stateBefore", it) }
            stateAfter?.let { put("stateAfter", it) }
            elapsed?.let { put("elapsed", it.toString()) }
            result?.let { put("result", it) }
            error?.let { put("error", it) }
        },
        markers = markers.map { it.name }.toSet(),
    )

public fun resolveDebugCategory(raw: String): AdsEventCategory =
    AdsEventCategory.fromWireName(raw) ?: when (raw.lowercase()) {
        "splash" -> AdsEventCategory.SPLASH_SKIP
        "language", "onboarding", "fullscreen" -> AdsEventCategory.NAVIGATION
        "turnback" -> AdsEventCategory.LIFECYCLE
        "debug" -> AdsEventCategory.LIFECYCLE
        else -> AdsEventCategory.LIFECYCLE
    }

public fun parseMarkers(raw: Collection<String>): Set<AdsEventMarker> =
    raw.mapNotNull { name ->
        runCatching { AdsEventMarker.valueOf(name.uppercase()) }.getOrNull()
    }.toSet()
