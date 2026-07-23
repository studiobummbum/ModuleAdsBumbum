package com.example.adsdemo.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.databinding.ActivityNativeFullSplashBinding
import com.example.adsdemo.language.LanguageLoadingActivity
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.splash.SplashNavigationEffect
import com.example.adsmodule.core.splash.SplashStage
import kotlinx.coroutines.launch

class NativeFullSplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNativeFullSplashBinding
    private var sessionId: SplashSessionId? = null
    private var showRequestId: ShowRequestId? = null

    private val graph get() = (application as AdsDemoApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNativeFullSplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionValue = intent.getStringExtra(EXTRA_SESSION_ID)
            ?: savedInstanceState?.getString(EXTRA_SESSION_ID)
        if (sessionValue.isNullOrBlank()) {
            finish()
            return
        }
        val resolvedSessionId = SplashSessionId(sessionValue)
        sessionId = resolvedSessionId

        ViewCompat.setOnApplyWindowInsetsListener(binding.nativeFullClose) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as MarginLayoutParams
            lp.topMargin = bars.top + dp(8)
            lp.marginEnd = bars.right + dp(8)
            view.layoutParams = lp
            insets
        }

        val launch = graph.splashCoordinator.consumeNativeFullLaunch(resolvedSessionId)
        if (launch != null) {
            showRequestId = launch.hostedSession.showRequestId
            binding.nativeFullBody.text = buildString {
                append(launch.hostedSession.storedAd.sourceConfigKey.value)
                append(" #")
                append(launch.hostedSession.storedAd.sourceListIndex)
                append(" w=")
                append(launch.hostedSession.storedAd.sourceWeight)
                append('\n')
                append(launch.hostedSession.storedAd.sourceAdunit)
            }
            binding.nativeFullCta.text = launch.hostedSession.storedAd.sourceAdunit
        } else {
            val snap = graph.splashCoordinator.snapshot.value
            showRequestId = snap?.nativeFull?.showRequestId
            binding.nativeFullBody.text = snap?.nativeFull?.showRequestId?.value.orEmpty()
        }

        binding.nativeFullClose.setOnClickListener {
            val requestId = showRequestId ?: return@setOnClickListener
            graph.splashCoordinator.onNativeFullCloseClicked(resolvedSessionId, requestId)
        }

        binding.nativeFullDebug.visibility =
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                graph.splashCoordinator.snapshot.collect { snap ->
                    if (snap == null || snap.sessionId != resolvedSessionId) return@collect
                    showRequestId = snap.nativeFull.showRequestId ?: showRequestId
                    val closeVisible = snap.nativeFull.closeVisible
                    binding.nativeFullClose.visibility =
                        if (closeVisible) View.VISIBLE else View.INVISIBLE
                    binding.nativeFullClose.isEnabled = closeVisible
                    if (binding.nativeFullDebug.visibility == View.VISIBLE) {
                        binding.nativeFullDebug.text = buildString {
                            append("closeVisible=").append(closeVisible).append('\n')
                            append("closeRem=").append(snap.nativeFull.remainingCloseDelayMillis)
                            append(" autoRem=").append(snap.nativeFull.remainingAutoSkipMillis)
                            append('\n')
                            append("exit=").append(snap.nativeFull.exitSource)
                        }
                    }
                    if (snap.pendingEffect == SplashNavigationEffect.OPEN_LANGUAGE_LOADING) {
                        if (graph.splashCoordinator.claimEffect(
                                resolvedSessionId,
                                SplashNavigationEffect.OPEN_LANGUAGE_LOADING,
                            )
                        ) {
                            startActivity(
                                Intent(this@NativeFullSplashActivity, LanguageLoadingActivity::class.java)
                                    .putExtra(LanguageLoadingActivity.EXTRA_SESSION_ID, resolvedSessionId.value),
                            )
                            graph.splashCoordinator.onLanguageLoadingOpened(resolvedSessionId)
                            finish()
                        }
                    } else if (
                        snap.stage == SplashStage.LANGUAGE_LOADING ||
                        snap.stage == SplashStage.TERMINAL
                    ) {
                        finish()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sessionId?.value?.let { outState.putString(EXTRA_SESSION_ID, it) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SESSION_ID: String = "native_full_splash_session_id"
    }
}
