package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardStopLossPolicyTest {
    @Test fun commonStopIsThreePercentBelowActualFill() {
        assertEquals(3.0, HardStopLossPolicy.MAX_LOSS_PCT, 0.0)
        assertEquals(97.0, HardStopLossPolicy.stopPrice(100.0), 0.0001)
        assertEquals(242.5, HardStopLossPolicy.stopPrice(250.0), 0.0001)
        assertEquals(120.5322, HardStopLossPolicy.stopPrice(124.26), 0.0001)
        assertEquals(3.0, ReleaseIntegrityManifest.HARD_STOP_LOSS_PCT, 0.0)
        assertTrue(ReleaseIntegrityManifest.verifyRuntimeConstants())
    }

    @Test fun breachOccursAtOrBelowThreePercentLoss() {
        assertFalse(HardStopLossPolicy.breached(100.0, 97.01))
        assertTrue(HardStopLossPolicy.breached(100.0, 97.0))
        assertTrue(HardStopLossPolicy.breached(100.0, 96.99))
        assertFalse(HardStopLossPolicy.breached(100.0, Double.NaN))
    }

    @Test fun profitableTrailingIsUnchangedAndAlwaysTighterOnceArmed() {
        assertEquals(0.55, SteppedTrailingPolicy.ARM_TRIGGER_PCT, 0.0)
        assertEquals(0.50, SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT, 0.0)
        assertEquals(0.10, SteppedTrailingPolicy.STEP_PCT, 0.0)
        assertEquals(null, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.54))
        assertEquals(0.50, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.55)!!, 0.0001)
        assertTrue(SteppedTrailingPolicy.stopPrice(100.0, 0.50) > HardStopLossPolicy.stopPrice(100.0))
    }
}
