package com.multify.traderpro.data.logging

import org.junit.Assert.*
import org.junit.Test

class FixedResearchAuditClassificationPolicyTest {
    @Test fun `separates expected no-data skips from genuine evaluation errors`() {
        val p = FixedResearchAuditClassificationPolicy
        assertEquals("CANDIDATE_SKIPPED_PRICE_BAND",
            p.label("FIXED_RESEARCH", "EVALUATION_ERROR", "FIXED_PRICE_BAND_OUTSIDE"))
        assertEquals("CANDIDATE_DEFERRED_FOR_NEW_COMPLETE_CANDLE",
            p.label("FIXED_RESEARCH", "EVALUATION_ERROR",
                "FIXED_INSUFFICIENT_CANDLES_DEFERRED_UNTIL_NEXT_15M_BAR"))
        assertEquals("EVALUATION_ERROR",
            p.label("FIXED_RESEARCH", "EVALUATION_ERROR", "broker unavailable"))
        assertEquals("EVALUATION_ERROR",
            p.label("FORECAST", "EVALUATION_ERROR", "FIXED_PRICE_BAND_OUTSIDE"))
    }
}
