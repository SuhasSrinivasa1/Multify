package com.multify.traderpro.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastNetTargetPolicyTest {
    @Test fun computedTargetClearsAtLeastHalfPercentNetAfterEstimatedCostsAndSpread() {
        val entry = 299.29
        val budget = 10_000L
        val spreadBps = 20.0
        val target = ForecastAdaptiveSelectionPolicy.minimumTargetPctForNetReturn(
            entryPrice = entry,
            budgetRupees = budget,
            spreadBps = spreadBps
        )
        val economics = ForecastAdaptiveSelectionPolicy.economics(
            entryPrice = entry,
            targetPct = target,
            budgetRupees = budget,
            spreadBps = spreadBps,
            atrPct = null
        )
        assertTrue(target > 0.0)
        assertTrue(economics.profitableAfterCosts)
        assertTrue(economics.netReturnPct + 1e-6 >= ForecastAdaptiveSelectionPolicy.MIN_NET_RETURN_PCT)
    }

    @Test fun missingSpreadUsesConservativeSpreadAssumptionForTargetSizing() {
        val known = ForecastAdaptiveSelectionPolicy.minimumTargetPctForNetReturn(
            299.29, 10_000L, 10.0
        )
        val missing = ForecastAdaptiveSelectionPolicy.minimumTargetPctForNetReturn(
            299.29, 10_000L, null
        )
        assertTrue(missing >= known)
    }
}
