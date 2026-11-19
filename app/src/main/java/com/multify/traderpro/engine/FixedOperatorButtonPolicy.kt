package com.multify.traderpro.engine

/**
 * Presentation-only Fixed operator states. Never modifies recommendation ranking,
 * auto-BUY, broker orders, GTT, exit rules, or trading eligibility on the server.
 * A button marked enabled still requires the repository's fresh broker checks.
 * Position counts are from the existing app dashboard, never an extra API query.
 */
object FixedOperatorButtonPolicy {
    data class Decision(val enabled: Boolean, val label: String)

    fun decide(
        symbol: String,
        status: String,
        executionEligible: Boolean,
        olderRecommendationEvent: Boolean,
        regularMarketOpen: Boolean,
        fixedEntryWindowOpen: Boolean,
        brokerReadiness: Boolean,
        appOwnedPositionQuantity: Int? = null,
        appOwnedOpenSymbolCount: Int? = null,
        maxConcurrentHoldings: Int? = null,
        quoteMarketabilityState: String = "NO_RECENT_QUOTE"
    ): Decision {
        if (olderRecommendationEvent)
            return Decision(false, "Earlier Fixed setup · BUY latest instead")
        if (status == "TARGET_HIT")
            return Decision(false, "TARGET HIT · BUY disabled")
        if (status == "BUY_EXECUTED") {
            val amount = appOwnedPositionQuantity?.takeIf { it > 0 }
            return Decision(false, amount?.let { "BUY EXECUTED · ${it} shares held" }
                ?: "BUY EXECUTED · no repeat BUY")
        }
        if (status != "ACTIVE")
            return Decision(false, "Fixed ${status.replace('_', ' ')} · BUY disabled")
        if ((appOwnedPositionQuantity ?: 0) > 0)
            return Decision(false, "Existing app holding · BUY blocked")
        if (appOwnedOpenSymbolCount != null && maxConcurrentHoldings != null &&
            maxConcurrentHoldings > 0 && appOwnedOpenSymbolCount >= maxConcurrentHoldings)
            return Decision(false, "Holding capacity ${appOwnedOpenSymbolCount}/${maxConcurrentHoldings} · BUY blocked")
        if (!regularMarketOpen)
            return Decision(false, "Market closed · BUY disabled")
        if (!fixedEntryWindowOpen)
            return Decision(false, "Fixed entry window ended · BUY disabled")
        if (!executionEligible)
            return Decision(false, "Entry not executable · BUY disabled")
        if (!brokerReadiness)
            return Decision(false, "Broker readiness required · ${symbol}")
        // Presentation mirror only: a contemporaneous quote with no seller cannot
        // execute. Existing broker quote/order checks remain authoritative.
        when (quoteMarketabilityState) {
            "UPPER_BAND_NO_SELL_OFFER" ->
                return Decision(false, "No seller at upper band · BUY unavailable")
            "NO_SELL_OFFER_OBSERVED" ->
                return Decision(false, "No sell offer · BUY unavailable")
            "OFFER_OUTSIDE_CIRCUIT" ->
                return Decision(false, "Offer outside price band · BUY blocked")
        }
        return Decision(true,
            if (quoteMarketabilityState == "NO_RECENT_QUOTE" ||
                quoteMarketabilityState == "STALE_CLIENT_QUOTE")
                "BUY ${symbol} · Verify offer"
            else "BUY ${symbol} · CNC")
    }
}
