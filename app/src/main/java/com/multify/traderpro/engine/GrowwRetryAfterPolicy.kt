package com.multify.traderpro.engine

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parse either form of HTTP Retry-After without shortening an explicit broker deadline. */
object GrowwRetryAfterPolicy {
    const val MAX_BROKER_RETRY_MS = 6L * 60L * 60L * 1_000L

    fun retryAfterMs(raw: String?, nowEpochMs: Long): Long {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        val seconds = text.toLongOrNull()
        if (seconds != null) {
            if (seconds <= 0L) return 0L
            return if (seconds > MAX_BROKER_RETRY_MS / 1_000L) MAX_BROKER_RETRY_MS
                else seconds * 1_000L
        }
        val until = runCatching {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        }.getOrNull() ?: return 0L
        return (until - nowEpochMs).coerceIn(0L, MAX_BROKER_RETRY_MS)
    }
}
