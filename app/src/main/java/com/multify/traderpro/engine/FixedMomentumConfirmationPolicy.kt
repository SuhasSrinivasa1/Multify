package com.multify.traderpro.engine

/**
 * Challenger-only confirmation rules derived from the uploaded session diagnostics.
 * This policy is SHADOW ONLY: it must not change the frozen Fixed publication or order path.
 */
data class FixedMomentumConfirmationVerdict(
    val confirmed: Boolean,
    val macdSlopeConfirmed: Boolean,
    val orderBookConfirmed: Boolean,
    val rsiSlopeConfirmed: Boolean,
    val reasons: List<String>
)

object FixedMomentumConfirmationPolicy {
    const val MIN_MACD_HISTOGRAM_SLOPE_ATR = 0.15

    fun evaluate(features: FixedFeatureSnapshot): FixedMomentumConfirmationVerdict {
        val macd = (features.macdHistogramSlope ?: Double.NEGATIVE_INFINITY) >= MIN_MACD_HISTOGRAM_SLOPE_ATR
        val book = (features.orderBookImbalance ?: Double.NEGATIVE_INFINITY) > 0.0
        val rsi = (features.rsiSlope ?: Double.NEGATIVE_INFINITY) > 0.0
        val reasons = buildList {
            if (!macd) add("MACD_HISTOGRAM_SLOPE_BELOW_0_15_ATR")
            if (!book) add("ORDER_BOOK_IMBALANCE_NOT_POSITIVE")
            if (!rsi) add("RSI_SLOPE_NOT_POSITIVE")
        }
        return FixedMomentumConfirmationVerdict(macd && book && rsi, macd, book, rsi, reasons)
    }
}

data class FixedFiveMinuteEntryVerdict(
    val confirmed: Boolean,
    val pullbackReclaim: Boolean,
    val aboveSessionVwap: Boolean,
    val volumeConfirmed: Boolean,
    val momentumConfirmed: Boolean,
    val reasons: List<String>
)

/** 5m pullback/reclaim overlay for offline shadow validation only. */
object FixedFiveMinuteEntryPolicy {
    fun evaluate(features: FixedFeatureSnapshot, fifteenMinutePrimary: Boolean): FixedFiveMinuteEntryVerdict {
        val lastOpen = features.lastOpen
        val lastClose = features.lastClose
        val previousOpen = features.prevOpen
        val previousClose = features.prevClose
        val previousHigh = features.prevHigh
        val pullbackThenReclaim = fifteenMinutePrimary &&
            features.sessionCandleCount >= 3 &&
            lastOpen != null && lastClose != null && lastClose > lastOpen &&
            previousOpen != null && previousClose != null && previousHigh != null &&
            previousClose < previousOpen && lastClose > previousOpen &&
            (features.vwap == null || lastClose > features.vwap)
        val aboveVwap = features.vwap != null && lastClose != null && lastClose > features.vwap
        val volume = (features.rvol ?: 0.0) >= 1.0
        val momentum = FixedMomentumConfirmationPolicy.evaluate(features).confirmed
        val reasons = buildList {
            if (!fifteenMinutePrimary) add("15M_PRIMARY_TREND_NOT_CONFIRMED")
            if (features.sessionCandleCount < 3) add("INSUFFICIENT_COMPLETED_5M_SESSION_BARS")
            if (!pullbackThenReclaim) add("5M_PULLBACK_RECLAIM_NOT_CONFIRMED")
            if (!aboveVwap) add("5M_CLOSE_NOT_ABOVE_SESSION_VWAP")
            if (!volume) add("5M_RELATIVE_VOLUME_BELOW_1_0")
            if (!momentum) add("5M_MACD_BOOK_RSI_CONFIRMATION_INCOMPLETE")
        }
        return FixedFiveMinuteEntryVerdict(
            confirmed = reasons.isEmpty(),
            pullbackReclaim = pullbackThenReclaim,
            aboveSessionVwap = aboveVwap,
            volumeConfirmed = volume,
            momentumConfirmed = momentum,
            reasons = reasons
        )
    }
}
