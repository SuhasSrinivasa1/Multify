package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Read-only metrics. Never blocks Fixed recommendations, alters target calculations,
 * or changes the automatic execution policy.
 */
object FixedPublicationTimingPolicy {
    private val INDIA = ZoneId.of("Asia/Kolkata")
    private val SESSION_CLOSE = LocalTime.of(15, 30)

    enum class Bucket { NORMAL_SESSION, LIMITED_TIME, LAST_30_MINUTES, MARKET_CLOSED }

    fun bucket(postedAtMs: Long): Bucket {
        val time = Instant.ofEpochMilli(postedAtMs).atZone(INDIA).toLocalTime()
        return when {
            time >= SESSION_CLOSE || time < LocalTime.of(9, 15) -> Bucket.MARKET_CLOSED
            time >= LocalTime.of(15, 0) -> Bucket.LAST_30_MINUTES
            time >= LocalTime.of(14, 30) -> Bucket.LIMITED_TIME
            else -> Bucket.NORMAL_SESSION
        }
    }

    fun remainingMinutes(postedAtMs: Long): Long {
        val time = Instant.ofEpochMilli(postedAtMs).atZone(INDIA).toLocalTime()
        return if (time >= SESSION_CLOSE) 0L
            else java.time.Duration.between(time, SESSION_CLOSE).toMinutes().coerceAtLeast(0L)
    }
}
