package com.multify.traderpro.engine
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
class FixedSessionFeatureTest {
 private fun bar(d:LocalDate,t:String,o:Double,h:Double,l:Double,c:Double,v:Double):List<JsonElement> =
  listOf(JsonPrimitive(d.toString()+"T"+t),JsonPrimitive(o),JsonPrimitive(h),JsonPrimitive(l),JsonPrimitive(c),JsonPrimitive(v))
 @Test fun sessionVwapAndOpeningRangesUseOnlyToday(){
  val today=LocalDate.parse("2026-10-30");val history=mutableListOf<List<JsonElement>>()
  for(offset in 4 downTo 1){val d=today.minusDays(offset.toLong())
   history+=bar(d,"09:15:00",49.0,51.0,48.0,50.0,100000.0)
   history+=bar(d,"09:20:00",50.0,52.0,49.0,51.0,100000.0)
   history+=bar(d,"09:25:00",51.0,53.0,50.0,52.0,100000.0)
  }
  history+=bar(today,"09:15:00",99.0,101.0,98.0,100.0,100.0)
  history+=bar(today,"09:20:00",100.0,102.0,99.0,101.0,100.0)
  history+=bar(today,"09:25:00",101.0,103.0,100.0,102.0,100.0)
  history+=bar(today,"09:30:00",102.0,104.0,101.0,103.0,100.0)
  val asOf=today.atTime(9,35).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
  val x=FixedLocalStrategyEngine.buildFeatures(QuotePayload(lastPrice=103.0,bidPrice=102.9,offerPrice=103.1),
   HistoricalPayload(candles=history,intervalInMinutes=5),5,today,asOf)
  assertEquals(4,x.sessionCandleCount);assertTrue(x.vwap!!>99.0);assertTrue(x.vwap!!<103.0)
  assertEquals(101.0,x.orb5High!!,.0001);assertEquals(103.0,x.orb15High!!,.0001)
  assertTrue(x.sessionHighDistancePct!!>0.0);assertTrue(x.sessionHighDistancePct!!<2.0)
 }
 @Test fun invalidTextTimestampsDoNotInventSessionMetrics(){
  val h=HistoricalPayload(candles=(0 until 5).map{i->listOf(JsonPrimitive("unknown-"+i),JsonPrimitive(100.0),
   JsonPrimitive(101.0),JsonPrimitive(99.0),JsonPrimitive(100.0),JsonPrimitive(1000))},intervalInMinutes=5)
  val x=FixedLocalStrategyEngine.buildFeatures(QuotePayload(lastPrice=100.0,bidPrice=99.9,offerPrice=100.0),
   h,5,LocalDate.parse("2026-10-30"))
  assertEquals(0,x.sessionCandleCount);assertEquals(null,x.vwap);assertEquals(null,x.orb5High)
 }
}
