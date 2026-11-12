package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedResearchLtpPacingPolicyTest {
    @Test fun `pacing does not delay protection by imposing research gap`() {
        assertEquals(950L,FixedResearchLtpPacingPolicy.effectiveGapMs(950L,true))
        assertEquals(1500L,FixedResearchLtpPacingPolicy.effectiveGapMs(950L,false))
        assertEquals(3200L,FixedResearchLtpPacingPolicy.effectiveGapMs(3200L,false))
    }
}
