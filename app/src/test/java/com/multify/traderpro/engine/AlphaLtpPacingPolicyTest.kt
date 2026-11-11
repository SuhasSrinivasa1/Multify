package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AlphaLtpPacingPolicyTest {
    @Test fun firstPermitDoesNotWait() {
        assertEquals(0L, AlphaLtpPacingPolicy.waitMs(10000L, 0L, null, 0))
    }
    @Test fun adaptiveSpacingStartsConservatively() {
        assertEquals(1_250L, AlphaLtpPacingPolicy.waitMs(10000L,10000L,9000L,5))
        assertEquals(500L, AlphaLtpPacingPolicy.waitMs(10750L,10000L,9000L,5))
        assertEquals(0L, AlphaLtpPacingPolicy.waitMs(11250L,10000L,9000L,5))
        assertEquals(1_000L, AlphaLtpPacingPolicy.waitMs(10000L,10000L,9000L,5,1_000L))
    }
    @Test fun alphaLtpEndpointCapIs40RequestsPerMinute() {
        assertEquals(40, AlphaLtpPacingPolicy.MAX_PER_MINUTE)
        assertEquals(45000L, AlphaLtpPacingPolicy.waitMs(15000L,10000L,0L,40))
        assertEquals(1000L, AlphaLtpPacingPolicy.waitMs(59000L,10000L,0L,40))
    }
}
