package com.example.adsmodule.core.analytics

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public analytics façade. Implementations must never throw to callers.
 */
public interface AdsAnalytics {
    public val events: SharedFlow<AdsAnalyticsEvent>
    public val snapshot: StateFlow<List<AdsAnalyticsEvent>>

    /**
     * Records [event] after validation and sanitization.
     * Invalid events are dropped silently; failures never propagate.
     *
     * @return stored event when accepted, or null when rejected/dropped.
     */
    public fun track(event: AdsAnalyticsEvent): AdsAnalyticsEvent?

    public fun clear()

    public fun latest(): AdsAnalyticsEvent?

    public fun filter(
        category: AdsEventCategory? = null,
        query: String? = null,
        markersOnly: Boolean = false,
    ): List<AdsAnalyticsEvent>

    public fun exportJson(
        category: AdsEventCategory? = null,
        query: String? = null,
        markersOnly: Boolean = false,
    ): String
}

/**
 * Convenience builder used by debug bridge and future coordinators.
 */
public fun AdsAnalytics.track(
    category: AdsEventCategory,
    name: String,
    timestamp: Long,
    sessionId: String,
    snapshotVersion: Long? = null,
    configKey: String? = null,
    screenInstanceId: String? = null,
    cycleId: String? = null,
    requestId: String? = null,
    itemIndex: Int? = null,
    weight: Int? = null,
    type: String? = null,
    adunitAlias: String? = null,
    objectId: String? = null,
    showRequestId: String? = null,
    fullSessionId: String? = null,
    targetPager: Int? = null,
    exitSource: String? = null,
    stateBefore: String? = null,
    stateAfter: String? = null,
    elapsed: Long? = null,
    result: String? = null,
    error: String? = null,
    markers: Set<AdsEventMarker> = emptySet(),
    details: Map<String, String> = emptyMap(),
): AdsAnalyticsEvent? =
    track(
        AdsAnalyticsEvent(
            category = category,
            name = name,
            timestamp = timestamp,
            sessionId = sessionId,
            snapshotVersion = snapshotVersion,
            configKey = configKey,
            screenInstanceId = screenInstanceId,
            cycleId = cycleId,
            requestId = requestId,
            itemIndex = itemIndex,
            weight = weight,
            type = type,
            adunitAlias = adunitAlias,
            objectId = objectId,
            showRequestId = showRequestId,
            fullSessionId = fullSessionId,
            targetPager = targetPager,
            exitSource = exitSource,
            stateBefore = stateBefore,
            stateAfter = stateAfter,
            elapsed = elapsed,
            result = result,
            error = error,
            markers = markers,
            details = details,
        ),
    )
