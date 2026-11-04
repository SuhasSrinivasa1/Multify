package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyFiveMinuteResearchPolicyTest {
    @Test fun aggregatesExistingOneMinuteCandlesWithoutConstructingIncompleteBar() {
        val start = 1_800_000_000L
        val candles = (0 until 101).map { minute ->
            val price = 100.0 + minute * 0.02
            Candle(start + minute * 60, price, price + 0.1, price - 0.1, price + 0.05, 10.0)
        }
        val asOf = (start + 100 * 60) * 1000L
        val research = ReadOnlyFiveMinuteResearchPolicy.evaluate(candles, asOf)
        assertEquals(20, research.completeBars)
        assertNotNull(research.ema9)
        assertNotNull(research.ema20)
        assertNotNull(research.vwap)
        assertEquals(true, research.emaBullish)
        assertTrue(research.researchOnly)
    }
}
