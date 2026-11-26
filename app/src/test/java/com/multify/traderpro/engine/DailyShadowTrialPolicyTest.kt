package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class DailyShadowTrialPolicyTest {
    private fun rows()=listOf(
        DailyShadowTrialPolicy.Sample("a",true,true,1.0),
        DailyShadowTrialPolicy.Sample("b",true,true,1.0),
        DailyShadowTrialPolicy.Sample("c",true,true,1.0),
        DailyShadowTrialPolicy.Sample("d",false,false,-2.0),
        DailyShadowTrialPolicy.Sample("e",false,false,-1.0))
    @Test fun threeConsecutiveImprovedDaysRemainResearchOnly(){
        val days=listOf("2026-11-26","2026-11-27","2026-11-30").map{DailyShadowTrialPolicy.assess(it,rows(),true)}
        assertEquals(3,DailyShadowTrialPolicy.consecutiveImproved(days))
        assertEquals("PROVISIONAL_RESEARCH_REVIEW_ONLY",DailyShadowTrialPolicy.status(days))
    }
    @Test fun missingOutcomeCannotCountAsSuccess(){
        val data=rows().dropLast(1)+DailyShadowTrialPolicy.Sample("e",false,null,null)
        assertEquals("INSUFFICIENT_OR_UNRESOLVED",DailyShadowTrialPolicy.assess("2026-11-26",data,true).result)
    }
    @Test fun samePopulationNoDifferenceIsNotImprovement(){
        assertEquals("NO_PARAMETER_DISCRIMINATION",DailyShadowTrialPolicy.assess("2026-11-26",rows().map{it.copy(keep=true)},true).result)
    }
    @Test fun unknownWeekdayGapBreaksStreak(){
        val days=listOf("2026-11-26","2026-11-30","2026-12-01").map{DailyShadowTrialPolicy.assess(it,rows(),true)}
        assertEquals(2,DailyShadowTrialPolicy.consecutiveImproved(days))
    }
    @Test fun duplicateSymbolsCannotInflateSampleSize(){
        assertEquals(5,DailyShadowTrialPolicy.assess("2026-11-26",rows()+rows(),true).candidates)
    }
    @Test fun fiveSectionsOneParameterEach(){
        assertEquals(setOf("FIXED","FIXED_REPEAT","RANGE","LARGE","LOWS","FII_DII"),DailyShadowTrialPolicy.plans.map{it.section}.toSet())
        assertTrue(DailyShadowTrialPolicy.plans.all{it.baseline!=it.challenger})
    }
}
