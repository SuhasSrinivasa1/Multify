package com.multify.traderpro.data.logging

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.WorkingRangeRecommendationDto
import com.multify.traderpro.engine.WorkingRangeCandidatePolicy
import com.multify.traderpro.engine.WorkingRangeExitComparisonPolicy
import com.multify.traderpro.engine.WorkingRangeExitPolicy
import com.multify.traderpro.engine.WorkingRangeMarketSnapshot
import com.multify.traderpro.engine.WorkingRangeMarketabilityPolicy
import com.multify.traderpro.engine.WorkingRangeOpeningMomentumPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Independent Working Range evidence and order-intent journal.
 *
 * - Candidate rules do not consume Fixed scores, FixedFinalContract or Univest signals.
 * - Reuses quote/history objects already fetched by the common scan transport.
 * - Adds zero discovery, quote or historical broker requests.
 * - Opening momentum uses already-received live quote/volume samples before 09:45 IST;
 *   it does not wait for the first completed same-day 15m candle.
 * - Two independent scan generations are still required for confirmation.
 * - One executable confirmation per symbol per IST day; later re-qualification after
 *   a broker-confirmed close is recorded as research-only and cannot create a BUY.
 * - Alpha remains the only execution/protection account; Beta remains read-only.
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

    private data class StoredSample(
        val dateIst: String,
        val symbol: String,
        val observedAtMs: Long,
        val price: Double,
        val cumulativeVolume: Long?
    )

    private data class StoredRejection(
        val dateIst: String,
        val symbol: String,
        val observedAtMs: Long,
        val price: Double,
        val reasons: List<String>,
        val rsi: Double?,
        val rsiSlope: Double?,
        val rvol: Double?,
        val greenVolumeShare: Double?,
        val volumeAcceleration: Double?,
        val ema9: Double?,
        val ema20: Double?,
        val ema50: Double?,
        val ema9Slope: Double?,
        val ema20Slope: Double?,
        val macdHistogramSlope: Double?,
        val openingMode: Boolean,
        val openingSampleCount: Int,
        val openingMomentumPct: Double?,
        val openingVolumePaceRvol: Double?,
        val targetRequirementAtr: Double?,
        val vwapExtensionAtr: Double?
    )

    private data class StoredReentry(
        val dateIst: String,
        val symbol: String,
        val priorConfirmedAtMs: Long,
        val priorPositionClosedAtMs: Long,
        val priorClosePrice: Double?,
        val priorCloseReason: String?,
        val requalifiedAtMs: Long,
        val requalifiedPrice: Double,
        val reasons: List<String>,
        val rsi: Double?,
        val rsiSlope: Double?,
        val rvol: Double?,
        val openingMomentumPct: Double?,
        val researchOnly: Boolean = true,
        val newBuyAllowed: Boolean = false
    )

    private val prefs = context.applicationContext.getSharedPreferences(
        "working_range_recommendations_v9021", Context.MODE_PRIVATE
    )
    // Historical v9.0.20 pilot evidence remains diagnostic-only and cannot enter the live queue.
    private val legacyPilotPrefs = context.applicationContext.getSharedPreferences(
        "working_range_recommendations_v9020", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val lock = Any()
    private val droppedQueueItems = AtomicLong(0L)
    @Volatile private var lastFlushedDroppedCount = 0L
    @Volatile private var lastQueueDropAtMs = prefs.getLong(KEY_QUEUE_DROP_AT_MS, 0L)
    @Volatile private var lastDropHandledAtMs = prefs.getLong(KEY_DROP_HANDLED_AT_MS, 0L)
    private val queue = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(256),
        { r -> Thread(r, "WorkingRangeEvidence").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } },
        { _, _ -> recordQueueDrop() }
    )
    private val rowType = object : TypeToken<MutableList<StoredRow>>() {}.type
    private val rejectionType = object : TypeToken<MutableList<StoredRejection>>() {}.type
    private val sampleType = object : TypeToken<MutableList<StoredSample>>() {}.type
    private val reentryType = object : TypeToken<MutableList<StoredReentry>>() {}.type
    private val rows: MutableList<StoredRow> = runCatching {
        gson.fromJson<MutableList<StoredRow>>(prefs.getString(KEY_ROWS, "[]"), rowType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())
    private val rejections: MutableList<StoredRejection> = runCatching {
        gson.fromJson<MutableList<StoredRejection>>(prefs.getString(KEY_REJECTIONS, "[]"), rejectionType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())
    private val samples: MutableList<StoredSample> = runCatching {
        gson.fromJson<MutableList<StoredSample>>(prefs.getString(KEY_SAMPLES, "[]"), sampleType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())
    private val reentries: MutableList<StoredReentry> = runCatching {
        gson.fromJson<MutableList<StoredReentry>>(prefs.getString(KEY_REENTRIES, "[]"), reentryType)
            ?: mutableListOf()
    }.getOrDefault(mutableListOf())

    // This switch gates broker execution ONLY. The research observer is always enabled.
    @Volatile private var autoBuyEnabled: Boolean = false

    fun setAutoBuyEnabled(value: Boolean) = synchronized(lock) {
        autoBuyEnabled = value
    }

    /** Wait until all already-enqueued evidence writes have completed. */
    fun flush(timeoutMs: Long = 5_000L): Boolean {
        val completed = runCatching {
            queue.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        }.getOrDefault(false)
        val droppedNow = droppedQueueItems.get()
        val newDropCount = droppedNow > lastFlushedDroppedCount
        val unhandledDrop = lastQueueDropAtMs > lastDropHandledAtMs
        if (unhandledDrop) {
            synchronized(lock) {
                val dropAt = lastQueueDropAtMs
                rows.forEach { row ->
                    val firstQualificationPredatesGap = row.dto.firstQualifiedAtMs > 0L &&
                        row.dto.firstQualifiedAtMs <= dropAt
                    if (row.dto.state == "PENDING_CONFIRMATION" && firstQualificationPredatesGap) {
                        row.dto = row.dto.copy(
                            state = "PENDING_DROPPED",
                            autoBuyState = "BLOCKED_EVIDENCE_GAP",
                            reason = "A dropped scan observation invalidated this confirmation chain; a fresh first qualification is required"
                        )
                    } else if (row.dto.confirmedAtMs > 0L && firstQualificationPredatesGap &&
                        row.dto.autoBuyState == "NOT_ATTEMPTED") {
                        row.dto = row.dto.copy(
                            autoBuyState = "BLOCKED_EVIDENCE_GAP",
                            reason = "A dropped scan observation invalidated this prior confirmation; no BUY permitted"
                        )
                    }
                }
                persistLocked()
                lastDropHandledAtMs = dropAt
                prefs.edit().putLong(KEY_DROP_HANDLED_AT_MS, dropAt).apply()
            }
        }
        lastFlushedDroppedCount = droppedNow
        return completed && !newDropCount && !unhandledDrop
    }

    private fun recordQueueDrop() {
        droppedQueueItems.incrementAndGet()
        synchronized(lock) {
            val at = max(System.currentTimeMillis(), lastQueueDropAtMs + 1L)
            lastQueueDropAtMs = at
            prefs.edit().putLong(KEY_QUEUE_DROP_AT_MS, at).apply()
        }
    }

    /** Durable one-shot intent, keyed by the confirmed symbol/event timestamp. */
    fun reserveAutoBuy(symbol: String, confirmedAtMs: Long, reference: String, quantity: Int): Boolean =
        synchronized(lock) {
            val row = rows.firstOrNull {
                it.symbol.equals(symbol, true) && it.dto.confirmedAtMs == confirmedAtMs
            } ?: return@synchronized false
            if (!autoBuyEnabled || row.dto.state != "IN_RANGE" ||
                row.dto.autoBuyState != "NOT_ATTEMPTED" || reference.isBlank() || quantity <= 0)
                return@synchronized false
            row.dto = row.dto.copy(
                autoBuyEnabled = true,
                autoBuyState = "ORDER_INTENT_RESERVED",
                autoBuyReference = reference,
                autoBuyQuantity = quantity,
                reason = "Working Range confirmed · Alpha order intent reserved · awaiting broker result"
            )
            // A synchronous disk commit is mandatory before any BUY POST.
            persistOrderIntentDurablyLocked()
        }

    fun markAutoBuyOutcome(
        symbol: String,
        confirmedAtMs: Long,
        state: String,
        orderId: String? = null,
        quantity: Int? = null,
        fillPrice: Double? = null,
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
            autoBuyFillAtMs = if (fillPrice != null) System.currentTimeMillis() else row.dto.autoBuyFillAtMs,
            firstQualifiedToFillRunupPct = fillPrice?.takeIf { row.firstQualifiedPrice > 0.0 }?.let {
                (it / row.firstQualifiedPrice - 1.0) * 100.0
            } ?: row.dto.firstQualifiedToFillRunupPct,
            confirmationToFillDriftPct = fillPrice?.takeIf { row.dto.entryPrice > 0.0 }?.let {
                (it / row.dto.entryPrice - 1.0) * 100.0
            } ?: row.dto.confirmationToFillDriftPct,
            // Do not infer that the operator's toggle is ON from an execution outcome.
            reason = reason ?: row.dto.reason
        )
        persistLocked()
    }

    /** Called only after a broker-confirmed close; later re-qualification remains research-only. */
    fun markPositionClosed(
        symbol: String,
        reference: String,
        closedAtMs: Long,
        closePrice: Double?,
        closeReason: String
    ) = synchronized(lock) {
        val row = rows.firstOrNull {
            it.symbol.equals(symbol, true) &&
                (it.dto.autoBuyReference == reference || reference.isBlank() &&
                    it.dto.autoBuyReference.isBlank() && it.dto.confirmedAtMs > 0L &&
                    it.dto.autoBuyState == "BUY_EXECUTED")
        } ?: return@synchronized
        row.dto = row.dto.copy(
            state = "POSITION_CLOSED",
            autoBuyState = "POSITION_CLOSED",
            positionClosedAtMs = closedAtMs,
            positionClosePrice = closePrice,
            positionCloseReason = closeReason,
            reason = "Broker-confirmed Working Range position close · repeat qualification is research-only"
        )
        persistLocked()
    }

    fun pendingAutoBuyIntents(dateIst: String? = null): List<WorkingRangeRecommendationDto> =
        synchronized(lock) {
            rows.filter { (dateIst == null || it.dateIst == dateIst) &&
                it.dto.autoBuyState == "ORDER_INTENT_RESERVED" }
                .map { it.dto }
        }

    internal fun offer(
        symbol: String,
        snapshot: WorkingRangeMarketSnapshot,
        quote: QuotePayload,
        quoteCapturedAtMs: Long,
        scanStartedAtMs: Long
    ) {
        val normalized = symbol.trim().uppercase(Locale.US)
        if (normalized.isBlank() || quoteCapturedAtMs <= 0L) return
        // The supplied quote and candle snapshot already exist for the current market scan.
        runCatching {
            queue.execute {
                runCatching {
                    observeOnWorker(normalized, snapshot, quote, quoteCapturedAtMs, scanStartedAtMs)
                }.onFailure { recordQueueDrop() }
            }
        }.onFailure { recordQueueDrop() }
    }

    private fun observeOnWorker(
        symbol: String,
        snapshot: WorkingRangeMarketSnapshot,
        quote: QuotePayload,
        observedAtMs: Long,
        scanStartedAtMs: Long
    ) {
        val zone = ZoneId.of("Asia/Kolkata")
        val at = Instant.ofEpochMilli(observedAtMs).atZone(zone)
        val date = at.toLocalDate().toString()
        val price = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: snapshot.price.takeIf { it.isFinite() && it > 0.0 } ?: return
        val sampleHistory = recordSampleAndRead(date, symbol, observedAtMs, price, quote.volume)
        val inputs = WorkingRangeOpeningMomentumPolicy.buildInputs(
            snapshot, quote, observedAtMs, sampleHistory
        )
        val decision = WorkingRangeCandidatePolicy.assess(inputs)
        val targetPct = WorkingRangeCandidatePolicy.TARGET_PCT
        val marketability = WorkingRangeMarketabilityPolicy.assess(
            quote, observedAtMs, System.currentTimeMillis()
        )
        val marketabilityState = when {
            marketability.state == "UPPER_BAND_NO_SELL_OFFER" -> marketability.state
            marketability.state == "NO_SELL_OFFER_OBSERVED" -> marketability.state
            marketability.state == "STALE_CLIENT_QUOTE" -> marketability.state
            marketability.state == "NO_RECENT_QUOTE" -> marketability.state
            inputs.spreadBps != null && inputs.spreadBps > 35.0 -> "WIDE_SPREAD"
            else -> marketability.state
        }

        synchronized(lock) {
            val index = rows.indexOfFirst { it.dateIst == date && it.symbol == symbol }
            val existing = rows.getOrNull(index)

            // One real app-owned Working Range trade per symbol/day. A later matching
            // pattern is logged separately and can never become a second executable event.
            if (existing != null && existing.dto.state == "POSITION_CLOSED") {
                if (decision.matches) {
                    reentries.add(StoredReentry(
                        dateIst = date,
                        symbol = symbol,
                        priorConfirmedAtMs = existing.dto.confirmedAtMs,
                        priorPositionClosedAtMs = existing.dto.positionClosedAtMs,
                        priorClosePrice = existing.dto.positionClosePrice,
                        priorCloseReason = existing.dto.positionCloseReason,
                        requalifiedAtMs = observedAtMs,
                        requalifiedPrice = price,
                        reasons = decision.reasons,
                        rsi = inputs.rsi,
                        rsiSlope = inputs.rsiSlope,
                        rvol = inputs.rvol,
                        openingMomentumPct = inputs.openingMomentumPct
                    ))
                    while (reentries.size > 300) reentries.removeAt(0)
                    persistReentriesLocked()
                }
                return
            }

            if (existing != null && existing.dto.confirmedAtMs > 0L) {
                updateConfirmed(
                    existing, decision.matches, decision.reasons, decision.emaAlignmentMode,
                    price, observedAtMs, marketabilityState, marketability, inputs
                )
                persistLocked()
                return
            }

            if (!decision.matches) {
                rejections.add(StoredRejection(
                    dateIst = date,
                    symbol = symbol,
                    observedAtMs = observedAtMs,
                    price = price,
                    reasons = decision.reasons,
                    rsi = inputs.rsi,
                    rsiSlope = inputs.rsiSlope,
                    rvol = inputs.rvol,
                    greenVolumeShare = inputs.greenVolumeShare,
                    volumeAcceleration = inputs.volumeAcceleration,
                    ema9 = inputs.ema9,
                    ema20 = inputs.ema20,
                    ema50 = inputs.ema50,
                    ema9Slope = inputs.ema9Slope,
                    ema20Slope = inputs.ema20Slope,
                    macdHistogramSlope = inputs.macdHistogramSlope,
                    openingMode = inputs.openingMode,
                    openingSampleCount = inputs.openingSampleCount,
                    openingMomentumPct = inputs.openingMomentumPct,
                    openingVolumePaceRvol = inputs.openingVolumePaceRvol,
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
                } else if (shouldShowOpeningWatch(inputs)) {
                    val watch = openingWatchDto(
                        symbol, scanStartedAtMs, observedAtMs, price, targetPct,
                        inputs, decision, marketabilityState, marketability
                    )
                    val row = StoredRow(date, symbol, watch, price, 0, observedAtMs)
                    if (index >= 0) rows[index] = row else rows.add(row)
                    trimLocked()
                    persistLocked()
                } else if (existing != null && existing.dto.state == "OPENING_WATCH") {
                    existing.dto = existing.dto.copy(
                        state = "OPENING_WATCH_DROPPED",
                        lastObservedAtMs = observedAtMs,
                        lastPrice = price,
                        openingMomentumPct = inputs.openingMomentumPct,
                        openingSampleCount = inputs.openingSampleCount,
                        reason = "Opening watch weakened or no longer meets its provisional context; no BUY was eligible"
                    )
                    persistLocked()
                }
                return
            }

            if (existing == null || existing.dto.state != "PENDING_CONFIRMATION") {
                val first = recommendationDto(
                    symbol, scanStartedAtMs, "PENDING_CONFIRMATION", price, 0.0,
                    targetPct, observedAtMs, 0L, price, inputs, decision,
                    marketabilityState, marketability,
                    autoBuyEnabled, "Working Range · first matching independent scan · confirmation 1/2"
                ).copy(lastObservedAtMs = observedAtMs, lastPrice = price)
                val row = StoredRow(date, symbol, first, price, 1, observedAtMs)
                if (index >= 0) rows[index] = row else rows.add(row)
                trimLocked()
                persistLocked()
                return
            }

            // Two matching observations from the SAME scan do not confirm a candidate.
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
                        ageMinutes > WorkingRangeCandidatePolicy.MAX_CANDIDATE_AGE_MINUTES ->
                            "Candidate age exceeded 36 minutes"
                        drift > WorkingRangeCandidatePolicy.MAX_DRIFT_FROM_FIRST_QUALIFIED_PCT ->
                            "Price drift exceeded +1.60% from first Working Range observation"
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
            existing.dto = recommendationDto(
                symbol = symbol,
                scanStartedAtMs = existing.dto.scanStartedAtMs,
                state = if (confirmed) "IN_RANGE" else "PENDING_CONFIRMATION",
                entryPrice = if (confirmed) price else 0.0,
                targetPrice = target,
                targetPct = targetPct,
                firstQualifiedAtMs = existing.dto.firstQualifiedAtMs,
                confirmedAtMs = if (confirmed) observedAtMs else 0L,
                firstQualifiedPrice = existing.firstQualifiedPrice,
                inputs = inputs,
                decision = decision,
                marketabilityState = marketabilityState,
                marketability = marketability,
                autoBuyEnabled = if (confirmed) autoBuyEnabled else existing.dto.autoBuyEnabled,
                reason = if (confirmed)
                    "WORKING RANGE · two independent scan evaluations · Alpha execution eligibility checked separately"
                else "Working Range · confirmation 2/2 pending"
            ).copy(
                lastObservedAtMs = observedAtMs,
                lastPrice = price,
                candidateAgeMinutes = ageMinutes,
                driftFromFirstQualifiedPct = drift
            )
            persistLocked()
        }
    }

    private fun shouldShowOpeningWatch(inputs: WorkingRangeCandidatePolicy.Inputs): Boolean {
        if (!inputs.openingMode || inputs.openingSampleCount < 2) return false
        if ((inputs.openingMomentumPct ?: Double.NEGATIVE_INFINITY) < 0.10) return false
        if ((inputs.rsi ?: Double.NEGATIVE_INFINITY) < WorkingRangeCandidatePolicy.MIN_RSI) return false
        if ((inputs.rvol ?: Double.NEGATIVE_INFINITY) < 0.80) return false
        val e9 = inputs.ema9 ?: return false
        val e20 = inputs.ema20 ?: return false
        val e50 = inputs.ema50 ?: return false
        return e9 > e20 && e20 > e50
    }

    private fun openingWatchDto(
        symbol: String,
        scanStartedAtMs: Long,
        observedAtMs: Long,
        price: Double,
        targetPct: Double,
        inputs: WorkingRangeCandidatePolicy.Inputs,
        decision: WorkingRangeCandidatePolicy.Decision,
        marketabilityState: String,
        marketability: WorkingRangeMarketabilityPolicy.Result
    ): WorkingRangeRecommendationDto = recommendationDto(
        symbol = symbol,
        scanStartedAtMs = scanStartedAtMs,
        state = "OPENING_WATCH",
        entryPrice = 0.0,
        targetPrice = 0.0,
        targetPct = targetPct,
        firstQualifiedAtMs = 0L,
        confirmedAtMs = 0L,
        firstQualifiedPrice = price,
        inputs = inputs,
        decision = decision,
        marketabilityState = marketabilityState,
        marketability = marketability,
        autoBuyEnabled = false,
        reason = "OPENING WATCH · provisional only · collecting independent momentum samples; no BUY permission"
    ).copy(lastObservedAtMs = observedAtMs, lastPrice = price)

    private fun recommendationDto(
        symbol: String,
        scanStartedAtMs: Long,
        state: String,
        entryPrice: Double,
        targetPrice: Double,
        targetPct: Double,
        firstQualifiedAtMs: Long,
        confirmedAtMs: Long,
        firstQualifiedPrice: Double,
        inputs: WorkingRangeCandidatePolicy.Inputs,
        decision: WorkingRangeCandidatePolicy.Decision,
        marketabilityState: String,
        marketability: WorkingRangeMarketabilityPolicy.Result,
        autoBuyEnabled: Boolean,
        reason: String
    ): WorkingRangeRecommendationDto = WorkingRangeRecommendationDto(
        symbol = symbol,
        scanStartedAtMs = scanStartedAtMs,
        state = state,
        entryPrice = entryPrice,
        targetPrice = targetPrice,
        targetPct = targetPct,
        firstQualifiedAtMs = firstQualifiedAtMs,
        firstQualifiedPrice = firstQualifiedPrice,
        confirmedAtMs = confirmedAtMs,
        lastObservedAtMs = System.currentTimeMillis(),
        lastPrice = inputs.price,
        rsi = inputs.rsi,
        rsiSlope = inputs.rsiSlope,
        rvol = inputs.rvol,
        volumeAcceleration = inputs.volumeAcceleration,
        greenVolumeShare = inputs.greenVolumeShare,
        ema9Slope = inputs.ema9Slope,
        ema20Slope = inputs.ema20Slope,
        macdHistogramSlope = inputs.macdHistogramSlope,
        openingMode = inputs.openingMode,
        openingSampleCount = inputs.openingSampleCount,
        openingMomentumPct = inputs.openingMomentumPct,
        openingVolumePaceRvol = inputs.openingVolumePaceRvol,
        openingSignalBasis = inputs.openingSignalBasis,
        targetRequirementAtr = decision.targetRequirementAtr,
        vwapExtensionAtr = decision.vwapExtensionAtr,
        range52Position = inputs.range52Position,
        spreadBps = inputs.spreadBps,
        orderBookImbalance = inputs.orderBookImbalance,
        marketabilityState = marketabilityState,
        upperCircuitPrice = marketability.brokerUpperCircuit,
        bestSellOffer = marketability.bestObservedOffer,
        quoteClientReceiptAgeMs = marketability.clientReceiptAgeMs,
        autoBuyEnabled = autoBuyEnabled,
        emaAlignmentMode = decision.emaAlignmentMode,
        researchStopLossPct = -WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT,
        reason = reason
    )

    private fun updateConfirmed(
        row: StoredRow,
        stillMatches: Boolean,
        reasons: List<String>,
        emaAlignmentMode: String,
        price: Double,
        observedAtMs: Long,
        marketabilityState: String,
        marketability: WorkingRangeMarketabilityPolicy.Result,
        inputs: WorkingRangeCandidatePolicy.Inputs
    ) {
        val old = row.dto
        val move = (price / old.entryPrice - 1.0) * 100.0
        val mfe = max(old.maxFavourablePct, move)
        val mae = min(old.maxAdversePct, move)
        val targetAt = if (old.targetFirstTouchAtMs == 0L && price >= old.targetPrice)
            observedAtMs else old.targetFirstTouchAtMs
        // Research chronology now uses the actual Working Range hard-stop policy: -1% from
        // the candidate reference. It remains sampled LTP chronology, not a broker stop path.
        val stopPrice = WorkingRangeExitPolicy.stopPrice(old.entryPrice)
        val stopAt = if (old.stopFirstTouchAtMs == 0L && price <= stopPrice)
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
        val path = synchronized(lock) {
            samples.filter { it.dateIst == row.dateIst && it.symbol == row.symbol &&
                it.observedAtMs >= old.confirmedAtMs }
                .map { WorkingRangeExitComparisonPolicy.Sample(it.observedAtMs, it.price) }
        }
        val comparison = WorkingRangeExitComparisonPolicy.evaluate(
            old.entryPrice, old.targetPct, path, WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT
        )
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
            rsi = inputs.rsi,
            rsiSlope = inputs.rsiSlope,
            rvol = inputs.rvol,
            volumeAcceleration = inputs.volumeAcceleration,
            greenVolumeShare = inputs.greenVolumeShare,
            ema9Slope = inputs.ema9Slope,
            ema20Slope = inputs.ema20Slope,
            macdHistogramSlope = inputs.macdHistogramSlope,
            openingMode = inputs.openingMode,
            openingSampleCount = inputs.openingSampleCount,
            openingMomentumPct = inputs.openingMomentumPct,
            openingVolumePaceRvol = inputs.openingVolumePaceRvol,
            openingSignalBasis = inputs.openingSignalBasis,
            spreadBps = inputs.spreadBps,
            orderBookImbalance = inputs.orderBookImbalance,
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
            researchStopLossPct = WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT,
            exitComparisonFixedState = comparison.fixedTargetStop.state,
            exitComparisonFixedReturnPct = comparison.fixedTargetStop.sampledReturnPct,
            exitComparisonTrailState = comparison.steppedProtection.state,
            exitComparisonTrailReturnPct = comparison.steppedProtection.sampledReturnPct,
            exitComparisonPartialState = comparison.partialThenTrail.state,
            exitComparisonPartialReturnPct = comparison.partialThenTrail.sampledReturnPct,
            reason = if (terminal)
                "Sampled LTP touched the Working Range target/-1% stop; sparse samples cannot prove intrabar ordering"
            else if (stillMatches) "Working Range conditions still match"
            else "Momentum conditions weakened: " + reasons.joinToString(", ")
        )
    }

    private fun recordSampleAndRead(
        date: String,
        symbol: String,
        observedAtMs: Long,
        price: Double,
        cumulativeVolume: Long?
    ): List<WorkingRangeOpeningMomentumPolicy.QuoteSample> = synchronized(lock) {
        val previous = samples.filter { it.dateIst == date && it.symbol == symbol }
            .maxByOrNull { it.observedAtMs }
        if (previous?.cumulativeVolume != null && cumulativeVolume != null &&
            cumulativeVolume < previous.cumulativeVolume) {
            // A broker day-volume counter reset invalidates the previous sample chain.
            samples.removeAll { it.dateIst == date && it.symbol == symbol }
        }
        if (samples.none { it.dateIst == date && it.symbol == symbol && it.observedAtMs == observedAtMs }) {
            samples.add(StoredSample(date, symbol, observedAtMs, price, cumulativeVolume))
        }
        val perSymbol = samples.filter { it.dateIst == date && it.symbol == symbol }
            .sortedBy { it.observedAtMs }
        if (perSymbol.size > MAX_SAMPLES_PER_SYMBOL) {
            val removeCount = perSymbol.size - MAX_SAMPLES_PER_SYMBOL
            val removeIds = perSymbol.take(removeCount).map { it.observedAtMs }.toSet()
            samples.removeAll { it.dateIst == date && it.symbol == symbol && it.observedAtMs in removeIds }
        }
        while (samples.size > MAX_TOTAL_SAMPLES) samples.removeAt(0)
        persistSamplesLocked()
        samples.filter { it.dateIst == date && it.symbol == symbol }
            .sortedBy { it.observedAtMs }
            .map {
                WorkingRangeOpeningMomentumPolicy.QuoteSample(
                    it.observedAtMs, it.price, it.cumulativeVolume
                )
            }
    }

    fun recent(dateIst: String): List<WorkingRangeRecommendationDto> = synchronized(lock) {
        rows.filter { it.dateIst == dateIst &&
            it.dto.state !in setOf("PENDING_DROPPED", "PENDING_EXPIRED", "OPENING_WATCH_DROPPED") }
            .sortedWith(compareByDescending<StoredRow> { it.dto.confirmedAtMs }
                .thenByDescending { it.dto.firstQualifiedAtMs })
            .map { it.dto.copy(firstQualifiedPrice = it.firstQualifiedPrice) }
    }

    fun export(): Map<String, Any?> {
        runCatching { queue.submit {}.get(5, TimeUnit.SECONDS) }
        return synchronized(lock) {
            mapOf(
                "schema" to "working_range_research_v9026",
                "research_observation_always_on" to true,
                "strategy_selection_independent_from_fixed_scores" to true,
                "univest_signal_dependency" to false,
                "multify_learning_or_event_prior_dependency" to false,
                "candidate_universe_source" to "shared_existing_full_market_discovery_detail_cohort",
                "shared_market_data_transport_only" to true,
                "extra_discovery_broker_requests" to 0,
                "extra_opening_quote_requests" to 0,
                "extra_opening_5m_requests" to 0,
                "opening_signal_basis" to "client-received-live-quote-volume-samples-plus-prior-completed-15m-trend",
                "opening_window_end_ist" to WorkingRangeOpeningMomentumPolicy.OPENING_WINDOW_END.toString(),
                "opening_confirmation_requires_samples" to WorkingRangeOpeningMomentumPolicy.MIN_OPENING_SAMPLES,
                "opening_minimum_momentum_pct" to WorkingRangeOpeningMomentumPolicy.MIN_OPENING_MOMENTUM_PCT,
                "quote_receipt_is_not_verified_exchange_tick_age" to true,
                "target_pct" to WorkingRangeCandidatePolicy.TARGET_PCT,
                "target_source" to "WORKING_RANGE_OWN_CONSTANT",
                "research_stop_loss_pct" to -WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT,
                "actual_stop_loss_pct" to -WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT,
                "actual_exit_policy" to "full target or fixed -1% stop; no stepped trailing",
                "exit_comparisons" to listOf(
                    "fixed target/-1% stop",
                    "0.55% arm/0.50% protected gain/+0.10% ratchet",
                    "50% at +0.55%/remainder stepped trail"
                ),
                "exit_comparisons_are_research_only" to true,
                "comparison_entry_basis" to "confirmed Working Range research entry, not broker fill",
                "research_comparison_path" to "sparse sampled client LTP; intrabar ordering unverified",
                "reentry_after_broker_confirmed_close" to "research_only_no_new_buy",
                "enabled_setting_is_separate_from_fixed" to true,
                "auto_buy_toggle_only_authorizes_new_alpha_auto_buys" to true,
                "alpha_only_execution" to true,
                "beta_read_only" to true,
                "working_range_position_engine" to "WORKING_RANGE_HOLDING",
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
                    "ema9_ema20_slopes_positive" to true,
                    "macd_histogram_slope_positive" to true,
                    "target_requirement_max_atr" to WorkingRangeCandidatePolicy.MAX_TARGET_ATR,
                    "minutes_to_close_min" to WorkingRangeCandidatePolicy.MIN_MINUTES_TO_CLOSE,
                    "vwap_extension_single_factor_veto" to false,
                    "range52_position_single_factor_veto" to false,
                    "rsi_upper_ceiling" to null,
                    "spread_is_execution_marketability_not_technical_selection" to true
                ),
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
                "evidence_queue_drop_at_ms" to lastQueueDropAtMs,
                "evidence_queue_drop_handled_at_ms" to lastDropHandledAtMs,
                "rows" to rows.map { mapOf(
                    "date_ist" to it.dateIst,
                    "symbol" to it.symbol,
                    "first_qualified_price" to it.firstQualifiedPrice,
                    "consecutive_passes" to it.consecutivePasses,
                    "last_pass_at_ms" to it.lastPassAtMs,
                    "recommendation" to it.dto
                ) },
                "opening_quote_samples" to samples,
                "rejected_candidate_observations" to rejections,
                "broker_confirmed_close_reentry_research_only" to reentries
            )
        }
    }

    private fun persistLocked() {
        prefs.edit().putString(KEY_ROWS, gson.toJson(rows)).apply()
    }

    private fun persistOrderIntentDurablyLocked(): Boolean =
        prefs.edit().putString(KEY_ROWS, gson.toJson(rows)).commit()

    private fun persistRejectionsLocked() {
        prefs.edit().putString(KEY_REJECTIONS, gson.toJson(rejections)).apply()
    }

    private fun persistSamplesLocked() {
        prefs.edit().putString(KEY_SAMPLES, gson.toJson(samples)).apply()
    }

    private fun persistReentriesLocked() {
        prefs.edit().putString(KEY_REENTRIES, gson.toJson(reentries)).apply()
    }

    private fun trimLocked() {
        if (rows.size > 400) rows.subList(0, rows.size - 400).clear()
    }

    companion object {
        private const val KEY_ROWS = "rows_json"
        private const val KEY_REJECTIONS = "rejections_json"
        private const val KEY_SAMPLES = "opening_samples_json"
        private const val KEY_REENTRIES = "reentries_json"
        private const val KEY_QUEUE_DROP_AT_MS = "evidence_queue_drop_at_ms"
        private const val KEY_DROP_HANDLED_AT_MS = "evidence_queue_drop_handled_at_ms"
        private const val MAX_SAMPLES_PER_SYMBOL = 32
        private const val MAX_TOTAL_SAMPLES = 8_000
    }
}
