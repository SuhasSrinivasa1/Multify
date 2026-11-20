package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class WorkingRangeCandidatePolicyTest {
    private fun input(
        rsi: Double = 93.0, rvol: Double = 8.9, accel: Double = 10.0,
        green: Double = 0.86, ema9: Double = 110.0, ema20: Double = 108.0,
        ema50: Double = 104.0, ema9Slope: Double = 0.5, ema20Slope: Double = 0.3,
        macd: Double = 0.05, atr: Double = 7.28, price: Double = 767.9,
        minutes: Double = 180.0, vwap: Double = 745.0
    ) = WorkingRangeCandidatePolicy.Inputs(
        price = price, atr = atr, rsi = rsi, rvol = rvol,
        volumeAcceleration = accel, greenVolumeShare = green,
        ema9 = ema9, ema20 = ema20, ema50 = ema50,
        ema9Slope = ema9Slope, ema20Slope = ema20Slope,
        macdHistogramSlope = macd, vwap = vwap,
        range52Position = 0.99, spreadBps = null,
        orderBookImbalance = null, minutesToClose = minutes
    )

    @Test fun highRsiIsNotSoloVetoWhenPriceRemainsInsideTheVwapBand() {
        val result = WorkingRangeCandidatePolicy.assess(input(rsi = 96.0, vwap = 742.5))
        assertTrue(result.matches)
        assertTrue(result.vwapExtensionAtr!! <= 3.5)
        assertTrue(result.targetRequirementAtr!! <= 1.6)
    }

    @Test fun vwapExtensionAboveThreeAndAHalfAtrIsOutsideWorkingRange() {
        val result = WorkingRangeCandidatePolicy.assess(input(vwap = 740.0))
        assertFalse(result.matches)
        assertTrue(result.reasons.contains("VWAP_EXTENSION_ABOVE_3_50_ATR"))
    }

    @Test fun wideSpreadIsOutsideWorkingRange() {
        val result = WorkingRangeCandidatePolicy.assess(input().copy(spreadBps = 35.1))
        assertFalse(result.matches)
        assertTrue(result.reasons.contains("SPREAD_ABOVE_35_BPS"))
    }

    @Test fun replayedGpilIsOutsideTheNewVwapEntryBandAndMustNotBeCalledValidated() {
        // 19-Nov GPIL matched the candidate envelope but did not reach target in
        // the available market path. This test guards against repeating the old 9/9 claim.
        val result = WorkingRangeCandidatePolicy.assess(input(
            rsi = 83.35, rvol = 2.68, accel = 0.76, green = 0.909,
            ema9 = 224.9, ema20 = 223.1, ema50 = 221.0,
            ema9Slope = 0.57, ema20Slope = 0.35, macd = 0.118,
            price = 234.79, atr = 1.776, vwap = 225.0
        ))
        assertFalse(result.matches)
        assertTrue(result.reasons.contains("VWAP_EXTENSION_ABOVE_3_50_ATR"))
    }

    @Test fun extremeImpulseAboveThirtyIsOutsidePilotRange() {
        val result = WorkingRangeCandidatePolicy.assess(input(
            rsi = 85.1, rvol = 21.57, accel = 62.15, green = 0.918,
            price = 315.0, atr = 2.468, minutes = 44.0
        ))
        assertFalse(result.matches)
        assertTrue(result.reasons.contains("VOLUME_ACCELERATION_OUTSIDE_PILOT_RANGE"))
    }

    @Test fun weakEmaStructureAndLowRsiAreOutsidePilotRange() {
        val result = WorkingRangeCandidatePolicy.assess(input(
            rsi = 68.8, ema9 = 386.7, ema20 = 385.4, ema50 = 386.46,
            price = 390.6, atr = 2.59
        ))
        assertFalse(result.matches)
        assertTrue(result.reasons.contains("RSI_BELOW_75"))
        assertTrue(result.reasons.contains("EMA9_20_50_NOT_ALIGNED"))
    }

    @Test fun lateEntryBelowThirtyEightMinutesIsNotPublished() {
        val result = WorkingRangeCandidatePolicy.assess(input(minutes = 37.9))
        assertFalse(result.matches)
        assertTrue(result.reasons.contains("LESS_THAN_38_MINUTES_TO_CLOSE"))
    }

    @Test fun missingSpreadDoesNotMasqueradeAsZeroAtCandidateStage() {
        val result = WorkingRangeCandidatePolicy.assess(input(spreadBps = null))
        assertTrue(result.matches)
        assertNull(result.targetRequirementAtr?.takeIf { !it.isFinite() })
    }
}
