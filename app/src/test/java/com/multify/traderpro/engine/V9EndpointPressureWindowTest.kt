package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class V9EndpointPressureWindowTest {
    @Test fun distinguishesEndpointFromCategoryAndCountsOnlyAdmittedCalls() {
        val w = V9EndpointPressureWindow()
        val a = w.admitted("GET","/v1/live-data/quote","LIVE_DATA",100_000)
        assertEquals(1,a.endpoint1s)
        assertEquals(1,a.category60s)
        assertTrue(a.emitMinuteSample)
        val b = w.admitted("GET","/v1/live-data/ltp","LIVE_DATA",100_500)
        assertEquals(1,b.endpoint60s)
        assertEquals(2,b.category1s)
        assertEquals(2,b.alphaAll60s)
        val c = w.admitted("GET","/v1/live-data/quote","LIVE_DATA",101_500)
        assertEquals(2,c.endpoint60s)
        assertEquals(1,c.endpoint1s)
        assertFalse(c.emitMinuteSample)
    }
    @Test fun expiresAfterMinuteWithoutFakingMinuteQuota() {
        val w = V9EndpointPressureWindow()
        w.admitted("GET","/v1/live-data/quote","LIVE_DATA",20_000)
        val next = w.admitted("GET","/v1/live-data/quote","LIVE_DATA",80_001)
        assertEquals(1,next.endpoint60s)
        assertTrue(next.emitMinuteSample)
    }
}
