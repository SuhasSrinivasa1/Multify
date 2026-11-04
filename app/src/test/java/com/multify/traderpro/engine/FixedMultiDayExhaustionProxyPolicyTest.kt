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

    @Test fun missingWindowEvidenceCannotFabricatePeak() {
        assertFalse(policy.risky(true,true,null,12.0))
        assertFalse(policy.risky(true,true,0.0,null))
        assertFalse(policy.risky(true,true,Double.NaN,12.0))
        assertFalse(policy.risky(true,false,0.0,12.0))
        assertFalse(policy.risky(false,true,0.0,12.0))
    }
}
