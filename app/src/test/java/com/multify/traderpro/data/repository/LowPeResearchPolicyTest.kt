package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LowPeResearchPolicyTest {
    private fun low(symbol: String, cap: String, distance: Double = 1.0): EquityLowObservation =
        EquityLowObservation(
            symbol=symbol, close=100.0, periodLow=99.0,
            distancePct=distance, tradingDays=55, latestDate=LocalDate.parse("2026-11-23"),
            periodLowDate=LocalDate.parse("2026-10-09"),
            oneMonthLow=99.0, oneMonthDistancePct=1.0, lookbackMonths=3,
            marketCapCategory=cap
        )
    private fun fullMarket(): Pair<Map<String,Double>,Map<String,String>> {
        val pe = (0 until 650).associate { "T${it}" to (10.0 + it) }
        val groups = pe.keys.associateWith { "OTHER" }
        return pe to groups
    }
    @Test fun nseDailySymbolPeParserDoesNotInventMissingOrNegativeEarnings() {
        val csv = buildString {
            append("Symbol,Symbol P/E,Adjusted P/E\n")
            for (i in 0 until 1250) append("S${i},${if (i==0) "-1" else if(i==1) "-" else "12.5"},10\n")
        }
        val parsed = NsePeReportResearchPolicy.parse(csv)
        assertNull(parsed["S0"])
        assertNull(parsed["S1"])
        assertEquals(12.5, parsed["S2"]!!, 0.0001)
        assertEquals(1248, parsed.size)
        assertTrue(runCatching { NsePeReportResearchPolicy.parse("Symbol,P/E\nTEST,5") }.isFailure)
    }
    @Test fun incompleteIndustryMappingNeverClaimsAWholeMarketSmallCapWinner() {
        val (pe,full)=fullMarket()
        val partial=full.filterKeys { it!="T0" }
        assertFalse(LowPeScreenResearchPolicy.marketIndustryCoverageComplete(pe,partial))
        assertFalse(LowPeScreenResearchPolicy.eligibleSmallCap(low("T1","SMALL_CAP"),pe,partial,true))
        assertFalse(LowPeScreenResearchPolicy.eligibleSmallCap(low("T1","SMALL_CAP"),pe,full,false))
    }
    @Test fun smallCapOnlyWhenNoLowerIndustryPeExistsAndSourceDateMatches() {
        val (basePe,baseIndustry)=fullMarket()
        val pe=basePe + mapOf("SMALL" to 4.5,"LOWER" to 3.5)
        val industry=baseIndustry + mapOf("SMALL" to "CHEMICALS","LOWER" to "CHEMICALS")
        assertFalse(LowPeScreenResearchPolicy.eligibleSmallCap(low("SMALL","SMALL_CAP"),pe,industry,true))
        val betterPe=pe + ("LOWER" to 7.5)
        assertTrue(LowPeScreenResearchPolicy.eligibleSmallCap(low("SMALL","SMALL_CAP"),betterPe,industry,true))
        val visible=LowPeScreenResearchPolicy.visibleAndSorted(
            listOf(low("T1","LARGE_CAP"),low("T0","MID_CAP"),low("SMALL","SMALL_CAP")),
            betterPe,industry,true
        )
        assertEquals(listOf("SMALL","T0","T1"),visible.map { it.symbol })
        assertTrue(visible.first().peRatio!!>0.0)
    }
    @Test fun incompleteOrStaleDataShowsLargeMidOnlyWithMissingRatiosLast() {
        val rows=listOf(low("A","MID_CAP"),low("B","SMALL_CAP"),low("C","LARGE_CAP"))
        val visible=LowPeScreenResearchPolicy.visibleAndSorted(
            rows,mapOf("A" to 14.0,"C" to 7.0),mapOf("B" to "IT"),true)
        assertEquals(listOf("C","A"),visible.map { it.symbol })
        val stale=LowPeScreenResearchPolicy.visibleAndSorted(
            rows,mapOf("A" to 14.0,"C" to 7.0),mapOf("B" to "IT"),false)
        assertTrue(stale.all { it.peRatio==null })
        assertEquals(2,stale.size)
    }
}
