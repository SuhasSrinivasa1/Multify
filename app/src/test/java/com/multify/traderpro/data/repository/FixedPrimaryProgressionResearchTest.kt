package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test

class FixedPrimaryProgressionResearchTest {
    private fun rec(id: Long, scan: Long, price: Double) = FixedRecommendationRecord(
        id = id, recommendationDate = "2026-11-18", rank = 1, symbol = "DEMO",
        entryPrice = price, targetPct = 1.1161043, targetPrice = price * 1.011161043,
        confidence = 0.8, score = 0.99, strategy = "Frozen",
        regime = "TREND", marketRegime = "BREADTH_NEUTRAL",
        championKey = "FROZEN", reason = "TEST", generatedAtMs = scan,
        lastPrice = price, lastObservedAtMs = scan
    )
    private fun obs(scan: Long, tier: String, px: Double) = FixedScanObservation(
        recommendationDate = "2026-11-18", scanAtMs = scan,
        symbol = "DEMO", qualityTier = tier, publishEligible = true,
        executionEligible = true, observedPrice = px
    )

    @Test fun reconstructsEarlierPrimaryWithoutEarlyWatch() {
        val report = FixedPrimaryProgressionResearch.export(
            listOf(obs(1000, "SECONDARY", 97.0), obs(2000, "PRIMARY", 98.0),
                obs(3000, "PRIMARY", 102.0), obs(3000, "PRIMARY", 102.0)),
            listOf(rec(11, 3000, 102.0), rec(22, 4000, 105.0))
        )
        val events = report["events"] as List<*>
        assertEquals(2, events.size)
        val first = events[0] as Map<*, *>
        assertEquals(2000L, first["first_qualified_primary_scan_at_ms"])
        assertEquals(2, first["independent_primary_scan_count_through_generation"])
        assertEquals(1000L, first["first_secondary_scan_at_ms"])
        assertEquals(11L, first["recommendation_event_id"])
        assertEquals(false, first["earliest_first_primary_missing"])
    }


    @Test fun repeatEventUsesFirstPrimaryAfterItsOwnReset() {
        val first = rec(11, 2000, 100.0)
        val repeat = rec(22, 5000, 104.0)
        val observations = listOf(
            obs(1000, "SECONDARY", 98.0),
            obs(2000, "PRIMARY", 100.0),
            obs(3000, "PRIMARY", 101.0),
            obs(3500, "RESEARCH", 99.0),
            obs(4000, "PRIMARY", 102.0),
            obs(5000, "PRIMARY", 104.0)
        )
        val report = FixedPrimaryProgressionResearch.export(observations, listOf(first, repeat))
        val events = report["events"] as List<*>
        val second = events[1] as Map<*, *>
        assertEquals(3500L, second["reset_observation_scan_at_ms"])
        assertEquals(4000L, second["first_qualified_primary_scan_at_ms"])
        assertEquals(102.0, second["first_qualified_primary_observed_price"])
        assertEquals(2, second["independent_primary_scan_count_through_generation"])
        assertEquals("AFTER_EXPLICIT_NON_PRIMARY_RESET", second["event_anchor_scope"])
    }

    @Test fun noFirstPrimaryIsNotInvented() {
        val report = FixedPrimaryProgressionResearch.export(
            listOf(obs(1000, "SECONDARY", 97.0)), listOf(rec(11, 3000, 102.0))
        )
        assertEquals(1, report["missing_first_primary_count"])
        val event = (report["events"] as List<*>).first() as Map<*, *>
        assertNull(event["first_qualified_primary_observed_price"])
    }
}
