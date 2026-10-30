package com.multify.traderpro.data.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuotePayloadMappingTest {
    @Test fun growwTopOfBookAndDepthFieldsDeserializeForSpreadSafety() {
        val json = """
            {
              "last_price": 149.5,
              "bid_price": 150.0,
              "bid_quantity": 1000,
              "offer_price": 150.5,
              "offer_quantity": 2000,
              "depth": {
                "buy": [{"price": 150.0, "quantity": 1000}],
                "sell": [{"price": 150.5, "quantity": 2000}]
              }
            }
        """.trimIndent()
        val quote = Gson().fromJson(json, QuotePayload::class.java)
        assertEquals(150.0, quote.bidPrice!!, 1e-9)
        assertEquals(150.5, quote.offerPrice!!, 1e-9)
        assertNotNull(quote.depth)
        assertEquals(1, quote.depth!!.buy!!.size)
        assertEquals(1, quote.depth!!.sell!!.size)
    }
}
