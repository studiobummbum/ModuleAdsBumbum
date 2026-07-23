package com.example.adsdemo.home

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.adsdemo.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeActivityTest {
    @Test
    fun launch_showsHomeChrome() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById(R.id.home_action_button))
                assertNotNull(activity.findViewById(R.id.banner_home_container))
                assertNotNull(activity.findViewById(R.id.home_status))
            }
        }
    }

    @Test
    fun recreation_doesNotCrash_andKeepsChrome() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            Thread.sleep(400)
            scenario.recreate()
            Thread.sleep(400)
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById(R.id.home_action_button))
                assertTrue(!activity.isFinishing)
            }
        }
    }
}
