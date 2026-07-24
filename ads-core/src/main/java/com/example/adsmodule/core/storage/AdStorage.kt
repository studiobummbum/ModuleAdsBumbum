package com.example.adsmodule.core.storage

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.state.AdsStateEvent
import com.example.adsmodule.core.state.AdsStateStore
import com.example.adsmodule.core.state.ApplyTransitionResult
import com.example.adsmodule.core.state.StateHistory
import com.example.adsmodule.core.turnback.TurnbackSelector
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.IdentityHashMap

/**
 * In-memory inventory of loaded ads with atomic reservation.
 *
 * Invariants:
 * - state updates are atomic under [lock]
 * - SDK [SdkLoadedAdHandle.destroy] is never called while holding [lock]
 * - normal-screen reserve matches exact configKey + screenInstanceId
 * - a slot may hold multiple READY objects (targetReadyCount > 1)
 * - Native / Native Fullscreen handles are never reused after insert
 * - turnback borrow selects globally among eligible READY Native/Banner objects
 */
public class AdStorage(
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val ttlMillis: Long? = null,
    private val onObjectRemoved: ((RemovedObjectInfo) -> Unit)? = null,
    stateHistory: StateHistory = StateHistory(),
) : StorageInspector {
    private val lock = Any()
    public val stateStore: AdsStateStore = AdsStateStore(
        clock = clock,
        history = stateHistory,
        initialState = AdSlotState.DISABLED,
        lock = lock,
    )

    private val byObjectId = LinkedHashMap<ObjectId, StoredAd>()
    private val readyBySlot = LinkedHashMap<StorageSlotKey, LinkedHashSet<ObjectId>>()
    private val reservations = LinkedHashMap<ReservationId, Reservation>()
    private val reservationByObject = LinkedHashMap<ObjectId, ReservationId>()
    private val nativeHandlesSeen: MutableMap<SdkLoadedAdHandle, ObjectId> =
        IdentityHashMap()

    public data class RemovedObjectInfo(
        val objectId: ObjectId,
        val sourceConfigKey: ConfigKey,
        val screenInstanceId: ScreenInstanceId?,
        val sourceWeight: Int,
        val sourceType: AdFormat,
        val reason: RemovalReason,
    )

    public enum class RemovalReason {
        RESERVED,
        CONSUMED,
        EXPIRED,
        FAILED,
        DESTROYED,
    }

    private data class PostLockAction(
        val handleToDestroy: SdkLoadedAdHandle?,
        val removed: RemovedObjectInfo?,
    )

    public fun putReady(storedAd: StoredAd): PutResult {
        if (storedAd.state != AdSlotState.READY) {
            return PutResult.Rejected("StoredAd.state must be READY, was ${storedAd.state}")
        }
        val isNative = storedAd.sourceType.isNativeFormat()
        synchronized(lock) {
            if (byObjectId.containsKey(storedAd.objectId)) {
                return PutResult.Rejected("Duplicate objectId ${storedAd.objectId.value}")
            }
            if (isNative && nativeHandlesSeen.containsKey(storedAd.sdkHandle)) {
                return PutResult.Rejected(
                    "Native sdkHandle already used by ${nativeHandlesSeen[storedAd.sdkHandle]?.value}",
                )
            }
            val slot = StorageSlotKey(storedAd.sourceConfigKey, storedAd.screenInstanceId)
            byObjectId[storedAd.objectId] = storedAd
            addReadyLocked(slot, storedAd.objectId)
            if (isNative) {
                nativeHandlesSeen[storedAd.sdkHandle] = storedAd.objectId
            }
            stateStore.ensureSubjectAlreadyLocked(storedAd.objectId.value, AdSlotState.LOADING)
            val applied = stateStore.applyAlreadyLocked(
                subjectId = storedAd.objectId.value,
                event = AdsStateEvent.MarkReady,
                objectId = storedAd.objectId,
            )
            if (applied is ApplyTransitionResult.Rejected) {
                byObjectId.remove(storedAd.objectId)
                removeReadyLocked(slot, storedAd.objectId)
                if (isNative) {
                    nativeHandlesSeen.remove(storedAd.sdkHandle)
                }
                return PutResult.Rejected(applied.reason)
            }
            return PutResult.Accepted(storedAd.toView())
        }
    }

    public fun reserveNormal(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
    ): ReserveResult {
        var removedInfo: RemovedObjectInfo? = null
        val result = synchronized(lock) {
            val slot = StorageSlotKey(configKey, screenInstanceId)
            val objectId = readyBySlot[slot]?.firstOrNull()
                ?: return@synchronized ReserveResult.Rejected(
                    "No READY object for ${configKey.value}/${screenInstanceId?.value}",
                )
            reserveObjectLocked(
                objectId = objectId,
                expectedConfigKey = configKey,
                expectedScreenInstanceId = screenInstanceId,
            ).also { reserveResult ->
                if (reserveResult is ReserveResult.Accepted) {
                    removedInfo = RemovedObjectInfo(
                        objectId = reserveResult.storedAd.objectId,
                        sourceConfigKey = reserveResult.storedAd.sourceConfigKey,
                        screenInstanceId = reserveResult.storedAd.screenInstanceId,
                        sourceWeight = reserveResult.storedAd.sourceWeight,
                        sourceType = reserveResult.storedAd.sourceType,
                        reason = RemovalReason.RESERVED,
                    )
                }
            }
        }
        removedInfo?.let { onObjectRemoved?.invoke(it) }
        return result
    }

    /**
     * Atomic turnback borrow: select → READY→RESERVED → pop → reservation →
     * [onReservedUnderLock] → unlock.
     *
     * [onReservedUnderLock] must only update bookkeeping / enqueue work. It must not
     * call SDK APIs or suspend.
     */
    public fun atomicBorrowTurnback(
        onReservedUnderLock: (ReserveResult.Accepted) -> Unit,
    ): ReserveResult {
        var removedInfo: RemovedObjectInfo? = null
        val result = synchronized(lock) {
            val selected = TurnbackSelector.select(listReadyLocked())
                ?: return@synchronized ReserveResult.Rejected("No eligible READY Native/Banner object")
            val accepted = reserveObjectLocked(
                objectId = selected.objectId,
                expectedConfigKey = selected.sourceConfigKey,
                expectedScreenInstanceId = selected.screenInstanceId,
            )
            if (accepted is ReserveResult.Accepted) {
                onReservedUnderLock(accepted)
                removedInfo = RemovedObjectInfo(
                    objectId = accepted.storedAd.objectId,
                    sourceConfigKey = accepted.storedAd.sourceConfigKey,
                    screenInstanceId = accepted.storedAd.screenInstanceId,
                    sourceWeight = accepted.storedAd.sourceWeight,
                    sourceType = accepted.storedAd.sourceType,
                    reason = RemovalReason.RESERVED,
                )
            }
            accepted
        }
        removedInfo?.let { onObjectRemoved?.invoke(it) }
        return result
    }

    public fun release(reservationId: ReservationId): Boolean = synchronized(lock) {
        val reservation = reservations[reservationId] ?: return false
        val current = byObjectId[reservation.objectId] ?: return false
        if (current.state != AdSlotState.RESERVED && current.state != AdSlotState.SHOWING) {
            return false
        }
        val applied = stateStore.applyAlreadyLocked(
            subjectId = reservation.objectId.value,
            event = AdsStateEvent.Release,
            objectId = reservation.objectId,
        )
        if (applied is ApplyTransitionResult.Rejected) {
            return false
        }
        val ready = current.withState(AdSlotState.READY)
        byObjectId[reservation.objectId] = ready
        val slot = StorageSlotKey(ready.sourceConfigKey, ready.screenInstanceId)
        addReadyLocked(slot, reservation.objectId)
        reservations.remove(reservationId)
        reservationByObject.remove(reservation.objectId)
        true
    }

    /**
     * Parks a SHOWING native back to READY without destroying the sdkHandle.
     * Used so Onboarding Full can be re-shown after back/swipe-back.
     */
    public fun returnShowingToReady(objectId: ObjectId): Boolean = synchronized(lock) {
        val current = byObjectId[objectId] ?: return false
        if (current.state != AdSlotState.SHOWING) {
            return false
        }
        val applied = stateStore.applyAlreadyLocked(
            subjectId = objectId.value,
            event = AdsStateEvent.Release,
            objectId = objectId,
        )
        if (applied is ApplyTransitionResult.Rejected) {
            return false
        }
        clearReservationLocked(objectId)
        val ready = current.withState(AdSlotState.READY)
        byObjectId[objectId] = ready
        addReadyLocked(
            StorageSlotKey(ready.sourceConfigKey, ready.screenInstanceId),
            objectId,
        )
        true
    }

    public fun markShowing(reservationId: ReservationId): Boolean = synchronized(lock) {
        val reservation = reservations[reservationId] ?: return false
        val current = byObjectId[reservation.objectId] ?: return false
        if (current.state != AdSlotState.RESERVED) {
            return false
        }
        val applied = stateStore.applyAlreadyLocked(
            subjectId = reservation.objectId.value,
            event = AdsStateEvent.MarkShowing,
            objectId = reservation.objectId,
        )
        if (applied is ApplyTransitionResult.Rejected) {
            return false
        }
        byObjectId[reservation.objectId] = current.withState(AdSlotState.SHOWING)
        true
    }

    public fun consume(reservationId: ReservationId): Boolean {
        val action = synchronized(lock) {
            val reservation = reservations[reservationId] ?: return false
            consumeLocked(reservation.objectId)
        } ?: return false
        finishOutsideLock(action)
        return true
    }

    public fun consume(objectId: ObjectId): Boolean {
        val action = synchronized(lock) {
            consumeLocked(objectId)
        } ?: return false
        finishOutsideLock(action)
        return true
    }

    public fun failShowing(objectId: ObjectId): Boolean {
        val action = synchronized(lock) {
            val current = byObjectId[objectId] ?: return false
            if (current.state != AdSlotState.SHOWING) {
                return false
            }
            val applied = stateStore.applyAlreadyLocked(
                subjectId = objectId.value,
                event = AdsStateEvent.Fail,
                objectId = objectId,
            )
            if (applied is ApplyTransitionResult.Rejected) {
                return false
            }
            byObjectId[objectId] = current.withState(AdSlotState.FAILED)
            clearReservationLocked(objectId)
            PostLockAction(
                handleToDestroy = current.sdkHandle,
                removed = RemovedObjectInfo(
                    objectId = objectId,
                    sourceConfigKey = current.sourceConfigKey,
                    screenInstanceId = current.screenInstanceId,
                    sourceWeight = current.sourceWeight,
                    sourceType = current.sourceType,
                    reason = RemovalReason.FAILED,
                ),
            )
        }
        finishOutsideLock(action)
        return true
    }

    public fun expire(objectId: ObjectId): Boolean {
        val action = synchronized(lock) {
            val current = byObjectId[objectId] ?: return false
            if (current.state != AdSlotState.READY) {
                return false
            }
            val applied = stateStore.applyAlreadyLocked(
                subjectId = objectId.value,
                event = AdsStateEvent.Expire,
                objectId = objectId,
            )
            if (applied is ApplyTransitionResult.Rejected) {
                return false
            }
            byObjectId[objectId] = current.withState(AdSlotState.EXPIRED)
            removeReadyLocked(
                StorageSlotKey(current.sourceConfigKey, current.screenInstanceId),
                objectId,
            )
            PostLockAction(
                handleToDestroy = current.sdkHandle,
                removed = RemovedObjectInfo(
                    objectId = objectId,
                    sourceConfigKey = current.sourceConfigKey,
                    screenInstanceId = current.screenInstanceId,
                    sourceWeight = current.sourceWeight,
                    sourceType = current.sourceType,
                    reason = RemovalReason.EXPIRED,
                ),
            )
        }
        finishOutsideLock(action)
        return true
    }

    public fun expireDue(nowMillis: Long = clock.nowMillis()): List<ObjectId> {
        val ttl = ttlMillis ?: return emptyList()
        val due = synchronized(lock) {
            byObjectId.values
                .filter { it.state == AdSlotState.READY && nowMillis - it.loadedAt >= ttl }
                .map { it.objectId }
        }
        val expired = ArrayList<ObjectId>(due.size)
        for (objectId in due) {
            if (expire(objectId)) {
                expired += objectId
            }
        }
        return expired
    }

    public fun destroy(objectId: ObjectId): Boolean {
        val action = synchronized(lock) {
            val current = byObjectId.remove(objectId) ?: return false
            removeReadyLocked(
                StorageSlotKey(current.sourceConfigKey, current.screenInstanceId),
                objectId,
            )
            clearReservationLocked(objectId)
            stateStore.removeSubjectAlreadyLocked(objectId.value)
            PostLockAction(
                handleToDestroy = current.sdkHandle,
                removed = RemovedObjectInfo(
                    objectId = objectId,
                    sourceConfigKey = current.sourceConfigKey,
                    screenInstanceId = current.screenInstanceId,
                    sourceWeight = current.sourceWeight,
                    sourceType = current.sourceType,
                    reason = RemovalReason.DESTROYED,
                ),
            )
        }
        finishOutsideLock(action)
        return true
    }

    public fun peekReady(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
    ): StoredAd? = synchronized(lock) {
        val objectId = readyBySlot[StorageSlotKey(configKey, screenInstanceId)]?.firstOrNull()
            ?: return null
        val ad = byObjectId[objectId] ?: return null
        ad.takeIf { it.state == AdSlotState.READY }
    }

    public fun readyCount(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
    ): Int = synchronized(lock) {
        readyCountLocked(StorageSlotKey(configKey, screenInstanceId))
    }

    public fun readyCount(slot: StorageSlotKey): Int = synchronized(lock) {
        readyCountLocked(slot)
    }

    public fun listReady(): List<StoredAd> = synchronized(lock) {
        listReadyLocked()
    }

    public fun get(objectId: ObjectId): StoredAd? = synchronized(lock) {
        byObjectId[objectId]
    }

    public fun getReservation(reservationId: ReservationId): Reservation? = synchronized(lock) {
        reservations[reservationId]
    }

    override fun snapshot(): StorageInspectorSnapshot = synchronized(lock) {
        StorageInspectorSnapshot(
            objects = byObjectId.values.map { it.toView() },
            readySlots = readyBySlot.mapValues { (_, ids) -> ids.toList() },
            reservations = reservations.values.toList(),
            slotStates = stateStore.snapshotStatesAlreadyLocked(),
            history = stateStore.historySnapshotAlreadyLocked(),
            capturedAtMillis = clock.nowMillis(),
        )
    }

    public fun inspector(): StorageInspectorSnapshot = snapshot()

    private fun reserveObjectLocked(
        objectId: ObjectId,
        expectedConfigKey: ConfigKey,
        expectedScreenInstanceId: ScreenInstanceId?,
    ): ReserveResult {
        val current = byObjectId[objectId]
            ?: return ReserveResult.Rejected("Missing StoredAd ${objectId.value}")
        if (current.state != AdSlotState.READY) {
            return ReserveResult.Rejected(
                "Object ${objectId.value} state is ${current.state}, expected READY",
            )
        }
        if (
            current.sourceConfigKey != expectedConfigKey ||
            current.screenInstanceId != expectedScreenInstanceId
        ) {
            return ReserveResult.Rejected("Slot metadata mismatch")
        }
        val applied = stateStore.applyIfAlreadyLocked(
            subjectId = objectId.value,
            expected = AdSlotState.READY,
            event = AdsStateEvent.Reserve,
            objectId = objectId,
        )
        if (applied is ApplyTransitionResult.Rejected) {
            return ReserveResult.Rejected(applied.reason)
        }
        val reserved = current.withState(AdSlotState.RESERVED)
        byObjectId[objectId] = reserved
        removeReadyLocked(
            StorageSlotKey(reserved.sourceConfigKey, reserved.screenInstanceId),
            objectId,
        )
        val reservation = Reservation(
            reservationId = ReservationId(idGenerator.nextId()),
            objectId = objectId,
            sourceConfigKey = reserved.sourceConfigKey,
            screenInstanceId = reserved.screenInstanceId,
            reservedAt = clock.nowMillis(),
        )
        reservations[reservation.reservationId] = reservation
        reservationByObject[objectId] = reservation.reservationId
        return ReserveResult.Accepted(storedAd = reserved.toView(), reservation = reservation)
    }

    private fun consumeLocked(objectId: ObjectId): PostLockAction? {
        val current = byObjectId[objectId] ?: return null
        when (current.state) {
            AdSlotState.READY -> {
                val applied = stateStore.applyAlreadyLocked(
                    subjectId = objectId.value,
                    event = AdsStateEvent.Consume,
                    objectId = objectId,
                )
                if (applied is ApplyTransitionResult.Rejected) {
                    return null
                }
                byObjectId[objectId] = current.withState(AdSlotState.CONSUMED)
                removeReadyLocked(
                    StorageSlotKey(current.sourceConfigKey, current.screenInstanceId),
                    objectId,
                )
            }
            AdSlotState.RESERVED -> {
                val toShowing = stateStore.applyAlreadyLocked(
                    subjectId = objectId.value,
                    event = AdsStateEvent.MarkShowing,
                    objectId = objectId,
                )
                if (toShowing is ApplyTransitionResult.Rejected) {
                    return null
                }
                val showing = current.withState(AdSlotState.SHOWING)
                byObjectId[objectId] = showing
                val toConsumed = stateStore.applyAlreadyLocked(
                    subjectId = objectId.value,
                    event = AdsStateEvent.Consume,
                    objectId = objectId,
                )
                if (toConsumed is ApplyTransitionResult.Rejected) {
                    return null
                }
                byObjectId[objectId] = showing.withState(AdSlotState.CONSUMED)
            }
            AdSlotState.SHOWING -> {
                val applied = stateStore.applyAlreadyLocked(
                    subjectId = objectId.value,
                    event = AdsStateEvent.Consume,
                    objectId = objectId,
                )
                if (applied is ApplyTransitionResult.Rejected) {
                    return null
                }
                byObjectId[objectId] = current.withState(AdSlotState.CONSUMED)
            }
            else -> return null
        }
        clearReservationLocked(objectId)
        val latest = checkNotNull(byObjectId[objectId])
        return PostLockAction(
            handleToDestroy = latest.sdkHandle,
            removed = RemovedObjectInfo(
                objectId = objectId,
                sourceConfigKey = latest.sourceConfigKey,
                screenInstanceId = latest.screenInstanceId,
                sourceWeight = latest.sourceWeight,
                sourceType = latest.sourceType,
                reason = RemovalReason.CONSUMED,
            ),
        )
    }

    private fun clearReservationLocked(objectId: ObjectId) {
        val reservationId = reservationByObject.remove(objectId)
        if (reservationId != null) {
            reservations.remove(reservationId)
        }
    }

    private fun finishOutsideLock(action: PostLockAction) {
        action.removed?.let { onObjectRemoved?.invoke(it) }
        action.handleToDestroy?.destroy()
    }

    private fun listReadyLocked(): List<StoredAd> =
        byObjectId.values.filter { it.state == AdSlotState.READY }

    private fun readyCountLocked(slot: StorageSlotKey): Int =
        readyBySlot[slot]?.size ?: 0

    private fun addReadyLocked(slot: StorageSlotKey, objectId: ObjectId) {
        readyBySlot.getOrPut(slot) { LinkedHashSet() }.add(objectId)
    }

    private fun removeReadyLocked(slot: StorageSlotKey, objectId: ObjectId) {
        val ids = readyBySlot[slot] ?: return
        ids.remove(objectId)
        if (ids.isEmpty()) {
            readyBySlot.remove(slot)
        }
    }

    private fun AdFormat.isNativeFormat(): Boolean =
        this == AdFormat.NATIVE || this == AdFormat.NATIVE_FULLSCREEN
}
