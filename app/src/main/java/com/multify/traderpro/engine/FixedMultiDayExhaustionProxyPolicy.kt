package com.multify.traderpro.engine

/** Conservative execution-risk overlay using the frozen 15m MULTI-DAY history.
 * The "session" high and VWAP inside FixedFeatureSnapshot include prior days.
 * They are not a verified broker day-high or intraday VWAP. No orders or extra
 * broker calls are made by this pure policy.
 *
 * Boundaries are provisional (one-session in-sample study); do not call this
 * an out-of-sample-validated predictive alpha rule.
 */
object FixedMultiDayExhaustionProxyPolicy {
    /** Explicitly MULTI-DAY: do not pass this value off as NSE's current-day high. */
    fun distanceFromMultiDayWindowHigh(snapshot: FixedFeatureSnapshot?): Double? =
        snapshot?.sessionHighDistancePct?.takeIf { it.isFinite() }

    const val MAX_DISTANCE_FROM_WINDOW_HIGH_PCT = 0.35
    const val MIN_WINDOW_VWAP_EXTENSION_ATR = 3.0
    // Reuse the existing execution-shadow limits; require all four signals together.
    const val MIN_EMA20_EXTENSION_ATR = 2.0
    const val MIN_RECENT_IMPULSE_ATR = 1.2

    fun risky(
        priorPrimaryExtended: Boolean,
        dayChangeStrong: Boolean,
        multiDayWindowHighDistancePct: Double?,
        multiDayWindowVwapAtr: Double?
    ): Boolean =
        priorPrimaryExtended && dayChangeStrong &&
            multiDayWindowHighDistancePct?.isFinite() == true &&
            multiDayWindowHighDistancePct in -0.10..MAX_DISTANCE_FROM_WINDOW_HIGH_PCT &&
            multiDayWindowVwapAtr?.isFinite() == true &&
            multiDayWindowVwapAtr >= MIN_WINDOW_VWAP_EXTENSION_ATR

    /**
     * Execution-only anti-chase guard for a newly published PRIMARY that has no
     * prior-PRIMARY run-up yet. Uses the explicitly multi-day 15m window (not a
     * claimed NSE day high) and requires a four-way confluence:
     * near the window high + extended above VWAP + extended above EMA20 +
     * a large latest 15m impulse. Missing evidence fails open; no extra API calls.
     *
     * This is a conservative entry-risk rule, not a validated predictive-alpha claim.
     */
    fun lateBreakoutRisk(
        multiDayWindowHighDistancePct: Double?,
        multiDayWindowVwapAtr: Double?,
        ema20ExtensionAtr: Double?,
        recentImpulseAtr: Double?
    ): Boolean =
        multiDayWindowHighDistancePct?.isFinite() == true &&
            multiDayWindowHighDistancePct in -0.10..MAX_DISTANCE_FROM_WINDOW_HIGH_PCT &&
            multiDayWindowVwapAtr?.isFinite() == true &&
            multiDayWindowVwapAtr >= MIN_WINDOW_VWAP_EXTENSION_ATR &&
            ema20ExtensionAtr?.isFinite() == true &&
            ema20ExtensionAtr >= MIN_EMA20_EXTENSION_ATR &&
            recentImpulseAtr?.isFinite() == true &&
            recentImpulseAtr >= MIN_RECENT_IMPULSE_ATR
}
