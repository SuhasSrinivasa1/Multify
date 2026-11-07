package com.multify.traderpro.data.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedMarketPathCodecTest {
    @Test fun canonicalPackingIsReversibleAndOrdered() {
        val sample = linkedMapOf("TCS" to 3020.15, "RELIANCE" to 1503.4, "AXISBANK" to 1084.0)
        val packed = FixedMarketPathCodec.pack(sample)
        assertEquals("AXISBANK~1084.0|RELIANCE~1503.4|TCS~3020.15", packed)
        assertEquals(sample, FixedMarketPathCodec.unpack(packed))
    }

    @Test fun rejectsInvalidOrNonPositivePricesWithoutInventingMarketMoves() {
        val sample = mapOf("GOOD" to 10.0, "BAD" to Double.NaN,
            "TOO_LOW" to 0.0, "NEGATIVE" to -1.0, "NOT:A:SYMBOL" to 42.0)
        val packed = FixedMarketPathCodec.pack(sample)
        assertEquals("GOOD~10.0", packed)
        assertFalse(packed.contains("BAD"))
        assertEquals(mapOf("GOOD" to 10.0), FixedMarketPathCodec.unpack(packed))
    }

    @Test fun invalidArchiveEntriesAreIgnoredWithoutThrowing() {
        val result = FixedMarketPathCodec.unpack("TCS~3200.25|BROKEN|MISSING~|NEG~-2|INF~Infinity|TCS~3201.0")
        assertEquals(1, result.size)
        assertTrue(result.containsKey("TCS"))
        assertEquals(3201.0, result["TCS"]!!, 0.0)
    }
}
