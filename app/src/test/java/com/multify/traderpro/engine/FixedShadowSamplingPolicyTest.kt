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
        historicalUsed: Int = 4,
        brokerReady: Boolean = true
    ): Boolean = FixedShadowSamplingPolicy.canCollect(
        nowMs = now, lastSuccessfulAtMs = last, fixedPriority = fixed,
        criticalTrade = trade, millisUntilNextFixed = untilFixed,
        liveUsedLastMinute = liveUsed, livePerMinuteLimit = 40,
        historicalUsedLastMinute = historicalUsed, historicalPerMinuteLimit = 8,
        brokerReady = brokerReady
    )

    @Test fun onlySpareBetaResearchCapacity() {
        assertTrue(allowed())
        assertFalse(allowed(fixed = true))
        assertFalse(allowed(trade = true))
        assertFalse(allowed(untilFixed = 3_000))
        assertFalse(allowed(liveUsed = 30))
        assertFalse(allowed(historicalUsed = 6))
        assertFalse(allowed(brokerReady = false))
    }

    @Test fun recent429AlwaysSuppressesOptionalResearch() {
        val now = 3_000_000L
        val quiet = FixedShadowSamplingPolicy.POST_429_RESEARCH_QUIET_PERIOD_MS
        fun permitted(cooldownUntil: Long, last429: Long): Boolean =
            FixedShadowSamplingPolicy.canCollect(
                nowMs = now, lastSuccessfulAtMs = 0L, fixedPriority = false,
                criticalTrade = false, millisUntilNextFixed = 60_000L,
                liveUsedLastMinute = 1, livePerMinuteLimit = 210,
                historicalUsedLastMinute = 1, historicalPerMinuteLimit = 45,
                brokerReady = true,
                latestBrokerCooldownUntilMs = cooldownUntil,
                lastObserved429AtMs = last429
            )
        assertFalse(permitted(now - quiet + 1L, 0L))
        assertFalse(permitted(0L, now - quiet + 1L))
        assertFalse(permitted(now + 60_000L, 0L))
        assertTrue(permitted(now - quiet - 1L, 0L))
        assertTrue(permitted(0L, now - quiet - 1L))
    }

    @Test fun noFixedTenPercentClockAndBoundedResearchInterval() {
        assertFalse(allowed(last = 890_000L))
        assertFalse(allowed(last = 200_000L))
        assertTrue(allowed(last = 100_000L))
        assertTrue(FixedShadowSamplingPolicy.MAX_PAIRED_SYMBOLS_PER_SAMPLE == 1)
    }
}
