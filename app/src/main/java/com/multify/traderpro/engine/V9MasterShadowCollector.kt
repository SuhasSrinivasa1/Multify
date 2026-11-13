package com.multify.traderpro.engine

import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.domain.ParsedSignal
import com.multify.traderpro.domain.SignalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Master v9, fully independent, no-network, bounded research comparison.
 * v16 original indicator/quality semantics vs identical v19/v28 production
 * matched on the SAME 15-minute historical response and quote.
 * NEVER controls discovery, PRIMARY publishing, auto-BUY, SELL or GTT.
 */
internal class V9MasterShadowCollector(private val auditLogger: AuditLogger) {
    private data class Input(
        val symbol: String, val quote: QuotePayload,
        val history: HistoricalPayload, val v19: FixedStrategyEvaluation,
        val scanAtMs: Long, val capturedAtMs: Long
    )
    private val queue = Channel<Input>(capacity = 128)
    private val completed = AtomicLong(0)
    private val dropped = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    init {
        scope.launch {
            for (row in queue) {
                try {
                    evaluate(row)
                } catch (e: Throwable) {
                    auditLogger.logAsync("V9_MASTER_SHADOW", "EVALUATION_FAILED", mapOf(
                        "symbol" to row.symbol, "reference_at_ms" to row.capturedAtMs,
                        "error_type" to e.javaClass.simpleName,
                        "production_unchanged" to true
                    ))
                }
            }
        }
    }

    /** Nonblocking enqueue: no extra network traffic and bounded RAM. */
    fun offer(
        symbol: String, quote: QuotePayload, history: HistoricalPayload,
        v19: FixedStrategyEvaluation, scanAtMs: Long, capturedAtMs: Long
    ) {
        if (!queue.trySend(Input(symbol, quote, history, v19,
                scanAtMs, capturedAtMs)).isSuccess) {
            val n = dropped.incrementAndGet()
            if (n == 1L || n % 100L == 0L) auditLogger.logAsync(
                "V9_MASTER_SHADOW", "BOUNDED_QUEUE_DROPPED", mapOf(
                    "dropped_total" to n,
                    "production_unchanged" to true
                )
            )
        }
    }

    private fun evaluate(row: Input) {
        val v16Features = V9Fixed8316StrategyShadow.buildFeatures(
            row.quote, row.history, 15
        )
        val signal = ParsedSignal(
            SignalType.TRADE_RELEASE, symbol = row.symbol,
            rawText = "v9-master-shadow", confidence = 1.0
        )
        val v16 = V9Fixed8316StrategyShadow.evaluateLong(
            signal, v16Features, includeEventPrior = false
        )
        // Both classifiers use neutral-breadth proxy because this independent
        // callback precedes the official full-universe breadth computation.
        val target = FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT
        val q16 = V9Fixed8316QualityShadow.evaluate(v16, target, "BREADTH_NEUTRAL")
        val q19 = FixedForecastQualityPolicy.evaluate(row.v19, target, "BREADTH_NEUTRAL")
        val suspiciousBars = row.history.candles.count { bar ->
            val ts = bar.firstOrNull()?.let { MarketCandleTimestampParser.toEpochMillis(it) }
            ts == null || ts + 900_000L > row.capturedAtMs
        }
        auditLogger.logAsync("V9_MASTER_SHADOW", "MATCHED_V16_V19", mapOf(
            "schema" to "v9_matched_shadow_v1",
            "symbol" to row.symbol,
            "scan_started_at_ms" to row.scanAtMs,
            "quote_captured_at_ms" to row.capturedAtMs,
            "same_quote_price" to row.quote.lastPrice,
            "same_raw_15m_candle_count" to row.history.candles.size,
            "v16_unverified_or_incomplete_bar_count" to suspiciousBars,
            "v16_lookahead_risk" to (suspiciousBars > 0),
            "shared_target_pct" to target,
            "breadth_comparison" to "NEUTRAL_PROXY_NOT_FULL_RANKING",
            "v16_tier" to q16.tier,
            "v16_confidence" to v16.confidence,
            "v16_direction" to v16.directionalScore,
            "v16_regime" to v16.regime,
            "v19_tier" to q19.tier,
            "v19_confidence" to row.v19.confidence,
            "v19_direction" to row.v19.directionalScore,
            "v19_regime" to row.v19.regime,
            "v26_v28_fixed_selection_identical_to_v19" to true,
            "new_broker_calls" to 0,
            "production_decision_changed" to false,
            "records_processed" to completed.incrementAndGet(),
            "records_dropped" to dropped.get()
        ))
    }
}
