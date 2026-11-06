package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ResearchCandleFirstTouchPolicyTest {
    private fun bar(at: Long, low: Double, high: Double) =
        ForecastReplayCandleDecoder.Bar(at,100.0,high,low,100.0,100.0)

    @Test fun excludesSignalBarAndMarksSameBarStopTargetAmbiguous() {
        val bars = listOf(bar(1_000L,94.5,103.0), bar(61_001L,94.5,103.0))
        val outcome = ResearchCandleFirstTouchPolicy.evaluate(1_000L,100.0,101.0,bars,200_000L)
        assertEquals("AMBIGUOUS_SAME_CANDLE", outcome.firstTouch)
        assertEquals(61_001L, outcome.firstTouchAtMs)
    }
    @Test fun givesChronologicalStopThenTargetPrecedence() {
        val rows = listOf(bar(61_000L,94.5,100.0),bar(121_000L,100.0,102.0))
        assertEquals("STOP_FIRST_1M",
            ResearchCandleFirstTouchPolicy.evaluate(1_000L,100.0,101.0,rows,200_000L).firstTouch)
    }
    @Test fun fivePercentResearchStopDoesNotTreatTwoPercentPullbackAsStop() {
        val rows = listOf(bar(61_000L,97.5,100.0),bar(121_000L,96.0,100.0))
        val outcome = ResearchCandleFirstTouchPolicy.evaluate(1_000L,100.0,101.0,rows,200_000L)
        assertEquals(-5.0, outcome.hypotheticalStopPct, 0.0)
        assertEquals("NO_TOUCH_OBSERVED", outcome.firstTouch)
    }
    @Test fun gapMakesPostGapTouchAmbiguous() {
        val rows = listOf(bar(61_000L,99.0,100.0),bar(241_000L,100.0,102.0))
        assertEquals("AMBIGUOUS_MISSING_BARS",
            ResearchCandleFirstTouchPolicy.evaluate(1_000L,100.0,101.0,rows,300_000L).firstTouch)
    }
}
