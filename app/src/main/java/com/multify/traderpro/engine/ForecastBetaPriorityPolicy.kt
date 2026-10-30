package com.multify.traderpro.engine

/** Beta-only scheduling fairness. It never borrows Alpha's Fixed or order API quota. */
object ForecastBetaPriorityPolicy {
    fun canResearch(
        fixedPriorityRequested: Boolean,
        criticalTradeInFlight: Boolean,
        fixedDeferredForBroker: Boolean,
        millisUntilNextFixed: Long,
        fixedGuardMs: Long,
        allowIndependentBetaDuringFixed: Boolean = false
    ): Boolean {
        if (criticalTradeInFlight) return false
        if (fixedPriorityRequested && !allowIndependentBetaDuringFixed) return false
        // Beta has its own credential, quota and mutex. Keep this exception bounded by the
        // caller to a small research tranche; protective trades always retain priority.
        if (fixedPriorityRequested && allowIndependentBetaDuringFixed) return true
        if (fixedDeferredForBroker) return true
        return millisUntilNextFixed > fixedGuardMs
    }
}
