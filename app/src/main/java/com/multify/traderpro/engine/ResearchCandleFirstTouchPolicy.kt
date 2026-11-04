package com.multify.traderpro.engine

/** One-minute historical candle study: source candles are not broker fills.
 * Excludes the candle overlapping the signal. When stop and target share a bar,
 * the intrabar sequence is unknowable and the result is AMBIGUOUS_SAME_CANDLE.
 */
object ResearchCandleFirstTouchPolicy {
    data class Result(
        val firstTouch: String, val firstTouchAtMs: Long?,
        val eligibleBars: Int, val completeSequence: Boolean,
        val hypotheticalStopPct: Double = -2.0,
        val evidence: String = "HISTORICAL_1M_CANDLE_SIMULATION_NOT_EXECUTED_PNL"
    )

    fun evaluate(
        entryAtMs: Long, entryPrice: Double, targetPrice: Double,
        bars: List<ForecastReplayCandleDecoder.Bar>, sessionEndMs: Long
    ): Result {
        if (entryAtMs <= 0L || entryPrice <= 0.0 || !entryPrice.isFinite() ||
            !targetPrice.isFinite() || targetPrice <= entryPrice || sessionEndMs <= entryAtMs)
            return Result("INCOMPLETE", null, 0, false)
        val valid = bars.asSequence()
            .filter { it.atMs > entryAtMs && it.atMs < sessionEndMs &&
                it.low > 0.0 && it.high >= it.low && it.high.isFinite() &&
                it.low.isFinite() && it.close.isFinite() }
            .distinctBy { it.atMs }.sortedBy { it.atMs }.toList()
        if (valid.isEmpty()) return Result("INCOMPLETE", null, 0, false)
        val stop = entryPrice * 0.98
        var previous: ForecastReplayCandleDecoder.Bar? = null
        var missingInterval = false
        for (bar in valid) {
            if (previous != null && bar.atMs - previous.atMs > 90_000L) missingInterval = true
            val target = bar.high >= targetPrice
            val stopped = bar.low <= stop
            if (target || stopped) {
                val reason = when {
                    missingInterval -> "AMBIGUOUS_MISSING_BARS"
                    target && stopped -> "AMBIGUOUS_SAME_CANDLE"
                    target -> "TARGET_FIRST_1M"
                    else -> "STOP_FIRST_1M"
                }
                return Result(reason, bar.atMs, valid.size, !missingInterval)
            }
            previous = bar
        }
        return Result(if (missingInterval) "INCOMPLETE_GAPPED_BARS" else "NO_TOUCH_OBSERVED",
            null, valid.size, !missingInterval)
    }
}
