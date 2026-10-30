package com.multify.traderpro.engine

/** Prevents one-scan noise from consuming the five daily production slots. */
object ForecastPublicationPolicy {
    const val DISCOVERED_PRIOR_PRIMARY_HITS = 0
    const val FALLBACK_PRIOR_PRIMARY_HITS = 1

    fun isReady(
        discoveryBaselineReady: Boolean,
        currentPrimary: Boolean,
        discoveredMarketWide: Boolean,
        priorPrimaryHits: Int,
        researchOnly: Boolean = false
    ): Boolean {
        if (!discoveryBaselineReady || !currentPrimary) return false
        // A validated PRIMARY may be observed without repeat confirmation while trading
        // is calibration-locked. Trading mode retains the original required confirmations.
        val required = if (researchOnly) 0 else
            if (discoveredMarketWide) DISCOVERED_PRIOR_PRIMARY_HITS else FALLBACK_PRIOR_PRIMARY_HITS
        return priorPrimaryHits >= required
    }
}
