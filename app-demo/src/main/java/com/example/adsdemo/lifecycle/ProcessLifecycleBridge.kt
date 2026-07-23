package com.example.adsdemo.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.adsmodule.core.resume.AppOpenResumeCoordinator

/**
 * Bridges Android process foreground/background into ads-core coordinators.
 */
class ProcessLifecycleBridge(
    private val appOpenResume: AppOpenResumeCoordinator,
) : DefaultLifecycleObserver {
    private var started = false

    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun stop() {
        if (!started) return
        started = false
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        appOpenResume.onProcessForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        appOpenResume.onProcessBackground()
    }
}
