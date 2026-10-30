package com.multify.traderpro.engine

/** Prevents one-scan noise from consuming the five daily production slots. */
object FixedForecastPublicationPolicy {
    const val DISCOVERED_PRIOR_PRIMARY_HITS = 1
    const val FALLBACK_PRIOR_PRIMARY_HITS = 2

    fun isReady(
        discoveryBaselineReady: Boolean,
        currentPrimary: Boolean,
        discoveredMarketWide: Boolean,
        priorPrimaryHits: Int
    ): Boolean {
        if (!discoveryBaselineReady || !currentPrimary) return false
        val required = if (discoveredMarketWide) DISCOVERED_PRIOR_PRIMARY_HITS else FALLBACK_PRIOR_PRIMARY_HITS
        return priorPrimaryHits >= required
    }
}
