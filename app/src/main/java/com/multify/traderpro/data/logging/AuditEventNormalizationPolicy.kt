package com.multify.traderpro.data.logging

internal data class NormalizedAuditEvent(
    val event: String,
    val details: Map<String, Any?>
)

/** Reclassifies one known deterministic eligibility filter without hiding real failures. */
internal object AuditEventNormalizationPolicy {
    fun normalize(category: String, event: String, details: Map<String, Any?>): NormalizedAuditEvent {
        if (category == "FIXED_RESEARCH" &&
            event == "EVALUATION_ERROR" &&
            details["message"] == "FIXED_PRICE_BAND_OUTSIDE"
        ) {
            return NormalizedAuditEvent(
                "EVALUATION_FILTERED_PRICE_BAND",
                details + mapOf(
                    "filter_reason" to "FIXED_PRICE_BAND_OUTSIDE",
                    "expected_eligibility_filter" to true
                )
            )
        }
        return NormalizedAuditEvent(event, details)
    }
}
