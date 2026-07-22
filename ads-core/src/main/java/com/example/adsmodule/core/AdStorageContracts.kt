package com.example.adsmodule.core

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import kotlinx.coroutines.CoroutineDispatcher

public enum class AdSlotState {
    DISABLED,
    IDLE,
    LOADING,
    READY,
    RESERVED,
    SHOWING,
    CONSUMED,
    FAILED,
    EXPIRED,
}

public data class StoredAd(
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
) {
    init {
        require(sourceListIndex >= 0) { "sourceListIndex must not be negative" }
        require(sourceAdunit.isNotBlank()) { "sourceAdunit must not be blank" }
        require(sourceWeight >= 0) { "sourceWeight must not be negative" }
        require(loadedAt >= 0L) { "loadedAt must not be negative" }
    }
}

public fun interface Clock {
    public fun nowMillis(): Long
}

public fun interface IdGenerator {
    public fun nextId(): String
}

public interface DispatcherProvider {
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val main: CoroutineDispatcher
}
