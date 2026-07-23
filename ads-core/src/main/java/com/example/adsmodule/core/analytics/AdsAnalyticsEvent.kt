package com.example.adsmodule.core.analytics

/**
 * Immutable analytics event. Required fields: [category], [name], [timestamp], [sessionId].
 */
public data class AdsAnalyticsEvent(
    val id: Long = 0L,
    val category: AdsEventCategory,
    val name: String,
    val timestamp: Long,
    val sessionId: String,
    val snapshotVersion: Long? = null,
    val configKey: String? = null,
    val screenInstanceId: String? = null,
    val cycleId: String? = null,
    val requestId: String? = null,
    val itemIndex: Int? = null,
    val weight: Int? = null,
    val type: String? = null,
    val adunitAlias: String? = null,
    val objectId: String? = null,
    val showRequestId: String? = null,
    val fullSessionId: String? = null,
    val targetPager: Int? = null,
    val exitSource: String? = null,
    val stateBefore: String? = null,
    val stateAfter: String? = null,
    val elapsed: Long? = null,
    val result: String? = null,
    val error: String? = null,
    val markers: Set<AdsEventMarker> = emptySet(),
    val details: Map<String, String> = emptyMap(),
) {
    public fun requireValid(): AdsAnalyticsEvent {
        require(name.isNotBlank()) { "AdsAnalyticsEvent.name must not be blank" }
        require(sessionId.isNotBlank()) { "AdsAnalyticsEvent.sessionId must not be blank" }
        require(timestamp >= 0L) { "AdsAnalyticsEvent.timestamp must be >= 0" }
        return this
    }

    public companion object {
        public fun isValid(event: AdsAnalyticsEvent): Boolean =
            event.name.isNotBlank() &&
                event.sessionId.isNotBlank() &&
                event.timestamp >= 0L
    }
}
