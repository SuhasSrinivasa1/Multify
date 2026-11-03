package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class GttProtectionReadinessPolicyTest {
    @Test fun allEnginesMustHaveBrokerVerifiedActiveProtection() {
        assertFalse(GttProtectionReadinessPolicy.verified("NONE", null, null))
        assertFalse(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", "gtt-1", null))
        assertFalse(GttProtectionReadinessPolicy.verified("TRAILING_GTT", "gtt-1", "COMPLETED"))
        assertFalse(GttProtectionReadinessPolicy.verified("TRAILING_GTT", "gtt-1", "TRIGGERED"))
        assertFalse(GttProtectionReadinessPolicy.verified("RECOVERY_GTT", "gtt-1", "ACTIVE"))
        assertTrue(GttProtectionReadinessPolicy.verified("HARD_STOP_GTT", "gtt-1", "ACTIVE"))
        assertTrue(GttProtectionReadinessPolicy.verified("TRAILING_GTT", "gtt-2", "OPEN"))
    }
}
