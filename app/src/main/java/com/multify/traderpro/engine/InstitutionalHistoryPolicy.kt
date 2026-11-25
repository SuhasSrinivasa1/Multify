package com.multify.traderpro.engine

import java.time.ZonedDateTime
import java.time.ZoneId

data class InstitutionalHourly(val day:String,val hour:Int,val receivedAtMs:Long,val prices:Map<String,Double>,val opens:Map<String,Double>)
data class InstitutionalOutcome(val source:String,val day:String,val symbol:String,val baselineDay:String,val outcomeDay:String,val returnPct:Double)
data class InstitutionalAccumulation(
    val symbol:String,val company:String,val grossBuy:Double,val grossSell:Double,
    val sideShares:Long,val fiiSide:Double,val diiSide:Double,val weightedBuy:Double?,
    val weightedSell:Double?,val repeatedBuyers:Int,val buyDays:Int,val investors:List<String>
) { val net:Double get()=grossBuy-grossSell }
data class InstitutionalSectorFlow(val sector:String,val buys:Double,val sells:Double) { val net:Double get()=buys-sells }
data class InstitutionalWindow(val rows:List<InstitutionalAccumulation>,val requested:Int,val knownSessions:Int,val reports:Int,val through:String)

/** Price observations are never classified as institutional trades. */
object InstitutionalHistoryPolicy {
    fun slot(at:ZonedDateTime):String? {
        val t=at.withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
        // Use the first eligible completed sweep in each hour, even if the
        // initial twenty minutes were lost to a broker cooldown. No backfill.
        if(t.hour !in 10..15 || !NseEquitySessionCalendar.isFixedScanWindow(t))return null
        return t.toLocalDate().toString()+"-"+t.hour
    }
    fun window(snapshot:InstitutionalSnapshot,source:String,side:String,category:String,days:Int,sectorOnly:String?=null):InstitutionalWindow {
        require(days in listOf(1,5,10,30) && source in setOf("BULK","BLOCK") && side in setOf("BUY","SELL") && category in setOf("ALL","FII","DII","DISCLOSED","UNCLASSIFIED"))
        val reports=(snapshot.history+listOfNotNull(snapshot.bulk,snapshot.block))
            .filter{it.source==source}.associateBy{it.day}.values
        val latest=reports.maxOfOrNull{it.day}.orEmpty()
        val expected=snapshot.sessions.distinct().sorted().takeLast(days)
        // Never silently slide a stale report into today's window. If the exchange
        // calendar is not yet locally available, show only the last dated file.
        val dates=if(expected.isEmpty())listOfNotNull(latest.takeIf{it.isNotEmpty()}) else expected
        val selected=reports.filter{it.day in dates}
        val deals=selected.flatMap{it.deals}.filter{InstitutionalIntelligencePolicy.includes(category,it.category) &&
            (sectorOnly==null||snapshot.sectors[it.symbol]==sectorOnly)}
        val rows=deals.groupBy{it.symbol}.mapNotNull{(sym,all)->
            val buys=all.filter{it.side=="BUY"}; val sells=all.filter{it.side=="SELL"}
            val chosen=if(side=="BUY")buys else sells
            if(chosen.isEmpty())return@mapNotNull null
            // Use decimal rupee values to avoid floating-point noise assigning a balanced stock a side.
            fun value(rows:List<InstitutionalDeal>)=rows.fold(java.math.BigDecimal.ZERO){total,d->
                total+java.math.BigDecimal.valueOf(d.price)*java.math.BigDecimal.valueOf(d.shares)}
            val buyValue=value(buys);val sellValue=value(sells)
            val direction=buyValue.compareTo(sellValue)
            if((side=="BUY" && direction<=0)||(side=="SELL" && direction>=0))return@mapNotNull null
            val b=buyValue.toDouble();val s=sellValue.toDouble()
            val bq=buys.sumOf{it.shares};val sq=sells.sumOf{it.shares}
            InstitutionalAccumulation(sym,chosen.first().company,b,s,chosen.sumOf{it.shares},
                chosen.filter{it.category=="FII"}.sumOf{it.shares*it.price},
                chosen.filter{it.category=="DII"}.sumOf{it.shares*it.price},
                if(bq>0)b/bq else null,if(sq>0)s/sq else null,
                buys.groupBy{it.investor}.count{(_,v)->v.map{it.day}.distinct().size>=2},
                buys.map{it.day}.distinct().size,chosen.map{it.investor}.distinct().sorted())
        }.sortedWith(compareByDescending<InstitutionalAccumulation>{if(side=="BUY")it.net else -it.net}.thenBy{it.symbol}).take(20)
        return InstitutionalWindow(rows,days,expected.size,selected.size,dates.lastOrNull().orEmpty())
    }
    /** Aggregate all matched disclosures, not merely the 20 displayed stocks. */
    fun sectorTotals(snapshot:InstitutionalSnapshot,source:String,category:String,days:Int):List<InstitutionalSectorFlow> {
        val reports=(snapshot.history+listOfNotNull(snapshot.bulk,snapshot.block))
            .filter{it.source==source}.associateBy{it.day}.values
        val expected=snapshot.sessions.distinct().sorted().takeLast(days)
        val dates=if(expected.isEmpty())listOfNotNull(reports.maxOfOrNull{it.day}) else expected
        return reports.filter{it.day in dates}.flatMap{it.deals}
            .filter{InstitutionalIntelligencePolicy.includes(category,it.category)}
            .groupBy{snapshot.sectors[it.symbol]?.takeIf{v->v.isNotBlank()} ?: "UNMAPPED"}
            .map{(name,deals)->InstitutionalSectorFlow(name,
                deals.filter{it.side=="BUY"}.sumOf{it.shares*it.price},
                deals.filter{it.side=="SELL"}.sumOf{it.shares*it.price})}
            .sortedWith(compareByDescending<InstitutionalSectorFlow>{kotlin.math.abs(it.net)}.thenBy{it.sector}).take(20)
    }
    fun movement(price:Double?,reference:Double?):Double? =
        if(price!=null && reference!=null && price.isFinite() && reference.isFinite() && price>0 && reference>0)
            (price/reference-1)*100 else null
}
