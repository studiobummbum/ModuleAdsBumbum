package com.example.adsdemo.home

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityHomeBinding
import com.example.adsmodule.core.home.HomeInterShowResult
import com.example.adsmodule.core.lifecycle.AdsLifecycleTransitionResult
import com.example.adsmodule.core.lifecycle.BackgroundReason
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenLoadStatus
import com.example.adsmodule.core.resume.AppOpenResumeResult
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val binder: HomeBannerBinder = FakeHomeBannerBinder()
    private var boundBannerObjectId: String? = null

    private val graph get() = (application as AdsDemoApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        graph.homeAds.ensureBannerPreloaded()
        graph.appOpenResume.ensurePreloaded()
        graph.debugApi.reportNavigation(
            activityName = "HomeActivity",
            screenLabel = "Home",
        )

        binding.homeActionButton.setOnClickListener {
            binding.homeStatus.text = getString(R.string.home_status_triggering_inter)
            graph.homeAds.triggerHomeActionAsync()
        }
        binding.simulateBackgroundButton.setOnClickListener {
            val result = graph.lifecycleCoordinator.onBackground(
                hint = BackgroundReason.USER_BACKGROUND,
            )
            binding.homeStatus.text = when (result) {
                is AdsLifecycleTransitionResult.Accepted ->
                    getString(R.string.home_status_background)
                is AdsLifecycleTransitionResult.Ignored ->
                    getString(R.string.home_status_ignored, result.reason)
            }
        }
        binding.simulateForegroundButton.setOnClickListener {
            val transition = graph.lifecycleCoordinator.onForeground()
            when (transition) {
                is AdsLifecycleTransitionResult.Ignored -> {
                    binding.homeStatus.text =
                        getString(R.string.home_status_ignored, transition.reason)
                }
                is AdsLifecycleTransitionResult.Accepted -> {
                    binding.homeStatus.text = getString(R.string.home_status_foreground)
                    lifecycleScope.launch {
                        val resume = graph.appOpenResume.tryShowResume()
                        binding.homeStatus.text = resumeStatus(resume)
                    }
                }
            }
        }
        // Debug entry is wired only in the debug source set (DebugNavInstaller).
        binding.openDebugDashboardButton.visibility = View.GONE

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val bound = graph.homeAds.bindBanner()
                if (bound is NormalScreenBindResult.Bound) {
                    renderBanner(bound)
                }
                launch {
                    graph.homeAds.snapshot.collect { snap ->
                        snap.bannerSession?.storedAd?.let { ad ->
                            if (boundBannerObjectId != ad.objectId.value) {
                                binder.bindBanner(binding.bannerHomeContainer, ad)
                                boundBannerObjectId = ad.objectId.value
                            }
                        }
                        val bannerStatus = snap.banner?.status
                        val inter = snap.lastInterResult
                        if (inter != null) {
                            binding.homeStatus.text = interStatus(inter)
                        } else if (
                            bannerStatus == NormalScreenLoadStatus.BOUND ||
                            bannerStatus == NormalScreenLoadStatus.READY
                        ) {
                            binding.homeStatus.text = getString(R.string.home_status_banner_ready)
                        }
                        if (snap.intervalBlocked) {
                            binding.homeStatus.text = getString(
                                R.string.home_status_interval_blocked,
                                snap.intervalRemainingMillis ?: 0L,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun renderBanner(bound: NormalScreenBindResult.Bound) {
        val ad = bound.session.storedAd
        if (boundBannerObjectId != ad.objectId.value) {
            binder.bindBanner(binding.bannerHomeContainer, ad)
            boundBannerObjectId = ad.objectId.value
        }
    }

    private fun interStatus(result: HomeInterShowResult): String = when (result) {
        is HomeInterShowResult.Shown ->
            getString(R.string.home_status_inter_shown, result.storedAd.sourceAdunit)
        is HomeInterShowResult.IntervalBlocked ->
            getString(R.string.home_status_interval_blocked, result.remainingMillis)
        is HomeInterShowResult.Failed ->
            getString(R.string.home_status_inter_failed, result.reason)
        is HomeInterShowResult.Rejected ->
            getString(R.string.home_status_inter_rejected, result.reason)
    }

    private fun resumeStatus(result: AppOpenResumeResult): String = when (result) {
        is AppOpenResumeResult.Shown ->
            getString(R.string.home_status_appopen_shown, result.storedAd.sourceAdunit)
        is AppOpenResumeResult.Suppressed ->
            getString(
                R.string.home_status_appopen_suppressed,
                result.suppression.reasons.joinToString(),
            )
        is AppOpenResumeResult.Skipped ->
            getString(R.string.home_status_appopen_skipped, result.reason)
        is AppOpenResumeResult.Failed ->
            getString(R.string.home_status_appopen_failed, result.reason)
        is AppOpenResumeResult.Rejected ->
            getString(R.string.home_status_appopen_rejected, result.reason)
    }

    override fun onDestroy() {
        binder.clear(binding.bannerHomeContainer)
        boundBannerObjectId = null
        graph.homeAds.destroyBanner()
        super.onDestroy()
    }
}
