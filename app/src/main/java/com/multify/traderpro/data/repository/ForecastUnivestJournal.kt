package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.engine.ForecastUnivestLearningPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Separate, append-only, portable FORECAST research journal. Never creates broker orders. */
data class ForecastUnivestCampaign(
    val key: String,
    val symbol: String,
    val entryNotificationAtMs: Long,
    val entryReceivedAtMs: Long,
    val durationMonths: Double,
    val entryPrice: Double? = null,
    val entryQuoteAtMs: Long? = null,
    val entryPriceSource: String = "UNKNOWN",
    val features48Json: String? = null,
    val featureCaptureAtMs: Long? = null,
    val observedFactorCount: Int = 0,
    val exitNotificationAtMs: Long? = null,
    val exitReceivedAtMs: Long? = null,
    val exitPrice: Double? = null,
    val exitPriceSource: String = "UNKNOWN",
    val outcomePct: Double? = null,
    val status: String = "ENTRY_RECEIVED"
)

@Singleton
class ForecastUnivestJournal @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "forecast_univest_same_day.jsonl")
    private val gson = GsonBuilder().serializeNulls().create()
    private val lock = Any()
    private val latest = linkedMapOf<String, ForecastUnivestCampaign>()

    init { if (file.isFile) file.forEachLine { line ->
        runCatching { gson.fromJson(line, ForecastUnivestCampaign::class.java) }.getOrNull()
            ?.takeIf { it.key.isNotBlank() }?.let { latest[it.key] = it }
    } }
    fun enter(symbol: String, at: Long, received: Long, months: Double): ForecastUnivestCampaign? = synchronized(lock) {
        val day = ForecastUnivestLearningPolicy.date(at)
        val key = "$day|$symbol"
        if (latest.containsKey(key)) return@synchronized null // no ambiguous re-entry pairing
        val row = ForecastUnivestCampaign(key, symbol, at, received, months)
        persist(row)
        row
    }
    fun get(key: String): ForecastUnivestCampaign? = synchronized(lock) { latest[key] }
    fun quoteEntry(key: String, price: Double, at: Long): ForecastUnivestCampaign? = synchronized(lock) {
        val old = latest[key] ?: return@synchronized null
        if (old.entryPrice != null || old.exitNotificationAtMs != null || price <= 0.0 || !price.isFinite() ||
            at < old.entryNotificationAtMs || at - old.entryNotificationAtMs > 180_000L) return@synchronized null
        val row = old.copy(entryPrice = price, entryQuoteAtMs = at, entryPriceSource = "GROWW_BETA_LIVE_QUOTE", status = "ENTRY_PRICE_OBSERVED")
        persist(row); row
    }
    fun snapshot(key: String, json: String, at: Long, observed: Int): ForecastUnivestCampaign? = synchronized(lock) {
        val old = latest[key] ?: return@synchronized null
        if (old.entryPrice == null || old.features48Json != null || old.exitNotificationAtMs != null ||
            at < old.entryNotificationAtMs || at - old.entryNotificationAtMs > 180_000L) return@synchronized null
        val row = old.copy(features48Json = json, featureCaptureAtMs = at, observedFactorCount = observed,
            status = "ENTRY_FEATURES_CAPTURED")
        persist(row); row
    }
    /** Persist closure before slow network I/O. Unmatched/multi-day exits remain diagnostics only. */
    fun claimExit(symbol: String, at: Long, received: Long): ForecastUnivestCampaign? = synchronized(lock) {
        val key = "${ForecastUnivestLearningPolicy.date(at)}|$symbol"
        val old = latest[key] ?: return@synchronized null
        if (old.exitNotificationAtMs != null || !ForecastUnivestLearningPolicy.sameTradingDay(old.entryNotificationAtMs, at)) return@synchronized null
        val row = old.copy(exitNotificationAtMs = at, exitReceivedAtMs = received, status = "SAME_DAY_EXIT_RECEIVED")
        persist(row); row
    }
    /** Exits can be completed using a bounded historical candle after a live-quote outage.
     * The price source remains explicit: a candle proxy is not an executed trade.
     */
    fun quoteExit(key: String, price: Double, at: Long, source: String = "GROWW_BETA_LIVE_QUOTE"): ForecastUnivestCampaign? = synchronized(lock) {
        val old = latest[key] ?: return@synchronized null
        val exitTime = old.exitNotificationAtMs ?: return@synchronized null
        if (old.exitPrice != null || price <= 0.0 || !price.isFinite() || at < exitTime || at - exitTime > 180_000L) return@synchronized null
        val entry = old.entryPrice
        val eligible = entry != null && entry > 0 && old.features48Json != null && old.featureCaptureAtMs != null &&
            old.featureCaptureAtMs < exitTime && ForecastUnivestLearningPolicy.sameTradingDay(old.entryNotificationAtMs, exitTime)
        val pct = if (eligible) (price / entry!! - 1.0) * 100.0 else null
        val row = old.copy(exitPrice = price, exitPriceSource = source, outcomePct = pct,
            status = if (pct == null || !pct.isFinite()) "CLOSED_INSUFFICIENT_ENTRY_EVIDENCE" else if (source.contains("CANDLE_PROXY")) "CLOSED_SAME_DAY_MARKET_ESTIMATE" else "CLOSED_SAME_DAY_VERIFIED")
        persist(row); row
    }
    fun pendingExitPricing(): List<ForecastUnivestCampaign> = synchronized(lock) {
        latest.values.filter { it.exitNotificationAtMs != null && it.exitPrice == null &&
            it.status == "SAME_DAY_EXIT_RECEIVED" && it.entryPrice != null }.take(6)
    }
    fun training(beforeDate: String): List<ForecastUnivestCampaign> = synchronized(lock) {
        latest.values.filter { row ->
            row.status == "CLOSED_SAME_DAY_VERIFIED" && row.outcomePct?.isFinite() == true &&
                ForecastUnivestLearningPolicy.date(row.entryNotificationAtMs) < beforeDate
        }.toList()
    }
    fun recent(limit: Int = 15): List<ForecastUnivestCampaign> = synchronized(lock) {
        latest.values.sortedByDescending { it.exitReceivedAtMs ?: it.entryReceivedAtMs }.take(limit)
    }
    fun summary(): Map<String, Any> = synchronized(lock) {
        mapOf("received_entries" to latest.size,
            "completed_same_day" to latest.values.count { it.status == "CLOSED_SAME_DAY_VERIFIED" },
            "awaiting_evidence" to latest.values.count { it.status != "CLOSED_SAME_DAY_VERIFIED" },
            "positive_closures" to latest.values.count { it.status == "CLOSED_SAME_DAY_VERIFIED" && (it.outcomePct ?: 0.0) > 0.0 },
            "market_estimates_not_in_training" to latest.values.count { it.status == "CLOSED_SAME_DAY_MARKET_ESTIMATE" },
            "pending_exit_prices" to latest.values.count { it.status == "SAME_DAY_EXIT_RECEIVED" })
    }
    fun archive(): String = synchronized(lock) { if (file.exists()) file.readText() else "" }
    fun restore(jsonl: String): Int = synchronized(lock) {
        var count = 0
        jsonl.lineSequence().forEach { line ->
            if (line.length > 200_000 || line.isBlank()) return@forEach
            val row = runCatching { gson.fromJson(line, ForecastUnivestCampaign::class.java) }.getOrNull() ?: return@forEach
            if (!row.symbol.matches(Regex("[A-Z][A-Z0-9&.\\-]{1,29}")) ||
                row.key != "${ForecastUnivestLearningPolicy.date(row.entryNotificationAtMs)}|${row.symbol}") return@forEach
            val old = latest[row.key]
            if (old == null || (row.exitReceivedAtMs ?: row.featureCaptureAtMs ?: row.entryReceivedAtMs) >
                (old.exitReceivedAtMs ?: old.featureCaptureAtMs ?: old.entryReceivedAtMs)) {
                persist(row)
                count++
            }
        }
        count
    }
    private fun persist(row: ForecastUnivestCampaign) {
        file.appendText(gson.toJson(row) + "\n")
        latest[row.key] = row
    }
}
