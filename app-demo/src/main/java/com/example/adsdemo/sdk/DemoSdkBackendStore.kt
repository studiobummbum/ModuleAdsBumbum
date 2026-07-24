package com.example.adsdemo.sdk

import android.content.Context
import android.content.SharedPreferences

class DemoSdkBackendStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): DemoSdkBackend =
        DemoSdkBackend.fromStorage(
            prefs.getString(KEY_BACKEND, DemoSdkBackend.AdMobTest.name),
        )

    fun write(backend: DemoSdkBackend) {
        prefs.edit().putString(KEY_BACKEND, backend.name).apply()
    }

    companion object {
        const val PREFS_NAME: String = "ads_demo_sdk_backend"
        const val KEY_BACKEND: String = "sdk_backend"
    }
}
