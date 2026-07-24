package com.example.adsmodule.core.testsupport

import com.example.adsmodule.fake.FakeAdsSdkController
import com.example.adsmodule.fake.FakeAdsSdkModule
import com.example.adsmodule.fake.FakeClock
import com.example.adsmodule.fake.SequentialFakeObjectIdGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Shared deterministic adapter wiring for JVM tests (not a product Fake Ads path).
 * Uses test dispatcher + deterministic object IDs + scheduler-backed clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FakeSdkTestSupport(
    scope: TestScope,
    objectIdPrefix: String = "fake-object",
) {
    val dispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler)
    val controller: FakeAdsSdkController = FakeAdsSdkController(
        clock = FakeClock { scope.testScheduler.currentTime },
        dispatcher = dispatcher,
        objectIdGenerator = SequentialFakeObjectIdGenerator(prefix = objectIdPrefix),
    )
    val sdk = FakeAdsSdkModule.create(controller)
    val idGenerator = SequentialIdGenerator()
    val clock: com.example.adsmodule.core.Clock = com.example.adsmodule.core.Clock {
        scope.testScheduler.currentTime
    }
}
