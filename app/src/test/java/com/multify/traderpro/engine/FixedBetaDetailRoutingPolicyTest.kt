package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedBetaDetailRoutingPolicyTest {
    @Test fun routesDeterministicallyAndNeverChangesCandidateSet() {
        val symbols = (1..300).map { "SYMBOL$it" }
        val first = symbols.filter { FixedBetaDetailRoutingPolicy.useBeta(it, true) }
        val second = symbols.filter { FixedBetaDetailRoutingPolicy.useBeta(it, true) }
        assertEquals(first, second)
        assertTrue(first.size in 175..185)
        assertEquals(14, FixedBetaDetailRoutingPolicy.BETA_HISTORICAL_PER_MINUTE)
        assertEquals(0, symbols.count { FixedBetaDetailRoutingPolicy.useBeta(it, false) })
        assertFalse(FixedBetaDetailRoutingPolicy.useBeta("", true))
        assertEquals(symbols.toSet(), (first + symbols.filterNot { it in first }).toSet())
    }
}
