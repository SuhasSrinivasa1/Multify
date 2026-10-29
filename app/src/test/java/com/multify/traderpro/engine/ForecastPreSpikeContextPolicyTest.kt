package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastPreSpikeContextPolicyTest {
    private fun bar(index: Int, mid: Double, width: Double, volume: Double) = Candle(
        index.toLong(), mid, mid + width, mid - width, mid, volume
    )

    @Test fun rangeCompressionAndOrderlyVolumeAreOptionalRankingContext() {
        val preceding = (0..9).map { bar(it, 100.0 + it * .01, 0.65, 100.0) }
        val recent = (10..14).map { bar(it, 100.15 + it * .01, 0.08, 135.0) }
        val last = bar(15, 100.3, 0.09, 200.0)
        val result = ForecastPreSpikeContextPolicy.evaluate(preceding + recent + last + last.copy(ts=16))
        assertTrue(result.rankingBonus >= 0.0)
        assertTrue(result.rankingBonus <= 0.016)
    }

    @Test fun tooFewBarsNeverManufactureAMove() {
        val result = ForecastPreSpikeContextPolicy.evaluate((0..14).map { bar(it,100.0,.1,100.0) })
        assertEquals(0.0,result.rankingBonus,0.0)
    }
}
