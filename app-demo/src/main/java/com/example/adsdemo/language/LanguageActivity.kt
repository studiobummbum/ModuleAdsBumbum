package com.example.adsdemo.language

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityLanguageBinding
import com.example.adsdemo.sdk.AdMobNormalNativeAdBinder
import com.example.adsmodule.core.language.DemoLanguage
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import kotlinx.coroutines.launch

class LanguageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageBinding
    private val binder: NormalNativeAdBinder by lazy { AdMobNormalNativeAdBinder() }
    private var boundObjectId: String? = null
    private var restoredSessionId: String? = null
    private var restoredLanguageTag: String? = null
    private var renderedLanguages: Boolean = false
    private var pendingSelection: DemoLanguage? = null
    private var navigatingToDup: Boolean = false

    private val viewModel: LanguageFlowViewModel by viewModels {
        LanguageFlowViewModel.factory(
            graph = (application as AdsDemoApplication).graph,
            savedSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: restoredSessionId,
            savedLanguageTag = restoredLanguageTag,
            startLoadingTimer = false,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredSessionId = savedInstanceState?.getString(KEY_SESSION_ID)
        restoredLanguageTag = savedInstanceState?.getString(KEY_LANGUAGE_TAG)
        super.onCreate(savedInstanceState)
        // Kill any residual enter/exit animation for this hop.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (pendingSelection == null && !restoredLanguageTag.isNullOrBlank()) {
            pendingSelection = viewModel.languages.find { it.tag == restoredLanguageTag }
        }

        binding.languageSystemRow.setOnClickListener {
            val defaultLanguage = viewModel.languages.firstOrNull() ?: return@setOnClickListener
            navigateWithSelection(defaultLanguage)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onLanguageSelectOpened()
                viewModel.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    if (!renderedLanguages) {
                        if (pendingSelection == null) {
                            pendingSelection = snap.selectedLanguage
                        }
                        renderLanguages(pendingSelection)
                        renderedLanguages = true
                    } else {
                        updateSelectionMarks(pendingSelection?.tag)
                    }
                    val placement = snap.placements.select
                    viewModel.tryReplaceBound(LanguagePlacement.SELECT)
                    val ad = viewModel.boundAd(LanguagePlacement.SELECT)?.session?.storedAd
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
                                binding.nativeLanguageContainer,
                                ad,
                                title = "Language",
                            )
                            binding.nativeLanguageContainer.visibility = View.VISIBLE
                            boundObjectId = objectId
                        }
                    }
                    // Fallback path if effect arrives via flow (e.g. recreation).
                    if (!navigatingToDup &&
                        snap.pendingEffect == LanguageNavigationEffect.OPEN_LANGUAGE_DUP
                    ) {
                        openDupImmediate(
                            sessionId = snap.sessionId.value,
                            languageTag = snap.selectedLanguage?.tag,
                        )
                    }
                }
            }
        }
    }

    private fun navigateWithSelection(language: DemoLanguage) {
        if (navigatingToDup) return
        pendingSelection = language
        updateSelectionMarks(language.tag)
        if (!viewModel.selectLanguage(language)) return
        val sessionId = viewModel.sessionIdOrNull()?.value ?: return
        // Navigate on the click path (no Flow round-trip) for zero perceived hitch.
        openDupImmediate(sessionId = sessionId, languageTag = language.tag)
    }

    private fun openDupImmediate(sessionId: String, languageTag: String?) {
        if (navigatingToDup || isFinishing) return
        if (!viewModel.claimEffect(LanguageNavigationEffect.OPEN_LANGUAGE_DUP)) return
        navigatingToDup = true
        val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
        startActivity(
            Intent(this, LanguageDupActivity::class.java)
                .putExtra(LanguageDupActivity.EXTRA_SESSION_ID, sessionId)
                .putExtra(LanguageDupActivity.EXTRA_LANGUAGE_TAG, languageTag)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
            options.toBundle(),
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun renderLanguages(selected: DemoLanguage?) {
        binding.languageList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        viewModel.languages.forEach { language ->
            val row = inflater.inflate(R.layout.item_language, binding.languageList, false)
            row.findViewById<TextView>(R.id.language_name).text = language.displayName
            val mark = row.findViewById<ImageView>(R.id.language_selected_mark)
            mark.setImageResource(
                if (selected?.tag == language.tag) {
                    R.drawable.ic_radio_checked
                } else {
                    R.drawable.ic_radio_unchecked
                },
            )
            row.tag = language.tag
            row.setOnClickListener {
                navigateWithSelection(language)
            }
            binding.languageList.addView(row)
        }
    }

    private fun updateSelectionMarks(selectedTag: String?) {
        for (index in 0 until binding.languageList.childCount) {
            val row = binding.languageList.getChildAt(index)
            val mark = row.findViewById<ImageView>(R.id.language_selected_mark)
            mark.setImageResource(
                if (row.tag == selectedTag) {
                    R.drawable.ic_radio_checked
                } else {
                    R.drawable.ic_radio_unchecked
                },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.sessionIdOrNull()?.value?.let { outState.putString(KEY_SESSION_ID, it) }
        pendingSelection?.tag?.let { outState.putString(KEY_LANGUAGE_TAG, it) }
    }

    companion object {
        const val EXTRA_SESSION_ID: String = "language_session_id"
        private const val KEY_SESSION_ID: String = "language_session_id"
        private const val KEY_LANGUAGE_TAG: String = "selected_language_tag"
    }
}
