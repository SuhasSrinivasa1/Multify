package com.multify.traderpro.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FixedSameDayHighEvidencePolicyTest {
    private val IST=ZoneId.of("Asia/Kolkata")
    private fun millis(day: Int, h: Int, min: Int)=
        LocalDate.of(2026,11,day).atTime(LocalTime.of(h,min))
            .atZone(IST).toInstant().toEpochMilli()
    private fun candle(atMs: Long, high: Double)=FixedCandle(
        ts=atMs/1_000L,open=high-1,high=high,low=high-2,close=high-1,volume=1000.0)

    @Test fun ignoresPreviousDaysEvenWhenPriorHighIsGreater() {
        val now=millis(4,11,0)
        val candles=listOf(candle(millis(3,15,0),999.0),
            candle(millis(4,9,15),472.0),candle(millis(4,9,30),473.5))
        assertEquals(473.5,FixedSameDayHighEvidencePolicy.todayHighFromBars(candles,now)!!,0.0001)
    }

    @Test fun excludesUnknownTimestampsAndFutureCandles() {
        val now=millis(4,10,0)
        val malformed=FixedCandle(0L,100.0,600.0,99.0,101.0,100.0)
        val future=candle(millis(4,12,0),1000.0)
        val valid=candle(millis(4,9,15),470.0)
        assertNull(FixedSameDayHighEvidencePolicy.todayHighFromBars(
            listOf(malformed,future,valid),now))
        assertEquals(475.0,FixedSameDayHighEvidencePolicy.todayHighFromBars(
            listOf(malformed,future,valid,candle(millis(4,9,30),475.0)),now)!!,0.0001)
    }

    @Test fun rejectsOutsideSessionAndSameDayGapWithoutTwoBars() {
        val now=millis(4,10,0)
        assertNull(FixedSameDayHighEvidencePolicy.todayHighFromBars(
            listOf(candle(millis(4,9,0),1000.0),
                candle(millis(3,12,0),1000.0)),now))
    }

    @Test fun supportsMillisecondTimestampsAsWell() {
        val now=millis(4,10,0)
        val a=candle(millis(4,9,15),472.0).copy(ts=millis(4,9,15))
        val b=candle(millis(4,9,30),475.0).copy(ts=millis(4,9,30))
        assertEquals(475.0,FixedSameDayHighEvidencePolicy.todayHighFromBars(listOf(a,b),now)!!,0.0001)
    }
}
