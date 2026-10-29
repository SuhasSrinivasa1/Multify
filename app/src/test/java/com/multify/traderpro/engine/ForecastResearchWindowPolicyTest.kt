package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ForecastResearchWindowPolicyTest {
    @Test fun boostIsOnlyFiveMinutesAfterEachHalfHourOfNormalFixedScanning() {
        assertFalse(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(9, 44)))
        assertTrue(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(9, 45)))
        assertTrue(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(9, 49)))
        assertFalse(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(9, 50)))
        assertTrue(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(10, 15)))
        assertTrue(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(15, 15)))
        assertFalse(ForecastResearchWindowPolicy.isBoostWindow(LocalTime.of(15, 30)))
    }
    @Test fun boostNeverUsesFinalFixedGuardOrReservedApiHeadroom() {
        assertFalse(ForecastResearchWindowPolicy.canUseSpareCapacity(35_000L, 25, 25, 10, true))
        assertTrue(ForecastResearchWindowPolicy.canUseSpareCapacity(36_000L, 10, 6, 5, true))
        assertFalse(ForecastResearchWindowPolicy.canUseSpareCapacity(36_000L, 10, 5, 5, true))
        assertFalse(ForecastResearchWindowPolicy.canUseSpareCapacity(36_000L, 9, 6, 5, true))
        assertTrue(ForecastResearchWindowPolicy.canUseSpareCapacity(36_000L, 8, 4, 5, false))
    }
    @Test fun recentBrokerThrottleSuppressesResearchBoost() {
        val now = 1_800_000_000_000L
        assertFalse(ForecastResearchWindowPolicy.noRecent429(now, listOf(now - 40_000L)))
        assertTrue(ForecastResearchWindowPolicy.noRecent429(now, listOf(now - 31L * 60_000L)))
        assertTrue(ForecastResearchWindowPolicy.noRecent429(now, listOf(0L, 0L)))
    }
    @Test fun researchBatchSizeIsBoundedEvenDuringBoost() {
        assertEquals(4, ForecastResearchWindowPolicy.trancheSize(true, true, 12))
        assertEquals(3, ForecastResearchWindowPolicy.trancheSize(false, true, 12))
        assertEquals(12, ForecastResearchWindowPolicy.trancheSize(false, false, 12))
    }
}
