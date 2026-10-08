package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTrajectoryMathTest {
    private fun features(ltp: Double, vwap: Double, e9: Double, e20: Double, slope: Double, rvol: Double, imbalance: Double, day: Double, rsi: Double) = FeatureSnapshot(
        ltp=ltp,bid=null,ask=null,spreadBps=8.0,orderBookImbalance=imbalance,dayChangePct=day,marketCap=null,range52Position=.5,
        vwap=vwap,ema9=e9,ema20=e20,atr14=2.0,rsi14=rsi,bollWidth=null,macdHistogram=null,trendSlopeAtr=slope,rvol=rvol,
        donchianHigh=null,donchianLow=null,orb5High=null,orb5Low=null,orb15High=null,orb15Low=null,lastOpen=null,lastHigh=null,lastLow=null,lastClose=null,
        prevOpen=null,prevHigh=null,prevLow=null,prevClose=null,candles=emptyList()
    )

    @Test fun strongUpTrendPrefersLong() {
        val d=MarketTrajectoryMath.classify(features(104.0,100.0,103.0,101.0,1.2,2.2,.55,2.8,64.0), .7,-.4)
        assertEquals("LONG", d.preferredSide)
        assertEquals("UP_TREND", d.regime)
    }

    @Test fun strongDownTrendPrefersShort() {
        val d=MarketTrajectoryMath.classify(features(96.0,100.0,97.0,99.0,-1.2,2.0,-.6,-2.5,35.0), -.4,.75)
        assertEquals("SHORT", d.preferredSide)
        assertEquals("DOWN_TREND", d.regime)
    }

    @Test fun disagreementCanBecomeOscillating() {
        val d=MarketTrajectoryMath.classify(features(100.2,100.0,100.5,100.0,-1.0,1.4,.1,.1,50.0), .55,-.45)
        assertTrue(d.waveiness >= .25)
    }
}
