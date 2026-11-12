package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedIntradayPatternResearchPolicyTest {
    @Test fun `all missing input flags remain nullable and never gate production`() {
        val features = FixedFeatureSnapshot(
            ltp = 350.0, bid = null, ask = null, spreadBps = null,
            orderBookImbalance = null, dayChangePct = null, marketCap = null,
            range52Position = null, vwap = null, ema9 = null, ema20 = null,
            atr14 = null, rsi14 = null, bollWidth = null, macdHistogram = null,
            trendSlopeAtr = null, rvol = null, donchianHigh = null,
            donchianLow = null, orb5High = null, orb5Low = null,
            orb15High = null, orb15Low = null,
            lastOpen = null, lastHigh = null, lastLow = null, lastClose = null,
            prevOpen = null, prevHigh = null, prevLow = null, prevClose = null,
            candles = emptyList()
        )
        val eval = FixedStrategyEvaluation("UNKNOWN", 0.0, 0.5, "None", "", emptyList(), features)
        val model = FixedMultifyFactorFamilies.evaluate(eval,"BREADTH_NEUTRAL")
        val x = FixedIntradayPatternResearchPolicy.analyse(eval,model,1_000_000L)
        assertNull(x["green_share_ge_80pct_hypothesis"])
        assertNull(x["green_rvol_depth_trio_hypothesis"])
        assertNull(x["peak_extension_and_deceleration_hypothesis"])
        assertEquals(false,x["trading_decisions_changed"])
    }
}
