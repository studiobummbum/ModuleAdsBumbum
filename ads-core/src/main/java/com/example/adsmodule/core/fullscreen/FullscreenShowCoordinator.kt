package com.example.adsmodule.core.fullscreen

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Coordinates fullscreen show requests through [GlobalFullscreenLock] and [AdStorage].
 *
 * Sequence:
 * 1. validate reservation + kind/format
 * 2. acquire lock
 * 3. markShowing
 * 4. collect SDK show events
 * 5. dismiss → consume; fail/abnormal end → failShowing
 * 6. release lock by matching [ShowRequestId], or complete covered owner when superseded
 *
 * Stale callbacks never mutate storage or unlock a newer owner.
 * Per-show observers receive events even when the global [events] flow has no replay.
 */
public class FullscreenShowCoordinator(
    private val storage: AdStorage,
    private val lock: GlobalFullscreenLock,
    private val adapters: AdSdkAdapterRegistry,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    private val mutableEvents = MutableSharedFlow<FullscreenShowEvent>(
        extraBufferCapacity = 64,
        replay = 0,
    )
    private val observers =
        ConcurrentHashMap<ShowRequestId, MutableSharedFlow<FullscreenShowEvent>>()

    public val events: SharedFlow<FullscreenShowEvent> = mutableEvents.asSharedFlow()

    /**
     * Returns a hot SharedFlow for one [ShowRequestId] with optional replay for late collectors.
     */
    public fun observe(showRequestId: ShowRequestId): SharedFlow<FullscreenShowEvent> =
        observerFlow(showRequestId).asSharedFlow()

    public suspend fun show(
        reservationId: ReservationId,
        kind: FullscreenAdKind,
    ): FullscreenShowResult {
        val reservation = storage.getReservation(reservationId)
            ?: return FullscreenShowResult.Rejected("Reservation not found")
        val storedAd = storage.get(reservation.objectId)
            ?: return FullscreenShowResult.Rejected("StoredAd missing for reservation")

        val formatError = validateKindAndFormat(kind, storedAd.sourceType)
        if (formatError != null) {
            storage.release(reservationId)
            return FullscreenShowResult.Rejected(formatError)
        }

        val showRequestId = ShowRequestId(idGenerator.nextId())
        observerFlow(showRequestId)
        val acquire = lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = showRequestId,
                objectId = storedAd.objectId,
                sourceConfigKey = storedAd.sourceConfigKey,
                screenInstanceId = storedAd.screenInstanceId,
                format = storedAd.sourceType,
                kind = kind,
            ),
        )
        if (acquire is FullscreenLockAcquireResult.Rejected) {
            storage.release(reservationId)
            observers.remove(showRequestId)
            return FullscreenShowResult.Rejected(acquire.reason)
        }

        if (!storage.markShowing(reservationId)) {
            lock.release(showRequestId)
            storage.release(reservationId)
            observers.remove(showRequestId)
            return FullscreenShowResult.Rejected("Unable to markShowing")
        }

        emit(
            FullscreenShowEvent.Started(
                showRequestId = showRequestId,
                objectId = storedAd.objectId,
                kind = kind,
                format = storedAd.sourceType,
                occurredAtMillis = clock.nowMillis(),
            ),
        )

        val adapter = adapters.adapterFor(storedAd.sourceType)
        if (adapter == null) {
            failAndRelease(
                showRequestId = showRequestId,
                objectId = storedAd.objectId,
                kind = kind,
                format = storedAd.sourceType,
                reason = "No adapter for ${storedAd.sourceType}",
            )
            return FullscreenShowResult.Failed(
                showRequestId = showRequestId,
                reason = "No adapter for ${storedAd.sourceType}",
            )
        }

        val terminalHandled = AtomicBoolean(false)
        var result: FullscreenShowResult = FullscreenShowResult.Failed(
            showRequestId = showRequestId,
            reason = "Show ended without terminal event",
        )

        try {
            adapter.show(
                AdShowRequest(
                    showRequestId = showRequestId.value,
                    handle = storedAd.sdkHandle,
                ),
            ).collect { event ->
                if (event.showRequestId != showRequestId.value) {
                    return@collect
                }
                when (event) {
                    is AdShowEvent.Shown -> {
                        emit(
                            FullscreenShowEvent.Shown(
                                showRequestId = showRequestId,
                                objectId = storedAd.objectId,
                                kind = kind,
                                format = storedAd.sourceType,
                                occurredAtMillis = clock.nowMillis(),
                            ),
                        )
                    }
                    is AdShowEvent.Impression -> {
                        emit(
                            FullscreenShowEvent.Impression(
                                showRequestId = showRequestId,
                                objectId = storedAd.objectId,
                                kind = kind,
                                format = storedAd.sourceType,
                                occurredAtMillis = clock.nowMillis(),
                            ),
                        )
                    }
                    is AdShowEvent.Click -> {
                        emit(
                            FullscreenShowEvent.Click(
                                showRequestId = showRequestId,
                                objectId = storedAd.objectId,
                                kind = kind,
                                format = storedAd.sourceType,
                                occurredAtMillis = clock.nowMillis(),
                            ),
                        )
                    }
                    is AdShowEvent.Dismiss -> {
                        if (terminalHandled.compareAndSet(false, true)) {
                            consumeAndRelease(
                                showRequestId = showRequestId,
                                reservationId = reservationId,
                                objectId = storedAd.objectId,
                                kind = kind,
                                format = storedAd.sourceType,
                            )
                            result = FullscreenShowResult.Dismissed(showRequestId)
                        }
                    }
                    is AdShowEvent.Fail -> {
                        if (terminalHandled.compareAndSet(false, true)) {
                            failAndRelease(
                                showRequestId = showRequestId,
                                objectId = storedAd.objectId,
                                kind = kind,
                                format = storedAd.sourceType,
                                reason = event.reason,
                            )
                            result = FullscreenShowResult.Failed(
                                showRequestId = showRequestId,
                                reason = event.reason,
                            )
                        }
                    }
                }
            }
        } finally {
            if (terminalHandled.compareAndSet(false, true)) {
                failAndRelease(
                    showRequestId = showRequestId,
                    objectId = storedAd.objectId,
                    kind = kind,
                    format = storedAd.sourceType,
                    reason = "Show flow completed without dismiss/fail",
                )
                result = FullscreenShowResult.Failed(
                    showRequestId = showRequestId,
                    reason = "Show flow completed without dismiss/fail",
                )
            }
            observers.remove(showRequestId)
        }

        return result
    }

    /**
     * Convenience entry that reserves then shows. Useful for tests and debug simulator.
     */
    public suspend fun reserveAndShow(
        reserve: () -> ReserveResult,
        kind: FullscreenAdKind,
    ): FullscreenShowResult {
        return when (val reserved = reserve()) {
            is ReserveResult.Accepted -> show(reserved.reservation.reservationId, kind)
            is ReserveResult.Rejected -> FullscreenShowResult.Rejected(reserved.reason)
        }
    }

    private fun consumeAndRelease(
        showRequestId: ShowRequestId,
        reservationId: ReservationId,
        objectId: ObjectId,
        kind: FullscreenAdKind,
        format: AdFormat,
    ) {
        val consumed = storage.consume(reservationId) || storage.consume(objectId)
        finishOwnership(showRequestId)
        emit(
            FullscreenShowEvent.Dismissed(
                showRequestId = showRequestId,
                objectId = objectId,
                kind = kind,
                format = format,
                consumed = consumed,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private fun failAndRelease(
        showRequestId: ShowRequestId,
        objectId: ObjectId,
        kind: FullscreenAdKind,
        format: AdFormat,
        reason: String,
    ) {
        val failed = storage.failShowing(objectId)
        finishOwnership(showRequestId)
        emit(
            FullscreenShowEvent.Failed(
                showRequestId = showRequestId,
                objectId = objectId,
                kind = kind,
                format = format,
                reason = reason,
                markedFailed = failed,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private fun finishOwnership(showRequestId: ShowRequestId) {
        val top = lock.currentOwner()
        if (top?.showRequestId == showRequestId) {
            lock.release(showRequestId)
            return
        }
        val covered = lock.coveredOwners().any { it.showRequestId == showRequestId }
        if (covered) {
            lock.completeCovered(showRequestId)
        } else {
            lock.release(showRequestId)
        }
    }

    private fun emit(event: FullscreenShowEvent) {
        mutableEvents.tryEmit(event)
        observers[event.showRequestId]?.tryEmit(event)
    }

    private fun observerFlow(showRequestId: ShowRequestId): MutableSharedFlow<FullscreenShowEvent> {
        val existing = observers[showRequestId]
        if (existing != null) {
            return existing
        }
        val created = MutableSharedFlow<FullscreenShowEvent>(
            replay = 8,
            extraBufferCapacity = 16,
        )
        val raced = observers.putIfAbsent(showRequestId, created)
        return raced ?: created
    }

    private fun validateKindAndFormat(
        kind: FullscreenAdKind,
        format: AdFormat,
    ): String? {
        val expected = when (kind) {
            FullscreenAdKind.INTERSTITIAL,
            FullscreenAdKind.INTER_ONBOARDING,
            -> setOf(AdFormat.INTERSTITIAL)
            FullscreenAdKind.APP_OPEN -> setOf(AdFormat.APP_OPEN)
            FullscreenAdKind.NATIVE_FULL_SPLASH,
            FullscreenAdKind.NATIVE_FULL_ONBOARDING,
            -> setOf(AdFormat.NATIVE_FULLSCREEN, AdFormat.NATIVE)
        }
        return if (format in expected) {
            null
        } else {
            "Kind $kind does not accept format $format"
        }
    }
}

public sealed class FullscreenShowResult {
    public data class Dismissed(
        val showRequestId: ShowRequestId,
    ) : FullscreenShowResult()

    public data class Failed(
        val showRequestId: ShowRequestId,
        val reason: String,
    ) : FullscreenShowResult()

    public data class Rejected(
        val reason: String,
    ) : FullscreenShowResult()
}

public sealed class FullscreenShowEvent {
    public abstract val showRequestId: ShowRequestId
    public abstract val objectId: ObjectId
    public abstract val kind: FullscreenAdKind
    public abstract val format: AdFormat
    public abstract val occurredAtMillis: Long

    public data class Started(
        override val showRequestId: ShowRequestId,
        override val objectId: ObjectId,
        override val kind: FullscreenAdKind,
        override val format: AdFormat,
        override val occurredAtMillis: Long,
    ) : FullscreenShowEvent()

    public data class Shown(
        override val showRequestId: ShowRequestId,
        override val objectId: ObjectId,
        override val kind: FullscreenAdKind,
        override val format: AdFormat,
        override val occurredAtMillis: Long,
    ) : FullscreenShowEvent()

    public data class Impression(
        override val showRequestId: ShowRequestId,
        override val objectId: ObjectId,
        override val kind: FullscreenAdKind,
        override val format: AdFormat,
        override val occurredAtMillis: Long,
    ) : FullscreenShowEvent()

    public data class Click(
        override val showRequestId: ShowRequestId,
        override val objectId: ObjectId,
        override val kind: FullscreenAdKind,
        override val format: AdFormat,
        override val occurredAtMillis: Long,
    ) : FullscreenShowEvent()

    public data class Dismissed(
        override val showRequestId: ShowRequestId,
        override val objectId: ObjectId,
        override val kind: FullscreenAdKind,
        override val format: AdFormat,
        val consumed: Boolean,
        override val occurredAtMillis: Long,
    ) : FullscreenShowEvent()

    public data class Failed(
        override val showRequestId: ShowRequestId,
        override val objectId: ObjectId,
        override val kind: FullscreenAdKind,
        override val format: AdFormat,
        val reason: String,
        val markedFailed: Boolean,
        override val occurredAtMillis: Long,
    ) : FullscreenShowEvent()
}
