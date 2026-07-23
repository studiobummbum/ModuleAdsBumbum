package com.example.adsmodule.core.onboarding.full

import com.example.adsmodule.core.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Auto-skip timer that starts only after Close X becomes visible/enabled.
 */
public class AutoSkipController(
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val lock = Any()
    private var job: Job? = null
    private var deadlineMillis: Long? = null
    private val fired = AtomicBoolean(false)
    private var onFire: (() -> Unit)? = null

    public fun start(
        autoSkipMillis: Long,
        onFire: () -> Unit,
    ) {
        require(autoSkipMillis >= 0L)
        synchronized(lock) {
            cancelLocked()
            this.onFire = onFire
            fired.set(false)
            val deadline = clock.nowMillis() + autoSkipMillis
            deadlineMillis = deadline
            if (autoSkipMillis == 0L) {
                if (fired.compareAndSet(false, true)) {
                    onFire()
                }
                return
            }
            job = scope.launch {
                val wait = (deadline - clock.nowMillis()).coerceAtLeast(0L)
                delay(wait)
                if (fired.compareAndSet(false, true)) {
                    onFire()
                }
            }
        }
    }

    /**
     * Reattach after Activity recreation using an absolute deadline.
     */
    public fun attach(
        deadlineMillis: Long,
        onFire: () -> Unit,
    ) {
        synchronized(lock) {
            cancelLocked()
            this.onFire = onFire
            this.deadlineMillis = deadlineMillis
            if (clock.nowMillis() >= deadlineMillis) {
                fired.set(true)
                onFire()
                return
            }
            fired.set(false)
            job = scope.launch {
                val wait = (deadlineMillis - clock.nowMillis()).coerceAtLeast(0L)
                delay(wait)
                if (fired.compareAndSet(false, true)) {
                    onFire()
                }
            }
        }
    }

    public fun remainingMillis(): Long? {
        val deadline = synchronized(lock) { deadlineMillis } ?: return null
        return (deadline - clock.nowMillis()).coerceAtLeast(0L)
    }

    public fun deadlineMillis(): Long? = synchronized(lock) { deadlineMillis }

    public fun isStarted(): Boolean = synchronized(lock) { deadlineMillis != null }

    public fun cancel() {
        synchronized(lock) {
            cancelLocked()
        }
    }

    private fun cancelLocked() {
        job?.cancel()
        job = null
        onFire = null
    }
}
