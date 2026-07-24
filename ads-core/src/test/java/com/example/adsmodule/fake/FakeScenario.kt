package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdLoadRequest

public enum class FakeScenario {
    SUCCESS,
    FAIL,
    NEVER_CALLBACK,
    DELAYED_SUCCESS,
    LATE_CALLBACK,
    DUPLICATE_CALLBACK,
    SHOW_FAIL,
}

public data class FakeAdItemKey(
    val sourceConfigKey: String,
    val sourceListIndex: Int,
    val adUnit: String,
) {
    init {
        require(sourceConfigKey.isNotBlank()) { "sourceConfigKey must not be blank" }
        require(sourceListIndex >= 0) { "sourceListIndex must not be negative" }
        require(adUnit.isNotBlank()) { "adUnit must not be blank" }
    }

    public companion object {
        public fun from(request: AdLoadRequest): FakeAdItemKey =
            FakeAdItemKey(
                sourceConfigKey = request.metadata.sourceConfigKey,
                sourceListIndex = request.metadata.sourceListIndex,
                adUnit = request.adUnit,
            )
    }
}

public data class FakeScenarioConfig(
    val scenario: FakeScenario = FakeScenario.SUCCESS,
    val loadDelayMillis: Long = 0L,
    val impressionDelayMillis: Long = 0L,
    val clickDelayMillis: Long = 0L,
    val dismissDelayMillis: Long = 0L,
    val callbackAfterCancel: Boolean = false,
    val fakeNetworkName: String = DEFAULT_NETWORK_NAME,
    val fakeRevenueMicros: Long = 0L,
) {
    init {
        require(loadDelayMillis >= 0L) { "loadDelayMillis must not be negative" }
        require(impressionDelayMillis >= 0L) {
            "impressionDelayMillis must not be negative"
        }
        require(clickDelayMillis >= 0L) { "clickDelayMillis must not be negative" }
        require(dismissDelayMillis >= 0L) { "dismissDelayMillis must not be negative" }
        require(fakeNetworkName.isNotBlank()) { "fakeNetworkName must not be blank" }
        require(fakeRevenueMicros >= 0L) { "fakeRevenueMicros must not be negative" }
    }

    public companion object {
        public const val DEFAULT_NETWORK_NAME: String = "fake-network"
    }
}
