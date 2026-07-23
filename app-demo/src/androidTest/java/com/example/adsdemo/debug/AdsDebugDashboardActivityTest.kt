package com.example.adsdemo.debug

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.adsmodule.debug.AdsDebugDashboardActivity
import com.example.adsmodule.debug.DebugDestination
import com.example.adsmodule.debug.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class AdsDebugDashboardActivityTest {
    @Test
    fun launchDashboard_opensMenuAndStorageInspector() {
        ActivityScenario.launch(AdsDebugDashboardActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById(R.id.debug_fragment_container))
                activity.openDestination(DebugDestination.STORAGE)
                activity.supportFragmentManager.executePendingTransactions()
                assertNotNull(
                    activity.supportFragmentManager.findFragmentByTag(DebugDestination.STORAGE.name),
                )
            }
        }
    }
}
