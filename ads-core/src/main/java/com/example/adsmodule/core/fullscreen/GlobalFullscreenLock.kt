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
 * - at most one [FullscreenLockOwner] at a time
 * - release must match the current owner's [ShowRequestId]
 * - duplicate matching release is idempotent
 * - stale release of a previous request never unlocks a newer owner
 * - owner metadata never holds Activity/View references
 */
public class GlobalFullscreenLock(
    private val clock: Clock,
) {
    private val lock = Any()
    private val mutableSnapshot = MutableStateFlow(FullscreenLockSnapshot(owner = null))

    public val snapshot: StateFlow<FullscreenLockSnapshot> = mutableSnapshot.asStateFlow()

    public fun isBusy(): Boolean = synchronized(lock) {
        mutableSnapshot.value.owner != null
    }

    public fun currentOwner(): FullscreenLockOwner? = synchronized(lock) {
        mutableSnapshot.value.owner
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
            val owner = FullscreenLockOwner(
                showRequestId = request.showRequestId,
                objectId = request.objectId,
                sourceConfigKey = request.sourceConfigKey,
                screenInstanceId = request.screenInstanceId,
                format = request.format,
                kind = request.kind,
                acquiredAtMillis = clock.nowMillis(),
            )
            publishLocked(owner)
            FullscreenLockAcquireResult.Acquired(owner)
        }

    /**
     * Releases ownership when [showRequestId] is the current owner.
     *
     * Returns:
     * - [FullscreenLockReleaseResult.Released] on first matching release
     * - [FullscreenLockReleaseResult.AlreadyReleased] when already unlocked for that id
     * - [FullscreenLockReleaseResult.Stale] when a different owner currently holds the lock
     */
    public fun release(showRequestId: ShowRequestId): FullscreenLockReleaseResult =
        synchronized(lock) {
            val current = mutableSnapshot.value.owner
            when {
                current == null -> FullscreenLockReleaseResult.AlreadyReleased(showRequestId)
                current.showRequestId != showRequestId -> FullscreenLockReleaseResult.Stale(
                    requestedShowRequestId = showRequestId,
                    currentOwner = current,
                )
                else -> {
                    publishLocked(null)
                    FullscreenLockReleaseResult.Released(showRequestId)
                }
            }
        }

    private fun publishLocked(owner: FullscreenLockOwner?) {
        mutableSnapshot.value = FullscreenLockSnapshot(owner = owner)
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

public sealed class FullscreenLockReleaseResult {
    public data class Released(
        val showRequestId: ShowRequestId,
    ) : FullscreenLockReleaseResult()

    public data class AlreadyReleased(
        val showRequestId: ShowRequestId,
    ) : FullscreenLockReleaseResult()

    public data class Stale(
        val requestedShowRequestId: ShowRequestId,
        val currentOwner: FullscreenLockOwner,
    ) : FullscreenLockReleaseResult()
}
