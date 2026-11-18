package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedWorkingRangeShadowPolicyTest {
    @Test fun explosiveDoesNotAuthorizeOrForbidABuy() {
        val row = FixedWorkingRangeShadowPolicy.evaluate(
            FixedWorkingRangeShadowPolicy.Inputs(
                price = 100.0, atr = 1.0, vwap = 96.0, rsi = 87.0,
                rsiSlope = -2.0, rvol = 23.0, volumeAcceleration = -3.0,
                macdHistogramSlope = -0.3, lastHigh = 103.0, lastLow = 97.0,
                lastClose = 100.0, prevClose = 99.0
            ), 1_000L
        )
        assertEquals("SPIKE_AND_DECELERATION_HYPOTHESIS", row["label_hypothesis_not_a_trade_signal"])
        assertEquals(false, row["working_range_qualified"])
        assertEquals(true, row["research_only"])
        assertEquals(true, row["does_not_approve_or_reject_trades"])
        assertEquals(0, row["no_extra_broker_calls"].let { if(it==true) 0 else -1 })
    }

    @Test fun extendedWinnerRemainsResearchOnly() {
        val row = FixedWorkingRangeShadowPolicy.evaluate(
            FixedWorkingRangeShadowPolicy.Inputs(
                price = 110.0, atr = 1.0, vwap = 103.0, rsi = 84.0,
                rsiSlope = 3.0, rvol = 7.0, volumeAcceleration = 1.0,
                macdHistogramSlope = 0.2, ema9 = 109.0, ema20 = 107.0,
                ema50 = 102.0, lastHigh = 110.0, lastLow = 108.0
            ), 1_000L
        )
        assertEquals("EXTENDED_TREND_CAN_STILL_WIN", row["label_hypothesis_not_a_trade_signal"])
        assertEquals(false, row["working_range_qualified"])
    }

    @Test fun missingDataStaysNullNotFalse() {
        val row = FixedWorkingRangeShadowPolicy.evaluate(
            FixedWorkingRangeShadowPolicy.Inputs(price = 100.0), 1_000L
        )
        val flags = row["shadow_hypotheses"] as Map<*, *>
        assertNull(flags["momentum_and_volume_joint_deceleration"])
        assertEquals("INSUFFICIENT_DATA", row["label_hypothesis_not_a_trade_signal"])
        assertTrue((row["missing_inputs"] as List<*>).contains("atr"))
    }
}
