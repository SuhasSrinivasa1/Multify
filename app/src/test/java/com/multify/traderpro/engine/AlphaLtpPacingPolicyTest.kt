package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AlphaLtpPacingPolicyTest {
    @Test fun firstPermitDoesNotWait() {
        assertEquals(0L, AlphaLtpPacingPolicy.waitMs(10000L, 0L, null, 0))
    }
    @Test fun adaptiveSpacingStartsConservatively() {
        assertEquals(1300L, AlphaLtpPacingPolicy.waitMs(10000L,10000L,9000L,5))
        assertEquals(550L, AlphaLtpPacingPolicy.waitMs(10750L,10000L,9000L,5))
        assertEquals(0L, AlphaLtpPacingPolicy.waitMs(11300L,10000L,9000L,5))
        assertEquals(250L, AlphaLtpPacingPolicy.waitMs(10000L,10000L,9000L,5,220L))
    }
    @Test fun alphaLtpCapIs240RequestsPerMinute() {
        assertEquals(45000L, AlphaLtpPacingPolicy.waitMs(15000L,10000L,0L,240))
        assertEquals(1000L, AlphaLtpPacingPolicy.waitMs(59000L,10000L,0L,240))
    }
}
