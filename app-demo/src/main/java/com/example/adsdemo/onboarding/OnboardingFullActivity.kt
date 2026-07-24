package com.example.adsdemo.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.ActivityOnboardingFullBinding
import com.example.adsdemo.sdk.AdMobOnboardingFullNativeBinder
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.onboarding.full.FullActivityPhase
import com.example.adsmodule.core.onboarding.full.FullExitSource
import com.example.adsmodule.core.onboarding.full.OnboardingFullStartResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

abstract class OnboardingFullActivity : AppCompatActivity() {
    protected abstract val fullIndex: Int
    protected abstract val titleRes: Int

    private lateinit var binding: ActivityOnboardingFullBinding
    private val binder: OnboardingFullNativeBinder by lazy {
        AdMobOnboardingFullNativeBinder()
    }
    private var gestureDetector: FullGestureDetector? = null
    private var excludedViews: List<View> = emptyList()
    private var sessionId: OnboardingSessionId? = null
    private var fullSessionId: FullSessionId? = null
    private var targetLogicalPage: Int? = null
    private val finishedOnce = AtomicBoolean(false)
    private var uiReady = false

    private val graph get() = (application as AdsDemoApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionValue = intent.getStringExtra(OnboardingFullContract.EXTRA_SESSION_ID)
            ?: savedInstanceState?.getString(OnboardingFullContract.EXTRA_SESSION_ID)
        val fullSessionValue = intent.getStringExtra(OnboardingFullContract.EXTRA_FULL_SESSION_ID)
            ?: savedInstanceState?.getString(OnboardingFullContract.EXTRA_FULL_SESSION_ID)
        if (sessionValue.isNullOrBlank() || fullSessionValue.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        sessionId = OnboardingSessionId(sessionValue)
        fullSessionId = FullSessionId(fullSessionValue)
        val hasTarget = intent.getBooleanExtra(
            OnboardingFullContract.EXTRA_HAS_TARGET,
            savedInstanceState?.getBoolean(OnboardingFullContract.EXTRA_HAS_TARGET) ?: false,
        )
        targetLogicalPage = if (hasTarget) {
            intent.getIntExtra(
                OnboardingFullContract.EXTRA_TARGET_PAGE,
                savedInstanceState?.getInt(OnboardingFullContract.EXTRA_TARGET_PAGE) ?: -1,
            ).takeIf { it in 1..4 }
        } else {
            null
        }

        // Confirmed empty → skip without flash. Otherwise inflate and wait for READY (incl. parked).
        if (graph.onboardingFullCoordinator.isConfirmedNoFill(fullIndex)) {
            deliverNoFillAndFinish(fullSessionValue, sessionValue)
            return
        }

        binding = ActivityOnboardingFullBinding.inflate(layoutInflater)
        setContentView(binding.root)
        uiReady = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingFullTopChrome) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as MarginLayoutParams
            lp.topMargin = bars.top + dp(8)
            lp.marginEnd = bars.right + dp(8)
            view.layoutParams = lp
            insets
        }
        ViewCompat.requestApplyInsets(binding.onboardingFullTopChrome)

        binding.onboardingFullClose.setOnClickListener {
            val id = fullSessionId ?: return@setOnClickListener
            graph.onboardingFullCoordinator.onCloseClicked(id)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val id = fullSessionId ?: return
                    val handled = graph.onboardingFullCoordinator.onSystemBack(id)
                    Log.i(TAG, "System Back on Full$fullIndex handled=$handled (CLOSE_X policy)")
                }
            },
        )

        val debuggable =
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        binding.onboardingFullDebug.visibility = if (debuggable) View.VISIBLE else View.GONE

        lifecycleScope.launch {
            val ready = graph.onboardingFullCoordinator.awaitReadyOrTerminal(fullIndex)
            if (isFinishing || isDestroyed) return@launch
            if (!ready) {
                deliverNoFillAndFinish(fullSessionValue, sessionValue)
                return@launch
            }
            when (
                val start = graph.onboardingFullCoordinator.startOrAttach(
                    fullSessionId = FullSessionId(fullSessionValue),
                    onboardingSessionId = OnboardingSessionId(sessionValue),
                    fullIndex = fullIndex,
                    targetLogicalPage = targetLogicalPage,
                )
            ) {
                is OnboardingFullStartResult.Rejected -> {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                    return@launch
                }
                is OnboardingFullStartResult.Skipped -> {
                    deliverNoFillAndFinish(fullSessionValue, sessionValue)
                    return@launch
                }
                is OnboardingFullStartResult.Attached -> {
                    if (start.snapshot.winningExitSource == FullExitSource.NO_FILL ||
                        start.snapshot.phase == FullActivityPhase.COMPLETED
                    ) {
                        deliverNoFillAndFinish(fullSessionValue, sessionValue)
                        return@launch
                    }
                    // Full is not sticky mid-session — bind once from start.
                    bindAd(start.snapshot.storedAd)
                    setupGesture()
                }
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                graph.onboardingFullCoordinator.snapshot.collect { snap ->
                    if (snap == null) return@collect
                    if (snap.fullSessionId.value != fullSessionValue) return@collect
                    val closeVisible = snap.closeVisible
                    binding.onboardingFullClose.visibility =
                        if (closeVisible) View.VISIBLE else View.INVISIBLE
                    binding.onboardingFullClose.isEnabled = closeVisible
                    val autoRem = snap.remainingAutoSkipMillis
                    if (closeVisible && autoRem != null) {
                        val seconds = ((autoRem + 999L) / 1000L).toInt().coerceAtLeast(0)
                        binding.onboardingFullAutoSkip.visibility = View.VISIBLE
                        binding.onboardingFullAutoSkip.text =
                            getString(R.string.onboarding_full_auto_skip, seconds)
                    } else {
                        binding.onboardingFullAutoSkip.visibility = View.INVISIBLE
                    }
                    if (snap.storedAd != null &&
                        binding.onboardingFullAdContainer.childCount == 0
                    ) {
                        bindAd(snap.storedAd)
                        setupGesture()
                    }
                    if (binding.onboardingFullDebug.visibility == View.VISIBLE) {
                        val gesture = gestureDetector
                        binding.onboardingFullDebug.text = buildString {
                            append("fullSession=").append(snap.fullSessionId.value).append('\n')
                            append("target=").append(snap.targetLogicalPage)
                            append(" phase=").append(snap.phase)
                            append(" gate=").append(snap.gateState).append('\n')
                            append("closeRem=").append(snap.remainingCloseDelayMillis)
                            append(" autoRem=").append(snap.remainingAutoSkipMillis).append('\n')
                            append("dx=").append(gesture?.lastDx ?: 0f)
                            append(" vel=").append(gesture?.lastVelocity ?: 0f)
                            append(" thr=").append(gesture?.distanceThreshold ?: 0f).append('\n')
                            append("excluded=").append(gesture?.excludedHit == true)
                            append(" win=").append(snap.winningExitSource)
                        }
                    }
                    if (snap.phase == FullActivityPhase.COMPLETED ||
                        snap.winningExitSource != null
                    ) {
                        deliverResultAndFinish()
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sessionId?.value?.let {
            outState.putString(OnboardingFullContract.EXTRA_SESSION_ID, it)
        }
        fullSessionId?.value?.let {
            outState.putString(OnboardingFullContract.EXTRA_FULL_SESSION_ID, it)
        }
        val target = targetLogicalPage
        outState.putBoolean(OnboardingFullContract.EXTRA_HAS_TARGET, target != null)
        if (target != null) {
            outState.putInt(OnboardingFullContract.EXTRA_TARGET_PAGE, target)
        }
    }

    override fun onDestroy() {
        gestureDetector?.cancel()
        if (uiReady && ::binding.isInitialized) {
            binder.clear(binding.onboardingFullAdContainer)
        }
        super.onDestroy()
    }

    private fun deliverNoFillAndFinish(fullSessionValue: String, sessionValue: String) {
        if (!finishedOnce.compareAndSet(false, true)) return
        // Ensure coordinator has a NO_FILL exit to consume when Activity skipped early.
        if (graph.onboardingFullCoordinator.peekActive() == null ||
            graph.onboardingFullCoordinator.peekActive()?.winningExitSource == null
        ) {
            graph.onboardingFullCoordinator.startOrAttach(
                fullSessionId = FullSessionId(fullSessionValue),
                onboardingSessionId = OnboardingSessionId(sessionValue),
                fullIndex = fullIndex,
                targetLogicalPage = targetLogicalPage,
            )
        }
        val fullId = FullSessionId(fullSessionValue)
        val result = graph.onboardingFullCoordinator.consumeExitResult(fullId)
        val intent = Intent()
            .putExtra(OnboardingFullContract.EXTRA_SESSION_ID, sessionValue)
            .putExtra(OnboardingFullContract.EXTRA_FULL_SESSION_ID, fullSessionValue)
            .putExtra(OnboardingFullContract.EXTRA_FULL_INDEX, fullIndex)
            .putExtra(
                OnboardingFullContract.EXTRA_EXIT_SOURCE,
                (result?.exitSource ?: FullExitSource.NO_FILL).name,
            )
            .putExtra(OnboardingFullContract.EXTRA_HAS_TARGET, targetLogicalPage != null)
        targetLogicalPage?.let {
            intent.putExtra(OnboardingFullContract.EXTRA_TARGET_PAGE, it)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun bindAd(storedAd: com.example.adsmodule.core.storage.StoredAdView?) {
        val bound = binder.bind(
            container = binding.onboardingFullAdContainer,
            storedAd = storedAd,
            title = getString(titleRes),
            fullIndex = fullIndex,
        )
        excludedViews = bound.clickableAssets + binding.onboardingFullClose
    }

    private fun setupGesture() {
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        gestureDetector = FullGestureDetector(
            host = binding.root,
            isForwardDx = { dx -> if (isRtl) dx > 0f else dx < 0f },
            isExcluded = { event -> isTouchExcluded(event) },
            onSwipeForward = {
                val id = fullSessionId ?: return@FullGestureDetector
                graph.onboardingFullCoordinator.onSwipeForward(id)
            },
        )
    }

    private fun isTouchExcluded(event: MotionEvent): Boolean {
        val x = if (event.rawX == 0f && event.rawY == 0f) event.x else event.rawX
        val y = if (event.rawX == 0f && event.rawY == 0f) event.y else event.rawY
        return excludedViews.any { view ->
            view.visibility == View.VISIBLE &&
                FullGestureDetector.hitTest(view, x, y)
        }
    }

    private fun deliverResultAndFinish() {
        if (!finishedOnce.compareAndSet(false, true)) return
        val fullId = fullSessionId ?: return
        val result = graph.onboardingFullCoordinator.consumeExitResult(fullId)
        if (result == null) {
            finish()
            return
        }
        val intent = Intent()
            .putExtra(OnboardingFullContract.EXTRA_SESSION_ID, result.sessionId.value)
            .putExtra(OnboardingFullContract.EXTRA_FULL_SESSION_ID, result.fullSessionId.value)
            .putExtra(OnboardingFullContract.EXTRA_FULL_INDEX, result.fullIndex)
            .putExtra(OnboardingFullContract.EXTRA_EXIT_SOURCE, result.exitSource.name)
            .putExtra(OnboardingFullContract.EXTRA_HAS_TARGET, result.targetLogicalPage != null)
        result.targetLogicalPage?.let {
            intent.putExtra(OnboardingFullContract.EXTRA_TARGET_PAGE, it)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "OnboardingFull"
    }
}
