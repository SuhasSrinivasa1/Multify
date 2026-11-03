package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FixedPublicationTimingPolicyTest {
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026,11,3,h,m)
        .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    @Test fun classifiesActualClockNotScanStart() {
        val p = FixedPublicationTimingPolicy
        assertEquals(FixedPublicationTimingPolicy.Bucket.NORMAL_SESSION, p.bucket(at(14,20)))
        assertEquals(FixedPublicationTimingPolicy.Bucket.LIMITED_TIME, p.bucket(at(14,40)))
        assertEquals(FixedPublicationTimingPolicy.Bucket.LAST_30_MINUTES, p.bucket(at(15,20)))
        assertEquals(FixedPublicationTimingPolicy.Bucket.MARKET_CLOSED, p.bucket(at(15,30)))
        assertEquals(10L, p.remainingMinutes(at(15,20)))
        assertEquals(0L, p.remainingMinutes(at(15,30)))
    }
}
