package com.example.adsmodule.core.debug

import com.example.adsmodule.core.AdClickTokenId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StoredAdView
import com.example.adsmodule.core.turnback.AdClickTokenStore
import com.example.adsmodule.core.turnback.AtomicBorrowService
import com.example.adsmodule.core.turnback.TurnbackSelector

/**
 * Public turnback preview + borrow actions for TurnbackSimulatorFragment.
 */
public class TurnbackDebugActions(
    private val storage: AdStorage,
    private val tokenStore: AdClickTokenStore,
    private val borrowService: AtomicBorrowService,
) {
    public fun previewEligible(): List<StoredAdView> =
        storage.listReady()
            .filter(TurnbackSelector::isEligible)
            .sortedWith(TurnbackSelector.comparator)
            .map { ad ->
                StoredAdView(
                    objectId = ad.objectId,
                    sourceConfigKey = ad.sourceConfigKey,
                    sourceListIndex = ad.sourceListIndex,
                    sourceType = ad.sourceType,
                    sourceAdunit = ad.sourceAdunit,
                    sourceWeight = ad.sourceWeight,
                    screenInstanceId = ad.screenInstanceId,
                    loadedAt = ad.loadedAt,
                    state = ad.state,
                    sdkHandle = ad.sdkHandle,
                )
            }

    public fun previewWinner(): StoredAdView? = previewEligible().firstOrNull()

    public fun issueToken(sessionId: SessionId, ttlMillis: Long = 30_000L): AdClickTokenId =
        tokenStore.issue(sessionId = sessionId, ttlMillis = ttlMillis)

    public fun borrow(sessionId: SessionId, tokenId: AdClickTokenId): ReserveResult =
        borrowService.borrow(sessionId = sessionId, tokenId = tokenId)

    public fun borrowWithFreshToken(
        sessionId: SessionId,
        ttlMillis: Long = 30_000L,
    ): Pair<AdClickTokenId, ReserveResult> {
        val tokenId = issueToken(sessionId, ttlMillis)
        return tokenId to borrow(sessionId, tokenId)
    }
}
