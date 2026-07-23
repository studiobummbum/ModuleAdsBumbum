package com.example.adsmodule.core.load

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.IdGenerator
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.AdsConfigValue
import com.example.adsmodule.core.config.ConfigValueOrigin
import com.example.adsmodule.core.config.ResolvedConfig
import com.example.adsmodule.fake.FakeAdItemKey
import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.FakeScenario
import com.example.adsmodule.fake.FakeScenarioConfig
import com.example.adsmodule.fake.FakeSdkEvent
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdSdkAdapter
import com.example.adsmodule.sdk.AdSdkAdapterRegistry
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeightedListLoaderTest {
    @Test
    fun weightOrder_loadsHighestWeightFirst() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_language_config_1")
        env.fail(key, index = 0, adUnit = "low")
        env.success(key, index = 1, adUnit = "high")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(enableAd = true, weight = 10, adunit = "low", index = 0),
                        item(enableAd = true, weight = 90, adunit = "high", index = 1),
                    ),
                ),
            ),
        )

        val success = result as WeightedLoadResult.Success
        assertEquals("high", success.storedAd.sourceAdunit)
        assertEquals(90, success.storedAd.sourceWeight)
        assertEquals(1, success.storedAd.sourceListIndex)
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(key.value, 1, "high")))
        assertEquals(0, env.controller.requestCount(FakeAdItemKey(key.value, 0, "low")))
    }

    @Test
    fun tieBreak_usesOriginalIndexAscending() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_language_config_1")
        env.success(key, index = 0, adUnit = "first")
        env.success(key, index = 2, adUnit = "second")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(enableAd = true, weight = 50, adunit = "first", index = 0),
                        item(enableAd = false, weight = 50, adunit = "disabled", index = 1),
                        item(enableAd = true, weight = 50, adunit = "second", index = 2),
                    ),
                ),
            ),
        )

        val success = result as WeightedLoadResult.Success
        assertEquals(0, success.storedAd.sourceListIndex)
        assertEquals("first", success.storedAd.sourceAdunit)
        assertEquals(0, env.controller.requestCount(FakeAdItemKey(key.value, 2, "second")))
    }

    @Test
    fun disabledItems_areSkipped() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("banner_home_config_1")
        env.success(key, index = 1, adUnit = "enabled")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(enableAd = false, weight = 100, adunit = "disabled", index = 0),
                        item(enableAd = true, weight = 1, adunit = "enabled", index = 1),
                    ),
                ),
            ),
        )

        assertTrue(result is WeightedLoadResult.Success)
        assertEquals(0, env.controller.requestCount(FakeAdItemKey(key.value, 0, "disabled")))
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(key.value, 1, "enabled")))
    }

    @Test
    fun firstSuccess_stopsFallback() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_splash_config_1")
        env.success(key, index = 0, adUnit = "a")
        env.success(key, index = 1, adUnit = "b")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(enableAd = true, weight = 20, adunit = "a", index = 0),
                        item(enableAd = true, weight = 10, adunit = "b", index = 1),
                    ),
                ),
            ),
        )

        assertTrue(result is WeightedLoadResult.Success)
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(key.value, 0, "a")))
        assertEquals(0, env.controller.requestCount(FakeAdItemKey(key.value, 1, "b")))
    }

    @Test
    fun failure_fallsBackToNextItem() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_language_dup_config_1")
        env.fail(key, index = 0, adUnit = "fail-unit")
        env.success(key, index = 1, adUnit = "ok-unit")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(enableAd = true, weight = 100, adunit = "fail-unit", index = 0),
                        item(enableAd = true, weight = 50, adunit = "ok-unit", index = 1),
                    ),
                ),
            ),
        )

        val success = result as WeightedLoadResult.Success
        assertEquals("ok-unit", success.storedAd.sourceAdunit)
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(key.value, 0, "fail-unit")))
        assertEquals(1, env.controller.requestCount(FakeAdItemKey(key.value, 1, "ok-unit")))
    }

    @Test
    fun allFail_returnsExhausted() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_onboarding_config_1")
        env.fail(key, index = 0, adUnit = "a")
        env.fail(key, index = 1, adUnit = "b")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(enableAd = true, weight = 2, adunit = "a", index = 0),
                        item(enableAd = true, weight = 1, adunit = "b", index = 1),
                    ),
                ),
            ),
        )

        val exhausted = result as WeightedLoadResult.Exhausted
        assertEquals(2, exhausted.attempts.size)
        assertTrue(exhausted.attempts.all { it.outcome == WeightedItemAttemptOutcome.FAILURE })
    }

    @Test
    fun mixedTypes_routeThroughCorrectAdapters() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("inter_splash_config_1")
        env.fail(key, index = 0, adUnit = "inter-unit")
        env.fail(key, index = 1, adUnit = "appopen-unit")
        env.success(key, index = 2, adUnit = "native-full-unit")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(
                            enableAd = true,
                            weight = 30,
                            adunit = "inter-unit",
                            index = 0,
                            type = "inter",
                        ),
                        item(
                            enableAd = true,
                            weight = 20,
                            adunit = "appopen-unit",
                            index = 1,
                            type = "appopen",
                        ),
                        item(
                            enableAd = true,
                            weight = 10,
                            adunit = "native-full-unit",
                            index = 2,
                            type = "native",
                        ),
                    ),
                ),
            ),
        )

        val success = result as WeightedLoadResult.Success
        assertEquals(AdFormat.NATIVE_FULLSCREEN, success.storedAd.sourceType)
        assertEquals(AdFormat.NATIVE_FULLSCREEN, success.context.format)
        assertEquals(
            listOf(AdFormat.INTERSTITIAL, AdFormat.APP_OPEN, AdFormat.NATIVE_FULLSCREEN),
            env.loader.debugStates.value.getValue(success.cycleId).attempts.map { it.format },
        )
    }

    @Test
    fun mixedTypes_interAndAppOpenFail_nativeSuccessStoresWeight80() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("inter_splash_config_1")
        env.fail(key, index = 0, adUnit = "inter-unit")
        env.fail(key, index = 1, adUnit = "appopen-unit")
        env.success(key, index = 2, adUnit = "native-unit")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    items = listOf(
                        item(
                            enableAd = true,
                            weight = 100,
                            adunit = "inter-unit",
                            index = 0,
                            type = "inter",
                        ),
                        item(
                            enableAd = true,
                            weight = 90,
                            adunit = "appopen-unit",
                            index = 1,
                            type = "appopen",
                        ),
                        item(
                            enableAd = true,
                            weight = 80,
                            adunit = "native-unit",
                            index = 2,
                            type = "native",
                        ),
                    ),
                ),
            ),
        )

        val success = result as WeightedLoadResult.Success
        assertEquals(AdFormat.NATIVE_FULLSCREEN, success.storedAd.sourceType)
        assertEquals(80, success.storedAd.sourceWeight)
        assertEquals("native-unit", success.storedAd.sourceAdunit)
        assertEquals(2, success.storedAd.sourceListIndex)
        assertEquals(key, success.storedAd.sourceConfigKey)
    }

    @Test
    fun typelessConfigs_useFixedDefaultFormats() = runTest {
        val cases = listOf(
            ConfigKey("appopen_resume_config_1") to AdFormat.APP_OPEN,
            ConfigKey("banner_ufo_config_1") to AdFormat.BANNER,
            ConfigKey("native_splash_full_config_1") to AdFormat.NATIVE_FULLSCREEN,
            ConfigKey("native_onb_full_2_config_1") to AdFormat.NATIVE_FULLSCREEN,
        )
        cases.forEach { (key, format) ->
            val env = LoaderTestEnvironment(this)
            env.success(key, index = 0, adUnit = "unit-$format")
            val result = env.loader.load(
                env.request(
                    configKey = key,
                    config = adsConfig(
                        items = listOf(
                            item(
                                enableAd = true,
                                weight = 1,
                                adunit = "unit-$format",
                                index = 0,
                                type = null,
                            ),
                        ),
                    ),
                ),
            )
            val success = result as WeightedLoadResult.Success
            assertEquals(format, success.storedAd.sourceType)
        }
    }

    @Test
    fun disabledConfig_doesNotRequestSdk() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_language_loading_config_1")
        val result = env.loader.load(
            env.request(
                configKey = key,
                config = adsConfig(
                    enable = false,
                    items = listOf(item(enableAd = true, weight = 1, adunit = "x", index = 0)),
                ),
            ),
        )

        assertTrue(result is WeightedLoadResult.Disabled)
        assertTrue(env.controller.eventsSnapshot().none { it is FakeSdkEvent.LoadRequested })
    }

    @Test
    fun sameConfig_serializesInFlightRequests() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("inter_onboarding_config_1")
        env.controller.setScenario(
            FakeAdItemKey(key.value, 0, "slow"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 1_000L,
            ),
        )
        env.success(key, index = 0, adUnit = "fast")

        val first = async {
            env.loader.load(
                env.request(
                    cycleId = LoadCycleId("cycle-slow"),
                    configKey = key,
                    config = adsConfig(
                        items = listOf(
                            item(enableAd = true, weight = 1, adunit = "slow", index = 0, type = "inter"),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        val second = async {
            env.loader.load(
                env.request(
                    cycleId = LoadCycleId("cycle-fast"),
                    configKey = key,
                    config = adsConfig(
                        items = listOf(
                            item(enableAd = true, weight = 1, adunit = "fast", index = 0, type = "inter"),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        assertFalse(second.isCompleted)

        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(first.await() is WeightedLoadResult.Success)
        assertTrue(second.await() is WeightedLoadResult.Success)
        val requested = env.controller.eventsSnapshot().filterIsInstance<FakeSdkEvent.LoadRequested>()
        assertEquals(2, requested.size)
        assertEquals("slow", requested[0].itemKey.adUnit)
        assertEquals("fast", requested[1].itemKey.adUnit)
    }

    @Test
    fun differentConfigs_canLoadConcurrently() = runTest {
        val env = LoaderTestEnvironment(this)
        val firstKey = ConfigKey("native_language_config_1")
        val secondKey = ConfigKey("banner_home_config_1")
        env.controller.setScenario(
            FakeAdItemKey(firstKey.value, 0, "native-slow"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 500L,
            ),
        )
        env.controller.setScenario(
            FakeAdItemKey(secondKey.value, 0, "banner-slow"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 500L,
            ),
        )

        val first = async {
            env.loader.load(
                env.request(
                    cycleId = LoadCycleId("cycle-native"),
                    configKey = firstKey,
                    config = adsConfig(
                        items = listOf(
                            item(enableAd = true, weight = 1, adunit = "native-slow", index = 0),
                        ),
                    ),
                ),
            )
        }
        val second = async {
            env.loader.load(
                env.request(
                    cycleId = LoadCycleId("cycle-banner"),
                    configKey = secondKey,
                    config = adsConfig(
                        items = listOf(
                            item(enableAd = true, weight = 1, adunit = "banner-slow", index = 0),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        assertEquals(
            2,
            env.controller.eventsSnapshot().count { it is FakeSdkEvent.LoadRequested },
        )
        advanceTimeBy(500L)
        runCurrent()
        assertTrue(first.await() is WeightedLoadResult.Success)
        assertTrue(second.await() is WeightedLoadResult.Success)
    }

    @Test
    fun deactivate_cancelsInFlightAndSkipsFallback() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("inter_splash_config_1")
        env.controller.setScenario(
            FakeAdItemKey(key.value, 0, "never"),
            FakeScenarioConfig(scenario = FakeScenario.NEVER_CALLBACK),
        )
        env.success(key, index = 1, adUnit = "fallback")
        val cycleId = LoadCycleId("cycle-cancel")
        val deferred = async {
            env.loader.load(
                env.request(
                    cycleId = cycleId,
                    configKey = key,
                    config = adsConfig(
                        items = listOf(
                            item(
                                enableAd = true,
                                weight = 20,
                                adunit = "never",
                                index = 0,
                                type = "inter",
                            ),
                            item(
                                enableAd = true,
                                weight = 10,
                                adunit = "fallback",
                                index = 1,
                                type = "inter",
                            ),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        assertTrue(env.loader.deactivate(cycleId))
        runCurrent()

        val result = deferred.await()
        assertTrue(result is WeightedLoadResult.Cancelled)
        assertEquals(0, env.controller.requestCount(FakeAdItemKey(key.value, 1, "fallback")))
    }

    @Test
    fun staleSuccess_afterDeactivate_isDestroyedAndIgnored() = runTest {
        val handle = TrackingHandle(AdFormat.NATIVE)
        val cycleId = LoadCycleId("cycle-stale")
        lateinit var loader: WeightedListLoader
        val adapter = object : AdSdkAdapter {
            override val supportedFormats: Set<AdFormat> = setOf(AdFormat.NATIVE)
            override suspend fun load(request: AdLoadRequest): AdLoadResult {
                loader.deactivate(cycleId)
                return AdLoadResult.Success(handle)
            }

            override fun show(request: AdShowRequest): Flow<AdShowEvent> = emptyFlow()
        }
        loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(listOf(adapter)),
            clock = Clock { testScheduler.currentTime },
            idGenerator = SequentialIdGenerator(),
        )
        val key = ConfigKey("native_language_config_1")
        val staleEvents = mutableListOf<WeightedLoadStaleEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            loader.staleEvents.collect { staleEvents += it }
        }

        val result = loader.load(
            WeightedLoadRequest(
                cycleId = cycleId,
                configKey = key,
                screenInstanceId = ScreenInstanceId("screen-1"),
                snapshot = snapshot(
                    key,
                    adsConfig(
                        items = listOf(
                            item(enableAd = true, weight = 1, adunit = "native-unit", index = 0),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result is WeightedLoadResult.Cancelled)
        assertEquals(1, handle.destroyCount.get())
        assertEquals(1, staleEvents.size)
        assertEquals("cycle deactivated", staleEvents.single().mismatch)
        assertEquals(
            WeightedItemAttemptOutcome.STALE,
            loader.debugStates.value.getValue(cycleId).attempts.single().outcome,
        )
        collector.cancel()
    }

    @Test
    fun snapshotRefresh_doesNotMutateActiveCycle() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_language_config_1")
        env.controller.setScenario(
            FakeAdItemKey(key.value, 0, "old-unit"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 200L,
            ),
        )
        val oldSnapshot = snapshot(
            key,
            adsConfig(
                items = listOf(item(enableAd = true, weight = 1, adunit = "old-unit", index = 0)),
            ),
            version = 1L,
        )
        val newSnapshot = snapshot(
            key,
            adsConfig(
                items = listOf(item(enableAd = true, weight = 1, adunit = "new-unit", index = 0)),
            ),
            version = 2L,
        )
        val oldDeferred = async {
            env.loader.load(
                WeightedLoadRequest(
                    cycleId = LoadCycleId("cycle-old"),
                    configKey = key,
                    snapshot = oldSnapshot,
                ),
            )
        }
        runCurrent()
        env.success(key, index = 0, adUnit = "new-unit")
        val newDeferred = async {
            env.loader.load(
                WeightedLoadRequest(
                    cycleId = LoadCycleId("cycle-new"),
                    configKey = key,
                    snapshot = newSnapshot,
                ),
            )
        }
        runCurrent()
        assertFalse(newDeferred.isCompleted)

        advanceTimeBy(200L)
        runCurrent()

        val oldResult = oldDeferred.await() as WeightedLoadResult.Success
        val newResult = newDeferred.await() as WeightedLoadResult.Success
        assertEquals(1L, oldResult.context.snapshotVersion)
        assertEquals("old-unit", oldResult.storedAd.sourceAdunit)
        assertEquals(oldSnapshot.contentHash, oldResult.context.snapshotContentHash)
        assertEquals(2L, newResult.context.snapshotVersion)
        assertEquals("new-unit", newResult.storedAd.sourceAdunit)
    }

    @Test
    fun itemTimeout_fallsBackToNextItem() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("inter_splash_config_1")
        env.controller.setScenario(
            FakeAdItemKey(key.value, 0, "slow"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 5_000L,
            ),
        )
        env.success(key, index = 1, adUnit = "next")
        val deferred = async {
            env.loader.load(
                env.request(
                    configKey = key,
                    config = adsConfig(
                        timeoutTotalMillis = null,
                        items = listOf(
                            item(
                                enableAd = true,
                                weight = 20,
                                adunit = "slow",
                                index = 0,
                                type = "inter",
                                timeoutMillis = 100L,
                            ),
                            item(
                                enableAd = true,
                                weight = 10,
                                adunit = "next",
                                index = 1,
                                type = "inter",
                            ),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        val success = deferred.await() as WeightedLoadResult.Success
        assertEquals("next", success.storedAd.sourceAdunit)
        assertEquals(
            WeightedItemAttemptOutcome.TIMEOUT,
            env.loader.debugStates.value.getValue(success.cycleId).attempts.first().outcome,
        )
    }

    @Test
    fun totalTimeout_terminatesCycle() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("inter_onboarding_config_1")
        env.controller.setScenario(
            FakeAdItemKey(key.value, 0, "slow"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 5_000L,
            ),
        )
        env.success(key, index = 1, adUnit = "unused")
        val deferred = async {
            env.loader.load(
                env.request(
                    configKey = key,
                    config = adsConfig(
                        timeoutTotalMillis = 100L,
                        items = listOf(
                            item(
                                enableAd = true,
                                weight = 20,
                                adunit = "slow",
                                index = 0,
                                type = "inter",
                                timeoutMillis = 1_000L,
                            ),
                            item(
                                enableAd = true,
                                weight = 10,
                                adunit = "unused",
                                index = 1,
                                type = "inter",
                            ),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        val result = deferred.await()
        assertTrue(result is WeightedLoadResult.TotalTimeout)
        assertEquals(0, env.controller.requestCount(FakeAdItemKey(key.value, 1, "unused")))
    }

    @Test
    fun absentTimeouts_doNotInventDeadlines() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_language_config_1")
        env.controller.setScenario(
            FakeAdItemKey(key.value, 0, "slow"),
            FakeScenarioConfig(
                scenario = FakeScenario.DELAYED_SUCCESS,
                loadDelayMillis = 2_000L,
            ),
        )
        val deferred = async {
            env.loader.load(
                env.request(
                    configKey = key,
                    config = adsConfig(
                        timeoutTotalMillis = null,
                        items = listOf(
                            item(
                                enableAd = true,
                                weight = 1,
                                adunit = "slow",
                                index = 0,
                                timeoutMillis = null,
                            ),
                        ),
                    ),
                ),
            )
        }
        runCurrent()
        advanceTimeBy(1_999L)
        runCurrent()
        assertFalse(deferred.isCompleted)
        advanceTimeBy(1L)
        runCurrent()
        assertTrue(deferred.await() is WeightedLoadResult.Success)
    }

    @Test
    fun adapterTimeout_fallsBack() = runTest {
        val timeoutAdapter = object : AdSdkAdapter {
            override val supportedFormats: Set<AdFormat> = setOf(AdFormat.NATIVE)
            private var calls = 0
            override suspend fun load(request: AdLoadRequest): AdLoadResult {
                calls += 1
                return if (calls == 1) {
                    AdLoadResult.Timeout
                } else {
                    AdLoadResult.Success(TrackingHandle(AdFormat.NATIVE))
                }
            }

            override fun show(request: AdShowRequest): Flow<AdShowEvent> = emptyFlow()
        }
        val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(listOf(timeoutAdapter)),
            clock = Clock { testScheduler.currentTime },
            idGenerator = SequentialIdGenerator(),
        )
        val key = ConfigKey("native_language_config_1")
        val result = loader.load(
            WeightedLoadRequest(
                cycleId = LoadCycleId("cycle-adapter-timeout"),
                configKey = key,
                snapshot = snapshot(
                    key,
                    adsConfig(
                        items = listOf(
                            item(enableAd = true, weight = 2, adunit = "a", index = 0),
                            item(enableAd = true, weight = 1, adunit = "b", index = 1),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result is WeightedLoadResult.Success)
        assertEquals(
            WeightedItemAttemptOutcome.TIMEOUT,
            loader.debugStates.value.getValue(result.cycleId).attempts.first().outcome,
        )
    }

    @Test
    fun missingAdapter_fallsBack() = runTest {
        val onlyInter = object : AdSdkAdapter {
            override val supportedFormats: Set<AdFormat> = setOf(AdFormat.INTERSTITIAL)
            override suspend fun load(request: AdLoadRequest): AdLoadResult =
                AdLoadResult.Success(TrackingHandle(AdFormat.INTERSTITIAL))

            override fun show(request: AdShowRequest): Flow<AdShowEvent> = emptyFlow()
        }
        val loader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(listOf(onlyInter)),
            clock = Clock { testScheduler.currentTime },
            idGenerator = SequentialIdGenerator(),
        )
        val key = ConfigKey("inter_splash_config_1")
        val result = loader.load(
            WeightedLoadRequest(
                cycleId = LoadCycleId("cycle-missing-adapter"),
                configKey = key,
                snapshot = snapshot(
                    key,
                    adsConfig(
                        items = listOf(
                            item(
                                enableAd = true,
                                weight = 20,
                                adunit = "native-unit",
                                index = 0,
                                type = "native",
                            ),
                            item(
                                enableAd = true,
                                weight = 10,
                                adunit = "inter-unit",
                                index = 1,
                                type = "inter",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val success = result as WeightedLoadResult.Success
        assertEquals(AdFormat.INTERSTITIAL, success.storedAd.sourceType)
        assertEquals(
            WeightedItemAttemptOutcome.MISSING_ADAPTER,
            loader.debugStates.value.getValue(success.cycleId).attempts.first().outcome,
        )
    }

    @Test
    fun success_preservesSourceMetadataAndDebugState() = runTest {
        val env = LoaderTestEnvironment(this)
        val key = ConfigKey("native_onboarding_config_1")
        val screen = ScreenInstanceId("ONBOARD_NATIVE#2")
        env.success(key, index = 1, adUnit = "page-native")
        val request = env.request(
            configKey = key,
            screenInstanceId = screen,
            config = adsConfig(
                items = listOf(
                    item(enableAd = false, weight = 100, adunit = "skip", index = 0),
                    item(enableAd = true, weight = 55, adunit = "page-native", index = 1),
                ),
            ),
        )
        val result = env.loader.load(request) as WeightedLoadResult.Success

        assertEquals(key, result.storedAd.sourceConfigKey)
        assertEquals(1, result.storedAd.sourceListIndex)
        assertEquals(AdFormat.NATIVE, result.storedAd.sourceType)
        assertEquals("page-native", result.storedAd.sourceAdunit)
        assertEquals(55, result.storedAd.sourceWeight)
        assertEquals(screen, result.storedAd.screenInstanceId)
        assertEquals(AdSlotState.READY, result.storedAd.state)
        val debug = env.loader.debugStates.value.getValue(result.cycleId)
        assertEquals(listOf(1), debug.orderedItems.map { it.originalIndex })
        assertEquals(WeightedLoadTerminalReason.SUCCESS, debug.terminalReason)
        assertFalse(debug.isActive)
        assertEquals(request.snapshot.version, debug.snapshotVersion)
    }

    private class LoaderTestEnvironment(
        scope: TestScope,
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val controller: FakeAdsSdkController = FakeAdsSdkController(
            clock = FakeClock { scope.testScheduler.currentTime },
            dispatcher = dispatcher,
            objectIdGenerator = SequentialFakeObjectIdGenerator(prefix = "fake-object"),
        )
        private val sdk = FakeAdsSdkModule.create(controller)
        val loader: WeightedListLoader = WeightedListLoader(
            adapterRegistry = AdSdkAdapterRegistry.create(sdk.adapters),
            clock = Clock { scope.testScheduler.currentTime },
            idGenerator = SequentialIdGenerator(),
        )

        fun success(key: ConfigKey, index: Int, adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(key.value, index, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.SUCCESS),
            )
        }

        fun fail(key: ConfigKey, index: Int, adUnit: String) {
            controller.setScenario(
                FakeAdItemKey(key.value, index, adUnit),
                FakeScenarioConfig(scenario = FakeScenario.FAIL),
            )
        }

        fun request(
            configKey: ConfigKey,
            config: OriginalAdsConfig,
            cycleId: LoadCycleId = LoadCycleId("cycle-${cycleSeq.incrementAndGet()}"),
            screenInstanceId: ScreenInstanceId? = null,
            snapshotVersion: Long = 1L,
        ): WeightedLoadRequest = WeightedLoadRequest(
            cycleId = cycleId,
            configKey = configKey,
            screenInstanceId = screenInstanceId,
            snapshot = snapshot(configKey, config, snapshotVersion),
        )

        private val cycleSeq = AtomicInteger(0)
    }

    private class SequentialIdGenerator(
        private val prefix: String = "id",
    ) : IdGenerator {
        private val next = AtomicLong(0L)
        override fun nextId(): String = "$prefix-${next.incrementAndGet()}"
    }

    private class TrackingHandle(
        override val format: AdFormat,
        override val adUnit: String = "tracked-unit",
    ) : SdkLoadedAdHandle {
        val destroyCount = AtomicInteger(0)
        override fun destroy() {
            destroyCount.incrementAndGet()
        }
    }

    private companion object {
        fun item(
            enableAd: Boolean,
            weight: Int,
            adunit: String,
            index: Int,
            type: String? = null,
            timeoutMillis: Long? = null,
        ): OriginalAdItem = OriginalAdItem(
            enableAd = enableAd,
            weight = weight,
            timeoutMillis = timeoutMillis,
            type = type,
            adunit = adunit,
            sourceListIndex = index,
        )

        fun adsConfig(
            items: List<OriginalAdItem>,
            enable: Boolean = true,
            timeoutTotalMillis: Long? = null,
        ): OriginalAdsConfig = OriginalAdsConfig(
            enable = enable,
            timeoutTotalMillis = timeoutTotalMillis,
            listAds = items,
        )

        fun snapshot(
            key: ConfigKey,
            config: OriginalAdsConfig,
            version: Long = 1L,
        ): AdsConfigSnapshot = AdsConfigSnapshot.create(
            version = version,
            configs = mapOf(
                key to ResolvedConfig(
                    value = AdsConfigValue(config),
                    canonicalJson = """{"enable":${config.enable},"list_ads":[]}""",
                    origin = ConfigValueOrigin.BUNDLED,
                ),
            ),
        )
    }
}
