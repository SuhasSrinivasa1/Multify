package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class WorkingRangeExitComparisonPolicyTest {
    private fun sample(seconds: Long, price: Double) =
        WorkingRangeExitComparisonPolicy.Sample(seconds * 1_000L, price)

    @Test fun baselineUsesTheActualMinusOnePercentWorkingRangeStop() {
        val entry = 100.0
        val result = WorkingRangeExitComparisonPolicy.evaluate(
            entry, 1.1161, listOf(sample(1, 99.0))
        )
        assertEquals("STOP_FIRST_SAMPLED", result.fixedTargetStop.state)
        assertEquals(-1.0, result.fixedTargetStop.sampledReturnPct!!, 0.0001)
    }

    @Test fun steppedProtectionIsShadowOnlyAndLocksAtLeastHalfPercent() {
        val result = WorkingRangeExitComparisonPolicy.evaluate(
            100.0, 1.1161, listOf(sample(1, 100.60), sample(2, 100.49))
        )
        assertEquals("TRAIL_EXIT_SAMPLED", result.steppedProtection.state)
        assertEquals(0.50, result.steppedProtection.sampledReturnPct!!, 0.0001)
    }

    @Test fun partialThenTrailUsesHalfPositionAtFiftyFiveBasisPoints() {
        val result = WorkingRangeExitComparisonPolicy.evaluate(
            100.0, 1.1161, listOf(sample(1, 100.60), sample(2, 100.49))
        )
        assertEquals("PARTIAL_THEN_TRAIL_EXIT_SAMPLED", result.partialThenTrail.state)
        assertEquals(0.525, result.partialThenTrail.sampledReturnPct!!, 0.0001)
    }

    @Test fun immaturePathsRemainCensoredRatherThanCountedAsLosses() {
        val result = WorkingRangeExitComparisonPolicy.evaluate(
            100.0, 1.1161, listOf(sample(1, 100.20))
        )
        assertEquals("UNRESOLVED_CENSORED", result.fixedTargetStop.state)
        assertNull(result.fixedTargetStop.sampledReturnPct)
        assertTrue(result.fixedTargetStop.note.contains("SPARSE_CLIENT_LTP"))
    }

    @Test fun targetHitIsNotRealizedPnl() {
        val result = WorkingRangeExitComparisonPolicy.evaluate(
            100.0, 1.1161, listOf(sample(1, 101.20))
        )
        assertEquals("TARGET_FIRST_SAMPLED", result.fixedTargetStop.state)
        assertEquals(1.1161, result.fixedTargetStop.sampledReturnPct!!, 0.0001)
    }
}
