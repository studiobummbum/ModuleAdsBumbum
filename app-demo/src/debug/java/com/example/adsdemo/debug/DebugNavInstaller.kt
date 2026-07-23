package com.example.adsdemo.debug

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.example.adsdemo.R
import com.example.adsmodule.core.debug.AdsDebugApiProvider
import com.example.adsmodule.debug.AdsDebugDashboardActivity

/**
 * Debug-only installer: shows debug buttons and reports Activity navigation.
 * Present only in the debug source set / debug APK.
 */
class DebugNavInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    wireDebugButton(activity)
                    AdsDebugApiProvider.getOrNull()?.reportNavigation(
                        activityName = activity::class.java.simpleName,
                        screenLabel = activity::class.java.simpleName,
                    )
                }

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        return true
    }

    private fun wireDebugButton(activity: Activity) {
        val button = activity.findViewById<View>(R.id.open_debug_dashboard_button) ?: return
        button.visibility = View.VISIBLE
        button.setOnClickListener {
            activity.startActivity(Intent(activity, AdsDebugDashboardActivity::class.java))
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
