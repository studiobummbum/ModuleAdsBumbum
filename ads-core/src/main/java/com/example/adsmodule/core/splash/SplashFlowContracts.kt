package com.example.adsmodule.core.splash

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.SplashSessionId
import com.example.adsmodule.core.fullscreen.FullscreenAdKind
import com.example.adsmodule.core.fullscreen.HostedFullscreenSession
import com.example.adsmodule.core.load.WeightedLoadTerminalReason
import com.example.adsmodule.core.storage.StoredAdView
import com.example.adsmodule.sdk.AdFormat

public enum class SplashStage {
    BOOTSTRAP,
    LOADING,
    PRIMARY_SHOWING,
    NATIVE_FULL,
    LANGUAGE_LOADING,
    TERMINAL,
}

public enum class SplashSkipTimerState {
    NOT_STARTED,
    RUNNING,
    COMPLETED,
    CANCELLED,
}

public enum class SplashPlacement {
    INTER_SPLASH,
    NATIVE_SPLASH,
    BANNER_UFO,
    NATIVE_FULL_SPLASH,
}

public enum class SplashLoadStatus {
    IDLE,
    LOADING,
    READY,
    DISABLED,
    INELIGIBLE,
    FAILED,
    EXHAUSTED,
    CANCELLED,
}

public enum class SplashNavigationEffect {
    OPEN_NATIVE_FULL,
    OPEN_LANGUAGE_LOADING,
}

public data class SplashPlacementState(
    val placement: SplashPlacement,
    val configKey: ConfigKey,
    val status: SplashLoadStatus,
    val cycleId: LoadCycleId? = null,
    val storedAd: StoredAdView? = null,
    val reservationId: ReservationId? = null,
    val reason: String? = null,
    val terminalReason: WeightedLoadTerminalReason? = null,
)

public data class SplashSkipTimerSnapshot(
    val state: SplashSkipTimerState = SplashSkipTimerState.NOT_STARTED,
    val showRequestId: ShowRequestId? = null,
    val startedAtMillis: Long? = null,
    val deadlineMillis: Long? = null,
    val remainingMillis: Long? = null,
)

public data class SplashNativeFullControlSnapshot(
    val showRequestId: ShowRequestId? = null,
    val closeVisible: Boolean = false,
    val closeEnabledAtMillis: Long? = null,
    val autoSkipDeadlineMillis: Long? = null,
    val remainingCloseDelayMillis: Long? = null,
    val remainingAutoSkipMillis: Long? = null,
    val exitSource: String? = null,
)

public data class SplashFlowSnapshot(
    val sessionId: SplashSessionId,
    val screenInstanceId: ScreenInstanceId,
    val stage: SplashStage,
    val placements: Map<SplashPlacement, SplashPlacementState>,
    val primaryShowRequestId: ShowRequestId? = null,
    val primaryKind: FullscreenAdKind? = null,
    val primaryFormat: AdFormat? = null,
    val primaryObjectId: ObjectId? = null,
    val skipTimer: SplashSkipTimerSnapshot = SplashSkipTimerSnapshot(),
    val nativeFull: SplashNativeFullControlSnapshot = SplashNativeFullControlSnapshot(),
    val pendingEffect: SplashNavigationEffect? = null,
    val languageLoadingOpened: Boolean = false,
    val nativeFullOpened: Boolean = false,
    val splashScreenSkippedFlag: Boolean = false,
    val debugMessage: String? = null,
)

public data class SplashNativeFullLaunch(
    val sessionId: SplashSessionId,
    val hostedSession: HostedFullscreenSession,
    val timeDelayXButtonMillis: Long,
    val autoSkipMillis: Long,
)

public sealed class SplashFlowEvent {
    public abstract val sessionId: SplashSessionId
    public abstract val occurredAtMillis: Long

    public data class StageChanged(
        override val sessionId: SplashSessionId,
        val stage: SplashStage,
        override val occurredAtMillis: Long,
    ) : SplashFlowEvent()

    public data class EffectRequested(
        override val sessionId: SplashSessionId,
        val effect: SplashNavigationEffect,
        override val occurredAtMillis: Long,
    ) : SplashFlowEvent()
}
