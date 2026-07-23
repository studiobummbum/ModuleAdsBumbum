package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public sealed interface ConfigRefreshResult {
    public val snapshot: AdsConfigSnapshot?

    public data class Updated(
        override val snapshot: AdsConfigSnapshot,
    ) : ConfigRefreshResult

    public data class Unchanged(
        override val snapshot: AdsConfigSnapshot,
    ) : ConfigRefreshResult

    public data class Failure(
        val issuesByKey: Map<ConfigKey, List<ConfigValidationIssue>>,
        override val snapshot: AdsConfigSnapshot?,
    ) : ConfigRefreshResult
}

public class OriginalRemoteConfigRepository(
    private val currentDataSource: ConfigDataSource,
    private val bundledDataSource: BundledConfigDataSource,
    private val lastKnownGoodStore: LastKnownGoodConfigStore,
    private val parser: OriginalAdsConfigParser = OriginalAdsConfigParser(),
    private val registry: ConfigKeyRegistry = ConfigKeyRegistry,
) {
    private val refreshMutex: Mutex = Mutex()
    private val mutableSnapshots: MutableStateFlow<AdsConfigSnapshot?> = MutableStateFlow(null)

    public val snapshots: StateFlow<AdsConfigSnapshot?> = mutableSnapshots.asStateFlow()

    public suspend fun refresh(): ConfigRefreshResult = refreshMutex.withLock {
        val resolvedConfigs = LinkedHashMap<ConfigKey, ResolvedConfig>()
        val stagedLastKnownGood = LinkedHashMap<ConfigKey, String>()
        val failures = LinkedHashMap<ConfigKey, List<ConfigValidationIssue>>()

        registry.descriptors.forEach { descriptor ->
            when (val resolution = resolve(descriptor)) {
                is Resolution.Success -> {
                    resolvedConfigs[descriptor.key] = resolution.config
                    if (resolution.saveAsLastKnownGood) {
                        stagedLastKnownGood[descriptor.key] = resolution.config.canonicalJson
                    }
                }
                is Resolution.Failure -> failures[descriptor.key] = resolution.issues
            }
        }

        if (failures.isNotEmpty()) {
            return@withLock ConfigRefreshResult.Failure(
                issuesByKey = failures.toUnmodifiableIssueMap(),
                snapshot = mutableSnapshots.value,
            )
        }

        lastKnownGoodStore.writeAll(stagedLastKnownGood)

        val previous = mutableSnapshots.value
        val candidate = AdsConfigSnapshot.create(
            version = (previous?.version ?: 0L) + 1L,
            configs = resolvedConfigs,
        )
        if (previous?.contentHash == candidate.contentHash) {
            return@withLock ConfigRefreshResult.Unchanged(previous)
        }

        mutableSnapshots.value = candidate
        ConfigRefreshResult.Updated(candidate)
    }

    private suspend fun resolve(descriptor: ConfigDescriptor): Resolution {
        val rejectedIssues = mutableListOf<ConfigValidationIssue>()

        currentDataSource.read(descriptor.key)?.let { rawJson ->
            when (val parsed = parser.parse(descriptor, rawJson)) {
                is OriginalConfigParseResult.Success -> {
                    return Resolution.Success(
                        config = parsed.toResolvedConfig(ConfigValueOrigin.CURRENT),
                        saveAsLastKnownGood = true,
                    )
                }
                is OriginalConfigParseResult.Failure -> rejectedIssues += parsed.issues
            }
        }

        lastKnownGoodStore.read(descriptor.key)?.let { rawJson ->
            when (val parsed = parser.parse(descriptor, rawJson)) {
                is OriginalConfigParseResult.Success -> {
                    return Resolution.Success(
                        config = parsed.toResolvedConfig(ConfigValueOrigin.LAST_KNOWN_GOOD),
                        saveAsLastKnownGood = false,
                    )
                }
                is OriginalConfigParseResult.Failure -> rejectedIssues += parsed.issues
            }
        }

        bundledDataSource.read(descriptor.key)?.let { rawJson ->
            when (val parsed = parser.parse(descriptor, rawJson)) {
                is OriginalConfigParseResult.Success -> {
                    return Resolution.Success(
                        config = parsed.toResolvedConfig(ConfigValueOrigin.BUNDLED),
                        saveAsLastKnownGood = false,
                    )
                }
                is OriginalConfigParseResult.Failure -> rejectedIssues += parsed.issues
            }
        }

        if (rejectedIssues.isEmpty()) {
            rejectedIssues += ConfigValidationIssue(
                severity = ConfigIssueSeverity.ERROR,
                code = ConfigIssueCode.REQUIRED_FIELD,
                path = "$",
                message = "No current, last-known-good, or bundled value exists for ${descriptor.key.value}",
            )
        }
        return Resolution.Failure(rejectedIssues)
    }

    private fun OriginalConfigParseResult.Success.toResolvedConfig(
        origin: ConfigValueOrigin,
    ): ResolvedConfig = ResolvedConfig(
        value = value,
        canonicalJson = canonicalJson,
        origin = origin,
        warnings = issues.filter { it.severity == ConfigIssueSeverity.WARNING },
    )

    private sealed interface Resolution {
        data class Success(
            val config: ResolvedConfig,
            val saveAsLastKnownGood: Boolean,
        ) : Resolution

        data class Failure(
            val issues: List<ConfigValidationIssue>,
        ) : Resolution
    }
}

private fun Map<ConfigKey, List<ConfigValidationIssue>>.toUnmodifiableIssueMap():
    Map<ConfigKey, List<ConfigValidationIssue>> {
    val copied = entries.associateTo(LinkedHashMap()) { (key, issues) ->
        key to Collections.unmodifiableList(ArrayList(issues))
    }
    return Collections.unmodifiableMap(copied)
}
