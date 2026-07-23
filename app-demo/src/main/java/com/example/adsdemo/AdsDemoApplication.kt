package com.example.adsdemo

import android.app.Application
import com.google.android.material.color.DynamicColors

class AdsDemoApplication : Application() {
    lateinit var graph: AdsDemoGraph
        private set

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        graph = AdsDemoGraph(this)
        graph.warmUp()
    }
}
