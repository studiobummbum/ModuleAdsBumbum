package com.example.adsdemo.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityOnboardingBinding
import com.example.adsdemo.home.HomeActivity
import com.example.adsmodule.core.onboarding.OnboardingForwardResult
import com.example.adsmodule.core.onboarding.OnboardingNavigationEffect
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter
    private var restoredBundle: Bundle? = null

    private val viewModel: OnboardingViewModel by viewModels {
        val app = application as AdsDemoApplication
        OnboardingViewModel.factory(
            graph = app.graph,
            languageSessionId = intent.getStringExtra(EXTRA_LANGUAGE_SESSION_ID)
                ?: restoredBundle?.getString(KEY_LANGUAGE_SESSION_ID),
            languageTag = intent.getStringExtra(EXTRA_LANGUAGE_TAG)
                ?: OnboardingStateBundle.languageTag(restoredBundle),
            restoredState = OnboardingStateBundle.read(restoredBundle),
        )
    }

    private val full1Launcher = registerForActivityResult(OnboardingFullContract(1)) { result ->
        if (result != null) {
            viewModel.onFullResult(result)
        }
    }

    private val full2Launcher = registerForActivityResult(OnboardingFullContract(2)) { result ->
        if (result != null) {
            viewModel.onFullResult(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        restoredBundle = savedInstanceState
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = OnboardingPagerAdapter(this, emptyList())
        binding.onboardingPager.adapter = adapter
        binding.onboardingPager.isUserInputEnabled = false
        // ViewPager2 creates its RecyclerView when the adapter is attached.
        binding.onboardingPager.post {
            OnboardingSwipeGate(
                pager = binding.onboardingPager,
                onForward = { handleForward() },
                onBackward = { handleBackward() },
            )
        }

        binding.onboardingNext.setOnClickListener { handleForward() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ensureStarted()
                viewModel.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    if (adapter.pages() != snap.activePages) {
                        adapter.submitPages(snap.activePages)
                    }
                    val adapterIndex = snap.currentAdapterIndex
                    if (binding.onboardingPager.currentItem != adapterIndex) {
                        binding.onboardingPager.setCurrentItem(adapterIndex, false)
                    }
                    binding.onboardingPageIndicator.text = getString(
                        R.string.onboarding_page_indicator,
                        snap.currentLogicalPage,
                        snap.activePages.size,
                    )
                    viewModel.onPageVisible(snap.currentLogicalPage)
                    maybeLaunchPendingEffect(snap.sessionId.value, snap.pendingEffect)
                }
            }
        }
    }

    private fun handleForward() {
        when (val result = viewModel.requestForward()) {
            is OnboardingForwardResult.MovedToPage -> Unit
            is OnboardingForwardResult.LaunchFull -> Unit
            is OnboardingForwardResult.OpenHome -> Unit
            is OnboardingForwardResult.Ignored -> Unit
        }
    }

    private fun handleBackward() {
        viewModel.requestBackward()
    }

    private fun maybeLaunchPendingEffect(
        sessionIdValue: String,
        effect: OnboardingNavigationEffect?,
    ) {
        if (effect == null) return
        val sessionId = viewModel.sessionIdOrNull() ?: return
        if (sessionId.value != sessionIdValue) return
        val snap = viewModel.snapshot.value ?: return
        when (effect) {
            OnboardingNavigationEffect.OPEN_FULL1 -> {
                val pending = snap.pendingFull ?: return
                if (!viewModel.claimEffect(effect)) return
                full1Launcher.launch(
                    OnboardingFullContract.Input(
                        sessionId = sessionId,
                        fullSessionId = pending.fullSessionId,
                        targetLogicalPage = pending.targetLogicalPage,
                    ),
                )
            }
            OnboardingNavigationEffect.OPEN_FULL2 -> {
                val pending = snap.pendingFull ?: return
                if (!viewModel.claimEffect(effect)) return
                full2Launcher.launch(
                    OnboardingFullContract.Input(
                        sessionId = sessionId,
                        fullSessionId = pending.fullSessionId,
                        targetLogicalPage = pending.targetLogicalPage,
                    ),
                )
            }
            OnboardingNavigationEffect.OPEN_HOME -> {
                if (!viewModel.claimEffect(effect)) return
                val graph = (application as AdsDemoApplication).graph
                graph.onboardingFinishInter.finishAsync { _ ->
                    runOnUiThread {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val saved = viewModel.exportSavedState()
        if (saved != null) {
            OnboardingStateBundle.write(
                outState = outState,
                saved = saved,
                languageTag = viewModel.languageTagValue,
            )
        }
        intent.getStringExtra(EXTRA_LANGUAGE_SESSION_ID)?.let {
            outState.putString(KEY_LANGUAGE_SESSION_ID, it)
        }
    }

    companion object {
        const val EXTRA_LANGUAGE_SESSION_ID: String = "language_session_id"
        const val EXTRA_LANGUAGE_TAG: String = "onboarding_language_tag"
        const val EXTRA_SESSION_ID: String = "onboarding_session_id"
        const val EXTRA_PAGER_INDEX: String = "onboarding_pager_index"
        private const val KEY_LANGUAGE_SESSION_ID: String = "language_session_id"
    }
}
