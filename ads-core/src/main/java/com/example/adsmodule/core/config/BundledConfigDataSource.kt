package com.example.adsmodule.core.config

import android.content.res.AssetManager
import com.example.adsmodule.core.ConfigKey

public fun interface ConfigAssetReader {
    public fun read(assetPath: String): String?
}

public class BundledConfigDataSource(
    private val assetReader: ConfigAssetReader,
    private val registry: ConfigKeyRegistry = ConfigKeyRegistry,
) : ConfigDataSource {
    override suspend fun read(key: ConfigKey): String? {
        val descriptor = registry.descriptor(key) ?: return null
        return assetReader.read("$ASSET_DIRECTORY/${descriptor.assetFileName}")
    }

    public companion object {
        public const val ASSET_DIRECTORY: String = "original-remote-config"

        public fun fromAssetManager(
            assetManager: AssetManager,
            registry: ConfigKeyRegistry = ConfigKeyRegistry,
        ): BundledConfigDataSource = BundledConfigDataSource(
            assetReader = ConfigAssetReader { assetPath ->
                runCatching {
                    assetManager.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull()
            },
            registry = registry,
        )
    }
}
