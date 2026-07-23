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

/** RC-001: curly/smart quotes (U+201C/U+201D) that must fail JSON parse. */
internal const val FIXTURE_CURLY_QUOTE_JSON: String =
    "{\u201Cenable\u201D:true,\"list_ads\":[{\"enable_ad\":true,\"weight\":1,\"adunit\":\"unit\"}]}"

/** RC-001: missing comma after weight field. */
internal const val FIXTURE_MISSING_COMMA_JSON: String =
    """{"enable":true,"list_ads":[{"enable_ad":true,"weight":1"adunit":"unit"}]}"""

/** Mixed inter → appopen → native in one list_ads (weights 100/90/80). */
internal val FIXTURE_MIXED_INTER_APPOPEN_NATIVE_JSON: String =
    """
    {
      "enable": true,
      "isOrganic": true,
      "timeout_total": 30000,
      "list_ads": [
        {"enable_ad":true,"weight":100,"timeout":1000,"type":"inter","adunit":"inter-unit"},
        {"enable_ad":true,"weight":90,"timeout":1000,"type":"appopen","adunit":"appopen-unit"},
        {"enable_ad":true,"weight":80,"timeout":1000,"type":"native","adunit":"native-unit"}
      ]
    }
    """.trimIndent()

internal const val FIXTURE_SPLASH_SKIP_ENABLED_JSON: String =
    """{"enable":true,"isOrganic":false,"time_skip":8000}"""

internal const val FIXTURE_SPLASH_SKIP_DISABLED_JSON: String =
    """{"enable":false,"isOrganic":false,"time_skip":8000}"""
