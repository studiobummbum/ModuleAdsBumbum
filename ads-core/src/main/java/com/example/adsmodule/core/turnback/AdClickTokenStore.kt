package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.AdClickTokenId
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.SessionId

/**
 * Process-memory click-token store for turnback gating.
 *
 * Policy:
 * - tokens are single-use
 * - claim is atomic; commit consumes, release returns the claim when borrow fails
 * - expired or wrong-session tokens are rejected
 */
public class AdClickTokenStore(
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    private val lock = Any()
    private val tokens = LinkedHashMap<AdClickTokenId, TokenRecord>()

    public fun issue(
        sessionId: SessionId,
        ttlMillis: Long,
        nowMillis: Long = clock.nowMillis(),
    ): AdClickTokenId {
        require(ttlMillis > 0L) { "ttlMillis must be positive" }
        require(nowMillis >= 0L) { "nowMillis must not be negative" }
        val tokenId = AdClickTokenId(idGenerator.nextId())
        synchronized(lock) {
            purgeExpiredLocked(nowMillis)
            tokens[tokenId] = TokenRecord(
                tokenId = tokenId,
                sessionId = sessionId,
                expiresAtMillis = nowMillis + ttlMillis,
                state = TokenState.VALID,
            )
        }
        return tokenId
    }

    public fun claim(
        tokenId: AdClickTokenId,
        sessionId: SessionId,
        nowMillis: Long = clock.nowMillis(),
    ): ClaimResult = synchronized(lock) {
        purgeExpiredLocked(nowMillis)
        val record = tokens[tokenId]
            ?: return ClaimResult.Rejected("Token not found")
        if (record.sessionId != sessionId) {
            return ClaimResult.Rejected("Token session mismatch")
        }
        if (record.state != TokenState.VALID) {
            return ClaimResult.Rejected("Token state is ${record.state}")
        }
        if (nowMillis >= record.expiresAtMillis) {
            tokens.remove(tokenId)
            return ClaimResult.Rejected("Token expired")
        }
        record.state = TokenState.CLAIMED
        ClaimResult.Accepted(
            Claim(
                tokenId = tokenId,
                sessionId = sessionId,
                expiresAtMillis = record.expiresAtMillis,
            ),
        )
    }

    public fun commit(claim: Claim): Boolean = synchronized(lock) {
        val record = tokens[claim.tokenId] ?: return false
        if (record.state != TokenState.CLAIMED) {
            return false
        }
        if (record.sessionId != claim.sessionId) {
            return false
        }
        tokens.remove(claim.tokenId)
        true
    }

    public fun release(claim: Claim, nowMillis: Long = clock.nowMillis()): Boolean =
        synchronized(lock) {
            val record = tokens[claim.tokenId] ?: return false
            if (record.state != TokenState.CLAIMED) {
                return false
            }
            if (record.sessionId != claim.sessionId) {
                return false
            }
            if (nowMillis >= record.expiresAtMillis) {
                tokens.remove(claim.tokenId)
                return false
            }
            record.state = TokenState.VALID
            true
        }

    public fun invalidate(tokenId: AdClickTokenId): Boolean = synchronized(lock) {
        tokens.remove(tokenId) != null
    }

    public fun invalidateSession(sessionId: SessionId): Int = synchronized(lock) {
        val removed = tokens.entries.filter { it.value.sessionId == sessionId }.map { it.key }
        removed.forEach { tokens.remove(it) }
        removed.size
    }

    /**
     * Returns true when [sessionId] has at least one non-expired VALID or CLAIMED token.
     * Expired tokens are purged as a side effect of the query.
     */
    public fun hasValidToken(
        sessionId: SessionId,
        nowMillis: Long = clock.nowMillis(),
    ): Boolean = synchronized(lock) {
        purgeExpiredLocked(nowMillis)
        tokens.values.any { it.sessionId == sessionId }
    }

    /**
     * First non-expired token for [sessionId], if any. Expired tokens are purged first.
     */
    public fun findValidToken(
        sessionId: SessionId,
        nowMillis: Long = clock.nowMillis(),
    ): AdClickTokenView? = synchronized(lock) {
        purgeExpiredLocked(nowMillis)
        tokens.values.firstOrNull { it.sessionId == sessionId }?.let {
            AdClickTokenView(
                tokenId = it.tokenId,
                sessionId = it.sessionId,
                expiresAtMillis = it.expiresAtMillis,
                state = it.state,
            )
        }
    }

    public fun snapshot(nowMillis: Long = clock.nowMillis()): AdClickTokenSnapshot =
        synchronized(lock) {
            purgeExpiredLocked(nowMillis)
            AdClickTokenSnapshot(
                tokens = tokens.values.map {
                    AdClickTokenView(
                        tokenId = it.tokenId,
                        sessionId = it.sessionId,
                        expiresAtMillis = it.expiresAtMillis,
                        state = it.state,
                    )
                },
                capturedAtMillis = nowMillis,
            )
        }

    private fun purgeExpiredLocked(nowMillis: Long) {
        val expired = tokens.values.filter { nowMillis >= it.expiresAtMillis }.map { it.tokenId }
        expired.forEach { tokens.remove(it) }
    }

    private data class TokenRecord(
        val tokenId: AdClickTokenId,
        val sessionId: SessionId,
        val expiresAtMillis: Long,
        var state: TokenState,
    )
}

public enum class TokenState {
    VALID,
    CLAIMED,
}

public data class Claim(
    val tokenId: AdClickTokenId,
    val sessionId: SessionId,
    val expiresAtMillis: Long,
)

public sealed class ClaimResult {
    public data class Accepted(val claim: Claim) : ClaimResult()

    public data class Rejected(val reason: String) : ClaimResult()
}

public data class AdClickTokenView(
    val tokenId: AdClickTokenId,
    val sessionId: SessionId,
    val expiresAtMillis: Long,
    val state: TokenState,
)

public data class AdClickTokenSnapshot(
    val tokens: List<AdClickTokenView>,
    val capturedAtMillis: Long,
)
