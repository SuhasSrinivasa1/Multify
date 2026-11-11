package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Current-session features are separated from multi-day trend inputs. */
class FixedSessionFeatureTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    private fun candle(ts: String, open: Double, high: Double, low: Double,
                       close: Double, volume: Double): List<JsonElement> = listOf(
        JsonPrimitive(ts), JsonPrimitive(open), JsonPrimitive(high),
        JsonPrimitive(low), JsonPrimitive(close), JsonPrimitive(volume))

    private fun priorDay() = (0 until 12).map { i ->
        val at = LocalDateTime.of(2026, 10, 29, 9, 15).plusMinutes(15L * i)
        candle(at.toString(), 49.0, 51.0, 48.0, 50.0, 100_000.0)
    }

    private fun currentDay() = listOf(9 to 15, 9 to 30, 9 to 45, 10 to 0).map { (h, m) ->
        candle("2026-10-30T%02d:%02d:00".format(h, m), 99.0, 104.0, 98.0, 101.0, 100.0)
    }

    private val asOf = LocalDateTime.of(2026, 10, 30, 10, 16)
        .atZone(ist).toInstant().toEpochMilli()

    @Test fun fixedUsesOnlyTodaysVWAPAndIndependentOpeningRanges() {
        val history = priorDay() + currentDay()
        val features = FixedLocalStrategyEngine.buildFeatures(
            QuotePayload(lastPrice = 101.0, bidPrice = 100.9, offerPrice = 101.1),
            HistoricalPayload(candles = history, intervalInMinutes = 15),
            intervalMinutes = 15, asOfMs = asOf
        )
        assertEquals(101.0, features.vwap!!, .00001)
        assertNull("15m bars cannot prove a 5m opening range", features.orb5High)
        assertEquals(104.0, features.orb15High!!, .00001)
        assertEquals(4, features.sessionCandleCount)
        assertTrue(features.vwap!! > 100.0)
    }

    @Test fun quoteSpreadInputsRemainUnchanged() {
        val features = FixedLocalStrategyEngine.buildFeatures(
            QuotePayload(lastPrice = 100.0, bidPrice = 99.8, offerPrice = 100.2),
            HistoricalPayload(candles = priorDay() + currentDay(), intervalInMinutes = 15),
            intervalMinutes = 15, asOfMs = asOf
        )
        assertEquals(40.0, features.spreadBps!!, .00001)
    }
}
