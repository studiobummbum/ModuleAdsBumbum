package com.example.adsdemo.sdk

import com.example.adsmodule.admob.ActivityPresentationHost
import com.example.adsmodule.sdk.AdPresentationHost
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the currently resumed Activity as an opaque presentation host.
 * Adapters never hold this; coordinators read it only at show time.
 */
class PresentationHostRegistry {
    private val current = AtomicReference<AdPresentationHost?>(null)

    fun attach(host: AdPresentationHost) {
        current.set(host)
    }

    fun detach(host: AdPresentationHost) {
        current.compareAndSet(host, null)
    }

    fun current(): AdPresentationHost? = current.get()

    fun attachActivity(activity: android.app.Activity) {
        attach(ActivityPresentationHost(activity))
    }

    fun detachActivity(activity: android.app.Activity) {
        val existing = current.get() as? ActivityPresentationHost ?: return
        if (existing.activity === activity) {
            detach(existing)
        }
    }
}
