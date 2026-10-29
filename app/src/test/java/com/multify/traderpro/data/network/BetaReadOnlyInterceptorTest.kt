package com.multify.traderpro.data.network

import org.junit.Assert.*
import org.junit.Test

class BetaReadOnlyInterceptorTest {
    @Test fun betaOnlyPermitsMarketDataAndExplicitAuthentication() {
        val isAllowed = BetaReadOnlyInterceptor::permits
        assertTrue(isAllowed("GET", "/v1/live-data/quote"))
        assertTrue(isAllowed("GET", "/v1/live-data/ltp"))
        assertTrue(isAllowed("GET", "/v1/historical/candles"))
        assertTrue(isAllowed("GET", "/v1/user/detail"))
        assertTrue(isAllowed("POST", "/v1/token/api/access"))
        listOf("/v1/order/create", "/v1/order/cancel", "/v1/order-advance/create",
            "/v1/order-advance/modify/123", "/v1/margins/detail/user",
            "/v1/holdings/user", "/v1/positions/user", "/v1/order-advance/list")
            .forEach { path ->
                assertFalse(isAllowed("GET", path))
                assertFalse(isAllowed("POST", path))
                assertFalse(isAllowed("PUT", path))
            }
        assertFalse(isAllowed("POST", "/v1/live-data/quote"))
    }
}
