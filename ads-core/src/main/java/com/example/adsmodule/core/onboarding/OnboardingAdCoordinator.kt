package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.AudienceType
import com.example.adsmodule.core.config.AdsConfigSnapshot
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
        pages.forEach { page ->
            OnboardingPages.requireValid(page)
            if (!policy.isAdsEnabled(page)) return@forEach
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
            return NormalScreenBindResult.Rejected(
                reason = "ads disabled for page $logicalPage",
                state = slotState(logicalPage),
            )
        }
        return mutex.withLock {
            val existing = boundSessions[logicalPage]
            if (existing != null && !existing.finished.get()) {
                return@withLock NormalScreenBindResult.Bound(
                    session = existing,
                    state = slotState(logicalPage),
                )
            }
            when (
                val result = normalAds.bind(
                    OnboardingConfigKeys.NATIVE,
                    OnboardingScreenInstances.page(logicalPage),
                )
            ) {
                is NormalScreenBindResult.Bound -> {
                    boundSessions[logicalPage] = result.session
                    result
                }
                is NormalScreenBindResult.Rejected -> result
            }
        }
    }

    public fun unbindPage(
        logicalPage: Int,
        mode: NormalScreenUnbindMode = NormalScreenUnbindMode.RELEASE,
    ) {
        OnboardingPages.requireValid(logicalPage)
        val session = boundSessions.remove(logicalPage) ?: return
        normalAds.unbind(session, mode)
    }

    public fun bindPageAsync(
        logicalPage: Int,
        onResult: (NormalScreenBindResult) -> Unit = {},
    ) {
        scope.launch {
            onResult(bindPage(logicalPage))
        }
    }
}
