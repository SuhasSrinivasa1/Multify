package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedShadowSamplingPolicyTest {
    private fun allowed(
        now: Long = 900_000L,
        last: Long = 0L,
        fixed: Boolean = false,
        trade: Boolean = false,
        untilFixed: Long = 8_000L,
        liveUsed: Int = 10,
        historicalUsed: Int = 10,
        brokerReady: Boolean = true
    ): Boolean = FixedShadowSamplingPolicy.canCollect(
        nowMs = now, lastSuccessfulAtMs = last, fixedPriority = fixed,
        criticalTrade = trade, millisUntilNextFixed = untilFixed,
        liveUsedLastMinute = liveUsed, livePerMinuteLimit = 210,
        historicalUsedLastMinute = historicalUsed, historicalPerMinuteLimit = 45,
        brokerReady = brokerReady
    )

    @Test fun onlySpareAlphaCapacity() {
        assertTrue(allowed())
        assertFalse(allowed(fixed = true))
        assertFalse(allowed(trade = true))
        assertFalse(allowed(untilFixed = 3_000))
        assertFalse(allowed(liveUsed = 105))
        assertFalse(allowed(historicalUsed = 21))
        assertFalse(allowed(brokerReady = false))
    }

    @Test fun noFixedTenPercentClockAndBoundedResearchInterval() {
        assertFalse(allowed(last = 890_000L))
        assertFalse(allowed(last = 200_000L))
        assertTrue(allowed(last = 100_000L))
        assertTrue(FixedShadowSamplingPolicy.MAX_PAIRED_SYMBOLS_PER_SAMPLE == 1)
    }
}
