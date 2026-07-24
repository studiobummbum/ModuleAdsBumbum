package com.example.adsmodule.admob

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CompletableDeferred

/**
 * One-shot Mobile Ads initialization using Application context only.
 * Adapters must [awaitInitialized] before load so requests do not race init.
 */
public object AdMobSdkInitializer {
    private const val TAG: String = "AdMobSdkInitializer"
    private val lock = Any()
    @Volatile
    private var deferred: CompletableDeferred<Unit>? = null

    /** Starts init if needed (non-blocking). Prefer [awaitInitialized] before load. */
    public fun ensureInitialized(appContext: Context) {
        startIfNeeded(appContext)
    }

    public suspend fun awaitInitialized(appContext: Context) {
        startIfNeeded(appContext).await()
    }

    private fun startIfNeeded(appContext: Context): CompletableDeferred<Unit> {
        synchronized(lock) {
            deferred?.let { return it }
            val created = CompletableDeferred<Unit>()
            deferred = created
            Log.i(TAG, "MobileAds.initialize starting")
            MobileAds.initialize(appContext.applicationContext) { status ->
                Log.i(
                    TAG,
                    "MobileAds.initialize complete adapterCount=${status.adapterStatusMap.size}",
                )
                created.complete(Unit)
            }
            return created
        }
    }

    /** Test-only reset. */
    public fun resetForTests() {
        synchronized(lock) {
            deferred = null
        }
    }
}
