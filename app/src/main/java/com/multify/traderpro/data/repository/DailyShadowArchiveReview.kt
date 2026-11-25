package com.multify.traderpro.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.multify.traderpro.engine.DailyShadowTrialPolicy as Policy
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipFile

/**
 * Prospective, local-only challenger review of existing nightly diagnostics.
 * Never changes any selection, broker, or trailing policy. Missing paths and
 * outcomes stay unresolved. One newly registered parameter per validated day.
 */
internal object DailyShadowArchiveReview {
    private val gson=Gson()
    private val india=ZoneId.of("Asia/Kolkata")
    private const val FILE="daily_shadow_review_v1.json"
    data class Trial(val section:String,val parameter:String,val baseline:String,val challenger:String,
        val registeredDay:String,val registeredAtMs:Long,val days:List<Policy.Day> = emptyList(),
        val status:String="SHADOW_COLLECTING")
    data class Ledger(val schema:String="daily_shadow_review_v1",
        val trials:List<Trial> = emptyList(),val updatedAtMs:Long=0L,
        val tradingRulesChanged:Boolean=false)
    private fun JsonObject.str(k:String)=runCatching{get(k).asString}.getOrDefault("")
    private fun JsonObject.num(k:String)=runCatching{get(k).asDouble}.getOrNull()?.takeIf{it.isFinite()}
    private fun JsonObject.long(k:String)=runCatching{get(k).asLong}.getOrDefault(0L)
    private fun JsonObject.obj(k:String)=runCatching{getAsJsonObject(k)}.getOrNull()
    private fun JsonObject.bool(k:String)=runCatching{get(k).asBoolean}.getOrDefault(false)
    private fun JsonObject.rows(k:String)=runCatching{getAsJsonArray(k).map{it.asJsonObject}}.getOrDefault(emptyList())
    private fun ZipFile.read(name:String)=getEntry(name)?.let{e->getInputStream(e).bufferedReader().use{it.readText()}}
    private fun date(ms:Long)=Instant.ofEpochMilli(ms).atZone(india).toLocalDate().toString()
    private fun minute(ms:Long)=Instant.ofEpochMilli(ms).atZone(india).let{it.hour*60+it.minute}
    private fun <T> withDb(context:Context,zip:ZipFile,name:String,block:(SQLiteDatabase)->T):T? {
        val entry=zip.getEntry("research_db/"+name)?:return null
        val tmp=File.createTempFile("shadow-db-",".db",context.cacheDir)
        try {
            zip.getInputStream(entry).use{input->tmp.outputStream().use{input.copyTo(it)}}
            return SQLiteDatabase.openDatabase(tmp.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use(block)
        } finally {tmp.delete()}
    }
    private data class Entry(val id:String,val day:String,val symbol:String,val at:Long,val price:Double,
        val target:Double,val stop:Double,val keep:Boolean?)
    private data class Point(val at:Long,val prices:Map<String,Double>)
    private fun points(zip:ZipFile,symbols:Set<String>):List<Point> {
        val entry=zip.getEntry("fixed_market_path.jsonl")?:return emptyList()
        return zip.getInputStream(entry).bufferedReader().useLines{lines->
            lines.mapNotNull{line->runCatching{
                val j=JsonParser.parseString(line).asJsonObject
                if(j.str("phase")!="OFFICIAL_FIXED_MARKET_WIDE_DISCOVERY")return@runCatching null
                val prices=j.str("full_market_ltp_packed").split('|').mapNotNull{v->
                    val k=v.substringBefore('~');val p=v.substringAfter('~',"").toDoubleOrNull()
                    if(k in symbols && p!=null && p.isFinite() && p>0)k to p else null
                }.toMap()
                Point(j.long("sweep_completed_at_ms"),prices)
            }.getOrNull()}.toList().distinctBy{it.at}.sortedBy{it.at}
        }
    }
    private fun intraday(entry:Entry,points:List<Point>):Policy.Sample {
        val end=entry.at+15*60_000L
        val path=points.filter{it.at>entry.at && it.at<=end && date(it.at)==entry.day}
            .mapNotNull{p->p.prices[entry.symbol]?.let{p.at to (it/entry.price-1)*100}}
        // Full scheduled horizon must have elapsed with a near-end observation.
        // Sample order is not continuous price-path or executable fill evidence.
        if(path.isEmpty() || path.first().first-entry.at>5*60_000L ||
            path.last().first<end-5*60_000L ||
            path.zipWithNext().any{(a,b)->b.first-a.first>5*60_000L})
            return Policy.Sample(entry.id,entry.keep,null,null)
        var hit=false
        var ret=path.last().second
        for((_,v) in path){
            if(v<=-entry.stop){ret=v;break}
            if(v>=entry.target){hit=true;ret=entry.target;break}
        }
        return Policy.Sample(entry.id,entry.keep,hit,ret)
    }
    private fun fixed(zip:ZipFile):List<Entry> {
        val audit=zip.getEntry("system_audit.jsonl")?:return emptyList()
        return zip.getInputStream(audit).bufferedReader().useLines{lines->
            lines.filter{it.contains("PUBLISHED_RECOMMENDATION_STUDY_V9011")}.mapNotNull{line->
                runCatching{
                    val x=JsonParser.parseString(line).asJsonObject
                    if(x.str("event")!="PUBLISHED_RECOMMENDATION_STUDY_V9011")return@runCatching null
                    val j=x.obj("details")?:return@runCatching null
                    val at=j.long("published_at_ms");val px=j.num("published_entry_price")?:0.0
                    val target=j.num("published_target_pct")?:0.0
                    if(at<=0 || px<=0 || target<=0 || minute(at)>=15*60+15)return@runCatching null
                    Entry(j.str("recommendation_id"),date(at),j.str("symbol"),at,px,target,2.0,minute(at)<14*60+45)
                }.getOrNull()
            }.toList().sortedBy{it.at}.distinctBy{it.day+"|"+it.symbol}
        }
    }
    private fun range(zip:ZipFile):List<Entry> {
        val root=zip.read("working_range_recommendations_v9021.json")?.let{JsonParser.parseString(it).asJsonObject}
            ?:return emptyList()
        return root.rows("rows").mapNotNull{x->
            val j=x.obj("recommendation")?:return@mapNotNull null
            val at=j.long("confirmedAtMs");val px=j.num("entryPrice")?:0.0
            val first=j.num("firstQualifiedPrice");val target=j.num("targetPct")?:0.0
            val stop=j.num("researchStopPct")?:0.0
            if(at<=0 || px<=0 || target<=0 || stop<=0 || minute(at)>=15*60+15)return@mapNotNull null
            val drift=first?.takeIf{it>0}?.let{(px/it-1)*100}
            Entry(date(at)+"|"+x.str("symbol"),date(at),x.str("symbol"),at,px,target,stop,drift?.let{it<=1.50})
        }.distinctBy{it.id}
    }
    fun run(context:Context,archive:File):Ledger {
        val dest=File(context.filesDir,FILE)
        val old=if(dest.exists())gson.fromJson(dest.readText(),Ledger::class.java) else Ledger()
        val now=System.currentTimeMillis()
        val today=date(now)
        val result=ZipFile(archive).use{zip->
            val sessions=withDb(context,zip,"suhas_shree_eod_research_v1.db"){db->
                db.rawQuery("SELECT day FROM eod_days ORDER BY day",null).use{c->
                    buildList{while(c.moveToNext())add(c.getString(0))}
                }
            }.orEmpty()
            val trials=old.trials.toMutableList()
            if(today in sessions && trials.none{it.registeredDay==today}){
                Policy.plans.firstOrNull{p->trials.none{it.section==p.section}}?.let{p->
                    trials+=Trial(p.section,p.parameter,p.baseline,p.challenger,today,now)
                }
            }
            if(trials.isEmpty())return@use old.copy(updatedAtMs=now)
            val fixedEntries=fixed(zip)
            val rangeEntries=range(zip)
            val points=points(zip,(fixedEntries+rangeEntries).map{it.symbol}.toSet())
            val cohorts=mutableMapOf<String,MutableMap<String,MutableList<Policy.Sample>>>()
            fun add(section:String,day:String,sample:Policy.Sample){
                cohorts.getOrPut(section){linkedMapOf()}.getOrPut(day){mutableListOf()}+=sample
            }
            for(e in fixedEntries)add("FIXED",e.day,intraday(e,points))
            for(e in rangeEntries)add("RANGE",e.day,intraday(e,points))
            fun horizon(day:String,n:Int)=sessions.filter{it>day}.getOrNull(n-1)
            withDb(context,zip,"large_watch_learning_v1.db"){db->
                db.rawQuery("SELECT day,symbol,feature_json,outcome_day,outcome_ret,outcome_hit FROM watch_event WHERE kind='SWING'",null).use{c->
                    while(c.moveToNext()){
                        val day=c.getString(0)
                        val f=runCatching{JsonParser.parseString(c.getString(2)).asJsonObject.obj("features")}.getOrNull()
                        val keep=f?.num("relative_5d_vs_equity_peers_pct")?.let{it>=0.30}
                        val mature=!c.isNull(3)&&horizon(day,3)==c.getString(3)&&!c.isNull(4)&&!c.isNull(5)
                        add("LARGE",day,Policy.Sample(c.getString(1),keep,if(mature)c.getInt(5)==1 else null,
                            if(mature)c.getDouble(4)else null))
                    }
                }
            }
            withDb(context,zip,"adaptive_low_research_v1.db"){db->
                db.rawQuery("SELECT s.day,s.months,s.symbol,d.json,s.outcome_day,s.outcome_hit FROM low_signal s LEFT JOIN low_day d ON s.day=d.day AND s.months=d.months AND s.symbol=d.symbol",null).use{c->
                    while(c.moveToNext()){
                        val day=c.getString(0)
                        val f=if(c.isNull(3))null else runCatching{JsonParser.parseString(c.getString(3)).asJsonObject}.getOrNull()
                        val keep=f?.num("distanceAboveSupportPct")?.let{it<=4.90}
                        val mature=!c.isNull(4)&&horizon(day,10)==c.getString(4)&&!c.isNull(5)
                        // Dedupe stock/day across 3M and 6M; 3M/6M is not two independent trades.
                        add("LOWS",day,Policy.Sample(c.getString(2),keep,if(mature)c.getInt(5)==1 else null,null))
                    }
                }
            }
            val pref=zip.read("research_prefs/institutional_disclosure_prefs.json")?.let{JsonParser.parseString(it).asJsonObject}
            if(pref!=null){
                fun array(k:String)=runCatching{JsonParser.parseString(pref.str(k)).asJsonArray.map{it.asJsonObject}}.getOrDefault(emptyList())
                val outcomes=array("outcomes").associateBy{it.str("source")+"|"+it.str("day")+"|"+it.str("symbol")}
                for(report in array("history").distinctBy{it.str("source")+"|"+it.str("day")}){
                    val day=report.str("day");val source=report.str("source");val receipt=report.long("receivedAtMs")
                    if(receipt<=0 || day.isBlank())continue
                    val observedDay=maxOf(day,date(receipt))
                    val ranked=report.rows("deals").filter{it.str("side")=="BUY" && it.str("category") in setOf("FII","DII")}
                        .groupBy{it.str("symbol")}.map{(sym,rows)->sym to rows.sumOf{(it.num("shares")?:0.0)*(it.num("price")?:0.0)}}
                        .sortedWith(compareByDescending<Pair<String,Double>>{it.second}.thenBy{it.first}).take(20)
                    for((i,pair) in ranked.withIndex()){
                        val outcome=outcomes[source+"|"+day+"|"+pair.first]
                        val ret=outcome?.num("returnPct")?.takeIf{outcome.str("outcomeDay")==horizon(observedDay,6)}
                        add("FII_DII",observedDay,Policy.Sample(pair.first,i<19,ret?.let{it>=0.5},ret))
                    }
                }
            }
            val updated=trials.map{trial->
                val byDay=cohorts[trial.section].orEmpty()
                val previous=trial.days.associateBy{it.day}
                val evaluated=sessions.filter{it>trial.registeredDay && it<=today}.map{day->
                    val existing=previous[day]
                    if(existing!=null && existing.matured==existing.candidates && existing.candidates>0)existing
                    else if(byDay[day].isNullOrEmpty() && existing!=null)existing
                    else Policy.assess(day,byDay[day].orEmpty(),trial.section!="LOWS")
                }
                trial.copy(days=(trial.days+evaluated).associateBy{it.day}.values.sortedBy{it.day},
                    status=Policy.status((trial.days+evaluated).associateBy{it.day}.values.toList()))
            }
            Ledger(trials=updated,updatedAtMs=now)
        }
        val temp=File(context.filesDir,FILE+".part")
        temp.writeText(gson.toJson(result))
        check(temp.renameTo(dest)){"Unable to atomically persist shadow review"}
        context.getSharedPreferences("phayra_shadow_review",Context.MODE_PRIVATE).edit()
            .putString("summary",result.trials.joinToString(" · "){it.section+": "+Policy.consecutiveImproved(it.days)+"/3 consecutive improved matured days ("+it.status+")"}).apply()
        return result
    }
}
