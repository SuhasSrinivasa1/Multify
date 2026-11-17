package com.multify.traderpro.engine

/**
 * Analysis-only snapshot of indicator history sufficiency.
 * Never affects the frozen v8.3.19 scoring or live filtering; selection
 * modification requires separately validated out-of-sample evidence.
 */
object FixedIndicatorWarmupDiagnosticPolicy {
    data class Status(
        val completeBars: Int,
        val ema9Warm: Boolean,
        val ema20Warm: Boolean,
        val ema50Warm: Boolean,
        val atr14Warm: Boolean,
        val rsi14Warm: Boolean,
        val bollinger20Warm: Boolean,
        val macdSignalWarm: Boolean,
        val orb5IndependentlyObservable: Boolean
    ) {
        val missing: List<String> get() = buildList {
            if (!ema9Warm) add("EMA9")
            if (!ema20Warm) add("EMA20")
            if (!ema50Warm) add("EMA50")
            if (!atr14Warm) add("ATR14")
            if (!rsi14Warm) add("RSI14")
            if (!bollinger20Warm) add("BOLL20")
            if (!macdSignalWarm) add("MACD_12_26_9")
            if (!orb5IndependentlyObservable) add("ORB5_FROM_15M_UNAVAILABLE")
        }
    }
    fun assess(bars: Int, intervalMinutes: Int = 15): Status =
        Status(
            bars, bars>=9, bars>=20, bars>=50, bars>=15, bars>=15,
            bars>=20, bars>=34, intervalMinutes<=5
        )
}
