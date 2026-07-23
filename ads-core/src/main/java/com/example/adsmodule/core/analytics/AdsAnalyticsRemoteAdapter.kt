package com.example.adsmodule.core.analytics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sink for a future remote analytics backend (e.g. Firebase).
 * Phase 15 ships the contract only — no Firebase dependency.
 */
public interface AdsAnalyticsRemoteAdapter {
    public fun send(event: AdsAnalyticsEvent)
}

/**
 * Placeholder remote adapter used until Firebase wiring lands.
 */
public object NoOpAdsAnalyticsRemoteAdapter : AdsAnalyticsRemoteAdapter {
    override fun send(event: AdsAnalyticsEvent): Unit = Unit
}

/**
 * Fan-out: always write to [local]; forward to [remote] without failing the caller
 * when the remote sink throws.
 */
public class CompositeAdsAnalytics(
    private val local: InMemoryAdsAnalytics,
    private val remote: AdsAnalyticsRemoteAdapter = NoOpAdsAnalyticsRemoteAdapter,
) : AdsAnalytics {
    override val events: SharedFlow<AdsAnalyticsEvent> = local.events
    override val snapshot: StateFlow<List<AdsAnalyticsEvent>> = local.snapshot

    override fun track(event: AdsAnalyticsEvent): AdsAnalyticsEvent? {
        val stored = try {
            local.track(event)
        } catch (_: Throwable) {
            null
        }
        if (stored != null) {
            try {
                remote.send(stored)
            } catch (_: Throwable) {
                // Remote failure must not crash or drop the local record.
            }
        }
        return stored
    }

    override fun clear() {
        try {
            local.clear()
        } catch (_: Throwable) {
            // ignore
        }
    }

    override fun latest(): AdsAnalyticsEvent? =
        try {
            local.latest()
        } catch (_: Throwable) {
            null
        }

    override fun filter(
        category: AdsEventCategory?,
        query: String?,
        markersOnly: Boolean,
    ): List<AdsAnalyticsEvent> =
        try {
            local.filter(category = category, query = query, markersOnly = markersOnly)
        } catch (_: Throwable) {
            emptyList()
        }

    override fun exportJson(
        category: AdsEventCategory?,
        query: String?,
        markersOnly: Boolean,
    ): String =
        try {
            local.exportJson(category = category, query = query, markersOnly = markersOnly)
        } catch (_: Throwable) {
            "[]"
        }
}

/**
 * Safe no-op analytics for hosts that have not wired a store yet.
 */
public object NoOpAdsAnalytics : AdsAnalytics {
    private val emptyEvents = MutableSharedFlow<AdsAnalyticsEvent>()
    private val emptySnapshot = MutableStateFlow<List<AdsAnalyticsEvent>>(emptyList())

    override val events: SharedFlow<AdsAnalyticsEvent> = emptyEvents.asSharedFlow()
    override val snapshot: StateFlow<List<AdsAnalyticsEvent>> = emptySnapshot.asStateFlow()

    override fun track(event: AdsAnalyticsEvent): AdsAnalyticsEvent? = null

    override fun clear(): Unit = Unit

    override fun latest(): AdsAnalyticsEvent? = null

    override fun filter(
        category: AdsEventCategory?,
        query: String?,
        markersOnly: Boolean,
    ): List<AdsAnalyticsEvent> = emptyList()

    override fun exportJson(
        category: AdsEventCategory?,
        query: String?,
        markersOnly: Boolean,
    ): String = "[]"
}
