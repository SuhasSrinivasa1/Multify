package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardStopLossPolicyTest {
    @Test fun commonStopIsFivePercentBelowActualFill() {
        assertEquals(5.0, HardStopLossPolicy.MAX_LOSS_PCT, 0.0)
        assertEquals(95.0, HardStopLossPolicy.stopPrice(100.0), 0.0001)
        assertEquals(237.5, HardStopLossPolicy.stopPrice(250.0), 0.0001)
        assertEquals(118.047, HardStopLossPolicy.stopPrice(124.26), 0.0001)
        assertEquals(5.0, ReleaseIntegrityManifest.HARD_STOP_LOSS_PCT, 0.0)
        assertTrue(ReleaseIntegrityManifest.verifyRuntimeConstants())
    }

    @Test fun breachOccursOnlyAtOrBelowFivePercentLossNotAtOldTwoPercentFloor() {
        assertFalse(HardStopLossPolicy.breached(100.0, 98.0))
        assertFalse(HardStopLossPolicy.breached(100.0, 95.01))
        assertTrue(HardStopLossPolicy.breached(100.0, 95.0))
        assertTrue(HardStopLossPolicy.breached(100.0, 94.99))
        assertFalse(HardStopLossPolicy.breached(100.0, Double.NaN))
    }

    @Test fun oldLossFloorGttMigratesButProfitableProtectionIsNeverLoosened() {
        assertTrue(HardStopLossPolicy.shouldMigrateHardStopGtt(100.0, 98.0, 95.0))
        assertTrue(HardStopLossPolicy.shouldMigrateHardStopGtt(100.0, 94.0, 95.0))
        assertFalse(HardStopLossPolicy.shouldMigrateHardStopGtt(100.0, 97.0, 95.0))
        assertFalse(HardStopLossPolicy.shouldMigrateHardStopGtt(100.0, 99.0, 95.0))
        assertFalse(HardStopLossPolicy.shouldMigrateHardStopGtt(100.0, 95.0, 95.0))
        assertFalse(HardStopLossPolicy.shouldMigrateHardStopGtt(100.0, 100.5, 95.0))
    }

    @Test fun oldLossFloorIsIgnoredDuringMigrationButProfitableTrailingStopIsPreserved() {
        assertNull(HardStopLossPolicy.previousProfitProtection(
            100.0, 98.0, 98.0, HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT))
        assertNull(HardStopLossPolicy.previousProfitProtection(
            100.0, 98.0, null, HoldingSmartOrderPolicy.MODE_NONE))
        assertEquals(97.0, HardStopLossPolicy.previousProfitProtection(
            100.0, 97.0, 97.0, HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)!!, 0.0)
        assertEquals(100.5, HardStopLossPolicy.previousProfitProtection(
            100.0, 100.5, 100.5, HoldingSmartOrderPolicy.MODE_TRAILING_GTT)!!, 0.0)
        assertEquals(100.5, HardStopLossPolicy.previousProfitProtection(
            100.0, 100.5, null, HoldingSmartOrderPolicy.MODE_NONE)!!, 0.0)
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
