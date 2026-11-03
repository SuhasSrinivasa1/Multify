package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastNewsOpportunityPolicyTest {
    private val p = ForecastNewsOpportunityPolicy
    private val proven = ForecastNewsOpportunityPolicy.Evidence(40, 8, .80, 1.45)
    private val notProven = ForecastNewsOpportunityPolicy.Evidence(19, 4, .95)

    private fun context(change: Double? = 0.2, spread: Double? = 18.0,
                        evidence: ForecastNewsOpportunityPolicy.Evidence = proven) =
        ForecastNewsOpportunityPolicy.Context(change, spread, 250.0, 120.0,
            12_000_000_000.0, 2, "ORDER_CONTRACT+PROFIT_MARGIN", 3, evidence)

    @Test fun trainedGoodNewsCanRecommendBeforeAnyPriceRise() {
        val result = p.decide(context(change = -0.4))
        assertTrue(result.eligible)
        assertEquals("VALIDATED_PRE_SPIKE_CATALYST", result.reason)
        assertEquals(10.0, result.materialityPct!!, 1e-6)
        assertTrue(result.patternKey.contains("MULTI"))
    }
    @Test fun validatedButMeanExactlyOnePercentIsNotActionable() {
        assertEquals("AVERAGE_GAIN_NOT_ABOVE_1PCT",
            p.decide(context(evidence = ForecastNewsOpportunityPolicy.Evidence(40,8,.80,1.0))).reason)
    }
    @Test fun highHistoricalPercentageWithoutEnoughCasesIsNotProven() {
        assertEquals("PATTERN_NOT_VALIDATED", p.decide(context(evidence = notProven)).reason)
    }
    @Test fun alreadyMovedOrWideSpreadAreNotRecommendations() {
        assertEquals("ALREADY_MOVED", p.decide(context(change = 2.01)).reason)
        assertEquals("SPREAD_UNAVAILABLE_OR_WIDE", p.decide(context(spread = 62.0)).reason)
        assertEquals("PRICE_REACTION_UNKNOWN", p.decide(context(change = null)).reason)
    }
    @Test fun missingMarketCapStaysUnknownNotZero() {
        assertNull(p.materialityPct(100.0, null))
        assertEquals("UNKNOWN", p.capBucket(null))
    }
    @Test fun fridayAfterCloseCarriesToMondayOpen() {
        val zone = ZoneId.of("Asia/Kolkata")
        val friday = LocalDateTime.of(2026,10,30,16,20).atZone(zone).toInstant().toEpochMilli()
        val monday = LocalDateTime.of(2026,11,2,9,17).atZone(zone).toInstant().toEpochMilli()
        assertTrue(p.isCarryForward(friday,monday))
        val duringFriday = LocalDateTime.of(2026,10,30,10,0).atZone(zone).toInstant().toEpochMilli()
        assertFalse(p.isCarryForward(duringFriday,monday))
    }
    @Test fun categorizationRemainsNseAndTwentyPlusCategories() {
        val p = ForecastNewsPatternPolicy.assess("Funding secured", "₹100 crore capital infusion secured",0,1,1,1,true)
        assertEquals("FUNDING",p.category)
        assertEquals(40,p.features.size)
        assertEquals(100.0,p.features["reported_amount_crore"])
    }
}
