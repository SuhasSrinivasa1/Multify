package com.multify.traderpro.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GrowwHotDataHubTest {
    private fun hub(): GrowwHotDataCacheInterceptor = GrowwHotDataCacheInterceptor(
        account = "ALPHA", recordAlphaStats = false,
        elapsedMs = { System.nanoTime() / 1_000_000L })

    @Test fun concurrentIdenticalGetUsesSingleBrokerRequest() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    Thread.sleep(100)
                    return MockResponse().setResponseCode(200).setBody("""{"ltp":100}""")
                }
            }
            server.start()
            val client = OkHttpClient.Builder().addInterceptor(hub()).build()
            val request = Request.Builder().url(server.url("/v1/live-data/quote?trading_symbol=RELIANCE"))
                .header("Authorization", "Bearer alpha-one").build()
            val pool = Executors.newFixedThreadPool(12)
            val start = CountDownLatch(1)
            try {
                val futures = (1..12).map {
                    pool.submit<String> {
                        start.await()
                        client.newCall(request).execute().use { response ->
                            assertTrue(response.isSuccessful)
                            response.body!!.string()
                        }
                    }
                }
                start.countDown()
                val results = futures.map { it.get(8, TimeUnit.SECONDS) }
                assertEquals(1, results.distinct().size)
                assertEquals(1, server.requestCount)
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun differentAlphaAuthorizationCannotReuseCachedQuote() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("token1"))
            server.enqueue(MockResponse().setBody("token2"))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor(hub()).build()
            val url = server.url("/v1/live-data/quote?trading_symbol=RELIANCE")
            fun get(token: String): String = client.newCall(Request.Builder().url(url)
                .header("Authorization", "Bearer $token").build()).execute()
                .use { it.body!!.string() }
            assertEquals("token1", get("alpha-one"))
            assertEquals("token2", get("alpha-two"))
            assertEquals(2, server.requestCount)
        }
    }
}
