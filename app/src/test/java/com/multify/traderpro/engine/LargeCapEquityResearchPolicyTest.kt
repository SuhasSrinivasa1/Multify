package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LargeCapEquityResearchPolicyTest {
    private val asOf = LocalDate.of(2026, 11, 20)
    private fun bars(n: Int = 125, symbol: String = "HDFCBANK",
                     f: (Int) -> Double = {100.0 + it * 0.25}): List<EquityEodBar> =
        (0 until n).map { i ->
            val price = f(i)
            EquityEodBar(asOf.minusDays(((n-i-1)*3L/2L)), symbol,
                price * .99, price, 1_000_000L + i * 1000L)
        }
    @Test fun sixMonthLowRequiresSixMonthHistoryAndLatestCompleteSession() {
        assertNull(LargeCapEquityResearchPolicy.nearSixMonthLow(bars(80),asOf,asOf,80))
        assertNull(LargeCapEquityResearchPolicy.nearSixMonthLow(bars(125).dropLast(1),
            asOf,asOf,125))
        assertNull(LargeCapEquityResearchPolicy.nearSixMonthLow(bars(125),asOf,asOf.plusDays(1),125))
    }
    @Test fun sixMonthLowIncludesZeroAndTenPctButNotEleven() {
        val low = bars(120, f={100.0})
        assertNotNull(LargeCapEquityResearchPolicy.nearSixMonthLow(low,asOf,asOf,120))
        val ten = low.dropLast(1) + low.last().copy(low=99.0,close=108.9)
        assertNotNull(LargeCapEquityResearchPolicy.nearSixMonthLow(ten,asOf,asOf,120))
        val tooFar = low.dropLast(1) + low.last().copy(low=99.0,close=112.0)
        assertNull(LargeCapEquityResearchPolicy.nearSixMonthLow(tooFar,asOf,asOf,120))
    }
    @Test fun sixMonthLowRejectsMissingOrInvalidTradePrices() {
        val bad = bars(120).map { it.copy(low=Double.NaN) }
        assertNull(LargeCapEquityResearchPolicy.nearSixMonthLow(bad,asOf,asOf,120))
    }
    @Test fun largeCapMomentumNoHistoryOrNoSectorPeersDoesNotSignal() {
        assertNull(LargeCapEquityResearchPolicy.scoreLargeCap("INFY","IT",bars(30),listOf(1.0,2.0,3.0),
            emptyList(),null,null))
        val candidate = LargeCapEquityResearchPolicy.scoreLargeCap(
            "INFY","IT",bars(90),emptyList(),emptyList(),110.0,100.0)!!
        assertFalse(candidate.intradayResearch)
        assertFalse(candidate.swingResearch)
    }
    @Test fun independentIntradayAndSwingResearchHaveDistinctConditions() {
        val rising=bars(90)
        val result=LargeCapEquityResearchPolicy.scoreLargeCap(
            "HDFCBANK","BANK",rising,listOf(1.5,1.5,1.5),
            listOf(.3,.3,.3),103.0,100.0)!!
        assertTrue(result.intradayResearch)
        assertTrue(result.swingResearch)
        val noLive=LargeCapEquityResearchPolicy.scoreLargeCap(
            "HDFCBANK","BANK",rising,listOf(1.5,1.5,1.5),
            emptyList(),null,null)!!
        assertFalse(noLive.intradayResearch)
        assertTrue(noLive.swingResearch)
    }
}
