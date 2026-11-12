package com.multify.traderpro.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FixedTimeframeShadowDecisionPolicyTest {
    private fun ist(h: Int, m: Int): Long =
        LocalDate.of(2026, 11, 12).atTime(LocalTime.of(h, m))
            .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    private fun evaluate(time: Long, tier15: String = "PRIMARY",
                         tier5: String = "PRIMARY", bars: Int = 2,
                         spread: Double? = 12.0, extension: Double? = 1.0,
                         rsi: Double? = 63.0, slope: Double? = 2.0) =
        FixedTimeframeShadowDecisionPolicy.assess(
            time, tier15, tier5, bars, spread, extension, rsi, slope
        )

    @Test fun switchingBoundaryIsResearchOnly() {
        val before = evaluate(ist(9, 44))
        val after = evaluate(ist(9, 45))
        assertEquals("15M_REFERENCE", before.scheduledResearchReference)
        assertEquals("5M_RESEARCH_HYPOTHESIS", after.scheduledResearchReference)
        assertEquals("FROZEN_FIXED_15M", before.productionAuthority)
        assertEquals("FROZEN_FIXED_15M", after.productionAuthority)
        assertFalse(before.canAuthorizeTrading)
        assertFalse(after.canAuthorizeTrading)
    }

    @Test fun missingSpreadAndIncompleteBarsCannotBecomeBullishClaims() {
        assertEquals("UNKNOWN_SPREAD", evaluate(ist(10, 0), spread = null).researchLabel)
        assertEquals("MISSING_COMPLETED_5M", evaluate(ist(10, 0), bars = 0).researchLabel)
        assertEquals("SPREAD_RISK", evaluate(ist(10, 0), spread = 61.0).researchLabel)
    }

    @Test fun exhaustionBeatsTwoPrimaryConfirmationsInShadowOnly() {
        val result = evaluate(ist(10, 15), extension = 3.5, rsi = 81.0, slope = -1.0)
        assertEquals("EXHAUSTION_WATCH", result.researchLabel)
        assertEquals("NO_NEW_SHADOW_ENTRY_HYPOTHESIS", result.dynamicResearchReference)
        assertTrue(result.exhaustionHypothesis == true)
        assertFalse(result.canAuthorizeTrading)
    }

    @Test fun divergenceAndEarlyWatchAreNotNewFixedSignals() {
        val early = evaluate(ist(10, 15), tier15 = "RESEARCH")
        val divergent = evaluate(ist(10, 15), tier5 = "RESEARCH")
        assertEquals("EARLY_WATCH", early.researchLabel)
        assertEquals("DIVERGENCE_WATCH", divergent.researchLabel)
        assertFalse(early.canAuthorizeTrading)
        assertFalse(divergent.canAuthorizeTrading)
    }

    @Test fun offHoursNeverCreateActionableSignal() {
        val result = evaluate(ist(15, 40))
        assertEquals("OUT_OF_SESSION", result.researchLabel)
        assertFalse(result.canAuthorizeTrading)
    }
}
