package com.example.adsmodule.core.onboarding

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.normal.NormalScreenBindSession
import com.example.adsmodule.core.normal.NormalScreenSlotState
import com.example.adsmodule.core.onboarding.full.FullExitSource
import com.example.adsmodule.core.storage.OnboardingScreenInstances

/** Logical onboarding page numbers are always 1..4. */
public object OnboardingPages {
    public const val COUNT: Int = 4
    public val ALL: IntRange = 1..COUNT

    public fun requireValid(page: Int): Int {
        require(page in ALL) { "Onboarding page must be 1..4, was $page" }
        return page
    }
}

public object OnboardingConfigKeys {
    public val NATIVE: ConfigKey = ConfigKey("native_onboarding_config_1")
    public val FULL1: ConfigKey = ConfigKey("native_onb_full_config_1")
    public val FULL2: ConfigKey = ConfigKey("native_onb_full_2_config_1")
    public val FULL1_TIMING: ConfigKey = ConfigKey("native_onb_full_1_config_2")
    public val FULL2_TIMING: ConfigKey = ConfigKey("native_onb_full_2_config_2")
}

public data class OnboardingPageModel(
    val logicalPage: Int,
    val screenInstanceId: ScreenInstanceId,
    val screenEnabled: Boolean,
    val adsEnabled: Boolean,
) {
    init {
        OnboardingPages.requireValid(logicalPage)
    }

    public val isActive: Boolean get() = screenEnabled
}

public data class OnboardingPagePolicy(
    val pages: List<OnboardingPageModel>,
) {
    public val activePages: List<Int> =
        pages.filter { it.isActive }.map { it.logicalPage }

    public fun page(logicalPage: Int): OnboardingPageModel =
        pages.first { it.logicalPage == logicalPage }

    public fun isScreenEnabled(logicalPage: Int): Boolean =
        page(logicalPage).screenEnabled

    public fun isAdsEnabled(logicalPage: Int): Boolean =
        page(logicalPage).let { it.screenEnabled && it.adsEnabled }

    public fun screenInstanceId(logicalPage: Int): ScreenInstanceId =
        OnboardingScreenInstances.page(logicalPage)
}

public enum class OnboardingNavigationEffect {
    OPEN_FULL1,
    OPEN_FULL2,
    OPEN_HOME,
}

public data class OnboardingPendingFull(
    val fullIndex: Int,
    val fullSessionId: FullSessionId,
    val targetLogicalPage: Int?,
) {
    init {
        require(fullIndex == 1 || fullIndex == 2) { "fullIndex must be 1 or 2" }
    }
}

/**
 * Saved navigation state for Activity recreation.
 * [activePages] is frozen for the session so mid-session config refresh cannot reshape the pager.
 */
public data class OnboardingSavedState(
    val sessionId: OnboardingSessionId,
    val activePages: List<Int>,
    val currentLogicalPage: Int,
    val pendingTargetLogicalPage: Int? = null,
    val full1Completed: Boolean = false,
    val full2Completed: Boolean = false,
    val pendingFull: OnboardingPendingFull? = null,
    val pendingEffect: OnboardingNavigationEffect? = null,
    val claimedEffects: Set<OnboardingNavigationEffect> = emptySet(),
) {
    init {
        require(activePages.isNotEmpty()) { "activePages must not be empty" }
        require(activePages.all { it in OnboardingPages.ALL }) {
            "activePages must be logical 1..4"
        }
        require(activePages == activePages.distinct().sorted()) {
            "activePages must be distinct and ascending"
        }
        require(currentLogicalPage in activePages) {
            "currentLogicalPage $currentLogicalPage must be in activePages"
        }
    }
}

public data class OnboardingFlowSnapshot(
    val sessionId: OnboardingSessionId,
    val activePages: List<Int>,
    val currentLogicalPage: Int,
    val pendingTargetLogicalPage: Int? = null,
    val full1Completed: Boolean = false,
    val full2Completed: Boolean = false,
    val pendingFull: OnboardingPendingFull? = null,
    val pendingEffect: OnboardingNavigationEffect? = null,
    val pageAds: Map<Int, NormalScreenSlotState> = emptyMap(),
    val debugMessage: String? = null,
) {
    public fun toSavedState(
        claimedEffects: Set<OnboardingNavigationEffect>,
    ): OnboardingSavedState = OnboardingSavedState(
        sessionId = sessionId,
        activePages = activePages,
        currentLogicalPage = currentLogicalPage,
        pendingTargetLogicalPage = pendingTargetLogicalPage,
        full1Completed = full1Completed,
        full2Completed = full2Completed,
        pendingFull = pendingFull,
        pendingEffect = pendingEffect,
        claimedEffects = claimedEffects,
    )

    public val currentAdapterIndex: Int
        get() = activePages.indexOf(currentLogicalPage).also {
            require(it >= 0) { "currentLogicalPage not in activePages" }
        }
}

public data class OnboardingFullResult(
    val sessionId: OnboardingSessionId,
    val fullSessionId: FullSessionId,
    val fullIndex: Int,
    val targetLogicalPage: Int?,
    val exitSource: FullExitSource = FullExitSource.CLOSE_X,
)

public data class OnboardingBoundAd(
    val logicalPage: Int,
    val session: NormalScreenBindSession,
)

public sealed class OnboardingForwardResult {
    public data class MovedToPage(
        val logicalPage: Int,
    ) : OnboardingForwardResult()

    public data class LaunchFull(
        val effect: OnboardingNavigationEffect,
        val fullSessionId: FullSessionId,
        val targetLogicalPage: Int?,
    ) : OnboardingForwardResult()

    public data class OpenHome(
        val effect: OnboardingNavigationEffect = OnboardingNavigationEffect.OPEN_HOME,
    ) : OnboardingForwardResult()

    public data class Ignored(
        val reason: String,
    ) : OnboardingForwardResult()
}

public sealed class OnboardingBackwardResult {
    public data class MovedToPage(
        val logicalPage: Int,
    ) : OnboardingBackwardResult()

    public data class Ignored(
        val reason: String,
    ) : OnboardingBackwardResult()
}

public sealed class OnboardingFlowEvent {
    public abstract val sessionId: OnboardingSessionId
    public abstract val occurredAtMillis: Long

    public data class PageChanged(
        override val sessionId: OnboardingSessionId,
        val logicalPage: Int,
        override val occurredAtMillis: Long,
    ) : OnboardingFlowEvent()

    public data class EffectRequested(
        override val sessionId: OnboardingSessionId,
        val effect: OnboardingNavigationEffect,
        override val occurredAtMillis: Long,
    ) : OnboardingFlowEvent()
}
