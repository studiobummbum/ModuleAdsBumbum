package com.example.adsmodule.core.fullscreen

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.sdk.AdFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide exclusive ownership for fullscreen presentation.
 *
 * Invariants:
 * - at most one top [FullscreenLockOwner] at a time
 * - release must match the current top owner's [ShowRequestId]
 * - duplicate matching release is idempotent
 * - stale release of a previous request never unlocks a newer owner
 * - Native Full Splash may supersede a matching primary owner without dismissing it
 * - covered owners stay tracked until their real terminal callback
 * - owner metadata never holds Activity/View references
 */
public class GlobalFullscreenLock(
    private val clock: Clock,
) {
    private val lock = Any()
    private val mutableSnapshot = MutableStateFlow(
        FullscreenLockSnapshot(owner = null, coveredOwners = emptyList()),
    )

    public val snapshot: StateFlow<FullscreenLockSnapshot> = mutableSnapshot.asStateFlow()

    public fun isBusy(): Boolean = synchronized(lock) {
        mutableSnapshot.value.owner != null
    }

    public fun currentOwner(): FullscreenLockOwner? = synchronized(lock) {
        mutableSnapshot.value.owner
    }

    public fun coveredOwners(): List<FullscreenLockOwner> = synchronized(lock) {
        mutableSnapshot.value.coveredOwners
    }

    public fun acquire(request: FullscreenLockAcquireRequest): FullscreenLockAcquireResult =
        synchronized(lock) {
            val current = mutableSnapshot.value.owner
            if (current != null) {
                return FullscreenLockAcquireResult.Rejected(
                    reason = "Fullscreen lock busy by ${current.showRequestId.value}",
                    currentOwner = current,
                )
            }
            val owner = request.toOwner(clock.nowMillis())
            publishLocked(owner = owner, covered = mutableSnapshot.value.coveredOwners)
            FullscreenLockAcquireResult.Acquired(owner)
        }

    /**
     * Pushes [request] on top when the current owner matches [expectedCoveredShowRequestId].
     * The previous owner remains covered until [completeCovered] or a later unlock path.
     */
    public fun supersede(
        request: FullscreenLockAcquireRequest,
        expectedCoveredShowRequestId: ShowRequestId,
    ): FullscreenLockSupersedeResult = synchronized(lock) {
        require(request.kind == FullscreenAdKind.NATIVE_FULL_SPLASH) {
            "Only NATIVE_FULL_SPLASH may supersede a fullscreen owner"
        }
        val current = mutableSnapshot.value.owner
            ?: return FullscreenLockSupersedeResult.Rejected(
                reason = "No current owner to supersede",
            )
        if (current.showRequestId != expectedCoveredShowRequestId) {
            return FullscreenLockSupersedeResult.Rejected(
                reason = "Expected covered ${expectedCoveredShowRequestId.value} but top is ${current.showRequestId.value}",
                currentOwner = current,
            )
        }
        val covered = mutableSnapshot.value.coveredOwners + current
        val owner = request.toOwner(clock.nowMillis())
        publishLocked(owner = owner, covered = covered)
        FullscreenLockSupersedeResult.Superseded(
            owner = owner,
            coveredOwner = current,
        )
    }

    /**
     * Releases ownership when [showRequestId] is the current top owner.
     *
     * If a covered owner remains, it is restored as the top owner so App Open
     * suppression continues until the SDK ad completes.
     */
    public fun release(showRequestId: ShowRequestId): FullscreenLockReleaseResult =
        synchronized(lock) {
            val current = mutableSnapshot.value.owner
            when {
                current == null -> {
                    if (mutableSnapshot.value.coveredOwners.any { it.showRequestId == showRequestId }) {
                        FullscreenLockReleaseResult.Stale(
                            requestedShowRequestId = showRequestId,
                            currentOwner = null,
                        )
                    } else {
                        FullscreenLockReleaseResult.AlreadyReleased(showRequestId)
                    }
                }
                current.showRequestId != showRequestId -> FullscreenLockReleaseResult.Stale(
                    requestedShowRequestId = showRequestId,
                    currentOwner = current,
                )
                else -> {
                    val remainingCovered = mutableSnapshot.value.coveredOwners
                    val restored = remainingCovered.lastOrNull()
                    val nextCovered = if (restored == null) {
                        emptyList()
                    } else {
                        remainingCovered.dropLast(1)
                    }
                    publishLocked(owner = restored, covered = nextCovered)
                    FullscreenLockReleaseResult.Released(
                        showRequestId = showRequestId,
                        restoredOwner = restored,
                    )
                }
            }
        }

    /**
     * Clears a covered owner after its real SDK terminal event without unlocking the top owner.
     */
    public fun completeCovered(showRequestId: ShowRequestId): FullscreenLockCoveredCompletionResult =
        synchronized(lock) {
            val covered = mutableSnapshot.value.coveredOwners
            val index = covered.indexOfFirst { it.showRequestId == showRequestId }
            if (index < 0) {
                return FullscreenLockCoveredCompletionResult.NotCovered(showRequestId)
            }
            val remaining = covered.toMutableList().also { it.removeAt(index) }
            publishLocked(owner = mutableSnapshot.value.owner, covered = remaining)
            FullscreenLockCoveredCompletionResult.Completed(showRequestId)
        }

    private fun publishLocked(
        owner: FullscreenLockOwner?,
        covered: List<FullscreenLockOwner>,
    ) {
        mutableSnapshot.value = FullscreenLockSnapshot(
            owner = owner,
            coveredOwners = covered,
        )
    }
}

public enum class FullscreenAdKind {
    INTERSTITIAL,
    APP_OPEN,
    NATIVE_FULL_SPLASH,
    NATIVE_FULL_ONBOARDING,
    INTER_ONBOARDING,
}

public data class FullscreenLockAcquireRequest(
    val showRequestId: ShowRequestId,
    val objectId: ObjectId,
    val sourceConfigKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
    val format: AdFormat,
    val kind: FullscreenAdKind,
)

public data class FullscreenLockOwner(
    val showRequestId: ShowRequestId,
    val objectId: ObjectId,
    val sourceConfigKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
    val format: AdFormat,
    val kind: FullscreenAdKind,
    val acquiredAtMillis: Long,
) {
    init {
        require(acquiredAtMillis >= 0L) { "acquiredAtMillis must not be negative" }
    }
}

public data class FullscreenLockSnapshot(
    val owner: FullscreenLockOwner?,
    val coveredOwners: List<FullscreenLockOwner> = emptyList(),
)

public sealed class FullscreenLockAcquireResult {
    public data class Acquired(
        val owner: FullscreenLockOwner,
    ) : FullscreenLockAcquireResult()

    public data class Rejected(
        val reason: String,
        val currentOwner: FullscreenLockOwner,
    ) : FullscreenLockAcquireResult()
}

public sealed class FullscreenLockSupersedeResult {
    public data class Superseded(
        val owner: FullscreenLockOwner,
        val coveredOwner: FullscreenLockOwner,
    ) : FullscreenLockSupersedeResult()

    public data class Rejected(
        val reason: String,
        val currentOwner: FullscreenLockOwner? = null,
    ) : FullscreenLockSupersedeResult()
}

public sealed class FullscreenLockReleaseResult {
    public data class Released(
        val showRequestId: ShowRequestId,
        val restoredOwner: FullscreenLockOwner? = null,
    ) : FullscreenLockReleaseResult()

    public data class AlreadyReleased(
        val showRequestId: ShowRequestId,
    ) : FullscreenLockReleaseResult()

    public data class Stale(
        val requestedShowRequestId: ShowRequestId,
        val currentOwner: FullscreenLockOwner?,
    ) : FullscreenLockReleaseResult()
}

public sealed class FullscreenLockCoveredCompletionResult {
    public data class Completed(
        val showRequestId: ShowRequestId,
    ) : FullscreenLockCoveredCompletionResult()

    public data class NotCovered(
        val showRequestId: ShowRequestId,
    ) : FullscreenLockCoveredCompletionResult()
}

private fun FullscreenLockAcquireRequest.toOwner(acquiredAtMillis: Long): FullscreenLockOwner =
    FullscreenLockOwner(
        showRequestId = showRequestId,
        objectId = objectId,
        sourceConfigKey = sourceConfigKey,
        screenInstanceId = screenInstanceId,
        format = format,
        kind = kind,
        acquiredAtMillis = acquiredAtMillis,
    )
