package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedNotificationRiskLabelPolicyTest {
    @Test fun `displays caution without changing execution permission`() {
        val p = FixedNotificationRiskLabelPolicy
        assertEquals(FixedNotificationRiskLabelPolicy.Level.CAUTION, p.level(true, "FIXED ENTRY CAUTION: ALREADY_UP_5PCT"))
        assertEquals("Fixed BUY · CAUTION · GABRIEL",p.title(FixedNotificationRiskLabelPolicy.Level.CAUTION,"GABRIEL"))
        assertEquals(FixedNotificationRiskLabelPolicy.Level.RESEARCH_ONLY,p.level(false,"FIXED RESEARCH ONLY"))
        assertEquals(FixedNotificationRiskLabelPolicy.Level.BUY,p.level(true,"Production TREND"))
    }
}
