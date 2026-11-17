package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FixedScanTimingGuardrailTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int) =
        LocalDate.of(2026, 11, 17).atTime(h, m).atZone(india).toInstant().toEpochMilli()

    @Test fun healthyThirtyEightSecondScanIsNormal() {
        val a = FixedScanTimingGuardrail.assess(at(9, 43), 38_100L, 90_400L, 0, 0)
        assertEquals("NORMAL", a.status)
        assertEquals("EARLY_0930_0945", a.timeOfDayBucket)
        assertTrue(a.scanElapsedWithinBudget)
        assertFalse(a.productionBuyVeto)
    }

    @Test fun scanOverFortyFiveSecondsIsFlaggedButNeverVetoes() {
        val a = FixedScanTimingGuardrail.assess(at(9, 48), 59_600L, 90_500L, 0, 0)
        assertEquals("SLOW_SCAN", a.status)
        assertFalse(a.scanElapsedWithinBudget)
        assertFalse(a.productionBuyVeto)
        assertEquals("OBSERVE_ONLY", a.action)
    }

    @Test fun cadenceDriftIsFlaggedIndependently() {
        val a = FixedScanTimingGuardrail.assess(at(10, 10), 32_000L, 120_000L, 0, 0)
        assertEquals("CADENCE_DRIFT", a.status)
        assertTrue(a.scanElapsedWithinBudget)
        assertEquals(false, a.startSpacingWithinBudget)
    }

    @Test fun broker429TakesPriorityAndIsExplicit() {
        val a = FixedScanTimingGuardrail.assess(at(10, 5), 162_000L, 180_000L, 1, 0)
        assertEquals("BROKER_RATE_LIMITED", a.status)
        assertTrue(a.brokerRateLimited)
        assertFalse(a.productionBuyVeto)
    }

    @Test fun missingPriorSpacingIsBaselineNotFailure() {
        val a = FixedScanTimingGuardrail.assess(at(9, 15), 32_000L, null, 0, 0)
        assertEquals("BASELINE_NO_SPACING", a.status)
        assertNull(a.startSpacingWithinBudget)
    }

    @Test fun timeBucketsUseIndiaLocalClock() {
        assertEquals("OPENING_0915_0930",
            FixedScanTimingGuardrail.assess(at(9, 20), 30_000L, 90_000L, 0, 0).timeOfDayBucket)
        assertEquals("MIDMORNING_0945_1100",
            FixedScanTimingGuardrail.assess(at(10, 5), 30_000L, 90_000L, 0, 0).timeOfDayBucket)
        assertEquals("MIDDAY_1100_1400",
            FixedScanTimingGuardrail.assess(at(12, 0), 30_000L, 90_000L, 0, 0).timeOfDayBucket)
    }
}
