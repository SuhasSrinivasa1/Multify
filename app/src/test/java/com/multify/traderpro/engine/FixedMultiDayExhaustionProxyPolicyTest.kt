package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedMultiDayExhaustionProxyPolicyTest {
    private val policy=FixedMultiDayExhaustionProxyPolicy

    private fun flagged(runupPct:Double, dayChange:Double,
        windowHighDistancePct:Double, vwapDistanceAtr:Double): Boolean =
        policy.risky(
            runupPct >= 1.50 && runupPct >= 1.1161043 * 1.25,
            dayChange >= 5.0, windowHighDistancePct, vwapDistanceAtr
        )

    @Test fun flagsThreeHistoricalAdverseExamplesUnderConservativeProxy() {
        // Derived from 2026-11-04 Fixed factor/recommendation diagnostics.
        // In-sample only: not proof of improved profitability.
        assertTrue(flagged(2.349756,10.912931,0.200612,19.549677)) // SFCTECH
        assertTrue(flagged(2.880461,6.78,0.093240,4.414623)) // IZMO
        assertTrue(flagged(5.462284,7.237317,0.0,7.797652)) // SGRL late session
    }

    @Test fun keepsThreeHistoricalTargetHitsOutsideThisGuard() {
        assertFalse(flagged(6.496786,17.472778,0.500465,4.304745)) // CNL
        assertFalse(flagged(4.393954,4.82,0.15,16.60)) // LODHA
        assertFalse(flagged(1.547487,11.18,0.48,2.62)) // FAZE3Q
    }

    @Test fun lateBreakoutGuardBlocksFourWayExtensionConfluence() {
        // November 9 v8.3.11 diagnostics; examples validate the guard wiring,
        // not out-of-sample profitability.
        assertTrue(policy.lateBreakoutRisk(0.0, 8.76, 5.08, 4.16)) // DHUNINV
        assertTrue(policy.lateBreakoutRisk(0.13, 5.81, 2.41, 1.25)) // EXIDEIND
        assertTrue(policy.lateBreakoutRisk(0.05, 6.90, 2.06, 1.95)) // TERASOFT
        assertTrue(policy.lateBreakoutRisk(0.338, 6.78, 5.02, 2.84)) // BORANA
        assertTrue(policy.lateBreakoutRisk(0.129, 6.69, 4.42, 2.75)) // FAIRCHEMOR
    }

    @Test fun lateBreakoutGuardDoesNotRejectMomentumAwayFromWindowHigh() {
        // AEGISLOG later reached its target; it was not near the multi-day window high.
        assertFalse(policy.lateBreakoutRisk(3.61, 3.30, 6.77, 6.05))
        // A near-window-high signal without sufficient impulse is not enough.
        assertFalse(policy.lateBreakoutRisk(0.20, 4.0, 3.0, 1.19))
    }

    @Test fun lateBreakoutGuardRequiresEveryEvidenceComponent() {
        assertFalse(policy.lateBreakoutRisk(null, 8.0, 5.0, 4.0))
        assertFalse(policy.lateBreakoutRisk(0.2, null, 5.0, 4.0))
        assertFalse(policy.lateBreakoutRisk(0.2, 8.0, null, 4.0))
        assertFalse(policy.lateBreakoutRisk(0.2, 8.0, 5.0, null))
        assertFalse(policy.lateBreakoutRisk(0.8, 8.0, 5.0, 4.0))
    }

    @Test fun missingWindowEvidenceCannotFabricatePeak() {
        assertFalse(policy.risky(true,true,null,12.0))
        assertFalse(policy.risky(true,true,0.0,null))
        assertFalse(policy.risky(true,true,Double.NaN,12.0))
        assertFalse(policy.risky(true,false,0.0,12.0))
        assertFalse(policy.risky(false,true,0.0,12.0))
    }
}
