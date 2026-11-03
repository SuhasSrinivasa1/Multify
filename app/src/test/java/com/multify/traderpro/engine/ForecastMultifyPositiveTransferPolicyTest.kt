package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastMultifyPositiveTransferPolicyTest {
    @Test fun onlyVerifiedSameSessionOfficialWinnersTransfer() {
        val morning = java.time.LocalDateTime.of(2026, 11, 3, 10, 0)
            .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val noon = morning + 120 * 60_000L
        val tomorrow = morning + 24 * 60 * 60_000L
        val p = ForecastMultifyPositiveTransferPolicy
        assertTrue(p.qualifiesVerifiedClose("LIVE", "BUY", 11, 12, morning, noon, 1.2))
        org.junit.Assert.assertFalse(p.qualifiesVerifiedClose("LIVE", "BUY", 11, null, morning, noon, 1.2))
        org.junit.Assert.assertFalse(p.qualifiesVerifiedClose("LIVE", "BUY", 11, 12, morning, tomorrow, 1.2))
        org.junit.Assert.assertFalse(p.qualifiesVerifiedClose("LIVE", "BUY", 11, 12, morning, noon, -0.2))
        org.junit.Assert.assertFalse(p.qualifiesVerifiedClose("HISTORICAL", "BUY", 11, 12, morning, noon, 1.2))
        org.junit.Assert.assertFalse(p.qualifiesVerifiedClose("LIVE", "BUY", 11, 12, morning, noon, null))
    }

    @Test
    fun onlyMatchingPositiveCasesAddBonus() {
        val cases = listOf(MultifyPositiveTransferCase("ABC", 1.2, 2.0))
        assertTrue(ForecastMultifyPositiveTransferPolicy.rankingBonus("ABC", cases) > 0.0)
        assertEquals(0.0, ForecastMultifyPositiveTransferPolicy.rankingBonus("XYZ", cases), 0.0)
    }
}
