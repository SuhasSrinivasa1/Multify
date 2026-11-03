package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GrowwRetryAfterPolicyTest {
    @Test fun preservesLongerThanTwoMinuteBrokerSeconds() {
        val p = GrowwRetryAfterPolicy
        assertEquals(600_000L, p.retryAfterMs("600", 1_000L))
        assertEquals(0L, p.retryAfterMs(null, 1_000L))
        assertEquals(0L, p.retryAfterMs("garbage", 1_000L))
        assertEquals(p.MAX_BROKER_RETRY_MS, p.retryAfterMs("99999999999", 1_000L))
    }
    @Test fun readsRfc1123BrokerDates() {
        val now = Instant.parse("2026-11-03T09:15:00Z").toEpochMilli()
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now + 600_000L), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        assertEquals(600_000L, GrowwRetryAfterPolicy.retryAfterMs(date, now))
    }
}
