package com.multify.traderpro.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class BrokerWireAttemptLedgerTest {
    @Test fun accountWide429AndFailureCountsIncludeCategoryResponses() {
        // Initialize the Alpha account/category counters before taking the baseline.
        val seed = BrokerWireAttemptLedger.start("ALPHA", "GET", "/v1/live-data/ltp")
        BrokerWireAttemptLedger.recordResponse(seed, 200)
        val before = BrokerWireAttemptLedger.summary()
        @Suppress("UNCHECKED_CAST")
        val beforeAccounts = before["accounts"] as Map<String, Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val beforeCategories = beforeAccounts.getValue("ALPHA")["by_category"] as Map<String, Map<String, Any>>
        val beforeAll = beforeAccounts.getValue("ALPHA").getValue("all") as Map<String, Any>
        val beforeLive = beforeCategories.getValue("LIVE_DATA")

        val ticket = BrokerWireAttemptLedger.start(
            "ALPHA", "GET", "/v1/live-data/ltp"
        )
        BrokerWireAttemptLedger.recordResponse(ticket, 429)

        val after = BrokerWireAttemptLedger.summary()
        @Suppress("UNCHECKED_CAST")
        val afterAccounts = after["accounts"] as Map<String, Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val afterCategories = afterAccounts.getValue("ALPHA")["by_category"] as Map<String, Map<String, Any>>
        val afterAll = afterAccounts.getValue("ALPHA").getValue("all") as Map<String, Any>
        val afterLive = afterCategories.getValue("LIVE_DATA")

        assertEquals((beforeAll["http_429"] as Number).toLong() + 1L,
            (afterAll["http_429"] as Number).toLong())
        assertEquals((beforeLive["http_429"] as Number).toLong() + 1L,
            (afterLive["http_429"] as Number).toLong())
        assertEquals((beforeAll["failure_count"] as Number).toLong() + 1L,
            (afterAll["failure_count"] as Number).toLong())
        assertEquals((beforeLive["failure_count"] as Number).toLong() + 1L,
            (afterLive["failure_count"] as Number).toLong())
    }
}
