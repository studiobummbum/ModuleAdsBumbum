package com.example.adsmodule.debug.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.adsmodule.debug.R
import com.google.android.material.snackbar.Snackbar

/**
 * Persists Fake vs AdMob Test selection. Requires process restart to apply.
 * Preference key is shared with app-demo [DemoSdkBackendStore].
 */
class SdkBackendSelectorFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val root = inflater.inflate(R.layout.fragment_sdk_backend_selector, container, false)
        val group = root.findViewById<RadioGroup>(R.id.sdk_backend_group)
        val fake = root.findViewById<RadioButton>(R.id.sdk_backend_fake)
        val admob = root.findViewById<RadioButton>(R.id.sdk_backend_admob_test)
        val hint = root.findViewById<TextView>(R.id.sdk_backend_hint)

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_BACKEND, DEFAULT_BACKEND) ?: DEFAULT_BACKEND
        if (current == BACKEND_ADMOB_TEST) {
            admob.isChecked = true
        } else {
            fake.isChecked = true
        }
        hint.text = getString(R.string.debug_sdk_backend_hint, current)

        group.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.sdk_backend_admob_test -> BACKEND_ADMOB_TEST
                else -> BACKEND_FAKE
            }
            prefs.edit().putString(KEY_BACKEND, selected).apply()
            hint.text = getString(R.string.debug_sdk_backend_hint, selected)
            Snackbar.make(
                root,
                R.string.debug_sdk_backend_restart_required,
                Snackbar.LENGTH_LONG,
            ).show()
        }
        return root
    }

    companion object {
        const val PREFS_NAME: String = "ads_demo_sdk_backend"
        const val KEY_BACKEND: String = "sdk_backend"
        const val     BACKEND_FAKE: String = "Fake"
        const val BACKEND_ADMOB_TEST: String = "AdMobTest"
        // Match app-demo default: AdMob Test unless user explicitly chose Fake.
        private const val DEFAULT_BACKEND: String = BACKEND_ADMOB_TEST
    }
}
