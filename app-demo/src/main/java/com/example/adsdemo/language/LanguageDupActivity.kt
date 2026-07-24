package com.example.adsdemo.language

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityLanguageDupBinding
import com.example.adsdemo.sdk.AdMobNormalNativeAdBinder
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import kotlinx.coroutines.launch

class LanguageDupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageDupBinding
    private val binder: NormalNativeAdBinder by lazy {
        AdMobNormalNativeAdBinder()
    }
    private var boundObjectId: String? = null
    private var restoredSessionId: String? = null
    private var restoredLanguageTag: String? = null
    private var renderedLanguages: Boolean = false

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
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        binding = ActivityLanguageDupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Paint UI from Intent immediately so the hop feels like the same screen.
        val intentTag = intent.getStringExtra(EXTRA_LANGUAGE_TAG) ?: restoredLanguageTag
        val intentLanguage = intentTag?.let { tag -> viewModel.languages.find { it.tag == tag } }
        binding.languageDupSelected.text =
            intentLanguage?.displayName ?: getString(R.string.language_default)
        binding.languageDupNext.isEnabled = intentLanguage != null
        binding.languageDupNext.alpha = if (intentLanguage != null) 1f else 0.4f
        if (!renderedLanguages) {
            renderLanguages(intentLanguage?.tag)
            renderedLanguages = true
        }

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
                        selected?.displayName ?: getString(R.string.language_default)
                    binding.languageDupNext.isEnabled = selected != null
                    binding.languageDupNext.alpha = if (selected != null) 1f else 0.4f
                    updateSelectionMarks(selected?.tag)
                    val placement = snap.placements.dup
                    viewModel.tryReplaceBound(LanguagePlacement.DUP)
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
                            binding.nativeLanguageDupContainer.visibility = View.VISIBLE
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

    private fun renderLanguages(selectedTag: String?) {
        binding.languageDupList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        viewModel.languages.forEach { language ->
            val row = inflater.inflate(R.layout.item_language, binding.languageDupList, false)
            row.findViewById<TextView>(R.id.language_name).text = language.displayName
            val mark = row.findViewById<ImageView>(R.id.language_selected_mark)
            mark.setImageResource(
                if (selectedTag == language.tag) {
                    R.drawable.ic_radio_checked
                } else {
                    R.drawable.ic_radio_unchecked
                },
            )
            row.tag = language.tag
            row.isClickable = false
            row.isFocusable = false
            binding.languageDupList.addView(row)
        }
    }

    private fun updateSelectionMarks(selectedTag: String?) {
        for (index in 0 until binding.languageDupList.childCount) {
            val row = binding.languageDupList.getChildAt(index)
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
