package com.multify.traderpro.engine

/**
 * Transport/execution-only timestamp provenance. The frozen Fixed recommendation
 * generatedAtMs remains SCAN START for selection and PRIMARY confirmation.
 * No entry is allowed merely because a late scan just finished: source data
 * AND publication must be within the original five-minute execution horizon.
 */
object FixedAutoBuyPublicationEvidencePolicy {
    data class Evidence(
        val recommendationId: Long,
        val symbol: String,
        val scanStartedAtMs: Long,
        val sourceQuoteReceivedAtMs: Long,
        val publishedAtMs: Long
    )
    data class Decision(val eligible: Boolean, val reason: String, val expiresAtMs: Long)

    fun decide(
        evidence: Evidence?, recommendationId: Long, symbol: String,
        frozenScanStartedAtMs: Long, activatedAtMs: Long, nowMs: Long
    ): Decision {
        if (evidence == null) return Decision(false, "PUBLICATION_PROVENANCE_UNAVAILABLE", 0L)
        if (evidence.recommendationId != recommendationId ||
            !evidence.symbol.equals(symbol, true) ||
            evidence.scanStartedAtMs != frozenScanStartedAtMs) {
            return Decision(false, "RECOMMENDATION_ID_OR_SCAN_MISMATCH", 0L)
        }
        if (!AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(
                frozenScanStartedAtMs, activatedAtMs)) {
            return Decision(false, "PRE_ACTIVATION_RECOMMENDATION", 0L)
        }
        val source = evidence.sourceQuoteReceivedAtMs
        val publication = evidence.publishedAtMs
        if (source < frozenScanStartedAtMs || publication < source ||
            nowMs < publication || source <= 0L) {
            return Decision(false, "INVALID_OR_FUTURE_SOURCE_EVIDENCE", 0L)
        }
        val expiry = minOf(
            source + AutoBuyEntrySafetyPolicy.FIXED_AUTO_BUY_MAX_AGE_MS,
            publication + AutoBuyEntrySafetyPolicy.FIXED_AUTO_BUY_MAX_AGE_MS
        )
        return if (nowMs <= expiry)
            Decision(true, "EVIDENCE_FRESH_REQUIRES_ALPHA_EXECUTION_QUOTE", expiry)
        else Decision(false, "SOURCE_QUOTE_OR_PUBLICATION_EXPIRED", expiry)
    }
}

/** One service-instance journal; process restart fails closed for old events. */
class FixedAutoBuyPublicationEvidenceLedger {
    private val rows = LinkedHashMap<Long, FixedAutoBuyPublicationEvidencePolicy.Evidence>()

    @Synchronized
    fun record(evidence: FixedAutoBuyPublicationEvidencePolicy.Evidence) {
        rows[evidence.recommendationId] = evidence
        while (rows.size > 128) rows.remove(rows.keys.first())
    }

    @Synchronized
    fun find(id: Long): FixedAutoBuyPublicationEvidencePolicy.Evidence? = rows[id]

    @Synchronized
    fun clear() = rows.clear()
}
