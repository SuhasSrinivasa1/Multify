package com.multify.traderpro.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastNewsIngestionTest {
    // Test date handling independently of live NSE availability; offline unit tests are deterministic.
    @Test fun parsesRfcIsoAndNseLocalDates() {
        fun ms(s: String): Long? = ForecastNewsDatePolicy.parse(s)
        assertEquals(1793523600000L, ms("2026-11-01T09:00:00Z"))
        assertEquals(1793523600000L, ms("Sun, 01 Nov 2026 09:00:00 GMT"))
        assertEquals(1793523600000L, ms("2026-11-01T14:30:00+05:30"))
        assertEquals(1793523600000L, ms("2026-11-01 14:30:00"))
        assertEquals(1793523600000L, ms("1793523600"))
        assertNull(ms("no-date"))
    }
}
