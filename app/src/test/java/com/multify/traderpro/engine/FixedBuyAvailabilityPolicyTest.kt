package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FixedBuyAvailabilityPolicyTest {
    private fun t(s: String): Long = LocalDateTime.parse(s)
        .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    @Test fun endOfSessionDisablesButtonEvenWhenCachedStatusIsActive() {
        assertTrue(FixedBuyAvailabilityPolicy.canBuy(t("2026-10-26T15:29:59"), true, "ACTIVE", true))
        assertFalse(FixedBuyAvailabilityPolicy.canBuy(t("2026-10-26T15:30:00"), true, "ACTIVE", true))
        assertFalse(FixedBuyAvailabilityPolicy.canBuy(t("2026-10-26T16:00:00"), true, "ACTIVE", true))
    }
    @Test fun terminalTargetAndClosedDaysAreAlwaysDisabled() {
        val open = t("2026-10-26T13:00:00")
        assertFalse(FixedBuyAvailabilityPolicy.canBuy(open, true, "TARGET_HIT", true))
        assertFalse(FixedBuyAvailabilityPolicy.canBuy(open, true, "SESSION_ENDED", true))
        assertFalse(FixedBuyAvailabilityPolicy.canBuy(open, false, "ACTIVE", true))
        assertFalse(FixedBuyAvailabilityPolicy.canBuy(t("2026-10-25T13:00:00"), true, "ACTIVE", true))
    }
}
