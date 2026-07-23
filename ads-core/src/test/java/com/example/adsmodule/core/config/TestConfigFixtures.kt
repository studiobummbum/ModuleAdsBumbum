package com.example.adsmodule.core.config

import com.example.adsmodule.core.ConfigKey
import java.io.File

internal fun bundledAssetRoot(): File {
    val candidates = listOf(
        File("src/main/assets/${BundledConfigDataSource.ASSET_DIRECTORY}"),
        File("ads-core/src/main/assets/${BundledConfigDataSource.ASSET_DIRECTORY}"),
    )
    return candidates.firstOrNull(File::isDirectory)
        ?: error("Cannot locate bundled Remote Config assets from ${File(".").absolutePath}")
}

internal fun bundledRaw(key: ConfigKey): String =
    File(bundledAssetRoot(), "${key.value}.json").readText(Charsets.UTF_8)

internal fun bundledDataSource(
    overrides: Map<ConfigKey, String> = emptyMap(),
): BundledConfigDataSource = BundledConfigDataSource(
    assetReader = ConfigAssetReader { assetPath ->
        val key = ConfigKey(assetPath.substringAfterLast('/').removeSuffix(".json"))
        overrides[key] ?: File(bundledAssetRoot(), assetPath.substringAfterLast('/'))
            .takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
    },
)
