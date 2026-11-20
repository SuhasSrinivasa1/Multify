package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedLateEntrySafetyPolicyTest {
    private val published = 1_000_000L
    private val source = 980_000L
    private fun risk(
        vwap: Double?, targetAtr: Double?, high: Double?,
        now: Long = published + 10_000L,
        sourceName: String = "TIMESTAMP_VERIFIED_TODAY_15M_CANDLES"
    ) = FixedLateEntrySafetyPolicy.assess(
        now, published, source, vwap, targetAtr, high, sourceName
    )

    @Test fun replayJashBlocksExecutionWithoutErasingPrimaryRecommendation() {
        val decision = risk(7.385774695910335, 2.5113861891470823, -0.9664582148948093)
        assertFalse(decision.executable)
        assertTrue(decision.peakRisk)
        assertEquals("FIXED_EXTENDED_PEAK_TARGET_CAPACITY_RISK", decision.reason)
    }

    @Test fun replayMpiManipalBlocksLateExtremeExtension() {
        assertFalse(risk(5.5443710403155695, 2.305024790205478, -0.5799373040752422).executable)
    }

    @Test fun replayExideIndAndEarlierOalRemainEligibleOnThisGuard() {
        assertTrue(risk(1.684194126582212, 1.3135353460770116, .7892).executable)
        assertTrue(risk(1.525402708058455, .4464286711688608, .0613).executable)
    }

    @Test fun strongMomentumAloneDoesNotProveExhaustion() {
        assertTrue(risk(5.2724382876251354, .7085263790246443, -2.82).executable)
        assertTrue(risk(7.4, 2.7, null).executable)
        assertTrue(risk(7.4, 2.7, -.3, sourceName = "UNAVAILABLE").executable)
    }

    @Test fun staleActualPublicationAndMissingTimingFailClosed() {
        assertEquals("FIXED_POST_PUBLICATION_ENTRY_EXPIRED",
            risk(1.0, 1.0, 3.0, published + 90_001L).reason)
        assertFalse(FixedLateEntrySafetyPolicy.assess(
            published + 10L, null, source, null, null, null, "UNAVAILABLE").executable)
    }

    @Test fun timeUsesActualPublicationNotFrozenScanStart() {
        val within = risk(1.0, 1.0, 5.0, now = published + 89_999L)
        assertTrue(within.executable)
        assertEquals(89_999L, within.publicationAgeMs)
    }
}
