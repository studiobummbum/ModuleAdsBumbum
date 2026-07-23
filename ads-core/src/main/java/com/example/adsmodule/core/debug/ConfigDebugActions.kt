package com.example.adsmodule.core.debug

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.ConfigKeyRegistry
import com.example.adsmodule.core.config.ConfigRefreshResult
import com.example.adsmodule.core.config.ConfigValidationIssue
import com.example.adsmodule.core.config.InMemoryConfigDataSource
import com.example.adsmodule.core.config.OriginalRemoteConfigRepository

/**
 * Public config edit/refresh actions for RemoteConfigEditorFragment.
 */
public class ConfigDebugActions(
    private val currentDataSource: InMemoryConfigDataSource,
    private val repository: OriginalRemoteConfigRepository,
) {
    public fun knownKeys(): List<ConfigKey> =
        ConfigKeyRegistry.descriptors.map { it.key }

    public suspend fun readRaw(key: ConfigKey): String? = currentDataSource.read(key)

    public fun writeRaw(key: ConfigKey, rawJson: String) {
        currentDataSource.set(key, rawJson)
    }

    public fun removeOverride(key: ConfigKey) {
        currentDataSource.remove(key)
    }

    public fun rawSnapshot(): Map<ConfigKey, String> = currentDataSource.snapshot()

    public suspend fun refresh(): ConfigRefreshResult = repository.refresh()

    public fun currentSnapshot(): AdsConfigSnapshot? = repository.snapshots.value

    public fun lastFailureIssues(
        result: ConfigRefreshResult,
    ): Map<ConfigKey, List<ConfigValidationIssue>> =
        when (result) {
            is ConfigRefreshResult.Failure -> result.issuesByKey
            else -> emptyMap()
        }
}
