package com.example.adsmodule.core.normal

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.LoadCycleId
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.load.WeightedLoadTerminalReason
import com.example.adsmodule.core.storage.StoredAdView
import java.util.concurrent.atomic.AtomicBoolean

public enum class NormalScreenLoadStatus {
    IDLE,
    LOADING,
    READY,
    BOUND,
    DISABLED,
    INELIGIBLE,
    FAILED,
    EXHAUSTED,
    CANCELLED,
}

public data class NormalScreenSlotState(
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId,
    val status: NormalScreenLoadStatus = NormalScreenLoadStatus.IDLE,
    val cycleId: LoadCycleId? = null,
    val storedAd: StoredAdView? = null,
    val reservationId: ReservationId? = null,
    val reason: String? = null,
    val terminalReason: WeightedLoadTerminalReason? = null,
)

public data class NormalScreenBindSession(
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId,
    val reservationId: ReservationId,
    val objectId: ObjectId,
    val storedAd: StoredAdView,
    val boundAtMillis: Long,
    internal val finished: AtomicBoolean = AtomicBoolean(false),
)

public sealed class NormalScreenEnsureResult {
    public data class Ready(
        val state: NormalScreenSlotState,
    ) : NormalScreenEnsureResult()

    public data class Terminal(
        val state: NormalScreenSlotState,
    ) : NormalScreenEnsureResult()
}

public sealed class NormalScreenBindResult {
    public data class Bound(
        val session: NormalScreenBindSession,
        val state: NormalScreenSlotState,
        /** Prior SHOWING session to CONSUME only after the UI has swapped to [session]. */
        val previousSession: NormalScreenBindSession? = null,
    ) : NormalScreenBindResult()

    public data class Rejected(
        val reason: String,
        val state: NormalScreenSlotState?,
    ) : NormalScreenBindResult()
}

public sealed class NormalScreenUnbindResult {
    public data class Consumed(
        val objectId: ObjectId,
    ) : NormalScreenUnbindResult()

    public data class Released(
        val objectId: ObjectId,
    ) : NormalScreenUnbindResult()

    public data class AlreadyFinished(
        val objectId: ObjectId,
    ) : NormalScreenUnbindResult()
}

public enum class NormalScreenUnbindMode {
    CONSUME,
    RELEASE,
}
