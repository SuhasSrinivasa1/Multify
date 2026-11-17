package com.multify.traderpro.engine

import java.util.Locale

/**
 * Research-only selection preference. Reserves more Alpha LIVE_DATA capacity
 * for the mandatory fresh Alpha BUY quote. Uses no extra broker calls.
 * The actual Beta LIVE category health and governor still decide admission.
 */
object FixedAutoBuyResearchQuoteRoutingPolicy {
    fun preferBetaQuote(symbol: String, fixedAutoBuyEnabled: Boolean): Boolean =
        fixedAutoBuyEnabled && Math.floorMod(
            symbol.uppercase(Locale.US).hashCode(), 2
        ) == 0
}
