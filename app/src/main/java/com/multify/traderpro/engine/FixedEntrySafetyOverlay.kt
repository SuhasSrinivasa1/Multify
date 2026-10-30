package com.multify.traderpro.engine

data class FixedEntrySafetyAssessment(
    val eligible: Boolean,
    val reasons: List<String>,
    val vwapExtensionAtr: Double?,
    val ema20ExtensionAtr: Double?,
    val recentImpulseAtr: Double?,
    val confirmedMultiPeakRebreakout: Boolean = false
)

/**
 * Publication-only guard. Frozen Fixed scoring, targets, five-minute inputs and SELL rules
 * are untouched. The exception below is narrowly limited to a completed, volume-confirmed
 * re-breakout after a higher-low retest; failed breakouts remain rejected.
 */
object FixedEntrySafetyOverlay {
    const val VERSION = "FIXED_ENTRY_SAFETY_V2_MULTI_PEAK"
    const val MAX_VWAP_EXTENSION_ATR = 2.25
    const val MAX_EMA20_EXTENSION_ATR = 2.0
    const val REBREAKOUT_MAX_VWAP_EXTENSION_ATR = 2.75
    const val REBREAKOUT_MAX_EMA20_EXTENSION_ATR = 2.35
    const val MAX_RECENT_IMPULSE_ATR = 1.2
    const val MAX_SPREAD_BPS = 40.0
    const val RSI_EXHAUSTION = 75.0
    const val REBREAKOUT_RSI_MAX = 80.0

    fun assess(f: FixedFeatureSnapshot): FixedEntrySafetyAssessment {
        val reasons = mutableListOf<String>()
        if (f.sessionCandleCount < 1) reasons += "current-session candles unavailable"
        val atr = f.atr14?.takeIf { it.isFinite() && it > 0.0 }
        if (atr == null) reasons += "ATR unavailable"
        val vwap = f.vwap?.takeIf { it.isFinite() && it > 0.0 }
        if (vwap == null) reasons += "current-session VWAP unavailable"
        val vwapExtension = if (atr != null && vwap != null) (f.ltp - vwap) / atr else null
        val ema20 = f.ema20?.takeIf { it.isFinite() && it > 0.0 }
        val emaExtension = if (atr != null && ema20 != null) (f.ltp - ema20) / atr else null
        val spreadSafe = f.spreadBps?.let { it.isFinite() && it in 0.0..MAX_SPREAD_BPS } == true
        val confirmedRebreakout =
            f.prevHigh != null && f.lastHigh != null && f.lastClose != null &&
                f.prevLow != null && f.lastLow != null &&
                f.lastHigh > f.prevHigh &&
                f.lastClose >= f.prevHigh * 1.0005 &&
                f.lastLow >= f.prevLow * 0.998 &&
                (f.rvol ?: 0.0) >= 1.0 &&
                spreadSafe &&
                (f.ema9Slope ?: 0.0) > 0.0 &&
                (f.vwapSlope ?: 0.0) > 0.0 &&
                (f.rsi14 ?: 100.0) < REBREAKOUT_RSI_MAX

        val maxVwapExtension = if (confirmedRebreakout) REBREAKOUT_MAX_VWAP_EXTENSION_ATR else MAX_VWAP_EXTENSION_ATR
        val maxEmaExtension = if (confirmedRebreakout) REBREAKOUT_MAX_EMA20_EXTENSION_ATR else MAX_EMA20_EXTENSION_ATR
        if (vwapExtension != null && vwapExtension > maxVwapExtension)
            reasons += "late entry: ${fmt(vwapExtension)} ATR above session VWAP"
        if (emaExtension != null && emaExtension > maxEmaExtension)
            reasons += "late entry: ${fmt(emaExtension)} ATR above EMA20"

        val rsiLimit = if (confirmedRebreakout) REBREAKOUT_RSI_MAX else RSI_EXHAUSTION
        if ((f.rsi14 ?: 0.0) >= rsiLimit &&
            ((vwapExtension ?: 0.0) >= 1.0 || (emaExtension ?: 0.0) >= 1.0)
        ) reasons += "overbought momentum exhaustion (RSI ${fmt(f.rsi14 ?: 0.0)})"

        val spread = f.spreadBps
        if (spread == null || !spread.isFinite() || spread < 0.0)
            reasons += "bid/ask spread unavailable or invalid"
        else if (spread > MAX_SPREAD_BPS)
            reasons += "bid/ask spread too wide (${fmt(spread)} bps)"

        if (f.prevHigh != null && f.lastHigh != null && f.lastClose != null &&
            f.lastHigh > f.prevHigh && f.lastClose < f.prevHigh
        ) reasons += "confirmed failed breakout / bull-trap rejection"

        val impulse = if (atr != null && f.lastClose != null && f.prevClose != null)
            (f.lastClose - f.prevClose) / atr else null
        if (impulse != null && impulse > MAX_RECENT_IMPULSE_ATR)
            reasons += "single-bar upward impulse too extended (${fmt(impulse)} ATR)"

        if (!confirmedRebreakout &&
            (f.sessionHighDistancePct ?: Double.POSITIVE_INFINITY) <= 0.10 &&
            (vwapExtension ?: 0.0) > 1.5
        ) reasons += "at session high after excessive VWAP extension"

        return FixedEntrySafetyAssessment(
            eligible = reasons.isEmpty(),
            reasons = reasons.distinct(),
            vwapExtensionAtr = vwapExtension,
            ema20ExtensionAtr = emaExtension,
            recentImpulseAtr = impulse,
            confirmedMultiPeakRebreakout = confirmedRebreakout
        )
    }

    private fun fmt(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
}
