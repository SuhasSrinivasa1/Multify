package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedPrimaryCarryForwardPolicyTest {
    private fun p(symbol: String, atMs: Long, tier: String = "PRIMARY", price: Double = 500.0) =
        FixedPrimaryCarryForwardPolicy.Observation(symbol, atMs, tier, true, true, price)

    @Test fun `carries recent validated PRIMARY when it disappears from dynamic ranking`() {
        val result = FixedPrimaryCarryForwardPolicy.candidates(
            listOf(p("GABRIEL", 100_000L), p("JARO", 110_000L)),
            200_000L, emptySet())
        assertEquals(listOf("JARO","GABRIEL"), result)
    }

    @Test fun `does not carry once a stock loses PRIMARY or has been published`() {
        val result = FixedPrimaryCarryForwardPolicy.candidates(
            listOf(p("A", 100_000L),p("A", 150_000L,"RESEARCH"),
                p("B", 140_000L), p("C", 120_000L, price = 90.0)),
            200_000L,setOf("B"))
        assertTrue(result.isEmpty())
    }

    @Test fun `expires stale rows and preserves strict bounded quota`() {
        val old = p("OLD",1_000L)
        val fresh = (1..9).map { p("SYM$it", 500_000L + it) }
        val result = FixedPrimaryCarryForwardPolicy.candidates(listOf(old)+fresh, 600_000L, emptySet())
        assertEquals(5,result.size)
        assertFalse(result.contains("OLD"))
        assertTrue(FixedPrimaryCarryForwardPolicy.wasRecentlyDiscovered("2026-11-12",500_000L,"2026-11-12",600_000L))
        assertFalse(FixedPrimaryCarryForwardPolicy.wasRecentlyDiscovered("2026-11-11",500_000L,"2026-11-12",600_000L))
    }
}
