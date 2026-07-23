package com.example.adsmodule.core.load

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.LoadRequestId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.sdk.AdFormat

/**
 * Caller-owned cycle input. The loader captures [snapshot] immutably for this cycle.
 */
public data class WeightedLoadRequest(
    val cycleId: LoadCycleId,
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId? = null,
    val snapshot: AdsConfigSnapshot,
)

/**
 * Enabled `list_ads` item after index mapping, filtering, sorting and format resolution.
 */
public data class RuntimeAdItem(
    val originalIndex: Int,
    val enableAd: Boolean,
    val weight: Int,
    val timeoutMillis: Long?,
    val type: String?,
    val adunit: String,
    val resolvedFormat: AdFormat,
) {
    init {
        require(originalIndex >= 0) { "originalIndex must not be negative" }
        require(weight >= 0) { "weight must not be negative" }
        require(adunit.isNotBlank()) { "adunit must not be blank" }
        require(timeoutMillis == null || timeoutMillis >= 0L) {
            "timeoutMillis must not be negative"
        }
    }
}

/**
 * Full request context for one in-flight SDK attempt.
 */
public data class WeightedRequestContext(
    val cycleId: LoadCycleId,
    val requestId: LoadRequestId,
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
    val itemIndex: Int,
    val runtimeIndex: Int,
    val type: String?,
    val format: AdFormat,
    val adunit: String,
    val weight: Int,
    val snapshotVersion: Long,
    val snapshotContentHash: String,
    val startedAt: Long,
) {
    init {
        require(itemIndex >= 0) { "itemIndex must not be negative" }
        require(runtimeIndex >= 0) { "runtimeIndex must not be negative" }
        require(adunit.isNotBlank()) { "adunit must not be blank" }
        require(weight >= 0) { "weight must not be negative" }
        require(snapshotVersion > 0L) { "snapshotVersion must be positive" }
        require(snapshotContentHash.isNotBlank()) { "snapshotContentHash must not be blank" }
        require(startedAt >= 0L) { "startedAt must not be negative" }
    }
}

public enum class WeightedItemAttemptOutcome {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    MISSING_ADAPTER,
    STALE,
    CANCELLED,
}

public data class WeightedItemAttemptResult(
    val runtimeIndex: Int,
    val originalIndex: Int,
    val format: AdFormat,
    val adunit: String,
    val weight: Int,
    val outcome: WeightedItemAttemptOutcome,
    val reason: String? = null,
    val elapsedMillis: Long,
)

public enum class WeightedLoadTerminalReason {
    SUCCESS,
    DISABLED,
    EXHAUSTED,
    TOTAL_TIMEOUT,
    CANCELLED,
    MISSING_CONFIG,
}

public sealed interface WeightedLoadResult {
    public val cycleId: LoadCycleId
    public val configKey: ConfigKey
    public val reason: WeightedLoadTerminalReason

    public data class Success(
        override val cycleId: LoadCycleId,
        override val configKey: ConfigKey,
        val storedAd: StoredAd,
        val context: WeightedRequestContext,
    ) : WeightedLoadResult {
        override val reason: WeightedLoadTerminalReason = WeightedLoadTerminalReason.SUCCESS

        init {
            require(storedAd.state == AdSlotState.READY) {
                "Successful WeightedLoadResult must carry READY StoredAd"
            }
        }
    }

    public data class Disabled(
        override val cycleId: LoadCycleId,
        override val configKey: ConfigKey,
    ) : WeightedLoadResult {
        override val reason: WeightedLoadTerminalReason = WeightedLoadTerminalReason.DISABLED
    }

    public data class Exhausted(
        override val cycleId: LoadCycleId,
        override val configKey: ConfigKey,
        val attempts: List<WeightedItemAttemptResult>,
    ) : WeightedLoadResult {
        override val reason: WeightedLoadTerminalReason = WeightedLoadTerminalReason.EXHAUSTED
    }

    public data class TotalTimeout(
        override val cycleId: LoadCycleId,
        override val configKey: ConfigKey,
        val attempts: List<WeightedItemAttemptResult>,
    ) : WeightedLoadResult {
        override val reason: WeightedLoadTerminalReason = WeightedLoadTerminalReason.TOTAL_TIMEOUT
    }

    public data class Cancelled(
        override val cycleId: LoadCycleId,
        override val configKey: ConfigKey,
        val attempts: List<WeightedItemAttemptResult>,
    ) : WeightedLoadResult {
        override val reason: WeightedLoadTerminalReason = WeightedLoadTerminalReason.CANCELLED
    }

    public data class MissingConfig(
        override val cycleId: LoadCycleId,
        override val configKey: ConfigKey,
    ) : WeightedLoadResult {
        override val reason: WeightedLoadTerminalReason = WeightedLoadTerminalReason.MISSING_CONFIG
    }
}

public data class WeightedLoadDebugState(
    val cycleId: LoadCycleId,
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
    val snapshotVersion: Long,
    val snapshotContentHash: String,
    val cycleStartedAt: Long,
    val orderedItems: List<RuntimeAdItem>,
    val currentRuntimeIndex: Int?,
    val currentOriginalIndex: Int?,
    val activeRequest: WeightedRequestContext?,
    val elapsedMillis: Long,
    val attempts: List<WeightedItemAttemptResult>,
    val terminalReason: WeightedLoadTerminalReason?,
    val isActive: Boolean,
)

public data class WeightedLoadStaleEvent(
    val cycleId: LoadCycleId,
    val requestId: LoadRequestId,
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
    val snapshotVersion: Long,
    val mismatch: String,
    val occurredAt: Long,
)
