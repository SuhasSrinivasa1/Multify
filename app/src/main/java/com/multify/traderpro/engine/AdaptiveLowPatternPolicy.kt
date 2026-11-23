package com.multify.traderpro.engine

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A completed historical reaction, NEVER a reconstructed broker fill. */
data class AdaptiveLowReaction(
    val lowDay: String,
    val supportLow: Double,
    val reboundPctFromLow: Double,
    val sessionsToRebound: Int,
    val volumeToPrior20: Double?,
    val rsi14AtLow: Double?,
    val ema20AtLow: Double?,
    val lowerWickReversal: Boolean?
)

/**
 * Prospective, read-only NSE EQ support-retest candidate.
 * The entry prices are historical support references, not guaranteed executable prices.
 */
data class AdaptiveLowCandidate(
    val symbol: String,
    val months: Int,
    val asOfDay: String,
    val latestClose: Double,
    val supportPrice: Double,
    val distanceAboveSupportPct: Double,
    val entryLow: Double,
    val entryHigh: Double,
    val preferredEntry: Double,
    val historicalBreakReference: Double,
    val medianHistoricalReboundPct: Double,
    val bestHistoricalReboundPct: Double,
    val medianSessionsToRebound: Double,
    val reactions: List<AdaptiveLowReaction>,
    val approachDrawdown10Pct: Double,
    val currentFiveDayPct: Double?,
    val volumeTo20Day: Double?,
    val average20DayTurnoverRupees: Double?,
    val rsi14: Double?,
    val ema9: Double?,
    val ema20: Double?,
    val ema50: Double?,
    val atr14: Double?,
    val lastCloseAboveEma20: Boolean?,
    val candleCoverage: Int,
    val phase: String,
    val patternScore: Double,
    val companyName: String = "",
    val marketCapCategory: String = "UNCLASSIFIED",
    val sector: String? = null,
    val stockPe: Double? = null,
    val stockPb: Double? = null,
    val revenueGrowthPct: Double? = null,
    val profitGrowthPct: Double? = null,
    val niftyEodPct: Double? = null,
    val niftyRegime: String = "UNKNOWN",
    val sectorIndexName: String? = null,
    val sectorEodPct: Double? = null,
    val sectorRegime: String = "UNKNOWN",
    val marketEvidenceDay: String = "",
    val valuationEvidenceDay: String = "",
    val coverageNote: String = "NSE EQ EOD; unadjusted corporate-action history"
)

/** Frozen scanner version v1. This object has no Groww access and no order authority. */
object AdaptiveLowPatternPolicy {
    const val SCHEMA = "ADAPTIVE_LOW_REBOUND_RETEST_V1"
    const val NEAR_SUPPORT_MAX_PCT = 5.0 // strictly LESS than +5%, never equal
    const val MIN_PRIOR_REACTIONS = 2
    const val HISTORICAL_REBOUND_PCT = 5.0
    const val RAPID_REBOUND_PCT = 2.0
    const val RAPID_HORIZON_SESSIONS = 4
    const val FULL_REBOUND_HORIZON_SESSIONS = 10
    const val MIN_REACTION_SPACING_SESSIONS = 8
    const val SUPPORT_CLUSTER_TOLERANCE_PCT = 2.5
    const val ENTRY_ZONE_MAX_PCT = 1.50
    const val MIN_TREND_APPROACH_DRAWDOWN_PCT = 1.50
    const val MIN_3M_SESSIONS = 55
    const val MIN_6M_SESSIONS = 105

    private fun pct(top: Double, bottom: Double): Double =
        if(bottom > 0.0) (top / bottom - 1.0) * 100.0 else Double.NaN

    private fun median(values: List<Double>): Double {
        val x=values.sorted()
        return if(x.size%2==1)x[x.size/2] else (x[x.size/2-1]+x[x.size/2])/2.0
    }

    private fun ema(data:List<Double>, periods:Int):Double? {
        if(data.size<periods)return null
        val alpha=2.0/(periods+1.0)
        return data.drop(1).fold(data.first()){prior,v->alpha*v+(1.0-alpha)*prior}
    }

    private fun rsi(data:List<Double>):Double? {
        if(data.size<15)return null
        val deltas=data.takeLast(15).zipWithNext{a,b->b-a}
        val gain=deltas.filter{it>0}.sum()
        val loss=-deltas.filter{it<0}.sum()
        return if(loss<=1e-9)100.0 else 100.0-100.0/(1+gain/loss)
    }

    private fun atr(bars:List<EquityEodBar>):Double? {
        if(bars.size<15)return null
        val tr=bars.takeLast(15).zipWithNext{old,b->
            val h=b.high?.takeIf{it.isFinite() && it>=b.low}?:return null
            max(h-b.low,max(abs(h-old.close),abs(b.low-old.close)))
        }
        return tr.takeIf{it.size==14}?.average()
    }

    private fun hammer(b:EquityEodBar):Boolean? {
        val o=b.open?.takeIf{it.isFinite()&&it>0.0}?:return null
        val h=b.high?.takeIf{it.isFinite()&&it>=b.low}?:return null
        if(h<b.close || h<o || b.close<b.low || o<b.low)return null
        val body=abs(b.close-o)
        val tail=min(b.close,o)-b.low
        val head=h-max(b.close,o)
        val span=h-b.low
        if(span<=0.0)return null
        return tail>=max(body*1.6,span*0.35) && head<=span*0.3 &&
            (h-b.close)/span<=0.50
    }

    fun evaluate(symbol:String, months:Int, asOf:LocalDate, rawBars:List<EquityEodBar>):AdaptiveLowCandidate? {
        if(months!=3 && months!=6)return null
        val start=asOf.minusMonths(months.toLong())
        val bars=rawBars.asSequence().filter{b->
            b.date>=start && b.date<=asOf &&
            b.low.isFinite()&&b.close.isFinite()&&b.low>0 &&
            b.close>=b.low && b.volume>=0
        }.sortedBy{it.date}.toList()
        val minimum=if(months==3)MIN_3M_SESSIONS else MIN_6M_SESSIONS
        if(bars.size<minimum || bars.lastOrNull()?.date!=asOf)return null
        // Unadjusted corporate actions may produce fake rebound shapes: fail closed
        // on extreme discontinuities until an adjusted-history feed is available.
        if(bars.zipWithNext().any{(a,b)->abs(pct(b.close,a.close))>=38.0})return null
        val close=bars.last().close
        val lastIdx=bars.lastIndex
        if(lastIdx<18)return null
        val closes=bars.map{it.close}
        val episodes=mutableListOf<Pair<Int,AdaptiveLowReaction>>()
        // At least ten COMPLETED sessions after a historical trough are needed.
        // Current retest is never used to validate itself (no look-ahead).
        for(i in 3..(lastIdx-FULL_REBOUND_HORIZON_SESSIONS-1)){
            val low=bars[i].low
            if((i-2..i+2).any{it!=i && bars[it].low<low-1e-7})continue
            if(bars[i].close>low*1.045)continue // huge wick alone is not a support touch
            val quick=(i+1..i+RAPID_HORIZON_SESSIONS).maxOf{bars[it].close}
            if(pct(quick,low)<RAPID_REBOUND_PCT)continue
            val reactions=(i+1..i+FULL_REBOUND_HORIZON_SESSIONS)
                .map{j->j to pct(bars[j].close,low)}
            val maxReaction=reactions.maxOf{it.second}
            if(maxReaction < HISTORICAL_REBOUND_PCT)continue
            val firstHit=reactions.firstOrNull{it.second>=HISTORICAL_REBOUND_PCT}?.first ?:continue
            val prior=bars.subList(max(0,i-20),i).map{it.volume.toDouble()}
                .filter{it>0}
            val avg=prior.takeIf{it.size>=10}?.average()
            val vol=avg?.let{if(it>0)bars[i].volume/it else null}
            episodes+=i to AdaptiveLowReaction(
                lowDay=bars[i].date.toString(),supportLow=low,
                reboundPctFromLow=maxReaction,
                sessionsToRebound=firstHit-i,
                volumeToPrior20=vol,
                rsi14AtLow=rsi(closes.take(i+1)),
                ema20AtLow=ema(closes.take(i+1),20),
                lowerWickReversal=hammer(bars[i])
            )
        }
        if(episodes.size<MIN_PRIOR_REACTIONS)return null
        val clusters=mutableListOf<List<Pair<Int,AdaptiveLowReaction>>>()
        for((_,anchor) in episodes){
            val raw=episodes.filter { (_,r)->
                abs(pct(r.supportLow,anchor.supportLow))<=SUPPORT_CLUSTER_TOLERANCE_PCT
            }.sortedBy{it.first}
            val selected=mutableListOf<Pair<Int,AdaptiveLowReaction>>()
            for(item in raw){
                if(selected.isEmpty() ||
                    item.first-selected.last().first>=MIN_REACTION_SPACING_SESSIONS)
                    selected+=item
            }
            if(selected.size>=MIN_PRIOR_REACTIONS)clusters+=selected
        }
        if(clusters.isEmpty())return null
        val latest10=closes.takeLast(10).maxOrNull()?:return null
        val approachDrawdown=pct(latest10,close)
        val threeBack=closes.getOrNull(lastIdx-3)?:return null
        val falling=pct(close,threeBack)<=-0.35
        if(approachDrawdown<MIN_TREND_APPROACH_DRAWDOWN_PCT || !falling)return null
        val eligible=clusters.mapNotNull { group ->
            val support=median(group.map{it.second.supportLow})
            val distance=pct(close,support)
            if(distance < 0.0 || distance >= NEAR_SUPPORT_MAX_PCT)return@mapNotNull null
            val disp=group.map{abs(pct(it.second.supportLow,support))}.maxOrNull()?:0.0
            if(disp>SUPPORT_CLUSTER_TOLERANCE_PCT)return@mapNotNull null
            val strength=median(group.map{it.second.reboundPctFromLow})
            val recentSession=group.last().first
            // Number of independently rebound lows is evidence, not hit-rate.
            val quality=group.size*15.0 + strength*2.0 - disp*5.0 -
                distance*2.0 + (recentSession.toDouble()/lastIdx)*5.0
            Triple(group,support,quality)
        }
        if(eligible.isEmpty())return null
        val best=eligible.maxByOrNull{it.third}?:return null
        val cluster=best.first
        val support=best.second
        val dist=pct(close,support)
        val vol20=bars.takeLast(20).map{it.volume.toDouble()}.filter{it>0}
            .takeIf{it.size>=15}?.average()
        val currentVol=vol20?.takeIf{it>0}?.let{bars.last().volume/it}
        val atr14=atr(bars)
        val halfWidth=atr14?.let{(it/support*100.0*0.55).coerceIn(0.70,ENTRY_ZONE_MAX_PCT)}
            ?:1.20
        val high=support*(1.0+halfWidth/100.0)
        val entry=support*(1.0+halfWidth*0.45/100.0)
        val invalidation=support*(1.0-(atr14?.let{it/support*100.0*0.7}
            ?:1.6).coerceIn(1.0,3.0)/100.0)
        val e9=ema(closes,9)
        val e20=ema(closes,20)
        val e50=ema(closes,50)
        return AdaptiveLowCandidate(
            symbol=symbol,months=months,asOfDay=asOf.toString(),
            latestClose=close,supportPrice=support,distanceAboveSupportPct=dist,
            entryLow=support,entryHigh=high,preferredEntry=entry,
            historicalBreakReference=invalidation,
            medianHistoricalReboundPct=median(cluster.map{it.second.reboundPctFromLow}),
            bestHistoricalReboundPct=cluster.maxOf{it.second.reboundPctFromLow},
            medianSessionsToRebound=median(cluster.map{it.second.sessionsToRebound.toDouble()}),
            reactions=cluster.map{it.second},
            approachDrawdown10Pct=approachDrawdown,
            currentFiveDayPct=closes.getOrNull(lastIdx-5)?.let{pct(close,it)},
            volumeTo20Day=currentVol,
            average20DayTurnoverRupees=vol20?.times(close),
            rsi14=rsi(closes),ema9=e9,ema20=e20,ema50=e50,atr14=atr14,
            lastCloseAboveEma20=e20?.let{close>it},
            candleCoverage=cluster.count{it.second.lowerWickReversal!=null},
            phase=if(close<=high)"IN_ENTRY_ZONE" else "APPROACHING_FROM_ABOVE",
            patternScore=best.third
        )
    }
}
