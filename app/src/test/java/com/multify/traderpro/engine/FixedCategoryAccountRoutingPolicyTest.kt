package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedCategoryAccountRoutingPolicyTest {
    private val p = FixedCategoryAccountRoutingPolicy
    private val a = FixedCategoryAccountRoutingPolicy.Account.ALPHA
    private val b = FixedCategoryAccountRoutingPolicy.Account.BETA
    private val none = FixedCategoryAccountRoutingPolicy.Account.NONE

    @Test fun alphaLive429WithHealthyAlphaHistoricalSplitsTheCategories() {
        val plan = p.choose(false, false, true, true, true, false)
        assertEquals(b, plan.live)
        assertEquals(a, plan.historical)
        assertTrue(plan.isUsable)
        assertTrue(plan.splitAcrossAccounts)
    }

    @Test fun alphaHistorical429WithHealthyAlphaQuoteAndBetaHistoryUsesTheOppositeSplit() {
        val plan = p.choose(false, true, false, false, true, true)
        assertEquals(a, plan.live)
        assertEquals(b, plan.historical)
        assertTrue(plan.isUsable)
    }

    @Test fun originalDeterministicOneInSixKeepsBetaWhenThereIsCapacity() {
        val plan = p.choose(true, true, true, true, true, true)
        assertEquals(b, plan.live)
        assertEquals(b, plan.historical)
        val ordinary = p.choose(false, true, true, true, true, true)
        assertEquals(a, ordinary.live)
        assertEquals(a, ordinary.historical)
    }

    @Test fun betaHistoricalBudgetIsNotBurnedWhileAlphaHistoricalIsHealthy() {
        val plan = p.choose(true, false, true, true, true, false)
        assertEquals(b, plan.live)
        assertEquals(a, plan.historical)
        assertTrue(plan.splitAcrossAccounts)
    }

    @Test fun quotePressureUsesBetaLiveWithoutConsumingBetaHistoryBudget() {
        val route = p.choose(
            deterministicBeta = false,
            alphaLiveReady = true, alphaHistoricalReady = true,
            betaLiveReady = true, betaHistoricalReady = true,
            betaHistoricalHasHeadroom = false,
            preferBetaLiveForQuote = true
        )
        assertEquals(b, route.live)
        assertEquals(a, route.historical)
        assertTrue(route.splitAcrossAccounts)
    }

    @Test fun quotePressureCannotBypassMissingBetaLiveTransport() {
        val route = p.choose(
            deterministicBeta = false,
            alphaLiveReady = true, alphaHistoricalReady = true,
            betaLiveReady = false, betaHistoricalReady = true,
            betaHistoricalHasHeadroom = false,
            preferBetaLiveForQuote = true
        )
        assertEquals(a, route.live)
        assertEquals(a, route.historical)
    }

    @Test fun onlyOneBrokerCanBeRequiredPerCategoryAndPartialAvailabilityFailsClosed() {
        val combos = (0..15).map { bits ->
            val al = bits and 1 != 0
            val ah = bits and 2 != 0
            val bl = bits and 4 != 0
            val bh = bits and 8 != 0
            val route = p.choose(false, al, ah, bl, bh, true)
            assertEquals(p.available(al, ah, bl, bh), route.isUsable)
            assertEquals(al || bl, route.live != none)
            assertEquals(ah || bh, route.historical != none)
            route
        }
        assertEquals(16, combos.size)
    }
}
