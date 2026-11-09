package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AlphaLiveDataPacingPolicyTest {
    private fun expectedWaitMs(
        now: Long, last: Long = 0L, totalOldest: Long? = null, total: Int = 0,
        researchOldest: Long? = null, research: Int = 0,
        protection: Boolean = false, holdings: Boolean = false
    ) = AlphaLiveDataPacingPolicy.waitMs(
        now, last, totalOldest, total, researchOldest, research, protection, holdings
    )

    @Test fun firstLiveDataRequestDoesNotWait() {
        assertEquals(0L, expectedWaitMs(10_000L))
    }

    @Test fun quoteAndLtpShareTheSameMinimumGap() {
        assertEquals(1_200L, expectedWaitMs(10_000L, last = 10_000L, totalOldest = 9_000L, total = 5))
        assertEquals(0L, expectedWaitMs(11_200L, last = 10_000L, totalOldest = 9_000L, total = 5))
    }

    @Test fun allAlphaLiveEndpointsShareFortyPerMinuteCeiling() {
        assertEquals(45_000L, expectedWaitMs(15_000L, last = 10_000L, totalOldest = 0L, total = 40))
        assertEquals(1_000L, expectedWaitMs(59_000L, last = 10_000L, totalOldest = 0L, total = 40))
    }

    @Test fun twelveRequestsRemainReservedForProtectionWhenHoldingsExist() {
        assertEquals(45_000L, wait(15_000L, researchOldest = 0L, research = 28, holdings = true))
        assertEquals(0L, wait(15_000L, totalOldest = 0L, total = 28,
            researchOldest = 0L, research = 28, protection = true, holdings = true))
        assertEquals(0L, wait(15_000L, totalOldest = 0L, total = 28,
            researchOldest = 0L, research = 28, holdings = false))
    }

    @Test fun protectionStillObeysTheOverallFortyPerMinuteCeiling() {
        assertEquals(1_000L, wait(59_000L, totalOldest = 0L, total = 40,
            protection = true, holdings = true))
    }
}
