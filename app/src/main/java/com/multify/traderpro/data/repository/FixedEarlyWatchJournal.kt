package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.network.FixedEarlyWatchDto
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.engine.FixedMarketQualityShadowPolicy
import com.multify.traderpro.engine.FixedEarlyWatchPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Research-only first-PRIMARY price record. Reads the immutable Fixed observations
 * AFTER the complete scan and order path. It cannot trigger an order, accelerate
 * the official recommendation, or change the frozen confirmation policy.
 */
internal data class FixedEarlyWatchRecord(
    val date: String,
    val symbol: String,
    val firstPrimaryScanAtMs: Long,
    val firstEvaluatedAtMs: Long,
    val firstPrimaryPrice: Double,
    val lastPrimaryScanAtMs: Long,
    val lastEvaluatedAtMs: Long,
    val lastPrice: Double,
    val independentPrimaryScans: Int,
    val publishedScanAtMs: Long? = null,
    val publishedEntryPrice: Double? = null,
    val publishedStatus: String? = null,
    val observedSpreadBps: Double? = null,
    val quoteBookStatus: String = "UNAVAILABLE",
    val dayTradedShares: Long? = null,
    val brokerQuoteHighDistancePct: Double? = null,
    val warnings: List<String> = emptyList()
) {
    fun toDto(): FixedEarlyWatchDto = FixedEarlyWatchDto(
        symbol = symbol,
        firstPrimaryScanAtMs = firstPrimaryScanAtMs,
        firstEvaluatedAtMs = firstEvaluatedAtMs,
        firstPrimaryPrice = firstPrimaryPrice,
        latestEvaluatedAtMs = lastEvaluatedAtMs,
        latestPrice = lastPrice,
        confirmationObservations = independentPrimaryScans,
        officialPublished = publishedScanAtMs != null,
        officialEntryPrice = publishedEntryPrice,
        officialStatus = publishedStatus,
        runupPct = FixedEarlyWatchPolicy.advanceFromFirstPrimary(
            firstPrimaryPrice, publishedEntryPrice ?: lastPrice) ?: 0.0,
        observedSpreadBps = observedSpreadBps,
        quoteBookStatus = quoteBookStatus,
        dayTradedShares = dayTradedShares,
        distanceFromQuoteDayHighPct = brokerQuoteHighDistancePct,
        warnings = warnings
    )
}

@Singleton
class FixedEarlyWatchJournal @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fixed_early_watch_read_only_v80", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().create()

    @Synchronized
    internal fun observe(
        date: String, observation: FixedScanObservation,
        evaluationReceivedAtMs: Long, quote: QuotePayload,
        official: FixedRecommendationRecord?
    ) {
        val price = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: return
        if (observation.qualityTier != "PRIMARY" || !observation.publishEligible ||
            !observation.executionEligible || observation.recommendationDate != date ||
            evaluationReceivedAtMs < observation.scanAtMs) return
        val rows = load().toMutableList()
        val index = rows.indexOfFirst { it.date == date && it.symbol.equals(observation.symbol, true) }
        val prior = rows.getOrNull(index)
        // One evidence row per symbol/scan, even if service restart causes a replay.
        val newScan = FixedEarlyWatchPolicy.independentObservation(
            prior?.lastPrimaryScanAtMs, observation.scanAtMs)
        val shadow = FixedMarketQualityShadowPolicy.assess(quote, prior?.firstPrimaryPrice ?: price)
        val row = FixedEarlyWatchRecord(
            date = date, symbol = observation.symbol.uppercase(),
            firstPrimaryScanAtMs = prior?.firstPrimaryScanAtMs ?: observation.scanAtMs,
            firstEvaluatedAtMs = prior?.firstEvaluatedAtMs ?: evaluationReceivedAtMs,
            firstPrimaryPrice = prior?.firstPrimaryPrice ?: price,
            lastPrimaryScanAtMs = observation.scanAtMs,
            lastEvaluatedAtMs = evaluationReceivedAtMs,
            lastPrice = price,
            independentPrimaryScans = (prior?.independentPrimaryScans ?: 0) +
                if (newScan) 1 else 0,
            publishedScanAtMs = official?.generatedAtMs ?: prior?.publishedScanAtMs,
            publishedEntryPrice = official?.entryPrice ?: prior?.publishedEntryPrice,
            publishedStatus = official?.status ?: prior?.publishedStatus,
            observedSpreadBps = shadow.observedSpreadBps,
            quoteBookStatus = shadow.bookStatus,
            dayTradedShares = shadow.dayTradedShares,
            brokerQuoteHighDistancePct = shadow.distanceFromQuoteDayHighPct,
            warnings = shadow.warnings
        )
        if (index >= 0) rows[index] = row else rows += row
        save(rows)
    }

    @Synchronized
    internal fun reconcileOfficial(date: String, official: List<FixedRecommendationRecord>) {
        val lookup = official.associateBy { it.symbol.uppercase() }
        val rows = load()
        if (rows.none { it.date == date && lookup.containsKey(it.symbol) }) return
        save(rows.map { old ->
            val matched = if (old.date == date) lookup[old.symbol] else null
            if (matched == null) old else old.copy(
                publishedScanAtMs = matched.generatedAtMs,
                publishedEntryPrice = matched.entryPrice,
                publishedStatus = matched.status)
        })
    }

    @Synchronized
    fun recent(date: String, limit: Int = 12): List<FixedEarlyWatchDto> = load()
        .filter { it.date == date }
        .sortedByDescending { it.firstPrimaryScanAtMs }
        .take(limit)
        .map { it.toDto() }

    @Synchronized
    fun archive(): String = gson.toJson(load())

    @Synchronized
    fun restoreArchived(raw: String): Int {
        if (raw.length > 2_000_000) return 0
        val incoming = runCatching {
            gson.fromJson(raw, Array<FixedEarlyWatchRecord>::class.java).toList()
        }.getOrNull().orEmpty()
        val existing = load().associateBy { "${it.date}|${it.symbol}" }.toMutableMap()
        var additions = 0
        for (item in incoming.take(1_000)) {
            if (!item.firstPrimaryPrice.isFinite() || item.firstPrimaryPrice <= 0.0 ||
                !item.lastPrice.isFinite() || item.lastPrice <= 0.0 ||
                !item.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ||
                !item.symbol.matches(Regex("[A-Z0-9&_.-]{1,40}"))) continue
            val key = "${item.date}|${item.symbol}"
            if (key !in existing) { existing[key] = item; additions++ }
        }
        if (additions > 0) save(existing.values.toList())
        return additions
    }

    private fun load(): List<FixedEarlyWatchRecord> =
        prefs.getString("records", null)?.let { json ->
            runCatching { gson.fromJson(json, Array<FixedEarlyWatchRecord>::class.java).toList() }
                .getOrNull()
        }.orEmpty()

    private fun save(rows: List<FixedEarlyWatchRecord>) {
        // Keep up to ten observed dates, with a hard cap to protect phone storage.
        val dates = rows.map { it.date }.distinct().sortedDescending().take(10).toSet()
        val retained = rows.filter { it.date in dates }
            .sortedWith(compareByDescending<FixedEarlyWatchRecord> { it.date }
                .thenByDescending { it.firstPrimaryScanAtMs })
            .take(1_000)
        prefs.edit().putString("records", gson.toJson(retained)).commit()
    }
}
