package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AlphaLtpPacingPolicyTest {
    @Test fun firstPermitDoesNotWait() {
        assertEquals(0L, AlphaLtpPacingPolicy.waitMs(10000L, 0L, null, 0))
    }
    @Test fun ltpEndpointHasConservativeMinimumGap() {
        assertEquals(1200L, AlphaLtpPacingPolicy.waitMs(10000L, 10000L, 9000L, 5))
        assertEquals(100L, AlphaLtpPacingPolicy.waitMs(11100L, 10000L, 9000L, 5))
        assertEquals(0L, AlphaLtpPacingPolicy.waitMs(11200L, 10000L, 9000L, 5))
    }
    @Test fun sustainedBatchCallsCannotExceedWindowBudget() {
        assertEquals(45000L, AlphaLtpPacingPolicy.waitMs(15000L, 10000L, 0L, 40))
        assertEquals(1000L, AlphaLtpPacingPolicy.waitMs(59000L, 10000L, 0L, 40))
    }
}
