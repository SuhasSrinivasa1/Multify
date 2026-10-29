package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.multify.traderpro.engine.FixedOutcomeMetrics
import com.multify.traderpro.engine.FixedOutcomeSample
import com.multify.traderpro.engine.FixedOutcomeSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Separate, reset-on-introduction Fixed analytics ledger. Never changes frozen recommendation inputs. */
@Singleton
class FixedOutcomePerformanceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fixed_outcome_averages_fresh_v787", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val listType = object : TypeToken<List<FixedOutcomeSample>>() {}.type
    private val ist = ZoneId.of("Asia/Kolkata")

    /** On the new release's first launch, start from zero; never import old, unmonitored v7.8.5 calls. */
    @Synchronized
    fun startedAtMs(): Long {
        val old = prefs.getLong("fresh_start_at_ms", 0L)
        if (old > 0) return old
        val start = System.currentTimeMillis()
        prefs.edit().putLong("fresh_start_at_ms", start).commit()
        return start
    }

    @Synchronized
    internal fun record(rows: List<FixedRecommendationRecord>) {
        val started = startedAtMs()
        val existing = load().associateBy { "${it.date}|${it.symbol.uppercase()}" }.toMutableMap()
        rows.forEach { row ->
            if (row.generatedAtMs < started || !row.entryPrice.isFinite() || row.entryPrice <= 0.0) return@forEach
            val key = "${row.recommendationDate}|${row.symbol.uppercase()}"
            val previous = existing[key]
            val latestMfe = maxOf(previous?.maxFavourablePct ?: 0.0, row.maxFavourablePct.coerceAtLeast(0.0))
            val observed = previous?.observed == true || row.lastObservedAtMs > row.generatedAtMs
            existing[key] = FixedOutcomeSample(
                date = row.recommendationDate,
                symbol = row.symbol,
                publishedAtMs = row.generatedAtMs,
                targetPct = row.targetPct,
                maxFavourablePct = latestMfe,
                observed = observed,
                fullTargetHit = previous?.fullTargetHit == true || row.status == "TARGET_HIT" ||
                    (observed && latestMfe + 1e-9 >= row.targetPct)
            )
        }
        val days = existing.values.map { it.date }.distinct().sortedDescending().take(30).toSet()
        prefs.edit().putString("samples", gson.toJson(existing.values.filter { it.date in days })).commit()
    }

    @Synchronized
    fun rolling(): FixedOutcomeSummary {
        startedAtMs()
        return FixedOutcomeMetrics.calculate(load())
    }

    @Synchronized
    fun today(): FixedOutcomeSummary {
        startedAtMs()
        val today = LocalDate.now(ist).toString()
        return FixedOutcomeMetrics.calculate(load().filter { it.date == today })
    }

    @Synchronized
    private fun load(): List<FixedOutcomeSample> = runCatching {
        val raw = prefs.getString("samples", null) ?: return@runCatching emptyList()
        gson.fromJson<List<FixedOutcomeSample>>(raw, listType) ?: emptyList()
    }.getOrDefault(emptyList())
}
