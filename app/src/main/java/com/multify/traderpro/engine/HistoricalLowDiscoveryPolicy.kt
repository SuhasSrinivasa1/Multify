package com.multify.traderpro.engine

import java.time.LocalDate

/** Pure local discovery. The reference excludes the latest candle. No order authority. */
data class HistoricalLowDiscovery(
    val symbol:String,val months:Int,val day:String,val close:Double,
    val priorLow:Double,val priorLowDay:String,val distancePct:Double,
    val marketCapCategory:String="UNCLASSIFIED",val companyName:String="",val sector:String=""
)
object HistoricalLowDiscoveryPolicy {
    fun evaluate(symbol:String,months:Int,day:LocalDate,rawBars:List<EquityEodBar>,minimumPrior:Int):HistoricalLowDiscovery? {
        require(months in listOf(3,6) && minimumPrior>0)
        val bars=rawBars.filter{it.date>=day.minusMonths(months.toLong()) && it.date<=day &&
            it.low.isFinite() && it.low>0 && it.close.isFinite() && it.close>=it.low}
            .distinctBy{it.date}.sortedBy{it.date}
        if(bars.lastOrNull()?.date!=day || bars.size-1<minimumPrior)return null
        if(bars.zipWithNext().any{(a,b)->kotlin.math.abs(b.close/a.close-1.0)>=0.38})return null
        val low=bars.dropLast(1).minByOrNull{it.low}?:return null
        val close=bars.last().close
        val distance=(close/low.low-1.0)*100.0
        if(!distance.isFinite() || distance>5.0+1e-8)return null
        return HistoricalLowDiscovery(symbol,months,day.toString(),close,low.low,low.date.toString(),distance)
    }
}
