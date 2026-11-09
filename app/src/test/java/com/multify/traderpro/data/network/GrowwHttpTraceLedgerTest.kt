package com.multify.traderpro.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class GrowwHttpTraceLedgerTest {
    @Test fun classifiedEndpointFamiliesAreSeparated() {
        assertEquals("LIVE_DATA", GrowwHttpTraceLedger.classify("/v1/live-data/ltp"))
        assertEquals("LIVE_DATA", GrowwHttpTraceLedger.classify("/v1/live-data/quote"))
        assertEquals("HISTORICAL", GrowwHttpTraceLedger.classify("/v1/historical/candles"))
        assertEquals("ORDERS", GrowwHttpTraceLedger.classify("/v1/order/create"))
        assertEquals("ORDERS", GrowwHttpTraceLedger.classify("/v1/order-advance/create"))
        assertEquals("NON_TRADING", GrowwHttpTraceLedger.classify("/v1/holdings/user"))
        assertEquals("AUTH", GrowwHttpTraceLedger.classify("/v1/token/api/access"))
    }
}
