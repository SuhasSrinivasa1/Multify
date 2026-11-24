package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.multify.traderpro.engine.InstitutionalReport
import com.multify.traderpro.engine.InstitutionalSnapshot

/** Independent local cache. A rejected response never erases a valid previous report. */
internal class InstitutionalDisclosureStore(context:Context) {
    private val prefs=context.getSharedPreferences("institutional_disclosure_v1",Context.MODE_PRIVATE)
    private val gson=Gson()
    private val lock=Any()
    private var cached=InstitutionalSnapshot(read("BULK"),read("BLOCK"))

    private fun read(source:String):InstitutionalReport? = runCatching {
        prefs.getString("report_"+source,null)?.let{gson.fromJson(it,InstitutionalReport::class.java)}
            ?.takeIf{it.source==source && it.day.isNotBlank() && it.disclosedRows>0}
    }.getOrNull()
    fun snapshot():InstitutionalSnapshot=synchronized(lock){cached}
    fun save(report:InstitutionalReport)=synchronized(lock){
        require(report.source in setOf("BULK","BLOCK") && report.disclosedRows>0)
        val old=if(report.source=="BULK")cached.bulk else cached.block
        if(old!=null && old.day>report.day)return@synchronized
        check(prefs.edit().putString("report_"+report.source,gson.toJson(report)).commit())
        cached=if(report.source=="BULK")cached.copy(bulk=report) else cached.copy(block=report)
    }
}
