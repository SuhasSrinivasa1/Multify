package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedMarketDiscoveryPolicyTest {
    @Test fun boundedPreSpikeBucketPrecedesMatureMovers() {
        val rows = FixedMarketDiscoveryPolicy.rank(
            currentLtps = mapOf("SPIKED" to 110.0, "EARLY" to 101.2, "MOMENTUM" to 103.0, "RETEST" to 101.0),
            previousLtps = mapOf("SPIKED" to 109.0, "EARLY" to 101.0, "MOMENTUM" to 102.4, "RETEST" to 101.1),
            sessionOpenLtps = mapOf("SPIKED" to 100.0, "EARLY" to 100.0, "MOMENTUM" to 100.0, "RETEST" to 100.0),
            limit = 3,
            preSpikeSlots = 1
        )
        assertEquals(3, rows.size)
        assertEquals("EARLY", rows.first().symbol)
        assertTrue(rows.first().sessionPct in 0.20..2.75)
        assertTrue(rows.any { it.symbol == "SPIKED" } || rows.any { it.symbol == "MOMENTUM" })
    }
}
