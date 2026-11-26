package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class LiveNewsSentimentPolicyTest{
    @Test fun clearDirections(){
        assertEquals("POSITIVE",LiveNewsSentimentPolicy.classify("Net profit rose 28%","").sentiment)
        assertEquals("NEGATIVE",LiveNewsSentimentPolicy.classify("Net profit fell 28%","").sentiment)
        assertEquals("MIXED",LiveNewsSentimentPolicy.classify("Profit rose but revenue declined","").sentiment)
        assertEquals("NEUTRAL",LiveNewsSentimentPolicy.classify("Board meeting to consider results","").sentiment)
        assertEquals("MIXED",LiveNewsSentimentPolicy.classify("Contract awarded subject to approval","").sentiment)
    }
    @Test fun distinctCatalysts(){
        assertEquals(1,LiveNewsSentimentPolicy.cluster(5000000L,1,listOf("same" to 4900000L,"same" to 4800000L)))
        assertEquals(1.0,LiveNewsSentimentPolicy.directionalReturn("NEGATIVE",-1.0)!!,1e-9)
    }
}
