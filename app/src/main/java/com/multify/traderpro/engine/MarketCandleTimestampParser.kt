package com.multify.traderpro.engine
import com.google.gson.JsonElement
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
object MarketCandleTimestampParser {
    val NSE_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    fun toEpochMillis(value: JsonElement?): Long? {
        if (value == null || !value.isJsonPrimitive) return null
        val p=value.asJsonPrimitive
        if (p.isNumber) return normalize(runCatching { p.asLong }.getOrNull() ?: return null)
        val text=runCatching { p.asString.trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        text.toLongOrNull()?.let { normalize(it)?.let { ms -> return ms } }
        val x=if (' ' in text && 'T' !in text) text.replaceFirst(' ','T') else text
        runCatching { Instant.parse(x).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(x,DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { ZonedDateTime.parse(x,DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(x,DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(NSE_ZONE).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        return runCatching { LocalDateTime.parse(x,DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(NSE_ZONE).toInstant().toEpochMilli() }.getOrNull()
    }
    private fun normalize(v:Long):Long?=when {
        v in 1_000_000_000L..9_999_999_999L -> v*1000L
        v in 1_000_000_000_000L..9_999_999_999_999L -> v
        v in 1_000_000_000_000_000L..9_999_999_999_999_999L -> v/1000L
        else -> null
    }
}
