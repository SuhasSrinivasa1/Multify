package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastFixedPositiveTransferPolicyTest {
    @Test fun successfulFixedExamplesAddButCapBonus() {
        val xs = listOf(
            FixedPositiveTransferCase("ABC", "TREND", "BULL", "BREADTH_BULL", 2.4),
            FixedPositiveTransferCase("XYZ", "TREND", "BULL", "BREADTH_BULL", 1.1)
        )
        val x = ForecastFixedPositiveTransferPolicy.rankingBonus("ABC", "TREND", "BULL", "BREADTH_BULL", xs)
        assertTrue(x > 0.0)
        assertTrue(x <= ForecastFixedPositiveTransferPolicy.MAX_BONUS)
    }

    @Test fun emptyPositiveHistoryAddsNothing() {
        assertEquals(0.0, ForecastFixedPositiveTransferPolicy.rankingBonus("ABC","TREND","BULL","BREADTH_BULL", emptyList()), 0.0)
    }
}
