package com.multify.traderpro.data.network

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only completed/deferred/failed Fixed scan history. Bounded to 160 records,
 * persisted across service restarts without network calls or strategy changes.
 */
object FixedScanTrendStore {
    data class Point(val id: String, val atMs: Long, val elapsedMs: Long,
        val result: String, val alphaLive: Long, val betaLive: Long,
        val alphaHistorical: Long, val betaHistorical: Long,
        val alpha429: Long, val beta429: Long)
    private const val PREF = "fixed_scan_diagnostic_history"
    private const val KEY = "recent"
    private const val LIMIT = 160
    private val lock = Any()
    fun record(context: Context, point: Point) = synchronized(lock) {
        val list = read(context).toMutableList()
        list.add(point)
        val data = JSONArray()
        list.takeLast(LIMIT).forEach { p ->
            data.put(JSONObject().put("id", p.id).put("at", p.atMs)
                .put("ms", p.elapsedMs).put("result", p.result)
                .put("aLive", p.alphaLive).put("bLive", p.betaLive)
                .put("aHist", p.alphaHistorical).put("bHist", p.betaHistorical)
                .put("a429", p.alpha429).put("b429", p.beta429))
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, data.toString()).apply()
    }
    fun read(context: Context): List<Point> = synchronized(lock) {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "[]").orEmpty()
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                Point(j.optString("id"),j.optLong("at"),j.optLong("ms"),j.optString("result"),
                    j.optLong("aLive"),j.optLong("bLive"),j.optLong("aHist"),j.optLong("bHist"),
                    j.optLong("a429"),j.optLong("b429"))
            }
        }.getOrDefault(emptyList())
    }
    fun percentile(values: List<Long>, fraction: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = kotlin.math.ceil(sorted.size * fraction).toInt().coerceIn(1, sorted.size)-1
        return sorted[index]
    }
}
