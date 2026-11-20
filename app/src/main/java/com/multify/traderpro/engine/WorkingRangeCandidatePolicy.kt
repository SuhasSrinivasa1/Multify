package com.multify.traderpro.engine

/**
 * Working Range v0.4 is a separate bounded entry-range filter, not the Fixed model.
 * It requires VWAP extension <=3.50 ATR, spread <=35 bps when observed,
 * drift within +0.50% / -0.75% of first qualification and a 120s confirmation
 * horizon. RSI has no upper ceiling; high RSI alone is not a veto.
 *
 * Boundaries are conservative operating guards informed by the 20-Nov ASIANHOTNR
 * chase, not historically optimized parameters. Live Auto-Buy also requires a
 * fresh Alpha quote and an explicit Working Range toggle activation.
 *
 * This policy consumes an evaluation and quote already fetched by Fixed. It
 * makes no API calls or direct orders and never changes Fixed scoring,
 * rank, publication, target, stop, GTT or trailing. The separate Auto-Buy consumer is outside Fixed.
 */
internal object WorkingRangeCandidatePolicy {
    const val TARGET_PCT = FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT
    const val MIN_RSI = 75.0
    const val MIN_RVOL = 1.0
    const val MIN_GREEN_VOLUME_SHARE = 0.80
    const val MIN_VOLUME_ACCELERATION = -0.50
    const val MAX_VOLUME_ACCELERATION = 30.0
    const val MAX_TARGET_ATR = 1.60
    const val MIN_MINUTES_TO_CLOSE = 38.0
    const val MAX_CANDIDATE_AGE_MINUTES =
        WorkingRangeEntryBandPolicy.MAX_FIRST_QUALIFIED_TO_BUY_MS / 60_000.0
    const val MAX_DRIFT_FROM_FIRST_QUALIFIED_PCT =
        WorkingRangeEntryBandPolicy.MAX_UPWARD_DRIFT_FROM_FIRST_QUALIFIED_PCT
    const val MIN_DRIFT_FROM_FIRST_QUALIFIED_PCT =
        -WorkingRangeEntryBandPolicy.MAX_DRAWDOWN_FROM_FIRST_QUALIFIED_PCT
    const val MAX_VWAP_EXTENSION_ATR = WorkingRangeEntryBandPolicy.MAX_VWAP_EXTENSION_ATR
    const val MAX_SPREAD_BPS = WorkingRangeEntryBandPolicy.MAX_SPREAD_BPS
    const val REQUIRED_CONSECUTIVE_OBSERVATIONS = 2
    const val MAX_CONFIRMATION_GAP_MS = 120_000L

    data class Inputs(
        val price: Double,
        val atr: Double?,
        val rsi: Double?,
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
        val minutesToClose: Double
    )

    data class Decision(
        val matches: Boolean,
        val reasons: List<String>,
        val targetRequirementAtr: Double?,
        val vwapExtensionAtr: Double?,
        val emaAligned: Boolean?,
        val emaAlignmentMode: String
    )

    fun assess(i: Inputs): Decision {
        val reasons = mutableListOf<String>()
        fun valid(v: Double?): Double? = v?.takeIf { it.isFinite() }
        val price = i.price.takeIf { it.isFinite() && it > 0.0 }
        val atr = valid(i.atr)?.takeIf { it > 0.0 }
        val rsi = valid(i.rsi)
        val rvol = valid(i.rvol)
        val accel = valid(i.volumeAcceleration)
        val green = valid(i.greenVolumeShare)
        val e9 = valid(i.ema9); val e20 = valid(i.ema20); val e50 = valid(i.ema50)
        val s9 = valid(i.ema9Slope); val s20 = valid(i.ema20Slope)
        val macd = valid(i.macdHistogramSlope)
        val vwap = valid(i.vwap)
        val targetAtr = if (price != null && atr != null)
            price * TARGET_PCT / (100.0 * atr) else null
        val vwapExtension = if (price != null && atr != null && vwap != null)
            (price - vwap) / atr else null
        val emaAligned = if (e9 != null && e20 != null && e50 != null)
            e9 > e20 && e20 > e50 else null
        val emaAlignmentMode = when {
            emaAligned == null -> "MISSING"
            emaAligned -> "STRICT"
            else -> "NOT_ALIGNED"
        }

        if (price == null || !RecommendationPriceBandPolicy.eligible(price))
            reasons += "PRICE_OUTSIDE_FIXED_EQUITY_BAND"
        if (rsi == null) reasons += "RSI_MISSING" else if (rsi < MIN_RSI) reasons += "RSI_BELOW_75"
        if (rvol == null) reasons += "RVOL_MISSING" else if (rvol < MIN_RVOL) reasons += "RVOL_BELOW_1"
        if (green == null) reasons += "GREEN_VOLUME_SHARE_MISSING"
        else if (green < MIN_GREEN_VOLUME_SHARE) reasons += "GREEN_VOLUME_SHARE_BELOW_80"
        if (accel == null) reasons += "VOLUME_ACCELERATION_MISSING"
        else if (accel !in MIN_VOLUME_ACCELERATION..MAX_VOLUME_ACCELERATION)
            reasons += "VOLUME_ACCELERATION_OUTSIDE_PILOT_RANGE"
        if (emaAligned == null) reasons += "EMA_ALIGNMENT_MISSING"
        else if (!emaAligned) reasons += "EMA9_20_50_NOT_ALIGNED"
        if (s9 == null || s20 == null) reasons += "EMA_SLOPE_MISSING"
        else if (s9 <= 0.0 || s20 <= 0.0) reasons += "EMA_SLOPE_NOT_POSITIVE"
        if (macd == null) reasons += "MACD_SLOPE_MISSING"
        else if (macd <= 0.0) reasons += "MACD_SLOPE_NOT_POSITIVE"
        if (targetAtr == null) reasons += "TARGET_ATR_UNAVAILABLE"
        else if (targetAtr > MAX_TARGET_ATR) reasons += "TARGET_REQUIREMENT_ABOVE_1_6_ATR"
        if (vwapExtension == null) reasons += "VWAP_EXTENSION_UNAVAILABLE"
        else if (vwapExtension > MAX_VWAP_EXTENSION_ATR)
            reasons += "VWAP_EXTENSION_ABOVE_3_50_ATR"
        val spread = valid(i.spreadBps)
        if (spread != null && spread > MAX_SPREAD_BPS)
            reasons += "SPREAD_ABOVE_35_BPS"
        if (!i.minutesToClose.isFinite() || i.minutesToClose < MIN_MINUTES_TO_CLOSE)
            reasons += "LESS_THAN_38_MINUTES_TO_CLOSE"

        return Decision(
            matches = reasons.isEmpty(),
            reasons = reasons,
            targetRequirementAtr = targetAtr,
            vwapExtensionAtr = vwapExtension,
            emaAligned = emaAligned,
            emaAlignmentMode = emaAlignmentMode
        )
    }

    fun inputs(e: FixedStrategyEvaluation, minutesToClose: Double): Inputs {
        val f = e.features
        return Inputs(
            price = f.ltp, atr = f.atr14, rsi = f.rsi14, rvol = f.rvol,
            volumeAcceleration = f.volumeAcceleration, greenVolumeShare = f.greenVolumeShare,
            ema9 = f.ema9, ema20 = f.ema20, ema50 = f.ema50,
            ema9Slope = f.ema9Slope, ema20Slope = f.ema20Slope,
            macdHistogramSlope = f.macdHistogramSlope, vwap = f.vwap,
            range52Position = f.range52Position, spreadBps = f.spreadBps,
            orderBookImbalance = f.orderBookImbalance, minutesToClose = minutesToClose
        )
    }
}
