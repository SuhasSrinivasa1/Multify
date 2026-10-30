package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastBetaPriorityPolicyTest {
    @Test fun sharedAlphaResearchStillYieldsToFixed() {
        assertFalse(ForecastBetaPriorityPolicy.canResearch(
            fixedPriorityRequested = true,
            criticalTradeInFlight = false,
            fixedDeferredForBroker = false,
            millisUntilNextFixed = 120_000L,
            fixedGuardMs = 35_000L
        ))
    }

    @Test fun separatelyAuthenticatedBetaCanUseItsOwnQuotaDuringFixed() {
        assertTrue(ForecastBetaPriorityPolicy.canResearch(
            fixedPriorityRequested = true,
            criticalTradeInFlight = false,
            fixedDeferredForBroker = false,
            millisUntilNextFixed = 120_000L,
            fixedGuardMs = 35_000L,
            allowIndependentBetaDuringFixed = true
        ))
    }

    @Test fun protectiveTradeAlwaysBlocksIndependentBeta() {
        assertFalse(ForecastBetaPriorityPolicy.canResearch(
            fixedPriorityRequested = true,
            criticalTradeInFlight = true,
            fixedDeferredForBroker = false,
            millisUntilNextFixed = 120_000L,
            fixedGuardMs = 35_000L,
            allowIndependentBetaDuringFixed = true
        ))
    }

    @Test fun nextFixedGuardStillAppliesWhenFixedIsNotRunning() {
        assertFalse(ForecastBetaPriorityPolicy.canResearch(
            fixedPriorityRequested = false,
            criticalTradeInFlight = false,
            fixedDeferredForBroker = false,
            millisUntilNextFixed = 25_000L,
            fixedGuardMs = 35_000L,
            allowIndependentBetaDuringFixed = true
        ))
        assertTrue(ForecastBetaPriorityPolicy.canResearch(
            fixedPriorityRequested = false,
            criticalTradeInFlight = false,
            fixedDeferredForBroker = false,
            millisUntilNextFixed = 45_000L,
            fixedGuardMs = 35_000L,
            allowIndependentBetaDuringFixed = true
        ))
    }
}
