package com.multify.traderpro.engine
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
class ForecastCandleShadowTest {
 @Test fun requiresFiveDistinctMinuteBars(){
  val epoch=1_800_000_000L/300L*300L
  val bars=(0 until 5).map{i->listOf(JsonPrimitive(epoch+i*60),JsonPrimitive(100.0),JsonPrimitive(102.0),
   JsonPrimitive(99.0),JsonPrimitive(100.0+i),JsonPrimitive(1000))}
  val asOf=(epoch+300L)*1000L
  val complete=ForecastCandleShadow.aggregateFiveMinute(HistoricalPayload(candles=bars),asOf)
  assertEquals(1,complete.completeBars);assertEquals(104.0,complete.lastClose!!,.0001)
  val partial=ForecastCandleShadow.aggregateFiveMinute(HistoricalPayload(candles=bars.take(4)),asOf)
  assertEquals(0,partial.completeBars);assertNull(partial.lastClose)
 }
 @Test fun parsesIsoLocalTimestamps(){
  val start=1_800_000_000_000L/300_000L*300_000L
  val bars=(0 until 5).map{i->
   val ts=Instant.ofEpochMilli(start+i*60_000L).atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
   listOf(JsonPrimitive(ts),JsonPrimitive(100.0),JsonPrimitive(102.0),JsonPrimitive(99.0),JsonPrimitive(101.0+i),JsonPrimitive(1000))
  }
  val x=ForecastCandleShadow.aggregateFiveMinute(HistoricalPayload(candles=bars),start+300_000L)
  assertEquals(1,x.completeBars);assertEquals(105.0,x.lastClose!!,.0001)
 }
}
