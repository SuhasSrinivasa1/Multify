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
 * - No automatic BUY, no GTT, no SELL, and no changes to Fixed rows.
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
    private val rowType = object : TypeToken<MutableList<StoredRow>>() {}.type
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
                updateConfirmed(existing, decision.matches, decision.reasons, price,
                    observedAtMs, marketabilityState, marketability, evaluation)
                persistLocked()
                return
            }
            if (!decision.matches) {
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
                    reason = "Working Range v0.1 · first matching scan · confirmation 1/2 · manual review only"
                )
                val row = StoredRow(date, symbol, first, price, 1, observedAtMs)
                if (index >= 0) rows[index] = row else rows.add(row)
                trimLocked()
                persistLocked()
                return
            }

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
                reason = if (confirmed)
                    "WORKING RANGE v0.1 · two consecutive 15m-evaluation sweeps · manual review only · no Auto-Buy"
                else "Working Range v0.1 · confirmation 2/2 pending"
            )
            persistLocked()
        }
    }

    private fun updateConfirmed(
        row: StoredRow,
        stillMatches: Boolean,
        reasons: List<String>,
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
            "schema" to "working_range_pilot_v9020",
            "toggle_enabled" to enabled,
            "enabled_setting_is_separate_from_fixed" to true,
            "source" to "existing_fixed_15m_evaluations_and_existing_quotes",
            "extra_broker_requests" to 0,
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
            "auto_buy_enabled" to false,
            "validated_working_range_claim" to false,
            "retrospective_sample_has_known_false_positive_gpil" to true,
            "deferred_queue_observations" to droppedQueueItems.get(),
            "rows" to rows.map { mapOf("date_ist" to it.dateIst, "symbol" to it.symbol,
                "first_qualified_price" to it.firstQualifiedPrice,
                "consecutive_passes" to it.consecutivePasses, "last_pass_at_ms" to it.lastPassAtMs,
                "recommendation" to it.dto) }
        )
        }
    }

    private fun trimLocked() {
        if (rows.size > 400) rows.subList(0, rows.size - 400).clear()
    }

    private fun persistLocked() {
        prefs.edit().putString(KEY_ROWS, gson.toJson(rows)).apply()
    }

    companion object { private const val KEY_ROWS = "rows_json" }
}
