package com.example.adsdemo.splash

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivitySplashBinding
import com.example.adsdemo.language.LanguageLoadingActivity
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.splash.SplashFlowSnapshot
import com.example.adsmodule.core.splash.SplashNavigationEffect
import com.example.adsmodule.core.splash.SplashStage
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private var progressAnimator: ValueAnimator? = null
    private var loadingAdsDialog: Dialog? = null
    private var confirmedPrimaryShowSessionId: String? = null
    private var progressStartedForSession: String? = null
    private var fallbackProgressStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingConfirmRunnable: Runnable? = null

    private val graph get() = (application as AdsDemoApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Always move the bar immediately so splash never looks frozen while config/ads load.
        startProgressAnimation(DEFAULT_TIMEOUT_MILLIS, fromProgress = 0)

        binding.splashDebugOverlay.visibility =
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        lifecycleScope.launch {
            val existing = savedInstanceState?.getString(KEY_SESSION_ID)?.let(::SplashSessionId)
            val repoSnapshot = graph.configRepository.snapshots.value
            if (repoSnapshot == null) {
                graph.configRepository.refresh()
            }
            val snapshot = graph.configRepository.snapshots.value
            if (snapshot != null) {
                graph.splashCoordinator.startOrAttach(snapshot, existing)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                graph.splashCoordinator.snapshot.collect { snap ->
                    if (snap != null) {
                        render(snap)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        graph.splashCoordinator.snapshot.value?.sessionId?.value?.let {
            outState.putString(KEY_SESSION_ID, it)
        }
    }

    override fun onDestroy() {
        cancelPendingConfirm()
        progressAnimator?.cancel()
        progressAnimator = null
        dismissLoadingAdsDialog()
        super.onDestroy()
    }

    private fun render(snap: SplashFlowSnapshot) {
        binding.splashStatus.text = getString(R.string.splash_status_loading)
        if (binding.splashDebugOverlay.visibility == View.VISIBLE) {
            binding.splashDebugOverlay.text = buildString {
                append("session=").append(snap.sessionId.value).append('\n')
                append("stage=").append(snap.stage).append('\n')
                append("skip=").append(snap.skipTimer.state)
                    .append(" rem=").append(snap.skipTimer.remainingMillis).append('\n')
                append("showRequest=").append(snap.primaryShowRequestId?.value).append('\n')
                append("effect=").append(snap.pendingEffect).append('\n')
                append("timeoutTotal=").append(snap.screenTimeoutTotalMillis).append('\n')
                append("progress=").append(binding.splashProgress.progress).append('\n')
                append(snap.debugMessage.orEmpty())
            }
        }

        updateProgress(snap)

        when (snap.stage) {
            SplashStage.PRIMARY_PRESHOW -> {
                snapProgressToFull()
                showLoadingAdsDialogAndConfirm(snap.sessionId)
            }
            SplashStage.PRIMARY_SHOWING,
            SplashStage.NATIVE_FULL,
            SplashStage.LANGUAGE_LOADING,
            SplashStage.TERMINAL,
            -> {
                cancelPendingConfirm()
                snapProgressToFull()
                dismissLoadingAdsDialog()
            }
            else -> {
                if (loadingAdsDialog?.isShowing == true &&
                    snap.stage != SplashStage.PRIMARY_PRESHOW
                ) {
                    cancelPendingConfirm()
                    dismissLoadingAdsDialog()
                }
            }
        }

        when (snap.pendingEffect) {
            SplashNavigationEffect.OPEN_NATIVE_FULL -> {
                if (graph.splashCoordinator.claimEffect(
                        snap.sessionId,
                        SplashNavigationEffect.OPEN_NATIVE_FULL,
                    )
                ) {
                    startActivity(
                        Intent(this, NativeFullSplashActivity::class.java)
                            .putExtra(NativeFullSplashActivity.EXTRA_SESSION_ID, snap.sessionId.value),
                    )
                }
            }
            SplashNavigationEffect.OPEN_LANGUAGE_LOADING -> {
                if (graph.splashCoordinator.claimEffect(
                        snap.sessionId,
                        SplashNavigationEffect.OPEN_LANGUAGE_LOADING,
                    )
                ) {
                    startActivity(
                        Intent(this, LanguageLoadingActivity::class.java)
                            .putExtra(LanguageLoadingActivity.EXTRA_SESSION_ID, snap.sessionId.value)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    graph.splashCoordinator.onLanguageLoadingOpened(snap.sessionId)
                    finish()
                }
            }
            null -> Unit
        }
    }

    private fun updateProgress(snap: SplashFlowSnapshot) {
        if (snap.stage == SplashStage.PRIMARY_PRESHOW ||
            snap.stage == SplashStage.PRIMARY_SHOWING ||
            snap.stage == SplashStage.NATIVE_FULL ||
            snap.stage == SplashStage.LANGUAGE_LOADING ||
            snap.stage == SplashStage.TERMINAL
        ) {
            snapProgressToFull()
            return
        }

        val total = snap.screenTimeoutTotalMillis ?: return
        if (total <= 0L) return
        if (progressStartedForSession == snap.sessionId.value && progressAnimator?.isRunning == true) {
            return
        }
        // Re-sync animator to coordinator timeout once available (keeps current fill).
        val from = binding.splashProgress.progress.coerceIn(0, 99)
        progressStartedForSession = snap.sessionId.value
        fallbackProgressStarted = true
        startProgressAnimation(total, fromProgress = from)
    }

    private fun startProgressAnimation(durationMillis: Long, fromProgress: Int) {
        progressAnimator?.cancel()
        binding.splashProgress.max = 100
        binding.splashProgress.progress = fromProgress
        if (fromProgress >= 100) return
        val duration = durationMillis.coerceAtLeast(1L)
        progressAnimator = ValueAnimator.ofInt(fromProgress, 100).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                binding.splashProgress.progress = animator.animatedValue as Int
            }
            start()
        }
        fallbackProgressStarted = true
    }

    private fun snapProgressToFull() {
        progressAnimator?.cancel()
        progressAnimator = null
        binding.splashProgress.progress = 100
    }

    private fun showLoadingAdsDialogAndConfirm(sessionId: SplashSessionId) {
        if (confirmedPrimaryShowSessionId == sessionId.value) return
        if (pendingConfirmRunnable != null) return

        if (loadingAdsDialog?.isShowing != true) {
            val dialog = Dialog(this).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                setContentView(
                    LayoutInflater.from(this@SplashActivity)
                        .inflate(R.layout.dialog_splash_loading_ads, null),
                )
            }
            loadingAdsDialog = dialog
            dialog.show()
        }

        val runnable = Runnable {
            pendingConfirmRunnable = null
            if (isFinishing || isDestroyed) return@Runnable
            if (confirmedPrimaryShowSessionId == sessionId.value) return@Runnable
            val current = graph.splashCoordinator.snapshot.value
            if (current?.sessionId != sessionId || current.stage != SplashStage.PRIMARY_PRESHOW) {
                return@Runnable
            }
            if (graph.splashCoordinator.confirmPrimaryShow(sessionId)) {
                confirmedPrimaryShowSessionId = sessionId.value
            }
        }
        pendingConfirmRunnable = runnable
        mainHandler.postDelayed(runnable, SPLASH_LOADING_ADS_DELAY_MILLIS)
    }

    private fun cancelPendingConfirm() {
        pendingConfirmRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingConfirmRunnable = null
    }

    private fun dismissLoadingAdsDialog() {
        loadingAdsDialog?.dismiss()
        loadingAdsDialog = null
    }

    private companion object {
        private const val KEY_SESSION_ID = "splash_session_id"
        private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        private const val SPLASH_LOADING_ADS_DELAY_MILLIS = 1_000L
    }
}
