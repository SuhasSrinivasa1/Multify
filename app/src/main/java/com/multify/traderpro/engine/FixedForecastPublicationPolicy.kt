package com.multify.traderpro.engine

/** Market-wide PRIMARY calls can publish on the first fully-qualified scan; fallback names need repeat proof. */
object FixedForecastPublicationPolicy {
    const val DISCOVERED_PRIOR_PRIMARY_HITS = 0
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
