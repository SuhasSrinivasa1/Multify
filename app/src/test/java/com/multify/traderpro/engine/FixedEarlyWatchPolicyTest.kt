package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedEarlyWatchPolicyTest {
    @Test fun firstPrimaryCorrelationIsBounded() {
        assertTrue(FixedEarlyWatchPolicy.quoteMatchesScan(10_000L, 20_000L))
        assertFalse(FixedEarlyWatchPolicy.quoteMatchesScan(20_000L, 10_000L))
        assertFalse(FixedEarlyWatchPolicy.quoteMatchesScan(10_000L, 200_001L))
        assertFalse(FixedEarlyWatchPolicy.quoteMatchesScan(0L, 10_000L))
    }
    @Test fun confirmationPriceAppreciationIsDescriptiveOnly() {
        assertEquals(2.958579, FixedEarlyWatchPolicy.advanceFromFirstPrimary(845.0, 870.0)!!, 0.001)
        assertNull(FixedEarlyWatchPolicy.advanceFromFirstPrimary(0.0, 100.0))
        assertTrue(FixedEarlyWatchPolicy.independentObservation(null, 10_000))
        assertFalse(FixedEarlyWatchPolicy.independentObservation(10_000, 10_000))
    }
}
