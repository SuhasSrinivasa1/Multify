package com.multify.traderpro.data.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditEventNormalizationPolicyTest {
    @Test fun fixedPriceBandVetoIsAFilterNotAnEvaluationFailure() {
        val normalized = AuditEventNormalizationPolicy.normalize(
            "FIXED_RESEARCH", "EVALUATION_ERROR",
            mapOf("symbol" to "OUTSIDE", "message" to "FIXED_PRICE_BAND_OUTSIDE")
        )
        assertEquals("EVALUATION_FILTERED_PRICE_BAND", normalized.event)
        assertEquals(true, normalized.details["expected_eligibility_filter"])
        assertEquals("FIXED_PRICE_BAND_OUTSIDE", normalized.details["filter_reason"])
    }

    @Test fun realEvaluationErrorsKeepTheirOriginalEvent() {
        val normalized = AuditEventNormalizationPolicy.normalize(
            "FIXED_RESEARCH", "EVALUATION_ERROR",
            mapOf("symbol" to "BROKEN", "message" to "HTTP 500")
        )
        assertEquals("EVALUATION_ERROR", normalized.event)
        assertFalse(normalized.details.containsKey("expected_eligibility_filter"))
    }

    @Test fun unrelatedCategoriesAreNeverReclassified() {
        val normalized = AuditEventNormalizationPolicy.normalize(
            "FORECAST_RESEARCH", "EVALUATION_ERROR",
            mapOf("message" to "FIXED_PRICE_BAND_OUTSIDE")
        )
        assertEquals("EVALUATION_ERROR", normalized.event)
        assertTrue(normalized.details.isNotEmpty())
    }
}
