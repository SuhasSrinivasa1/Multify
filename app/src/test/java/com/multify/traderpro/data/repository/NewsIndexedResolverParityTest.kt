package com.multify.traderpro.data.repository
import org.junit.Assert.*
import org.junit.Test
class NewsIndexedResolverParityTest {
    @Test fun matchesOriginalAnchoringAndAmbiguity(){
        val c=mapOf("Alpha Steel Limited" to "ALPHA","Alpha Steel Power Limited" to "POWER","Beta Motors Ltd" to "BETA")
        val idx=ForecastNewsSymbolResolutionPolicy.Index(c)
        for(s in listOf("Alpha Steel Limited has informed exchange","Alpha Steel Power Ltd announces result","Beta Motors Ltd intimates meeting","News about Beta Motors Ltd","unrelated")){
            assertEquals(ForecastNewsSymbolResolutionPolicy.resolve(s,c),idx.resolve(s))
        }
    }
}
