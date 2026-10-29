package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEntryTimingPolicyTest {
    private val minute=60_000L
    private fun bars(start: Long, count: Int, changes: (Int)->Double):List<ForecastEntryTimingPolicy.Bar> =
        (0 until count).map { i ->
            val close=changes(i)
            ForecastEntryTimingPolicy.Bar(start+i*minute,close,close+.08,close-.08,close,120.0)
        }

    @Test fun neverBuysBarPartiallyKnownAtSignalTime() {
        val candles=bars(1_000_000L, 40){100.0+it*.02}
        val signal=1_000_000L+30_000L
        val study=ForecastEntryTimingPolicy.reconstruct(signal,100.0,.5,candles)
        assertTrue(study.nextBarObserved)
        assertEquals(1_000_000L+minute, study.outcomes.first().entryAtMs)
        assertEquals(ForecastEntryTimingPolicy.Method.NOW, study.outcomes.first().method)
    }

    @Test fun delayedConfirmationRequiresFutureCompletedBarAndNextOpen() {
        val candles=bars(1_000_000L,40){100.0 + .03*it}
        val study=ForecastEntryTimingPolicy.reconstruct(1_000_000L+1L,100.0,.5,candles)
        val now=study.outcomes.first{it.method==ForecastEntryTimingPolicy.Method.NOW}
        val one=study.outcomes.first{it.method==ForecastEntryTimingPolicy.Method.CONFIRM_1M}
        val three=study.outcomes.first{it.method==ForecastEntryTimingPolicy.Method.CONFIRM_3M}
        assertTrue(one.filled)
        assertTrue(three.filled)
        assertEquals(now.entryAtMs!!+minute,one.entryAtMs)
        assertEquals(now.entryAtMs!!+3*minute,three.entryAtMs)
    }

    @Test fun noPullbackMeansNoImaginaryLowFill() {
        val candles=bars(1_000_000L,40){100.0+it*.02}
        val study=ForecastEntryTimingPolicy.reconstruct(1_000_000L+1L,100.0,.55,candles)
        val pull=study.outcomes.first{it.method==ForecastEntryTimingPolicy.Method.PULLBACK_RETEST}
        assertFalse(pull.filled)
        assertEquals(null,pull.entryPrice)
    }

    @Test fun noLookaheadOnSameBarTargetAndStop() {
        val t=1_000_000L
        val rows=(0 until 40).map { i ->
            val lo=if(i==1)99.0 else 99.94
            val hi=if(i==1)102.0 else 100.08
            ForecastEntryTimingPolicy.Bar(t+i*minute,100.0,hi,lo,100.0,100.0)
        }
        val study=ForecastEntryTimingPolicy.reconstruct(t+1L,100.0,.55,rows)
        val immediate=study.outcomes.first()
        assertEquals("RISK_PROXY",immediate.exitReason)
        assertFalse(immediate.targetHit)
        assertTrue(immediate.netPct!!<0.0)
    }

    @Test fun fewCorrelatedObservationsCannotPromoteTimingModel() {
        val study=ForecastEntryTimingPolicy.reconstruct(1_000_000L+1L,100.0,.55,
            bars(1_000_000L,40){100.0+.03*it})
        val history=(1..20).map { i ->
            ForecastEntryTimingPolicy.Example("2026-10-20","SYMBOL$i",1_000_000L,
                "BREADTH_BULL","OPEN","HIGH","COIL",study.outcomes)
        }
        val decision=ForecastEntryTimingPolicy.advise("BREADTH_BULL","OPEN","HIGH","COIL",history,"2026-10-27")
        assertEquals(ForecastEntryTimingPolicy.Method.NOW,decision.method)
        assertFalse(decision.validated)
    }

    @Test fun tomorrowCannotTrainOnTodaysResults() {
        val study=ForecastEntryTimingPolicy.reconstruct(1_000_000L+1L,100.0,.55,
            bars(1_000_000L,40){100.0+.03*it})
        val data=(1..35).map { i ->
            ForecastEntryTimingPolicy.Example("2026-10-27","SYMBOL$i",1_000_000L,
                "BREADTH_BULL","OPEN","HIGH","COIL",study.outcomes)
        }
        val advice=ForecastEntryTimingPolicy.advise("BREADTH_BULL","OPEN","HIGH","COIL",data,"2026-10-27")
        assertEquals(0,advice.sampleSize)
        assertFalse(advice.validated)
    }
}
