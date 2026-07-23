package com.example.adsmodule.core.lifecycle

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.SessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class ForegroundSessionTrackerTest {
    @Test
    fun foregroundAfterAdClick_retainsClickReason() {
        val tracker = ForegroundSessionTracker(clock = MutableClock())
        val session = SessionId("session-1")
        tracker.bindSession(session)
        val background = tracker.onBackground(session, BackgroundReason.AD_CLICK)
            as ForegroundTransitionResult.Accepted
        assertEquals(BackgroundReason.AD_CLICK, background.snapshot.lastBackgroundReason)
        assertFalse(background.snapshot.isInForeground)

        val foreground = tracker.onForeground(session) as ForegroundTransitionResult.Accepted
        assertTrue(foreground.snapshot.isInForeground)
        assertEquals(BackgroundReason.AD_CLICK, foreground.snapshot.lastBackgroundReason)
    }

    @Test
    fun foregroundAfterHome_hasNoTurnbackHint() {
        val tracker = ForegroundSessionTracker(clock = MutableClock())
        val session = SessionId("session-1")
        tracker.bindSession(session)
        tracker.onBackground(session, BackgroundReason.USER_BACKGROUND)
        val foreground = tracker.onForeground(session) as ForegroundTransitionResult.Accepted
        assertEquals(BackgroundReason.USER_BACKGROUND, foreground.snapshot.lastBackgroundReason)
    }

    @Test
    fun staleGeneration_isIgnored() {
        val tracker = ForegroundSessionTracker(clock = MutableClock())
        val session = SessionId("session-1")
        val bound = tracker.bindSession(session)
        val ignored = tracker.onBackground(
            sessionId = session,
            reason = BackgroundReason.USER_BACKGROUND,
            generation = bound.generation - 1,
        )
        assertTrue(ignored is ForegroundTransitionResult.Ignored)
        assertTrue(tracker.snapshot.value.isInForeground)
    }

    @Test
    fun newSession_invalidatesStaleTransition() {
        val tracker = ForegroundSessionTracker(clock = MutableClock())
        tracker.bindSession(SessionId("old"))
        tracker.bindSession(SessionId("new"))
        val ignored = tracker.onBackground(SessionId("old"), BackgroundReason.UNKNOWN)
        assertTrue(ignored is ForegroundTransitionResult.Ignored)
        assertEquals(SessionId("new"), tracker.snapshot.value.sessionId)
        assertNull(tracker.snapshot.value.lastBackgroundReason)
    }

    private class MutableClock(start: Long = 0L) : Clock {
        private val now = AtomicLong(start)
        override fun nowMillis(): Long = now.get()
    }
}
