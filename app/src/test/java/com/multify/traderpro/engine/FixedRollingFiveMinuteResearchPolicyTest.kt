package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedRollingFiveMinuteResearchPolicyTest {
    @Test fun `does not invent momentum without five minute history`() {
        val result = FixedRollingFiveMinuteResearchPolicy.study(
            300_000L, mapOf("ABC" to 105.0),
            listOf(FixedRollingMarketSample(270_000L, mapOf("ABC" to 100.0))),
            listOf("ABC")
        )
        assertFalse(result.available)
        assertNull(result.referenceAtMs)
    }

    @Test fun `uses closest prior completed sweep and does not change selected names`() {
        val history = listOf(
            FixedRollingMarketSample(100_000L, mapOf("ABC" to 100.0, "XYZ" to 90.0)),
            FixedRollingMarketSample(190_000L, mapOf("ABC" to 110.0, "XYZ" to 99.0)),
            FixedRollingMarketSample(280_000L, mapOf("ABC" to 115.0, "XYZ" to 100.0))
        )
        val result = FixedRollingFiveMinuteResearchPolicy.study(
            460_000L, mapOf("ABC" to 121.0, "XYZ" to 96.0),
            history, listOf("XYZ", "ABC")
        )
        assertTrue(result.available)
        assertEquals(190_000L, result.referenceAtMs)
        assertEquals(270_000L, result.lookbackMs)
        assertEquals(2, result.measuredCount)
        assertEquals("ABC", result.topMovers.first().symbol)
        assertEquals(listOf("XYZ", "ABC"), result.selectedCandidates.map { it.symbol })
        assertEquals(10.0, result.topMovers.first().changePct, 1e-9)
    }
}
