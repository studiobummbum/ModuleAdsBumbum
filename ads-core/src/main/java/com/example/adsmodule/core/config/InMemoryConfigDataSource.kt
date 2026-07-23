package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey

public class InMemoryConfigDataSource(
    initialValues: Map<ConfigKey, String> = emptyMap(),
) : ConfigDataSource {
    private val lock: Any = Any()
    private val values: MutableMap<ConfigKey, String> = initialValues.toMutableMap()

    override suspend fun read(key: ConfigKey): String? = synchronized(lock) {
        values[key]
    }

    public fun set(
        key: ConfigKey,
        rawJson: String,
    ) {
        synchronized(lock) {
            values[key] = rawJson
        }
    }

    public fun remove(key: ConfigKey) {
        synchronized(lock) {
            values.remove(key)
        }
    }

    public fun replaceAll(newValues: Map<ConfigKey, String>) {
        synchronized(lock) {
            values.clear()
            values.putAll(newValues)
        }
    }

    public fun snapshot(): Map<ConfigKey, String> = synchronized(lock) {
        values.toMap()
    }
}
