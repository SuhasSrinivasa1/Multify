package com.multify.traderpro.engine

/** Beta-only scheduling fairness. This policy never starts or delays a Fixed or SELL job. */
object ForecastBetaPriorityPolicy {
    fun canResearch(
        fixedPriorityRequested: Boolean,
        criticalTradeInFlight: Boolean,
        fixedDeferredForBroker: Boolean,
        millisUntilNextFixed: Long,
        fixedGuardMs: Long
    ): Boolean {
        if (fixedPriorityRequested || criticalTradeInFlight) return false
        // Fixed was explicitly deferred by the Alpha broker; Beta can use its own quota.
        // The next Fixed attempt still takes priority as soon as it starts.
        if (fixedDeferredForBroker) return true
        return millisUntilNextFixed > fixedGuardMs
    }
}
