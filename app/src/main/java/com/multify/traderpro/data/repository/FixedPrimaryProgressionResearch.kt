package com.multify.traderpro.data.repository

/**
 * Reconstructs the earliest *persisted scan-start* tier observations using the
 * source's actual recommendation EVENT IDs. This intentionally does not infer
 * exchange ticks, execution prices, broker fills or actual publication times.
 *
 * A previous Early Watch capture could be empty when asynchronous quote
 * associations were lost. Tier observations are independent and survive that
 * failure, providing a lower-fidelity but honest chronology for every cohort.
 */
internal object FixedPrimaryProgressionResearch {
    internal fun export(
        observations: List<FixedScanObservation>,
        recommendations: List<FixedRecommendationRecord>
    ): Map<String, Any?> {
        val distinct = observations.distinctBy {
            listOf(it.recommendationDate, it.scanAtMs.toString(), it.symbol.uppercase(),
                it.qualityTier).joinToString("|")
        }
        val indexed = distinct.groupBy {
            it.recommendationDate to it.symbol.uppercase()
        }.mapValues { (_, rows) -> rows.sortedBy { it.scanAtMs } }
        val orderedRecommendations = recommendations.sortedWith(
            compareBy<FixedRecommendationRecord> { it.recommendationDate }
                .thenBy { it.symbol.uppercase() }.thenBy { it.generatedAtMs }
        )
        val rows = orderedRecommendations.map { recommendation ->
            val key = recommendation.recommendationDate to recommendation.symbol.uppercase()
            val symbolObservations = indexed[key].orEmpty()
            val series = symbolObservations.filter { it.scanAtMs <= recommendation.generatedAtMs }
            val previousAt = orderedRecommendations.asSequence().filter {
                it.recommendationDate == recommendation.recommendationDate &&
                    it.symbol.equals(recommendation.symbol, true) &&
                    it.generatedAtMs < recommendation.generatedAtMs
            }.maxOfOrNull { it.generatedAtMs }
            // A repeat event starts a new evidence cohort after the latest explicit
            // non-PRIMARY reset following the previous event. Never reuse the
            // symbol's first PRIMARY from an earlier setup.
            val resetAt = previousAt?.let {
                com.multify.traderpro.engine.FixedRepeatQualificationPolicy.resetAt(
                    recommendation.symbol, it, symbolObservations)
            }
            val eventSeries = when {
                previousAt == null -> series
                resetAt != null -> series.filter { it.scanAtMs >= resetAt }
                else -> emptyList()
            }
            val firstAny = eventSeries.firstOrNull()
            val primary = eventSeries.filter {
                it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible &&
                    it.observedPrice?.isFinite() == true && (it.observedPrice ?: 0.0) > 0.0
            }.distinctBy { it.scanAtMs }
            val secondary = eventSeries.firstOrNull { it.qualityTier == "SECONDARY" }
            val first = primary.firstOrNull()
            val firstPrice = first?.observedPrice?.takeIf { it.isFinite() && it > 0.0 }
            val drift = firstPrice?.let {
                if (recommendation.entryPrice > 0.0)
                    (recommendation.entryPrice / it - 1.0) * 100.0 else null
            }
            mapOf(
                "recommendation_event_id" to recommendation.id,
                "symbol" to recommendation.symbol,
                "date_ist" to recommendation.recommendationDate,
                "frozen_scan_start_at_ms" to recommendation.generatedAtMs,
                "actual_publication_at_ms" to null,
                "actual_publication_time_in_system_audit" to
                    "PUBLISHED_RECOMMENDATION_STUDY_V9011",
                "previous_event_scan_start_at_ms" to previousAt,
                "reset_observation_scan_at_ms" to resetAt,
                "event_anchor_scope" to if (previousAt == null)
                    "FIRST_SETUP_OF_SESSION" else "AFTER_EXPLICIT_NON_PRIMARY_RESET",
                "first_any_tier_scan_at_ms" to firstAny?.scanAtMs,
                "first_any_tier" to firstAny?.qualityTier,
                "first_secondary_scan_at_ms" to secondary?.scanAtMs,
                "first_secondary_price" to secondary?.observedPrice,
                "first_qualified_primary_scan_at_ms" to first?.scanAtMs,
                "first_qualified_primary_observed_price" to firstPrice,
                "independent_primary_scan_count_through_generation" to primary.size,
                "scan_start_to_generation_ms" to first?.let {
                    recommendation.generatedAtMs - it.scanAtMs
                },
                "first_primary_to_recommendation_reference_pct" to drift,
                "first_primary_quote_exchange_time_verified" to false,
                "recommendation_reference_price" to recommendation.entryPrice,
                "reference_target_pct" to recommendation.targetPct,
                "recommendation_status_at_export" to recommendation.status,
                "earliest_first_primary_missing" to (first == null),
                "cohort_required_no_publication_veto" to true,
                "observation_series" to eventSeries.map {
                    mapOf(
                        "scan_start_at_ms" to it.scanAtMs, "tier" to it.qualityTier,
                        "observed_price" to it.observedPrice,
                        "publish_eligible" to it.publishEligible,
                        "execution_eligible" to it.executionEligible
                    )
                }
            )
        }
        return mapOf(
            "schema" to "fixed_primary_progression_v9018",
            "source" to "PERSISTED_FIXED_ALL_TIER_OBSERVATIONS",
            "trade_or_fixed_selection_effect" to false,
            "new_broker_requests" to 0,
            "scan_start_is_not_quote_receipt_or_exchange_timestamp" to true,
            "actual_publication_joins_via_existing_system_audit" to true,
            "one_row_per_independent_recommendation_event" to true,
            "publication_event_count" to rows.size,
            "missing_first_primary_count" to rows.count { it["earliest_first_primary_missing"] == true },
            "events" to rows
        )
    }
}
