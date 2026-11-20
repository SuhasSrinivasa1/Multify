package com.multify.traderpro.data.network

import org.junit.Assert.*
import org.junit.Test

class GrowwHotDataCacheInterceptorTest {
    private val cache = GrowwHotDataCacheInterceptor(recordAlphaStats = false)

    @Test fun fifteenMinuteHistoryIsReusedOnlyWithinItsCandleBucket() {
        val first = cache.normalizeEndBucket("2026-11-20 13:33:47", "15minute")
        val sameBucket = cache.normalizeEndBucket("2026-11-20 13:44:59", "15minute")
        val nextBucket = cache.normalizeEndBucket("2026-11-20 13:45:00", "15minute")
        assertEquals("2026-11-20 13:30:00", first)
        assertEquals(first, sameBucket)
        assertEquals("2026-11-20 13:45:00", nextBucket)
        assertNotEquals(first, nextBucket)
        assertEquals(10 * 60_000L,
            cache.ttlFor("/v1/historical/candles", "15minute"))
    }

    @Test fun fiveMinuteHistoryAndLiveQuotesKeepShortFreshnessWindows() {
        assertEquals(15_000L, cache.ttlFor("/v1/historical/candles", "5minute"))
        assertEquals(1_000L, cache.ttlFor("/v1/live-data/ltp", null))
        assertEquals(350L, cache.ttlFor("/v1/live-data/quote", null))
    }
}
