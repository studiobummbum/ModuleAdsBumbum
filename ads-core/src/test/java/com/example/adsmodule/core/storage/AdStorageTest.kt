package com.example.adsmodule.core.storage

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdStorageTest {
    @Test
    fun putReady_rejectsDuplicateObjectIdAndNonReadyState() {
        val env = StorageEnv()
        val ad = env.nativeAd(objectId = "o1", screen = OnboardingScreenInstances.page1)
        assertTrue(env.storage.putReady(ad) is PutResult.Accepted)

        val duplicate = env.storage.putReady(
            ad.copy(sdkHandle = TrackingHandle(AdFormat.NATIVE, "unit-2")),
        )
        assertTrue(duplicate is PutResult.Rejected)

        val notReady = env.storage.putReady(
            env.nativeAd(
                objectId = "o2",
                screen = OnboardingScreenInstances.page2,
                state = AdSlotState.LOADING,
            ),
        )
        assertTrue(notReady is PutResult.Rejected)
    }

    @Test
    fun nativeHandle_cannotBeReusedAfterInsert() {
        val env = StorageEnv()
        val handle = TrackingHandle(AdFormat.NATIVE, "shared-native")
        val first = env.nativeAd(
            objectId = "o1",
            screen = OnboardingScreenInstances.page1,
            handle = handle,
        )
        assertTrue(env.storage.putReady(first) is PutResult.Accepted)

        val reuse = env.nativeAd(
            objectId = "o2",
            screen = OnboardingScreenInstances.page2,
            handle = handle,
            adunit = "shared-native",
        )
        assertTrue(env.storage.putReady(reuse) is PutResult.Rejected)

        assertTrue(env.storage.consume(ObjectId("o1")))
        assertTrue(handle.destroyed)

        val afterConsume = env.nativeAd(
            objectId = "o3",
            screen = OnboardingScreenInstances.page2,
            handle = handle,
        )
        assertTrue(env.storage.putReady(afterConsume) is PutResult.Rejected)
    }

    @Test
    fun reserveNormal_matchesExactConfigAndScreen() {
        val env = StorageEnv()
        val config = ConfigKey("native_language_config_1")
        val screenA = ScreenInstanceId("lang-a")
        val screenB = ScreenInstanceId("lang-b")
        env.storage.putReady(
            env.nativeAd(objectId = "a", configKey = config, screen = screenA, weight = 80),
        )
        env.storage.putReady(
            env.nativeAd(objectId = "b", configKey = config, screen = screenB, weight = 90),
        )

        val wrongScreen = env.storage.reserveNormal(config, ScreenInstanceId("missing"))
        assertTrue(wrongScreen is ReserveResult.Rejected)

        val reserved = env.storage.reserveNormal(config, screenA) as ReserveResult.Accepted
        assertEquals(ObjectId("a"), reserved.storedAd.objectId)
        assertEquals(80, reserved.storedAd.sourceWeight)
        assertEquals(AdSlotState.RESERVED, reserved.storedAd.state)
        assertNull(env.storage.peekReady(config, screenA))
        assertNotNull(env.storage.peekReady(config, screenB))
    }

    @Test
    fun concurrentReserve_onlyOneWinner() = runBlocking {
        val env = StorageEnv()
        val config = ConfigKey("native_language_config_1")
        val screen = ScreenInstanceId("screen-1")
        env.storage.putReady(env.nativeAd(objectId = "only", configKey = config, screen = screen))

        val results = (1..30).map {
            async(Dispatchers.Default) {
                env.storage.reserveNormal(config, screen)
            }
        }.awaitAll()

        val accepted = results.filterIsInstance<ReserveResult.Accepted>()
        val rejected = results.filterIsInstance<ReserveResult.Rejected>()
        assertEquals(1, accepted.size)
        assertEquals(29, rejected.size)
        assertEquals(ObjectId("only"), accepted.single().storedAd.objectId)
    }

    @Test
    fun duplicateCallback_afterReserveIsRejected() {
        val env = StorageEnv()
        val config = ConfigKey("native_language_config_1")
        val screen = ScreenInstanceId("screen-1")
        val ad = env.nativeAd(objectId = "o1", configKey = config, screen = screen)
        env.storage.putReady(ad)

        assertTrue(env.storage.reserveNormal(config, screen) is ReserveResult.Accepted)
        assertTrue(env.storage.reserveNormal(config, screen) is ReserveResult.Rejected)
        // Slot empty after reserve — new distinct object for same slot is allowed.
        assertTrue(
            env.storage.putReady(
                env.nativeAd(objectId = "o2", configKey = config, screen = screen),
            ) is PutResult.Accepted,
        )
        // Duplicate objectId is still rejected.
        assertTrue(
            env.storage.putReady(
                env.nativeAd(objectId = "o1", configKey = config, screen = ScreenInstanceId("other")),
            ) is PutResult.Rejected,
        )
    }

    @Test
    fun expireAndDestroy_destroyHandleOutsideInventoryLock() {
        val env = StorageEnv(ttlMillis = 100L)
        val handle = TrackingHandle(AdFormat.INTERSTITIAL, "inter-1")
        env.storage.putReady(
            env.storedAd(
                objectId = "o1",
                configKey = ConfigKey("inter_splash_config_1"),
                screen = null,
                format = AdFormat.INTERSTITIAL,
                handle = handle,
                loadedAt = 0L,
            ),
        )
        env.clock.advance(150L)

        val expired = env.storage.expireDue()
        assertEquals(listOf(ObjectId("o1")), expired)
        assertTrue(handle.destroyed)
        assertEquals(AdSlotState.EXPIRED, env.storage.get(ObjectId("o1"))?.state)
        assertTrue(
            env.removed.any { it.objectId == ObjectId("o1") && it.reason == AdStorage.RemovalReason.EXPIRED },
        )

        val handle2 = TrackingHandle(AdFormat.BANNER, "banner-1")
        env.storage.putReady(
            env.storedAd(
                objectId = "o2",
                configKey = ConfigKey("banner_home_config_1"),
                screen = ScreenInstanceId("home"),
                format = AdFormat.BANNER,
                handle = handle2,
            ),
        )
        assertTrue(env.storage.destroy(ObjectId("o2")))
        assertTrue(handle2.destroyed)
        assertNull(env.storage.get(ObjectId("o2")))
    }

    @Test
    fun sharedAdunit_differentConfigAreIndependent() {
        val env = StorageEnv()
        val unit = "ca-app-pub-shared/native"
        val screen = ScreenInstanceId("screen-1")
        val a = env.nativeAd(
            objectId = "cfg-a",
            configKey = ConfigKey("native_language_config_1"),
            screen = screen,
            adunit = unit,
        )
        val b = env.nativeAd(
            objectId = "cfg-b",
            configKey = ConfigKey("native_language_dup_config_1"),
            screen = screen,
            adunit = unit,
        )
        assertTrue(env.storage.putReady(a) is PutResult.Accepted)
        assertTrue(env.storage.putReady(b) is PutResult.Accepted)

        val reservedA = env.storage.reserveNormal(
            ConfigKey("native_language_config_1"),
            screen,
        ) as ReserveResult.Accepted
        assertEquals(ObjectId("cfg-a"), reservedA.storedAd.objectId)
        assertNotNull(
            env.storage.peekReady(ConfigKey("native_language_dup_config_1"), screen),
        )
    }

    @Test
    fun fourOnboardingObjects_areDistinct() {
        val env = StorageEnv()
        val config = ConfigKey("native_onboarding_config_1")
        val ids = OnboardingScreenInstances.all.mapIndexed { index, screen ->
            val objectId = ObjectId("onb-${index + 1}")
            val put = env.storage.putReady(
                env.nativeAd(
                    objectId = objectId.value,
                    configKey = config,
                    screen = screen,
                    weight = 100 - index,
                ),
            )
            assertTrue(put is PutResult.Accepted)
            objectId
        }

        assertEquals(4, ids.toSet().size)
        val snapshot = env.storage.inspector()
        assertEquals(4, snapshot.objects.size)
        assertEquals(4, snapshot.readySlots.size)

        val reserved = OnboardingScreenInstances.all.map { screen ->
            env.storage.reserveNormal(config, screen) as ReserveResult.Accepted
        }
        assertEquals(4, reserved.map { it.storedAd.objectId }.toSet().size)
        reserved.forEachIndexed { index, result ->
            assertEquals(OnboardingScreenInstances.page(index + 1), result.reservation.screenInstanceId)
            assertEquals(config, result.reservation.sourceConfigKey)
        }
    }

    @Test
    fun releaseAndShowConsume_followStateMachine() {
        val env = StorageEnv()
        val config = ConfigKey("native_language_config_1")
        val screen = ScreenInstanceId("screen-1")
        val handle = TrackingHandle(AdFormat.NATIVE, "n1")
        env.storage.putReady(
            env.nativeAd(objectId = "o1", configKey = config, screen = screen, handle = handle),
        )

        val reserved = env.storage.reserveNormal(config, screen) as ReserveResult.Accepted
        assertTrue(env.storage.release(reserved.reservation.reservationId))
        assertEquals(AdSlotState.READY, env.storage.get(ObjectId("o1"))?.state)
        assertNotNull(env.storage.peekReady(config, screen))

        val reservedAgain = env.storage.reserveNormal(config, screen) as ReserveResult.Accepted
        assertTrue(env.storage.markShowing(reservedAgain.reservation.reservationId))
        assertEquals(AdSlotState.SHOWING, env.storage.get(ObjectId("o1"))?.state)
        assertTrue(env.storage.consume(reservedAgain.reservation.reservationId))
        assertEquals(AdSlotState.CONSUMED, env.storage.get(ObjectId("o1"))?.state)
        assertTrue(handle.destroyed)
        assertFalse(env.storage.consume(ObjectId("o1")))
    }

    @Test
    fun inspector_isReadOnlySnapshot() {
        val env = StorageEnv()
        val config = ConfigKey("native_language_config_1")
        val screen = ScreenInstanceId("screen-1")
        env.storage.putReady(env.nativeAd(objectId = "o1", configKey = config, screen = screen))

        val snap1 = env.storage.inspector()
        assertEquals(1, snap1.objects.size)
        assertEquals(AdSlotState.READY, snap1.objects.single().state)

        env.storage.reserveNormal(config, screen)
        val snap2 = env.storage.inspector()
        assertEquals(1, snap2.reservations.size)
        assertEquals(0, snap2.readySlots.size)
        // Prior snapshot unchanged
        assertEquals(1, snap1.readySlots.size)
        assertEquals(0, snap1.reservations.size)
    }

    @Test
    fun storageTypes_doNotReferenceAndroidUi() {
        val storageFqcn = AdStorage::class.java.name
        val reservationFqcn = Reservation::class.java.name
        val viewFqcn = StoredAdView::class.java.name
        val inspectorFqcn = StorageInspectorSnapshot::class.java.name
        listOf(storageFqcn, reservationFqcn, viewFqcn, inspectorFqcn).forEach { name ->
            assertFalse(name.contains("android.app", ignoreCase = true))
            assertFalse(name.contains("android.view", ignoreCase = true))
        }
        val fields = AdStorage::class.java.declaredFields.map { it.type.name }
        fields.forEach { typeName ->
            assertFalse(typeName.startsWith("android.app."))
            assertFalse(typeName.startsWith("android.view."))
        }
    }

    @Test
    fun onboardingScreenInstances_areFourDistinctValues() {
        assertEquals(4, OnboardingScreenInstances.all.toSet().size)
        assertEquals(ScreenInstanceId("onboarding-page-1"), OnboardingScreenInstances.page1)
        assertEquals(ScreenInstanceId("onboarding-page-4"), OnboardingScreenInstances.page(4))
        assertNotEquals(OnboardingScreenInstances.page2, OnboardingScreenInstances.page3)
    }

    private class StorageEnv(
        ttlMillis: Long? = null,
    ) {
        val clock = FakeClock()
        val removed = CopyOnWriteArrayList<AdStorage.RemovedObjectInfo>()
        private val idSeq = AtomicInteger(0)
        val storage = AdStorage(
            clock = clock,
            idGenerator = IdGenerator { "reservation-${idSeq.incrementAndGet()}" },
            ttlMillis = ttlMillis,
            onObjectRemoved = { removed += it },
        )

        fun nativeAd(
            objectId: String,
            screen: ScreenInstanceId?,
            configKey: ConfigKey = ConfigKey("native_onboarding_config_1"),
            weight: Int = 100,
            adunit: String = "native-$objectId",
            handle: TrackingHandle = TrackingHandle(AdFormat.NATIVE, adunit),
            state: AdSlotState = AdSlotState.READY,
            loadedAt: Long = clock.nowMillis(),
        ): StoredAd = storedAd(
            objectId = objectId,
            configKey = configKey,
            screen = screen,
            format = AdFormat.NATIVE,
            weight = weight,
            adunit = adunit,
            handle = handle,
            state = state,
            loadedAt = loadedAt,
        )

        fun storedAd(
            objectId: String,
            configKey: ConfigKey,
            screen: ScreenInstanceId?,
            format: AdFormat,
            weight: Int = 100,
            adunit: String = "$format-$objectId",
            handle: SdkLoadedAdHandle = TrackingHandle(format, adunit),
            state: AdSlotState = AdSlotState.READY,
            loadedAt: Long = clock.nowMillis(),
            listIndex: Int = 0,
        ): StoredAd = StoredAd(
            objectId = ObjectId(objectId),
            sourceConfigKey = configKey,
            sourceListIndex = listIndex,
            sourceType = format,
            sourceAdunit = adunit,
            sourceWeight = weight,
            screenInstanceId = screen,
            loadedAt = loadedAt,
            state = state,
            sdkHandle = handle,
        )
    }

    private class FakeClock(start: Long = 0L) : Clock {
        private val now = AtomicLong(start)
        override fun nowMillis(): Long = now.get()
        fun advance(delta: Long) {
            now.addAndGet(delta)
        }
    }

    private class TrackingHandle(
        override val format: AdFormat,
        override val adUnit: String,
    ) : SdkLoadedAdHandle {
        private val destroyedState = AtomicBoolean(false)
        val destroyed: Boolean get() = destroyedState.get()
        var onDestroy: (() -> Unit)? = null

        override fun destroy() {
            if (destroyedState.compareAndSet(false, true)) {
                onDestroy?.invoke()
            }
        }
    }
}
