package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.9.1 Fixed input contract, independent of Forecast's session-only VWAP. */
class FixedSessionFeatureTest {
    private fun candle(ts: String, open: Double, high: Double, low: Double,
                       close: Double, volume: Double): List<JsonElement> = listOf(
        JsonPrimitive(ts), JsonPrimitive(open), JsonPrimitive(high),
        JsonPrimitive(low), JsonPrimitive(close), JsonPrimitive(volume))

    @Test fun frozenFixedUsesOriginalAllSuppliedCandleVWAPAndOpeningRange() {
        val history = (0 until 12).map { i ->
            candle("2026-10-29T${9 + i / 4}:15:00", 49.0, 51.0, 48.0, 50.0, 100000.0)
        } + (0 until 4).map { i ->
            candle("2026-10-30T09:${15+i*15}:00", 99.0, 104.0, 98.0, 101.0, 100.0)
        }
        val features = FixedLocalStrategyEngine.buildFeatures(
            QuotePayload(lastPrice = 101.0, bidPrice = 100.9, offerPrice = 101.1),
            HistoricalPayload(candles = history, intervalInMinutes = 15), 15)
        // v7.9.1 uses the supplied historical bars, not just today's bars.
        assertTrue(features.vwap!! < 60.0)
        assertEquals(51.0, features.orb5High!!, .00001)
        assertEquals(51.0, features.orb15High!!, .00001)
        assertEquals(0, features.sessionCandleCount)
    }

    @Test fun frozenFixedRestoresOriginalQuoteSpreadInputs() {
        val candles = (0 until 10).map { i ->
            candle("2026-10-30T09:${15 + i}:00", 99.0, 101.0, 98.0, 100.0, 1000.0)
        }
        val f = FixedLocalStrategyEngine.buildFeatures(
            QuotePayload(lastPrice = 100.0, bidPrice = 99.8, offerPrice = 100.2),
            HistoricalPayload(candles = candles, intervalInMinutes = 15), 15)
        assertEquals(40.0, f.spreadBps!!, .00001)
    }
}
