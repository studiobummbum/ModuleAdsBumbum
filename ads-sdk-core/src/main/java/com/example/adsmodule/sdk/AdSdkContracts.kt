package com.example.adsmodule.sdk

import kotlinx.coroutines.flow.Flow

public enum class AdFormat {
    INTERSTITIAL,
    APP_OPEN,
    NATIVE,
    BANNER,
    NATIVE_FULLSCREEN,
}

/**
 * Releases resources owned by an SDK-backed object.
 *
 * Implementations must make repeated calls safe.
 */
public fun interface AdDestroyable {
    public fun destroy()
}

/**
 * Opaque SDK-owned ad object. Core modules must not unwrap this handle.
 */
public interface SdkLoadedAdHandle : AdDestroyable {
    public val format: AdFormat
    public val adUnit: String
}

/**
 * Identifies the original Remote Config item without coupling SDK adapters to ads-core.
 */
public data class AdRequestMetadata(
    val sourceConfigKey: String,
    val sourceListIndex: Int,
) {
    init {
        require(sourceConfigKey.isNotBlank()) { "sourceConfigKey must not be blank" }
        require(sourceListIndex >= 0) { "sourceListIndex must not be negative" }
    }
}

public data class AdLoadRequest(
    val loadRequestId: String,
    val format: AdFormat,
    val adUnit: String,
    val timeoutMillis: Long?,
    val metadata: AdRequestMetadata,
) {
    init {
        require(loadRequestId.isNotBlank()) { "loadRequestId must not be blank" }
        require(adUnit.isNotBlank()) { "adUnit must not be blank" }
        require(timeoutMillis == null || timeoutMillis >= 0L) {
            "timeoutMillis must not be negative"
        }
    }
}

public sealed interface AdLoadResult {
    public data class Success(
        val handle: SdkLoadedAdHandle,
    ) : AdLoadResult

    public data class Failure(
        val reason: String,
        val retryable: Boolean = false,
    ) : AdLoadResult

    public data object Timeout : AdLoadResult
}

public data class AdShowRequest(
    val showRequestId: String,
    val handle: SdkLoadedAdHandle,
) {
    init {
        require(showRequestId.isNotBlank()) { "showRequestId must not be blank" }
    }
}

public sealed interface AdShowEvent {
    public val showRequestId: String

    public data class Impression(
        override val showRequestId: String,
    ) : AdShowEvent

    public data class Click(
        override val showRequestId: String,
    ) : AdShowEvent

    public data class Dismiss(
        override val showRequestId: String,
    ) : AdShowEvent

    public data class Fail(
        override val showRequestId: String,
        val reason: String,
    ) : AdShowEvent
}

/**
 * SDK-neutral boundary implemented by concrete ad SDK modules.
 */
public interface AdSdkAdapter {
    public val supportedFormats: Set<AdFormat>

    public suspend fun load(request: AdLoadRequest): AdLoadResult

    public fun show(request: AdShowRequest): Flow<AdShowEvent>
}
