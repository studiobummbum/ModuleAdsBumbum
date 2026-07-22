package com.example.adsdemo

import android.app.Application
import com.google.android.material.color.DynamicColors

class AdsDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
