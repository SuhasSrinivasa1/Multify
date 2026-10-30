package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedMarketDiscoveryPolicyTest {
    @Test fun shortlistKeepsMomentumAndReservesEarlyPreSpikeLane() {
        val current = linkedMapOf<String, Double>()
        val previous = linkedMapOf<String, Double>()
        val open = linkedMapOf<String, Double>()
        repeat(40) { i ->
            val symbol = "SPIKE$i"
            open[symbol] = 100.0
            previous[symbol] = 101.0
            current[symbol] = 105.0 + i
        }
        open["EARLY"] = 100.0
        previous["EARLY"] = 100.0
        current["EARLY"] = 100.12
        val rows = FixedMarketDiscoveryPolicy.rank(current, previous, open, 28)
        assertEquals(28, rows.size)
        assertTrue(rows.any { it.symbol == "EARLY" && it.lane == "EARLY_PRE_SPIKE" })
        assertTrue(rows.any { it.lane == "MOMENTUM" })
    }


    @Test fun secondPeakPullbackRemainsInPreSpikeLaneAfterLargeSessionMove() {
        val rows = FixedMarketDiscoveryPolicy.rank(
            currentLtps = mapOf("SECOND_PEAK" to 106.10, "SPIKE" to 110.0),
            previousLtps = mapOf("SECOND_PEAK" to 106.00, "SPIKE" to 109.0),
            sessionOpenLtps = mapOf("SECOND_PEAK" to 100.0, "SPIKE" to 100.0),
            limit = 10
        )
        assertTrue(rows.any {
            it.symbol == "SECOND_PEAK" && it.lane == "EARLY_PRE_SPIKE"
        })
    }

    @Test fun earlyLaneDoesNotSelectFallingStocksWithoutPositiveSessionOrPulse() {
        val rows = FixedMarketDiscoveryPolicy.rank(
            currentLtps = mapOf("FALL" to 98.0, "EARLY" to 100.12),
            previousLtps = mapOf("FALL" to 100.0, "EARLY" to 100.0),
            sessionOpenLtps = mapOf("FALL" to 100.0, "EARLY" to 100.0),
            limit = 10
        )
        assertTrue(rows.none { it.symbol == "FALL" })
        assertTrue(rows.any { it.symbol == "EARLY" })
    }
}
