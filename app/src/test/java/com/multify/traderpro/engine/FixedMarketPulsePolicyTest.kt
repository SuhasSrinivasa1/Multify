package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedMarketPulsePolicyTest {
    private val p = FixedMarketPulsePolicy

    @Test fun ranksOnlyNewMarketPulsesWithinPriceBand() {
        val prior = mapOf("ABC" to 1_000.0, "XYZ" to 250.0, "BAD" to 100.0)
        val current = mapOf("ABC" to 1_004.0, "XYZ" to 252.0, "BAD" to 101.0)
        val pulses = p.rank(prior, current, 1_000_000L, 1_120_000L)
        assertEquals(listOf("XYZ", "ABC"), pulses.map { it.symbol })
        assertEquals(0.8, pulses.first().increasePct, 0.00001)
    }

    @Test fun neverClaimsMarketOpenBaselineWithoutTwoRecentSnapshots() {
        assertTrue(p.rank(emptyMap(), mapOf("ABC" to 500.0), 0L, 1_000L).isEmpty())
        assertTrue(p.rank(mapOf("ABC" to 500.0), mapOf("ABC" to 507.0),
            1_000_000L, 2_000_001L).isEmpty())
        assertTrue(p.rank(mapOf("ABC" to 500.0), mapOf("ABC" to 503.0),
            1_000_000L, 999_000L).isEmpty())
    }

    @Test fun noExtraRequestsOrExecutionOutputs() {
        assertEquals(20, p.MAX_ROWS_PER_SCAN)
        val pulses = p.rank(mapOf("LOW" to 500.0), mapOf("LOW" to 500.5),
            1_000_000L, 1_120_000L)
        assertTrue(pulses.isEmpty())
    }
}
