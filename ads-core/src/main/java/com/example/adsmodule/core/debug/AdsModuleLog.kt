package com.example.adsmodule.core.debug

import android.util.Log
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ScreenInstanceId

/**
 * Unified Logcat tag for ads preload / ready / bind diagnostics.
 * Filter: `adb logcat -s ADS-Module`
 */
public object AdsModuleLog {
    public const val TAG: String = "ADS-Module"

    public fun i(message: String) {
        // Unit tests run without Android Log mocks — never crash ad flows.
        runCatching { Log.i(TAG, message) }
    }

    public fun w(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    public fun placement(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
    ): String = buildString {
        append("config=").append(configKey.value)
        append(" screen=")
        append(screenInstanceId?.value ?: "-")
    }

    public fun preloadStart(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
        alreadyInFlight: Boolean = false,
    ) {
        val verb = if (alreadyInFlight) "already-in-flight" else "start"
        i("PRELOAD $verb ${placement(configKey, screenInstanceId)}")
    }

    public fun readyOk(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
        objectId: String,
        elapsedMs: Long,
        cacheHit: Boolean = false,
    ) {
        val kind = if (cacheHit) "cache-hit" else "ok"
        i(
            "READY $kind ${placement(configKey, screenInstanceId)} " +
                "objectId=$objectId elapsedMs=$elapsedMs",
        )
    }

    public fun readyFail(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
        status: String,
        elapsedMs: Long,
        reason: String?,
    ) {
        w(
            "READY fail ${placement(configKey, screenInstanceId)} " +
                "status=$status elapsedMs=$elapsedMs reason=${reason ?: "-"}",
        )
    }

    public fun bind(
        configKey: ConfigKey,
        screenInstanceId: ScreenInstanceId?,
        obtained: Boolean,
        objectId: String? = null,
        reason: String? = null,
        replace: Boolean = false,
    ) {
        val verb = if (replace) "replace" else "obtained"
        if (obtained) {
            i(
                "BIND $verb=true ${placement(configKey, screenInstanceId)} " +
                    "objectId=${objectId ?: "-"}",
            )
        } else {
            w(
                "BIND $verb=false ${placement(configKey, screenInstanceId)} " +
                    "reason=${reason ?: "-"}",
            )
        }
    }
}
