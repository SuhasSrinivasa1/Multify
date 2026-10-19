package com.multify.traderpro.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarketDiscoveryStateCodecTest {
    @Test fun roundTripsNseSymbolsAndPrices() {
        val source = mapOf("M&M" to 3012.45, "BAJAJ-AUTO" to 9123.10, "ABC_DEF" to 10.05)
        val decoded = MarketDiscoveryStateCodec.decode(MarketDiscoveryStateCodec.encode(source))
        assertEquals(source.keys, decoded.keys)
        source.forEach { (symbol, value) -> assertEquals(value, decoded[symbol] ?: 0.0, 0.000001) }
    }

    @Test fun rejectsInvalidValues() {
        val decoded = MarketDiscoveryStateCodec.decode("GOOD~10.0|BAD~NaN|NOPE~-2")
        assertEquals(10.0, decoded["GOOD"] ?: 0.0, 0.0)
        assertFalse(decoded.containsKey("BAD"))
        assertFalse(decoded.containsKey("NOPE"))
    }
}
