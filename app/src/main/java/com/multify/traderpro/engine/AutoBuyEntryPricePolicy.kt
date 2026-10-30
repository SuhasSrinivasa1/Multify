package com.multify.traderpro.engine

data class AutoBuyEntryPriceDecision(
    val allowed: Boolean,
    val deviationPct: Double
)

/**
 * Fast pre-order guard using the live quote already fetched by the BUY path.
 * Positive slippage is bounded; a lower price remains eligible for the existing strategy gates.
 */
object AutoBuyEntryPricePolicy {
    const val MAX_POSITIVE_SLIPPAGE_PCT = 0.35

    fun evaluate(recommendedEntryPrice: Double, livePrice: Double): AutoBuyEntryPriceDecision {
        if (!recommendedEntryPrice.isFinite() || recommendedEntryPrice <= 0.0 ||
            !livePrice.isFinite() || livePrice <= 0.0
        ) return AutoBuyEntryPriceDecision(false, Double.NaN)
        val deviation = (livePrice / recommendedEntryPrice - 1.0) * 100.0
        return AutoBuyEntryPriceDecision(
            allowed = deviation <= MAX_POSITIVE_SLIPPAGE_PCT + 1e-9,
            deviationPct = deviation
        )
    }
}
