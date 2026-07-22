# Mẫu kiến trúc Kotlin

Các đoạn dưới là mẫu định hướng, phải thích nghi với SDK và kiến trúc dự án.

## Model cấu hình

```kotlin
data class CandidateConfig(
    val id: String,
    val enabled: Boolean,
    val tier: String?,
    val adUnitId: String,
    val loadTimeoutMs: Long? = null,
)

data class AudienceConfig(
    val paid: Boolean,
    val organic: Boolean,
    val unknown: Boolean,
)

data class PlacementConfig(
    val placementId: String,
    val enabled: Boolean,
    val audience: AudienceConfig,
    val weight: Int? = null,
    val loadStrategy: LoadStrategy,
    val totalTimeoutMs: Long? = null,
    val candidates: List<CandidateConfig> = emptyList(),
)

enum class LoadStrategy {
    SEQUENTIAL_LIST_UNTIL_SUCCESS,
    SEQUENTIAL_LIST_WITH_TIMEOUT,
}
```

## Placement key

```kotlin
@JvmInline
value class PlacementId(val value: String)

@JvmInline
value class PlacementInstanceId(val value: String)
```

## State

```kotlin
sealed interface AdSlotState {
    data object Disabled : AdSlotState
    data object Idle : AdSlotState

    data class Loading(
        val cycleId: String,
        val requestId: String,
        val candidateIndex: Int,
    ) : AdSlotState

    data class Ready(
        val objectId: String,
        val placementInstanceId: PlacementInstanceId,
        val weight: Int,
        val loadedAtMs: Long,
        val expiresAtMs: Long?,
    ) : AdSlotState

    data class Reserved(
        val objectId: String,
        val reservationId: String,
    ) : AdSlotState

    data class Showing(val objectId: String) : AdSlotState
    data object Consumed : AdSlotState
    data class Failed(val error: Throwable?, val retryAtMs: Long?) : AdSlotState
    data object Expired : AdSlotState
}
```

## SDK adapter

```kotlin
interface AdSdkLoader<T : Any> {
    suspend fun load(candidate: CandidateConfig): Result<T>
    fun destroy(ad: T)
}
```

Đối với Splash timeout, nên đặt timeout ở orchestration layer thay vì làm thay đổi adapter của mọi format.

## Sequential loader

```kotlin
class SequentialPlacementLoader<T : Any>(
    private val sdkLoader: AdSdkLoader<T>,
    private val clock: Clock,
) {
    suspend fun load(
        config: PlacementConfig,
        isRequestActive: (requestId: String) -> Boolean,
    ): Result<LoadedAd<T>> {
        val candidates = config.candidates.filter { it.enabled }

        for ((index, candidate) in candidates.withIndex()) {
            val requestId = newRequestId()

            val result = if (config.loadStrategy == LoadStrategy.SEQUENTIAL_LIST_WITH_TIMEOUT) {
                val timeout = requireNotNull(candidate.loadTimeoutMs)
                withTimeoutOrNull(timeout) {
                    sdkLoader.load(candidate)
                } ?: Result.failure(CandidateTimeoutException(candidate.id))
            } else {
                sdkLoader.load(candidate)
            }

            if (!isRequestActive(requestId)) {
                result.getOrNull()?.let(sdkLoader::destroy)
                continue
            }

            if (result.isSuccess) {
                return Result.success(
                    LoadedAd(
                        value = result.getOrThrow(),
                        candidateId = candidate.id,
                        candidateIndex = index,
                    )
                )
            }
        }

        return Result.failure(AllCandidatesFailedException(config.placementId))
    }
}
```

Không copy nguyên mẫu nếu request identity của dự án được quản lý khác.

## Inventory

```kotlin
data class InventoryState<T : Any>(
    val placementInstanceId: PlacementInstanceId,
    val weight: Int,
    val targetReadyCount: Int,
    val ready: List<StoredAd<T>>,
    val inFlightCount: Int,
    val active: Boolean,
) {
    val deficit: Int
        get() = (targetReadyCount - ready.size - inFlightCount).coerceAtLeast(0)
}
```

## Atomic reserve

```kotlin
suspend fun reserveForTurnback(): BorrowedAd? = mutex.withLock {
    val winner = inventory.values
        .asSequence()
        .filter { it.active && it.ready.isNotEmpty() }
        .maxWithOrNull(
            compareBy<InventoryState<*>> { it.weight }
                .thenByDescending { it.ready.first().loadedAtMs }
                .thenBy { it.placementInstanceId.value }
        )
        ?: return@withLock null

    val borrowed = popReadyObject(winner.placementInstanceId)
    val newState = inventory.getValue(winner.placementInstanceId)

    if (newState.deficit > 0 && newState.inFlightCount == 0) {
        refillQueue.enqueue(
            placementInstanceId = winner.placementInstanceId,
            priority = winner.weight,
        )
    }

    borrowed
}
```

Đảm bảo pop, deficit và enqueue nằm trong cùng vùng đồng bộ.

## Fullscreen lock

```kotlin
interface FullscreenShowLock {
    suspend fun tryAcquire(owner: PlacementInstanceId, requestId: String): Boolean
    suspend fun release(requestId: String)
}
```

Release phải idempotent.
