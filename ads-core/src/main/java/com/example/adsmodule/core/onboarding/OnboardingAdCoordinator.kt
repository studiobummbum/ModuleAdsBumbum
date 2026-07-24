package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.debug.AdsModuleLog
import com.example.adsmodule.core.normal.NormalScreenAdCoordinator
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenBindSession
import com.example.adsmodule.core.normal.NormalScreenSlotState
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
import com.example.adsmodule.core.refill.AdsConfigSnapshotProvider
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns per-page Native preload/bind for onboarding.
 *
 * Look-ahead:
 * - Language Dup / Apply → pages 1 and 2 (eligible only)
 * - Page 1 visible → page 3
 * - Page 2 visible → page 4
 */
public class OnboardingAdCoordinator(
    private val scope: CoroutineScope,
    private val normalAds: NormalScreenAdCoordinator,
    private val snapshotProvider: AdsConfigSnapshotProvider,
    private val audience: AudienceType,
) {
    private val mutex = Mutex()
    private val boundSessions =
        ConcurrentHashMap<Int, NormalScreenBindSession>()
    private val policyRef = AtomicReference(OnboardingConfigPolicy.defaultAllEnabled())
    private val _pageBindEvents = MutableSharedFlow<OnboardingPageBindEvent>(
        extraBufferCapacity = OnboardingPages.COUNT,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits when a page bind/replace should be applied to sticky UI hosts. */
    public val pageBindEvents: SharedFlow<OnboardingPageBindEvent> = _pageBindEvents.asSharedFlow()

    public fun refreshPolicy(snapshot: AdsConfigSnapshot? = snapshotProvider.current()) {
        policyRef.set(OnboardingConfigPolicy.resolveOrDefault(snapshot, audience))
    }

    public fun currentPolicy(): OnboardingPagePolicy = policyRef.get()

    public fun ensureEarlyPreload(snapshot: AdsConfigSnapshot) {
        refreshPolicy(snapshot)
        preloadEligible(listOf(1, 2))
    }

    public fun onPageVisible(logicalPage: Int) {
        OnboardingPages.requireValid(logicalPage)
        val hasReady = normalAds.hasReadyObject(
            OnboardingConfigKeys.NATIVE,
            OnboardingScreenInstances.page(logicalPage),
        )
        AdsModuleLog.i("VISIBLE onboard page=$logicalPage hasReady=$hasReady")
        when (logicalPage) {
            1 -> {
                preloadEligible(listOf(3))
                onFullPreload?.invoke(1)
            }
            2 -> {
                preloadEligible(listOf(4))
                onFullPreload?.invoke(2)
            }
            3 -> onFullPreload?.invoke(2)
        }
        preloadEligible(listOf(logicalPage))
    }

    /**
     * Optional hook so demo/app can preload Full 1/2 without ads-core depending on
     * Full Activity UI. Full index is 1 or 2.
     */
    public var onFullPreload: ((fullIndex: Int) -> Unit)? = null

    public fun preloadEligible(pages: Collection<Int>) {
        val policy = policyRef.get()
        val eligible = pages.filter { page ->
            OnboardingPages.requireValid(page)
            policy.isAdsEnabled(page)
        }
        if (eligible.isNotEmpty()) {
            AdsModuleLog.i("PRELOAD onboard pages=$eligible")
        }
        eligible.forEach { page ->
            normalAds.ensureLoadedAsync(
                OnboardingConfigKeys.NATIVE,
                OnboardingScreenInstances.page(page),
            )
        }
    }

    public fun slotState(logicalPage: Int): NormalScreenSlotState {
        OnboardingPages.requireValid(logicalPage)
        return normalAds.slotState(
            OnboardingConfigKeys.NATIVE,
            OnboardingScreenInstances.page(logicalPage),
        )
    }

    public fun pageAdsSnapshot(activePages: Collection<Int>): Map<Int, NormalScreenSlotState> =
        activePages.associateWith { slotState(it) }

    public fun boundAd(logicalPage: Int): OnboardingBoundAd? {
        val session = boundSessions[logicalPage] ?: return null
        return OnboardingBoundAd(logicalPage = logicalPage, session = session)
    }

    public suspend fun bindPage(logicalPage: Int): NormalScreenBindResult {
        OnboardingPages.requireValid(logicalPage)
        val policy = policyRef.get()
        if (!policy.isAdsEnabled(logicalPage)) {
            AdsModuleLog.i("BIND onboard page=$logicalPage obtained=false reason=ads_disabled")
            return NormalScreenBindResult.Rejected(
                reason = "ads disabled for page $logicalPage",
                state = slotState(logicalPage),
            )
        }
        return mutex.withLock {
            val existing = boundSessions[logicalPage]
            val result = if (existing != null && !existing.finished.get()) {
                // Sticky: keep SHOWING; swap only when a newer READY object exists.
                when (
                    val replaced = normalAds.replaceBoundIfReady(
                        OnboardingConfigKeys.NATIVE,
                        OnboardingScreenInstances.page(logicalPage),
                    )
                ) {
                    is NormalScreenBindResult.Bound -> {
                        boundSessions[logicalPage] = replaced.session
                        replaced
                    }
                    is NormalScreenBindResult.Rejected -> {
                        NormalScreenBindResult.Bound(
                            session = existing,
                            state = slotState(logicalPage),
                        )
                    }
                }
            } else {
                when (
                    val bound = normalAds.bind(
                        OnboardingConfigKeys.NATIVE,
                        OnboardingScreenInstances.page(logicalPage),
                    )
                ) {
                    is NormalScreenBindResult.Bound -> {
                        boundSessions[logicalPage] = bound.session
                        bound
                    }
                    is NormalScreenBindResult.Rejected -> bound
                }
            }
            when (result) {
                is NormalScreenBindResult.Bound -> AdsModuleLog.i(
                    "BIND onboard page=$logicalPage obtained=true " +
                        "objectId=${result.session.objectId.value}" +
                        if (result.previousSession != null) " replace=true" else "",
                )
                is NormalScreenBindResult.Rejected -> AdsModuleLog.i(
                    "BIND onboard page=$logicalPage obtained=false reason=${result.reason}",
                )
            }
            result
        }
    }

    public fun unbindPage(
        logicalPage: Int,
        mode: NormalScreenUnbindMode = NormalScreenUnbindMode.CONSUME,
    ) {
        OnboardingPages.requireValid(logicalPage)
        val session = boundSessions.remove(logicalPage) ?: return
        normalAds.unbind(session, mode)
    }

    public fun consumeReplacedSession(session: NormalScreenBindSession) {
        normalAds.unbind(session, NormalScreenUnbindMode.CONSUME)
    }

    /**
     * Keep the currently displayed native (sticky), but request a background reload so a
     * newer READY can replace it in-place via [replaceBoundIfReady].
     *
     * Emits [pageBindEvents] when the UI should re-apply (immediate keep, then swap).
     */
    public fun reloadPageKeepingVisible(logicalPage: Int) {
        OnboardingPages.requireValid(logicalPage)
        val policy = policyRef.get()
        if (!policy.isAdsEnabled(logicalPage)) return
        val configKey = OnboardingConfigKeys.NATIVE
        val screen = OnboardingScreenInstances.page(logicalPage)
        val beforeId = boundSessions[logicalPage]?.objectId
        normalAds.requestBackgroundReload(configKey, screen)
        scope.launch {
            val first = bindPage(logicalPage)
            _pageBindEvents.tryEmit(OnboardingPageBindEvent(logicalPage, first))
            if (first is NormalScreenBindResult.Bound && first.previousSession != null) {
                return@launch
            }
            // Wait for refill to produce a newer READY, then swap in-place.
            repeat(RELOAD_POLL_ATTEMPTS) {
                delay(RELOAD_POLL_MILLIS)
                if (!normalAds.hasReadyObject(configKey, screen)) return@repeat
                val next = bindPage(logicalPage)
                if (next is NormalScreenBindResult.Bound &&
                    (next.previousSession != null || next.session.objectId != beforeId)
                ) {
                    _pageBindEvents.tryEmit(OnboardingPageBindEvent(logicalPage, next))
                    return@launch
                }
            }
        }
    }

    public fun bindPageAsync(
        logicalPage: Int,
        onResult: (NormalScreenBindResult) -> Unit = {},
    ) {
        scope.launch {
            onResult(bindPage(logicalPage))
        }
    }

    private companion object {
        const val RELOAD_POLL_ATTEMPTS: Int = 40
        const val RELOAD_POLL_MILLIS: Long = 100L
    }
}

/** UI host signal: apply [result] for [logicalPage] without blanking sticky natives. */
public data class OnboardingPageBindEvent(
    val logicalPage: Int,
    val result: NormalScreenBindResult,
)
