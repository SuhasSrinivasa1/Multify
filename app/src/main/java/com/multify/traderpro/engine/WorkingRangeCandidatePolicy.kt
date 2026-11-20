package com.multify.traderpro.engine

/**
 * Standalone Working Range momentum filter.
 *
 * It consumes a strategy-owned snapshot made from market data already fetched by the
 * shared scan transport. It does not read other strategy scores, external alert
 * notifications, learning priors or another strategy's recommendation state.
 * The target constant is owned here and is intentionally frozen for comparable replay.
 */
internal object WorkingRangeCandidatePolicy {
    const val TARGET_PCT = 1.1161043030113955
    const val MIN_RSI = 75.0
    const val MIN_RVOL = 1.0
    const val MIN_GREEN_VOLUME_SHARE = 0.80
    const val MIN_VOLUME_ACCELERATION = -0.50
    const val MAX_VOLUME_ACCELERATION = 30.0
    const val MAX_TARGET_ATR = 1.60
    const val MIN_MINUTES_TO_CLOSE = 38.0
    const val MAX_CANDIDATE_AGE_MINUTES = 36.0
    const val MAX_DRIFT_FROM_FIRST_QUALIFIED_PCT = 1.60
    const val REQUIRED_CONSECUTIVE_OBSERVATIONS = 2
    const val MAX_CONFIRMATION_GAP_MS = 5 * 60_000L
    const val MIN_OPENING_MOMENTUM_PCT = WorkingRangeOpeningMomentumPolicy.MIN_OPENING_MOMENTUM_PCT
    const val MIN_OPENING_SAMPLES = WorkingRangeOpeningMomentumPolicy.MIN_OPENING_SAMPLES
    const val MIN_PRICE = 200.0
    const val MAX_PRICE = 5_000.0

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
        val minutesToClose: Double,
        val rsiSlope: Double? = null,
        val openingMode: Boolean = false,
        val openingSampleCount: Int = 0,
        val openingMomentumPct: Double? = null,
        val openingVolumePaceRvol: Double? = null,
        val openingSignalBasis: String = "COMPLETED_15M_SESSION_CANDLES"
    )

    data class Decision(
        val matches: Boolean,
        val reasons: List<String>,
        val targetRequirementAtr: Double?,
        val vwapExtensionAtr: Double?,
        val emaAligned: Boolean?,
        val emaAlignmentMode: String
    )

    fun priceEligible(price: Double): Boolean = price.isFinite() && price > MIN_PRICE && price < MAX_PRICE

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

        if (price == null || price <= MIN_PRICE || price >= MAX_PRICE)
            reasons += "PRICE_OUTSIDE_WORKING_RANGE_EQUITY_BAND"
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
        if (i.openingMode) {
            if (i.openingSampleCount < MIN_OPENING_SAMPLES)
                reasons += "OPENING_MOMENTUM_SAMPLES_INSUFFICIENT"
            val momentum = valid(i.openingMomentumPct)
            if (momentum == null) reasons += "OPENING_MOMENTUM_MISSING"
            else if (momentum < MIN_OPENING_MOMENTUM_PCT)
                reasons += "OPENING_MOMENTUM_BELOW_0_20_PCT"
        }
        if (targetAtr == null) reasons += "TARGET_ATR_UNAVAILABLE"
        else if (targetAtr > MAX_TARGET_ATR) reasons += "TARGET_REQUIREMENT_ABOVE_1_6_ATR"
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
}
