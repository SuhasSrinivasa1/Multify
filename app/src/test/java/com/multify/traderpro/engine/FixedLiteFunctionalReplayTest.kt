package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Offline, deterministic policy replay for the two-screen Fixed Lite product.
 * No broker credentials, network requests, BUY/SELL orders or GTT mutations are made.
 */
class FixedLiteFunctionalReplayTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(hour: Int, minute: Int): Long =
        LocalDate.of(2026, 11, 4).atTime(LocalTime.of(hour, minute)).atZone(ist).toInstant().toEpochMilli()

    @Test fun fixedPublicationFreshnessAndActionabilityReplay() {
        // Discovery must be complete and the candidate must be PRIMARY with its required repeat evidence.
        assertFalse(FixedForecastPublicationPolicy.isReady(false, true, true, 1))
        assertFalse(FixedForecastPublicationPolicy.isReady(true, false, true, 1))
        assertTrue(FixedForecastPublicationPolicy.isReady(true, true, true, 1))
        assertFalse(FixedForecastPublicationPolicy.isReady(true, true, false, 1))
        assertTrue(FixedForecastPublicationPolicy.isReady(true, true, false, 2))

        // The first PRIMARY reference is used to assess drift; a strong day alone is not a veto.
        val quote = QuotePayload(
            lastPrice = 500.0, high = 500.3, dayChangePct = 10.0,
            bidPrice = 499.9, offerPrice = 500.0, volume = 100_000L
        )
        val verdict = FixedEntryActionabilityPolicy.evaluate(at(11, 15), quote, 498.0, 1.1161043)
        assertTrue(verdict.actionable)
        assertEquals("CAUTION", verdict.status)

        // Auto-Buy only sees a fresh, post-activation publication; stale and pre-toggle calls fail closed.
        assertTrue(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(1_000_000L, 1_150_000L))
        assertFalse(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(1_000_000L, 1_300_001L))
        assertFalse(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(2_000L, 2_000L))
        assertTrue(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(2_001L, 2_000L))
    }

    @Test fun workingRangeNeedsCandidateAndIndependentConfirmationThenFreshEntry() {
        val candidate = WorkingRangeCandidatePolicy.assess(
            WorkingRangeCandidatePolicy.Inputs(
                price = 767.9, atr = 7.28, rsi = 93.0, rvol = 8.9,
                volumeAcceleration = 10.0, greenVolumeShare = 0.86,
                ema9 = 110.0, ema20 = 108.0, ema50 = 104.0,
                ema9Slope = 0.5, ema20Slope = 0.3, macdHistogramSlope = 0.05,
                vwap = 700.0, range52Position = 0.99, spreadBps = null,
                orderBookImbalance = null, minutesToClose = 180.0
            )
        )
        assertTrue(candidate.matches)

        assertFalse(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(1_000L, 1_000L, 1_500L, 2_000L))
        assertFalse(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(1_000L, 2_000L, 0L, 2_500L))
        assertTrue(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(1_000L, 2_000L, 1_500L, 2_500L))

        val allowed = WorkingRangeAutoBuyPolicy.decide(
            enabled = true, safetyHalt = false, activatedAtMs = 1_000L,
            confirmedAtMs = 2_500L, state = "IN_RANGE",
            autoBuyState = "NOT_ATTEMPTED", nowMs = 3_000L
        )
        assertTrue(allowed.eligible)
        assertFalse(WorkingRangeAutoBuyPolicy.decide(
            enabled = true, safetyHalt = false, activatedAtMs = 1_000L,
            confirmedAtMs = 2_500L, state = "IN_RANGE",
            autoBuyState = "NOT_ATTEMPTED", nowMs = 123_000L + 2_500L
        ).eligible)
        assertEquals(
            "FIRST_QUALIFIED_PRICE_EXTENSION_TOO_HIGH",
            WorkingRangeEntryMomentumPolicy.runupRejectionReason(100.0, 101.0)
        )
        assertNotNull(WorkingRangeEntryMomentumPolicy.rejectionReason(100.0, 98.9, 2_500L, 3_000L))
    }

    @Test fun alphaBetaAndBrokerProtectionBoundariesReplay() {
        assertTrue(BetaReadOnlyInterceptor.permits("GET", "/v1/live-data/quote"))
        assertTrue(BetaReadOnlyInterceptor.permits("GET", "/v1/historical/candles"))
        assertFalse(BetaReadOnlyInterceptor.permits("POST", "/v1/order/create"))
        assertFalse(BetaReadOnlyInterceptor.permits("POST", "/v1/order-advance/create"))
        assertFalse(BetaReadOnlyInterceptor.permits("GET", "/v1/holdings/user"))

        assertFalse(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", null, "ACTIVE"))
        assertFalse(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", "gtt-1", "TRIGGERED"))
        assertTrue(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", "gtt-1", "ACTIVE"))
        assertTrue(GttProtectionReadinessPolicy.verified("TRAILING_GTT", "gtt-2", "OPEN"))

        // This records the actual inherited runtime constant; it is not silently changed by the UI refresh.
        assertEquals(3.0, HardStopLossPolicy.MAX_LOSS_PCT, 0.0)
        assertEquals(0.55, SteppedTrailingPolicy.ARM_TRIGGER_PCT, 0.0)
        assertEquals(0.50, SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT, 0.0)
        assertEquals(0.10, SteppedTrailingPolicy.STEP_PCT, 0.0)
    }
}
