package com.example.adsmodule.core.debug

/**
 * Process-scoped holder installed by the host Application graph.
 */
public object AdsDebugApiProvider {
    @Volatile
    private var instance: AdsDebugApi? = null

    public fun install(api: AdsDebugApi) {
        instance = api
        api.start()
    }

    public fun get(): AdsDebugApi =
        instance ?: error("AdsDebugApi not installed. Call AdsDebugApiProvider.install(...) first.")

    public fun getOrNull(): AdsDebugApi? = instance
}
