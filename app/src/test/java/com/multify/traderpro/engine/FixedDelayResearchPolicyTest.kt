package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedDelayResearchPolicyTest {
    @Test fun bucketsAreStableAndDoNotRepresentTradeDecisions() {
        assertEquals("UNVERIFIED", FixedDelayResearchPolicy.bucket(null))
        assertEquals("UNVERIFIED", FixedDelayResearchPolicy.bucket(-1L))
        assertEquals("0_TO_10_SECONDS", FixedDelayResearchPolicy.bucket(0L))
        assertEquals("0_TO_10_SECONDS", FixedDelayResearchPolicy.bucket(10_000L))
        assertEquals("10_TO_30_SECONDS", FixedDelayResearchPolicy.bucket(10_001L))
        assertEquals("10_TO_30_SECONDS", FixedDelayResearchPolicy.bucket(30_000L))
        assertEquals("30_TO_60_SECONDS", FixedDelayResearchPolicy.bucket(30_001L))
        assertEquals("OVER_60_SECONDS", FixedDelayResearchPolicy.bucket(60_001L))
    }
}
