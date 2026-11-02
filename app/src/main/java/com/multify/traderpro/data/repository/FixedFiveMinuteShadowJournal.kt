package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import java.time.LocalDate

/** Local research ledger. Never exposed to execution, Fixed ranking or Forecast learning. */
internal data class FixedFiveMinuteShadowPair(
    val date: String,
    val atMs: Long,
    val symbol: String,
    val entryPrice: Double,
    val targetPct: Double,
    val fifteenMinuteTier: String,
    val fiveMinuteTier: String,
    val fifteenMinuteConfidence: Double,
    val fiveMinuteConfidence: Double,
    val fifteenMinuteDirection: Double,
    val fiveMinuteDirection: Double,
    val fifteenMinuteStrategy: String,
    val fiveMinuteStrategy: String,
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val observations: Int = 0,
    val lastObservedAtMs: Long = 0L
) {
    val fifteenMinutePrimary get() = fifteenMinuteTier == "PRIMARY"
    val fiveMinutePrimary get() = fiveMinuteTier == "PRIMARY"
    val targetObserved get() = maxFavourablePct >= targetPct
}

/**
 * At each later Fixed full-market LTP sweep we update both counterfactuals against the same
 * real price path. Intrabar extremes are NOT reconstructed: these are sampled LTP outcomes.
 */
internal class FixedFiveMinuteShadowJournal(context: Context) {
    private val prefs = context.getSharedPreferences("fixed_five_minute_shadow_v1", Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized fun all(): List<FixedFiveMinuteShadowPair> =
        runCatching {
            gson.fromJson(prefs.getString("pairs", "[]"), Array<FixedFiveMinuteShadowPair>::class.java)
                ?.toList().orEmpty()
        }.getOrDefault(emptyList())

    @Synchronized fun append(pair: FixedFiveMinuteShadowPair) {
        val rows = all().filterNot { it.date == pair.date && it.symbol == pair.symbol && it.atMs == pair.atMs }
        save(rows + pair)
    }

    @Synchronized fun updateObservedPrices(date: String, prices: Map<String, Double>, nowMs: Long): List<FixedFiveMinuteShadowPair> {
        var dirty = false
        val updates = mutableListOf<FixedFiveMinuteShadowPair>()
        val result = all().map { row ->
            val price = prices[row.symbol]
            if (row.date != date || price == null || !price.isFinite() || price <= 0.0 ||
                nowMs <= row.atMs || row.lastObservedAtMs >= nowMs
            ) row else {
                val pct = (price / row.entryPrice - 1.0) * 100.0
                val revised = row.copy(
                    maxFavourablePct = maxOf(row.maxFavourablePct, pct),
                    maxAdversePct = minOf(row.maxAdversePct, pct),
                    observations = row.observations + 1,
                    lastObservedAtMs = nowMs
                )
                dirty = true
                updates.add(revised)
                revised
            }
        }
        if (dirty) save(result)
        return updates
    }

    private fun save(rows: List<FixedFiveMinuteShadowPair>) {
        val dates = rows.map { it.date }.distinct().sortedDescending().take(5).toSet()
        prefs.edit().putString("pairs", gson.toJson(rows.filter { it.date in dates }.takeLast(500))).commit()
    }
}
