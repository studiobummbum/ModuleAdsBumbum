package com.example.adsmodule.core.fullscreen

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.debug.AdsModuleLog
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StoredAdView
import com.example.adsmodule.sdk.AdFormat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Activity-hosted fullscreen presentation without depending on Android View types.
 *
 * Used for Native Full Splash: reserve → mark showing → acquire or supersede lock →
 * finish with consume/release when the hosted Activity exits.
 */
public class HostedFullscreenCoordinator(
    private val storage: AdStorage,
    private val lock: GlobalFullscreenLock,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    public fun begin(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
        kind: FullscreenAdKind,
        supersedeShowRequestId: ShowRequestId? = null,
    ): HostedFullscreenBeginResult {
        val reserved = storage.reserveNormal(configKey, screenInstanceId)
        if (reserved is ReserveResult.Rejected) {
            return HostedFullscreenBeginResult.Rejected(reserved.reason)
        }
        val accepted = reserved as ReserveResult.Accepted
        return beginReservation(
            reservationId = accepted.reservation.reservationId,
            storedAd = accepted.storedAd,
            kind = kind,
            supersedeShowRequestId = supersedeShowRequestId,
        )
    }

    public fun beginReservation(
        reservationId: ReservationId,
        storedAd: StoredAdView,
        kind: FullscreenAdKind,
        supersedeShowRequestId: ShowRequestId? = null,
    ): HostedFullscreenBeginResult {
        val formatError = validateKindAndFormat(kind, storedAd.sourceType)
        if (formatError != null) {
            storage.release(reservationId)
            return HostedFullscreenBeginResult.Rejected(formatError)
        }

        val showRequestId = ShowRequestId(idGenerator.nextId())
        val lockRequest = FullscreenLockAcquireRequest(
            showRequestId = showRequestId,
            objectId = storedAd.objectId,
            sourceConfigKey = storedAd.sourceConfigKey,
            screenInstanceId = storedAd.screenInstanceId,
            format = storedAd.sourceType,
            kind = kind,
        )

        if (supersedeShowRequestId != null) {
            when (
                val supersede = lock.supersede(
                    request = lockRequest,
                    expectedCoveredShowRequestId = supersedeShowRequestId,
                )
            ) {
                is FullscreenLockSupersedeResult.Rejected -> {
                    storage.release(reservationId)
                    return HostedFullscreenBeginResult.Rejected(supersede.reason)
                }
                is FullscreenLockSupersedeResult.Superseded -> Unit
            }
        } else {
            when (val acquire = lock.acquire(lockRequest)) {
                is FullscreenLockAcquireResult.Rejected -> {
                    storage.release(reservationId)
                    return HostedFullscreenBeginResult.Rejected(acquire.reason)
                }
                is FullscreenLockAcquireResult.Acquired -> Unit
            }
        }

        if (!storage.markShowing(reservationId)) {
            lock.release(showRequestId)
            storage.release(reservationId)
            return HostedFullscreenBeginResult.Rejected("Unable to markShowing")
        }

        return HostedFullscreenBeginResult.Started(
            session = HostedFullscreenSession(
                showRequestId = showRequestId,
                reservationId = reservationId,
                objectId = storedAd.objectId,
                kind = kind,
                format = storedAd.sourceType,
                storedAd = storedAd,
                coveredShowRequestId = supersedeShowRequestId,
                startedAtMillis = clock.nowMillis(),
            ),
        )
    }

    public fun finish(
        session: HostedFullscreenSession,
        outcome: HostedFullscreenOutcome,
    ): HostedFullscreenFinishResult {
        if (!session.finished.compareAndSet(false, true)) {
            return HostedFullscreenFinishResult.AlreadyFinished(session.showRequestId)
        }
        // Splash Native Full supersedes Inter without dismissing it. Leaving Splash must
        // drop covered owners so release does not restore Inter and block Onboarding Full.
        dropCoveredOwnersForSplashExit(session)
        return when (outcome) {
            HostedFullscreenOutcome.COMPLETED -> {
                val consumed =
                    storage.consume(session.reservationId) || storage.consume(session.objectId)
                lock.release(session.showRequestId)
                HostedFullscreenFinishResult.Completed(
                    showRequestId = session.showRequestId,
                    consumed = consumed,
                )
            }
            HostedFullscreenOutcome.PARKED -> {
                // Keep sdkHandle alive as READY for back/swipe-back re-show.
                val parked =
                    storage.returnShowingToReady(session.objectId) ||
                        storage.release(session.reservationId)
                lock.release(session.showRequestId)
                HostedFullscreenFinishResult.Completed(
                    showRequestId = session.showRequestId,
                    consumed = parked,
                )
            }
            HostedFullscreenOutcome.FAILED -> {
                val failed = storage.failShowing(session.objectId)
                lock.release(session.showRequestId)
                HostedFullscreenFinishResult.Failed(
                    showRequestId = session.showRequestId,
                    markedFailed = failed,
                )
            }
        }
    }

    private fun dropCoveredOwnersForSplashExit(session: HostedFullscreenSession) {
        if (session.kind != FullscreenAdKind.NATIVE_FULL_SPLASH) return
        val coveredId = session.coveredShowRequestId
        if (coveredId != null) {
            when (val result = lock.completeCovered(coveredId)) {
                is FullscreenLockCoveredCompletionResult.Completed -> {
                    AdsModuleLog.i("SPLASH lock clear covered=${coveredId.value}")
                }
                is FullscreenLockCoveredCompletionResult.NotCovered -> {
                    AdsModuleLog.i(
                        "SPLASH lock covered already cleared id=${coveredId.value}",
                    )
                }
            }
        }
        // Defensive: clear any remaining covered owners before Native Full release.
        lock.coveredOwners().toList().forEach { owner ->
            when (lock.completeCovered(owner.showRequestId)) {
                is FullscreenLockCoveredCompletionResult.Completed -> {
                    AdsModuleLog.i(
                        "SPLASH lock clear covered=${owner.showRequestId.value}",
                    )
                }
                else -> Unit
            }
        }
    }

    private fun validateKindAndFormat(
        kind: FullscreenAdKind,
        format: AdFormat,
    ): String? {
        val expected = when (kind) {
            FullscreenAdKind.NATIVE_FULL_SPLASH,
            FullscreenAdKind.NATIVE_FULL_ONBOARDING,
            -> setOf(AdFormat.NATIVE_FULLSCREEN, AdFormat.NATIVE)
            else -> emptySet()
        }
        return if (format in expected) {
            null
        } else {
            "Hosted kind $kind does not accept format $format"
        }
    }
}

public data class HostedFullscreenSession(
    val showRequestId: ShowRequestId,
    val reservationId: ReservationId,
    val objectId: ObjectId,
    val kind: FullscreenAdKind,
    val format: AdFormat,
    val storedAd: StoredAdView,
    val coveredShowRequestId: ShowRequestId?,
    val startedAtMillis: Long,
    internal val finished: AtomicBoolean = AtomicBoolean(false),
)

public enum class HostedFullscreenOutcome {
    COMPLETED,
    /** Return SHOWING → READY without destroying; for onboard Full back/swipe-back cache. */
    PARKED,
    FAILED,
}

public sealed class HostedFullscreenBeginResult {
    public data class Started(
        val session: HostedFullscreenSession,
    ) : HostedFullscreenBeginResult()

    public data class Rejected(
        val reason: String,
    ) : HostedFullscreenBeginResult()
}

public sealed class HostedFullscreenFinishResult {
    public data class Completed(
        val showRequestId: ShowRequestId,
        val consumed: Boolean,
    ) : HostedFullscreenFinishResult()

    public data class Failed(
        val showRequestId: ShowRequestId,
        val markedFailed: Boolean,
    ) : HostedFullscreenFinishResult()

    public data class AlreadyFinished(
        val showRequestId: ShowRequestId,
    ) : HostedFullscreenFinishResult()
}
