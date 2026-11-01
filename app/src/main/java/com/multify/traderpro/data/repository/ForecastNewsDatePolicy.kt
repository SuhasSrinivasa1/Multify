package com.multify.traderpro.data.repository

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RSS feeds may publish RFC1123, ISO-8601, NSE local times, or Unix timestamps. */
object ForecastNewsDatePolicy {
    fun parse(value: String): Long? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        val number = raw.toLongOrNull()
        if (number != null) return when {
            number in 1_000_000_000_000L..9_999_999_999_999L -> number
            number in 1_000_000_000L..9_999_999_999L -> number * 1000L
            else -> null
        }
        runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrNull()?.let { return it }
        for (pattern in listOf("dd MMM yyyy HH:mm:ss", "dd-MMM-yyyy HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")) {
            val epoch = runCatching {
                LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                    .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
            }.getOrNull()
            if (epoch != null) return epoch
        }
        return null
    }
}
