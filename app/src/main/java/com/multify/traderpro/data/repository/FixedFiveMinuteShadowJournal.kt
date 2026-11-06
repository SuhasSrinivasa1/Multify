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
    val tenMinuteTier: String? = null,
    val tenMinuteConfidence: Double? = null,
    val tenMinuteDirection: Double? = null,
    val tenMinuteStrategy: String? = null,
    val fiveMinuteCompletedBars: Int = 0,
    val tenMinuteCompletedBars: Int = 0,
    val fiveMinuteLastBarVolume: Double? = null,
    val tenMinuteLastBarVolume: Double? = null,
    val providerLastTradeTimeMs: Long? = null,
    val providerLastTradeAgeMs: Long? = null,
    val brokerReportedDayVolume: Long? = null,
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val observations: Int = 0,
    val lastObservedAtMs: Long = 0L,
    val firstTargetObservedAtMs: Long? = null,
    val firstTwoPctRiskObservedAtMs: Long? = null
) {
    // Both variants see the same entry quote and subsequently sampled LTP path;
    // only their hypothetical qualification differs. No fills/orders are claimed.
    val sessionBucket: String get() {
        val time = java.time.Instant.ofEpochMilli(atMs)
            .atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalTime()
        return if (time < java.time.LocalTime.of(10, 15)) "OPEN_0915_1015"
               else if (time < java.time.LocalTime.of(13, 0)) "MID_SESSION"
               else "LATE_SESSION"
    }
    val comparisonGroup: String get() = when {
        fifteenMinutePrimary && fiveMinutePrimary -> "BOTH_PRIMARY"
        fifteenMinutePrimary -> "ONLY_15M_PRIMARY"
        fiveMinutePrimary -> "ONLY_5M_PRIMARY"
        else -> "NEITHER_PRIMARY"
    }
    val sampledTargetBeforeRisk: Boolean get() =
        firstTargetObservedAtMs != null &&
            (firstTwoPctRiskObservedAtMs == null ||
                firstTargetObservedAtMs < firstTwoPctRiskObservedAtMs)
    val fifteenMinutePrimary get() = fifteenMinuteTier == "PRIMARY"
    val fiveMinutePrimary get() = fiveMinuteTier == "PRIMARY"
    val tenMinutePrimary get() = tenMinuteTier == "PRIMARY"
    val triadComparisonGroup: String get() {
        val primaryCount = listOf(fifteenMinutePrimary, fiveMinutePrimary, tenMinutePrimary).count { it }
        return when (primaryCount) {
            3 -> "ALL_THREE_PRIMARY"
            2 -> "TWO_PRIMARY"
            1 -> "ONE_PRIMARY"
            else -> "NONE_PRIMARY"
        }
    }
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
                    lastObservedAtMs = nowMs,
                    firstTargetObservedAtMs = row.firstTargetObservedAtMs
                        ?: if (pct >= row.targetPct) nowMs else null,
                    firstTwoPctRiskObservedAtMs = row.firstTwoPctRiskObservedAtMs
                        ?: if (pct <= -2.0) nowMs else null
                )
                dirty = true
                updates.add(revised)
                revised
            }
        }
        if (dirty) save(result)
        return updates
    }

    data class CohortStats(
        val sessionBucket: String, val comparisonGroup: String, val observations: Int,
        val targetObserved: Int, val targetBeforeTwoPctRisk: Int, val noForwardObservations: Int
    )
    @Synchronized fun cohortSummary(): List<CohortStats> = all()
        .groupBy { it.sessionBucket to it.comparisonGroup }
        .map { (key, rows) -> CohortStats(
            key.first, key.second, rows.size,
            rows.count { it.targetObserved && it.observations > 0 },
            rows.count { it.sampledTargetBeforeRisk && it.observations > 0 },
            rows.count { it.observations == 0 }
        ) }.sortedWith(compareBy({ it.sessionBucket }, { it.comparisonGroup }))

    private fun save(rows: List<FixedFiveMinuteShadowPair>) {
        val dates = rows.map { it.date }.distinct().sortedDescending().take(5).toSet()
        prefs.edit().putString("pairs", gson.toJson(rows.filter { it.date in dates }.takeLast(500))).commit()
    }
}
