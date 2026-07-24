package com.example.adsdemo.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.databinding.ActivitySplashBinding
import com.example.adsdemo.language.LanguageLoadingActivity
import com.example.adsdemo.sdk.SelectingSplashInlineAdBinder
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.splash.SplashFlowSnapshot
import com.example.adsmodule.core.splash.SplashLoadStatus
import com.example.adsmodule.core.splash.SplashNavigationEffect
import com.example.adsmodule.core.splash.SplashPlacement
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val binder: SplashInlineAdBinder by lazy {
        SelectingSplashInlineAdBinder(graph.sdkBackend)
    }
    private var boundNativeObjectId: String? = null
    private var boundBannerObjectId: String? = null
    private var attachedSessionId: String? = null

    private val graph get() = (application as AdsDemoApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun render(snap: SplashFlowSnapshot) {
        attachedSessionId = snap.sessionId.value
        binding.splashStatus.text = "Stage: ${snap.stage} | skip=${snap.skipTimer.state}"
        if (binding.splashDebugOverlay.visibility == View.VISIBLE) {
            binding.splashDebugOverlay.text = buildString {
                append("session=").append(snap.sessionId.value).append('\n')
                append("stage=").append(snap.stage).append('\n')
                append("skip=").append(snap.skipTimer.state)
                    .append(" rem=").append(snap.skipTimer.remainingMillis).append('\n')
                append("showRequest=").append(snap.primaryShowRequestId?.value).append('\n')
                append("effect=").append(snap.pendingEffect).append('\n')
                snap.placements.values.forEach { placement ->
                    append(placement.placement).append('=')
                        .append(placement.status).append(' ')
                        .append(placement.storedAd?.sourceAdunit ?: "")
                        .append('\n')
                }
                append(snap.debugMessage.orEmpty())
            }
        }

        val native = snap.placements[SplashPlacement.NATIVE_SPLASH]
        if (native?.status == SplashLoadStatus.READY && native.storedAd != null) {
            val objectId = native.storedAd!!.objectId.value
            if (boundNativeObjectId != objectId) {
                binder.bindNative(binding.nativeSplashContainer, native.storedAd!!)
                boundNativeObjectId = objectId
            }
        }

        val banner = snap.placements[SplashPlacement.BANNER_UFO]
        if (banner?.status == SplashLoadStatus.READY && banner.storedAd != null) {
            val objectId = banner.storedAd!!.objectId.value
            if (boundBannerObjectId != objectId) {
                binder.bindBanner(binding.bannerUfoContainer, banner.storedAd!!)
                boundBannerObjectId = objectId
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

    private companion object {
        private const val KEY_SESSION_ID = "splash_session_id"
    }
}
