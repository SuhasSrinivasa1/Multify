package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedNotificationRiskLabelPolicyTest {
    @Test fun `displays caution without changing execution permission`() {
        val p = FixedNotificationRiskLabelPolicy
        assertEquals(p.Level.CAUTION, p.level(true, "FIXED ENTRY CAUTION: ALREADY_UP_5PCT"))
        assertEquals("Fixed BUY · CAUTION · GABRIEL",p.title(p.Level.CAUTION,"GABRIEL"))
        assertEquals(p.Level.RESEARCH_ONLY,p.level(false,"FIXED RESEARCH ONLY"))
        assertEquals(p.Level.BUY,p.level(true,"Production TREND"))
    }
}
