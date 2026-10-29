package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastEarlyEntryPoliciesTest {
    private fun ist(date: String): Long = LocalDateTime.parse(date)
        .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    @Test fun forecastBuyClosesAt1530AndAfterTarget() {
        assertTrue(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T15:29:59"), true, "ACTIVE", true))
        assertFalse(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T15:30:00"), true, "ACTIVE", true))
        assertFalse(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T13:00:00"), true, "TARGET_HIT", true))
        assertFalse(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T13:00:00"), false, "ACTIVE", true))
    }

    @Test fun repeatRequiresCompletedTargetCooldownAndPullback() {
        val now = 1_000_000L
        assertTrue(ForecastRepeatEntryPolicy.mayPublish(null, null, null, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("ACTIVE", 1000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("MISSED", 1000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", 900_000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", 1000L, 105.0, 105.0, now))
        assertTrue(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", 1000L, 105.0, 104.0, now))
    }

    @Test fun fullMarketEarlyRankDoesNotFavorCompletedSpikes() {
        val winners = ForecastEarlyDiscoveryPolicy.rank(
            mapOf("EARLY" to 101.0, "SPIKED" to 110.0, "FLAT" to 100.0),
            mapOf("EARLY" to 100.8, "SPIKED" to 101.0, "FLAT" to 100.1),
            mapOf("EARLY" to 100.0, "SPIKED" to 100.0, "FLAT" to 100.0),
            100
        )
        assertEquals("EARLY", winners.first().symbol)
        assertFalse(winners.any { it.symbol == "SPIKED" })
    }
}
