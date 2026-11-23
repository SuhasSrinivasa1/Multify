package com.multify.traderpro.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import java.time.LocalDate
import kotlin.math.sqrt

data class LargeWatchLearningSummary(
    val type:String,
    val variant:String,
    val matured:Int,
    val hits:Int,
    val independentDays:Int,
    val hitRatePct:Double,
    val wilsonLowerPct:Double,
    val holdoutCount:Int,
    val holdoutDays:Int,
    val holdoutWilsonLowerPct:Double,
    val researchFreezeEligible:Boolean
)

/**
 * Shadow-only prospective watch snapshots. No BUY, SELL, GTT, recommendation changes,
 * model mutation or account API access. EOD-close outcomes do not imply executable fills.
 */
internal object LargeWatchLearningPolicy {
    const val HORIZON = "SWING_THIRD_FUTURE_EOD_CLOSE_OR_INTRADAY_SAME_DAY_EOD_CLOSE"
    const val HIT_PCT = 0.50
    const val MIN_EVENTS = 100
    const val MIN_DAYS = 20
    const val MIN_HOLDOUT = 50
    const val MIN_HOLDOUT_DAYS = 10

    fun variants(e:LargeWatchEvidence):Set<String>{
        val f=e.features
        val variants=linkedSetOf("BASELINE")
        val nifty=f["nifty_eod_move_pct"]
        val sector=f["sector_eod_move_pct"]
        val stock5=f["return_5d_pct"]
        val sector5=f["sector_5d_move_pct"]
        val rsi=f["rsi14"]
        val v=f["volume_avg5_to_avg20"]
        val e9=f["ema9"]; val e20=f["ema20"]
        if(nifty!=null && sector!=null && nifty>=0.0 && sector>=0.0)
            variants+="NIFTY_AND_SECTOR_NONNEGATIVE"
        if(stock5!=null && sector5!=null && sector!=null &&
            stock5>sector5 && sector>0.0)
            variants+="SECTOR_RELATIVE_LEADER"
        if(rsi!=null && v!=null && e9!=null && e20!=null &&
            rsi in 50.0..75.0 && v>=1.0 && e9>e20)
            variants+="MOMENTUM_CONSISTENT"
        if("NIFTY_AND_SECTOR_NONNEGATIVE" in variants &&
            "SECTOR_RELATIVE_LEADER" in variants)
            variants+="MARKET_SECTOR_COMBINED"
        return variants
    }

    fun wilsonLower(hits:Int,total:Int,z:Double=1.96):Double {
        if(total<=0)return 0.0
        val n=total.toDouble();val p=hits.toDouble()/n
        val denom=1.0+z*z/n
        val centre=p+z*z/(2*n)
        val margin=z*sqrt((p*(1-p)+z*z/(4*n))/n)
        return (centre-margin)/denom
    }
    fun freezeEligible(total:Int,days:Int,hits:Int,holdoutN:Int,holdoutDays:Int,holdoutHits:Int):Boolean =
        total>=MIN_EVENTS && days>=MIN_DAYS &&
            holdoutN>=MIN_HOLDOUT && holdoutDays>=MIN_HOLDOUT_DAYS &&
            hits.toDouble()/total>=0.90 && wilsonLower(hits,total)>=0.85 &&
            holdoutHits.toDouble()/holdoutN>=0.90 &&
            wilsonLower(holdoutHits,holdoutN)>=0.85
}

internal class LargeWatchLearningStore(context:Context) {
    private class Helper(context:Context):SQLiteOpenHelper(context,"large_watch_learning_v1.db",null,1){
        override fun onCreate(db:SQLiteDatabase){
            db.execSQL("CREATE TABLE index_eod(day TEXT NOT NULL,name TEXT NOT NULL,close REAL NOT NULL,change_pct REAL,pe REAL,pb REAL,PRIMARY KEY(day,name))")
            db.execSQL("CREATE INDEX idx_large_index_name_day ON index_eod(name,day)")
            db.execSQL("CREATE TABLE watch_event(day TEXT NOT NULL,symbol TEXT NOT NULL,kind TEXT NOT NULL,ref_price REAL NOT NULL,observed_ms INTEGER NOT NULL,feature_json TEXT NOT NULL,variant_codes TEXT NOT NULL,outcome_day TEXT,outcome_ret REAL,outcome_hit INTEGER,PRIMARY KEY(day,symbol,kind))")
            db.execSQL("CREATE INDEX idx_watch_outcome ON watch_event(outcome_day,kind)")
        }
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=
            error("Large watch learning migration must be explicit")
    }
    private val db=Helper(context)
    private val lock=Any()
    private val gson=Gson()

    fun hasIndex(day:String):Boolean=synchronized(lock){
        db.readableDatabase.rawQuery(
            "SELECT 1 FROM index_eod WHERE day=? AND name='NIFTY 50' LIMIT 1",
            arrayOf(day)).use{it.moveToFirst()}
    }

    fun saveIndex(day:String,entries:List<LargeIndexDaily>)=synchronized(lock){
        require(entries.size>=30 && entries.all{it.day.toString()==day})
        val w=db.writableDatabase
        w.beginTransaction()
        try{
            w.delete("index_eod","day=?",arrayOf(day))
            for(x in entries){
                w.insertOrThrow("index_eod",null,ContentValues().apply{
                    put("day",day);put("name",x.name);put("close",x.close)
                    if(x.changePct!=null)put("change_pct",x.changePct)
                    if(x.pe!=null)put("pe",x.pe)
                    if(x.pb!=null)put("pb",x.pb)
                })
            }
            w.setTransactionSuccessful()
        }finally{w.endTransaction()}
    }

    fun indexHistories(day:String):Map<String,List<LargeIndexDaily>>=synchronized(lock){
        val all=linkedMapOf<String,MutableList<LargeIndexDaily>>()
        db.readableDatabase.rawQuery(
            "SELECT day,name,close,change_pct,pe,pb FROM index_eod WHERE day<=? ORDER BY day DESC LIMIT 2000",
            arrayOf(day)).use{c->
            while(c.moveToNext()){
                val name=c.getString(1)
                val series=all.getOrPut(name){mutableListOf()}
                if(series.size>=7)continue
                series+=LargeIndexDaily(LocalDate.parse(c.getString(0)),name,c.getDouble(2),
                    if(c.isNull(3))null else c.getDouble(3),
                    if(c.isNull(4))null else c.getDouble(4),
                    if(c.isNull(5))null else c.getDouble(5))
            }
        }
        all.mapValues{it.value.asReversed()}
    }

    fun record(day:String,symbol:String,kind:String,price:Double,
               capturedAtMs:Long,e:LargeWatchEvidence) = synchronized(lock){
        require(kind=="SWING"||kind=="INTRADAY")
        require(price.isFinite()&&price>0 && e.eodDate.isNotBlank())
        require(day.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val variants=LargeWatchLearningPolicy.variants(e).joinToString("|")
        db.writableDatabase.insertWithOnConflict(
            "watch_event",null,ContentValues().apply{
                put("day",day);put("symbol",symbol);put("kind",kind)
                put("ref_price",price);put("observed_ms",capturedAtMs)
                put("feature_json",gson.toJson(mapOf(
                    "features" to e.features, "feature_eod_date" to e.eodDate,
                    "market_index_date" to e.marketDate,
                    "nifty_regime" to e.niftyRegime,
                    "sector_index" to e.sectorIndex,
                    "sector_regime" to e.sectorRegime,
                    "stock_pb_source" to "NOT_AVAILABLE",
                    "outcome_definition" to LargeWatchLearningPolicy.HORIZON
                )))
                put("variant_codes",variants)
            },SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Unresolved events remain censored until the required EOD horizon matures. */
    fun unresolved():List<Triple<String,String,String>>=synchronized(lock){
        val events=mutableListOf<Triple<String,String,String>>()
        db.readableDatabase.rawQuery(
            "SELECT day,symbol,kind FROM watch_event WHERE outcome_day IS NULL ORDER BY day,symbol LIMIT 2500",
            null).use{c->while(c.moveToNext())events+=Triple(c.getString(0),c.getString(1),c.getString(2))}
        events
    }

    fun resolveEvent(day:String,symbol:String,kind:String,referenceOutcomeClose:Double,outcomeDay:String)=synchronized(lock){
        if(!referenceOutcomeClose.isFinite() || referenceOutcomeClose<=0.0)return@synchronized
        val original=db.readableDatabase.rawQuery(
            "SELECT ref_price FROM watch_event WHERE day=? AND symbol=? AND kind=? AND outcome_day IS NULL",
            arrayOf(day,symbol,kind)).use{c->if(c.moveToFirst())c.getDouble(0)else null}
            ?:return@synchronized
        if(!original.isFinite()||original<=0.0)return@synchronized
        val ret=(referenceOutcomeClose/original-1.0)*100.0
        db.writableDatabase.update("watch_event",ContentValues().apply{
            put("outcome_day",outcomeDay);put("outcome_ret",ret)
            put("outcome_hit",if(ret>=LargeWatchLearningPolicy.HIT_PCT)1 else 0)
        },"day=? AND symbol=? AND kind=? AND outcome_day IS NULL",arrayOf(day,symbol,kind))
    }

    fun learningSummaries():List<LargeWatchLearningSummary>=synchronized(lock){
        data class Event(val day:String,val kind:String,val variants:Set<String>,val hit:Boolean)
        val observed=mutableListOf<Event>()
        db.readableDatabase.rawQuery(
            "SELECT day,kind,variant_codes,outcome_hit FROM watch_event WHERE outcome_day IS NOT NULL ORDER BY day,symbol",
            null).use{c->while(c.moveToNext()){
                observed+=Event(c.getString(0),c.getString(1),
                    c.getString(2).split('|').toSet(),c.getInt(3)==1)
            }}
        val result=mutableListOf<LargeWatchLearningSummary>()
        for(kind in listOf("SWING","INTRADAY")){
            for(variant in listOf("BASELINE","NIFTY_AND_SECTOR_NONNEGATIVE",
                "SECTOR_RELATIVE_LEADER","MOMENTUM_CONSISTENT","MARKET_SECTOR_COMBINED")){
                val data=observed.filter{it.kind==kind && variant in it.variants}
                if(data.isEmpty())continue
                val days=data.map{it.day}.distinct()
                // Holdout is a CHRONOLOGICALLY LAST portion; only matured outcomes count.
                val lastDays=days.takeLast(maxOf(10,(days.size+2)/3)).toSet()
                val holdout=data.filter{it.day in lastDays}
                val hits=data.count{it.hit}
                val holdoutHits=holdout.count{it.hit}
                val eligible=LargeWatchLearningPolicy.freezeEligible(
                    data.size,days.size,hits,holdout.size,lastDays.size,holdoutHits)
                result+=LargeWatchLearningSummary(kind,variant,data.size,hits,days.size,
                    100.0*hits/data.size,
                    100.0*LargeWatchLearningPolicy.wilsonLower(hits,data.size),
                    holdout.size,lastDays.size,
                    100.0*LargeWatchLearningPolicy.wilsonLower(holdoutHits,holdout.size),
                    eligible)
            }
        }
        result.sortedWith(compareBy<LargeWatchLearningSummary>{it.type}
            .thenByDescending{it.wilsonLowerPct})
    }
}
