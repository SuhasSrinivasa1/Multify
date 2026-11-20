package com.multify.traderpro.engine

/**
 * Execution-only guard for confirmed Working Range entries.
 * The frozen Fixed selection, ranking, confirmation and target are untouched.
 *
 * A confirmed candidate may be executed only promptly and without a material
 * downward reversal from its own confirmed reference. Rejected events are not
 * revived by a later quote; a new independently confirmed event is required.
 */
object WorkingRangeEntryMomentumPolicy {
    const val MAX_CONFIRMATION_AGE_MS = 120_000L
    const val MAX_DRAWDOWN_FROM_CONFIRMATION_PCT = 0.75
    // Research may still identify a technically valid setup after an initial
    // surge. Auto-Buy must not chase a materially extended first observation.
    // Tunable research hypothesis: initial cap is deliberately conservative
    // after the 20-Nov ASIANHOTNR loss, not a proven optimal return threshold.
    const val MAX_RUNUP_FROM_FIRST_QUALIFIED_PCT = 0.75

    fun runupRejectionReason(
        firstQualifiedPrice: Double,
        freshAlphaPrice: Double
    ): String? {
        if (!firstQualifiedPrice.isFinite() || firstQualifiedPrice <= 0.0 ||
            !freshAlphaPrice.isFinite() || freshAlphaPrice <= 0.0
        ) return "FIRST_QUALIFIED_REFERENCE_UNAVAILABLE"
        val runupPct = (freshAlphaPrice / firstQualifiedPrice - 1.0) * 100.0
        return if (runupPct > MAX_RUNUP_FROM_FIRST_QUALIFIED_PCT)
            "FIRST_QUALIFIED_PRICE_EXTENSION_TOO_HIGH"
        else null
    }


    fun rejectionReason(
        confirmedPrice: Double,
        freshAlphaPrice: Double,
        confirmedAtMs: Long,
        nowMs: Long
    ): String? {
        if (!confirmedPrice.isFinite() || confirmedPrice <= 0.0 ||
            !freshAlphaPrice.isFinite() || freshAlphaPrice <= 0.0
        ) return "INVALID_CONFIRMATION_OR_FRESH_PRICE"
        if (confirmedAtMs <= 0L || nowMs < confirmedAtMs) return "INVALID_CONFIRMATION_CLOCK"
        if (nowMs - confirmedAtMs > MAX_CONFIRMATION_AGE_MS) return "WORKING_RANGE_SIGNAL_STALE"
        val declinePct = (confirmedPrice - freshAlphaPrice) / confirmedPrice * 100.0
        if (declinePct > MAX_DRAWDOWN_FROM_CONFIRMATION_PCT)
            return "WORKING_RANGE_MOMENTUM_LOST_PRICE_RETRACE"
        return null
    }
}
