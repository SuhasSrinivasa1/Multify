package com.multify.traderpro.data.logging

import android.content.Context
import android.os.Process
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.multify.traderpro.engine.FixedMultifyFactorFamilies
import com.multify.traderpro.engine.FixedStrategyEvaluation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHADOW-ONLY evidence stream, intentionally isolated from the Fixed scanner and trading executor.
 * The scanner enqueues an existing immutable evaluation reference; no scoring, JSON serialization,
 * file I/O, additional market requests, blocking waits or suspensions run on the Fixed path.
 * A full queue drops diagnostics rather than slowing Fixed. Do not read these rows as BUY decisions.
 */
@Singleton
class FixedFactorTelemetry @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "fixed_factor_telemetry").apply { mkdirs() }
    private val current = File(dir, "fixed_factor_snapshots.jsonl")
    private val lock = Any()
    private val gson: Gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val dropped = AtomicLong(0)
    private val written = AtomicLong(0)
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "fixed-factor-shadow-writer").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        } },
        { _, _ -> dropped.incrementAndGet() }
    )

    fun tryRecord(symbol: String, evaluation: FixedStrategyEvaluation, scanStartedAtMs: Long) {
        // Keep the entire calling-thread path O(1). Heavy transformation takes place only in worker.
        val completedAtMs = System.currentTimeMillis()
        executor.execute {
            runCatching {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                val row = buildEvidence(symbol, evaluation, scanStartedAtMs, completedAtMs)
                val jsonl = gson.toJson(row) + "\n"
                synchronized(lock) {
                    rotateIfNecessary(jsonl.toByteArray(Charsets.UTF_8).size.toLong())
                    current.appendText(jsonl)
                    written.incrementAndGet()
                }
            }.onFailure { dropped.incrementAndGet() }
        }
    }

    /** Raw 40-factor rows are calculated off-thread from the *already evaluated* Fixed features. */
    internal fun buildEvidence(
        symbol: String,
        e: FixedStrategyEvaluation,
        scanStartedAtMs: Long,
        completedAtMs: Long
    ): Map<String, Any?> {
        val f = e.features
        // The factor raw/score values are invariant to market breadth; the market-regime-weighted
        // family consensus from the live ranking is NOT reconstructed or claimed here.
        val model = FixedMultifyFactorFamilies.evaluate(e, "BREADTH_NEUTRAL")
        val atr = f.atr14?.takeIf { it.isFinite() && it > 0.0 }
        fun clean(v: Double?): Double? = v?.takeIf { it.isFinite() }
        return mapOf(
            "schema" to "fixed_factor_v1",
            "source" to "SHADOW_ONLY_NOT_FOR_TRADING",
            "snapshot_key" to "${scanStartedAtMs}|${symbol.uppercase()}",
            "symbol" to symbol.uppercase(),
            "scan_started_at_ms" to scanStartedAtMs,
            "evaluation_completed_at_ms" to completedAtMs,
            "evaluation_time_utc" to Instant.ofEpochMilli(completedAtMs).toString(),
            "candle_interval_minutes" to 5,
            "factor_count" to model.factors.size,
            "market_regime_for_factor_raw_scores" to "BREADTH_NEUTRAL_REFERENCE",
            "market_regime_for_live_ranking" to "NOT_CAPTURED_IN_EVALUATION",
            "strategy" to e.strategy,
            "regime" to e.regime,
            "confidence" to clean(e.confidence),
            "directional_score" to clean(e.directionalScore),
            "quote_age_ms" to null,
            "features" to mapOf(
                "ltp" to clean(f.ltp), "bid" to clean(f.bid), "ask" to clean(f.ask),
                "spread_bps" to clean(f.spreadBps), "order_book_imbalance" to clean(f.orderBookImbalance),
                "day_change_pct" to clean(f.dayChangePct), "market_cap" to clean(f.marketCap),
                "range52_position" to clean(f.range52Position),
                "vwap" to clean(f.vwap), "ema9" to clean(f.ema9), "ema20" to clean(f.ema20),
                "ema50" to clean(f.ema50), "ema9_slope" to clean(f.ema9Slope),
                "ema20_slope" to clean(f.ema20Slope), "vwap_slope" to clean(f.vwapSlope),
                "macd_histogram_slope" to clean(f.macdHistogramSlope), "rsi_slope" to clean(f.rsiSlope),
                "volume_acceleration" to clean(f.volumeAcceleration),
                "green_volume_share" to clean(f.greenVolumeShare),
                "structure_score" to clean(f.structureScore),
                "session_high_distance_pct" to clean(f.sessionHighDistancePct),
                "session_low_distance_pct" to clean(f.sessionLowDistancePct),
                "atr14" to clean(f.atr14), "rsi14" to clean(f.rsi14),
                "bollinger_width" to clean(f.bollWidth), "macd_histogram" to clean(f.macdHistogram),
                "trend_slope_atr" to clean(f.trendSlopeAtr), "rvol" to clean(f.rvol),
                "donchian_high" to clean(f.donchianHigh), "donchian_low" to clean(f.donchianLow),
                "orb5_high" to clean(f.orb5High), "orb5_low" to clean(f.orb5Low),
                "orb15_high" to clean(f.orb15High), "orb15_low" to clean(f.orb15Low),
                "last_open" to clean(f.lastOpen), "last_high" to clean(f.lastHigh),
                "last_low" to clean(f.lastLow), "last_close" to clean(f.lastClose),
                "prev_open" to clean(f.prevOpen), "prev_high" to clean(f.prevHigh),
                "prev_low" to clean(f.prevLow), "prev_close" to clean(f.prevClose)
            ),
            "diagnostic_metrics" to mapOf(
                "vwap_distance_atr" to if (atr != null) f.vwap?.let { clean((f.ltp - it) / atr) } else null,
                "ema9_ema20_separation_atr" to if (atr != null && f.ema9 != null && f.ema20 != null)
                    clean((f.ema9 - f.ema20) / atr) else null,
                "atr_pct" to if (f.ltp > 0.0 && atr != null) clean(atr / f.ltp * 100.0) else null,
                "session_high_proximity_pct" to clean(f.sessionHighDistancePct),
                "last_candle_range_atr" to if (atr != null && f.lastHigh != null && f.lastLow != null)
                    clean((f.lastHigh - f.lastLow) / atr) else null
            ),
            "votes" to e.votes.map { mapOf("name" to it.name, "score" to clean(it.score), "note" to it.note) },
            "factors" to model.factors.map { mapOf("name" to it.name, "family" to it.family,
                "raw" to clean(it.raw), "score" to clean(it.score)) },
            "families_unweighted_reference" to model.families.map { mapOf("name" to it.name,
                "score" to clean(it.score), "available_factors" to it.availableFactors,
                "total_factors" to it.totalFactors) },
            "recent_candles" to f.candles.takeLast(3).map { mapOf("ts" to it.ts, "open" to clean(it.open),
                "high" to clean(it.high), "low" to clean(it.low), "close" to clean(it.close),
                "volume" to clean(it.volume)) }
        )
    }

    /** User-initiated export may wait for the background queue; market scans never do. */
    fun appendExportEntry(zip: ZipOutputStream) {
        runCatching { executor.submit {}.get(3, TimeUnit.SECONDS) }
        zip.putNextEntry(ZipEntry("fixed_factor_snapshots.jsonl"))
        synchronized(lock) {
            for (i in ARCHIVE_COUNT downTo 1) {
                val file = File(dir, "fixed_factor_snapshots.$i.jsonl")
                if (file.exists()) file.inputStream().use { it.copyTo(zip) }
            }
            if (current.exists()) current.inputStream().use { it.copyTo(zip) }
        }
        zip.closeEntry()
    }

    /** Only used during explicit learning ZIP restore; never imports live trading state. */
    fun importArchivedSnapshots(jsonl: String): Int {
        runCatching { executor.submit {}.get(3, TimeUnit.SECONDS) }
        var added = 0
        synchronized(lock) {
            val existing = HashSet<String>()
            for (i in ARCHIVE_COUNT downTo 1) {
                val file = File(dir, "fixed_factor_snapshots.$i.jsonl")
                if (file.exists()) file.forEachLine { line ->
                    runCatching { JsonParser.parseString(line).asJsonObject.get("snapshot_key")?.asString }
                        .getOrNull()?.let(existing::add)
                }
            }
            if (current.exists()) current.forEachLine { line ->
                runCatching { JsonParser.parseString(line).asJsonObject.get("snapshot_key")?.asString }
                    .getOrNull()?.let(existing::add)
            }
            jsonl.lineSequence().forEach { line ->
                if (line.length > MAX_ROW_CHARS || line.isBlank()) return@forEach
                val obj = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
                    ?: return@forEach
                if (obj.get("schema")?.asString != "fixed_factor_v1" ||
                    obj.get("source")?.asString != "SHADOW_ONLY_NOT_FOR_TRADING" ||
                    obj.get("factor_count")?.asInt != FixedMultifyFactorFamilies.FACTOR_COUNT ||
                    obj.getAsJsonArray("factors")?.size() != FixedMultifyFactorFamilies.FACTOR_COUNT)
                    return@forEach
                val key = obj.get("snapshot_key")?.asString ?: return@forEach
                if (key.length > 100 || !existing.add(key)) return@forEach
                rotateIfNecessary((line.length + 1).toLong())
                current.appendText(line + "\n")
                added++
            }
        }
        return added
    }

    fun droppedCount(): Long = dropped.get()
    fun writtenCount(): Long = written.get()
    private fun rotateIfNecessary(incoming: Long) {
        if (!current.exists() || current.length() + incoming <= MAX_BYTES_PER_FILE) return
        File(dir, "fixed_factor_snapshots.$ARCHIVE_COUNT.jsonl").delete()
        for (i in ARCHIVE_COUNT - 1 downTo 1) {
            val old = File(dir, "fixed_factor_snapshots.$i.jsonl")
            if (old.exists()) old.renameTo(File(dir, "fixed_factor_snapshots.${i + 1}.jsonl"))
        }
        current.renameTo(File(dir, "fixed_factor_snapshots.1.jsonl"))
    }

    companion object {
        private const val QUEUE_CAPACITY = 128
        private const val MAX_BYTES_PER_FILE = 12L * 1024L * 1024L
        private const val ARCHIVE_COUNT = 2
        private const val MAX_ROW_CHARS = 30_000
    }
}
