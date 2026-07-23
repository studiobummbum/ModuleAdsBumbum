package com.example.adsmodule.core.language

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.LanguageSessionId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.normal.NormalScreenBindSession
import com.example.adsmodule.core.normal.NormalScreenSlotState
import com.example.adsmodule.core.storage.OnboardingScreenInstances

public enum class LanguageStage {
    BOOTSTRAP,
    LANGUAGE_LOADING,
    LANGUAGE_SELECT,
    LANGUAGE_DUP,
    APPLY_LANGUAGE,
    ONBOARDING,
    TERMINAL,
}

public enum class LanguageNavigationEffect {
    OPEN_LANGUAGE_SELECT,
    OPEN_LANGUAGE_DUP,
    OPEN_APPLY_LANGUAGE,
    OPEN_ONBOARDING,
}

public enum class LocaleApplyStatus {
    IDLE,
    APPLYING,
    SUCCEEDED,
    FAILED_FALLBACK,
}

public data class DemoLanguage(
    val tag: String,
    val displayName: String,
) {
    init {
        require(tag.isNotBlank()) { "language tag must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}

public data class LanguageTimerSnapshot(
    val startedAtMillis: Long? = null,
    val deadlineMillis: Long? = null,
    val remainingMillis: Long? = null,
    val completed: Boolean = false,
)

public data class LanguagePlacementSnapshot(
    val loading: NormalScreenSlotState? = null,
    val select: NormalScreenSlotState? = null,
    val dup: NormalScreenSlotState? = null,
)

public data class LanguageFlowSnapshot(
    val sessionId: LanguageSessionId,
    val stage: LanguageStage,
    val loadingScreenId: ScreenInstanceId,
    val selectScreenId: ScreenInstanceId,
    val dupScreenId: ScreenInstanceId,
    val selectedLanguage: DemoLanguage? = null,
    val loadingTimer: LanguageTimerSnapshot = LanguageTimerSnapshot(),
    val applyTimer: LanguageTimerSnapshot = LanguageTimerSnapshot(),
    val localeStatus: LocaleApplyStatus = LocaleApplyStatus.IDLE,
    val localeMessage: String? = null,
    val placements: LanguagePlacementSnapshot = LanguagePlacementSnapshot(),
    val pendingEffect: LanguageNavigationEffect? = null,
    val onboardingPreloadStarted: Boolean = false,
    val languagePreloadStarted: Boolean = false,
    val debugMessage: String? = null,
)

public data class LanguageBoundAd(
    val placement: LanguagePlacement,
    val session: NormalScreenBindSession,
)

public enum class LanguagePlacement {
    LOADING,
    SELECT,
    DUP,
}

public object LanguageConfigKeys {
    public val LOADING: ConfigKey = ConfigKey("native_language_loading_config_1")
    public val SELECT: ConfigKey = ConfigKey("native_language_config_1")
    public val DUP: ConfigKey = ConfigKey("native_language_dup_config_1")
    public val ONBOARDING: ConfigKey = ConfigKey("native_onboarding_config_1")
}

public object LanguageScreenInstances {
    public fun loading(sessionId: LanguageSessionId): ScreenInstanceId =
        ScreenInstanceId("language-loading-${sessionId.value}")

    public fun select(sessionId: LanguageSessionId): ScreenInstanceId =
        ScreenInstanceId("language-select-${sessionId.value}")

    public fun dup(sessionId: LanguageSessionId): ScreenInstanceId =
        ScreenInstanceId("language-dup-${sessionId.value}")

    public val onboardingPage1: ScreenInstanceId = OnboardingScreenInstances.page1
    public val onboardingPage2: ScreenInstanceId = OnboardingScreenInstances.page2
}

public object DemoLanguages {
    public val all: List<DemoLanguage> = listOf(
        DemoLanguage(tag = "en", displayName = "English"),
        DemoLanguage(tag = "vi", displayName = "Tiếng Việt"),
        DemoLanguage(tag = "es", displayName = "Español"),
        DemoLanguage(tag = "fr", displayName = "Français"),
        DemoLanguage(tag = "hi", displayName = "हिन्दी"),
        DemoLanguage(tag = "pt", displayName = "Português"),
    )

    public fun find(tag: String): DemoLanguage? =
        all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
}

public fun interface LocaleApplier {
    public suspend fun apply(languageTag: String): LocaleApplyResult
}

public sealed class LocaleApplyResult {
    public data object Success : LocaleApplyResult()

    public data class Failure(
        val reason: String,
    ) : LocaleApplyResult()
}

public sealed class LanguageFlowEvent {
    public abstract val sessionId: LanguageSessionId
    public abstract val occurredAtMillis: Long

    public data class StageChanged(
        override val sessionId: LanguageSessionId,
        val stage: LanguageStage,
        override val occurredAtMillis: Long,
    ) : LanguageFlowEvent()

    public data class EffectRequested(
        override val sessionId: LanguageSessionId,
        val effect: LanguageNavigationEffect,
        override val occurredAtMillis: Long,
    ) : LanguageFlowEvent()
}
