package com.multify.traderpro.engine

/**
 * Shadow-only entry quality hypothesis. Never selects, publishes or trades a stock.
 * Observes completed-session candle features that production already computed.
 * Requires independent multi-session validation before live eligibility use.
 */
object FixedAccuracyChallengerPolicy {
    const val MACD_SLOPE_FLOOR = 0.15
    const val MAX_VWAP_EXTENSION_ATR = 2.25
    data class Verdict(
        val dataReady: Boolean,
        val threeFactorConfirmed: Boolean,
        val extendedFromSessionVwap: Boolean,
        val rsiMomentumExhaustion: Boolean,
        val macdSlope: Double?,
        val rsiSlope: Double?,
        val orderBookImbalance: Double?,
        val vwapExtensionAtr: Double?
    )
    fun assess(f: FixedFeatureSnapshot): Verdict {
        val atr = f.atr14?.takeIf { it.isFinite() && it > 0.0 }
        val vwap = f.vwap?.takeIf { it.isFinite() && it > 0.0 }
        val extension = if (atr != null && vwap != null) (f.ltp - vwap) / atr else null
        val macd = f.macdHistogramSlope?.takeIf { it.isFinite() }
        val rsiSlope = f.rsiSlope?.takeIf { it.isFinite() }
        val book = f.orderBookImbalance?.takeIf { it.isFinite() }
        return Verdict(
            dataReady = f.sessionCandleCount >= 1 && extension != null &&
                macd != null && rsiSlope != null && book != null,
            threeFactorConfirmed = macd != null && macd > MACD_SLOPE_FLOOR &&
                rsiSlope != null && rsiSlope > 0.0 && book != null && book > 0.0,
            extendedFromSessionVwap = extension != null && extension > MAX_VWAP_EXTENSION_ATR,
            rsiMomentumExhaustion = (f.rsi14 ?: 0.0) >= 75.0 && (rsiSlope ?: 0.0) <= 0.0,
            macdSlope = macd, rsiSlope = rsiSlope, orderBookImbalance = book,
            vwapExtensionAtr = extension
        )
    }
}
