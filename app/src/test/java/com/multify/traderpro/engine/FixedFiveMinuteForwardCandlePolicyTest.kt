package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedFiveMinuteForwardCandlePolicyTest {
    private val p = FixedFiveMinuteForwardCandlePolicy
    private val barMs = p.BAR_DURATION_MS
    private fun bar(i: Long, h: Double, l: Double) = FixedFiveMinuteForwardCandlePolicy.Bar(i * barMs, h, l)

    @Test fun rejectsStillFormingCandleWhenReferenceIsMidBar() {
        val result = p.evaluate(
            barMs + 10_000L, 100.0, 1.0,
            listOf(bar(1, 115.0, 70.0),bar(2, 100.5, 99.9),
                bar(3, 100.8, 99.5),bar(4, 100.4, 99.7)), 15)
        assertTrue(result.matured)
        assertFalse(result.targetTouched!!)
    }

    @Test fun marksSameCandleTargetAndRiskAmbiguousNotAsWin() {
        val result = p.evaluate(barMs, 100.0, 1.0,
            listOf(bar(1, 101.1, 97.5), bar(2, 102.0, 99.0),
                bar(3, 101.0, 99.0)),15)
        assertTrue(result.matured)
        assertTrue(result.sameCandleOrderingAmbiguous)
        assertFalse(result.targetBeforeTwoPctAdverse!!)
    }

    @Test fun targetBeforeLaterAdverseIsIdentifiedWithCompletedFutureBars() {
        val r = p.evaluate(barMs, 100.0, 1.0,
            listOf(bar(1, 101.6, 99.8),bar(2, 100.8, 97.9),
                bar(3, 100.4, 98.0)),15)
        assertTrue(r.matured)
        assertTrue(r.targetTouched!!)
        assertTrue(r.targetBeforeTwoPctAdverse!!)
    }

    @Test fun shortMarketSessionIsCensoredNotTreatedAsFailure() {
        val r = p.evaluate(barMs,100.0,1.0,listOf(bar(1,103.0,99.0)),60)
        assertFalse(r.matured)
        assertNull(r.targetTouched)
        assertNull(r.targetBeforeTwoPctAdverse)
    }
}
