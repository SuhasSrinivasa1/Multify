package com.multify.traderpro.engine

import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import org.junit.Assert.*
import org.junit.Test

class ForecastCandleShadowTest {
    @Test fun fiveMinuteShadowRequiresFiveDistinctOneMinuteBars() {
        val epoch = 1800000000L / 300L * 300L
        val five = (0 until 5).map { minute -> listOf(
            JsonPrimitive(epoch + minute * 60), JsonPrimitive(100.0), JsonPrimitive(102.0),
            JsonPrimitive(99.0), JsonPrimitive(100.0 + minute), JsonPrimitive(1000)
        ) }
        val complete = ForecastCandleShadow.aggregateFiveMinute(HistoricalPayload(candles = five))
        assertEquals(1, complete.completeBars)
        assertEquals(104.0, complete.lastClose!!, 0.0001)
        val partial = ForecastCandleShadow.aggregateFiveMinute(HistoricalPayload(candles = five.take(4)))
        assertEquals(0, partial.completeBars)
        assertNull(partial.lastClose)
    }
}
