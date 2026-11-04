package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ResearchOutcomeChronologyPolicyTest {
    private fun fresh() = ResearchOutcomeChronology("FIXED|1000|ABC","FIXED","ABC",
        1000L,100.0,101.2,98.0)
    @Test fun stopFirstCannotBeRetroactivelyCountedAsTargetFirst() {
        val first = ResearchOutcomeChronologyPolicy.observe(fresh(), 97.5, 2000L, "LIVE_LTP")
        val later = ResearchOutcomeChronologyPolicy.observe(first, 102.0, 3000L, "DISCOVERY_CACHE")
        assertEquals("STOP_OBSERVED_FIRST", later.firstTouch)
        assertEquals(2000L, later.firstStopObservedAtMs)
        assertEquals(3000L, later.firstTargetObservedAtMs)
        assertEquals("SPARSE_SAMPLED_PRICES_ONLY_NOT_REALIZED_PNL", later.evidence)
    }
    @Test fun preservesTargetFirstAndRejectsOutOfOrderObservations() {
        val first = ResearchOutcomeChronologyPolicy.observe(fresh(), 102.0, 2000L, "LIVE_LTP")
        assertEquals("TARGET_OBSERVED_FIRST", first.firstTouch)
        assertEquals(first, ResearchOutcomeChronologyPolicy.observe(first, 97.0, 1900L, "LIVE_LTP"))
        assertEquals("TARGET_OBSERVED_FIRST",
            ResearchOutcomeChronologyPolicy.observe(first, 97.0, 3000L, "LIVE_LTP").firstTouch)
    }
    @Test fun tracksTrailingActivationAsObservedNotBrokerExecution() {
        val observed = ResearchOutcomeChronologyPolicy.observe(fresh(), 100.60, 2000L, "LIVE_LTP")
        assertEquals(2000L, observed.trailingArmSeenAtMs)
        assertEquals("UNRESOLVED", observed.firstTouch)
    }
}
