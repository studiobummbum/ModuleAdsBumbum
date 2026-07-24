package com.example.adsdemo.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.adsdemo.AdsDemoGraph
import com.example.adsmodule.core.LanguageSessionId
import com.example.adsmodule.core.language.DemoLanguage
import com.example.adsmodule.core.language.DemoLanguages
import com.example.adsmodule.core.language.LanguageNavigationEffect
import com.example.adsmodule.core.language.LanguagePlacement
import kotlinx.coroutines.launch

class LanguageFlowViewModel(
    private val graph: AdsDemoGraph,
    private val savedSessionId: String?,
    private val savedLanguageTag: String?,
    private val startLoadingTimer: Boolean,
) : ViewModel() {
    val snapshot = graph.languageCoordinator.snapshot

    val languages: List<DemoLanguage> = DemoLanguages.all

    init {
        viewModelScope.launch {
            val config = graph.configRepository.snapshots.value
                ?: run {
                    graph.configRepository.refresh()
                    graph.configRepository.snapshots.value
                }
            if (config != null) {
                val existing = savedSessionId?.let(::LanguageSessionId)
                val sessionId = if (startLoadingTimer) {
                    graph.languageCoordinator.startOrAttach(config, existing)
                } else {
                    graph.languageCoordinator.attach(config, existing)
                }
                if (!savedLanguageTag.isNullOrBlank()) {
                    graph.languageCoordinator.restoreSelectedLanguage(sessionId, savedLanguageTag)
                }
            }
        }
    }

    fun sessionIdOrNull(): LanguageSessionId? = snapshot.value?.sessionId

    fun selectLanguage(language: DemoLanguage): Boolean {
        val sessionId = sessionIdOrNull() ?: return false
        return graph.languageCoordinator.selectLanguage(sessionId, language)
    }

    fun onLanguageSelectOpened() {
        val sessionId = sessionIdOrNull() ?: return
        graph.languageCoordinator.onLanguageSelectOpened(sessionId)
    }

    fun onLanguageDupOpened() {
        val sessionId = sessionIdOrNull() ?: return
        graph.languageCoordinator.onLanguageDupOpened(sessionId)
    }

    fun onLanguageDupNext(): Boolean {
        val sessionId = sessionIdOrNull() ?: return false
        return graph.languageCoordinator.onLanguageDupNext(sessionId)
    }

    fun onApplyLanguageOpened() {
        val sessionId = sessionIdOrNull() ?: return
        graph.languageCoordinator.onApplyLanguageOpened(sessionId)
    }

    fun onOnboardingOpened() {
        val sessionId = sessionIdOrNull() ?: return
        graph.languageCoordinator.onOnboardingOpened(sessionId)
    }

    fun claimEffect(effect: LanguageNavigationEffect): Boolean {
        val sessionId = sessionIdOrNull() ?: return false
        return graph.languageCoordinator.claimEffect(sessionId, effect)
    }

    fun boundAd(placement: LanguagePlacement) = graph.languageCoordinator.boundAd(placement)

    /** Ask the coordinator to swap in a newer READY native if one is available. */
    fun tryReplaceBound(placement: LanguagePlacement) {
        graph.languageCoordinator.tryReplaceBound(placement)
    }

    companion object {
        fun factory(
            graph: AdsDemoGraph,
            savedSessionId: String?,
            savedLanguageTag: String?,
            startLoadingTimer: Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LanguageFlowViewModel(
                    graph = graph,
                    savedSessionId = savedSessionId,
                    savedLanguageTag = savedLanguageTag,
                    startLoadingTimer = startLoadingTimer,
                ) as T
            }
        }
    }
}
