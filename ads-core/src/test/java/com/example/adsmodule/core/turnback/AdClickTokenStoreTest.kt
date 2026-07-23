package com.example.adsmodule.core.turnback

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.SessionId
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdClickTokenStoreTest {
    @Test
    fun issueAndClaim_succeedsForValidToken() {
        val env = Env()
        val token = env.store.issue(env.session, ttlMillis = 1_000L)
        val claim = env.store.claim(token, env.session) as ClaimResult.Accepted
        assertEquals(token, claim.claim.tokenId)
        assertTrue(env.store.commit(claim.claim))
        assertTrue(env.store.claim(token, env.session) is ClaimResult.Rejected)
    }

    @Test
    fun claim_rejectsExpiredToken() {
        val env = Env()
        val token = env.store.issue(env.session, ttlMillis = 100L)
        env.clock.advance(100L)
        assertTrue(env.store.claim(token, env.session) is ClaimResult.Rejected)
        assertEquals(0, env.store.snapshot().tokens.size)
    }

    @Test
    fun claim_rejectsWrongSession() {
        val env = Env()
        val token = env.store.issue(env.session, ttlMillis = 1_000L)
        assertTrue(
            env.store.claim(token, SessionId("other")) is ClaimResult.Rejected,
        )
    }

    @Test
    fun release_returnsClaimForRetryWhenStillValid() {
        val env = Env()
        val token = env.store.issue(env.session, ttlMillis = 1_000L)
        val claim = (env.store.claim(token, env.session) as ClaimResult.Accepted).claim
        assertTrue(env.store.release(claim))
        val reclaim = env.store.claim(token, env.session)
        assertTrue(reclaim is ClaimResult.Accepted)
    }

    @Test
    fun release_failsWhenExpiredDuringClaim() {
        val env = Env()
        val token = env.store.issue(env.session, ttlMillis = 50L)
        val claim = (env.store.claim(token, env.session) as ClaimResult.Accepted).claim
        env.clock.advance(50L)
        assertFalse(env.store.release(claim))
        assertTrue(env.store.claim(token, env.session) is ClaimResult.Rejected)
    }

    @Test
    fun invalidateSession_removesAllTokens() {
        val env = Env()
        env.store.issue(env.session, ttlMillis = 1_000L)
        env.store.issue(env.session, ttlMillis = 1_000L)
        assertEquals(2, env.store.invalidateSession(env.session))
        assertEquals(0, env.store.snapshot().tokens.size)
    }

    @Test
    fun hasValidToken_trueUntilExpiryBoundary() {
        val env = Env()
        env.store.issue(env.session, ttlMillis = 100L)
        assertTrue(env.store.hasValidToken(env.session))
        assertNotNull(env.store.findValidToken(env.session))
        env.clock.advance(99L)
        assertTrue(env.store.hasValidToken(env.session))
        env.clock.advance(1L)
        assertFalse(env.store.hasValidToken(env.session))
        assertNull(env.store.findValidToken(env.session))
        assertEquals(0, env.store.snapshot().tokens.size)
    }

    @Test
    fun hasValidToken_ignoresOtherSessions() {
        val env = Env()
        env.store.issue(SessionId("other"), ttlMillis = 1_000L)
        assertFalse(env.store.hasValidToken(env.session))
    }

    private class Env {
        val clock = FakeClock()
        val session = SessionId("session-1")
        private val seq = AtomicLong(0L)
        val store = AdClickTokenStore(
            clock = clock,
            idGenerator = IdGenerator { "token-${seq.incrementAndGet()}" },
        )
    }

    private class FakeClock(start: Long = 0L) : Clock {
        private val now = AtomicLong(start)
        override fun nowMillis(): Long = now.get()
        fun advance(delta: Long) {
            now.addAndGet(delta)
        }
    }
}
