package com.example.adsmodule.core.home

/**
 * Impression-based interval gate for `inter_all_config_1`.
 *
 * [intervalMillis] is the gap since the last successful impression, not a load timeout.
 * Null / missing interval always allows show.
 */
public class InterIntervalGate {
    private val lock = Any()
    private var lastImpressionAtMillis: Long? = null

    public fun lastImpressionAtMillis(): Long? = synchronized(lock) { lastImpressionAtMillis }

    public fun markImpressed(atMillis: Long) {
        synchronized(lock) {
            lastImpressionAtMillis = atMillis
        }
    }

    public fun clear() {
        synchronized(lock) {
            lastImpressionAtMillis = null
        }
    }

    public fun canShow(
        nowMillis: Long,
        intervalMillis: Long?,
    ): InterIntervalDecision {
        synchronized(lock) {
            if (intervalMillis == null) {
                return InterIntervalDecision.Allowed(reason = "no interval configured")
            }
            require(intervalMillis >= 0L) { "intervalMillis must not be negative" }
            val last = lastImpressionAtMillis
                ?: return InterIntervalDecision.Allowed(reason = "no prior impression")
            val elapsed = nowMillis - last
            return if (elapsed >= intervalMillis) {
                InterIntervalDecision.Allowed(
                    reason = "elapsed=${elapsed}ms >= interval=${intervalMillis}ms",
                )
            } else {
                InterIntervalDecision.Blocked(
                    remainingMillis = intervalMillis - elapsed,
                    reason = "elapsed=${elapsed}ms < interval=${intervalMillis}ms",
                )
            }
        }
    }
}

public sealed class InterIntervalDecision {
    public data class Allowed(
        val reason: String,
    ) : InterIntervalDecision()

    public data class Blocked(
        val remainingMillis: Long,
        val reason: String,
    ) : InterIntervalDecision()
}
