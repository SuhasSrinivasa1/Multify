package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ExecutionEntryPriceGuardPolicyTest {
    @Test fun blocksPriceChasingAndSlowBrokerResponse() {
        assertNull(ExecutionEntryPriceGuardPolicy.rejectionReason(1_000.0, 1_002.0, 500L))
        assertEquals("PRICE_EXTENSION_ABOVE_0_40_PCT",
            ExecutionEntryPriceGuardPolicy.rejectionReason(1_000.0, 1_005.0, 500L))
        assertEquals("SLOW_OR_STALE_BROKER_RESPONSE",
            ExecutionEntryPriceGuardPolicy.rejectionReason(1_000.0, 1_000.0, 11_000L))
        assertEquals("INVALID_PRICE",
            ExecutionEntryPriceGuardPolicy.rejectionReason(0.0, 1_001.0, 300L))
    }
    @Test fun quietPeriodAffectsOnlyResearchScheduling() {
        assertFalse(ResearchRateLimitQuietPolicy.canRun(1_200_000L, 1_000_000L))
        assertTrue(ResearchRateLimitQuietPolicy.canRun(1_310_000L, 1_000_000L))
        assertTrue(ResearchRateLimitQuietPolicy.canRun(1_200_000L, 0L))
    }
}
