package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingBoundaryCoordinatorTest {
    @Test
    fun pagerCount_andDistinctScreenIds() {
        val env = Env()
        env.coordinator.startOrRestore(allPagesPolicy())
        val snap = env.coordinator.snapshot.value!!
        assertEquals(listOf(1, 2, 3, 4), snap.activePages)
        assertEquals(4, OnboardingScreenInstances.all.distinct().size)
        assertEquals("ONBOARD_NATIVE#1", OnboardingScreenInstances.page1.value)
        assertEquals("ONBOARD_NATIVE#4", OnboardingScreenInstances.page4.value)
    }

    @Test
    fun screen3Off_adapterHasThreePages_keepsLogicalIds() {
        val env = Env()
        val policy = OnboardingPagePolicy(
            pages = listOf(
                page(1, screen = true),
                page(2, screen = true),
                page(3, screen = false),
                page(4, screen = true),
            ),
        )
        env.coordinator.startOrRestore(policy)
        assertEquals(listOf(1, 2, 4), env.coordinator.snapshot.value!!.activePages)
        assertEquals(0, env.coordinator.adapterIndexFor(1))
        assertEquals(1, env.coordinator.adapterIndexFor(2))
        assertEquals(2, env.coordinator.adapterIndexFor(4))
        assertEquals(-1, env.coordinator.adapterIndexFor(3))
    }

    @Test
    fun page1Forward_movesToPage2() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        val result = env.coordinator.requestForward(session)
        assertEquals(OnboardingForwardResult.MovedToPage(2), result)
        assertEquals(2, env.coordinator.snapshot.value!!.currentLogicalPage)
    }

    @Test
    fun page2Forward_launchesFull1_doesNotAdvance() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        env.coordinator.requestForward(session)
        val result = env.coordinator.requestForward(session)
        assertTrue(result is OnboardingForwardResult.LaunchFull)
        val launch = result as OnboardingForwardResult.LaunchFull
        assertEquals(OnboardingNavigationEffect.OPEN_FULL1, launch.effect)
        assertEquals(3, launch.targetLogicalPage)
        assertEquals(2, env.coordinator.snapshot.value!!.currentLogicalPage)
        assertEquals(3, env.coordinator.snapshot.value!!.pendingTargetLogicalPage)
    }

    @Test
    fun full1Result_restoresPager3() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        env.coordinator.requestForward(session)
        val launch = env.coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
        assertTrue(
            env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1),
        )
        assertTrue(
            env.coordinator.onFullResult(
                OnboardingFullResult(
                    sessionId = session,
                    fullSessionId = launch.fullSessionId,
                    fullIndex = 1,
                    targetLogicalPage = 3,
                ),
            ),
        )
        assertEquals(3, env.coordinator.snapshot.value!!.currentLogicalPage)
        assertTrue(env.coordinator.snapshot.value!!.full1Completed)
        assertNull(env.coordinator.snapshot.value!!.pendingFull)
    }

    @Test
    fun page3Forward_launchesFull2_thenRestoresPager4() {
        val env = Env()
        val session = env.goToPage3()
        val launch = env.coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
        assertEquals(OnboardingNavigationEffect.OPEN_FULL2, launch.effect)
        assertEquals(4, launch.targetLogicalPage)
        env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL2)
        assertTrue(
            env.coordinator.onFullResult(
                OnboardingFullResult(
                    sessionId = session,
                    fullSessionId = launch.fullSessionId,
                    fullIndex = 2,
                    targetLogicalPage = 4,
                ),
            ),
        )
        assertEquals(4, env.coordinator.snapshot.value!!.currentLogicalPage)
    }

    @Test
    fun page3Off_full1TargetsPage4_skipsFull2() {
        val env = Env()
        val policy = OnboardingPagePolicy(
            pages = listOf(
                page(1, true),
                page(2, true),
                page(3, false),
                page(4, true),
            ),
        )
        val session = env.coordinator.startOrRestore(policy)
        env.coordinator.requestForward(session) // -> 2
        val launch = env.coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
        assertEquals(4, launch.targetLogicalPage)
        env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1)
        env.coordinator.onFullResult(
            OnboardingFullResult(session, launch.fullSessionId, 1, 4),
        )
        assertEquals(4, env.coordinator.snapshot.value!!.currentLogicalPage)
        val home = env.coordinator.requestForward(session)
        assertTrue(home is OnboardingForwardResult.OpenHome)
    }

    @Test
    fun page2Off_skipsFull1_goesDirectlyTowardPage3() {
        val env = Env()
        val policy = OnboardingPagePolicy(
            pages = listOf(
                page(1, true),
                page(2, false),
                page(3, true),
                page(4, true),
            ),
        )
        val session = env.coordinator.startOrRestore(policy)
        val result = env.coordinator.requestForward(session)
        assertEquals(OnboardingForwardResult.MovedToPage(3), result)
        assertFalse(env.coordinator.snapshot.value!!.full1Completed)
    }

    @Test
    fun backwardFromPage3_launchesFull1_thenRestoresPage2() {
        val env = Env()
        val session = env.goToPage3()
        val back = env.coordinator.requestBackward(session)
        assertTrue(back is OnboardingBackwardResult.LaunchFull)
        val launch = back as OnboardingBackwardResult.LaunchFull
        assertEquals(OnboardingNavigationEffect.OPEN_FULL1, launch.effect)
        assertEquals(2, launch.targetLogicalPage)
        assertEquals(3, env.coordinator.snapshot.value!!.currentLogicalPage)
        assertNotNull(env.coordinator.snapshot.value!!.pendingFull)

        assertTrue(env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1))
        assertTrue(
            env.coordinator.onFullResult(
                OnboardingFullResult(session, launch.fullSessionId, 1, 2),
            ),
        )
        assertEquals(2, env.coordinator.snapshot.value!!.currentLogicalPage)
        assertNull(env.coordinator.snapshot.value!!.pendingFull)

        // Forward again after Full1 already completed skips Full1.
        val forward = env.coordinator.requestForward(session)
        assertEquals(OnboardingForwardResult.MovedToPage(3), forward)
    }

    @Test
    fun backwardFromPage4_launchesFull2_thenRestoresPage3() {
        val env = Env()
        val session = env.goToPage4()
        val back = env.coordinator.requestBackward(session)
        assertTrue(back is OnboardingBackwardResult.LaunchFull)
        val launch = back as OnboardingBackwardResult.LaunchFull
        assertEquals(OnboardingNavigationEffect.OPEN_FULL2, launch.effect)
        assertEquals(3, launch.targetLogicalPage)
        assertEquals(4, env.coordinator.snapshot.value!!.currentLogicalPage)

        assertTrue(env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL2))
        assertTrue(
            env.coordinator.onFullResult(
                OnboardingFullResult(session, launch.fullSessionId, 2, 3),
            ),
        )
        assertEquals(3, env.coordinator.snapshot.value!!.currentLogicalPage)
    }

    @Test
    fun backwardFromPage4_whenPage3Off_launchesFull1() {
        val env = Env()
        val policy = OnboardingPagePolicy(
            pages = listOf(
                page(1, true),
                page(2, true),
                page(3, false),
                page(4, true),
            ),
        )
        val session = env.coordinator.startOrRestore(policy)
        env.coordinator.requestForward(session) // -> 2
        val forwardFull = env.coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
        env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1)
        env.coordinator.onFullResult(
            OnboardingFullResult(session, forwardFull.fullSessionId, 1, 4),
        )
        assertEquals(4, env.coordinator.snapshot.value!!.currentLogicalPage)

        val back = env.coordinator.requestBackward(session)
        assertTrue(back is OnboardingBackwardResult.LaunchFull)
        val launch = back as OnboardingBackwardResult.LaunchFull
        assertEquals(OnboardingNavigationEffect.OPEN_FULL1, launch.effect)
        assertEquals(2, launch.targetLogicalPage)
    }

    @Test
    fun backwardWhileForwardFullPending_cancelsAndMovesToPage1() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        env.coordinator.requestForward(session) // -> 2
        val launch = env.coordinator.requestForward(session)
        assertTrue(launch is OnboardingForwardResult.LaunchFull)
        assertNotNull(env.coordinator.snapshot.value!!.pendingFull)

        // Pull back from page 2 → page 1: cancel pending Full1, no backward Full gate.
        val back = env.coordinator.requestBackward(session)
        assertEquals(OnboardingBackwardResult.MovedToPage(1), back)
        assertNull(env.coordinator.snapshot.value!!.pendingFull)
        assertNull(env.coordinator.snapshot.value!!.pendingEffect)
        assertEquals(1, env.coordinator.snapshot.value!!.currentLogicalPage)
    }

    @Test
    fun staleFullResult_ignored() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        env.coordinator.requestForward(session)
        val launch = env.coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
        assertFalse(
            env.coordinator.onFullResult(
                OnboardingFullResult(
                    sessionId = session,
                    fullSessionId = FullSessionId("stale"),
                    fullIndex = 1,
                    targetLogicalPage = 3,
                ),
            ),
        )
        assertFalse(
            env.coordinator.onFullResult(
                OnboardingFullResult(
                    sessionId = OnboardingSessionId("other"),
                    fullSessionId = launch.fullSessionId,
                    fullIndex = 1,
                    targetLogicalPage = 3,
                ),
            ),
        )
        assertEquals(2, env.coordinator.snapshot.value!!.currentLogicalPage)
    }

    @Test
    fun claimEffect_onceOnly() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        env.coordinator.requestForward(session)
        env.coordinator.requestForward(session)
        assertTrue(env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1))
        assertFalse(env.coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1))
    }

    @Test
    fun recreate_restoresPager3AndFlags() {
        val env = Env()
        val session = env.goToPage3()
        val saved = env.coordinator.exportSavedState()!!
        assertEquals(3, saved.currentLogicalPage)
        assertTrue(saved.full1Completed)

        val restored = Env()
        restored.coordinator.restore(saved)
        val snap = restored.coordinator.snapshot.value!!
        assertEquals(session, snap.sessionId)
        assertEquals(3, snap.currentLogicalPage)
        assertTrue(snap.full1Completed)
        assertEquals(listOf(1, 2, 3, 4), snap.activePages)
    }

    @Test
    fun recreate_restoresPendingFull() {
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        env.coordinator.requestForward(session)
        env.coordinator.requestForward(session)
        val saved = env.coordinator.exportSavedState()!!
        assertNotNull(saved.pendingFull)
        assertEquals(OnboardingNavigationEffect.OPEN_FULL1, saved.pendingEffect)

        val restored = Env()
        restored.coordinator.restore(saved)
        assertEquals(2, restored.coordinator.snapshot.value!!.currentLogicalPage)
        assertEquals(3, restored.coordinator.snapshot.value!!.pendingTargetLogicalPage)
        assertNotNull(restored.coordinator.snapshot.value!!.pendingFull)
    }

    @Test
    fun forwardAndNext_shareSameApi() {
        // Documented by using requestForward for both gesture and button.
        val env = Env()
        val session = env.coordinator.startOrRestore(allPagesPolicy())
        val swipe = env.coordinator.requestForward(session)
        assertEquals(OnboardingForwardResult.MovedToPage(2), swipe)
    }

    private fun allPagesPolicy(): OnboardingPagePolicy =
        OnboardingPagePolicy(
            pages = (1..4).map { page(it, screen = true, ads = true) },
        )

    private fun page(
        logical: Int,
        screen: Boolean,
        ads: Boolean = true,
    ): OnboardingPageModel = OnboardingPageModel(
        logicalPage = logical,
        screenInstanceId = OnboardingScreenInstances.page(logical),
        screenEnabled = screen,
        adsEnabled = screen && ads,
    )

    private class Env {
        private val seq = AtomicLong(0L)
        private var now = 0L
        val coordinator = OnboardingBoundaryCoordinator(
            clock = Clock { now++ },
            idGenerator = IdGenerator { "id-${seq.incrementAndGet()}" },
        )

        fun goToPage3(): OnboardingSessionId {
            val session = coordinator.startOrRestore(allPagesPolicy())
            coordinator.requestForward(session)
            val launch = coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
            coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL1)
            coordinator.onFullResult(
                OnboardingFullResult(session, launch.fullSessionId, 1, 3),
            )
            return session
        }

        fun goToPage4(): OnboardingSessionId {
            val session = goToPage3()
            val launch = coordinator.requestForward(session) as OnboardingForwardResult.LaunchFull
            coordinator.claimEffect(session, OnboardingNavigationEffect.OPEN_FULL2)
            coordinator.onFullResult(
                OnboardingFullResult(session, launch.fullSessionId, 2, 4),
            )
            return session
        }

        private fun allPagesPolicy(): OnboardingPagePolicy =
            OnboardingPagePolicy(
                pages = (1..4).map {
                    OnboardingPageModel(
                        logicalPage = it,
                        screenInstanceId = OnboardingScreenInstances.page(it),
                        screenEnabled = true,
                        adsEnabled = true,
                    )
                },
            )
    }
}
