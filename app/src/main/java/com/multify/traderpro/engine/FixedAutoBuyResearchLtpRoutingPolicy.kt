package com.multify.traderpro.engine

/**
 * Avoids saturating Alpha's quote/LTP account when Fixed Auto Buy is ON.
 * Original 53 NSE batch slots are unchanged. This read-only route is valid only
 * for a healthy, authenticated Beta LIVE account; Beta cannot BUY/SELL/GTT.
 * Per-account governors and all 429 fail-closed behavior remain authoritative.
 */
object FixedAutoBuyResearchLtpRoutingPolicy {
    fun useBetaBatch(
        index: Int, betaHealthy: Boolean, fixedAutoBuyEnabled: Boolean
    ): Boolean {
        if (!betaHealthy) return false
        return if (fixedAutoBuyEnabled) index % 3 != 0
        else FixedDualAccountPolicy.betaDiscoveryBatch(index, true)
    }
}
