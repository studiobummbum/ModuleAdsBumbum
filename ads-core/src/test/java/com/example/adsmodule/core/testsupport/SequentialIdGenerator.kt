package com.example.adsmodule.core.testsupport

import com.example.adsmodule.core.IdGenerator
import java.util.concurrent.atomic.AtomicLong

/**
 * Deterministic [IdGenerator] for unit/integration tests.
 */
internal class SequentialIdGenerator(
    private val prefix: String = "id",
) : IdGenerator {
    private val next = AtomicLong(0L)

    init {
        require(prefix.isNotBlank()) { "prefix must not be blank" }
    }

    override fun nextId(): String = "$prefix-${next.incrementAndGet()}"
}
