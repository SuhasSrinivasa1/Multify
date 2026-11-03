package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class ForecastExpectedAverageGainPolicyTest {
    private fun row(day: String, symbol: String, mfe: Double) = ForecastHistoricalOutcome(
        day,symbol,"PRE_SPIKE","TREND","BREADTH_BULL",mfe,0.0,0.0,false)
    @Test fun strictGreaterThanOneAndIncludeNonWinners() {
        val r=listOf(row("2026-10-28","A",1.0),row("2026-10-28","B",1.0),
            row("2026-10-29","C",1.0),row("2026-10-29","D",1.0),row("2026-10-30","E",1.0))
        val a=ForecastExpectedAverageGainPolicy.estimate("PRE_SPIKE","TREND","BREADTH_BULL",r)
        assertFalse(a.eligible);assertEquals(1.0,a.averageMfePct!!,0.00001)
        assertTrue(ForecastExpectedAverageGainPolicy.estimate("PRE_SPIKE","TREND","BREADTH_BULL",
            r.dropLast(1)+row("2026-10-30","E",2.0)).eligible)
        assertFalse(ForecastExpectedAverageGainPolicy.estimate("PRE_SPIKE","TREND","BREADTH_BULL",
            r.dropLast(1)).eligible)
    }
    @Test fun noLeakAcrossRegimeOrDuplicateSymbolDay() {
        val r=listOf(row("2026-10-28","A",5.0),row("2026-10-28","A",5.0),
           row("2026-10-29","B",5.0),row("2026-10-30","C",5.0),row("2026-10-31","D",5.0))
        assertEquals(4, ForecastExpectedAverageGainPolicy.estimate("PRE_SPIKE","TREND","BREADTH_BULL",r).observations)
        assertFalse(ForecastExpectedAverageGainPolicy.estimate("PRE_SPIKE","TREND","BREADTH_WEAK",r).eligible)
    }
}
