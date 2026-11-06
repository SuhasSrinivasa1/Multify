package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TenMinuteCandleAggregatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val start = LocalDate.of(2026, 11, 6).atTime(LocalTime.of(9, 15))
        .atZone(zone).toInstant().toEpochMilli()
    private fun bar(offset: Long, open: Double, high: Double, low: Double, close: Double, volume: Double) =
        MarketCandleBar(start + offset * 60_000L, open, high, low, close, volume)

    @Test fun aggregatesTwoCompletedFiveMinuteBarsIntoSessionAnchoredTenMinuteBars() {
        val result = TenMinuteCandleAggregator.aggregate(listOf(
            bar(0, 100.0, 102.0, 99.0, 101.0, 100.0),
            bar(5, 101.0, 105.0, 100.0, 104.0, 150.0),
            bar(10, 104.0, 106.0, 103.0, 105.0, 120.0),
            bar(15, 105.0, 107.0, 104.0, 106.0, 130.0)
        ), start + 20 * 60_000L)
        assertEquals(2, result.size)
        assertEquals(start, result[0].atMs)
        assertEquals(100.0, result[0].open, 0.0001)
        assertEquals(105.0, result[0].high, 0.0001)
        assertEquals(99.0, result[0].low, 0.0001)
        assertEquals(104.0, result[0].close, 0.0001)
        assertEquals(250.0, result[0].volume, 0.0001)
        assertEquals(start + 10 * 60_000L, result[1].atMs)
        assertEquals(250.0, result[1].volume, 0.0001)
    }

    @Test fun rejectsMissingPairsAndUnfinishedBars() {
        val gap = listOf(
            bar(0, 100.0, 102.0, 99.0, 101.0, 100.0),
            bar(10, 104.0, 106.0, 103.0, 105.0, 120.0)
        )
        assertTrue(TenMinuteCandleAggregator.aggregate(gap, start + 20 * 60_000L).isEmpty())
        val pair = listOf(bar(0, 100.0, 102.0, 99.0, 101.0, 100.0),
            bar(5, 101.0, 105.0, 100.0, 104.0, 150.0))
        assertTrue(TenMinuteCandleAggregator.aggregate(pair, start + 9 * 60_000L).isEmpty())
        assertEquals(1, TenMinuteCandleAggregator.aggregate(pair, start + 10 * 60_000L).size)
    }
}
