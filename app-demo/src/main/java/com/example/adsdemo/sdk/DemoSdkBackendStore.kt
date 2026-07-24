package com.example.adsdemo.sdk

import android.content.Context
import android.content.SharedPreferences
import com.example.adsdemo.BuildConfig

class DemoSdkBackendStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): DemoSdkBackend {
        val raw = prefs.getString(KEY_BACKEND, null)
        val stored = raw?.let(DemoSdkBackend::fromStorage)
        return DemoSdkBackendPolicy.effective(
            debuggable = BuildConfig.DEBUG,
            stored = stored,
        )
    }

    fun write(backend: DemoSdkBackend) {
        val sanitized = DemoSdkBackendPolicy.sanitizeWrite(BuildConfig.DEBUG, backend) ?: return
        prefs.edit().putString(KEY_BACKEND, sanitized.name).apply()
    }

    companion object {
        const val PREFS_NAME: String = "ads_demo_sdk_backend"
        const val KEY_BACKEND: String = "sdk_backend"
    }
}
