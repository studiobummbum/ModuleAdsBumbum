package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.AdClickTokenId
import com.example.adsmodule.core.SessionId
import com.example.adsmodule.core.refill.WholeListRefillScheduler
import com.example.adsmodule.core.storage.AdStorage
import com.example.adsmodule.core.storage.ReserveResult
import com.example.adsmodule.core.storage.StorageSlotKey

/**
 * Orchestrates turnback borrow:
 * claim token → atomic select/pop/reserve → enqueue whole-list refill → commit token.
 *
 * Token is consumed only when borrow succeeds. Failed borrow releases the claim.
 */
public class AtomicBorrowService(
    private val tokenStore: AdClickTokenStore,
    private val storage: AdStorage,
    private val refillScheduler: WholeListRefillScheduler,
) {
    public fun borrow(
        sessionId: SessionId,
        tokenId: AdClickTokenId,
    ): ReserveResult {
        val claim = when (val claimResult = tokenStore.claim(tokenId, sessionId)) {
            is ClaimResult.Accepted -> claimResult.claim
            is ClaimResult.Rejected -> {
                return ReserveResult.Rejected("Invalid AD_CLICK_TOKEN: ${claimResult.reason}")
            }
        }

        val reserveResult = storage.atomicBorrowTurnback { accepted ->
            val slot = StorageSlotKey(
                configKey = accepted.storedAd.sourceConfigKey,
                screenInstanceId = accepted.storedAd.screenInstanceId,
            )
            refillScheduler.requestRefill(slot)
        }

        when (reserveResult) {
            is ReserveResult.Accepted -> tokenStore.commit(claim)
            is ReserveResult.Rejected -> tokenStore.release(claim)
        }
        return reserveResult
    }
}
