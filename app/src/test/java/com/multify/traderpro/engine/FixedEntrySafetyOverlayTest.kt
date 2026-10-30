package com.multify.traderpro.engine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class FixedEntrySafetyOverlayTest {
 private fun f(ltp:Double=100.0,vwap:Double?=99.5,ema20:Double?=99.0,atr:Double?=1.0,rsi:Double?=60.0,
  spread:Double?=10.0,sessionCount:Int=6,lastHigh:Double?=100.2,prevHigh:Double?=100.0,
  lastClose:Double?=100.0,prevClose:Double?=99.8,highDistance:Double?=0.8)=FixedFeatureSnapshot(
  ltp=ltp,bid=99.9,ask=100.0,spreadBps=spread,orderBookImbalance=.1,dayChangePct=1.0,marketCap=null,range52Position=null,
  vwap=vwap,ema9=99.8,ema20=ema20,atr14=atr,rsi14=rsi,bollWidth=.02,macdHistogram=.1,trendSlopeAtr=.1,rvol=1.2,
  donchianHigh=100.0,donchianLow=98.0,orb5High=99.5,orb5Low=98.5,orb15High=99.8,orb15Low=98.0,
  lastOpen=99.8,lastHigh=lastHigh,lastLow=99.5,lastClose=lastClose,prevOpen=99.5,prevHigh=prevHigh,
  prevLow=99.0,prevClose=prevClose,candles=emptyList(),sessionCandleCount=sessionCount,sessionHighDistancePct=highDistance
 )
 @Test fun allowsHealthyNonExtendedEntry(){assertTrue(FixedEntrySafetyOverlay.assess(f()).eligible)}
 @Test fun rejectsPeakExtensionAndOverboughtMomentum(){
  val x=FixedEntrySafetyOverlay.assess(f(ltp=110.0,vwap=100.0,ema20=101.0,rsi=90.0))
  assertFalse(x.eligible);assertTrue(x.reasons.any{it.contains("above session VWAP")});assertTrue(x.reasons.any{it.contains("overbought")})
 }
 @Test fun rejectsConfirmedFailedBreakout(){
  val x=FixedEntrySafetyOverlay.assess(f(lastHigh=105.0,prevHigh=104.0,lastClose=103.5))
  assertFalse(x.eligible);assertTrue(x.reasons.any{it.contains("failed breakout")})
 }
 @Test fun missingSpreadOrSessionFailsClosed(){
  assertFalse(FixedEntrySafetyOverlay.assess(f(spread=null)).eligible)
  assertFalse(FixedEntrySafetyOverlay.assess(f(sessionCount=0)).eligible)
 }
}
