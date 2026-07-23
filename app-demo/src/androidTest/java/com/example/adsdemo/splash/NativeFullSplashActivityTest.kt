package com.example.adsdemo.splash

import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import com.example.adsdemo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeFullSplashActivityTest {
    @Test
    @LargeTest
    fun missingSession_finishesImmediately() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            NativeFullSplashActivity::class.java,
        )
        ActivityScenario.launch<NativeFullSplashActivity>(intent).use { scenario ->
            Thread.sleep(400)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    @MediumTest
    fun layout_closeButtonIsTopEndAndStartsInvisible() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themed = ContextThemeWrapper(appContext, R.style.Theme_ModuleAdsExample)
        val root = LayoutInflater.from(themed)
            .inflate(R.layout.activity_native_full_splash, null) as FrameLayout
        val close = root.findViewById<ImageButton>(R.id.native_full_close)
        assertNotNull(close)
        assertEquals(View.INVISIBLE, close.visibility)
        val lp = close.layoutParams as FrameLayout.LayoutParams
        assertTrue((lp.gravity and Gravity.TOP) == Gravity.TOP)
        assertTrue((lp.gravity and Gravity.END) == Gravity.END || (lp.gravity and Gravity.RIGHT) == Gravity.RIGHT)
        assertEquals(
            (48 * themed.resources.displayMetrics.density).toInt(),
            close.layoutParams.width,
        )
        assertEquals(
            (48 * themed.resources.displayMetrics.density).toInt(),
            close.layoutParams.height,
        )
    }
}
