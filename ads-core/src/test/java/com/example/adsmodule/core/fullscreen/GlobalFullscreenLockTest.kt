package com.example.adsmodule.core.fullscreen

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ShowRequestId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.adsmodule.sdk.AdFormat

class GlobalFullscreenLockTest {
    @Test
    fun concurrentAcquire_onlyOneOwnerWins() = runBlocking {
        val lock = GlobalFullscreenLock(clock = Clock { 0L })
        val results = (1..40).map { index ->
            async(Dispatchers.Default) {
                lock.acquire(
                    FullscreenLockAcquireRequest(
                        showRequestId = ShowRequestId("show-$index"),
                        objectId = ObjectId("obj-$index"),
                        sourceConfigKey = ConfigKey("inter_splash_config_1"),
                        screenInstanceId = null,
                        format = AdFormat.INTERSTITIAL,
                        kind = FullscreenAdKind.INTERSTITIAL,
                    ),
                )
            }
        }.awaitAll()

        val acquired = results.filterIsInstance<FullscreenLockAcquireResult.Acquired>()
        val rejected = results.filterIsInstance<FullscreenLockAcquireResult.Rejected>()
        assertEquals(1, acquired.size)
        assertEquals(39, rejected.size)
        assertTrue(lock.isBusy())
        assertEquals(acquired.single().owner.showRequestId, lock.currentOwner()?.showRequestId)
    }

    @Test
    fun ownerRelease_unlocks() {
        val lock = GlobalFullscreenLock(clock = Clock { 10L })
        val acquired = lock.acquire(request("show-1")) as FullscreenLockAcquireResult.Acquired
        assertEquals(10L, acquired.owner.acquiredAtMillis)
        val released = lock.release(ShowRequestId("show-1"))
        assertTrue(released is FullscreenLockReleaseResult.Released)
        assertFalse(lock.isBusy())
        assertNull(lock.currentOwner())
    }

    @Test
    fun duplicateRelease_isIdempotent() {
        val lock = GlobalFullscreenLock(clock = Clock { 0L })
        lock.acquire(request("show-1"))
        assertTrue(lock.release(ShowRequestId("show-1")) is FullscreenLockReleaseResult.Released)
        assertTrue(
            lock.release(ShowRequestId("show-1")) is FullscreenLockReleaseResult.AlreadyReleased,
        )
        assertFalse(lock.isBusy())
    }

    @Test
    fun staleRelease_doesNotReleaseNewOwner() {
        val lock = GlobalFullscreenLock(clock = Clock { 0L })
        lock.acquire(request("old"))
        lock.release(ShowRequestId("old"))
        val newer = lock.acquire(request("new")) as FullscreenLockAcquireResult.Acquired
        val stale = lock.release(ShowRequestId("old"))
        assertTrue(stale is FullscreenLockReleaseResult.Stale)
        assertEquals(newer.owner.showRequestId, lock.currentOwner()?.showRequestId)
        assertTrue(lock.isBusy())
    }

    @Test
    fun snapshot_preservesOwnerMetadata() {
        val clock = MutableClock()
        val lock = GlobalFullscreenLock(clock = clock)
        clock.now = 42L
        lock.acquire(
            FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("show-meta"),
                objectId = ObjectId("object-meta"),
                sourceConfigKey = ConfigKey("native_splash_full_config_1"),
                screenInstanceId = null,
                format = AdFormat.NATIVE_FULLSCREEN,
                kind = FullscreenAdKind.NATIVE_FULL_SPLASH,
            ),
        )
        val owner = lock.snapshot.value.owner
        assertNotNull(owner)
        assertEquals(ShowRequestId("show-meta"), owner!!.showRequestId)
        assertEquals(ObjectId("object-meta"), owner.objectId)
        assertEquals(ConfigKey("native_splash_full_config_1"), owner.sourceConfigKey)
        assertEquals(AdFormat.NATIVE_FULLSCREEN, owner.format)
        assertEquals(FullscreenAdKind.NATIVE_FULL_SPLASH, owner.kind)
        assertEquals(42L, owner.acquiredAtMillis)
    }

    @Test
    fun lockTypes_doNotReferenceAndroidActivityOrView() {
        val ownerClass = FullscreenLockOwner::class.java
        val names = ownerClass.declaredFields.map { it.type.name } +
            ownerClass.constructors.flatMap { ctor -> ctor.parameterTypes.map { it.name } }
        assertFalse(names.any { it.contains("android.app.Activity") })
        assertFalse(names.any { it.contains("android.view.View") })
        assertFalse(names.any { it.contains("google.android.gms") })
    }

    @Test
    fun supersede_coversPrimaryAndRestoresOnRelease() {
        val lock = GlobalFullscreenLock(clock = Clock { 0L })
        lock.acquire(request("primary"))
        val supersede = lock.supersede(
            request = FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("native-full"),
                objectId = ObjectId("native-obj"),
                sourceConfigKey = ConfigKey("native_splash_full_config_1"),
                screenInstanceId = null,
                format = AdFormat.NATIVE_FULLSCREEN,
                kind = FullscreenAdKind.NATIVE_FULL_SPLASH,
            ),
            expectedCoveredShowRequestId = ShowRequestId("primary"),
        )
        assertTrue(supersede is FullscreenLockSupersedeResult.Superseded)
        assertEquals(ShowRequestId("native-full"), lock.currentOwner()?.showRequestId)
        assertEquals(1, lock.coveredOwners().size)

        val released = lock.release(ShowRequestId("native-full"))
        assertTrue(released is FullscreenLockReleaseResult.Released)
        assertEquals(ShowRequestId("primary"), lock.currentOwner()?.showRequestId)
        assertTrue(lock.coveredOwners().isEmpty())
    }

    @Test
    fun completeCovered_removesCoveredWithoutUnlockingTop() {
        val lock = GlobalFullscreenLock(clock = Clock { 0L })
        lock.acquire(request("primary"))
        lock.supersede(
            request = FullscreenLockAcquireRequest(
                showRequestId = ShowRequestId("native-full"),
                objectId = ObjectId("native-obj"),
                sourceConfigKey = ConfigKey("native_splash_full_config_1"),
                screenInstanceId = null,
                format = AdFormat.NATIVE_FULLSCREEN,
                kind = FullscreenAdKind.NATIVE_FULL_SPLASH,
            ),
            expectedCoveredShowRequestId = ShowRequestId("primary"),
        )
        val completed = lock.completeCovered(ShowRequestId("primary"))
        assertTrue(completed is FullscreenLockCoveredCompletionResult.Completed)
        assertEquals(ShowRequestId("native-full"), lock.currentOwner()?.showRequestId)
        assertTrue(lock.coveredOwners().isEmpty())
    }

    private fun request(id: String): FullscreenLockAcquireRequest =
        FullscreenLockAcquireRequest(
            showRequestId = ShowRequestId(id),
            objectId = ObjectId("obj-$id"),
            sourceConfigKey = ConfigKey("inter_splash_config_1"),
            screenInstanceId = null,
            format = AdFormat.INTERSTITIAL,
            kind = FullscreenAdKind.INTERSTITIAL,
        )

    private class MutableClock : Clock {
        var now: Long = 0L
        override fun nowMillis(): Long = now
    }
}
