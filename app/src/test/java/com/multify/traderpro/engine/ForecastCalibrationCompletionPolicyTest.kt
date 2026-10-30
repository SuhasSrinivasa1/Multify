package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastCalibrationCompletionPolicyTest {
    private fun outcome(date: String, symbol: String, complete: Boolean = true) =
        ForecastCalibrationCompletionPolicy.CompletedOutcome(
            date, symbol, if (complete) 100L else null,
            if (complete) 1.0 else null,
            if (complete) 0.4 else null,
            if (complete) 0.1 else null
        )
    @Test fun fiveUniqueCompletedSymbolsAdvanceHistoricalSession() {
        val five = (1..5).map { outcome("2026-10-21", "SYM$it") }
        val dates = ForecastCalibrationCompletionPolicy.qualifyingDates(
            five,"2026-10-30",false,5)
        assertEquals(setOf("2026-10-21"),dates)
    }
    @Test fun duplicateRowsAndTwoSymbolDatesNeverUnlock() {
        val xs = listOf(outcome("2026-10-28","A"),outcome("2026-10-28","A"),
            outcome("2026-10-28","B"),outcome("2026-10-28","C",false))
        assertTrue(ForecastCalibrationCompletionPolicy.qualifyingDates(
            xs,"2026-10-30",true,5).isEmpty())
    }
    @Test fun currentDayWaitsForFullMarketSessionAndFutureNeverCounts() {
        val today = (1..5).map { outcome("2026-10-30","SYM$it") }
        val future = (1..5).map { outcome("2026-11-02","SYM$it") }
        assertTrue(ForecastCalibrationCompletionPolicy.qualifyingDates(
            today+future,"2026-10-30",false,5).isEmpty())
        assertEquals(setOf("2026-10-30"),
            ForecastCalibrationCompletionPolicy.qualifyingDates(
                today+future,"2026-10-30",true,5))
    }
}
