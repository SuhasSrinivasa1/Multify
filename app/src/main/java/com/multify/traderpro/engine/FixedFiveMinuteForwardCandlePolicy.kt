package com.multify.traderpro.engine

/**
 * Conservative, after-the-fact 5m OHLC observation against the SAME entry
 * quote used to evaluate the 15m and 5m candidate. Not executed trades or P&L.
 * All bars used for features must be cutoff at referenceAtMs separately.
 */
internal object FixedFiveMinuteForwardCandlePolicy {
    const val BAR_DURATION_MS = 300_000L
    data class Bar(val startAtMs: Long, val high: Double, val low: Double)
    data class Outcome(
        val matured: Boolean,
        val completedForwardBars: Int,
        val targetTouched: Boolean?,
        val twoPctAdverseTouched: Boolean?,
        val targetBeforeTwoPctAdverse: Boolean?,
        val sameCandleOrderingAmbiguous: Boolean,
        val maxFavourablePct: Double?,
        val maxAdversePct: Double?
    )

    fun evaluate(
        referenceAtMs: Long,
        entryPrice: Double,
        targetPct: Double,
        candles: List<Bar>,
        horizonMinutes: Int
    ): Outcome {
        fun censored() = Outcome(false, 0, null, null, null, false, null, null)
        if (referenceAtMs <= 0L || entryPrice <= 0.0 || !entryPrice.isFinite() ||
            !targetPct.isFinite() || targetPct <= 0.0 || horizonMinutes !in 5..120
        ) return censored()
        val horizonEnd = referenceAtMs + horizonMinutes * 60_000L
        val future = candles.asSequence()
            // A candle already forming when the quote arrives is not future evidence.
            .filter {
                it.startAtMs >= referenceAtMs &&
                    it.startAtMs + BAR_DURATION_MS <= horizonEnd &&
                    it.high.isFinite() && it.low.isFinite() &&
                    it.high > 0.0 && it.low > 0.0 && it.high >= it.low
            }
            .distinctBy { it.startAtMs }
            .sortedBy { it.startAtMs }
            .toList()
        // Missing end-of-horizon evidence and short market sessions are censored.
        val matured = future.isNotEmpty() &&
            future.last().startAtMs + BAR_DURATION_MS >= horizonEnd - BAR_DURATION_MS
        if (!matured) return Outcome(false, future.size, null, null, null,
            false, null, null)
        val target = entryPrice * (1.0 + targetPct / 100.0)
        val risk = entryPrice * 0.98
        val targetAt = future.indexOfFirst { it.high >= target }
        val riskAt = future.indexOfFirst { it.low <= risk }
        val firstTarget = targetAt >= 0
        val firstRisk = riskAt >= 0
        val ambiguity = firstTarget && firstRisk && targetAt == riskAt
        return Outcome(
            true, future.size, firstTarget, firstRisk,
            firstTarget && (!firstRisk || targetAt < riskAt),
            ambiguity,
            (future.maxOf { it.high } / entryPrice - 1.0) * 100.0,
            (future.minOf { it.low } / entryPrice - 1.0) * 100.0
        )
    }
}
