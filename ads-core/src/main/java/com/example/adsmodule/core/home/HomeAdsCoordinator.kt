package com.example.adsmodule.core.home

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.FullscreenShowCoordinator
import com.example.adsmodule.core.fullscreen.FullscreenShowResult
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenBindSession
import com.example.adsmodule.core.normal.NormalScreenEnsureResult
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
import com.example.adsmodule.core.normal.NormalScreenUnbindResult
import com.example.adsmodule.core.refill.AdsConfigSnapshotProvider
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.splash.AudienceEligibility
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StorageSlotKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Home screen ads: banner lifecycle + action-triggered Inter All with impression interval.
 *
 * Does not invent timeout fields for configs that omit them.
 */
public class HomeAdsCoordinator(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val normalAds: NormalScreenAdCoordinator,
    private val storage: AdStorage,
    private val fullscreen: FullscreenShowCoordinator,
    private val refillScheduler: WholeListRefillScheduler,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val audience: AudienceType,
    private val intervalGate: InterIntervalGate = InterIntervalGate(),
    private val screenInstanceId: ScreenInstanceId = ScreenInstanceId("home-1"),
) {
    private val bannerMutex = Mutex()
    private val interMutex = Mutex()
    private val interInFlight = AtomicBoolean(false)
    private var bannerSession: NormalScreenBindSession? = null
    private val lastInterResult = AtomicReference<HomeInterShowResult?>(null)

    private val mutableSnapshot = MutableStateFlow(buildSnapshot())
    public val snapshot: StateFlow<HomeAdsSnapshot> = mutableSnapshot.asStateFlow()

    public fun intervalGate(): InterIntervalGate = intervalGate

    public fun ensureBannerPreloaded() {
        normalAds.ensureLoadedAsync(HomeAdsKeys.BANNER_HOME, screenInstanceId)
        normalAds.ensureLoadedAsync(HomeAdsKeys.INTER_ALL, screenInstanceId)
        publish()
    }

    public suspend fun bindBanner(): NormalScreenBindResult {
        return bannerMutex.withLock {
            val existing = bannerSession
            if (existing != null && !existing.finished.get()) {
                val state = normalAds.slotState(HomeAdsKeys.BANNER_HOME, screenInstanceId)
                return@withLock NormalScreenBindResult.Bound(existing, state)
            }
            when (val bound = normalAds.bind(HomeAdsKeys.BANNER_HOME, screenInstanceId)) {
                is NormalScreenBindResult.Bound -> {
                    bannerSession = bound.session
                    publish()
                    bound
                }
                is NormalScreenBindResult.Rejected -> {
                    publish()
                    bound
                }
            }
        }
    }

    public fun destroyBanner(): NormalScreenUnbindResult? {
        val session = bannerSession ?: return null
        bannerSession = null
        val result = normalAds.unbind(session, NormalScreenUnbindMode.CONSUME)
        publish()
        return result
    }

    /**
     * Demo / product action that may show Home Inter subject to interval + eligibility.
     */
    public fun triggerHomeActionAsync() {
        scope.launch { triggerHomeAction() }
    }

    public suspend fun triggerHomeAction(): HomeInterShowResult {
        if (!interInFlight.compareAndSet(false, true)) {
            return HomeInterShowResult.Rejected("inter show already in flight").also {
                lastInterResult.set(it)
                publish()
            }
        }
        return try {
            interMutex.withLock { showInterAllLocked() }
        } finally {
            interInFlight.set(false)
            publish()
        }
    }

    private suspend fun showInterAllLocked(): HomeInterShowResult {
        val snapshot = snapshotProvider.current()
            ?: return reject("missing snapshot")
        val ads = snapshot.adsConfig(HomeAdsKeys.INTER_ALL)
            ?: return reject("missing inter_all config")
        if (!ads.enable) {
            return reject("inter_all enable=false")
        }
        if (!AudienceEligibility.isEligible(audience, ads.isOrganic)) {
            return reject("audience ineligible")
        }

        when (val decision = intervalGate.canShow(clock.nowMillis(), ads.intervalMillis)) {
            is InterIntervalDecision.Blocked -> {
                val blocked = HomeInterShowResult.IntervalBlocked(
                    remainingMillis = decision.remainingMillis,
                    reason = decision.reason,
                )
                lastInterResult.set(blocked)
                return blocked
            }
            is InterIntervalDecision.Allowed -> Unit
        }

        normalAds.ensureActivated(HomeAdsKeys.INTER_ALL, screenInstanceId, refillIfDeficit = true)
        when (val ensure = normalAds.ensureLoaded(HomeAdsKeys.INTER_ALL, screenInstanceId)) {
            is NormalScreenEnsureResult.Terminal -> {
                return reject(ensure.state.reason ?: ensure.state.status.name)
            }
            is NormalScreenEnsureResult.Ready -> Unit
        }

        val reserved = storage.reserveNormal(HomeAdsKeys.INTER_ALL, screenInstanceId)
        if (reserved !is ReserveResult.Accepted) {
            return reject((reserved as ReserveResult.Rejected).reason)
        }
        refillScheduler.requestRefill(StorageSlotKey(HomeAdsKeys.INTER_ALL, screenInstanceId))

        return when (
            val result = fullscreen.show(
                reserved.reservation.reservationId,
                FullscreenAdKind.INTERSTITIAL,
            )
        ) {
            is FullscreenShowResult.Dismissed -> {
                // Interval is counted from a completed show (impression occurred during show).
                intervalGate.markImpressed(clock.nowMillis())
                val shown = HomeInterShowResult.Shown(
                    showRequestId = result.showRequestId,
                    objectId = reserved.storedAd.objectId,
                    storedAd = reserved.storedAd,
                )
                lastInterResult.set(shown)
                shown
            }
            is FullscreenShowResult.Failed -> {
                val failed = HomeInterShowResult.Failed(
                    reason = result.reason,
                    showRequestId = result.showRequestId,
                )
                lastInterResult.set(failed)
                failed
            }
            is FullscreenShowResult.Rejected -> {
                storage.release(reserved.reservation.reservationId)
                reject(result.reason)
            }
        }
    }

    private fun reject(reason: String): HomeInterShowResult {
        val rejected = HomeInterShowResult.Rejected(reason)
        lastInterResult.set(rejected)
        return rejected
    }

    private fun publish() {
        mutableSnapshot.value = buildSnapshot()
    }

    private fun buildSnapshot(): HomeAdsSnapshot {
        val ads = snapshotProvider.current()?.adsConfig(HomeAdsKeys.INTER_ALL)
        val decision = intervalGate.canShow(clock.nowMillis(), ads?.intervalMillis)
        return HomeAdsSnapshot(
            screenInstanceId = screenInstanceId,
            banner = normalAds.slotState(HomeAdsKeys.BANNER_HOME, screenInstanceId),
            bannerSession = bannerSession,
            lastInterResult = lastInterResult.get(),
            intervalBlocked = decision is InterIntervalDecision.Blocked,
            intervalRemainingMillis = (decision as? InterIntervalDecision.Blocked)?.remainingMillis,
        )
    }
}
