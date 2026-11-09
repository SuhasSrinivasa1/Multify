package com.multify.traderpro.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FixedScanBuyIsolationPolicyTest {
    @Test fun aPendingGttDoesNotInvalidateFinishedFixedSelection() = runBlocking {
        var errors = 0
        val completed = FixedScanBuyIsolationPolicy.execute(
            buy = { throw IllegalStateException("Broker recovery is reconciling executed GTT sell") },
            onFailure = { errors++ }
        )
        assertEquals(0, completed)
        assertEquals(1, errors)
        assertEquals(1, FixedScanBuyIsolationPolicy.execute({ 1 }, { errors++ }))
        assertEquals(1, errors)
    }

    @Test fun cancellationIsNeverSwallowed() = runBlocking {
        var called = false
        try {
            FixedScanBuyIsolationPolicy.execute(
                buy = { throw CancellationException("worker cancelled") },
                onFailure = { called = true }
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(called)
        }
    }
}
