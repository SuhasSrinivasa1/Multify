package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedUnifiedRequestBudgetPolicyTest {
    @Test fun normalBudgetsPreserveTheTwoAccountEnvelope() {
        val p = FixedUnifiedRequestBudgetPolicy
        assertEquals(55, p.effectiveAlphaLiveLimit(false))
        assertEquals(35, p.effectiveBetaLiveLimit(false))
        assertEquals(90, p.effectiveSharedLiveLimit(false))
        assertEquals(45, p.effectiveAlphaHistoricalLimit(false))
        assertEquals(8, p.effectiveBetaHistoricalLimit(false))
        assertEquals(53, p.effectiveSharedHistoricalLimit(false))
    }

    @Test fun a429TemporarilyReducesOnlyTransportCapacity() {
        val p = FixedUnifiedRequestBudgetPolicy
        assertEquals(50, p.effectiveAlphaLiveLimit(true))
        assertEquals(30, p.effectiveBetaLiveLimit(true))
        assertEquals(75, p.effectiveSharedLiveLimit(true))
        assertEquals(40, p.effectiveAlphaHistoricalLimit(true))
        assertEquals(7, p.effectiveBetaHistoricalLimit(true))
        assertEquals(45, p.effectiveSharedHistoricalLimit(true))
    }

    @Test fun rollingWindowWaitUsesOldestRequestExpiry() {
        val p = FixedUnifiedRequestBudgetPolicy
        assertEquals(5_000L, p.windowWaitMs(10_000L, 55, 55, 0L))
        assertEquals(0L, p.windowWaitMs(10_000L, 54, 55, 0L))
        assertEquals(1L, p.windowWaitMs(70_000L, 55, 55, 0L))
        assertEquals(0L, p.windowWaitMs(10_000L, 55, 55, null))
    }
}
