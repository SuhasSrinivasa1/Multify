package com.multify.traderpro.engine

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastReplayCandleDecoderTest {
    private fun row(ts: String): List<com.google.gson.JsonElement> =
        JsonParser.parseString("""[$ts,150.0,151.0,149.0,150.5,1250]""").asJsonArray.toList()

    @Test fun handlesGrowwEpochSecondsAndMilliseconds() {
        val result = ForecastReplayCandleDecoder.decode(listOf(
            row("1793245500"), row("1793245560000")
        ))
        assertEquals(2, result.bars.size)
        assertEquals(0, result.badTimestampRows)
        assertEquals(0, result.badOhlcRows)
        assertTrue(result.bars[0].atMs > 1_700_000_000_000L)
    }

    @Test fun handlesIsoLocalSpaceSeparatedOffsetAndUtcTimestamps() {
        val expected = LocalDateTime.parse("2026-10-29T09:15:00")
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val result = ForecastReplayCandleDecoder.decode(listOf(
            row("\"2026-10-29T09:15:00\""),
            row("\"2026-10-29 09:15:00\""),
            row("\"2026-10-29T09:15:00+05:30\""),
            row("\"2026-10-29T03:45:00Z\"")
        ))
        assertEquals(4, result.bars.size)
        assertTrue(result.bars.all { it.atMs == expected })
        assertEquals(0, result.badTimestampRows)
    }

    @Test fun rejectsMalformedTimestampAndMissingPricesWithoutFabricatingBars() {
        val broken = JsonParser.parseString("""["not-a-time",150,151,149,150.5,1250]""").asJsonArray.toList()
        val noOpen = JsonParser.parseString("""[1793245500,null,151,149,150.5,1250]""").asJsonArray.toList()
        val short = JsonArray().toList()
        val result = ForecastReplayCandleDecoder.decode(listOf(broken,noOpen,short))
        assertEquals(0,result.bars.size)
        assertEquals(1,result.badTimestampRows)
        assertEquals(1,result.badOhlcRows)
        assertEquals(1,result.shortRows)
    }
}
