package com.multify.traderpro.engine

import java.time.LocalDate

/** Research review only. No references from broker orders, Fixed ranking or execution. */
object DailyShadowTrialPolicy {
    data class Plan(val section:String,val parameter:String,val baseline:String,val challenger:String)
    val plans=listOf(
        Plan("FIXED","latest publication time IST","15:15","14:45"),
        Plan("FIXED_REPEAT","minimum time from preceding publication minutes","30","45"),
        Plan("RANGE","maximum first-qualification upward entry drift %","1.60","1.50"),
        Plan("LARGE","minimum swing five-day relative strength %","0.25","0.30"),
        Plan("LOWS","maximum distance above support %","5.00","4.90"),
        Plan("FII_DII","verified net-buy rank cutoff","20","19")
    )
    data class Sample(val id:String,val keep:Boolean?,val hit:Boolean?,val outcomePct:Double?)
    data class Day(val day:String,val candidates:Int,val matured:Int,val retained:Int,
        val baselineHits:Int,val challengerHits:Int,val baselineRate:Double?,
        val challengerRate:Double?,val baselineMeanProxy:Double?,val challengerMeanProxy:Double?,
        val result:String)
    const val MIN_BASELINE=5
    const val MIN_RETAINED=3
    const val MIN_STREAK=3

    fun assess(day:String, samples:List<Sample>, returnComparable:Boolean):Day {
        val rows=samples.distinctBy{it.id}
        val valid=rows.filter{it.keep!=null && it.hit!=null &&
            (!returnComparable || it.outcomePct?.isFinite()==true)}
        val chosen=valid.filter{it.keep==true}
        val baseHits=valid.count{it.hit==true}
        val newHits=chosen.count{it.hit==true}
        val br=valid.takeIf{it.isNotEmpty()}?.let{100.0*baseHits/it.size}
        val cr=chosen.takeIf{it.isNotEmpty()}?.let{100.0*newHits/it.size}
        val baseReturn=if(returnComparable && valid.isNotEmpty())valid.sumOf{it.outcomePct!!}/valid.size else null
        // Same opportunity denominator; rejected observations contribute cash=0, not fabricated trades.
        val newReturn=if(returnComparable && valid.isNotEmpty())chosen.sumOf{it.outcomePct!!}/valid.size else null
        val result=when {
            valid.size!=rows.size || valid.size<MIN_BASELINE || chosen.size<MIN_RETAINED -> "INSUFFICIENT_OR_UNRESOLVED"
            chosen.size==valid.size -> "NO_PARAMETER_DISCRIMINATION"
            cr!=null && br!=null && cr>br && newHits>=0.8*baseHits &&
                (!returnComparable || (newReturn!=null && baseReturn!=null && newReturn>=baseReturn)) -> "SHADOW_DAY_IMPROVED"
            else -> "NOT_IMPROVED"
        }
        return Day(day,rows.size,valid.size,chosen.size,baseHits,newHits,br,cr,baseReturn,newReturn,result)
    }
    /** Conservative continuity: unknown weekday gaps break the streak; weekends may be skipped. */
    fun consecutiveImproved(days:List<Day>):Int {
        var n=0
        var last:LocalDate?=null
        for(d in days.distinctBy{it.day}.sortedBy{it.day}){
            val date=LocalDate.parse(d.day)
            var expected=last?.plusDays(1)
            while(expected!=null && expected.dayOfWeek.value>=6)expected=expected.plusDays(1)
            if(d.result!="SHADOW_DAY_IMPROVED") n=0
            else n=if(expected==date) n+1 else 1
            last=date
        }
        return n
    }
    fun status(days:List<Day>):String = if(consecutiveImproved(days)>=MIN_STREAK)
        "PROVISIONAL_RESEARCH_REVIEW_ONLY" else "SHADOW_COLLECTING"
}
