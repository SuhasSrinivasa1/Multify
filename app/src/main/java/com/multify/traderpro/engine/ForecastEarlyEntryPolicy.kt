package com.multify.traderpro.engine
data class EarlyEntryAssessment(val qualifies:Boolean,val watch:Boolean,val score:Double,val reason:String,val breakoutDistancePct:Double,val fiveMinuteChangePct:Double)
/** Forecast only. Uses completed 5m bars aggregated from timestamped 1m candles. */
object ForecastEarlyEntryPolicy {
    fun assess(evaluation:StrategyEvaluation,asOfMs:Long=System.currentTimeMillis()):EarlyEntryAssessment{
        val f=evaluation.features
        val bars=FiveMinuteCandleAggregator.aggregate(f.candles.map{MarketCandleBar(it.ts,it.open,it.high,it.low,it.close,it.volume)},asOfMs)
        if(bars.size<6||f.ltp<=0.0||!f.ltp.isFinite())
            return EarlyEntryAssessment(false,false,0.0,"Insufficient complete 5-minute bars for 30-minute context",0.0,0.0)
        val referenceHigh=bars.takeLast(6).maxOf{it.high}
        val lastThree=bars.takeLast(3)
        val previousFiveClose=bars.last().close
        if(referenceHigh<=0.0||previousFiveClose<=0.0)
            return EarlyEntryAssessment(false,false,0.0,"Invalid reference price",0.0,0.0)
        val breakoutDistancePct=(f.ltp/referenceHigh-1.0)*100.0
        val fiveChangePct=(f.ltp/previousFiveClose-1.0)*100.0
        val lastOneMinute=f.candles.asSequence().filter{it.ts>0L&&it.ts+60_000L<=asOfMs}.maxByOrNull{it.ts}
        val recentBarChangePct=if(lastOneMinute!=null&&lastOneMinute.close>0.0)(f.ltp/lastOneMinute.close-1.0)*100.0 else fiveChangePct
        val low=lastThree.minOf{it.low}
        val fiveLowRisePct=if(low>0.0)(f.ltp/low-1.0)*100.0 else 100.0
        val nearBreakout=breakoutDistancePct in -0.55..0.20
        val notChasing=fiveChangePct in -0.55..0.90&&recentBarChangePct<=0.65&&fiveLowRisePct<=1.25
        val spreadSafe=f.spreadBps?.let{it.isFinite()&&it in 0.0..40.0}?:false
        val participation=(f.rvol?:0.0)>=0.75&&(f.greenVolumeShare?:0.0)>=0.43
        val direction=((f.ema9Slope?:0.0)>-0.08||(f.rsiSlope?:0.0)>0.0)&&evaluation.directionalScore>=0.08&&evaluation.confidence>=0.53
        val structure=(f.structureScore?:0.0)>-0.33&&(f.vwap?.let{f.ltp>=it*0.996}?:false)
        val capacity=f.atr14?.let{it>0.0&&it/f.ltp*100.0>=0.12}?:false
        val votes=listOf(spreadSafe,participation,direction,structure,capacity).count{it}
        val qualifies=nearBreakout&&notChasing&&votes>=4&&spreadSafe&&direction&&participation
        val watch=nearBreakout&&notChasing&&votes>=3
        val score=(votes/5.0*0.75+(if(nearBreakout)0.13 else 0.0)+(if(notChasing)0.12 else 0.0)).coerceIn(0.0,1.0)
        val reasons=listOf(if(nearBreakout)"near prior 30m resistance" else "not near prior 30m resistance",
            if(notChasing)"pre-spike price extension controlled" else "recent spike overextended",
            if(spreadSafe)"spread acceptable" else "spread missing/too wide",
            if(participation)"early volume participation" else "participation weak",
            if(direction)"momentum starting" else "momentum not ready",
            if(structure)"structure building" else "structure weak")
        val reason=reasons.joinToString(" · ")+" · 5m "+String.format(java.util.Locale.US,"%.2f",fiveChangePct)+
            "% · 30m resistance distance "+String.format(java.util.Locale.US,"%.2f",breakoutDistancePct)+"%"
        return EarlyEntryAssessment(qualifies,watch,score,reason,breakoutDistancePct,fiveChangePct)
    }
    fun quality(evaluation:StrategyEvaluation,targetPct:Double):ForecastQualityDecision{
        val d=assess(evaluation);val f=evaluation.features
        val capacityPct=f.atr14?.takeIf{it>0.0&&f.ltp>0.0}?.let{it/f.ltp*100.0*4.0}?:0.0
        val viable=capacityPct+1e-9>=targetPct*0.55;val primary=d.qualifies&&viable
        return ForecastQualityDecision(tier=if(primary)"PRIMARY" else if(d.watch)"SECONDARY" else "RESEARCH",
            publishEligible=primary,executionEligible=primary,volatilityCapacityPct=capacityPct,
            reason=if(primary)"EARLY ENTRY · "+d.reason else if(!viable)"EARLY WATCH · insufficient volatility capacity · "+d.reason else "EARLY WATCH · "+d.reason)
    }
}
