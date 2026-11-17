package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class UnivestAutoBuyPolicyTest {
    private val entry = ForecastUnivestLearningPolicy.Type.ENTRY
    private val exit = ForecastUnivestLearningPolicy.Type.EXIT

    @Test fun fixedToggleControlsUnivestAndRejectsOlderAlerts() {
        assertNull(UnivestAutoBuyPolicy.rejectionReason(entry, 5_000L, 5_100L, true, 4_000L))
        assertEquals("FIXED_AUTO_BUY_OFF", UnivestAutoBuyPolicy.rejectionReason(entry, 5_000L, 5_100L, false, 4_000L))
        assertEquals("NO_CURRENT_FIXED_TOGGLE_ACTIVATION", UnivestAutoBuyPolicy.rejectionReason(entry, 5_000L, 5_100L, true, 0L))
        assertEquals("ALERT_PREDATES_FIXED_TOGGLE", UnivestAutoBuyPolicy.rejectionReason(entry, 4_000L, 5_100L, true, 4_000L))
        assertEquals("ALERT_PREDATES_FIXED_TOGGLE", UnivestAutoBuyPolicy.rejectionReason(entry, 3_000L, 5_100L, true, 4_000L))
    }

    @Test fun onlyFreshOfficialParsedEntriesMayProceed() {
        assertNull(UnivestAutoBuyPolicy.rejectionReason(entry, 10_001L, 130_001L, true, 10_000L))
        assertEquals("STALE_UNIVEST_NOTIFICATION", UnivestAutoBuyPolicy.rejectionReason(entry, 10_001L, 130_002L, true, 10_000L))
        assertEquals("INVALID_NOTIFICATION_TIME", UnivestAutoBuyPolicy.rejectionReason(entry, 10_001L, 9_999L, true, 10_000L))
        assertEquals("NOT_UNIVEST_ENTRY", UnivestAutoBuyPolicy.rejectionReason(exit, 10_001L, 10_010L, true, 10_000L))
        assertNull(ForecastUnivestLearningPolicy.parse("com.fake.univest", "New Advisory Pick\nSTOCK: ABC"))
    }
}
