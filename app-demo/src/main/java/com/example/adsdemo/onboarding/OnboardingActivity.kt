package com.example.adsdemo.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.databinding.ActivityOnboardingBinding
import com.example.adsdemo.home.HomeActivity
import com.example.adsmodule.core.onboarding.OnboardingBackwardResult
import com.example.adsmodule.core.onboarding.OnboardingForwardResult
import com.example.adsmodule.core.onboarding.OnboardingNavigationEffect
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter
    private var restoredBundle: Bundle? = null
    /** Prevents feedback loops when we programmatically move the pager. */
    private var suppressPagerCallback: Boolean = false
    private var pagerScrollState: Int = ViewPager2.SCROLL_STATE_IDLE

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
        binding.onboardingPager.isUserInputEnabled = true
        binding.onboardingPager.offscreenPageLimit = 1
        binding.onboardingPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    pagerScrollState = state
                    // Only commit navigation when the user gesture has settled. This avoids
                    // launching Full when the user swipes forward then pulls back.
                    if (state == ViewPager2.SCROLL_STATE_IDLE && !suppressPagerCallback) {
                        handleUserPageSettled(binding.onboardingPager.currentItem)
                    }
                }
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ensureStarted()
                viewModel.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    if (adapter.pages() != snap.activePages) {
                        adapter.submitPages(snap.activePages)
                    }
                    val adapterIndex = snap.currentAdapterIndex
                    if (binding.onboardingPager.currentItem != adapterIndex &&
                        pagerScrollState == ViewPager2.SCROLL_STATE_IDLE
                    ) {
                        setPagerItem(adapterIndex, smooth = false)
                    }
                    viewModel.onPageVisible(snap.currentLogicalPage)
                    maybeLaunchPendingEffect(snap.sessionId.value, snap.pendingEffect)
                }
            }
        }
    }

    internal fun forwardFromFragment() {
        handleForward()
    }

    internal fun activePages(): List<Int> =
        viewModel.snapshot.value?.activePages.orEmpty()

    /**
     * Called only when pager scroll is IDLE. Syncs user swipe with the boundary
     * coordinator. Backward re-opens Full when the previous page sits behind a Full gate.
     */
    private fun handleUserPageSettled(position: Int) {
        val snap = viewModel.snapshot.value ?: return
        if (position !in snap.activePages.indices) return
        val targetLogical = snap.activePages[position]
        val currentLogical = snap.currentLogicalPage
        if (targetLogical == currentLogical) return

        val currentIndex = snap.currentAdapterIndex
        if (position > currentIndex) {
            when (val result = viewModel.requestForward()) {
                is OnboardingForwardResult.MovedToPage -> {
                    val newIndex = viewModel.snapshot.value?.currentAdapterIndex ?: currentIndex
                    if (newIndex != position) {
                        setPagerItem(newIndex, smooth = false)
                    }
                }
                is OnboardingForwardResult.LaunchFull -> {
                    // Keep pager on the gate page; effect collector opens Full.
                    setPagerItem(currentIndex, smooth = false)
                }
                is OnboardingForwardResult.OpenHome -> {
                    setPagerItem(currentIndex, smooth = false)
                }
                is OnboardingForwardResult.Ignored -> {
                    setPagerItem(currentIndex, smooth = false)
                }
            }
        } else {
            when (val result = viewModel.requestBackward()) {
                is OnboardingBackwardResult.MovedToPage -> {
                    val newIndex = viewModel.snapshot.value?.currentAdapterIndex ?: currentIndex
                    if (newIndex != position) {
                        setPagerItem(newIndex, smooth = false)
                    }
                }
                is OnboardingBackwardResult.LaunchFull -> {
                    // Stay on current pager while Full opens; result restores previous page.
                    setPagerItem(currentIndex, smooth = false)
                }
                is OnboardingBackwardResult.Ignored -> {
                    setPagerItem(currentIndex, smooth = false)
                }
            }
        }
    }

    private fun setPagerItem(index: Int, smooth: Boolean) {
        if (binding.onboardingPager.currentItem == index) return
        suppressPagerCallback = true
        binding.onboardingPager.setCurrentItem(index, smooth)
        binding.onboardingPager.post {
            suppressPagerCallback = false
        }
    }

    private fun handleForward() {
        val before = viewModel.snapshot.value?.currentAdapterIndex
        when (val result = viewModel.requestForward()) {
            is OnboardingForwardResult.MovedToPage -> Unit
            is OnboardingForwardResult.LaunchFull -> {
                if (before != null) setPagerItem(before, smooth = false)
            }
            is OnboardingForwardResult.OpenHome -> Unit
            is OnboardingForwardResult.Ignored -> Unit
        }
    }

    private fun maybeLaunchPendingEffect(
        sessionIdValue: String,
        effect: OnboardingNavigationEffect?,
    ) {
        if (effect == null) return
        // Wait until the pager gesture settles so pull-back can cancel pending Full first.
        if (pagerScrollState != ViewPager2.SCROLL_STATE_IDLE) return
        val sessionId = viewModel.sessionIdOrNull() ?: return
        if (sessionId.value != sessionIdValue) return
        val snap = viewModel.snapshot.value ?: return
        when (effect) {
            OnboardingNavigationEffect.OPEN_FULL1 -> {
                val pending = snap.pendingFull ?: return
                if (pending.fullIndex != 1) return
                // Forward gate stays on page 2; backward re-open keeps current after the gate.
                val forwardGate = snap.currentLogicalPage == 2
                val backwardGate = pending.targetLogicalPage == 2 && snap.currentLogicalPage != 2
                if (!forwardGate && !backwardGate) return
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
                if (pending.fullIndex != 2) return
                val forwardGate = snap.currentLogicalPage == 3
                val backwardGate = pending.targetLogicalPage == 3 && snap.currentLogicalPage != 3
                if (!forwardGate && !backwardGate) return
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
