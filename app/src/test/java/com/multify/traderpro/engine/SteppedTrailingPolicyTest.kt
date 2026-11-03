package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteppedTrailingPolicyTest {
    @Test fun doesNotArmBeforePointFiveFivePercent() {
        assertNull(SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.54))
    }

    @Test fun armsAtPointFiveFiveAndProtectsPointFiveZero() {
        assertEquals(0.50, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.55)!!, 0.0001)
        assertEquals(100.50, SteppedTrailingPolicy.stopPrice(100.0, 0.50), 0.0001)
    }

    @Test fun advancesAfterPointSevenFivePercentNotPointSixFive() {
        assertEquals(0.50, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.65)!!, 0.0001)
        assertEquals(0.50, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.749)!!, 0.0001)
        assertEquals(0.60, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.75)!!, 0.0001)
        assertEquals(0.70, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.85)!!, 0.0001)
        assertEquals(0.80, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.95)!!, 0.0001)
        assertEquals(1.10, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 101.25)!!, 0.0001)
    }

    @Test fun nextTriggerKeepsFiveBasisPointBuffer() {
        assertEquals(0.55, SteppedTrailingPolicy.nextTriggerGainPct(null), 0.0001)
        assertEquals(0.75, SteppedTrailingPolicy.nextTriggerGainPct(0.50), 0.0001)
        assertEquals(0.85, SteppedTrailingPolicy.nextTriggerGainPct(0.60), 0.0001)
        assertEquals(0.95, SteppedTrailingPolicy.nextTriggerGainPct(0.70), 0.0001)
    }

    @Test fun stateNeverLoosensAnExistingHigherStop() {
        val s = SteppedTrailingPolicy.state(100.0, 100.60, 100.70)
        assertEquals(0.70, s.stopGainPct!!, 0.0001)
    }
}
