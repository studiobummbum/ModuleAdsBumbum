package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdLoadRequest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

public fun interface FakeClock {
    public fun nowMillis(): Long
}

public fun interface FakeObjectIdGenerator {
    public fun nextObjectId(): String
}

public class SequentialFakeObjectIdGenerator(
    private val prefix: String = "fake-object",
) : FakeObjectIdGenerator {
    private val nextValue: AtomicLong = AtomicLong(0L)

    init {
        require(prefix.isNotBlank()) { "prefix must not be blank" }
    }

    override fun nextObjectId(): String = "$prefix-${nextValue.incrementAndGet()}"
}

public class FakeAdsSdkController(
    public val clock: FakeClock = FakeClock(System::currentTimeMillis),
    internal val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val objectIdGenerator: FakeObjectIdGenerator = SequentialFakeObjectIdGenerator(),
    private val defaultScenario: FakeScenarioConfig = FakeScenarioConfig(),
) {
    private val ownerToken: Any = Any()
    private val generation: AtomicLong = AtomicLong(0L)
    private val controllerScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val scenarios: ConcurrentHashMap<FakeAdItemKey, FakeScenarioConfig> =
        ConcurrentHashMap()
    private val requestCounters: ConcurrentHashMap<FakeAdItemKey, AtomicInteger> =
        ConcurrentHashMap()
    private val activeLoadCancellations: ConcurrentHashMap<String, () -> Unit> =
        ConcurrentHashMap()
    private val pendingCallbackJobs: MutableSet<Job> =
        Collections.newSetFromMap(ConcurrentHashMap())
    private val ownedHandles: ConcurrentHashMap<String, FakeLoadedAd> = ConcurrentHashMap()
    private val eventHistory: MutableList<FakeSdkEvent> =
        Collections.synchronizedList(mutableListOf())
    private val mutableEvents: MutableSharedFlow<FakeSdkEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    public val events: SharedFlow<FakeSdkEvent> = mutableEvents.asSharedFlow()

    internal val engine: FakeAdapterEngine = FakeAdapterEngine(this)

    public fun setScenario(
        itemKey: FakeAdItemKey,
        config: FakeScenarioConfig,
    ) {
        scenarios[itemKey] = config
    }

    public fun clearScenario(itemKey: FakeAdItemKey) {
        scenarios.remove(itemKey)
    }

    public fun scenarioFor(itemKey: FakeAdItemKey): FakeScenarioConfig =
        scenarios[itemKey] ?: defaultScenario

    public fun requestCount(itemKey: FakeAdItemKey): Int =
        requestCounters[itemKey]?.get() ?: 0

    public fun eventsSnapshot(): List<FakeSdkEvent> =
        synchronized(eventHistory) {
            eventHistory.toList()
        }

    public fun handlesSnapshot(): List<FakeLoadedAd> = ownedHandles.values.toList()

    public fun reset() {
        generation.incrementAndGet()

        activeLoadCancellations.values.toList().forEach { cancel -> cancel() }
        pendingCallbackJobs.toList().forEach(Job::cancel)
        ownedHandles.values.toSet().forEach(FakeLoadedAd::destroy)

        activeLoadCancellations.clear()
        pendingCallbackJobs.clear()
        ownedHandles.clear()
        scenarios.clear()
        requestCounters.clear()
        synchronized(eventHistory) {
            eventHistory.clear()
        }
    }

    internal fun currentGeneration(): Long = generation.get()

    internal fun recordLoadRequested(
        request: AdLoadRequest,
        itemKey: FakeAdItemKey,
    ): Int {
        val newCounter = AtomicInteger(0)
        val counter = requestCounters[itemKey]
            ?: requestCounters.putIfAbsent(itemKey, newCounter)
            ?: newCounter
        val count = counter.incrementAndGet()
        record(
            FakeSdkEvent.LoadRequested(
                loadRequestId = request.loadRequestId,
                itemKey = itemKey,
                format = request.format,
                requestCount = count,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
        return count
    }

    internal fun registerActiveLoad(
        loadRequestId: String,
        cancel: () -> Unit,
    ): Boolean = activeLoadCancellations.putIfAbsent(loadRequestId, cancel) == null

    internal fun unregisterActiveLoad(
        loadRequestId: String,
        cancel: () -> Unit,
    ) {
        activeLoadCancellations.remove(loadRequestId, cancel)
    }

    internal fun launchCallback(
        onCreated: (Job) -> Unit = {},
        block: suspend () -> Unit,
    ): Job {
        val job = controllerScope.launch(start = CoroutineStart.LAZY) {
            block()
        }
        pendingCallbackJobs += job
        onCreated(job)
        job.invokeOnCompletion {
            pendingCallbackJobs -= job
        }
        job.start()
        return job
    }

    internal fun createLoadedAd(
        request: AdLoadRequest,
        config: FakeScenarioConfig,
    ): FakeLoadedAd {
        val loadedAd = FakeLoadedAd(
            objectId = objectIdGenerator.nextObjectId(),
            loadRequestId = request.loadRequestId,
            format = request.format,
            adUnit = request.adUnit,
            metadata = request.metadata,
            createdAt = clock.nowMillis(),
            fakeNetworkName = config.fakeNetworkName,
            fakeRevenueMicros = config.fakeRevenueMicros,
            scenarioConfig = config,
            ownerToken = ownerToken,
            onDestroyed = ::onHandleDestroyed,
        )
        check(ownedHandles.putIfAbsent(loadedAd.objectId, loadedAd) == null) {
            "Fake object IDs must be unique: ${loadedAd.objectId}"
        }
        return loadedAd
    }

    internal fun owns(handle: FakeLoadedAd): Boolean =
        handle.ownerToken === ownerToken && ownedHandles[handle.objectId] === handle

    internal fun discard(handle: FakeLoadedAd) {
        handle.destroy()
        ownedHandles.remove(handle.objectId, handle)
    }

    internal fun record(event: FakeSdkEvent) {
        eventHistory += event
        mutableEvents.tryEmit(event)
    }

    private fun onHandleDestroyed(handle: FakeLoadedAd) {
        record(
            FakeSdkEvent.Destroyed(
                objectId = handle.objectId,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }

    private companion object {
        private const val EVENT_BUFFER_CAPACITY: Int = 64
    }
}
