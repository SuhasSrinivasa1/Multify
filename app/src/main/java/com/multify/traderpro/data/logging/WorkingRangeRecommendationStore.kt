package com.multify.traderpro.data.logging

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.WorkingRangeRecommendationDto
import com.multify.traderpro.engine.FixedQuoteMarketabilityPresentationPolicy
import com.multify.traderpro.engine.FixedStrategyEvaluation
import com.multify.traderpro.engine.WorkingRangeCandidatePolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Separate Working Range pilot journal.
 *
 * - Consumes only a Fixed evaluation/quote that has already been fetched.
 * - Adds ZERO Groww calls and never blocks the scan thread.
 * - Two consecutive matching scan evaluations are required.
 * - One confirmed Working Range event per symbol per IST day.
 * - Live BUY is authorized only by the new explicit Working Range ON activation timestamp.
 * - Order intent is persisted before Alpha submission; ambiguous outcomes are never resubmitted.
 */
class WorkingRangeRecommendationStore(context: Context) {
    private data class StoredRow(
        val dateIst: String,
        val symbol: String,
        var dto: WorkingRangeRecommendationDto,
        var firstQualifiedPrice: Double,
        var consecutivePasses: Int,
        var lastPassAtMs: Long
    )

    private val prefs = context.applicationContext.getSharedPreferences(
        "working_range_recommendations_v9021", Context.MODE_PRIVATE
    )
    // Historical v9.0.20 pilot evidence is kept for diagnostics, never migrated
    // into a newly live, actionable v9.0.21 order queue.
    private val legacyPilotPrefs = context.applicationContext.getSharedPreferences(
        "working_range_recommendations_v9020", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val lock = Any()
    private val droppedQueueItems = AtomicLong(0L)
    private val queue = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(256),
        { r -> Thread(r, "WorkingRangeEvidence").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } },
        { _, _ -> droppedQueueItems.incrementAndGet() }
    )
    private data class StoredRejection(
        val dateIst: String, val symbol: String, val observedAtMs: Long, val price: Double,
        val reasons: List<String>, val rsi: Double?, val rvol: Double?,
        val greenVolumeShare: Double?, val volumeAcceleration: Double?,
        val ema9: Double?, val ema20: Double?, val ema50: Double?,
        val targetRequirementAtr: Double?, val vwapExtensionAtr: Double?
    )
    private val rowType = object : TypeToken<MutableList<StoredRow>>() {}.type
    private val rejectionType = object : TypeToken<MutableList<StoredRejection>>() {}.type
    private val rejections: MutableList<StoredRejection> = runCatching {
        gson.fromJson<MutableList<StoredRejection>>(prefs.getString(KEY_REJECTIONS, "[]"), rejectionType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())
    @Volatile private var enabled: Boolean = false
    @Volatile private var generation: Long = 0L
    private val rows: MutableList<StoredRow> = runCatching {
        gson.fromJson<MutableList<StoredRow>>(prefs.getString(KEY_ROWS, "[]"), rowType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())

    fun setEnabled(value: Boolean, reason: String = "Working Range toggle changed") = synchronized(lock) {
        if (enabled != value) {
            enabled = value
            generation += 1L
            rows.filter { it.dto.state == "PENDING_CONFIRMATION" }.forEach { row ->
                row.dto = row.dto.copy(state = "PENDING_DROPPED", reason = reason)
            }
            persistLocked()
        }
    }

    /** Wait until every already-enqueued Working Range observation has been persisted. */
    fun flush(timeoutMs: Long = 5_000L): Boolean =
        runCatching { queue.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS); true }.getOrDefault(false)

    /** Durable one-shot order intent, keyed by the confirmed symbol/event timestamp. */
    fun reserveAutoBuy(symbol: String, confirmedAtMs: Long, reference: String, quantity: Int): Boolean =
        synchronized(lock) {
            val row = rows.firstOrNull {
                it.symbol.equals(symbol, true) && it.dto.confirmedAtMs == confirmedAtMs
            } ?: return@synchronized false
            if (!enabled || row.dto.state != "IN_RANGE" ||
                row.dto.autoBuyState != "NOT_ATTEMPTED" || reference.isBlank() || quantity <= 0)
                return@synchronized false
            row.dto = row.dto.copy(
                autoBuyEnabled = true, autoBuyState = "ORDER_INTENT_RESERVED",
                autoBuyReference = reference, autoBuyQuantity = quantity,
                reason = "Working Range confirmed · Alpha order intent reserved · awaiting broker result"
            )
            // A successful synchronous disk commit is mandatory before a POST.
            // If it fails, keep the in-memory intent RESERVED, refuse the BUY,
            // and reconcile manually. SharedPreferences.apply() is NOT durable
            // enough for crash-safe order-intent reservation.
            persistOrderIntentDurablyLocked()
        }

    fun markAutoBuyOutcome(
        symbol: String, confirmedAtMs: Long, state: String,
        orderId: String? = null, quantity: Int? = null, fillPrice: Double? = null,
        reason: String? = null
    ) = synchronized(lock) {
        val row = rows.firstOrNull {
            it.symbol.equals(symbol, true) && it.dto.confirmedAtMs == confirmedAtMs
        } ?: return@synchronized
        row.dto = row.dto.copy(
            autoBuyState = state,
            autoBuyOrderId = orderId ?: row.dto.autoBuyOrderId,
            autoBuyQuantity = quantity ?: row.dto.autoBuyQuantity,
            autoBuyFillPrice = fillPrice ?: row.dto.autoBuyFillPrice,
            autoBuyEnabled = true,
            reason = reason ?: row.dto.reason
        )
        persistLocked()
    }

    /** Include older-day unresolved orders: midnight must not erase a broker liability. */
    fun pendingAutoBuyIntents(dateIst: String? = null): List<WorkingRangeRecommendationDto> =
        synchronized(lock) {
            rows.filter { (dateIst == null || it.dateIst == dateIst) &&
                it.dto.autoBuyState == "ORDER_INTENT_RESERVED" }
                .map { it.dto }
        }

    fun offer(
        symbol: String,
        evaluation: FixedStrategyEvaluation,
        quote: QuotePayload?,
        quoteCapturedAtMs: Long,
        scanStartedAtMs: Long
    ) {
        val normalized = symbol.trim().uppercase(java.util.Locale.US)
        if (!enabled || normalized.isBlank() || quoteCapturedAtMs <= 0L) return
        val queuedGeneration = generation
        runCatching {
            queue.execute {
                if (!enabled || generation != queuedGeneration) return@execute
                runCatching {
                    observeOnWorker(normalized, evaluation, quote, quoteCapturedAtMs,
                        scanStartedAtMs, queuedGeneration)
                }
            }
        }.onFailure { droppedQueueItems.incrementAndGet() }
    }

    private fun observeOnWorker(
        symbol: String,
        evaluation: FixedStrategyEvaluation,
        quote: QuotePayload?,
        observedAtMs: Long,
        scanStartedAtMs: Long,
        queuedGeneration: Long
    ) {
        if (!enabled || generation != queuedGeneration) return
        val zone = ZoneId.of("Asia/Kolkata")
        val at = Instant.ofEpochMilli(observedAtMs).atZone(zone)
        val date = at.toLocalDate().toString()
        val minutesToClose = Duration.between(at.toLocalTime(), LocalTime.of(15, 30))
            .toMillis() / 60_000.0
        val f = evaluation.features
        val inputs = WorkingRangeCandidatePolicy.inputs(evaluation, minutesToClose)
        val decision = WorkingRangeCandidatePolicy.assess(inputs)
        val price = f.ltp.takeIf { it.isFinite() && it > 0.0 } ?: return
        val targetPct = WorkingRangeCandidatePolicy.TARGET_PCT
        val marketability = FixedQuoteMarketabilityPresentationPolicy.assess(
            quote, observedAtMs, System.currentTimeMillis()
        )
        val marketabilityState = when {
            marketability.state == "UPPER_BAND_NO_SELL_OFFER" -> marketability.state
            marketability.state == "NO_SELL_OFFER_OBSERVED" -> marketability.state
            marketability.state == "STALE_CLIENT_QUOTE" -> marketability.state
            marketability.state == "NO_RECENT_QUOTE" -> marketability.state
            f.spreadBps != null && f.spreadBps > 35.0 -> "WIDE_SPREAD"
            else -> marketability.state
        }
        val key = "$date|$symbol"
        synchronized(lock) {
            if (!enabled || generation != queuedGeneration) return
            val index = rows.indexOfFirst { it.dateIst == date && it.symbol == symbol }
            val existing = rows.getOrNull(index)
            if (existing != null && existing.dto.confirmedAtMs > 0L) {
                updateConfirmed(existing, decision.matches, decision.reasons, decision.emaAlignmentMode, price,
                    observedAtMs, marketabilityState, marketability, evaluation)
                persistLocked()
                return
            }
            if (!decision.matches) {
                rejections.add(StoredRejection(
                    dateIst = date, symbol = symbol, observedAtMs = observedAtMs, price = price,
                    reasons = decision.reasons, rsi = f.rsi14, rvol = f.rvol,
                    greenVolumeShare = f.greenVolumeShare, volumeAcceleration = f.volumeAcceleration,
                    ema9 = f.ema9, ema20 = f.ema20, ema50 = f.ema50,
                    targetRequirementAtr = decision.targetRequirementAtr,
                    vwapExtensionAtr = decision.vwapExtensionAtr
                ))
                while (rejections.size > 600) rejections.removeAt(0)
                persistRejectionsLocked()
                if (existing != null && existing.dto.state == "PENDING_CONFIRMATION") {
                    existing.dto = existing.dto.copy(
                        state = "PENDING_DROPPED",
                        lastObservedAtMs = observedAtMs,
                        lastPrice = price,
                        marketabilityState = marketabilityState,
                        reason = "Confirmation failed: " + decision.reasons.joinToString(", ")
                    )
                    persistLocked()
                }
                return
            }
            if (existing == null || existing.dto.state != "PENDING_CONFIRMATION") {
                val first = WorkingRangeRecommendationDto(
                    symbol = symbol,
                    scanStartedAtMs = scanStartedAtMs,
                    state = "PENDING_CONFIRMATION",
                    targetPct = targetPct,
                    firstQualifiedAtMs = observedAtMs,
                    lastObservedAtMs = observedAtMs,
                    lastPrice = price,
                    candidateAgeMinutes = 0.0,
                    driftFromFirstQualifiedPct = 0.0,
                    rsi = f.rsi14, rvol = f.rvol,
                    volumeAcceleration = f.volumeAcceleration,
                    greenVolumeShare = f.greenVolumeShare,
                    targetRequirementAtr = decision.targetRequirementAtr,
                    vwapExtensionAtr = decision.vwapExtensionAtr,
                    range52Position = f.range52Position,
                    spreadBps = f.spreadBps,
                    orderBookImbalance = f.orderBookImbalance,
                    marketabilityState = marketabilityState,
                    upperCircuitPrice = marketability.brokerUpperCircuit,
                    bestSellOffer = marketability.bestObservedOffer,
                    quoteClientReceiptAgeMs = marketability.clientReceiptAgeMs,
                    autoBuyEnabled = true,
                    emaAlignmentMode = decision.emaAlignmentMode,
                    reason = "Working Range v0.2 · first matching scan · confirmation 1/2 · Alpha Auto-Buy permission ON"
                )
                val row = StoredRow(date, symbol, first, price, 1, observedAtMs)
                if (index >= 0) rows[index] = row else rows.add(row)
                trimLocked()
                persistLocked()
                return
            }

            // Two matching observations from the SAME scan are not confirmation.
            // The v9.0.20 research stream could count both; live v9.0.21
            // requires distinct official scan generations before Alpha BUY.
            if (!com.multify.traderpro.engine.WorkingRangeDistinctScanPolicy.isIndependentSecondScan(
                    existing.dto.scanStartedAtMs, scanStartedAtMs,
                    existing.lastPassAtMs, observedAtMs)) return

            val ageMs = (observedAtMs - existing.dto.firstQualifiedAtMs).coerceAtLeast(0L)
            val ageMinutes = ageMs / 60_000.0
            val drift = if (existing.firstQualifiedPrice > 0.0)
                (price / existing.firstQualifiedPrice - 1.0) * 100.0 else Double.POSITIVE_INFINITY
            val gap = observedAtMs - existing.lastPassAtMs
            if (ageMinutes > WorkingRangeCandidatePolicy.MAX_CANDIDATE_AGE_MINUTES ||
                drift > WorkingRangeCandidatePolicy.MAX_DRIFT_FROM_FIRST_QUALIFIED_PCT ||
                gap < 0L || gap > WorkingRangeCandidatePolicy.MAX_CONFIRMATION_GAP_MS) {
                existing.dto = existing.dto.copy(
                    state = "PENDING_EXPIRED",
                    lastObservedAtMs = observedAtMs,
                    lastPrice = price,
                    candidateAgeMinutes = ageMinutes,
                    driftFromFirstQualifiedPct = drift,
                    marketabilityState = marketabilityState,
                    reason = when {
                        ageMinutes > WorkingRangeCandidatePolicy.MAX_CANDIDATE_AGE_MINUTES -> "Candidate age exceeded 36 minutes"
                        drift > WorkingRangeCandidatePolicy.MAX_DRIFT_FROM_FIRST_QUALIFIED_PCT -> "Price drift exceeded +1.60% from first Working Range observation"
                        else -> "Confirmation gap exceeded five minutes"
                    }
                )
                persistLocked()
                return
            }

            existing.consecutivePasses += 1
            existing.lastPassAtMs = observedAtMs
            val confirmed = existing.consecutivePasses >=
                WorkingRangeCandidatePolicy.REQUIRED_CONSECUTIVE_OBSERVATIONS
            val target = if (confirmed) price * (1.0 + targetPct / 100.0) else 0.0
            existing.dto = existing.dto.copy(
                state = if (confirmed) "IN_RANGE" else "PENDING_CONFIRMATION",
                entryPrice = if (confirmed) price else 0.0,
                targetPrice = target,
                targetPct = targetPct,
                confirmedAtMs = if (confirmed) observedAtMs else 0L,
                lastObservedAtMs = observedAtMs,
                lastPrice = price,
                candidateAgeMinutes = ageMinutes,
                driftFromFirstQualifiedPct = drift,
                rsi = f.rsi14, rvol = f.rvol,
                volumeAcceleration = f.volumeAcceleration,
                greenVolumeShare = f.greenVolumeShare,
                targetRequirementAtr = decision.targetRequirementAtr,
                vwapExtensionAtr = decision.vwapExtensionAtr,
                range52Position = f.range52Position,
                spreadBps = f.spreadBps,
                orderBookImbalance = f.orderBookImbalance,
                marketabilityState = marketabilityState,
                upperCircuitPrice = marketability.brokerUpperCircuit,
                bestSellOffer = marketability.bestObservedOffer,
                quoteClientReceiptAgeMs = marketability.clientReceiptAgeMs,
                autoBuyEnabled = true,
                emaAlignmentMode = decision.emaAlignmentMode,
                reason = if (confirmed)
                    "WORKING RANGE v0.2 · two consecutive evaluations · Alpha Auto-Buy preflight"
                else "Working Range v0.2 · confirmation 2/2 pending"
            )
            persistLocked()
        }
    }

    private fun updateConfirmed(
        row: StoredRow,
        stillMatches: Boolean,
        reasons: List<String>,
        emaAlignmentMode: String,
        price: Double,
        observedAtMs: Long,
        marketabilityState: String,
        marketability: FixedQuoteMarketabilityPresentationPolicy.Result,
        evaluation: FixedStrategyEvaluation
    ) {
        val f = evaluation.features
        val old = row.dto
        val move = (price / old.entryPrice - 1.0) * 100.0
        val mfe = max(old.maxFavourablePct, move)
        val mae = min(old.maxAdversePct, move)
        val targetAt = if (old.targetFirstTouchAtMs == 0L && price >= old.targetPrice)
            observedAtMs else old.targetFirstTouchAtMs
        // Research comparison uses the currently deployed -3% hard-stop level.
        // This is sampled LTP chronology, not a broker stop or intrabar path.
        val stopAt = if (old.stopFirstTouchAtMs == 0L && price <= old.entryPrice * 0.97)
            observedAtMs else old.stopFirstTouchAtMs
        val outcome = when {
            targetAt > 0L && stopAt == 0L -> "TARGET_HIT_SAMPLED"
            stopAt > 0L && targetAt == 0L -> "STOP_FIRST_SAMPLED"
            targetAt > 0L && stopAt > 0L && targetAt < stopAt -> "TARGET_FIRST_SAMPLED"
            targetAt > 0L && stopAt > 0L && stopAt < targetAt -> "STOP_FIRST_SAMPLED"
            targetAt > 0L && stopAt > 0L -> "SAME_SAMPLE_ORDER_UNRESOLVED"
            else -> "UNRESOLVED_CENSORED"
        }
        val terminal = targetAt > 0L || stopAt > 0L
        row.dto = old.copy(
            state = when {
                terminal -> outcome
                stillMatches -> "IN_RANGE"
                else -> "MOMENTUM_WEAKENING"
            },
            lastObservedAtMs = observedAtMs,
            lastPrice = price,
            candidateAgeMinutes = (observedAtMs - old.firstQualifiedAtMs).coerceAtLeast(0L) / 60_000.0,
            driftFromFirstQualifiedPct = if (row.firstQualifiedPrice > 0.0)
                (price / row.firstQualifiedPrice - 1.0) * 100.0 else old.driftFromFirstQualifiedPct,
            rsi = f.rsi14, rvol = f.rvol,
            volumeAcceleration = f.volumeAcceleration,
            greenVolumeShare = f.greenVolumeShare,
            spreadBps = f.spreadBps,
            orderBookImbalance = f.orderBookImbalance,
            marketabilityState = marketabilityState,
            upperCircuitPrice = marketability.brokerUpperCircuit,
            bestSellOffer = marketability.bestObservedOffer,
            quoteClientReceiptAgeMs = marketability.clientReceiptAgeMs,
            emaAlignmentMode = emaAlignmentMode,
            maxFavourablePct = mfe,
            maxAdversePct = mae,
            targetFirstTouchAtMs = targetAt,
            stopFirstTouchAtMs = stopAt,
            sampledOutcomeNote = outcome,
            reason = if (terminal) "Sampled LTP touched target/stop threshold; sparse observations cannot prove intrabar order"
                else if (stillMatches) "Working Range conditions still match"
                else "Momentum conditions weakened: " + reasons.joinToString(", ")
        )
    }

    fun recent(dateIst: String): List<WorkingRangeRecommendationDto> = synchronized(lock) {
        rows.filter { it.dateIst == dateIst &&
            it.dto.state !in setOf("PENDING_DROPPED", "PENDING_EXPIRED") }
            .sortedWith(compareByDescending<StoredRow> { it.dto.confirmedAtMs }
                .thenByDescending { it.dto.firstQualifiedAtMs })
            .map { it.dto }
    }

    fun export(): Map<String, Any?> {
        runCatching { queue.submit {}.get(5, TimeUnit.SECONDS) }
        return synchronized(lock) {
        mapOf(
            "schema" to "working_range_auto_buy_v9021",
            "toggle_enabled" to enabled,
            "enabled_setting_is_separate_from_fixed" to true,
            "working_range_on_authorizes_new_alpha_auto_buys" to true,
            "source" to "existing_fixed_15m_evaluations_and_existing_quotes",
            // Selection/confirmation reuses Fixed's existing data. Actual live
            // Alpha BUY, quote, cash, order-read and GTT calls ARE incremental
            // network requests and appear in the actual wire-request ledger.
            "extra_discovery_broker_requests" to 0,
            "working_range_live_autobuy_adds_alpha_execution_and_protection_calls" to true,
            "authoritative_wire_usage_file" to "wire_api_usage_snapshot.json",
            "consecutive_confirmations_required" to WorkingRangeCandidatePolicy.REQUIRED_CONSECUTIVE_OBSERVATIONS,
            "candidate_age_max_minutes" to WorkingRangeCandidatePolicy.MAX_CANDIDATE_AGE_MINUTES,
            "drift_max_pct_from_first_qualified_observation" to WorkingRangeCandidatePolicy.MAX_DRIFT_FROM_FIRST_QUALIFIED_PCT,
            "candidate_parameters" to mapOf(
                "rsi_min" to WorkingRangeCandidatePolicy.MIN_RSI,
                "rvol_min" to WorkingRangeCandidatePolicy.MIN_RVOL,
                "green_volume_share_min" to WorkingRangeCandidatePolicy.MIN_GREEN_VOLUME_SHARE,
                "volume_acceleration_min" to WorkingRangeCandidatePolicy.MIN_VOLUME_ACCELERATION,
                "volume_acceleration_max" to WorkingRangeCandidatePolicy.MAX_VOLUME_ACCELERATION,
                "ema9_gt_ema20_gt_ema50" to true,
                "ema20_ema50_near_alignment_tolerance_atr" to 0.0,
                "ema9_ema20_slopes_positive" to true,
                "macd_histogram_slope_positive" to true,
                "target_requirement_max_atr" to WorkingRangeCandidatePolicy.MAX_TARGET_ATR,
                "minutes_to_close_min" to WorkingRangeCandidatePolicy.MIN_MINUTES_TO_CLOSE,
                "vwap_extension_single_factor_veto" to false,
                "range52_position_single_factor_veto" to false,
                "rsi_upper_ceiling" to null,
                "spread_is_execution_marketability_not_technical_selection" to true
            ),
            "target_pct_unchanged_from_fixed_reference" to WorkingRangeCandidatePolicy.TARGET_PCT,
            "marketability_is_separate_from_technical_range" to true,
            "upper_circuit_or_no_offer_does_not_erase_candidate" to true,
            "auto_buy_enabled" to enabled,
            "auto_buy_requires_new_confirmation_after_activation" to true,
            "ambiguous_order_intent_never_resubmitted" to true,
            "validated_working_range_claim" to false,
            "retrospective_sample_has_known_false_positive_gpil" to true,
            "legacy_v9020_rows_json_research_only" to
                (legacyPilotPrefs.getString(KEY_ROWS, "[]") ?: "[]"),
            "legacy_pilot_rows_never_order_eligible" to true,
            "auto_buy_intent_write_is_synchronous_disk_commit" to true,
            "deferred_queue_observations" to droppedQueueItems.get(),
            "rows" to rows.map { mapOf("date_ist" to it.dateIst, "symbol" to it.symbol,
                "first_qualified_price" to it.firstQualifiedPrice,
                "consecutive_passes" to it.consecutivePasses, "last_pass_at_ms" to it.lastPassAtMs,
                "recommendation" to it.dto) },
            "rejected_candidate_observations" to rejections
        )
        }
    }

    private fun trimLocked() {
        if (rows.size > 400) rows.subList(0, rows.size - 400).clear()
    }

    private fun persistLocked() {
        prefs.edit().putString(KEY_ROWS, gson.toJson(rows)).apply()
    }

    private fun persistOrderIntentDurablyLocked(): Boolean {
        // Commit under the same ledger lock. Failure must fail closed.
        return prefs.edit().putString(KEY_ROWS, gson.toJson(rows)).commit()
    }

    private fun persistRejectionsLocked() {
        prefs.edit().putString(KEY_REJECTIONS, gson.toJson(rejections)).apply()
    }

    companion object {
        private const val KEY_ROWS = "rows_json"
        private const val KEY_REJECTIONS = "rejections_json"
    }
}
