package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey

public interface ConfigDataSource {
    public suspend fun read(key: ConfigKey): String?
}
