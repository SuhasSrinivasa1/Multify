package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTargetExpansionPolicyTest {
    @Test
    fun matchingPositiveFixedUpsideCanExpandTargetWithinCapacity() {
        val positives = listOf(
            FixedPositiveTransferCase("ABC", "TREND", "BULL", "BREADTH_BULL", 2.2, 2.8),
            FixedPositiveTransferCase("ABC", "TREND", "BULL", "BREADTH_BULL", 1.8, 2.4)
        )
        val expanded = ForecastTargetExpansionPolicy.targetPct(
            baseTargetPct = 1.0,
            symbol = "ABC",
            strategy = "TREND",
            regime = "BULL",
            marketRegime = "BREADTH_BULL",
            volatilityCapacityPct = 2.0,
            positives = positives
        )
        assertTrue(expanded > 1.0)
        assertTrue(expanded <= 1.75)
        assertTrue(expanded <= 2.0)
    }

    @Test
    fun noMatchingEvidenceKeepsBaseTarget() {
        val positives = listOf(
            FixedPositiveTransferCase("XYZ", "REVERSAL", "NEUTRAL", "BREADTH_NEUTRAL", 3.0, 3.5)
        )
        assertEquals(
            1.0,
            ForecastTargetExpansionPolicy.targetPct(1.0, "ABC", "TREND", "BULL", "BREADTH_BULL", 3.0, positives),
            0.0
        )
    }

    @Test
    fun volatilityCapacityIsHardCeiling() {
        val positives = listOf(
            FixedPositiveTransferCase("ABC", "TREND", "BULL", "BREADTH_BULL", 4.0, 5.0)
        )
        assertEquals(
            1.2,
            ForecastTargetExpansionPolicy.targetPct(1.0, "ABC", "TREND", "BULL", "BREADTH_BULL", 1.2, positives),
            1e-9
        )
    }
}
