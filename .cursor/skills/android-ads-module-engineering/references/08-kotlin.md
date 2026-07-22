# Kotlin mẫu

```kotlin
@Serializable
data class OriginalAdsConfig(
    val enable: Boolean,
    val isOrganic: Boolean? = null,
    @SerialName("timeout_total")
    val timeoutTotal: Long? = null,
    @SerialName("type_layout")
    val typeLayout: String? = null,
    @SerialName("list_ads")
    val listAds: List<OriginalAdItem> = emptyList(),
)

@Serializable
data class OriginalAdItem(
    @SerialName("enable_ad")
    val enableAd: Boolean,
    val weight: Int,
    val timeout: Long? = null,
    val type: String? = null,
    val adunit: String,
)
```

```kotlin
val runtimeItems = config.listAds
    .mapIndexed { index, item -> RuntimeAdItem(index, item) }
    .filter { it.item.enableAd }
    .sortedWith(
        compareByDescending<RuntimeAdItem> { it.item.weight }
            .thenBy { it.originalIndex }
    )
```

```kotlin
data class StoredAd<T>(
    val objectId: String,
    val sourceConfigKey: String,
    val sourceListIndex: Int,
    val sourceType: String?,
    val sourceAdunit: String,
    val sourceWeight: Int,
    val screenInstanceId: String?,
    val value: T,
)
```
