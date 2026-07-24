package com.example.adsdemo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.material.color.DynamicColors

class AdsDemoApplication : Application() {
    lateinit var graph: AdsDemoGraph
        private set

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        graph = AdsDemoGraph(this)
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    graph.presentationHosts.attachActivity(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    graph.presentationHosts.detachActivity(activity)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        graph.warmUp()
    }
}
