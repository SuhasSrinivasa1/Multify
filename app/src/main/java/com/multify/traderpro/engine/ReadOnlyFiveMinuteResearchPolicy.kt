package com.multify.traderpro.engine

/** Local five-minute intelligence from existing one-minute candles; no network or BUY path. */
object ReadOnlyFiveMinuteResearchPolicy {
    data class Result(
        val completeBars: Int, val lastFiveMinuteClose: Double?,
        val ema9: Double?, val ema20: Double?, val vwap: Double?,
        val relativeVolume: Double?, val bullishCandle: Boolean?,
        val aboveVwap: Boolean?, val emaBullish: Boolean?,
        val threeBarBreakout: Boolean?, val researchOnly: Boolean = true
    )

    fun evaluate(candles: List<Candle>, asOfMs: Long): Result {
        val bars = FiveMinuteCandleAggregator.aggregate(
            candles.map { MarketCandleBar(it.ts * 1_000L, it.open, it.high, it.low, it.close, it.volume) },
            asOfMs
        )
        val last = bars.lastOrNull()
        fun ema(period: Int): Double? {
            if (bars.size < period) return null
            val k = 2.0 / (period + 1)
            return bars.map { it.close }.drop(1).fold(bars.first().close) { acc, price ->
                price * k + acc * (1.0 - k)
            }
        }
        val e9 = ema(9); val e20 = ema(20)
        val totalVolume = bars.sumOf { it.volume }
        val vwap = if (totalVolume > 0.0)
            bars.sumOf { ((it.high + it.low + it.close) / 3.0) * it.volume } / totalVolume else null
        val previousVolumes = bars.dropLast(1).takeLast(20)
        val rvol = if (last != null && previousVolumes.isNotEmpty() && previousVolumes.averageOfVolume() > 0.0)
            last.volume / previousVolumes.averageOfVolume() else null
        val previousThree = bars.dropLast(1).takeLast(3)
        return Result(
            bars.size, last?.close, e9, e20, vwap, rvol,
            last?.let { it.close > it.open },
            if (last != null && vwap != null) last.close > vwap else null,
            if (e9 != null && e20 != null) e9 > e20 else null,
            if (last != null && previousThree.size == 3) last.close > previousThree.maxOf { it.high } else null
        )
    }

    private fun List<MarketCandleBar>.averageOfVolume(): Double = sumOf { it.volume } / size
}
