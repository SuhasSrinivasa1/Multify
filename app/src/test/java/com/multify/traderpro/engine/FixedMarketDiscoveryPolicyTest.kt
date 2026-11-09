package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

/** The v7.9.17 Fixed market-wide rank must not be reweighted by later pre-spike slots. */
class FixedMarketDiscoveryPolicyTest {
    @Test fun restoredMomentumRankingMatchesReference() {
        val rows = FixedMarketDiscoveryPolicy.rank(
            currentLtps = mapOf("SPIKED" to 110.0, "EARLY" to 101.2, "MOMENTUM" to 103.0, "RETEST" to 101.0),
            previousLtps = mapOf("SPIKED" to 109.0, "EARLY" to 101.0, "MOMENTUM" to 102.4, "RETEST" to 101.1),
            sessionOpenLtps = mapOf("SPIKED" to 100.0, "EARLY" to 100.0, "MOMENTUM" to 100.0, "RETEST" to 100.0),
            limit = 3
        )
        assertEquals(listOf("SPIKED", "MOMENTUM", "EARLY"), rows.map { it.symbol })
    }

    @Test fun noCandidatesWhenLimitZero() {
        assertTrue(FixedMarketDiscoveryPolicy.rank(mapOf("ONE" to 101.0), mapOf("ONE" to 100.0), emptyMap(), 0).isEmpty())
    }

    @Test fun positiveSubTenPricesCanBeCountedForCoverageButNeverEnterDiscoveryRanking() {
        val ranked = FixedMarketDiscoveryPolicy.rank(
            currentLtps = mapOf("LOWPRICE" to 8.5, "ELIGIBLE" to 250.0),
            previousLtps = mapOf("LOWPRICE" to 8.0, "ELIGIBLE" to 240.0),
            sessionOpenLtps = mapOf("LOWPRICE" to 8.0, "ELIGIBLE" to 240.0),
            limit = 10
        )
        assertEquals(listOf("ELIGIBLE"), ranked.map { it.symbol })
    }
}
