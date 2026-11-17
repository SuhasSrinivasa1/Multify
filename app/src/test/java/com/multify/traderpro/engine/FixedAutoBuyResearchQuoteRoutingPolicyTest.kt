package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedAutoBuyResearchQuoteRoutingPolicyTest {
    private val p = FixedAutoBuyResearchQuoteRoutingPolicy

    @Test fun noChangeWhenAutoBuyOff() {
        for (symbol in listOf("RELIANCE", "TCS", "INFY", "HDFCBANK"))
            assertFalse(p.preferBetaQuote(symbol, false))
    }

    @Test fun deterministicReadOnlyPreferenceWhenAutoBuyOn() {
        val symbols = listOf("RELIANCE", "TCS", "INFY", "HDFCBANK", "MVELECTRO", "BLUESTARCO")
        symbols.forEach { s ->
            assertEquals(Math.floorMod(s.hashCode(), 2) == 0, p.preferBetaQuote(s, true))
            assertEquals(p.preferBetaQuote(s, true), p.preferBetaQuote(s.lowercase(), true))
        }
    }
}
