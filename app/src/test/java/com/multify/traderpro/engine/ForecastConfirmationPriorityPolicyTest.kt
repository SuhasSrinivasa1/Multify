package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastConfirmationPriorityPolicyTest {
    @Test fun prioritizesRecentPrimariesAndPreservesPendingStocks() {
        assertEquals(listOf("C", "A", "B", "D"),
            ForecastConfirmationPriorityPolicy.prioritize(
                listOf("A", "B", "C", "D"), listOf("C", "A", "C", "UNKNOWN"),
                listOf("A", "B", "C", "D")))
    }

    @Test fun boundedUrgentQueueDoesNotDiscardOtherCandidates() {
        val universe = (1..18).map { "S$it" }
        val actual = ForecastConfirmationPriorityPolicy.prioritize(universe, universe.reversed(), universe)
        assertEquals(18, actual.size)
        assertEquals(18, actual.distinct().size)
        assertEquals(universe.reversed().take(8), actual.take(8))
    }
}
