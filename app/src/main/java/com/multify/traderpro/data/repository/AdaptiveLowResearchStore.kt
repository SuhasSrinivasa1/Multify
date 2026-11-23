package com.multify.traderpro.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.multify.traderpro.engine.AdaptiveLowCandidate
import java.time.LocalDate
import kotlin.math.sqrt

data class AdaptiveLowRunState(
    val day: String,
    val months: Int,
    val allNseEquitySymbols: Int,
    val historicallyCompleteSymbols: Int,
    val qualifiedPatterns: Int,
    val completedAtMs: Long
)

data class AdaptiveLowResearchSummary(
    val months: Int,
    val variant: String,
    val matured: Int,
    val successes: Int,
    val independentDays: Int,
    val observedPct: Double,
    val wilsonLowerPct: Double,
    val heldOut: Int,
    val heldOutDays: Int,
    val heldOutWilsonLowerPct: Double,
    val researchFreezeEligible: Boolean
)

internal object AdaptiveLowLearningPolicy {
    const val SCHEMA="ADAPTIVE_LOW_REBOUND_RETEST_V1"
    const val FULL_HORIZON_SESSIONS=10
    const val FORWARD_EOD_REBOUND_PCT=5.0
    const val MIN_MATURED=100
    const val MIN_DAYS=20
    const val MIN_HOLDOUT=50
    const val MIN_HOLDOUT_DAYS=10

    fun variants(c:AdaptiveLowCandidate):Set<String> {
        val v=linkedSetOf("ALL_CONFIRMED_RETESTS")
        if(c.reactions.size>=3)v+="THREE_PRIOR_REBOUNDS"
        if(c.niftyEodPct!=null && c.niftyEodPct>=0.0)v+="NIFTY_NON_BEARISH_EOD"
        if(c.sectorEodPct!=null && c.sectorEodPct>=0.0)v+="SECTOR_NON_BEARISH_EOD"
        if(c.volumeTo20Day!=null && c.volumeTo20Day>=1.0)v+="ELEVATED_VOLUME"
        if(c.reactions.size>=3 && c.sectorEodPct!=null && c.sectorEodPct>=0.0)
            v+="THREE_REBOUNDS_SECTOR_ALIGNED"
        return v
    }
    fun wilsonLower(hits:Int,n:Int,z:Double=1.96):Double {
        if(n<=0)return 0.0
        val p=hits.toDouble()/n
        val den=1+z*z/n
        val centre=p+z*z/(2*n)
        val margin=z*sqrt((p*(1-p)+z*z/(4*n))/n)
        return (centre-margin)/den
    }

    fun freezeEligible(n:Int,days:Int,hits:Int,heldOut:Int,heldDays:Int,heldHits:Int):Boolean =
        n>=MIN_MATURED && days>=MIN_DAYS &&
        heldOut>=MIN_HOLDOUT && heldDays>=MIN_HOLDOUT_DAYS &&
        hits.toDouble()/n>=0.90 && wilsonLower(hits,n)>=0.85 &&
        heldHits.toDouble()/heldOut>=0.90 && wilsonLower(heldHits,heldOut)>=0.85
}

/**
 * Separate SQLite research-only store. Does not share a broker/trading database.
 * Daily snapshots are immutable once completed; sparse future data stays unresolved.
 */
internal class AdaptiveLowResearchStore(context:Context) {
    private class Store(context:Context):
        SQLiteOpenHelper(context,"adaptive_low_research_v1.db",null,1){
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE run(day TEXT NOT NULL, months INTEGER NOT NULL, universe INTEGER NOT NULL, complete INTEGER NOT NULL, patterns INTEGER NOT NULL, at_ms INTEGER NOT NULL, PRIMARY KEY(day,months))")
            db.execSQL("CREATE TABLE low_day(day TEXT NOT NULL,months INTEGER NOT NULL,symbol TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(day,months,symbol))")
            db.execSQL("CREATE TABLE low_signal(day TEXT NOT NULL,months INTEGER NOT NULL,symbol TEXT NOT NULL,support REAL NOT NULL,reference_close REAL NOT NULL,variants TEXT NOT NULL,outcome_day TEXT,outcome_max_close_pct REAL,outcome_min_close_pct REAL,first_rebound_day TEXT,outcome_hit INTEGER,PRIMARY KEY(day,months,symbol))")
            db.execSQL("CREATE INDEX idx_adaptive_low_signal_pending ON low_signal(outcome_day,day)")
        }
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=
            error("Adaptive low research migration requires explicit implementation")
    }
    private val store=Store(context)
    private val lock=Any()
    private val gson=Gson()

    fun hasRun(day:String,months:Int):Boolean=synchronized(lock){
        store.readableDatabase.rawQuery("SELECT 1 FROM run WHERE day=? AND months=?",
            arrayOf(day,months.toString())).use{it.moveToFirst()}
    }

    /** Atomic database publication: old valid snapshot remains until new complete pass. */
    fun saveDay(
        day:String,months:Int,universe:Int,complete:Int,rows:List<AdaptiveLowCandidate>
    )=synchronized(lock) {
        require(months in listOf(3,6))
        require(LocalDate.parse(day).toString()==day && universe>=1500)
        require(complete>=0 && complete<=universe)
        require(rows.all{it.months==months && it.asOfDay==day && it.symbol.isNotBlank()})
        val db=store.writableDatabase
        db.beginTransaction()
        try {
            db.delete("low_day","day=? AND months=?",arrayOf(day,months.toString()))
            for(c in rows) {
                db.insertOrThrow("low_day",null,ContentValues().apply{
                    put("day",day);put("months",months)
                    put("symbol",c.symbol);put("json",gson.toJson(c))
                })
                // One independent research setup per symbol/window every 21 calendar days,
                // unless the source support itself has materially changed.
                val latest=db.rawQuery(
                    "SELECT day,support FROM low_signal WHERE symbol=? AND months=? ORDER BY day DESC LIMIT 1",
                    arrayOf(c.symbol,months.toString())).use{x->
                    if(x.moveToFirst())x.getString(0) to x.getDouble(1) else null
                }
                val prior=latest?.let{runCatching{LocalDate.parse(it.first)}.getOrNull()}
                val recent=prior!=null && !prior.isBefore(LocalDate.parse(day).minusDays(21))
                val sameLevel=latest!=null && latest.second>0.0 &&
                    kotlin.math.abs(c.supportPrice/latest.second-1.0)*100.0<=2.5
                if(recent && sameLevel)continue
                db.insertWithOnConflict("low_signal",null,ContentValues().apply{
                    put("day",day);put("months",months);put("symbol",c.symbol)
                    put("support",c.supportPrice);put("reference_close",c.latestClose)
                    put("variants",AdaptiveLowLearningPolicy.variants(c).joinToString("|"))
                },SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.insertWithOnConflict("run",null,ContentValues().apply{
                put("day",day);put("months",months);put("universe",universe)
                put("complete",complete);put("patterns",rows.size)
                put("at_ms",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }

    fun latestRun(months:Int):AdaptiveLowRunState?=synchronized(lock){
        store.readableDatabase.rawQuery(
            "SELECT day,months,universe,complete,patterns,at_ms FROM run WHERE months=? ORDER BY day DESC LIMIT 1",
            arrayOf(months.toString())).use{c->
            if(!c.moveToFirst())null
            else AdaptiveLowRunState(c.getString(0),c.getInt(1),c.getInt(2),
                c.getInt(3),c.getInt(4),c.getLong(5))
        }
    }

    /** UI reads the fully published EOD candidate snapshot: no scan or HTTP calls. */
    fun rows(months:Int,latestDay:String):List<AdaptiveLowCandidate>=synchronized(lock){
        if(!hasRun(latestDay,months))return@synchronized emptyList()
        val result=mutableListOf<AdaptiveLowCandidate>()
        store.readableDatabase.rawQuery(
            "SELECT json FROM low_day WHERE day=? AND months=?",
            arrayOf(latestDay,months.toString())).use{c->
            while(c.moveToNext())runCatching{
                gson.fromJson(c.getString(0),AdaptiveLowCandidate::class.java)
            }.getOrNull()?.let{result+=it}
        }
        result.sortedWith(compareByDescending<AdaptiveLowCandidate>{it.reactions.size}
            .thenBy{it.distanceAboveSupportPct}
            .thenByDescending{it.patternScore}.thenBy{it.symbol})
    }

    data class Pending(val day:String,val months:Int,val symbol:String,val close:Double)

    fun pending():List<Pending>=synchronized(lock){
        val list=mutableListOf<Pending>()
        store.readableDatabase.rawQuery(
            "SELECT day,months,symbol,reference_close FROM low_signal WHERE outcome_day IS NULL ORDER BY day ASC LIMIT 1500",
            null).use{c->while(c.moveToNext()){
            list+=Pending(c.getString(0),c.getInt(1),c.getString(2),c.getDouble(3))
        }}
        list
    }

    /** Mature only after ten full subsequent SYMBOL sessions; EOD closes only. */
    fun resolve(p:Pending,forward:List<Pair<String,Double>>)=synchronized(lock){
        if(forward.size!=AdaptiveLowLearningPolicy.FULL_HORIZON_SESSIONS ||
           !forward.all{it.second.isFinite() && it.second>0.0} ||
           p.close<=0.0)return@synchronized
        val returns=forward.map{(it.second/p.close-1.0)*100.0}
        val first=returns.indexOfFirst{it>=AdaptiveLowLearningPolicy.FORWARD_EOD_REBOUND_PCT}
        val hit=first>=0
        store.writableDatabase.update("low_signal",ContentValues().apply{
            put("outcome_day",forward.last().first)
            put("outcome_max_close_pct",returns.maxOrNull())
            put("outcome_min_close_pct",returns.minOrNull())
            if(hit)put("first_rebound_day",forward[first].first)
            put("outcome_hit",if(hit)1 else 0)
        },"day=? AND months=? AND symbol=? AND outcome_day IS NULL",
            arrayOf(p.day,p.months.toString(),p.symbol))
    }

    fun learning(months:Int):List<AdaptiveLowResearchSummary> = synchronized(lock) {
        data class E(val day:String,val vs:Set<String>,val hit:Boolean)
        val all=mutableListOf<E>()
        store.readableDatabase.rawQuery(
            "SELECT day,variants,outcome_hit FROM low_signal WHERE months=? AND outcome_day IS NOT NULL ORDER BY day,symbol",
            arrayOf(months.toString())).use{c->while(c.moveToNext()){
            all+=E(c.getString(0),c.getString(1).split('|').toSet(),c.getInt(2)==1)
        }}
        val names=listOf("ALL_CONFIRMED_RETESTS","THREE_PRIOR_REBOUNDS",
            "NIFTY_NON_BEARISH_EOD","SECTOR_NON_BEARISH_EOD","ELEVATED_VOLUME",
            "THREE_REBOUNDS_SECTOR_ALIGNED")
        names.mapNotNull{variant->
            val rows=all.filter{variant in it.vs}
            if(rows.isEmpty())return@mapNotNull null
            val days=rows.map{it.day}.distinct()
            val holdoutDays=days.takeLast(maxOf(10,(days.size+2)/3)).toSet()
            val hold=rows.filter{it.day in holdoutDays}
            val hits=rows.count{it.hit}
            val heldHits=hold.count{it.hit}
            AdaptiveLowResearchSummary(months,variant,rows.size,hits,days.size,
                hits*100.0/rows.size,
                AdaptiveLowLearningPolicy.wilsonLower(hits,rows.size)*100.0,
                hold.size,holdoutDays.size,
                AdaptiveLowLearningPolicy.wilsonLower(heldHits,hold.size)*100.0,
                AdaptiveLowLearningPolicy.freezeEligible(rows.size,days.size,hits,
                    hold.size,holdoutDays.size,heldHits))
        }.sortedWith(compareByDescending<AdaptiveLowResearchSummary>{it.wilsonLowerPct}
            .thenByDescending{it.matured})
    }
}
