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
 * - Research observation is always on during the Indian Fixed scan, independent of the Auto-Buy toggle.
 * - Live BUY is authorized only by the separate explicit Working Range Auto-Buy ON activation timestamp.
 * - Turning Auto-Buy OFF never stops observations or discards an unconfirmed research candidate.
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
        val rsiSlope: Double? = null, val ema9Slope: Double? = null,
        val ema20Slope: Double? = null, val macdHistogramSlope: Double? = null,
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
    // This switch gates broker execution ONLY. The research observer is always enabled.
    @Volatile private var autoBuyEnabled: Boolean = false
    private val rows: MutableList<StoredRow> = runCatching {
        gson.fromJson<MutableList<StoredRow>>(prefs.getString(KEY_ROWS, "[]"), rowType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())
    private var evaluatedObservations = prefs.getLong(KEY_STATS_EVALUATED, 0L)
    private var qualifyingObservations = prefs.getLong(KEY_STATS_QUALIFYING, 0L)
    private var rejectedObservations = prefs.getLong(KEY_STATS_REJECTED, 0L)
    private var firstQualifiedEvents = prefs.getLong(KEY_STATS_FIRST_QUALIFIED, 0L)
    private var confirmedEvents = prefs.getLong(KEY_STATS_CONFIRMED, 0L)
    private var pendingDroppedEvents = prefs.getLong(KEY_STATS_PENDING_DROPPED, 0L)
    private var pendingExpiredEvents = prefs.getLong(KEY_STATS_PENDING_EXPIRED, 0L)
    private var observedScanGenerations = prefs.getLong(KEY_STATS_SCAN_GENERATIONS, 0L)
    private var lastObservationAtMs = prefs.getLong(KEY_STATS_LAST_OBSERVED, 0L)
    private var lastScanStartedAtMs = prefs.getLong(KEY_STATS_LAST_SCAN_STARTED, 0L)

    /**
     * Auto-Buy permission only. Do not change research observation, invalidate
     * queued samples, or drop pending confirmations when the operator toggles it.
     * The immutable activation timestamp separately excludes confirmations that
     * predate the most recent ON action.
     */
    fun setAutoBuyEnabled(value: Boolean) = synchronized(lock) {
        autoBuyEnabled = value
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
            if (!autoBuyEnabled || row.dto.state != "IN_RANGE" ||
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
        if (normalized.isBlank() || quoteCapturedAtMs <= 0L) return
        // Research is unconditional: no Auto-Buy toggle check at enqueue or execution.
        runCatching {
            queue.execute {
                runCatching {
                    observeOnWorker(normalized, evaluation, quote, quoteCapturedAtMs,
                        scanStartedAtMs)
                }
            }
        }.onFailure { droppedQueueItems.incrementAndGet() }
    }

    private fun observeOnWorker(
        symbol: String,
        evaluation: FixedStrategyEvaluation,
        quote: QuotePayload?,
        observedAtMs: Long,
        scanStartedAtMs: Long
    ) {
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
            evaluatedObservations++
            if (decision.matches) qualifyingObservations++ else rejectedObservations++
            lastObservationAtMs = maxOf(lastObservationAtMs, observedAtMs)
            if (scanStartedAtMs > lastScanStartedAtMs) {
                observedScanGenerations++
                lastScanStartedAtMs = scanStartedAtMs
            }
            val index = rows.indexOfFirst { it.dateIst == date && it.symbol == symbol }
            val existing = rows.getOrNull(index)
            if (existing != null && existing.dto.confirmedAtMs > 0L) {
                updateConfirmed(existing, decision.matches, decision.reasons, decision.emaAlignmentMode, price,
                    observedAtMs, marketabilityState, marketability, evaluation, scanStartedAtMs)
                persistLocked()
                return
            }
            if (!decision.matches) {
                rejections.add(StoredRejection(
                    dateIst = date, symbol = symbol, observedAtMs = observedAtMs, price = price,
                    reasons = decision.reasons, rsi = f.rsi14, rsiSlope = f.rsiSlope,
                    ema9Slope = f.ema9Slope, ema20Slope = f.ema20Slope,
                    macdHistogramSlope = f.macdHistogramSlope, rvol = f.rvol,
                    greenVolumeShare = f.greenVolumeShare, volumeAcceleration = f.volumeAcceleration,
                    ema9 = f.ema9, ema20 = f.ema20, ema50 = f.ema50,
                    targetRequirementAtr = decision.targetRequirementAtr,
                    vwapExtensionAtr = decision.vwapExtensionAtr
                ))
                while (rejections.size > 600) rejections.removeAt(0)
                persistRejectionsLocked()
                if (existing != null && existing.dto.state == "PENDING_CONFIRMATION") {
                    pendingDroppedEvents++
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
                firstQualifiedEvents++
                val first = WorkingRangeRecommendationDto(
                    symbol = symbol,
                    scanStartedAtMs = scanStartedAtMs,
                    state = "PENDING_CONFIRMATION",
                    targetPct = targetPct,
                    researchStopPct = com.multify.traderpro.engine.WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT,
                    firstQualifiedAtMs = observedAtMs,
                    firstQualifiedPrice = price,
                    lastObservedAtMs = observedAtMs,
                    lastPrice = price,
                    candidateAgeMinutes = 0.0,
                    driftFromFirstQualifiedPct = 0.0,
                    rsi = f.rsi14, rsiSlope = f.rsiSlope,
                    ema9Slope = f.ema9Slope, ema20Slope = f.ema20Slope,
                    macdHistogramSlope = f.macdHistogramSlope, rvol = f.rvol,
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
                    autoBuyEnabled = this@WorkingRangeRecommendationStore.autoBuyEnabled,
                    emaAlignmentMode = decision.emaAlignmentMode,
                    reason = "Working Range v0.3 · first matching scan · confirmation 1/2 · research observation"
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
                    existing.lastPassAtMs, observedAtMs)) {
                persistStatsLocked()
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
                pendingExpiredEvents++
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
            if (confirmed) confirmedEvents++
            existing.dto = existing.dto.copy(
                state = if (confirmed) "IN_RANGE" else "PENDING_CONFIRMATION",
                entryPrice = if (confirmed) price else 0.0,
                targetPrice = target,
                targetPct = targetPct,
                confirmedAtMs = if (confirmed) observedAtMs else 0L,
                firstQualifiedPrice = existing.firstQualifiedPrice,
                lastObservedAtMs = observedAtMs,
                lastPrice = price,
                candidateAgeMinutes = ageMinutes,
                driftFromFirstQualifiedPct = drift,
                rsi = f.rsi14, rsiSlope = f.rsiSlope,
                    ema9Slope = f.ema9Slope, ema20Slope = f.ema20Slope,
                    macdHistogramSlope = f.macdHistogramSlope, rvol = f.rvol,
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
                autoBuyEnabled = if (confirmed)
                    this@WorkingRangeRecommendationStore.autoBuyEnabled
                else existing.dto.autoBuyEnabled,
                emaAlignmentMode = decision.emaAlignmentMode,
                reason = if (confirmed)
                    "WORKING RANGE v0.3 · two independent scan evaluations · Auto-Buy eligibility checked separately"
                else "Working Range v0.3 · confirmation 2/2 pending"
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
        evaluation: FixedStrategyEvaluation,
        scanStartedAtMs: Long
    ) {
        val f = evaluation.features
        val old = row.dto
        val move = (price / old.entryPrice - 1.0) * 100.0
        val mfe = max(old.maxFavourablePct, move)
        val mae = min(old.maxAdversePct, move)
        val targetAt = if (old.targetFirstTouchAtMs == 0L && price >= old.targetPrice)
            observedAtMs else old.targetFirstTouchAtMs
        // Preserve the historical basis of pre-upgrade -3% research rows.
        // New events use the exact -1% Working Range broker-stop threshold.
        val stopAt = if (old.stopFirstTouchAtMs == 0L &&
            price <= old.entryPrice * (1.0 - old.researchStopPct / 100.0))
            observedAtMs else old.stopFirstTouchAtMs
        val plus050 = if (old.firstPlus050AtMs == 0L && price >= old.entryPrice * 1.005)
            observedAtMs else old.firstPlus050AtMs
        val plus055 = if (old.firstPlus055AtMs == 0L && price >= old.entryPrice * 1.0055)
            observedAtMs else old.firstPlus055AtMs
        val minus2 = if (old.shadowMinus2AtMs == 0L && price <= old.entryPrice * 0.98)
            observedAtMs else old.shadowMinus2AtMs
        val minus3 = if (old.shadowMinus3AtMs == 0L && price <= old.entryPrice * 0.97)
            observedAtMs else old.shadowMinus3AtMs
        // A second setup after a terminal result is recorded but NEVER executable.
        val previousTerminal = old.state in setOf(
            "TARGET_HIT_SAMPLED", "TARGET_FIRST_SAMPLED", "STOP_FIRST_SAMPLED")
        val newRepeatScan = previousTerminal && stillMatches &&
            scanStartedAtMs > old.scanStartedAtMs &&
            scanStartedAtMs > old.repeatShadowLastScanAtMs
        val withinGap = old.repeatShadowLastScanAtMs > 0L &&
            scanStartedAtMs - old.repeatShadowLastScanAtMs in
                1L..WorkingRangeCandidatePolicy.MAX_CONFIRMATION_GAP_MS
        val repeatCount = when {
            !newRepeatScan -> old.repeatShadowConsecutiveScans
            withinGap -> old.repeatShadowConsecutiveScans + 1
            else -> 1
        }
        val repeatFirst = if (newRepeatScan && !withinGap) scanStartedAtMs
            else old.repeatShadowFirstScanAtMs
        val repeatLast = if (newRepeatScan) scanStartedAtMs
            else old.repeatShadowLastScanAtMs
        val repeatConfirmed = if (repeatCount >= 2 && old.repeatShadowConfirmedAtMs == 0L)
            observedAtMs else old.repeatShadowConfirmedAtMs
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
            rsi = f.rsi14, rsiSlope = f.rsiSlope,
                    ema9Slope = f.ema9Slope, ema20Slope = f.ema20Slope,
                    macdHistogramSlope = f.macdHistogramSlope, rvol = f.rvol,
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
            firstPlus050AtMs = plus050,
            firstPlus055AtMs = plus055,
            shadowMinus2AtMs = minus2,
            shadowMinus3AtMs = minus3,
            repeatShadowFirstScanAtMs = repeatFirst,
            repeatShadowLastScanAtMs = repeatLast,
            repeatShadowConsecutiveScans = repeatCount,
            repeatShadowConfirmedAtMs = repeatConfirmed,
            targetFirstTouchAtMs = targetAt,
            stopFirstTouchAtMs = stopAt,
            sampledOutcomeNote = if (old.researchStopPct !=
                com.multify.traderpro.engine.WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT)
                "LEGACY_MINUS_3_RESEARCH_STOP_NOT_COMPARABLE_TO_LIVE_MINUS_1; REPLAY_REQUIRED"
                else outcome,
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
            // Backfill rows from v9.0.21/v9.0.22: the already-persisted
            // StoredRow firstQualifiedPrice is authoritative, not the mutable
            // latest research drift or a guessed quote.
            .map { it.dto.copy(firstQualifiedPrice = it.firstQualifiedPrice) }
    }

    fun export(): Map<String, Any?> {
        runCatching { queue.submit {}.get(5, TimeUnit.SECONDS) }
        return synchronized(lock) {
        val recentReasonCounts = rejections.flatMap { it.reasons }.groupingBy { it }.eachCount()
        mapOf(
            "schema" to "working_range_research_v9025",
            "research_observation_always_on" to true,
            "enabled_setting_is_separate_from_fixed" to true,
            "auto_buy_toggle_only_authorizes_new_alpha_auto_buys" to true,
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
            "new_event_stop_basis_pct" to com.multify.traderpro.engine.WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT,
            "legacy_minus3_rows_require_replay" to true,
            "research_milestones_plus050_plus055_minus2_minus3" to true,
            "repeat_same_day_signals_shadow_only" to true,
            "marketability_is_separate_from_technical_range" to true,
            "upper_circuit_or_no_offer_does_not_erase_candidate" to true,
            "auto_buy_enabled" to autoBuyEnabled,
            "auto_buy_requires_new_confirmation_after_activation" to true,
            "ambiguous_order_intent_never_resubmitted" to true,
            "validated_working_range_claim" to false,
            "retrospective_sample_has_known_false_positive_gpil" to true,
            "legacy_v9020_rows_json_research_only" to
                (legacyPilotPrefs.getString(KEY_ROWS, "[]") ?: "[]"),
            "legacy_pilot_rows_never_order_eligible" to true,
            "auto_buy_intent_write_is_synchronous_disk_commit" to true,
            "deferred_queue_observations" to droppedQueueItems.get(),
            "throughput" to mapOf(
                "candidate_evaluations_total" to evaluatedObservations,
                "qualifying_observations_total" to qualifyingObservations,
                "rejected_observations_total" to rejectedObservations,
                "first_qualification_events_total" to firstQualifiedEvents,
                "confirmed_events_total" to confirmedEvents,
                "pending_dropped_total" to pendingDroppedEvents,
                "pending_expired_total" to pendingExpiredEvents,
                "distinct_scan_generations_observed" to observedScanGenerations,
                "pending_confirmation_rows_current" to rows.count { it.dto.state == "PENDING_CONFIRMATION" },
                "confirmed_in_range_rows_current" to rows.count { it.dto.state == "IN_RANGE" },
                "retained_rejection_observations" to rejections.size,
                "recent_rejection_reason_counts" to recentReasonCounts,
                "recent_distinct_observation_dates" to (rows.map { it.dateIst } + rejections.map { it.dateIst }).distinct().sorted(),
                "last_observation_at_ms" to lastObservationAtMs,
                "last_scan_started_at_ms" to lastScanStartedAtMs,
                "queue_drops_current_process" to droppedQueueItems.get(),
                "research_observer_always_on" to true,
                "additional_groww_discovery_calls" to 0,
                "selection_or_confirmation_thresholds_changed" to false
            ),
            "rows" to rows.map { mapOf("date_ist" to it.dateIst, "symbol" to it.symbol,
                "first_qualified_price" to it.firstQualifiedPrice,
                "consecutive_passes" to it.consecutivePasses, "last_pass_at_ms" to it.lastPassAtMs,
                "recommendation" to it.dto) },
            "rejected_candidate_observations" to rejections
        )
        }
    }

    /** Restore only cumulative research counters from an exported JSON snapshot. Order intents/rows are never imported. */
    fun restorePortableStatsFromExport(json:String):Int=synchronized(lock) {
        val root=runCatching{com.google.gson.JsonParser.parseString(json).asJsonObject}.getOrNull() ?: return@synchronized 0
        val stats=root.getAsJsonObject("throughput") ?: return@synchronized 0
        fun merge(name:String,current:Long):Long {
            val incoming=stats.get(name)?.takeIf{it.isJsonPrimitive && it.asJsonPrimitive.isNumber}?.asLong ?: return current
            return max(current,incoming)
        }
        val before=listOf(evaluatedObservations,qualifyingObservations,rejectedObservations,
            firstQualifiedEvents,confirmedEvents,pendingDroppedEvents,pendingExpiredEvents,
            observedScanGenerations,lastObservationAtMs,lastScanStartedAtMs)
        evaluatedObservations=merge("candidate_evaluations_total",evaluatedObservations)
        qualifyingObservations=merge("qualifying_observations_total",qualifyingObservations)
        rejectedObservations=merge("rejected_observations_total",rejectedObservations)
        firstQualifiedEvents=merge("first_qualification_events_total",firstQualifiedEvents)
        confirmedEvents=merge("confirmed_events_total",confirmedEvents)
        pendingDroppedEvents=merge("pending_dropped_total",pendingDroppedEvents)
        pendingExpiredEvents=merge("pending_expired_total",pendingExpiredEvents)
        observedScanGenerations=merge("distinct_scan_generations_observed",observedScanGenerations)
        lastObservationAtMs=merge("last_observation_at_ms",lastObservationAtMs)
        lastScanStartedAtMs=merge("last_scan_started_at_ms",lastScanStartedAtMs)
        val after=listOf(evaluatedObservations,qualifyingObservations,rejectedObservations,
            firstQualifiedEvents,confirmedEvents,pendingDroppedEvents,pendingExpiredEvents,
            observedScanGenerations,lastObservationAtMs,lastScanStartedAtMs)
        val changed=before.zip(after).count{it.first!=it.second}
        if(changed>0)persistStatsLocked()
        changed
    }

    private fun trimLocked() {
        if (rows.size > 400) rows.subList(0, rows.size - 400).clear()
    }

    private fun persistLocked() {
        val editor = prefs.edit().putString(KEY_ROWS, gson.toJson(rows))
        appendStats(editor)
        editor.apply()
    }

    private fun persistOrderIntentDurablyLocked(): Boolean {
        // Commit under the same ledger lock. Failure must fail closed.
        return prefs.edit().putString(KEY_ROWS, gson.toJson(rows)).commit()
    }

    private fun persistRejectionsLocked() {
        val editor = prefs.edit().putString(KEY_REJECTIONS, gson.toJson(rejections))
        appendStats(editor)
        editor.apply()
    }

    private fun persistStatsLocked() {
        val editor = prefs.edit()
        appendStats(editor)
        editor.apply()
    }

    /** Batch counters into the same preference commit as row/rejection writes to avoid extra I/O per candidate. */
    private fun appendStats(editor: android.content.SharedPreferences.Editor) {
        editor
            .putLong(KEY_STATS_EVALUATED, evaluatedObservations)
            .putLong(KEY_STATS_QUALIFYING, qualifyingObservations)
            .putLong(KEY_STATS_REJECTED, rejectedObservations)
            .putLong(KEY_STATS_FIRST_QUALIFIED, firstQualifiedEvents)
            .putLong(KEY_STATS_CONFIRMED, confirmedEvents)
            .putLong(KEY_STATS_PENDING_DROPPED, pendingDroppedEvents)
            .putLong(KEY_STATS_PENDING_EXPIRED, pendingExpiredEvents)
            .putLong(KEY_STATS_SCAN_GENERATIONS, observedScanGenerations)
            .putLong(KEY_STATS_LAST_OBSERVED, lastObservationAtMs)
            .putLong(KEY_STATS_LAST_SCAN_STARTED, lastScanStartedAtMs)
    }

    companion object {
        private const val KEY_ROWS = "rows_json"
        private const val KEY_REJECTIONS = "rejections_json"
        private const val KEY_STATS_EVALUATED = "stats_evaluated_v9052"
        private const val KEY_STATS_QUALIFYING = "stats_qualifying_v9052"
        private const val KEY_STATS_REJECTED = "stats_rejected_v9052"
        private const val KEY_STATS_FIRST_QUALIFIED = "stats_first_qualified_v9052"
        private const val KEY_STATS_CONFIRMED = "stats_confirmed_v9052"
        private const val KEY_STATS_PENDING_DROPPED = "stats_pending_dropped_v9052"
        private const val KEY_STATS_PENDING_EXPIRED = "stats_pending_expired_v9052"
        private const val KEY_STATS_SCAN_GENERATIONS = "stats_scan_generations_v9052"
        private const val KEY_STATS_LAST_OBSERVED = "stats_last_observed_v9052"
        private const val KEY_STATS_LAST_SCAN_STARTED = "stats_last_scan_started_v9052"
    }
}
