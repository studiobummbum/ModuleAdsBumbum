package com.example.adsmodule.core.debug

import com.example.adsmodule.core.ConfigKey
import com.example.adsmodule.core.OriginalAdItem
import com.example.adsmodule.core.OriginalAdsConfig
import com.example.adsmodule.core.config.AdsConfigSnapshot
import com.example.adsmodule.core.config.ConfigKeyRegistry
import com.example.adsmodule.core.config.ConfigValueKind
import com.example.adsmodule.core.load.RuntimeAdItem
import com.example.adsmodule.core.load.WeightedLoadDebugState

/**
 * Read-only placement views: original list_ads vs runtime weight order.
 */
public object PlacementDebugInspector {
    public fun placements(snapshot: AdsConfigSnapshot?): List<PlacementDebugView> {
        if (snapshot == null) return emptyList()
        return ConfigKeyRegistry.descriptors
            .filter { it.kind == ConfigValueKind.ADS }
            .mapNotNull { descriptor ->
                val config = snapshot.adsConfig(descriptor.key) ?: return@mapNotNull null
                PlacementDebugView(
                    configKey = descriptor.key,
                    enable = config.enable,
                    isOrganic = config.isOrganic,
                    originalItems = config.listAds.mapIndexed { index, item ->
                        OriginalItemView(
                            originalIndex = if (item.sourceListIndex >= 0) {
                                item.sourceListIndex
                            } else {
                                index
                            },
                            enableAd = item.enableAd,
                            weight = item.weight,
                            type = item.type,
                            adunit = item.adunit,
                            timeoutMillis = item.timeoutMillis,
                        )
                    },
                    runtimeOrder = runtimeOrder(descriptor.key, config),
                )
            }
    }

    public fun runtimeOrder(
        configKey: ConfigKey,
        config: OriginalAdsConfig,
    ): List<RuntimeAdItem> =
        config.listAds
            .mapIndexed { index, item -> toRuntimeItem(configKey, index, item) }
            .filter { it.enableAd }
            .sortedWith(
                compareByDescending<RuntimeAdItem> { it.weight }
                    .thenBy { it.originalIndex },
            )

    public fun weightedSummary(
        debugStates: Map<*, WeightedLoadDebugState>,
    ): List<WeightedLoadDebugState> = debugStates.values.toList()

    private fun toRuntimeItem(
        configKey: ConfigKey,
        mappedIndex: Int,
        item: OriginalAdItem,
    ): RuntimeAdItem {
        val originalIndex = if (item.sourceListIndex >= 0) {
            item.sourceListIndex
        } else {
            mappedIndex
        }
        return RuntimeAdItem(
            originalIndex = originalIndex,
            enableAd = item.enableAd,
            weight = item.weight,
            timeoutMillis = item.timeoutMillis,
            type = item.type,
            adunit = item.adunit,
            resolvedFormat = ConfigKeyRegistry.resolveAdFormat(configKey, item.type),
        )
    }
}

public data class PlacementDebugView(
    val configKey: ConfigKey,
    val enable: Boolean,
    val isOrganic: Boolean?,
    val originalItems: List<OriginalItemView>,
    val runtimeOrder: List<RuntimeAdItem>,
)

public data class OriginalItemView(
    val originalIndex: Int,
    val enableAd: Boolean,
    val weight: Int,
    val type: String?,
    val adunit: String,
    val timeoutMillis: Long?,
)
