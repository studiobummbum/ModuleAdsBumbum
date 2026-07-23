package com.example.adsdemo.language

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.adsdemo.databinding.ActivityLanguageLoadingBinding

/**
 * Phase 09 placeholder destination. Phase 10 will complete Language Loading ads UI.
 */
class LanguageLoadingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageLoadingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        binding.languageLoadingStatus.text =
            getString(
                com.example.adsdemo.R.string.language_loading_placeholder,
            ) + if (sessionId.isNotBlank()) "\n$sessionId" else ""
    }

    companion object {
        const val EXTRA_SESSION_ID: String = "language_loading_session_id"
    }
}
