package com.example.adsmodule.core.onboarding.full

import com.example.adsmodule.core.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Delays Close X until [enabledAtMillis]. Not a load timeout.
 */
public class CloseDelayController(
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val lock = Any()
    private var job: Job? = null
    private var enabledAtMillis: Long? = null
    private val fired = AtomicBoolean(false)
    private var onReady: (() -> Unit)? = null

    public fun start(
        delayMillis: Long,
        onReady: () -> Unit,
    ) {
        require(delayMillis >= 0L)
        synchronized(lock) {
            cancelLocked()
            this.onReady = onReady
            fired.set(false)
            val enabledAt = clock.nowMillis() + delayMillis
            enabledAtMillis = enabledAt
            if (delayMillis == 0L) {
                if (fired.compareAndSet(false, true)) {
                    onReady()
                }
                return
            }
            job = scope.launch {
                val wait = (enabledAt - clock.nowMillis()).coerceAtLeast(0L)
                delay(wait)
                if (fired.compareAndSet(false, true)) {
                    onReady()
                }
            }
        }
    }

    /**
     * Reattach after Activity recreation using an absolute deadline.
     */
    public fun attach(
        enabledAtMillis: Long,
        alreadyReady: Boolean,
        onReady: () -> Unit,
    ) {
        synchronized(lock) {
            cancelLocked()
            this.onReady = onReady
            this.enabledAtMillis = enabledAtMillis
            if (alreadyReady || clock.nowMillis() >= enabledAtMillis) {
                fired.set(true)
                onReady()
                return
            }
            fired.set(false)
            job = scope.launch {
                val wait = (enabledAtMillis - clock.nowMillis()).coerceAtLeast(0L)
                delay(wait)
                if (fired.compareAndSet(false, true)) {
                    onReady()
                }
            }
        }
    }

    public fun isReady(): Boolean {
        val enabledAt = synchronized(lock) { enabledAtMillis } ?: return false
        return clock.nowMillis() >= enabledAt || fired.get()
    }

    public fun remainingMillis(): Long {
        val enabledAt = synchronized(lock) { enabledAtMillis } ?: return 0L
        return (enabledAt - clock.nowMillis()).coerceAtLeast(0L)
    }

    public fun enabledAtMillis(): Long? = synchronized(lock) { enabledAtMillis }

    public fun cancel() {
        synchronized(lock) {
            cancelLocked()
        }
    }

    private fun cancelLocked() {
        job?.cancel()
        job = null
        onReady = null
    }
}
