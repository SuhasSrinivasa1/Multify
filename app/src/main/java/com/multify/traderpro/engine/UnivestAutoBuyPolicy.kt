package com.multify.traderpro.engine

/**
 * Univest is an entry-signal source, not an exit authority.
 * The existing Fixed Auto Buy switch intentionally also controls Univest ENTRY orders.
 * Old notification replays, previously captured alerts and official EXIT calls are never buys.
 */
object UnivestAutoBuyPolicy {
    const val MAX_NOTIFICATION_AGE_MS = 120_000L

    fun rejectionReason(
        type: ForecastUnivestLearningPolicy.Type,
        postedAtMs: Long,
        nowMs: Long,
        fixedAutoBuyEnabled: Boolean,
        fixedAutoBuyActivatedAtMs: Long
    ): String? = when {
        type != ForecastUnivestLearningPolicy.Type.ENTRY -> "NOT_UNIVEST_ENTRY"
        !fixedAutoBuyEnabled -> "FIXED_AUTO_BUY_OFF"
        fixedAutoBuyActivatedAtMs <= 0L -> "NO_CURRENT_FIXED_TOGGLE_ACTIVATION"
        postedAtMs <= fixedAutoBuyActivatedAtMs -> "ALERT_PREDATES_FIXED_TOGGLE"
        postedAtMs <= 0L || nowMs < postedAtMs -> "INVALID_NOTIFICATION_TIME"
        nowMs - postedAtMs > MAX_NOTIFICATION_AGE_MS -> "STALE_UNIVEST_NOTIFICATION"
        else -> null
    }
}
