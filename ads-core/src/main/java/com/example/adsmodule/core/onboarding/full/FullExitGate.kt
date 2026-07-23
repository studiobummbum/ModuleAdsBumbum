package com.example.adsmodule.core.onboarding.full

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * First-wins CAS gate for Full Activity exit sources.
 */
public class FullExitGate {
    private val exited = AtomicBoolean(false)
    private val winning = AtomicReference<FullExitSource?>(null)

    public fun tryExit(
        source: FullExitSource,
        action: (FullExitSource) -> Unit,
    ): Boolean {
        if (!exited.compareAndSet(false, true)) {
            return false
        }
        winning.set(source)
        action(source)
        return true
    }

    public fun hasExited(): Boolean = exited.get()

    public fun winningSource(): FullExitSource? = winning.get()

    public fun gateState(): FullGateState =
        when {
            !exited.get() -> FullGateState.OPEN
            winning.get() != null -> FullGateState.COMPLETED
            else -> FullGateState.EXITING
        }
}
