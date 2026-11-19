package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedMomentumContinuationShadowPolicyTest {
    private fun assess(i: FixedMomentumContinuationShadowPolicy.Inputs) =
        FixedMomentumContinuationShadowPolicy.evaluate(i)
    @Test fun veryHighRsiCanStillBeContinuation() {
        val x=assess(FixedMomentumContinuationShadowPolicy.Inputs(
            rsi=96.0,rsiSlope=2.0,rvol=9.0,volumeAcceleration=6.0,
            greenVolumeShare=0.9,macdHistogramSlope=0.3,
            ema9=120.0,ema20=110.0,ema50=100.0,
            ema9Slope=1.0,ema20Slope=0.6,vwapSlope=0.4,range52Position=0.99))
        assertEquals("FRESH_PARTICIPATION_CONTINUATION_HYPOTHESIS",
            x["label_hypothesis_not_verified_working_range"])
        assertEquals(false,x["working_range_certified"])
        assertEquals(false,x["changes_fixed_selection_or_execution"])
    }
    @Test fun matureWeakVolumeCombinationOnlyLabelsResearch() {
        val x=assess(FixedMomentumContinuationShadowPolicy.Inputs(
            rvol=0.8,volumeAcceleration=-1.0,macdHistogramSlope=-0.1,
            range52Position=0.94,rsiSlope=-2.0))
        assertEquals("MATURE_WEAK_PARTICIPATION_HYPOTHESIS",
            x["label_hypothesis_not_verified_working_range"])
    }
    @Test fun fadingRepeatCanDifferFromFirstEvent() {
        val x=assess(FixedMomentumContinuationShadowPolicy.Inputs(
            rvol=4.0,volumeAcceleration=-0.2,macdHistogramSlope=-0.1,
            rsiSlope=-4.5,greenVolumeShare=0.45))
        assertEquals("JOINT_MOMENTUM_DECELERATION_HYPOTHESIS",
            x["label_hypothesis_not_verified_working_range"])
    }
    @Test fun missingDataAndPreviousVolumeRemainNull() {
        val x=assess(FixedMomentumContinuationShadowPolicy.Inputs(rsi=85.0,
            lastCompleted15mVolume=300.0))
        assertEquals("INSUFFICIENT_DATA",x["label_hypothesis_not_verified_working_range"])
        assertNull(x["last_completed_15m_volume_vs_previous"])
        assertTrue((x["missing_input_names"] as List<*>).contains("rvol"))
    }
}
