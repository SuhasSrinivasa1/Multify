package com.multify.traderpro.engine

/** Strategy-owned market-data snapshot. It contains no Fixed score, Univest signal or Multify learning prior. */
internal data class WorkingRangeCandle(
    val atMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

internal data class WorkingRangeMarketSnapshot(
    val price: Double,
    val atr: Double?,
    val rsi: Double?,
    val rsiSlope: Double?,
    val rvol: Double?,
    val volumeAcceleration: Double?,
    val greenVolumeShare: Double?,
    val ema9: Double?,
    val ema20: Double?,
    val ema50: Double?,
    val ema9Slope: Double?,
    val ema20Slope: Double?,
    val macdHistogramSlope: Double?,
    val vwap: Double?,
    val range52Position: Double?,
    val spreadBps: Double?,
    val orderBookImbalance: Double?,
    val candles: List<WorkingRangeCandle>
)
