package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class FixedSessionFeatureTest {
    private fun candle(ts: String, o: Double, h: Double, l: Double, c: Double, v: Double): List<JsonElement> =
        listOf(JsonPrimitive(ts), JsonPrimitive(o), JsonPrimitive(h), JsonPrimitive(l), JsonPrimitive(c), JsonPrimitive(v))

    private fun asOf(t: String): Long = ZonedDateTime.parse(t).toInstant().toEpochMilli()

    @Test fun fifteenMinuteVWAPAndHighAreCurrentDayOnlyAndFiveMinuteOrbIsUnavailable() {
        val old = listOf(
            candle("2026-11-10T09:15:00+05:30",49.0,51.0,48.0,50.0,1_000_000.0),
            candle("2026-11-10T09:30:00+05:30",49.0,51.0,48.0,50.0,1_000_000.0))
        val today = listOf(
            candle("2026-11-11T09:15:00+05:30",100.0,104.0,99.0,101.0,100.0),
            candle("2026-11-11T09:30:00+05:30",101.0,103.0,100.0,102.0,100.0),
            candle("2026-11-11T09:45:00+05:30",102.0,105.0,101.0,103.0,100.0),
            candle("2026-11-11T10:00:00+05:30",103.0,106.0,102.0,104.0,100.0),
            candle("2026-11-11T10:15:00+05:30",104.0,150.0,103.0,149.0,100.0))
        val f = FixedLocalStrategyEngine.buildFeatures(
            QuotePayload(lastPrice=104.0,bidPrice=103.9,offerPrice=104.1),
            HistoricalPayload(candles=old+today, intervalInMinutes=15), 15,
            asOf("2026-11-11T10:20:00+05:30"))
        assertEquals(4, f.sessionCandleCount)
        assertTrue(f.vwap!! > 100.0)
        assertEquals(104.0, f.orb15High!!, 0.0001)
        assertNull(f.orb5High)
        assertEquals(106.0, f.candles.last().high, 0.0001)
        assertEquals(6, f.candles.size)
        assertEquals((106.0-104.0)/106.0*100.0, f.sessionHighDistancePct!!, 0.0001)
    }

    @Test fun actualFiveMinuteBarsGiveIndependentFiveAndFifteenMinuteOpeningRanges() {
        val bars = listOf(
            candle("2026-11-11T09:15:00+05:30", 100.0, 102.0, 99.0, 101.0, 100.0),
            candle("2026-11-11T09:20:00+05:30", 101.0, 103.0, 100.0, 102.0, 100.0),
            candle("2026-11-11T09:25:00+05:30", 102.0, 104.0, 101.0, 103.0, 100.0),
            candle("2026-11-11T09:30:00+05:30", 103.0, 105.0, 102.0, 104.0, 100.0))
        val f = FixedLocalStrategyEngine.buildFeatures(QuotePayload(lastPrice=104.0),
            HistoricalPayload(candles=bars,intervalInMinutes=5),5,asOf("2026-11-11T09:40:00+05:30"))
        assertEquals(102.0,f.orb5High!!,0.0001)
        assertEquals(104.0,f.orb15High!!,0.0001)
        assertEquals(4,f.sessionCandleCount)
    }

    @Test fun textualOrInvalidTimestampsCannotFabricateSessionEvidence() {
        val data = listOf(candle("NOT_A_TIMESTAMP", 100.0, 105.0, 90.0, 100.0, 100.0))
        try {
            FixedLocalStrategyEngine.buildFeatures(QuotePayload(lastPrice=100.0),
                HistoricalPayload(candles=data,intervalInMinutes=15),15,
                asOf("2026-11-11T10:20:00+05:30"))
            fail("Unverified candles must not produce an entry")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("FIXED_NO_VERIFIED_COMPLETED_CANDLES"))
        }
    }
}
