package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.multify.traderpro.engine.*
import java.time.ZonedDateTime

/** Bounded local archive. Same-date corrections replace rather than append. */
internal class InstitutionalDisclosureStore(context:Context) {
    private val prefs=context.getSharedPreferences("institutional_disclosure_v1",Context.MODE_PRIVATE)
    private val gson=Gson()
    private val lock=Any()
    private fun read(source:String):InstitutionalReport?=runCatching {
        prefs.getString("report_"+source,null)?.let{gson.fromJson(it,InstitutionalReport::class.java)}
            ?.takeIf{it.source==source && it.day.isNotBlank() && it.disclosedRows>0}
    }.getOrNull()
    private var cached=InstitutionalSnapshot(read("BULK"),read("BLOCK"),
        history=runCatching{gson.fromJson(prefs.getString("history",null),Array<InstitutionalReport>::class.java)?.toList().orEmpty()}.getOrDefault(emptyList()),
        hourly=runCatching{gson.fromJson(prefs.getString("hourly",null),InstitutionalHourly::class.java)}.getOrNull(),
        outcomes=runCatching{gson.fromJson(prefs.getString("outcomes",null),Array<InstitutionalOutcome>::class.java)?.toList().orEmpty()}.getOrDefault(emptyList()))
    private var tracked=(cached.history+listOfNotNull(cached.bulk,cached.block)).flatMap{it.deals}.map{it.symbol}.toSet()
    private var lastSlot=cached.hourly?.let{it.day+"-"+it.hour}.orEmpty()
    fun snapshot():InstitutionalSnapshot=synchronized(lock){cached}
    fun save(report:InstitutionalReport)=synchronized(lock){
        require(report.source in setOf("BULK","BLOCK") && report.disclosedRows>0)
        val old=if(report.source=="BULK")cached.bulk else cached.block
        val history=(cached.history+listOfNotNull(cached.bulk,cached.block)+report)
            .associateBy{it.source+"|"+it.day}.values.groupBy{it.source}
            .values.flatMap{it.sortedByDescending{r->r.day}.take(60)}
        val newer=old==null || old.day<=report.day
        val editor=prefs.edit().putString("history",gson.toJson(history))
        if(newer)editor.putString("report_"+report.source,gson.toJson(report))
        check(editor.commit())
        cached=cached.copy(history=history,
            bulk=if(newer&&report.source=="BULK")report else cached.bulk,
            block=if(newer&&report.source=="BLOCK")report else cached.block)
        tracked=history.flatMap{it.deals}.map{it.symbol}.toSet()
    }
    /** One passive sample per clock hour, only from a completed existing sweep.
     * No network request, database query, retry, or trading decision here. */
    fun captureHourly(at:ZonedDateTime,current:Map<String,Double>,open:Map<String,Double>):Boolean=synchronized(lock){
        val slot=InstitutionalHistoryPolicy.slot(at)?:return@synchronized false
        if(slot==lastSlot || tracked.isEmpty())return@synchronized false
        val prices=current.filter{(s,p)->s in tracked && p.isFinite() && p>0}
        if(prices.isEmpty())return@synchronized false
        val sample=InstitutionalHourly(at.toLocalDate().toString(),at.hour,System.currentTimeMillis(),
            prices,open.filter{(s,p)->s in prices && p.isFinite() && p>0})
        lastSlot=slot
        cached=cached.copy(hourly=sample)
        prefs.edit().putString("hourly",gson.toJson(sample)).apply()
        true
    }
    fun saveOutcomes(rows:List<InstitutionalOutcome>)=synchronized(lock){
        val next=(cached.outcomes+rows).associateBy{it.source+"|"+it.day+"|"+it.symbol}
            .values.sortedByDescending{it.day}.take(6000)
        prefs.edit().putString("outcomes",gson.toJson(next)).apply()
        cached=cached.copy(outcomes=next)
    }
}
