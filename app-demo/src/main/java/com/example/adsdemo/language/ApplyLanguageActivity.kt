package com.example.adsdemo.language

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityApplyLanguageBinding
import com.example.adsdemo.onboarding.OnboardingActivity
import com.example.adsdemo.sdk.AdMobNormalNativeAdBinder
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import com.example.adsmodule.core.language.LocaleApplyStatus
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import kotlinx.coroutines.launch

class ApplyLanguageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityApplyLanguageBinding
    private val binder: NormalNativeAdBinder by lazy { AdMobNormalNativeAdBinder() }
    private var boundObjectId: String? = null
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
        binding = ActivityApplyLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onApplyLanguageOpened()
                viewModel.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    val selected = snap.selectedLanguage
                    val displayName = selected?.displayName ?: selected?.tag.orEmpty()
                    binding.applyLanguageSelected.text = displayName
                    binding.applyLanguageStatus.text = when (snap.localeStatus) {
                        LocaleApplyStatus.FAILED_FALLBACK ->
                            "Locale fallback: ${snap.localeMessage.orEmpty()}"
                        else -> getString(R.string.apply_language_status_named, displayName)
                    }
                    val placement = snap.placements.dup
                    val ad = viewModel.boundAd(LanguagePlacement.DUP)?.session?.storedAd
                        ?: placement?.storedAd
                    if (
                        ad != null &&
                        (
                            placement?.status == NormalScreenLoadStatus.BOUND ||
                                placement?.status == NormalScreenLoadStatus.READY
                            )
                    ) {
                        val objectId = ad.objectId.value
                        if (boundObjectId != objectId) {
                            binder.bindNative(
                                binding.nativeApplyLanguageContainer,
                                ad,
                                title = "Apply Language",
                            )
                            binding.nativeApplyLanguageContainer.visibility = View.VISIBLE
                            boundObjectId = objectId
                        }
                    }
                    if (snap.pendingEffect == LanguageNavigationEffect.OPEN_ONBOARDING) {
                        if (viewModel.claimEffect(LanguageNavigationEffect.OPEN_ONBOARDING)) {
                            startActivity(
                                Intent(this@ApplyLanguageActivity, OnboardingActivity::class.java)
                                    .putExtra(
                                        OnboardingActivity.EXTRA_LANGUAGE_SESSION_ID,
                                        snap.sessionId.value,
                                    )
                                    .putExtra(
                                        OnboardingActivity.EXTRA_LANGUAGE_TAG,
                                        snap.selectedLanguage?.tag,
                                    ),
                            )
                            finish()
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
        const val EXTRA_SESSION_ID: String = "apply_language_session_id"
        const val EXTRA_LANGUAGE_TAG: String = "apply_language_tag"
        private const val KEY_SESSION_ID: String = "language_session_id"
        private const val KEY_LANGUAGE_TAG: String = "selected_language_tag"
    }
}
