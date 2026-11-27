package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Pure research only. No broker client, no real transaction or protection authority. */
object PortfolioShadowPolicy {
    data class Bar(val day:String,val close:Double,val high:Double,val low:Double,val volume:Long)
    data class Trend(val count:Int,val latest:String,val ema20:Double,val ema50:Double,val atr:Double,val rising:Boolean,val support:Double)
    data class Sample(val at:Long,val price:Double)
    data class State(val reference:Double,val brokerQuantity:Int,val brokerAverage:Double,
        val peak:Double=0.0,val armed:Boolean=false,val lastAt:Long=0L,
        val stages:Int=0,val nextProfit:Int=10,val paperRemaining:Int=brokerQuantity,
        val samples:List<Sample> = emptyList(),val events:List<Event> = emptyList(),
        val changed:Boolean=false)
    data class Event(val at:Long,val kind:String,val trigger:Double,val observed:Double,val paperQuantity:Int)
    data class Candidate(val kind:String,val reason:String,val quantity:Int=0)
    fun initial(avg:Double,qty:Int):State {
        require(avg.isFinite()&&avg>0&&qty>0)
        return State(avg,qty,avg)
    }
    fun trend(bars:List<Bar>):Trend? {
        val b=bars.sortedBy{it.day}.distinctBy{it.day}
        if(b.size<60 || b.any{!it.close.isFinite()||!it.high.isFinite()||!it.low.isFinite()||
                it.low<=0||it.high<it.close||it.low>it.close})return null
        fun ema(n:Int,rows:List<Bar>):Double {
            val a=2.0/(n+1);return rows.drop(1).fold(rows.first().close){v,x->v+(x.close-v)*a}
        }
        val e20=ema(20,b);val e50=ema(50,b)
        val tr=b.zipWithNext().takeLast(14).map{(a,c)->max(c.high-c.low,max(kotlin.math.abs(c.high-a.close),kotlin.math.abs(c.low-a.close)))}
        val atr=tr.average()
        if(!atr.isFinite()||atr<=0)return null
        return Trend(b.size,b.last().day,e20,e50,atr,
            e20>e50&&e20>ema(20,b.dropLast(5))&&e50>ema(50,b.dropLast(5))&&b.last().close>e20,
            b.takeLast(10).minOf{it.low})
    }
    fun levels(state:State):List<Double> = if(!state.armed) emptyList() else
        listOf(.05,.08,.10).map{state.peak-state.reference*it}
    fun stepQty(price:Double,budget:Double,feeReservePct:Double=0.5):Int {
        if(!price.isFinite()||price<=0||!budget.isFinite()||budget<=0||
            !feeReservePct.isFinite()||feeReservePct<0)return 0
        return floor(budget/(price*(1+feeReservePct/100))).coerceIn(0.0,Int.MAX_VALUE.toDouble()).toInt()
    }
    /** Sampled paper SELL only; a gap can cross multiple stages, never claims a broker fill. */
    fun observe(old:State,qty:Int,avg:Double,price:Double,at:Long):State {
        if(old.changed||qty!=old.brokerQuantity||!avg.isFinite()||kotlin.math.abs(avg-old.brokerAverage)>.0001)
            return old.copy(changed=true)
        if(!price.isFinite()||price<=0||at<=old.lastAt)return old
        val peak=max(old.peak,price)
        var s=old.copy(peak=peak,armed=old.armed||peak>=old.reference*1.20-1e-8,lastAt=at,
            samples=(old.samples+Sample(at,price)).filter{at-it.at in 0..60*60_000L}.takeLast(60))
        val events=mutableListOf<Event>()
        fun sell(kind:String,trigger:Double) {
            val n=PortfolioLadderPolicy.sellQuantity(s.paperRemaining)
            events+=Event(at,kind,trigger,price,n)
            s=s.copy(paperRemaining=s.paperRemaining-n)
        }
        // Protect first, then milestones; one stage per level, no sell below a previously observed milestone.
        val stops=levels(s)
        while(s.stages<stops.size&&price<=stops[s.stages]+1e-8) {
            val i=s.stages;sell("PAPER_TRAIL_"+(i+1),stops[i]);s=s.copy(stages=i+1)
        }
        // At most one milestone per sampled observation, never replay historical profits at adoption.
        val target=s.reference*(1+s.nextProfit/100.0)
        if(events.isEmpty()&&price>=target-1e-8&&s.paperRemaining>=4) {
            sell("PAPER_PROFIT_"+s.nextProfit,target)
            s=s.copy(nextProfit=s.nextProfit+10)
        }
        return s.copy(events=(s.events+events).takeLast(200))
    }
    /** Candidate only: sweep samples are NOT exchange OHLC candles or real entry confirmation. */
    fun candidate(s:State,t:Trend?,price:Double,budget:Double,negativeNews:Boolean):Candidate {
        if(s.changed)return Candidate("BLOCKED","Broker holding changed; reconciliation needed")
        if(negativeNews)return Candidate("BLOCKED","Material negative-news review")
        if(t==null)return Candidate("WAIT","At least 60 valid daily bars required")
        if(s.armed&&s.stages>0)return Candidate("WAIT","Protection triggered; no further additions")
        val n=stepQty(price,budget)
        if(n==0)return Candidate("WAIT","No whole share within step budget")
        val a=s.samples.groupBy{it.at/300_000L}.values.map{it.last()}.takeLast(4)
        if(a.size<4||a.last().at-a.first().at<10*60_000L||a.last().at-a.first().at>25*60_000L)
            return Candidate("WAIT","Need distinct recent sampled buckets")
        val recovering=a.last().price>a[a.lastIndex-1].price&&a[a.lastIndex-1].price>a[a.lastIndex-2].price
        val up=price>=s.reference*1.05&&t.rising&&price>t.ema20&&price-t.ema20<=1.5*t.atr
        val down=price<=s.reference*.90&&price>=t.support&&recovering
        return when {
            up&&recovering -> Candidate("UP_RESEARCH","Daily trend and sampled continuation; actual 15m/5m, RVOL, relative strength and liquidity unverified",n)
            down -> Candidate("DOWN_RESEARCH","Downward zone and sampled recovery; actual candle, fundamentals and liquidity unverified",n)
            else -> Candidate("WAIT","No sampled continuation/recovery setup")
        }
    }
}
