package com.example.adsmodule.core.splash

import com.example.adsmodule.core.Clock
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.fullscreen.HostedFullscreenCoordinator
import com.example.adsmodule.core.fullscreen.HostedFullscreenOutcome
import com.example.adsmodule.core.fullscreen.HostedFullscreenSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controls Native Full Splash X delay and auto-skip.
 *
 * auto_skip starts only after the X button becomes visible/enabled.
 */
public class NativeFullSplashController(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val hosted: HostedFullscreenCoordinator,
) {
    private val lock = Any()
    private var active: ActiveNativeFull? = null

    public fun start(
        sessionId: SplashSessionId,
        hostedSession: HostedFullscreenSession,
        timeDelayXButtonMillis: Long,
        autoSkipMillis: Long,
        onSnapshot: (SplashNativeFullControlSnapshot) -> Unit,
        onExit: (exitSource: String) -> Unit,
    ) {
        require(timeDelayXButtonMillis >= 0L)
        require(autoSkipMillis >= 0L)
        synchronized(lock) {
            active?.cancelJobs()
            val startedAt = clock.nowMillis()
            val closeEnabledAt = startedAt + timeDelayXButtonMillis
            val controller = ActiveNativeFull(
                sessionId = sessionId,
                hostedSession = hostedSession,
                closeEnabledAtMillis = closeEnabledAt,
                autoSkipMillis = autoSkipMillis,
                onSnapshot = onSnapshot,
                onExit = onExit,
            )
            active = controller
            publish(controller, closeVisible = false)
            controller.closeJob = scope.launch {
                val wait = (closeEnabledAt - clock.nowMillis()).coerceAtLeast(0L)
                delay(wait)
                synchronized(lock) {
                    if (active !== controller || controller.exited.get()) return@synchronized
                    publish(controller, closeVisible = true)
                    controller.autoSkipDeadlineMillis = clock.nowMillis() + autoSkipMillis
                    publish(controller, closeVisible = true)
                }
                controller.autoSkipJob = scope.launch {
                    delay(autoSkipMillis)
                    requestExit(controller, "AUTO_SKIP")
                }
            }
        }
    }

    public fun onCloseClicked(sessionId: SplashSessionId, showRequestId: ShowRequestId): Boolean {
        val controller = synchronized(lock) { active } ?: return false
        if (controller.sessionId != sessionId) return false
        if (controller.hostedSession.showRequestId != showRequestId) return false
        if (clock.nowMillis() < controller.closeEnabledAtMillis) return false
        return requestExit(controller, "CLOSE_X")
    }

    public fun finishHosted(
        sessionId: SplashSessionId,
        showRequestId: ShowRequestId,
        outcome: HostedFullscreenOutcome,
    ) {
        val session: HostedFullscreenSession
        synchronized(lock) {
            val controller = active
            if (controller != null) {
                if (controller.sessionId != sessionId) return
                if (controller.hostedSession.showRequestId != showRequestId) return
                session = controller.hostedSession
                controller.cancelJobs()
                if (active === controller) {
                    active = null
                }
            } else {
                return
            }
        }
        hosted.finish(session, outcome)
    }

    public fun cancel(sessionId: SplashSessionId) {
        val toFinish: HostedFullscreenSession
        synchronized(lock) {
            val controller = active ?: return
            if (controller.sessionId != sessionId) return
            controller.cancelJobs()
            toFinish = controller.hostedSession
            active = null
        }
        // Always release GlobalFullscreenLock so Onboarding Full can acquire later.
        hosted.finish(toFinish, HostedFullscreenOutcome.FAILED)
    }

    private fun requestExit(controller: ActiveNativeFull, exitSource: String): Boolean {
        if (!controller.exited.compareAndSet(false, true)) {
            return false
        }
        controller.cancelJobs()
        synchronized(lock) {
            if (active === controller) {
                active = null
            }
        }
        // Finish before onExit navigation. Clearing active first used to make finishHosted
        // a no-op, which restored/left the Inter lock and blocked Onboarding Full.
        hosted.finish(controller.hostedSession, HostedFullscreenOutcome.COMPLETED)
        controller.onSnapshot(
            SplashNativeFullControlSnapshot(
                showRequestId = controller.hostedSession.showRequestId,
                closeVisible = true,
                closeEnabledAtMillis = controller.closeEnabledAtMillis,
                autoSkipDeadlineMillis = controller.autoSkipDeadlineMillis,
                remainingCloseDelayMillis = 0L,
                remainingAutoSkipMillis = 0L,
                exitSource = exitSource,
            ),
        )
        controller.onExit(exitSource)
        return true
    }

    private fun publish(controller: ActiveNativeFull, closeVisible: Boolean) {
        val now = clock.nowMillis()
        val remainingClose = (controller.closeEnabledAtMillis - now).coerceAtLeast(0L)
        val remainingAuto = controller.autoSkipDeadlineMillis?.let { deadline ->
            (deadline - now).coerceAtLeast(0L)
        }
        controller.onSnapshot(
            SplashNativeFullControlSnapshot(
                showRequestId = controller.hostedSession.showRequestId,
                closeVisible = closeVisible,
                closeEnabledAtMillis = controller.closeEnabledAtMillis,
                autoSkipDeadlineMillis = controller.autoSkipDeadlineMillis,
                remainingCloseDelayMillis = remainingClose,
                remainingAutoSkipMillis = remainingAuto,
                exitSource = null,
            ),
        )
    }

    private class ActiveNativeFull(
        val sessionId: SplashSessionId,
        val hostedSession: HostedFullscreenSession,
        val closeEnabledAtMillis: Long,
        val autoSkipMillis: Long,
        val onSnapshot: (SplashNativeFullControlSnapshot) -> Unit,
        val onExit: (String) -> Unit,
        val exited: AtomicBoolean = AtomicBoolean(false),
        var closeJob: Job? = null,
        var autoSkipJob: Job? = null,
        var autoSkipDeadlineMillis: Long? = null,
    ) {
        fun cancelJobs() {
            closeJob?.cancel()
            autoSkipJob?.cancel()
            closeJob = null
            autoSkipJob = null
        }
    }
}
