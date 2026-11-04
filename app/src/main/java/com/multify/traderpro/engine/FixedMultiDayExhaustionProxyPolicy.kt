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
    const val MAX_DISTANCE_FROM_WINDOW_HIGH_PCT = 0.35
    const val MIN_WINDOW_VWAP_EXTENSION_ATR = 3.0

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
}
