package com.example.adsmodule.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.adsmodule.core.debug.AdsDebugApiProvider
import com.example.adsmodule.debug.databinding.ActivityDebugDashboardBinding
import com.example.adsmodule.debug.ui.DebugMenuFragment

/**
 * Host Activity for Phase 14 Debug Control Center.
 * Prefer this name; [DebugDashboardActivity] remains as a binary-compatible alias.
 */
open class AdsDebugDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDebugDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AdsDebugApiProvider.get().reportNavigation(
            activityName = "AdsDebugDashboardActivity",
            screenLabel = "Debug",
        )

        binding.closeButton.setOnClickListener { finish() }
        binding.backOrMenuButton.setOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                openDestination(DebugDestination.DASHBOARD, addToBackStack = false)
            }
        }

        if (savedInstanceState == null) {
            openDestination(DebugDestination.DASHBOARD, addToBackStack = false)
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }
        updateTitle()
    }

    fun openDestination(destination: DebugDestination, addToBackStack: Boolean = true) {
        val fragment: Fragment = if (destination == DebugDestination.DASHBOARD) {
            DebugMenuFragment()
        } else {
            destination.factory()
        }
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.debug_fragment_container, fragment, destination.name)
        if (addToBackStack) {
            transaction.addToBackStack(destination.name)
        }
        transaction.commit()
        binding.toolbarTitle.text = destination.title
        AdsDebugApiProvider.get().reportNavigation(
            activityName = "AdsDebugDashboardActivity",
            fragmentName = destination.name,
            screenLabel = destination.title,
        )
    }

    private fun updateTitle() {
        val tag = supportFragmentManager.findFragmentById(R.id.debug_fragment_container)?.tag
        val destination = DebugDestination.entries.firstOrNull { it.name == tag }
        binding.toolbarTitle.text = destination?.title ?: getString(R.string.debug_dashboard_title)
        binding.backOrMenuButton.text = if (supportFragmentManager.backStackEntryCount > 0) {
            "Back"
        } else {
            getString(R.string.debug_menu)
        }
    }
}

/** Binary-compatible alias for bootstrap placeholder name. */
@Deprecated("Use AdsDebugDashboardActivity", ReplaceWith("AdsDebugDashboardActivity"))
class DebugDashboardActivity : AdsDebugDashboardActivity()
