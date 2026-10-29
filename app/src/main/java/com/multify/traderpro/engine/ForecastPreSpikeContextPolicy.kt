package com.multify.traderpro.engine

import kotlin.math.max

/**
 * Forecast-only contextual features for 5-minute EARLY ENTRY candle ranking.
 * These features never create eligibility on their own and never modify Fixed.
 * Use completed preceding bars; do not mistake an already expanded five-minute
 * breakout candle for evidence of pre-spike compression.
 */
object ForecastPreSpikeContextPolicy {
    data class Evidence(
        val compressionRatio: Double?,
        val risingFloor: Boolean,
        val orderlyVolumeBuild: Boolean,
        val rankingBonus: Double
    )

    fun evaluate(candles: List<Candle>): Evidence {
        if (candles.size < 11) return Evidence(null, false, false, 0.0)
        val complete = candles.dropLast(1) // live/incomplete bar cannot invent advance notice
        val recent = complete.takeLast(3)
        val older = complete.dropLast(3).takeLast(6)
        fun range(bars: List<Candle>): Double =
            (bars.maxOf { it.high } - bars.minOf { it.low }) /
                max(0.01, bars.map { it.close }.average()) * 100.0
        if (recent.any { it.close <= 0.0 || it.low <= 0.0 || it.volume < 0.0 } ||
            older.any { it.close <= 0.0 || it.low <= 0.0 || it.volume < 0.0 }
        ) return Evidence(null, false, false, 0.0)
        val previousRange = range(older)
        val compression = if (previousRange > 0.01) range(recent) / previousRange else null
        // A compressed final 15-minute range following a wider 30-minute range
        val coil = compression != null && compression in 0.10..0.85
        val olderLow = older.takeLast(3).minOf { it.low }
        val recentLow = recent.minOf { it.low }
        val rising = recentLow > olderLow * 1.0003
        val olderVolume = older.map { it.volume }.average()
        val recentVolume = recent.map { it.volume }.average()
        val volumeRatio = if (olderVolume > 0.0) recentVolume / olderVolume else 0.0
        // Avoid treating a massive post-breakout flush as an orderly early build.
        val build = volumeRatio in 1.05..2.5
        return Evidence(compression, rising, build,
            ((if (coil) 0.006 else 0.0) +
             (if (rising) 0.005 else 0.0) +
             (if (build) 0.005 else 0.0)).coerceAtMost(0.016)
        )
    }
}
