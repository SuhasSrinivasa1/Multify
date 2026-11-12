package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedSampledOutcomeResearchPolicyTest {
    @Test fun `young calls do not dilute mature measured hit rates`() {
        val first = FixedSampledRecommendation("a","ABC",1_000_000L,100.0,1.1161043030113955)
        val young = FixedSampledRecommendation("b","XYZ",2_000_000L,100.0,1.1161043030113955)
        val report = FixedSampledOutcomeResearchPolicy.report(
            listOf(first,young),
            mapOf("a" to listOf(
                FixedSampledQuote(1_000_010L,150.0), // suspicious sample before scan+90s, ignored
                FixedSampledQuote(1_100_000L,101.2))),
            2_100_000L
        )
        @Suppress("UNCHECKED_CAST")
        val horizons = report["horizons"] as List<Map<String, Any?>>
        val fifteen = horizons.first()
        assertEquals(1, fifteen["matured_events"])
        assertEquals(1, fifteen["immature_events_excluded"])
        @Suppress("UNCHECKED_CAST")
        val all = fifteen["all"] as Map<String, Any?>
        assertEquals(1, all["full_target_hits"])
        assertEquals(100.0, all["full_target_rate_pct"])
    }

    @Test fun `missing later samples are excluded instead of called failures`() {
        val r = FixedSampledRecommendation("a","ABC",1_000_000L,100.0,1.116)
        val report = FixedSampledOutcomeResearchPolicy.report(listOf(r), emptyMap(), 5_000_000L)
        @Suppress("UNCHECKED_CAST")
        val horizons = report["horizons"] as List<Map<String, Any?>>
        assertEquals(1, horizons.first()["matured_with_no_valid_sample"])
        @Suppress("UNCHECKED_CAST")
        val all = horizons.first()["all"] as Map<String, Any?>
        assertNull(all["full_target_rate_pct"])
    }
}
