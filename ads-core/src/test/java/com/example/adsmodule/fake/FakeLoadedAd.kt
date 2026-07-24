package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdRequestMetadata
import com.example.adsmodule.sdk.SdkLoadedAdHandle
import java.util.concurrent.atomic.AtomicBoolean

public class FakeLoadedAd internal constructor(
    public val objectId: String,
    public val loadRequestId: String,
    override val format: AdFormat,
    override val adUnit: String,
    public val metadata: AdRequestMetadata,
    public val createdAt: Long,
    public val fakeNetworkName: String,
    public val fakeRevenueMicros: Long,
    internal val scenarioConfig: FakeScenarioConfig,
    internal val ownerToken: Any,
    private val onDestroyed: (FakeLoadedAd) -> Unit,
) : SdkLoadedAdHandle {
    private val consumedState: AtomicBoolean = AtomicBoolean(false)
    private val destroyedState: AtomicBoolean = AtomicBoolean(false)

    public val sourceConfigKey: String
        get() = metadata.sourceConfigKey

    public val sourceListIndex: Int
        get() = metadata.sourceListIndex

    public val consumed: Boolean
        get() = consumedState.get()

    public val destroyed: Boolean
        get() = destroyedState.get()

    internal fun tryConsume(): Boolean = consumedState.compareAndSet(false, true)

    override fun destroy() {
        if (destroyedState.compareAndSet(false, true)) {
            onDestroyed(this)
        }
    }
}
