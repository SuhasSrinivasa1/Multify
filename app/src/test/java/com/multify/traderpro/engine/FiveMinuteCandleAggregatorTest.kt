package com.multify.traderpro.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class FiveMinuteCandleAggregatorTest {
 @Test fun aggregatesOnlyFiveDistinctCompletedMinutes(){
  val start=1_800_000_000_000L/300_000L*300_000L
  val mins=(0 until 5).map{i->MarketCandleBar(start+i*60_000L,100.0+i,102.0+i,99.0+i,101.0+i,100.0)}
  val bar=FiveMinuteCandleAggregator.aggregate(mins,start+5*60_000L).single()
  assertEquals(100.0,bar.open,.0001);assertEquals(106.0,bar.high,.0001);assertEquals(99.0,bar.low,.0001)
  assertEquals(105.0,bar.close,.0001);assertEquals(500.0,bar.volume,.0001)
 }
 @Test fun rejectsGapsAndUnfinishedLastMinute(){
  val start=1_800_000_000_000L/300_000L*300_000L
  val gap=(0 until 5).filter{it!=2}.map{i->MarketCandleBar(start+i*60_000L,100.0,101.0,99.0,100.0,1.0)}
  assertTrue(FiveMinuteCandleAggregator.aggregate(gap,start+5*60_000L).isEmpty())
  val full=(0 until 5).map{i->MarketCandleBar(start+i*60_000L,100.0,101.0,99.0,100.0,1.0)}
  assertTrue(FiveMinuteCandleAggregator.aggregate(full,start+4*60_000L+30_000L).isEmpty())
 }
}
