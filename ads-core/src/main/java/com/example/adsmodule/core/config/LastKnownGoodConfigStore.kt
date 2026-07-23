package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey

public interface LastKnownGoodConfigStore {
    public suspend fun read(key: ConfigKey): String?

    public suspend fun writeAll(values: Map<ConfigKey, String>)
}

public class InMemoryLastKnownGoodConfigStore(
    initialValues: Map<ConfigKey, String> = emptyMap(),
) : LastKnownGoodConfigStore {
    private val lock: Any = Any()
    private val values: MutableMap<ConfigKey, String> = initialValues.toMutableMap()

    override suspend fun read(key: ConfigKey): String? = synchronized(lock) {
        values[key]
    }

    override suspend fun writeAll(values: Map<ConfigKey, String>) {
        synchronized(lock) {
            this.values.putAll(values)
        }
    }

    public fun snapshot(): Map<ConfigKey, String> = synchronized(lock) {
        values.toMap()
    }
}
