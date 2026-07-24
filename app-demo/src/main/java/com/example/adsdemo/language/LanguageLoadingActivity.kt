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
import com.example.adsdemo.databinding.ActivityLanguageLoadingBinding
import com.example.adsdemo.sdk.SelectingNormalNativeAdBinder
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import kotlinx.coroutines.launch

class LanguageLoadingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLanguageLoadingBinding
    private val binder: NormalNativeAdBinder by lazy {
        SelectingNormalNativeAdBinder(
            (application as AdsDemoApplication).graph.sdkBackend,
        )
    }
    private var boundObjectId: String? = null
    private var restoredSessionId: String? = null

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
                    val remaining = snap.loadingTimer.remainingMillis
                    binding.languageLoadingStatus.text = buildString {
                        append(getString(R.string.language_loading_status))
                        if (remaining != null) {
                            append('\n')
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.sessionIdOrNull()?.value?.let { outState.putString(KEY_SESSION_ID, it) }
    }

    companion object {
        const val EXTRA_SESSION_ID: String = "language_loading_session_id"
        private const val KEY_SESSION_ID: String = "language_session_id"
    }
}
