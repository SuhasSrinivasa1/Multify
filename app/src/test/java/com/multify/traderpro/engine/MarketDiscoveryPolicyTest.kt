package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDiscoveryPolicyTest {
    @Test fun ranksFreshPositiveMoversAcrossTheBroadMarket() {
        val current = mapOf("A" to 102.0, "B" to 101.0, "C" to 99.0, "D" to 105.0)
        val previous = mapOf("A" to 100.0, "B" to 100.5, "C" to 100.0, "D" to 104.9)
        val open = mapOf("A" to 100.0, "B" to 99.0, "C" to 100.0, "D" to 100.0)
        val ranked = MarketDiscoveryPolicy.rank(current, previous, open, 3)
        assertEquals(listOf("D", "A", "B"), ranked.map { it.symbol })
        assertTrue(ranked.all { it.score > 0.0 })
    }

    @Test fun flatOrFallingNamesDoNotConsumeDiscoverySlots() {
        val current = mapOf("A" to 100.0, "B" to 99.0)
        val previous = mapOf("A" to 100.0, "B" to 100.0)
        val open = mapOf("A" to 100.0, "B" to 100.0)
        assertTrue(MarketDiscoveryPolicy.rank(current, previous, open, 5).isEmpty())
    }
}
