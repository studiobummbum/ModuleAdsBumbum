package com.example.adsdemo.onboarding

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityOnboardingBinding
import com.example.adsdemo.language.LanguageFlowViewModel
import kotlinx.coroutines.launch

/**
 * Phase 10 destination placeholder. Phase 11 owns ViewPager2 / fragments.
 */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private var restoredSessionId: String? = null
    private var restoredLanguageTag: String? = null

    private val viewModel: LanguageFlowViewModel by viewModels {
        LanguageFlowViewModel.factory(
            graph = (application as AdsDemoApplication).graph,
            savedSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: restoredSessionId,
            savedLanguageTag = intent.getStringExtra(EXTRA_LANGUAGE_TAG) ?: restoredLanguageTag,
            startLoadingTimer = false,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredSessionId = savedInstanceState?.getString(KEY_SESSION_ID)
        restoredLanguageTag = savedInstanceState?.getString(KEY_LANGUAGE_TAG)
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pager = intent.getIntExtra(EXTRA_PAGER_INDEX, 1)
        binding.onboardingPlaceholderTitle.text =
            getString(R.string.onboarding_placeholder_title) + " (page $pager)"

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onOnboardingOpened()
                viewModel.snapshot.collect { snap ->
                    val language = snap?.selectedLanguage?.displayName
                        ?: intent.getStringExtra(EXTRA_LANGUAGE_TAG).orEmpty()
                    binding.onboardingPlaceholderBody.text = buildString {
                        append(getString(R.string.onboarding_placeholder_body))
                        if (language.isNotBlank()) {
                            append("\nLanguage: ")
                            append(language)
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.sessionIdOrNull()?.value?.let { outState.putString(KEY_SESSION_ID, it) }
        viewModel.snapshot.value?.selectedLanguage?.tag?.let {
            outState.putString(KEY_LANGUAGE_TAG, it)
        }
    }

    companion object {
        const val EXTRA_SESSION_ID: String = "onboarding_session_id"
        const val EXTRA_LANGUAGE_TAG: String = "onboarding_language_tag"
        const val EXTRA_PAGER_INDEX: String = "onboarding_pager_index"
        private const val KEY_SESSION_ID: String = "language_session_id"
        private const val KEY_LANGUAGE_TAG: String = "selected_language_tag"
    }
}
