package com.example.adsmodule.admob

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * GMA load/show APIs require the main thread (App Open hard-fails otherwise).
 */
internal object AdMobMainThread {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun <T> onMain(block: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) { block() }

    fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    suspend fun <T> awaitMain(block: () -> T): T =
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            suspendCancellableCoroutine { cont ->
                mainHandler.post {
                    try {
                        cont.resume(block())
                    } catch (t: Throwable) {
                        cont.cancel(t)
                    }
                }
            }
        }
}
