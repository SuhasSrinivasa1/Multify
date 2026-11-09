package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AlphaLiveDataPacingPolicyTest {
    @Test fun firstLiveDataRequestDoesNotWait() {
        assertEquals(0L, AlphaLiveDataPacingPolicy.waitMs(10_000L, 0L, null, 0))
    }

    @Test fun quoteAndLtpShareTheSameMinimumGap() {
        assertEquals(1_200L, AlphaLiveDataPacingPolicy.waitMs(10_000L, 10_000L, 9_000L, 5))
        assertEquals(0L, AlphaLiveDataPacingPolicy.waitMs(11_200L, 10_000L, 9_000L, 5))
    }

    @Test fun everyLiveEndpointSharesFortyPerMinuteCeiling() {
        assertEquals(45_000L, AlphaLiveDataPacingPolicy.waitMs(15_000L, 10_000L, 0L, 40))
        assertEquals(1_000L, AlphaLiveDataPacingPolicy.waitMs(59_000L, 10_000L, 0L, 40))
        assertEquals(0L, AlphaLiveDataPacingPolicy.waitMs(60_000L, 10_000L, 0L, 39))
    }
}
