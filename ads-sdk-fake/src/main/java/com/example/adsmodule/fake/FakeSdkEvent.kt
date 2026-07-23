package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat

public enum class FakeCallbackOutcome {
    SUCCESS,
    FAILURE,
}

public enum class FakeIgnoredCallbackReason {
    CANCELLED,
    DUPLICATE,
    RESET,
}

public sealed interface FakeSdkEvent {
    public val occurredAtMillis: Long

    public data class LoadRequested(
        val loadRequestId: String,
        val itemKey: FakeAdItemKey,
        val format: AdFormat,
        val requestCount: Int,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class LoadCallbackAttempt(
        val loadRequestId: String,
        val attempt: Int,
        val outcome: FakeCallbackOutcome,
        val objectId: String?,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class LoadCallbackAccepted(
        val loadRequestId: String,
        val attempt: Int,
        val outcome: FakeCallbackOutcome,
        val objectId: String?,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class LoadCallbackIgnored(
        val loadRequestId: String,
        val attempt: Int,
        val outcome: FakeCallbackOutcome,
        val objectId: String?,
        val reason: FakeIgnoredCallbackReason,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class LoadCancelled(
        val loadRequestId: String,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class ShowStarted(
        val showRequestId: String,
        val objectId: String,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class ShowFailed(
        val showRequestId: String,
        val objectId: String?,
        val reason: String,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class Impression(
        val showRequestId: String,
        val objectId: String,
        val fakeNetworkName: String,
        val fakeRevenueMicros: Long,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class Click(
        val showRequestId: String,
        val objectId: String,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class Dismiss(
        val showRequestId: String,
        val objectId: String,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent

    public data class Destroyed(
        val objectId: String,
        override val occurredAtMillis: Long,
    ) : FakeSdkEvent
}
