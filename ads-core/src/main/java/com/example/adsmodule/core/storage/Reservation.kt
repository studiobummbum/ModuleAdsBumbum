package com.example.adsmodule.core.storage

import com.example.adsmodule.core.AdSlotState
import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.ObjectId
import com.example.adsmodule.core.ReservationId
import com.example.adsmodule.core.ScreenInstanceId
import com.example.adsmodule.core.StoredAd
import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle

public data class Reservation(
    val reservationId: ReservationId,
    val objectId: ObjectId,
    val sourceConfigKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
    val reservedAt: Long,
) {
    init {
        require(reservedAt >= 0L) { "reservedAt must not be negative" }
    }
}

public data class StorageSlotKey(
    val configKey: ConfigKey,
    val screenInstanceId: ScreenInstanceId?,
)

public sealed class PutResult {
    public data class Accepted(val storedAd: StoredAdView) : PutResult()

    public data class Rejected(val reason: String) : PutResult()
}

public sealed class ReserveResult {
    public data class Accepted(
        val storedAd: StoredAdView,
        val reservation: Reservation,
    ) : ReserveResult()

    public data class Rejected(val reason: String) : ReserveResult()
}

/**
 * Immutable read model of [StoredAd] for inspector and reserve results.
 */
public data class StoredAdView(
    val objectId: ObjectId,
    val sourceConfigKey: ConfigKey,
    val sourceListIndex: Int,
    val sourceType: AdFormat,
    val sourceAdunit: String,
    val sourceWeight: Int,
    val screenInstanceId: ScreenInstanceId?,
    val loadedAt: Long,
    val state: AdSlotState,
    val sdkHandle: SdkLoadedAdHandle,
)

internal fun StoredAd.toView(): StoredAdView =
    StoredAdView(
        objectId = objectId,
        sourceConfigKey = sourceConfigKey,
        sourceListIndex = sourceListIndex,
        sourceType = sourceType,
        sourceAdunit = sourceAdunit,
        sourceWeight = sourceWeight,
        screenInstanceId = screenInstanceId,
        loadedAt = loadedAt,
        state = state,
        sdkHandle = sdkHandle,
    )
