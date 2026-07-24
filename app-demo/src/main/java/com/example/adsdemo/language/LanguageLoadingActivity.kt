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
import com.example.adsdemo.databinding.ActivityLanguageLoadingBinding
import com.example.adsdemo.sdk.AdMobNormalNativeAdBinder
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import kotlinx.coroutines.launch

class LanguageLoadingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageLoadingBinding
    private val binder: NormalNativeAdBinder by lazy {
        AdMobNormalNativeAdBinder()
    }
    private var boundObjectId: String? = null
    private var restoredSessionId: String? = null
    private var renderedSkeleton: Boolean = false

    private val viewModel: LanguageFlowViewModel by viewModels {
        LanguageFlowViewModel.factory(
            graph = (application as AdsDemoApplication).graph,
            // Splash may pass its own session id in the Intent; Language owns a separate session.
            savedSessionId = restoredSessionId,
            savedLanguageTag = null,
            startLoadingTimer = true,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredSessionId = savedInstanceState?.getString(KEY_SESSION_ID)
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    if (!renderedSkeleton) {
                        renderSkeletonList()
                        renderedSkeleton = true
                    }
                    val remaining = snap.loadingTimer.remainingMillis
                    binding.languageLoadingStatus.text = buildString {
                        append(getString(R.string.language_loading_label))
                        if (remaining != null) {
                            append(" · ")
                            append("${remaining}ms")
                        }
                    }
                    val placement = snap.placements.loading
                    val ad = viewModel.boundAd(LanguagePlacement.LOADING)?.session?.storedAd
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
                                binding.nativeLanguageLoadingContainer,
                                ad,
                                title = "Language Loading",
                            )
                            binding.nativeLanguageLoadingContainer.visibility = View.VISIBLE
                            boundObjectId = objectId
                        }
                    }
                    if (snap.pendingEffect == LanguageNavigationEffect.OPEN_LANGUAGE_SELECT) {
                        if (viewModel.claimEffect(LanguageNavigationEffect.OPEN_LANGUAGE_SELECT)) {
                            startActivity(
                                Intent(this@LanguageLoadingActivity, LanguageActivity::class.java)
                                    .putExtra(LanguageActivity.EXTRA_SESSION_ID, snap.sessionId.value),
                            )
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun renderSkeletonList() {
        binding.languageLoadingList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        viewModel.languages.forEach { language ->
            val row = inflater.inflate(R.layout.item_language, binding.languageLoadingList, false)
            row.findViewById<TextView>(R.id.language_name).text = language.displayName
            row.findViewById<ImageView>(R.id.language_selected_mark)
                .setImageResource(R.drawable.ic_radio_unchecked)
            row.isClickable = false
            row.isFocusable = false
            binding.languageLoadingList.addView(row)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.sessionIdOrNull()?.value?.let { outState.putString(KEY_SESSION_ID, it) }
    }

    companion object {
        const val EXTRA_SESSION_ID: String = "language_loading_session_id"
        private const val KEY_SESSION_ID: String = "language_session_id"
    }
}
