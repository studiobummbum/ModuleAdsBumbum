package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalRemoteConfigRepositoryTest {
    private val splashKey = ConfigKey("inter_splash_config_1")

    @Test
    fun validCurrentValueIsSavedAndInvalidCurrentFallsBackPerKeyToLkg() = runBlocking {
        val currentValue = validSplash(weight = 500, adunit = "current-unit")
        val store = InMemoryLastKnownGoodConfigStore()
        val firstRepository = repository(
            current = InMemoryConfigDataSource(mapOf(splashKey to currentValue)),
            store = store,
        )

        val first = firstRepository.refresh() as ConfigRefreshResult.Updated
        assertEquals(ConfigValueOrigin.CURRENT, first.snapshot[splashKey]?.origin)
        val saved = requireNotNull(store.snapshot()[splashKey])

        val fallbackRepository = repository(
            current = InMemoryConfigDataSource(mapOf(splashKey to "{invalid")),
            store = store,
        )
        val fallback = fallbackRepository.refresh() as ConfigRefreshResult.Updated

        assertEquals(ConfigValueOrigin.LAST_KNOWN_GOOD, fallback.snapshot[splashKey]?.origin)
        assertEquals(500, fallback.snapshot.adsConfig(splashKey)?.listAds?.single()?.weight)
        assertEquals(saved, store.snapshot()[splashKey])
        assertEquals(
            ConfigValueOrigin.BUNDLED,
            fallback.snapshot[ConfigKey("native_splash_config_1")]?.origin,
        )
    }

    @Test
    fun invalidCurrentWithoutLkgFallsBackToBundledForOnlyThatKey() = runBlocking {
        val repository = repository(
            current = InMemoryConfigDataSource(mapOf(splashKey to "{invalid")),
        )

        val result = repository.refresh() as ConfigRefreshResult.Updated

        assertEquals(24, result.snapshot.configs.size)
        assertEquals(ConfigValueOrigin.BUNDLED, result.snapshot[splashKey]?.origin)
        assertEquals(listOf(100, 90), result.snapshot.adsConfig(splashKey)?.listAds?.map { it.weight })
    }

    @Test
    fun unchangedCanonicalContentKeepsVersionAndChangedContentCreatesNewSnapshot() = runBlocking {
        val current = InMemoryConfigDataSource()
        val repository = repository(current)

        val first = repository.refresh() as ConfigRefreshResult.Updated
        val unchanged = repository.refresh() as ConfigRefreshResult.Unchanged
        assertSame(first.snapshot, unchanged.snapshot)
        assertEquals(1L, unchanged.snapshot.version)

        current.set(splashKey, validSplash(weight = 250, adunit = "changed"))
        val changed = repository.refresh() as ConfigRefreshResult.Updated

        assertEquals(2L, changed.snapshot.version)
        assertEquals(250, changed.snapshot.adsConfig(splashKey)?.listAds?.single()?.weight)
        assertEquals(listOf(100, 90), first.snapshot.adsConfig(splashKey)?.listAds?.map { it.weight })
    }

    @Test
    fun failedResolutionPublishesNothingAndDoesNotPartiallyWriteLkg() = runBlocking {
        val badBooleanKey = ConfigKey("enable_ads_app")
        val store = InMemoryLastKnownGoodConfigStore()
        val current = InMemoryConfigDataSource(
            mapOf(splashKey to validSplash(weight = 400, adunit = "staged")),
        )
        val repository = OriginalRemoteConfigRepository(
            currentDataSource = current,
            bundledDataSource = bundledDataSource(
                overrides = mapOf(badBooleanKey to "\"not-a-boolean\""),
            ),
            lastKnownGoodStore = store,
        )

        val result = repository.refresh() as ConfigRefreshResult.Failure

        assertTrue(badBooleanKey in result.issuesByKey)
        assertNull(result.snapshot)
        assertNull(repository.snapshots.value)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun concurrentRefreshesNeverPublishPartialSnapshots() = runBlocking {
        val repository = repository(InMemoryConfigDataSource())

        val results = (1..8).map {
            async(Dispatchers.Default) { repository.refresh() }
        }.awaitAll()

        assertEquals(1, results.count { it is ConfigRefreshResult.Updated })
        assertTrue(results.all { it.snapshot?.configs?.size == 24 })
        assertEquals(24, repository.snapshots.value?.configs?.size)
        assertEquals(1L, repository.snapshots.value?.version)
    }

    private fun repository(
        current: InMemoryConfigDataSource,
        store: InMemoryLastKnownGoodConfigStore = InMemoryLastKnownGoodConfigStore(),
    ): OriginalRemoteConfigRepository = OriginalRemoteConfigRepository(
        currentDataSource = current,
        bundledDataSource = bundledDataSource(),
        lastKnownGoodStore = store,
    )

    private fun validSplash(
        weight: Int,
        adunit: String,
    ): String =
        """
        {
          "enable": true,
          "isOrganic": true,
          "timeout_total": 30000,
          "list_ads": [
            {
              "enable_ad": true,
              "weight": $weight,
              "timeout": 15000,
              "type": "inter",
              "adunit": "$adunit"
            }
          ]
        }
        """.trimIndent()
}
