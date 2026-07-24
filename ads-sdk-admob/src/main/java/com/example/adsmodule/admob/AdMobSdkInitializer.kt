package com.example.adsmodule.admob

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot Mobile Ads initialization using Application context only.
 */
public object AdMobSdkInitializer {
    private const val TAG: String = "AdMobSdkInitializer"
    private val initialized = AtomicBoolean(false)

    public fun ensureInitialized(appContext: Context) {
        if (!initialized.compareAndSet(false, true)) return
        Log.i(TAG, "MobileAds.initialize starting")
        MobileAds.initialize(appContext.applicationContext) { status ->
            Log.i(TAG, "MobileAds.initialize complete adapterCount=${status.adapterStatusMap.size}")
        }
    }

    /** Test-only reset. */
    public fun resetForTests() {
        initialized.set(false)
    }
}
