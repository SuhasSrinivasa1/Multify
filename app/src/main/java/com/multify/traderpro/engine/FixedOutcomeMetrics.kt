package com.multify.traderpro.engine

/** Fixed publication outcomes only. No input from the adaptive Forecast engine or Multify trades. */
data class FixedOutcomeSample(
    val date: String,
    val symbol: String,
    val publishedAtMs: Long,
    val targetPct: Double,
    val maxFavourablePct: Double,
    val observed: Boolean,
    val fullTargetHit: Boolean
)

data class FixedOutcomeSummary(
    val published: Int = 0,
    val observed: Int = 0,
    val meanTargetPct: Double? = null,
    val meanObservedMfePct: Double? = null,
    val armHits: Int = 0,
    val onePctHits: Int = 0,
    val fullTargetHits: Int = 0,
    val armHitRatePct: Double? = null,
    val onePctHitRatePct: Double? = null,
    val fullTargetHitRatePct: Double? = null,
    val armHitMeanMfePct: Double? = null,
    val onePctHitMeanMfePct: Double? = null,
    val fullHitMeanMfePct: Double? = null,
    val p25Pct: Double? = null,
    val medianPct: Double? = null,
    val p75Pct: Double? = null
)

object FixedOutcomeMetrics {
    fun calculate(samples: List<FixedOutcomeSample>): FixedOutcomeSummary {
        val unique = samples.distinctBy { "${it.date}|${it.symbol.uppercase()}" }
        val eligible = unique.filter { it.observed && it.maxFavourablePct.isFinite() && it.maxFavourablePct >= 0.0 }
        fun hitCount(condition: (FixedOutcomeSample) -> Boolean) = eligible.count(condition)
        fun hitMean(condition: (FixedOutcomeSample) -> Boolean): Double? = eligible.filter(condition)
            .map { it.maxFavourablePct }.takeIf { it.isNotEmpty() }?.average()
        fun rate(count: Int): Double? = if (eligible.isEmpty()) null else count.toDouble() / eligible.size * 100.0
        val arm = hitCount { it.maxFavourablePct >= 0.55 - 1e-9 }
        val one = hitCount { it.maxFavourablePct >= 1.00 - 1e-9 }
        val full = hitCount { it.fullTargetHit || (it.targetPct > 0 && it.maxFavourablePct >= it.targetPct - 1e-9) }
        val sorted = eligible.map { it.maxFavourablePct }.sorted()
        fun percentile(p: Double): Double? {
            if (sorted.isEmpty()) return null
            val pos = (sorted.size - 1) * p
            val lower = pos.toInt()
            val upper = kotlin.math.ceil(pos).toInt()
            return sorted[lower] + (sorted[upper] - sorted[lower]) * (pos - lower)
        }
        return FixedOutcomeSummary(
            published = unique.size,
            observed = eligible.size,
            meanTargetPct = unique.map { it.targetPct }.filter { it.isFinite() && it > 0.0 }.takeIf { it.isNotEmpty() }?.average(),
            meanObservedMfePct = sorted.takeIf { it.isNotEmpty() }?.average(),
            armHits = arm, onePctHits = one, fullTargetHits = full,
            armHitRatePct = rate(arm), onePctHitRatePct = rate(one), fullTargetHitRatePct = rate(full),
            armHitMeanMfePct = hitMean { it.maxFavourablePct >= 0.55 - 1e-9 },
            onePctHitMeanMfePct = hitMean { it.maxFavourablePct >= 1.00 - 1e-9 },
            fullHitMeanMfePct = hitMean { it.fullTargetHit || (it.targetPct > 0 && it.maxFavourablePct >= it.targetPct - 1e-9) },
            p25Pct = percentile(.25), medianPct = percentile(.50), p75Pct = percentile(.75)
        )
    }
}
