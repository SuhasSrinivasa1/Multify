package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.domain.ParsedSignal
import com.multify.traderpro.domain.SignalType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FixedLocalStrategyEngineTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    private fun bar(at: LocalDateTime, open: Double, high: Double, low: Double,
                    close: Double, volume: Double): List<JsonElement> = listOf(
        JsonPrimitive(at.atZone(ist).toInstant().toEpochMilli()),
        JsonPrimitive(open), JsonPrimitive(high), JsonPrimitive(low),
        JsonPrimitive(close), JsonPrimitive(volume)
    )

    private fun quote(price: Double = 107.0) = QuotePayload(
        lastPrice = price, bidPrice = price - .1, offerPrice = price + .1,
        totalBuyQuantity = 200.0, totalSellQuantity = 100.0,
        volume = 100_000L, dayChangePct = 2.0
    )

    private fun historical15m(asOf: LocalDateTime): List<List<JsonElement>> {
        val rows = mutableListOf<List<JsonElement>>()
        val priorDay = asOf.toLocalDate().minusDays(1)
        for (i in 0 until 25) {
            val at = priorDay.atTime(9, 15).plusMinutes(15L * i)
            val px = 500.0 + i
            rows += bar(at, px, px + 1, px - 1, px + .5, 100_000.0)
        }
        val today = asOf.toLocalDate()
        rows += bar(today.atTime(9, 15), 100.0, 102.0, 99.0, 101.0, 100.0)
        rows += bar(today.atTime(9, 30), 101.0, 103.0, 100.0, 102.0, 200.0)
        rows += bar(today.atTime(9, 45), 102.0, 105.0, 101.0, 104.0, 300.0)
        rows += bar(today.atTime(10, 0), 104.0, 108.0, 103.0, 107.0, 400.0)
        rows += bar(today.atTime(10, 15), 107.0, 110.0, 106.0, 109.0, 500.0) // still forming
        rows += listOf(
            JsonPrimitive("not-a-time"), JsonPrimitive(109.0), JsonPrimitive(111.0),
            JsonPrimitive(108.0), JsonPrimitive(110.0), JsonPrimitive(600.0)
        )
        return rows
    }

    @Test fun sessionVwapAndHighUseOnlyTodaysCompletedCandles() {
        val asOf = LocalDateTime.of(2026, 11, 11, 10, 16)
        val result = FixedLocalStrategyEngine.buildFeatures(
            quote(), HistoricalPayload(candles = historical15m(asOf), intervalInMinutes = 15),
            intervalMinutes = 15, asOfMs = asOf.atZone(ist).toInstant().toEpochMilli()
        )
        val expected = (
            (102.0 + 99.0 + 101.0) / 3.0 * 100.0 +
            (103.0 + 100.0 + 102.0) / 3.0 * 200.0 +
            (105.0 + 101.0 + 104.0) / 3.0 * 300.0 +
            (108.0 + 103.0 + 107.0) / 3.0 * 400.0
        ) / 1_000.0
        assertEquals(expected, result.vwap!!, 0.000001)
        assertEquals(4, result.sessionCandleCount)
        assertEquals(1, result.incompleteCandleCount)
        assertEquals(1, result.timestampInvalidCandleCount)
        assertEquals(29, result.timestampValidCandleCount)
        assertEquals(102.0, result.orb15High!!, 0.000001)
        assertNull("15m bars cannot independently establish ORB5", result.orb5High)
        assertEquals(108.0, result.candles.filter {
            java.time.Instant.ofEpochMilli(it.ts).atZone(ist).toLocalDate() == asOf.toLocalDate()
        }.maxOf { it.high }, 0.000001)
        assertEquals(107.0, result.lastClose!!, 0.000001)
        assertEquals(asOf.toLocalDate().atTime(10, 15).atZone(ist).toInstant().toEpochMilli(),
            result.latestCompletedCandleAtMs)
        assertTrue(result.candles.all { it.ts > 1_700_000_000_000L })
    }


    @Test fun highRsiWithImprovingMomentumIsNotPenalisedAsExhaustion() {
        val asOf = LocalDateTime.of(2026, 11, 11, 10, 16)
        val base = FixedLocalStrategyEngine.buildFeatures(
            quote(), HistoricalPayload(candles = historical15m(asOf), intervalInMinutes = 15),
            intervalMinutes = 15, asOfMs = asOf.atZone(ist).toInstant().toEpochMilli()
        )
        val strong = base.copy(
            ltp = 107.0, vwap = 106.8, ema9 = 108.0, ema20 = 106.5,
            atr14 = 1.0, rsi14 = 80.0, rsiSlope = 2.0, rvol = 1.2
        )
        val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = "TEST",
            rawText = "unit-test", confidence = 1.0)
        val accelerating = FixedLocalStrategyEngine.evaluateLong(signal, strong, includeEventPrior = false)
        assertTrue(accelerating.votes.any { it.name == "RSI trend regime" })
        assertFalse(accelerating.votes.any { it.name == "RSI exhaustion filter" })

        val decelerating = FixedLocalStrategyEngine.evaluateLong(
            signal, strong.copy(rsiSlope = -1.0), includeEventPrior = false
        )
        assertTrue(decelerating.votes.any { it.name == "RSI exhaustion filter" })
    }

    @Test fun fiveMinuteBarsProduceIndependentOrb5AndOrb15() {
        val asOf = LocalDateTime.of(2026, 11, 11, 9, 31)
        val rows = mutableListOf<List<JsonElement>>()
        val prior = asOf.toLocalDate().minusDays(1)
        for (i in 0 until 25) {
            val at = prior.atTime(9, 15).plusMinutes(15L * i)
            val px = 100.0 + i
            rows += bar(at, px, px + 1, px - 1, px + .5, 100_000.0)
        }
        val today = asOf.toLocalDate()
        rows += bar(today.atTime(9, 15), 100.0, 101.0, 99.0, 100.5, 100.0)
        rows += bar(today.atTime(9, 20), 100.5, 102.0, 100.0, 101.0, 110.0)
        rows += bar(today.atTime(9, 25), 101.0, 103.0, 100.5, 102.0, 120.0)
        rows += bar(today.atTime(9, 30), 102.0, 104.0, 101.0, 103.0, 130.0) // still forming
        val result = FixedLocalStrategyEngine.buildFeatures(
            quote(102.0), HistoricalPayload(candles = rows, intervalInMinutes = 5),
            intervalMinutes = 5, asOfMs = asOf.atZone(ist).toInstant().toEpochMilli()
        )
        assertEquals(3, result.sessionCandleCount)
        assertEquals(101.0, result.orb5High!!, 0.000001)
        assertEquals(103.0, result.orb15High!!, 0.000001)
        assertEquals(1, result.incompleteCandleCount)
    }

    @Test fun invalidTimestampsNeverBecomeSyntheticIndexBars() {
        val asOf = LocalDateTime.of(2026, 11, 11, 10, 16)
        val rows = historical15m(asOf).map { row ->
            if (row.first().asString == "not-a-time") row else row
        }
        val result = FixedLocalStrategyEngine.buildFeatures(
            quote(), HistoricalPayload(candles = rows, intervalInMinutes = 15),
            intervalMinutes = 15, asOfMs = asOf.atZone(ist).toInstant().toEpochMilli()
        )
        assertEquals(1, result.timestampInvalidCandleCount)
        assertTrue(result.candles.none { it.ts in 0L..100L })
    }
}
