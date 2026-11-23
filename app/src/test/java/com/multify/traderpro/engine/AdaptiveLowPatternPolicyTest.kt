package com.multify.traderpro.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLowPatternPolicyTest {
    private val day=LocalDate.of(2026,11,23)
    private fun data(n:Int=70,last:Double=935.0,secondLow:Double=900.0,
                     rebound:Boolean=true,ohlc:Boolean=true):List<EquityEodBar>{
        val closes=MutableList(n){955.0}
        val lows=MutableList(n){950.0}
        val episodes=if(n>100)listOf(20,57,89) else listOf(12,34)
        for((i,idx) in episodes.withIndex()){
            val l=if(i==episodes.lastIndex)secondLow else 900.0
            if(idx+11>=n)continue
            closes[idx]=l+5.0
            lows[idx]=l
            for(j in 1..10) {
                closes[idx+j]=if(rebound) (l+5.0+j*11.0).coerceAtMost(l+64.0)
                    else l+10+j*1.5
                lows[idx+j]=closes[idx+j]-4.0
            }
        }
        for(j in 0..9){
            val idx=n-10+j
            closes[idx]=978.0 - j*(978.0-last)/9.0
            lows[idx]=closes[idx]-3.0
        }
        return closes.indices.map{idx->
            val c=closes[idx]
            EquityEodBar(
                date=day.minusDays((n-1-idx).toLong()),symbol="DEMO",
                low=lows[idx],close=c,volume=200000L+(idx*200),
                open=if(ohlc)c-0.8 else null,
                high=if(ohlc)c+2.0 else null
            )
        }
    }

    @Test fun threeMonthDoubleBottomWithTwoProvenRapidReboundsQualifiesFromAbove() {
        val match=AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,data())!!
        assertEquals(3,match.months)
        assertTrue(match.reactions.size>=2)
        assertTrue(match.distanceAboveSupportPct>0.0)
        assertTrue(match.distanceAboveSupportPct<5.0)
        assertEquals(900.0,match.supportPrice,0.00001)
        assertTrue(match.preferredEntry>match.supportPrice)
        assertTrue(match.entryHigh<match.latestClose)
        assertEquals("APPROACHING_FROM_ABOVE",match.phase)
        assertTrue(match.reactions.all{it.sessionsToRebound in 1..10})
        assertTrue(match.reactions.all{it.reboundPctFromLow>=5.0})
        assertTrue(match.candleCoverage>=2)
    }

    @Test fun rejectsBelowSupportOrExactlyFivePercentAway() {
        assertNull(AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,data(last=899.0)))
        assertNull(AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,data(last=945.0)))
    }

    @Test fun lowWithoutHistoricalReboundIsNotAnOpportunity() {
        assertNull(AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,
            data(rebound=false)))
    }

    @Test fun failedSupportClusterDoesNotQualify() {
        assertNull(AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,
            data(secondLow=840.0)))
    }

    @Test fun threeMonthDataIsNotSixMonthEvidence() {
        assertNull(AdaptiveLowPatternPolicy.evaluate("DEMO",6,day,data()))
        val six=AdaptiveLowPatternPolicy.evaluate("DEMO",6,day,data(n=130))!!
        assertTrue(six.reactions.size>=2)
        assertTrue(six.distanceAboveSupportPct<5.0)
    }

    @Test fun missingHistoricalOhlcNeverGeneratesFakeHammerOrAtr() {
        val x=AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,data(ohlc=false))!!
        assertNull(x.atr14)
        assertEquals(0,x.candleCoverage)
        assertTrue(x.reactions.all{it.lowerWickReversal==null})
    }

    @Test fun corporateActionLikeHugeGapIsExcludedUntilAdjusted() {
        val bars=data().toMutableList()
        bars[22]=bars[22].copy(close=1800.0,high=1801.0)
        assertNull(AdaptiveLowPatternPolicy.evaluate("DEMO",3,day,bars))
    }
}
