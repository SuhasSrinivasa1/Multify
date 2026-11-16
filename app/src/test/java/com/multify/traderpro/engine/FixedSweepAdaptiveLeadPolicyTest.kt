package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedSweepAdaptiveLeadPolicyTest {
    @Test fun usesOriginalLeadsUntilEnoughVerifiedSweeps() {
        val policy = FixedSweepAdaptiveLeadPolicy()
        assertEquals(50_000L, policy.expectedLeadMs(true, true))
        assertEquals(76_000L, policy.expectedLeadMs(true, false))
        policy.record(true, true, 40_000L, valid = true)
        policy.record(true, true, 41_000L, valid = true)
        assertEquals(50_000L, policy.expectedLeadMs(true, true))
    }

    @Test fun p90IsPerRoutingModeAndDoesNotTreatFailedSweepsAsValid() {
        val policy = FixedSweepAdaptiveLeadPolicy()
        listOf(39_000L, 40_000L, 41_000L, 42_000L).forEach {
            policy.record(true, true, it, valid = true)
        }
        policy.record(true, true, 290_000L, valid = false)
        policy.record(false, true, 115_000L, valid = true)
        assertEquals(50_000L, policy.expectedLeadMs(true, true))
        assertEquals(76_000L, policy.expectedLeadMs(true, false))
        assertEquals(4, policy.sampleCount(true, true))
    }

    @Test fun slowVerifiedSweepsIncreaseLeadWithinSafeBound() {
        val policy = FixedSweepAdaptiveLeadPolicy()
        listOf(99_000L, 107_000L, 140_000L).forEach {
            policy.record(false, true, it, valid = true)
        }
        assertEquals(148_000L, policy.expectedLeadMs(true, false))
        policy.record(false, true, 290_000L, valid = true)
        assertEquals(150_000L, policy.expectedLeadMs(true, false))
    }
}
