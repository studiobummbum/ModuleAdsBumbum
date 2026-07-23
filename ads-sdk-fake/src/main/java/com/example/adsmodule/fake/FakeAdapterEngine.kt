package com.example.adsmodule.fake

import com.example.adsmodule.sdk.AdFormat
import com.example.adsmodule.sdk.AdLoadRequest
import com.example.adsmodule.sdk.AdLoadResult
import com.example.adsmodule.sdk.AdShowEvent
import com.example.adsmodule.sdk.AdShowRequest
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield

internal class FakeAdapterEngine(
    private val controller: FakeAdsSdkController,
) {
    suspend fun load(
        adapterFormat: AdFormat,
        request: AdLoadRequest,
    ): AdLoadResult {
        if (request.format != adapterFormat) {
            return AdLoadResult.Failure(
                reason = "Adapter for $adapterFormat cannot load ${request.format}",
            )
        }

        val itemKey = FakeAdItemKey.from(request)
        val config = controller.scenarioFor(itemKey)
        val requestGeneration = controller.currentGeneration()
        controller.recordLoadRequested(request, itemKey)

        return suspendCancellableCoroutine { continuation ->
            val terminalState = AtomicReference(LoadTerminalState.ACTIVE)
            val acceptedHandle = AtomicReference<FakeLoadedAd?>(null)
            val callbackJobs: MutableSet<Job> =
                Collections.newSetFromMap(ConcurrentHashMap())
            val cancelFromReset: () -> Unit = {
                continuation.cancel(CancellationException(RESET_CANCELLATION_REASON))
            }

            if (!controller.registerActiveLoad(request.loadRequestId, cancelFromReset)) {
                continuation.resume(
                    AdLoadResult.Failure(
                        reason = "loadRequestId is already active: ${request.loadRequestId}",
                    ),
                )
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                val cancelledWhileActive = terminalState.compareAndSet(
                    LoadTerminalState.ACTIVE,
                    LoadTerminalState.CANCELLED,
                )
                if (cancelledWhileActive) {
                    controller.record(
                        FakeSdkEvent.LoadCancelled(
                            loadRequestId = request.loadRequestId,
                            occurredAtMillis = controller.clock.nowMillis(),
                        ),
                    )
                } else if (terminalState.get() == LoadTerminalState.ACCEPTED) {
                    acceptedHandle.get()?.let(controller::discard)
                }

                controller.unregisterActiveLoad(request.loadRequestId, cancelFromReset)
                if (!config.callbackAfterCancel) {
                    callbackJobs.toList().forEach(Job::cancel)
                }
            }

            fun deliverCallback(
                outcome: FakeCallbackOutcome,
                attempt: Int,
            ) {
                val loadedAd = if (outcome == FakeCallbackOutcome.SUCCESS) {
                    controller.createLoadedAd(request, config)
                } else {
                    null
                }
                controller.record(
                    FakeSdkEvent.LoadCallbackAttempt(
                        loadRequestId = request.loadRequestId,
                        attempt = attempt,
                        outcome = outcome,
                        objectId = loadedAd?.objectId,
                        occurredAtMillis = controller.clock.nowMillis(),
                    ),
                )

                if (controller.currentGeneration() != requestGeneration) {
                    controller.recordIgnoredCallback(
                        request = request,
                        attempt = attempt,
                        outcome = outcome,
                        loadedAd = loadedAd,
                        reason = FakeIgnoredCallbackReason.RESET,
                    )
                    return
                }

                acceptedHandle.set(loadedAd)
                if (!terminalState.compareAndSet(
                        LoadTerminalState.ACTIVE,
                        LoadTerminalState.ACCEPTED,
                    )
                ) {
                    val reason = when (terminalState.get()) {
                        LoadTerminalState.CANCELLED -> FakeIgnoredCallbackReason.CANCELLED
                        LoadTerminalState.ACCEPTED -> FakeIgnoredCallbackReason.DUPLICATE
                        LoadTerminalState.ACTIVE -> error("Unexpected active callback state")
                    }
                    controller.recordIgnoredCallback(
                        request = request,
                        attempt = attempt,
                        outcome = outcome,
                        loadedAd = loadedAd,
                        reason = reason,
                    )
                    return
                }

                controller.unregisterActiveLoad(request.loadRequestId, cancelFromReset)
                controller.record(
                    FakeSdkEvent.LoadCallbackAccepted(
                        loadRequestId = request.loadRequestId,
                        attempt = attempt,
                        outcome = outcome,
                        objectId = loadedAd?.objectId,
                        occurredAtMillis = controller.clock.nowMillis(),
                    ),
                )
                continuation.resume(
                    if (loadedAd == null) {
                        AdLoadResult.Failure(reason = FAKE_LOAD_FAILURE_REASON)
                    } else {
                        AdLoadResult.Success(handle = loadedAd)
                    },
                )
            }

            fun launchCallback(block: suspend () -> Unit) {
                controller.launchCallback(
                    onCreated = { job ->
                        callbackJobs += job
                        job.invokeOnCompletion {
                            callbackJobs -= job
                        }
                    },
                    block = block,
                )
            }

            when (config.scenario) {
                FakeScenario.SUCCESS,
                FakeScenario.SHOW_FAIL,
                -> deliverCallback(FakeCallbackOutcome.SUCCESS, attempt = 1)

                FakeScenario.FAIL ->
                    deliverCallback(FakeCallbackOutcome.FAILURE, attempt = 1)

                FakeScenario.NEVER_CALLBACK -> Unit

                FakeScenario.DELAYED_SUCCESS,
                FakeScenario.LATE_CALLBACK,
                -> launchCallback {
                    delay(config.loadDelayMillis)
                    deliverCallback(FakeCallbackOutcome.SUCCESS, attempt = 1)
                }

                FakeScenario.DUPLICATE_CALLBACK -> launchCallback {
                    delay(config.loadDelayMillis)
                    deliverCallback(FakeCallbackOutcome.SUCCESS, attempt = 1)
                    yield()
                    deliverCallback(FakeCallbackOutcome.SUCCESS, attempt = 2)
                }
            }
        }
    }

    fun show(
        adapterFormat: AdFormat,
        request: AdShowRequest,
    ): Flow<AdShowEvent> = flow {
        val handle = request.handle as? FakeLoadedAd
        val validationFailure = when {
            handle == null -> "Handle is not a FakeLoadedAd"
            handle.format != adapterFormat ->
                "Adapter for $adapterFormat cannot show ${handle.format}"

            !controller.owns(handle) -> "Handle belongs to another fake SDK controller"
            handle.destroyed -> "Handle is destroyed"
            !handle.tryConsume() -> "Handle is already consumed"
            else -> null
        }
        if (validationFailure != null) {
            emit(recordShowFailure(request, handle, validationFailure))
            return@flow
        }
        checkNotNull(handle)

        controller.record(
            FakeSdkEvent.ShowStarted(
                showRequestId = request.showRequestId,
                objectId = handle.objectId,
                occurredAtMillis = controller.clock.nowMillis(),
            ),
        )
        if (handle.scenarioConfig.scenario == FakeScenario.SHOW_FAIL) {
            emit(recordShowFailure(request, handle, FAKE_SHOW_FAILURE_REASON))
            return@flow
        }

        controller.record(
            FakeSdkEvent.Shown(
                showRequestId = request.showRequestId,
                objectId = handle.objectId,
                occurredAtMillis = controller.clock.nowMillis(),
            ),
        )
        emit(AdShowEvent.Shown(showRequestId = request.showRequestId))

        delay(handle.scenarioConfig.impressionDelayMillis)
        if (handle.destroyed) return@flow
        controller.record(
            FakeSdkEvent.Impression(
                showRequestId = request.showRequestId,
                objectId = handle.objectId,
                fakeNetworkName = handle.fakeNetworkName,
                fakeRevenueMicros = handle.fakeRevenueMicros,
                occurredAtMillis = controller.clock.nowMillis(),
            ),
        )
        emit(AdShowEvent.Impression(showRequestId = request.showRequestId))

        delay(handle.scenarioConfig.clickDelayMillis)
        if (handle.destroyed) return@flow
        controller.record(
            FakeSdkEvent.Click(
                showRequestId = request.showRequestId,
                objectId = handle.objectId,
                occurredAtMillis = controller.clock.nowMillis(),
            ),
        )
        emit(AdShowEvent.Click(showRequestId = request.showRequestId))

        delay(handle.scenarioConfig.dismissDelayMillis)
        if (handle.destroyed) return@flow
        controller.record(
            FakeSdkEvent.Dismiss(
                showRequestId = request.showRequestId,
                objectId = handle.objectId,
                occurredAtMillis = controller.clock.nowMillis(),
            ),
        )
        emit(AdShowEvent.Dismiss(showRequestId = request.showRequestId))
    }.flowOn(controller.dispatcher)

    private fun FakeAdsSdkController.recordIgnoredCallback(
        request: AdLoadRequest,
        attempt: Int,
        outcome: FakeCallbackOutcome,
        loadedAd: FakeLoadedAd?,
        reason: FakeIgnoredCallbackReason,
    ) {
        record(
            FakeSdkEvent.LoadCallbackIgnored(
                loadRequestId = request.loadRequestId,
                attempt = attempt,
                outcome = outcome,
                objectId = loadedAd?.objectId,
                reason = reason,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
        loadedAd?.let(::discard)
    }

    private fun recordShowFailure(
        request: AdShowRequest,
        handle: FakeLoadedAd?,
        reason: String,
    ): AdShowEvent.Fail {
        controller.record(
            FakeSdkEvent.ShowFailed(
                showRequestId = request.showRequestId,
                objectId = handle?.objectId,
                reason = reason,
                occurredAtMillis = controller.clock.nowMillis(),
            ),
        )
        return AdShowEvent.Fail(
            showRequestId = request.showRequestId,
            reason = reason,
        )
    }

    private enum class LoadTerminalState {
        ACTIVE,
        ACCEPTED,
        CANCELLED,
    }

    private companion object {
        private const val RESET_CANCELLATION_REASON: String = "Fake SDK reset"
        private const val FAKE_LOAD_FAILURE_REASON: String = "Fake load failure"
        private const val FAKE_SHOW_FAILURE_REASON: String = "Fake show failure"
    }
}
