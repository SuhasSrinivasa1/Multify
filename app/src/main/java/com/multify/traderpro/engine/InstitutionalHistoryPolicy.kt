package com.multify.traderpro.engine

import java.time.ZonedDateTime
import java.time.ZoneId

data class InstitutionalHourly(val day:String,val hour:Int,val receivedAtMs:Long,val prices:Map<String,Double>,val opens:Map<String,Double>)
data class InstitutionalOutcome(val source:String,val day:String,val symbol:String,val baselineDay:String,val outcomeDay:String,val returnPct:Double)
data class InstitutionalAccumulation(
    val symbol:String,val company:String,val grossBuy:Double,val grossSell:Double,
    val sideShares:Long,val fiiSide:Double,val diiSide:Double,val weightedBuy:Double?,
    val weightedSell:Double?,val repeatedBuyers:Int,val buyDays:Int,val investors:List<String>,
    val mfSide:Double=0.0,val promoterSide:Double=0.0,val preciseNet:Double?=null
) { val net:Double get()=preciseNet?:grossBuy-grossSell }
data class InstitutionalSectorFlow(val sector:String,val buys:Double,val sells:Double) { val net:Double get()=buys-sells }
data class InstitutionalWindow(val rows:List<InstitutionalAccumulation>,val requested:Int,val knownSessions:Int,val reports:Int,val through:String,val excludedOverlaps:Int=0,val sourceCoverage:Map<String,Int> = emptyMap())

/** Price observations are never classified as institutional trades. */
object InstitutionalHistoryPolicy {
    fun slot(at:ZonedDateTime):String? {
        val t=at.withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
        // Use the first eligible completed sweep in each hour, even if the
        // initial twenty minutes were lost to a broker cooldown. No backfill.
        if(t.hour !in 10..15 || !NseEquitySessionCalendar.isFixedScanWindow(t))return null
        return t.toLocalDate().toString()+"-"+t.hour
    }
    private fun amount(d:InstitutionalDeal):java.math.BigDecimal =
        d.reportedValue?.let{java.math.BigDecimal.valueOf(it)}
            ?:java.math.BigDecimal.valueOf(d.price)*java.math.BigDecimal.valueOf(d.shares)
    private fun total(rows:List<InstitutionalDeal>)=rows.fold(java.math.BigDecimal.ZERO){sum,d->sum+amount(d)}
    private data class Selection(val deals:List<InstitutionalDeal>,val dates:List<String>,val known:Int,
        val reports:Int,val overlaps:Int,val coverage:Map<String,Int>)
    private fun select(snapshot:InstitutionalSnapshot,source:String,category:String,days:Int,sectorOnly:String?):Selection {
        val sources=if(source=="COMBINED")setOf("BULK","BLOCK","PROMOTER") else setOf(source)
        val reports=(snapshot.history+listOfNotNull(snapshot.bulk,snapshot.block,snapshot.promoter))
            .filter{it.source in sources}.associateBy{it.source+"|"+it.day}.values
        val expected=snapshot.sessions.distinct().sorted().takeLast(days)
        val latest=reports.maxOfOrNull{it.day}.orEmpty()
        val dates=if(expected.isEmpty())listOfNotNull(latest.takeIf{it.isNotEmpty()})else expected
        val selected=reports.filter{it.day in dates}
        val all=selected.flatMap{r->r.deals.map{r.source to it}}
        fun identity(d:InstitutionalDeal)=d.investor.uppercase(java.util.Locale.ROOT).replace(Regex("[^A-Z0-9]"),"")
        fun sameIdentity(a:InstitutionalDeal,b:InstitutionalDeal)=a.symbol==b.symbol && a.side==b.side &&
            identity(a)==identity(b)
        // For overlapping sources with uncertain transaction identity, retain promoter filings
        // before exchange deal summaries; prefer block over bulk. Do not sum possible duplicates.
        // A multi-day promoter filing can cover a bulk/block trade on any day of its trade interval.
        val sorted=all.sortedBy{when(it.first){"PROMOTER"->0;"BLOCK"->1;else->2}}
        val kept=mutableListOf<Pair<String,InstitutionalDeal>>()
        var excluded=0
        for((src,d) in sorted){
            if(source=="COMBINED" && kept.any{(prior,p)->prior!=src && sameIdentity(p,d) &&
                (p.tradeFrom?:p.day)<=d.day && (d.tradeFrom?:d.day)<=p.day}) {
                excluded++;continue
            }
            kept+=src to d
        }
        val deals=kept.map{it.second}.filter{InstitutionalIntelligencePolicy.matches(category,it) &&
            (sectorOnly==null||snapshot.sectors[it.symbol]==sectorOnly)}
        return Selection(deals,dates,expected.size,selected.map{it.day}.distinct().size,excluded,
            sources.associateWith{src->selected.count{it.source==src}})
    }
    fun window(snapshot:InstitutionalSnapshot,source:String,side:String,category:String,days:Int,
        sectorOnly:String?=null,limit:Int=20):InstitutionalWindow {
        require(days in listOf(1,5,10,30) && source in setOf("BULK","BLOCK","PROMOTER","COMBINED") &&
            side in setOf("BUY","SELL") && category in setOf("ALL","FII","DII","MF","PROMOTER","OWNERSHIP","DISCLOSED","UNCLASSIFIED")
            && limit in 1..20)
        val selection=select(snapshot,source,category,days,sectorOnly)
        val rows=selection.deals.groupBy{it.symbol}.mapNotNull{(sym,all)->
            val buys=all.filter{it.side=="BUY"};val sells=all.filter{it.side=="SELL"}
            val chosen=if(side=="BUY")buys else sells
            val buyValue=total(buys);val sellValue=total(sells)
            val direction=buyValue.compareTo(sellValue)
            if((side=="BUY" && direction<=0)||(side=="SELL" && direction>=0))return@mapNotNull null
            val b=buyValue.toDouble();val s=sellValue.toDouble()
            val bq=buys.sumOf{it.shares};val sq=sells.sumOf{it.shares}
            fun group(name:String)=total(chosen.filter{InstitutionalIntelligencePolicy.effectiveCategory(it)==name}).toDouble()
            InstitutionalAccumulation(sym,chosen.first().company,b,s,chosen.sumOf{it.shares},group("FII"),group("DII"),
                if(bq>0)b/bq else null,if(sq>0)s/sq else null,
                buys.groupBy{it.investor}.count{(_,v)->v.map{it.day}.distinct().size>=2},
                buys.map{it.day}.distinct().size,chosen.map{it.investor}.distinct().sorted(),
                group("MF"),group("PROMOTER"),(buyValue-sellValue).toDouble())
        }.sortedWith(compareByDescending<InstitutionalAccumulation>{if(side=="BUY")it.net else -it.net}.thenBy{it.symbol}).take(limit)
        return InstitutionalWindow(rows,days,selection.known,selection.reports,selection.dates.lastOrNull().orEmpty(),
            selection.overlaps,selection.coverage)
    }
    fun sectorTotals(snapshot:InstitutionalSnapshot,source:String,category:String,days:Int):List<InstitutionalSectorFlow> =
        select(snapshot,source,category,days,null).deals
            .groupBy{snapshot.sectors[it.symbol]?.takeIf{v->v.isNotBlank()}?:"UNMAPPED"}
            .map{(name,deals)->InstitutionalSectorFlow(name,total(deals.filter{it.side=="BUY"}).toDouble(),
                total(deals.filter{it.side=="SELL"}).toDouble())}
            .sortedWith(compareByDescending<InstitutionalSectorFlow>{kotlin.math.abs(it.net)}.thenBy{it.sector}).take(20)
    fun movement(price:Double?,reference:Double?):Double? =
        if(price!=null && reference!=null && price.isFinite() && reference.isFinite() && price>0 && reference>0)
            (price/reference-1)*100 else null
}
