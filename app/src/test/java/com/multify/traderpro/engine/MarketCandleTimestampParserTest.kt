package com.multify.traderpro.engine
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
class MarketCandleTimestampParserTest {
 @Test fun parsesEpochSecondsAndMilliseconds(){
  assertEquals(1_800_000_000_000L,MarketCandleTimestampParser.toEpochMillis(JsonPrimitive(1_800_000_000L)))
  assertEquals(1_800_000_000_000L,MarketCandleTimestampParser.toEpochMillis(JsonPrimitive(1_800_000_000_000L)))
 }
 @Test fun parsesIsoLocalAsIndiaAndOffsetAsInstant(){
  val expected=LocalDateTime.parse("2026-10-30T09:15:00").atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
  assertEquals(expected,MarketCandleTimestampParser.toEpochMillis(JsonPrimitive("2026-10-30T09:15:00")))
  assertEquals(expected,MarketCandleTimestampParser.toEpochMillis(JsonPrimitive("2026-10-30T09:15:00+05:30")))
 }
 @Test fun invalidTimestampDoesNotBecomeAnIndex(){ assertNull(MarketCandleTimestampParser.toEpochMillis(JsonPrimitive("not-a-time"))) }
}
