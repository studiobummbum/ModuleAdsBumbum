package com.example.adsdemo.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.adsdemo.AdsDemoGraph
import com.example.adsmodule.core.LanguageSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.onboarding.OnboardingBackwardResult
import com.example.adsmodule.core.onboarding.OnboardingConfigPolicy
import com.example.adsmodule.core.onboarding.OnboardingForwardResult
import com.example.adsmodule.core.onboarding.OnboardingFullResult
import com.example.adsmodule.core.onboarding.OnboardingNavigationEffect
import com.example.adsmodule.core.onboarding.OnboardingSavedState
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val graph: AdsDemoGraph,
    private val languageSessionId: String?,
    private val languageTag: String?,
    private val restoredState: OnboardingSavedState?,
) : ViewModel() {
    val snapshot = graph.onboardingCoordinator.snapshot
    val languageTagValue: String? = languageTag

    private var started = false

    init {
        viewModelScope.launch {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        if (started && graph.onboardingCoordinator.snapshot.value != null) return
        started = true
        viewModelScope.launch {
            val config = graph.configRepository.snapshots.value
                ?: run {
                    graph.configRepository.refresh()
                    graph.configRepository.snapshots.value
                }
            val policy = OnboardingConfigPolicy.resolveOrDefault(
                snapshot = config,
                audience = graph.audience,
            )
            graph.onboardingAds.refreshPolicy(config)
            val current = graph.onboardingCoordinator.snapshot.value
            when {
                restoredState != null &&
                    current != null &&
                    current.sessionId == restoredState.sessionId -> {
                    // Process-scoped coordinator already holds this session.
                }
                restoredState != null -> {
                    graph.onboardingCoordinator.restore(restoredState)
                }
                else -> {
                    graph.onboardingCoordinator.startOrRestore(policy, existing = null)
                }
            }
            notifyLanguageFlowOpened()
            val page = graph.onboardingCoordinator.snapshot.value?.currentLogicalPage
            if (page != null) {
                graph.onboardingAds.onPageVisible(page)
            }
        }
    }

    fun sessionIdOrNull(): OnboardingSessionId? = snapshot.value?.sessionId

    fun exportSavedState(): OnboardingSavedState? =
        graph.onboardingCoordinator.exportSavedState()

    fun requestForward(): OnboardingForwardResult {
        val sessionId = sessionIdOrNull()
            ?: return OnboardingForwardResult.Ignored("no session")
        return graph.onboardingCoordinator.requestForward(sessionId)
    }

    fun requestBackward(): OnboardingBackwardResult {
        val sessionId = sessionIdOrNull()
            ?: return OnboardingBackwardResult.Ignored("no session")
        return graph.onboardingCoordinator.requestBackward(sessionId)
    }

    fun claimEffect(effect: OnboardingNavigationEffect): Boolean {
        val sessionId = sessionIdOrNull() ?: return false
        return graph.onboardingCoordinator.claimEffect(sessionId, effect)
    }

    fun onFullResult(result: OnboardingFullResult): Boolean =
        graph.onboardingCoordinator.onFullResult(result)

    fun onPageVisible(logicalPage: Int) {
        graph.onboardingAds.onPageVisible(logicalPage)
    }

    /** Keep sticky native on screen but kick a background reload when returning via back/swipe. */
    fun reloadNativeOnReturn(logicalPage: Int) {
        graph.onboardingAds.reloadPageKeepingVisible(logicalPage)
    }

    suspend fun bindPage(logicalPage: Int): NormalScreenBindResult =
        graph.onboardingAds.bindPage(logicalPage)

    fun unbindPage(logicalPage: Int) {
        graph.onboardingAds.unbindPage(logicalPage, NormalScreenUnbindMode.CONSUME)
    }

    fun boundObjectId(logicalPage: Int): String? =
        graph.onboardingAds.boundAd(logicalPage)?.session?.objectId?.value

    private fun notifyLanguageFlowOpened() {
        val languageSession = languageSessionId?.let(::LanguageSessionId) ?: return
        graph.languageCoordinator.onOnboardingOpened(languageSession)
    }

    companion object {
        fun factory(
            graph: AdsDemoGraph,
            languageSessionId: String?,
            languageTag: String?,
            restoredState: OnboardingSavedState?,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(
                    graph = graph,
                    languageSessionId = languageSessionId,
                    languageTag = languageTag,
                    restoredState = restoredState,
                ) as T
            }
        }
    }
}
