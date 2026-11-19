package com.multify.traderpro.engine

/**
 * Presentation and manual-action policy for Fixed recommendation execution.
 * It never changes Fixed selection, publication, automatic retry or SELL rules.
 */
object FixedExecutionPresentationPolicy {
    const val NOT_ATTEMPTED = "NOT_ATTEMPTED"
    const val EXECUTED = "EXECUTED"
    const val EXECUTED_LEGACY = "EXECUTED_LEGACY"
    const val PARTIAL_FILL = "PARTIAL_FILL"
    const val BLOCKED_EXTERNAL_HOLDING = "BLOCKED_EXTERNAL_HOLDING"
    const val BLOCKED_EXISTING_HOLDING = "BLOCKED_EXISTING_HOLDING"
    const val BLOCKED_MAX_HOLDINGS = "BLOCKED_MAX_HOLDINGS"
    const val BLOCKED_PENDING_GTT = "BLOCKED_PENDING_GTT"
    const val BLOCKED_LIQUIDITY = "BLOCKED_LIQUIDITY"
    const val BLOCKED_ENTRY_QUALITY = "BLOCKED_ENTRY_QUALITY"
    const val BLOCKED_PRICE_EXTENSION = "BLOCKED_PRICE_EXTENSION"
    const val BLOCKED_PRICE_BAND = "BLOCKED_PRICE_BAND"
    const val BLOCKED_INSUFFICIENT_FUNDS = "BLOCKED_INSUFFICIENT_FUNDS"
    const val BROKER_REJECTED = "BROKER_REJECTED"
    const val EXPIRED = "EXPIRED"

    fun blocksManualBuy(status: String): Boolean = status in setOf(
        EXECUTED, EXECUTED_LEGACY, PARTIAL_FILL,
        BLOCKED_EXTERNAL_HOLDING, BLOCKED_EXISTING_HOLDING,
        BLOCKED_MAX_HOLDINGS, BLOCKED_PENDING_GTT,
        BLOCKED_LIQUIDITY, BLOCKED_ENTRY_QUALITY,
        BLOCKED_PRICE_EXTENSION, BLOCKED_PRICE_BAND,
        BLOCKED_INSUFFICIENT_FUNDS, BROKER_REJECTED, EXPIRED
    )

    fun canBuy(
        atMs: Long,
        brokerReady: Boolean,
        recommendationStatus: String,
        executionEligible: Boolean,
        executionStatus: String,
        oldFixedEvent: Boolean,
        hasOpenPosition: Boolean
    ): Boolean =
        !oldFixedEvent && !hasOpenPosition && !blocksManualBuy(executionStatus) &&
            FixedBuyAvailabilityPolicy.canBuy(
                atMs, brokerReady, recommendationStatus, executionEligible
            )

    fun buttonLabel(
        symbol: String,
        recommendationStatus: String,
        executionStatus: String,
        filledQuantity: Int?,
        requestedQuantity: Int?,
        oldFixedEvent: Boolean,
        marketOpen: Boolean,
        entryWindowOpen: Boolean,
        brokerReady: Boolean,
        executionEligible: Boolean,
        hasOpenPosition: Boolean
    ): String {
        if (oldFixedEvent) return "Earlier Fixed setup · BUY latest instead"
        if (recommendationStatus == "TARGET_HIT") return "TARGET HIT · BUY disabled"
        when (executionStatus) {
            PARTIAL_FILL -> return "PARTIAL FILL · ${filledQuantity ?: "?"}/${requestedQuantity ?: "?"} · DISABLED"
            EXECUTED -> return "BUY EXECUTED · DISABLED"
            EXECUTED_LEGACY -> return "BUY EXECUTED · FILL DETAILS PENDING"
            BLOCKED_EXTERNAL_HOLDING -> return "EXTERNAL HOLDING · BUY BLOCKED"
            BLOCKED_EXISTING_HOLDING -> return "HOLDING OPEN · BUY BLOCKED"
            BLOCKED_MAX_HOLDINGS -> return "MAX HOLDINGS · WAITING FOR SLOT"
            BLOCKED_PENDING_GTT -> return "PENDING LEGACY GTT · BUY BLOCKED"
            BLOCKED_LIQUIDITY -> return "LIQUIDITY NOT VERIFIED · BUY BLOCKED"
            BLOCKED_ENTRY_QUALITY -> return "ENTRY QUALITY BLOCKED · RESEARCH"
            BLOCKED_PRICE_EXTENSION -> return "PRICE EXTENSION · BUY BLOCKED"
            BLOCKED_PRICE_BAND -> return "PRICE BAND · BUY BLOCKED"
            BLOCKED_INSUFFICIENT_FUNDS -> return "INSUFFICIENT FUNDS · BUY BLOCKED"
            BROKER_REJECTED -> return "BROKER REJECTED · REVIEW"
            EXPIRED -> return "SIGNAL EXPIRED · BUY disabled"
        }
        if (recommendationStatus == "BUY_EXECUTED") return "BUY EXECUTED · FILL DETAILS PENDING"
        if (recommendationStatus != "ACTIVE")
            return recommendationStatus.replace('_', ' ') + " · BUY disabled"
        if (hasOpenPosition) return "HOLDING OPEN · BUY BLOCKED"
        if (!marketOpen) return "MARKET CLOSED · BUY disabled"
        if (!entryWindowOpen) return "ENTRY CUTOFF REACHED · BUY disabled"
        if (!brokerReady) return "Broker readiness required · " + symbol
        if (!executionEligible) return "PRIMARY · observation only"
        return "BUY " + symbol + " · CNC"
    }
}
