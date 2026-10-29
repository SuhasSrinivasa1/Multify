package com.multify.traderpro.engine

import com.multify.traderpro.data.local.LearningCallEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastMultifyReferenceLedgerTest {
    private fun frozen() = (1..107).map { i ->
        LearningCallEntity(callDate = "2026-09-30", symbol = "STOCK$i", source = "SEED",
            entryPrice = 100.0, buyAtMs = i.toLong(), action = "BUY")
    }

    private fun live(event: Long, symbol: String = "NEW", date: String = "2026-10-27") =
        LearningCallEntity(callDate = date, symbol = symbol, source = "LIVE", action = "BUY",
            buyEventId = event, entryPrice = 105.0, buyAtMs = 1793080000000L + event)

    @Test fun newNotificationsIncreaseCountAndOutcomesOnlyEnrich() {
        val canonical = frozen()
        val first = live(11)
        val duplicateSameEvent = live(11).copy(multifyExitPrice = 106.0, sellAtMs = 1793080060000L, multifyMfePct = 1.0)
        val second = live(12, "NEW")
        val prior = live(9, "OLD", "2026-10-19")
        val summary = ForecastMultifyReferenceLedger.summary(canonical, canonical + first + duplicateSameEvent + second + prior)
        assertEquals(107, summary.frozenCases)
        assertEquals(2, summary.appendedCalls)
        assertEquals(109, summary.totalCalls)
        assertEquals(1, summary.liveCompleted)
        assertEquals(1, summary.livePendingOutcome)
        assertEquals("2026-10-27", summary.latestLiveCallDate)
    }

    @Test fun repeatedSameSymbolDistinctEventsCountSeparately() {
        val canonical = frozen()
        val first = live(11)
        val second = live(12).copy(sellAtMs = 1793080120000L, multifyMfePct = 1.15)
        val result = ForecastMultifyReferenceLedger.summary(canonical, listOf(first, second))
        assertEquals(109, result.totalCalls)
        assertEquals(1, result.liveCompleted)
        assertEquals(1, result.liveMfeMeasured)
        assertEquals(1, result.livePendingOutcome)
    }

    @Test fun futureAndTodayCannotHelpPriorAndSingleWinIsNotEnough() {
        val seed = listOf(live(11,"ABC", "2026-10-22").copy(longRealizedPct = 1.4))
        assertEquals(0.0, ForecastMultifyReferenceLedger.historicalContextBonus("ABC","2026-10-22",seed),0.0)
        assertEquals(0.0, ForecastMultifyReferenceLedger.historicalContextBonus("ABC","2026-10-24",seed),0.0)
        val more = seed + live(12,"ABC", "2026-10-23").copy(longRealizedPct = 0.5)
        assertTrue(ForecastMultifyReferenceLedger.historicalContextBonus("ABC","2026-10-24",more)>0.0)
        assertEquals(0.0, ForecastMultifyReferenceLedger.historicalContextBonus("XYZ","2026-10-24",more),0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun frozenBaselineCountMustNeverDrift() {
        ForecastMultifyReferenceLedger.build(frozen().drop(1), emptyList())
    }
}
