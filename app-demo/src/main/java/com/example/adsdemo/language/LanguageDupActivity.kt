package com.example.adsdemo.language

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityLanguageDupBinding
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import kotlinx.coroutines.launch

class LanguageDupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageDupBinding
    private val binder: NormalNativeAdBinder = FakeNormalNativeAdBinder()
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
        binding = ActivityLanguageDupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.languageDupNext.setOnClickListener {
            viewModel.onLanguageDupNext()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onLanguageDupOpened()
                viewModel.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    val selected = snap.selectedLanguage
                    binding.languageDupSelected.text =
                        getString(
                            R.string.language_dup_selected,
                            selected?.displayName ?: selected?.tag.orEmpty(),
                        )
                    binding.languageDupNext.isEnabled = selected != null
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
                                binding.nativeLanguageDupContainer,
                                ad,
                                title = "Language Dup",
                            )
                            boundObjectId = objectId
                        }
                    }
                    if (snap.pendingEffect == LanguageNavigationEffect.OPEN_APPLY_LANGUAGE) {
                        if (viewModel.claimEffect(LanguageNavigationEffect.OPEN_APPLY_LANGUAGE)) {
                            startActivity(
                                Intent(this@LanguageDupActivity, ApplyLanguageActivity::class.java)
                                    .putExtra(ApplyLanguageActivity.EXTRA_SESSION_ID, snap.sessionId.value)
                                    .putExtra(
                                        ApplyLanguageActivity.EXTRA_LANGUAGE_TAG,
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
        const val EXTRA_SESSION_ID: String = "language_dup_session_id"
        const val EXTRA_LANGUAGE_TAG: String = "language_dup_language_tag"
        private const val KEY_SESSION_ID: String = "language_session_id"
        private const val KEY_LANGUAGE_TAG: String = "selected_language_tag"
    }
}
