package com.multify.traderpro.engine

import com.multify.traderpro.data.network.GrowwHttpTraceLedger
import org.junit.Assert.*
import org.junit.Test

class GrowwApiRequestClassificationV909Test {
    @Test fun informationalOrdersAreNonTrading() {
        assertEquals("NON_TRADING",
            GrowwHttpTraceLedger.classify("/v1/order/list"))
        assertEquals("NON_TRADING",
            GrowwHttpTraceLedger.classify("/v1/order/detail/12345"))
        assertEquals("NON_TRADING",
            GrowwHttpTraceLedger.classify("/v1/order-advance/list"))
        assertEquals("NON_TRADING",
            GrowwHttpTraceLedger.classify("/v1/order-advance/status/CASH/OCO/internal/12345"))
    }
    @Test fun actualBrokerMutationsRemainOrders() {
        assertEquals("ORDERS",
            GrowwHttpTraceLedger.classify("/v1/order/create"))
        assertEquals("ORDERS",
            GrowwHttpTraceLedger.classify("/v1/order/cancel"))
        assertEquals("ORDERS",
            GrowwHttpTraceLedger.classify("/v1/order-advance/create"))
        assertEquals("ORDERS",
            GrowwHttpTraceLedger.classify("/v1/order-advance/modify/123"))
    }
}
