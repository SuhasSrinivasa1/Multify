package com.multify.traderpro.engine

import com.multify.traderpro.data.network.FixedEarlyWatchDto
import com.multify.traderpro.data.network.FixedPulseResearchDto
import org.junit.Assert.*
import org.junit.Test

class FixedFastCandidateSelectionPolicyTest {
    @Test fun prioritizesRecentPrimaryExcludesPublishedAndStalePulse() {
        val now=3_000_000L
        val early=listOf(
            FixedEarlyWatchDto(symbol="FIRST", firstPrimaryPrice=500.0, firstEvaluatedAtMs=now-60_000L),
            FixedEarlyWatchDto(symbol="EXCLUDED", firstPrimaryPrice=510.0, firstEvaluatedAtMs=now-10_000L),
            FixedEarlyWatchDto(symbol="OLD", firstPrimaryPrice=500.0, firstEvaluatedAtMs=now-25L*60_000L)
        )
        val pulses=listOf(
            FixedPulseResearchDto(symbol="FIRST", firstPulsePrice=490.0, firstPulseObservedAtMs=now-15_000L),
            FixedPulseResearchDto(symbol="SECOND", firstPulsePrice=650.0, firstPulseObservedAtMs=now-15_000L),
            FixedPulseResearchDto(symbol="STALE", firstPulsePrice=650.0, firstPulseObservedAtMs=now-13L*60_000L)
        )
        val selected=FixedFastCandidateSelectionPolicy.select(early,pulses,listOf("EXCLUDED"),now)
        assertEquals(listOf("FIRST","SECOND"),selected.map { it.symbol })
        assertEquals("FIRST_PRIMARY_RESEARCH",selected[0].origin)
    }

    @Test fun neverExceedsOneBrokerBatchOrAcceptsInvalidPrice() {
        val now=3_000_000L
        val early=(1..75).map {
            FixedEarlyWatchDto(symbol="S$it",firstPrimaryPrice=500.0,firstEvaluatedAtMs=now-1000L)
        } + FixedEarlyWatchDto(symbol="BAD",firstPrimaryPrice=Double.NaN,firstEvaluatedAtMs=now-1000L)
        val selected=FixedFastCandidateSelectionPolicy.select(early,emptyList(),emptyList(),now)
        assertEquals(48,selected.size)
        assertFalse(selected.any { it.symbol=="BAD" })
    }

    @Test fun researchYieldsToFixedAndTrading() {
        val p=FixedFastCandidateSamplingPolicy
        fun permit(fixedBusy:Boolean=false,holding:Boolean=false,headroom:Long=60_000L,
            lastAt:Long=0L,tradingCritical:Boolean=false)=p.candidateOnlyAllowed(
            2_000_000L,lastAt,headroom,fixedBusy,tradingCritical,holding,20,true)
        assertTrue(permit())
        assertFalse(permit(fixedBusy=true))
        assertFalse(permit(holding=true))
        assertFalse(permit(headroom=10_000L))
        assertFalse(permit(lastAt=1_990_000L))
        assertFalse(permit(tradingCritical=true))
    }
}
