package com.multify.traderpro.data.network

import com.multify.traderpro.engine.MarketDataPriorityAdmissionPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test

/** This does not simulate Groww or count actual wire requests. It verifies
 * that only authenticated, identical research GETs can share admission.
 */
class AlphaResearchAdmissionGateTest {
    private val cache = GrowwHotDataCacheInterceptor(
        account = "ALPHA", recordAlphaStats = false,
        elapsedMs = { System.nanoTime() / 1_000_000L }
    )
    private fun gate(waitMs: Long = 1_000L) = AlphaResearchAdmissionGate(cache, waitMs)
    private fun request(token: String, path: String = "/v1/historical/candles?candle_interval=15minute") =
        Request.Builder().url("https://api.groww.in$path")
            .header("Authorization", "Bearer $token").build()

    @Test fun oneLeaderForIdenticalAuthenticatedHistoricalResearch() = runBlocking {
        val g = gate()
        val request = request("alpha-one")
        val leader = g.enter(request)!!
        val follower = g.enter(request)!!
        assertTrue(leader.isLeader)
        assertFalse(follower.isLeader)
        assertEquals(1, g.activeFlights())
        g.finish(leader, true)
        assertTrue(g.awaitLeader(follower))
        assertEquals(0, g.activeFlights())
    }

    @Test fun tokenRotationAndEndpointParametersNeverCrossAccounts() {
        val g = gate()
        val a = g.enter(request("alpha-one"))!!
        val b = g.enter(request("alpha-two"))!!
        val c = g.enter(request("alpha-one", "/v1/historical/candles?candle_interval=5minute"))!!
        assertTrue(a.isLeader)
        assertTrue(b.isLeader)
        assertTrue(c.isLeader)
        assertEquals(3, g.activeFlights())
        listOf(a,b,c).forEach { g.finish(it,false) }
        assertEquals(0, g.activeFlights())
    }

    @Test fun protectionQuotesOrdersAndAccountReadsCannotBeCoalesced() {
        val g = gate()
        val protectedQuote = request("alpha-one", "/v1/live-data/quote?symbol=TEST")
            .newBuilder()
            .header(MarketDataPriorityAdmissionPolicy.HEADER, MarketDataPriorityAdmissionPolicy.PROTECTION)
            .build()
        assertNull(g.enter(protectedQuote))
        val order = Request.Builder().url("https://api.groww.in/v1/order/create")
            .post("{}".toByteArray().toRequestBody()).build()
        assertNull(g.enter(order))
        assertNull(g.enter(request("alpha-one", "/v1/positions/user")))
        assertEquals(0, g.activeFlights())
    }

    @Test fun failedAndTimedOutFlightsNeverBecomeSuccess() = runBlocking {
        val g = gate(10L)
        val leader = g.enter(request("alpha-one"))!!
        val follower = g.enter(request("alpha-one"))!!
        assertFalse(g.awaitLeader(follower))
        g.finish(leader,false)
        assertFalse(g.awaitLeader(follower))
        assertEquals(0, g.activeFlights())
    }
}
