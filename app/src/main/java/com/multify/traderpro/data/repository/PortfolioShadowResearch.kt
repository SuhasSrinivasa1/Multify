package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.multify.traderpro.engine.PortfolioShadowPolicy as P
import com.multify.traderpro.engine.NseEquitySessionCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent sampled paper journal, never invokes any order or GTT endpoint. */
@Singleton
class PortfolioShadowResearch @Inject constructor(@ApplicationContext context:Context,
    private val repository:TradingRepository,private val eod:EquityEodResearchRepository,
    private val news:LiveNewsIntelligence) {
    private val prefs=context.getSharedPreferences("portfolio_shadow_v1",Context.MODE_PRIVATE)
    private val gson=Gson()
    private val india=ZoneId.of("Asia/Kolkata")
    data class Row(val holding:PortfolioHoldingPreview,val state:P.State?,val trend:P.Trend?,val candidate:P.Candidate)
    data class Snapshot(val inventoryAt:Long?,val rows:List<Row>,val cnc:List<String>,val budget:Double)
    private val trends=mutableMapOf<String,Pair<Long,P.Trend?>>()
    @Synchronized fun budget():Double=prefs.getString("step","0")?.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0}?:0.0
    @Synchronized fun setBudget(value:Double):Boolean =
        value.isFinite()&&value>=0&&prefs.edit().putString("step",value.toString()).commit()
    private fun key(h:PortfolioHoldingPreview)="state_"+h.isin.ifBlank{h.symbol}
    private fun state(h:PortfolioHoldingPreview):P.State?=prefs.getString(key(h),null)?.let{
        runCatching{gson.fromJson(it,P.State::class.java)}.getOrNull()
    }
    @Synchronized fun cycle():Snapshot {
        val now=System.currentTimeMillis()
        val z=ZonedDateTime.now(india)
        val inv=repository.portfolioPreview()
        val inventoryFresh=inv.inventoryAtMs?.let{now-it in 0L..180_000L}==true
        val market=NseEquitySessionCalendar.isOpen(z)
        val neg=news.snapshot().events.filter{it.liveNews?.sentiment=="NEGATIVE"&&now-it.publishedAtMs in 0L..24*60*60_000L}.map{it.symbol}.toSet()
        val amount=budget()
        val rows=inv.holdings.map{h->
            var s=state(h)
            val p=h.price
            val at=h.priceAtMs
            val valid=h.average.isFinite()&&h.average>0&&h.quantity>0
            val cached=trends[h.symbol]
            val t=if(cached!=null&&now-cached.first<60*60_000L)cached.second else {
                val v=P.trend(eod.portfolioDailyBars(h.symbol))
                trends[h.symbol]=now to v;v
            }
            // Existing trading positions are not enrolled. Holdings with unknown free quantity are observed only.
            val eligible=market&&inventoryFresh&&valid&&p!=null&&at!=null&&now-at in 0L..180_000L&&h.symbol !in inv.cncPositionSymbols
            var saved=true
            if(eligible&&p!=null&&at!=null) {
                val prior=s?:P.initial(h.average,h.quantity).let{base->
                    // Adoption cannot replay previously earned milestones as simulated sales.
                    val next=((kotlin.math.floor(((p/h.average-1)*100)/10).toInt()+1)*10).coerceAtLeast(10)
                    base.copy(nextProfit=next)
                }
                val updated=P.observe(prior,h.quantity,h.average,p,at)
                if(updated!=s) {
                    saved=prefs.edit().putString(key(h),gson.toJson(updated)).commit()
                    if(saved)s=updated
                }
            }
            val c=when {
                !saved -> P.Candidate("BLOCKED","Research state write failed")
                !eligible -> P.Candidate("WAIT","No fresh market/inventory evidence or overlapping CNC position")
                t==null||t.latest!=EquityEodResearchRepository.latestEligibleDate(z).toString() -> P.Candidate("WAIT","Missing current daily history")
                s==null||p==null -> P.Candidate("WAIT","No valid observation")
                else -> P.candidate(s,t,p,amount,h.symbol in neg)
            }
            Row(h,s,t,c)
        }
        val result=Snapshot(inv.inventoryAtMs,rows,inv.cncPositionSymbols,amount)
        // Keep latest research reasons, not a second copy of the complete paper journal.
        val decisions=gson.toJson(rows.map{r->mapOf("symbol" to r.holding.symbol,"price_at_ms" to r.holding.priceAtMs,
            "kind" to r.candidate.kind,"reason" to r.candidate.reason,"paper_qty" to r.candidate.quantity,
            "daily_bar_count" to r.trend?.count,"armed" to r.state?.armed)})
        if(prefs.getString("latest_decisions","")!=decisions) prefs.edit().putString("latest_decisions",decisions).apply()
        return result
    }
}
