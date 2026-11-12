package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedPriceBandDiscoveryPrefilterTest {
    @Test fun `preselection retains eligible movers instead of wasting top ranks`() {
        val current = linkedMapOf(
            "OUT1" to 100.0, "OUT2" to 6500.0,
            "GOOD" to 400.0, "ALSO" to 500.0)
        val previous = mapOf("OUT1" to 80.0, "OUT2" to 6000.0,
            "GOOD" to 380.0, "ALSO" to 485.0)
        val original = FixedMarketDiscoveryPolicy.rank(current, previous, previous, 2)
        assertTrue(original.any { it.symbol == "OUT1" })
        val eligible = current.filterValues { RecommendationPriceBandPolicy.eligible(it) }
        val now = FixedMarketDiscoveryPolicy.rank(eligible, previous, previous, 2)
        assertEquals(2, now.size)
        assertEquals(setOf("GOOD", "ALSO"), now.map { it.symbol }.toSet())
        assertTrue(now.all { RecommendationPriceBandPolicy.eligible(it.ltp) })
    }
}
