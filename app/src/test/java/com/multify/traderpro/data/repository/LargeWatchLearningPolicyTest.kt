package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test

class LargeWatchLearningPolicyTest {
    private fun evidence(nifty:Double?,sector:Double?,relative:Double?):LargeWatchEvidence {
        val m=LargeWatchFeaturePolicy.METRICS.associateWith { null as Double? }.toMutableMap()
        m["nifty_eod_move_pct"]=nifty
        m["sector_eod_move_pct"]=sector
        m["sector_5d_move_pct"]=relative
        m["return_5d_pct"]=3.5
        m["rsi14"]=60.0
        m["volume_avg5_to_avg20"]=1.5
        m["ema9"]=101.0
        m["ema20"]=99.0
        return LargeWatchEvidence(m,"2026-11-23","2026-11-23","BULLISH","NIFTY IT","BULLISH",true)
    }
    @Test fun fiveWinnersNeverFreezeAndUnknownMarketDoesNotCreateAlignedSignals() {
        assertFalse(LargeWatchLearningPolicy.freezeEligible(5,1,5,0,0,0))
        assertTrue(LargeWatchLearningPolicy.wilsonLower(5,5)<0.85)
        assertEquals(setOf("BASELINE","MOMENTUM_CONSISTENT"),
            LargeWatchLearningPolicy.variants(evidence(null,null,null)))
    }
    @Test fun marketSectorResearchVariantsAreVersionedHypotheses() {
        val x=LargeWatchLearningPolicy.variants(evidence(0.5,1.0,0.2))
        assertTrue(x.contains("BASELINE"))
        assertTrue(x.contains("NIFTY_AND_SECTOR_NONNEGATIVE"))
        assertTrue(x.contains("SECTOR_RELATIVE_LEADER"))
        assertTrue(x.contains("MOMENTUM_CONSISTENT"))
        assertTrue(x.contains("MARKET_SECTOR_COMBINED"))
    }
    @Test fun freezeRequiresIndependentDaysAndChronologicalHoldoutWithLowerBounds() {
        assertFalse(LargeWatchLearningPolicy.freezeEligible(120,1,120,50,1,50))
        assertFalse(LargeWatchLearningPolicy.freezeEligible(120,30,90,50,10,40))
        assertFalse(LargeWatchLearningPolicy.freezeEligible(120,30,120,20,10,20))
        assertTrue(LargeWatchLearningPolicy.freezeEligible(200,30,194,80,12,79))
    }
}
