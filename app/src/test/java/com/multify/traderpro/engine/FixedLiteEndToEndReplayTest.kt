package com.multify.traderpro.engine

import com.multify.traderpro.data.network.BetaReadOnlyInterceptor
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic offline replay of safety and decision boundaries.
 * These tests prove software policy invariants, not Groww fills, device latency,
 * exchange tick completeness, or market profitability.
 *
 * A mirrored baseline suite runs against v9.0.25 in the comparison workflow.
 */
class FixedLiteEndToEndReplayTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private fun ist(day: Int, hour: Int, minute: Int): ZonedDateTime =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(india)

    private fun safety(
        vwapAtr: Double?, targetAtr: Double?, distancePct: Double?,
        publicationAge: Long = 45_000L,
        highSource: String = "TIMESTAMP_VERIFIED_TODAY_15M_CANDLES"
    ): FixedLateEntrySafetyPolicy.Decision {
        val now = 5_000_000L
        val publishedAt = now - publicationAge
        val quoteReceived = publishedAt - 1_000L
        return FixedLateEntrySafetyPolicy.assess(
            nowMs = now,
            publishedAtMs = publishedAt,
            sourceQuoteReceivedAtMs = quoteReceived,
            vwapExtensionAtr = vwapAtr,
            targetRequiredAtr = targetAtr,
            verifiedHighDistancePct = distancePct,
            highSource = highSource
        )
    }

    @Test fun sameOneDayHistoricalPeakExamplesRemainExecutionOnlyHolds() {
        // Reconstructed diagnostic factor examples from the one available day.
        // No intrabar path, actual Fixed fill, or profitability is inferred.
        assertEquals(
            "FIXED_EXTENDED_PEAK_TARGET_CAPACITY_RISK",
            safety(7.386, 2.511, 0.3).reason
        )
        assertEquals(
            "FIXED_EXTENDED_PEAK_TARGET_CAPACITY_RISK",
            safety(5.544, 2.305, 0.4).reason
        )
        // EXIDEIND's smaller VWAP and target-ATR requirements do not trip the combined hold.
        assertTrue(safety(1.684, 1.314, 0.2).executable)
        // High RSI or VWAP extension by itself is not a universal Fixed veto.
        assertTrue(safety(6.0, 1.5, 0.2).executable)
        // Missing authoritative high source does not create an invented peak-risk hit.
        assertTrue(safety(6.0, 2.5, 0.1, highSource = "CLIENT_UNVERIFIED").executable)
        assertFalse(safety(2.0, 1.0, 10.0, publicationAge = 90_001L).executable)
        assertEquals(
            "FIXED_POST_PUBLICATION_ENTRY_EXPIRED",
            safety(2.0, 1.0, 10.0, publicationAge = 90_001L).reason
        )
        val missing = FixedLateEntrySafetyPolicy.assess(
            nowMs = 100_000L, publishedAtMs = null,
            sourceQuoteReceivedAtMs = 99_000L,
            vwapExtensionAtr = 1.0, targetRequiredAtr = 1.0,
            verifiedHighDistancePct = 1.0, highSource = "BROKER_REPORTED_DAY_HIGH"
        )
        assertFalse(missing.executable)
        assertEquals("FIXED_PUBLICATION_PROVENANCE_UNAVAILABLE", missing.reason)
    }

    @Test fun workingRangeExitsStaySourceSpecificAndPriceBasedOnFill() {
        val fill = 326.50
        assertEquals(323.235, WorkingRangeExitPolicy.stopPrice(fill), 0.000_001)
        assertNull(WorkingRangeExitPolicy.exitReason(fill, 330.0, 327.0))
        assertEquals(
            WorkingRangeExitPolicy.STOP_REASON,
            WorkingRangeExitPolicy.exitReason(fill, 330.0, 322.0)
        )
        assertEquals(
            WorkingRangeExitPolicy.TARGET_REASON,
            WorkingRangeExitPolicy.exitReason(fill, 330.0, 330.1)
        )
        assertTrue(WorkingRangeExitPolicy.owns("WRM-20261120-1234", ""))
        assertFalse(WorkingRangeExitPolicy.owns("FXD-20261120-1234", "FIXED_LONG"))
        assertNull(WorkingRangeExitPolicy.exitReason(fill, 330.0, Double.NaN))
        assertEquals(1.0, WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT, 0.0)
        // Unlike Fixed, Working Range is not stepped-trail armed at +0.55%.
        assertNull(WorkingRangeExitPolicy.exitReason(fill, 330.0, fill * 1.0055))
    }

    @Test fun ambiguousAcknowledgementsNeverCountAsResolvedIds() {
        assertNull(BrokerOrderAcknowledgementPolicy.usableId(null))
        assertNull(BrokerOrderAcknowledgementPolicy.usableId(""))
        assertNull(BrokerOrderAcknowledgementPolicy.usableId("   "))
        assertEquals("order-123", BrokerOrderAcknowledgementPolicy.usableId(" order-123 "))
        assertTrue(
            BrokerOrderAcknowledgementPolicy.unresolvedMessage(
                "BUY", "TEST", "FXD-1"
            ).contains("reconcile broker order before any retry")
        )
        assertFalse(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", null, "ACTIVE"))
        assertFalse(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", "GTT-1", "TRIGGERED"))
        assertTrue(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", "GTT-1", "ACTIVE"))
        assertTrue(GttProtectionReadinessPolicy.verified("TRAILING_GTT", "GTT-2", "OPEN"))
    }

    @Test fun workingRangeAutoBuyNeverRevivesPreActivationOrReusesReservedEvent() {
        fun check(
            enabled: Boolean = true,
            activated: Long = 100L,
            confirmed: Long = 200L,
            state: String = "IN_RANGE",
            autoBuyState: String = "NOT_ATTEMPTED",
            now: Long = 300L,
            halt: Boolean = false
        ) = WorkingRangeAutoBuyPolicy.decide(
            enabled = enabled, safetyHalt = halt, activatedAtMs = activated,
            confirmedAtMs = confirmed, state = state, autoBuyState = autoBuyState,
            nowMs = now
        )
        assertTrue(check().eligible)
        assertEquals("WORKING_RANGE_DISABLED", check(enabled = false).reason)
        assertEquals("SAFETY_HALT", check(halt = true).reason)
        assertEquals("ACTIVATION_TIMESTAMP_MISSING", check(activated = 0L).reason)
        assertEquals("CONFIRMED_BEFORE_ACTIVATION", check(confirmed = 100L).reason)
        assertEquals("EVENT_ALREADY_RESERVED_OR_CONSUMED", check(autoBuyState = "RESERVED").reason)
        assertEquals("NOT_CURRENTLY_IN_RANGE", check(state = "EXPIRED").reason)
        assertEquals("CLOCK_ORDER_INVALID", check(now = 150L).reason)
        assertEquals(
            "CONFIRMATION_EXPIRED",
            check(now = 200L + WorkingRangeAutoBuyPolicy.MAX_CONFIRMATION_AGE_MS + 1L).reason
        )
        assertFalse(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(1_000L, 1_000L, 2_000L, 2_010L))
        assertTrue(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(1_000L, 1_200L, 2_000L, 2_010L))
    }

    @Test fun betaReadOnlyAndNseSessionBoundaries() {
        assertTrue(BetaReadOnlyInterceptor.permits("GET", "/v1/live-data/ltp"))
        assertTrue(BetaReadOnlyInterceptor.permits("GET", "/v1/historical/candles"))
        assertFalse(BetaReadOnlyInterceptor.permits("POST", "/v1/order/create"))
        assertFalse(BetaReadOnlyInterceptor.permits("DELETE", "/v1/order-advance/delete"))
        assertFalse(BetaReadOnlyInterceptor.permits("GET", "/v1/positions/user"))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist(20, 8, 18)))
        assertTrue(NseEquitySessionCalendar.isFixedScanWindow(ist(20, 9, 15)))
        assertTrue(NseEquitySessionCalendar.isFixedScanWindow(ist(20, 15, 14)))
        assertFalse(NseEquitySessionCalendar.isFixedNewEntryWindow(ist(20, 15, 15)))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist(20, 20, 18)))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist(21, 10, 30)))
    }
}
