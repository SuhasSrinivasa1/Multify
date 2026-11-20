package com.multify.traderpro.engine

/**
 * A real, bounded Working Range entry band. This policy is separate from Fixed:
 * it never changes Fixed's 40-factor score, 15m inputs, PRIMARY rules or ranking.
 *
 * The thresholds are conservative operational guards selected after the 20-Nov
 * ASIANHOTNR chase: +1.555% from first qualification, 4.60 ATR above VWAP,
 * 38.28 bps spread and a 199s confirmation gap. They are not claimed to be
 * historically optimal; every rejection is logged for equal-horizon replay.
 */
object WorkingRangeEntryBandPolicy {
    const val MAX_FIRST_QUALIFIED_TO_BUY_MS = 120_000L
    const val MAX_CONFIRMATION_TO_BUY_MS = 30_000L
    const val MAX_UPWARD_DRIFT_FROM_FIRST_QUALIFIED_PCT = 0.50
    const val MAX_DRAWDOWN_FROM_FIRST_QUALIFIED_PCT = 0.75
    const val MAX_VWAP_EXTENSION_ATR = 3.50
    const val MAX_SPREAD_BPS = 35.0

    data class Assessment(
        val eligible: Boolean,
        val reason: String,
        val driftFromFirstQualifiedPct: Double?,
        val freshVwapExtensionAtr: Double?
    )

    fun driftAtFreshQuote(
        driftAtConfirmationPct: Double,
        confirmationPrice: Double,
        freshPrice: Double
    ): Double? {
        if (!driftAtConfirmationPct.isFinite() ||
            !confirmationPrice.isFinite() || confirmationPrice <= 0.0 ||
            !freshPrice.isFinite() || freshPrice <= 0.0
        ) return null
        return ((1.0 + driftAtConfirmationPct / 100.0) *
            (freshPrice / confirmationPrice) - 1.0) * 100.0
    }

    fun vwapExtensionAtFreshQuote(
        vwapExtensionAtConfirmationAtr: Double?,
        targetRequirementAtr: Double?,
        targetPct: Double,
        confirmationPrice: Double,
        freshPrice: Double
    ): Double? {
        val extension = vwapExtensionAtConfirmationAtr
            ?.takeIf { it.isFinite() } ?: return null
        val targetAtr = targetRequirementAtr
            ?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (!targetPct.isFinite() || targetPct <= 0.0 ||
            !confirmationPrice.isFinite() || confirmationPrice <= 0.0 ||
            !freshPrice.isFinite() || freshPrice <= 0.0
        ) return null
        val atr = confirmationPrice * targetPct / (100.0 * targetAtr)
        if (!atr.isFinite() || atr <= 0.0) return null
        return extension + (freshPrice - confirmationPrice) / atr
    }

    fun assess(
        firstQualifiedAtMs: Long,
        confirmedAtMs: Long,
        nowMs: Long,
        driftAtConfirmationPct: Double,
        confirmationPrice: Double,
        freshPrice: Double,
        vwapExtensionAtConfirmationAtr: Double?,
        targetRequirementAtr: Double?,
        targetPct: Double,
        freshSpreadBps: Double?
    ): Assessment {
        val drift = driftAtFreshQuote(
            driftAtConfirmationPct, confirmationPrice, freshPrice
        )
        val extension = vwapExtensionAtFreshQuote(
            vwapExtensionAtConfirmationAtr, targetRequirementAtr,
            targetPct, confirmationPrice, freshPrice
        )
        fun reject(reason: String) = Assessment(false, reason, drift, extension)

        if (firstQualifiedAtMs <= 0L || confirmedAtMs <= firstQualifiedAtMs ||
            nowMs < confirmedAtMs
        ) return reject("WORKING_RANGE_ENTRY_CLOCK_INVALID")
        if (nowMs - firstQualifiedAtMs > MAX_FIRST_QUALIFIED_TO_BUY_MS)
            return reject("WORKING_RANGE_FIRST_QUALIFIED_SIGNAL_STALE")
        if (nowMs - confirmedAtMs > MAX_CONFIRMATION_TO_BUY_MS)
            return reject("WORKING_RANGE_CONFIRMATION_TO_BUY_OVER_30S")
        if (drift == null) return reject("WORKING_RANGE_FIRST_PRICE_DRIFT_UNAVAILABLE")
        if (drift > MAX_UPWARD_DRIFT_FROM_FIRST_QUALIFIED_PCT)
            return reject("WORKING_RANGE_PRICE_ABOVE_ENTRY_BAND")
        if (drift < -MAX_DRAWDOWN_FROM_FIRST_QUALIFIED_PCT)
            return reject("WORKING_RANGE_MOMENTUM_LOST_BELOW_ENTRY_BAND")
        if (extension == null) return reject("WORKING_RANGE_VWAP_EXTENSION_UNAVAILABLE")
        if (extension > MAX_VWAP_EXTENSION_ATR)
            return reject("WORKING_RANGE_VWAP_EXTENSION_ABOVE_3_50_ATR")
        if (freshSpreadBps == null || !freshSpreadBps.isFinite() || freshSpreadBps < 0.0)
            return reject("WORKING_RANGE_FRESH_SPREAD_UNAVAILABLE")
        if (freshSpreadBps > MAX_SPREAD_BPS)
            return reject("WORKING_RANGE_FRESH_SPREAD_ABOVE_35_BPS")
        return Assessment(true, "WORKING_RANGE_ENTRY_INSIDE_BOUNDED_RANGE", drift, extension)
    }
}
