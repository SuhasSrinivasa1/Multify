package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphaQuotePacingPolicyTest {
    @Test fun firstQuoteIsNotDelayedAndSubsequentQuotesAreSmoothed() {
        assertEquals(0L, AlphaQuotePacingPolicy.waitMs(10_000L, 0L, 650L))
        assertEquals(650L, AlphaQuotePacingPolicy.waitMs(10_000L, 10_000L, 650L))
        assertEquals(150L, AlphaQuotePacingPolicy.waitMs(10_500L, 10_000L, 650L))
        assertEquals(0L, AlphaQuotePacingPolicy.waitMs(10_650L, 10_000L, 650L))
    }

    @Test fun repeatedQuote429BacksOffAndCleanTrafficGraduallyRecovers() {
        val start = AlphaQuotePacingPolicy.State(650L, 10_000L)
        val backedOff = AlphaQuotePacingPolicy.on429(start, 20_000L)
        assertEquals(1_300L, backedOff.gapMs)
        val recovered = AlphaQuotePacingPolicy.onSuccess(backedOff, 140_000L)
        assertEquals(1_250L, recovered.gapMs)
        assertEquals(8_000L, AlphaQuotePacingPolicy.on429(
            AlphaQuotePacingPolicy.State(5_000L, 0L), 10_000L).gapMs)
    }
}
