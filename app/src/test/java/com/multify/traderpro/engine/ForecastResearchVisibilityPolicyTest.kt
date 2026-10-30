package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class ForecastResearchVisibilityPolicyTest {
    @Test fun firstSightPrimaryCanBePublishedResearchOnly() {
        assertTrue(ForecastPublicationPolicy.isReady(true,true,false,0,researchOnly=true))
        assertTrue(ForecastPublicationPolicy.isReady(true,true,true,0,researchOnly=true))
        assertFalse(ForecastPublicationPolicy.isReady(false,true,false,0,researchOnly=true))
        assertFalse(ForecastPublicationPolicy.isReady(true,false,false,0,researchOnly=true))
    }
    @Test fun liveFallbackRetainsOriginalRepeatConfirmation() {
        assertFalse(ForecastPublicationPolicy.isReady(true,true,false,0))
        assertTrue(ForecastPublicationPolicy.isReady(true,true,false,1))
        assertTrue(ForecastPublicationPolicy.isReady(true,true,true,0))
    }
    @Test fun FixedRetainsOriginalRepeatConfirmation() {
        assertFalse(FixedForecastPublicationPolicy.isReady(true,true,true,0))
        assertTrue(FixedForecastPublicationPolicy.isReady(true,true,true,1))
        assertFalse(FixedForecastPublicationPolicy.isReady(true,true,false,1))
        assertTrue(FixedForecastPublicationPolicy.isReady(true,true,false,2))
    }
}
