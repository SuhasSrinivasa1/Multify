package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEvidenceDeduplicationTest {
    private fun example(source: String, date: String, symbol: String, at: Long): ForecastPositiveEvidenceCase =
        ForecastPositiveEvidenceCase(source, date, symbol, "TREND", "TREND", "BREADTH_BULL",
            "UNAVAILABLE", "CAP_UNKNOWN", "MORNING", "MOMENTUM_TREND",
            null, 0.8, 1.2, 0.6, at)

    @Test fun repeatedIntradaySameStockCannotMatureRanking() {
        val repeated = (1..100).map { example("FORECAST", "2026-10-30", "AAA", it.toLong()) }
        val independent = ForecastUniversalPositiveEvidencePolicy.independentOutcomes(repeated)
        assertEquals(1, independent.size)
        assertEquals(1L, independent.single().observedAtMs)
        assertEquals(0.0, ForecastUniversalPositiveEvidencePolicy.rankingBonus(
            "AAA", "TREND", "TREND", "BREADTH_BULL", "UNAVAILABLE",
            "CAP_UNKNOWN", "MORNING", "MOMENTUM_TREND", repeated), 0.0)
    }

    @Test fun differentSourcesAndDaysRemainIndependent() {
        val rows = listOf(example("FIXED", "2026-10-30", "AAA", 100),
            example("MULTIFY", "2026-10-30", "AAA", 101),
            example("FORECAST", "2026-10-31", "AAA", 102),
            example("FORECAST", "2026-10-31", "AAA", 103))
        assertEquals(3, ForecastUniversalPositiveEvidencePolicy.independentOutcomes(rows).size)
    }
}
