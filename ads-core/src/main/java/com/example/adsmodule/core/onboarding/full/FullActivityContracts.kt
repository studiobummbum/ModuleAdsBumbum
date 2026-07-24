package com.example.adsmodule.core.onboarding.full

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.FullSessionId
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.OnboardingSessionId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.ShowRequestId
import com.example.adsmodule.core.onboarding.OnboardingConfigKeys
import com.example.adsmodule.core.storage.StoredAdView

public enum class FullExitSource {
    SWIPE_FORWARD,
    CLOSE_X,
    AUTO_SKIP,
    NO_FILL,
}

public enum class FullGateState {
    OPEN,
    EXITING,
    COMPLETED,
}

public enum class FullActivityPhase {
    IDLE,
    WAITING_CLOSE_DELAY,
    ACTIVE,
    EXITING,
    COMPLETED,
}

public object OnboardingFullScreenInstances {
    public val full1: ScreenInstanceId = ScreenInstanceId("NATIVE_FULL_ONBOARD#1")
    public val full2: ScreenInstanceId = ScreenInstanceId("NATIVE_FULL_ONBOARD#2")

    public fun forIndex(fullIndex: Int): ScreenInstanceId {
        require(fullIndex == 1 || fullIndex == 2) { "fullIndex must be 1 or 2" }
        return if (fullIndex == 1) full1 else full2
    }
}

public object OnboardingFullConfigKeys {
    public fun adsKey(fullIndex: Int): ConfigKey {
        require(fullIndex == 1 || fullIndex == 2) { "fullIndex must be 1 or 2" }
        return if (fullIndex == 1) OnboardingConfigKeys.FULL1 else OnboardingConfigKeys.FULL2
    }

    public fun timingKey(fullIndex: Int): ConfigKey {
        require(fullIndex == 1 || fullIndex == 2) { "fullIndex must be 1 or 2" }
        return if (fullIndex == 1) {
            OnboardingConfigKeys.FULL1_TIMING
        } else {
            OnboardingConfigKeys.FULL2_TIMING
        }
    }
}

public data class OnboardingFullSnapshot(
    val fullSessionId: FullSessionId,
    val onboardingSessionId: OnboardingSessionId,
    val fullIndex: Int,
    val targetLogicalPage: Int?,
    val phase: FullActivityPhase,
    val gateState: FullGateState,
    val closeVisible: Boolean,
    val closeEnabledAtMillis: Long?,
    val autoSkipDeadlineMillis: Long?,
    val remainingCloseDelayMillis: Long,
    val remainingAutoSkipMillis: Long?,
    val showRequestId: ShowRequestId? = null,
    val objectId: ObjectId? = null,
    val storedAd: StoredAdView? = null,
    val winningExitSource: FullExitSource? = null,
    val debugMessage: String? = null,
    val adUnavailable: Boolean = false,
)

public sealed class OnboardingFullStartResult {
    public data class Attached(
        val snapshot: OnboardingFullSnapshot,
    ) : OnboardingFullStartResult()

    public data class Skipped(
        val snapshot: OnboardingFullSnapshot,
    ) : OnboardingFullStartResult()

    public data class Rejected(
        val reason: String,
    ) : OnboardingFullStartResult()
}
