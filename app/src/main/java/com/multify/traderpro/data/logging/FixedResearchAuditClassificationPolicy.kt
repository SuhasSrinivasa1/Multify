package com.multify.traderpro.data.logging

/** Diagnostic labels only. Does not control evaluation, scanning, or trading. */
internal object FixedResearchAuditClassificationPolicy {
    fun label(category: String, event: String, message: String?): String = when {
        category == "FIXED_RESEARCH" && event == "EVALUATION_ERROR" &&
            message == "FIXED_PRICE_BAND_OUTSIDE" -> "CANDIDATE_SKIPPED_PRICE_BAND"
        category == "FIXED_RESEARCH" && event == "EVALUATION_ERROR" &&
            message == "FIXED_INSUFFICIENT_CANDLES_DEFERRED_UNTIL_NEXT_15M_BAR" ->
                "CANDIDATE_DEFERRED_FOR_NEW_COMPLETE_CANDLE"
        else -> event
    }
}
