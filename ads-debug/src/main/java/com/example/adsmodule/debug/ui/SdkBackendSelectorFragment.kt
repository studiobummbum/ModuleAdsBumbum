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
 * Persists AdMob Test / AdMob selection. Requires process restart to apply.
 * Preference key is shared with app-demo DemoSdkBackendStore.
 * Legacy "Fake" values are treated as AdMob Test.
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
        val admobTest = root.findViewById<RadioButton>(R.id.sdk_backend_admob_test)
        val admob = root.findViewById<RadioButton>(R.id.sdk_backend_admob)
        val hint = root.findViewById<TextView>(R.id.sdk_backend_hint)

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BACKEND, DEFAULT_BACKEND) ?: DEFAULT_BACKEND
        val current = when (raw) {
            BACKEND_ADMOB -> BACKEND_ADMOB
            BACKEND_ADMOB_TEST, BACKEND_FAKE_LEGACY -> BACKEND_ADMOB_TEST
            else -> BACKEND_ADMOB_TEST
        }
        if (raw == BACKEND_FAKE_LEGACY) {
            prefs.edit().putString(KEY_BACKEND, BACKEND_ADMOB_TEST).apply()
        }
        when (current) {
            BACKEND_ADMOB -> admob.isChecked = true
            else -> admobTest.isChecked = true
        }
        hint.text = getString(R.string.debug_sdk_backend_hint, current)

        group.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.sdk_backend_admob -> BACKEND_ADMOB
                else -> BACKEND_ADMOB_TEST
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
        const val BACKEND_ADMOB_TEST: String = "AdMobTest"
        const val BACKEND_ADMOB: String = "AdMob"
        private const val BACKEND_FAKE_LEGACY: String = "Fake"
        private const val DEFAULT_BACKEND: String = BACKEND_ADMOB_TEST
    }
}
