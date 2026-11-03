package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEvidencePolicyTest {
    @Test fun capturesGoodAndBadOutcomesNotOnlyWinners() {
        val p = DecisionEvidencePolicy
        assertTrue(p.shouldCapture("FORECAST_UNIVEST", "SAME_DAY_EXIT_OUTCOME"))
        assertTrue(p.shouldCapture("MULTIFY_RESEARCH", "OFFICIAL_SAME_DAY_FACTOR_LEARNING"))
        assertTrue(p.shouldCapture("FIXED_ENGINE", "LONG_SCAN_TIERED"))
        assertTrue(p.shouldCapture("FIXED_OUTCOME", "TARGET_HIT"))
        assertTrue(p.shouldCapture("LEARNING", "MULTIFY_RETURN_RECORDED"))
        assertTrue(p.shouldCapture("GTT", "PROTECTIVE_GTT_RATCHET_FAILED"))
        assertTrue(p.shouldCapture("GTT", "TRAIL_GTT_RATCHETED"))
        assertTrue(p.shouldCapture("SAFETY", "NEW_BUY_BLOCKED_UNPROTECTED_HOLDING"))
        assertTrue(p.shouldCapture("EXECUTION", "SELL_PAUSED_SELLABLE_QUANTITY_MISMATCH"))
        assertTrue(p.shouldCapture("FORECAST_AUTO_BUY", "ENTRY_MARKET_FILLED"))
    }

    @Test fun excludesRepeatedPollingAndSensitiveInformation() {
        val p = DecisionEvidencePolicy
        assertFalse(p.shouldCapture("API_BUDGET", "ROLLING_USAGE"))
        assertFalse(p.shouldCapture("RUNTIME", "TRAILING_MONITOR_PROFILE"))
        assertFalse(p.shouldCapture("FIXED_DISCOVERY", "MARKET_WIDE_SCAN"))
        assertFalse(p.allowedDetailKey("access_token"))
        assertFalse(p.allowedDetailKey("totp"))
        assertFalse(p.allowedDetailKey("api_key"))
        assertFalse(p.allowedDetailKey("notification_body"))
        assertTrue(p.allowedDetailKey("symbol"))
        assertTrue(p.allowedDetailKey("features"))
        assertTrue(p.allowedDetailKey("exit_price"))
    }

    @Test fun labelsRejectionsAndEvidenceWithoutTreatingItAsTrade() {
        val p = DecisionEvidencePolicy
        assertEquals("REJECTED_OR_ERROR", p.classification("GTT","PROTECTIVE_GTT_RATCHET_FAILED"))
        assertEquals("RESEARCH_EVIDENCE", p.classification("FORECAST_UNIVEST","SAME_DAY_EXIT_OUTCOME"))
        assertEquals("DECISION", p.classification("FIXED_ENGINE","LONG_SCAN_TIERED"))
    }
}
