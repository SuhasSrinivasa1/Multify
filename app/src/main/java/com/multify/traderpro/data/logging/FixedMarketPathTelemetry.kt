package com.multify.traderpro.data.logging

import android.content.Context
import android.os.Process
import com.google.gson.GsonBuilder
import com.multify.traderpro.engine.FixedMarketDiscoveryCandidate
import com.multify.traderpro.engine.FixedRollingMarketSample
import com.multify.traderpro.engine.FixedRollingFiveMinuteResearchPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compact, deterministic reversible read-only market snapshots.
 * LTP means last TRADED price as returned by the broker, not NSE indicative
 * auction equilibrium or exchange-time validated bid/ask. No volume can be
 * derived from this endpoint.
 */
internal object FixedMarketPathCodec {
    fun pack(prices: Map<String, Double>): String = prices.entries.asSequence()
        .filter { (symbol, px) ->
            symbol.matches(Regex("[A-Za-z0-9&._-]{1,32}")) && px.isFinite() && px > 0.0
        }
        .sortedBy { it.key }
        .joinToString("|") { (symbol, px) -> "${symbol.uppercase(Locale.US)}~${px}" }

    fun unpack(packed: String): Map<String, Double> = buildMap {
        for (part in packed.split('|')) {
            val i = part.lastIndexOf('~')
            if (i <= 0 || i >= part.lastIndex) continue
            val symbol = part.substring(0, i)
            val price = part.substring(i + 1).toDoubleOrNull() ?: continue
            if (symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) &&
                price.isFinite() && price > 0.0) put(symbol, price)
        }
    }
}

/**
 * SHADOW-ONLY archive of every completed official full-NSE discovery LTP
 * sweep plus one separate pre-open research pass. Captures previously
 * ignored movers, scans, and ranking without ANY extra broker API calls.
 *
 * O(1) on the critical scanner thread: enqueue immutable references only.
 * Packing, JSON, sorting and disk writes execute on a single low-priority
 * bounded thread. If overloaded, evidence is dropped, NEVER a trade delayed.
 *
 * Rolling limits intentionally prioritize current sessions; export each
 * trading day before older segments rotate. Export counters reveal losses.
 */
@Singleton
class FixedMarketPathTelemetry @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dir = File(context.filesDir, "fixed_market_path_telemetry").apply { mkdirs() }
    private val current = File(dir, "fixed_market_path.jsonl")
    private val lock = Any()
    // This ring is shadow-only. It neither changes the persisted Fixed baseline
    // nor schedules additional requests. All access is on the telemetry worker.
    private val rollingSnapshots = java.util.ArrayDeque<FixedRollingMarketSample>()
    private val researchPrefs = context.getSharedPreferences("fixed_rolling_research_v8320", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val dropped = AtomicLong(0L)
    private val written = AtomicLong(0L)
    private val queued = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_CAPACITY),
        { action ->
            Thread(action, "fixed-market-path-shadow").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        },
        { _, _ -> dropped.incrementAndGet() }
    )

    fun tryRecord(
        date: String,
        phase: String,
        scanStartedAtMs: Long,
        sweepCompletedAtMs: Long,
        prices: Map<String, Double>,
        universeCount: Int,
        previousLtps: Map<String, Double> = emptyMap(),
        sessionOpenLtps: Map<String, Double> = emptyMap(),
        selected: List<FixedMarketDiscoveryCandidate> = emptyList(),
        baselineReady: Boolean = false,
        source: String = "GROWW_BULK_LTP_CLIENT"
    ) {
        val queuedAtMs = System.currentTimeMillis()
        queued.execute {
            runCatching {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                val packed = FixedMarketPathCodec.pack(prices)
                val completeMarketSample =
                    phase == "OFFICIAL_FIXED_MARKET_WIDE_DISCOVERY" &&
                        universeCount > 0 && prices.size * 100L >= universeCount * 80L
                val sample = if (completeMarketSample)
                    FixedRollingFiveMinuteResearchPolicy.study(
                        sweepCompletedAtMs, prices, rollingSnapshots.toList(),
                        selected.map { it.symbol })
                else null
                if (completeMarketSample) {
                    rollingSnapshots.addLast(FixedRollingMarketSample(sweepCompletedAtMs, prices))
                    while (rollingSnapshots.isNotEmpty() &&
                        sweepCompletedAtMs - rollingSnapshots.first.atMs >
                            FixedRollingFiveMinuteResearchPolicy.MAX_LOOKBACK_MS) {
                        rollingSnapshots.removeFirst()
                    }
                }
                val firstObservedAtMs = if (completeMarketSample) {
                    val sameDay = researchPrefs.getString("first_date", "") == date
                    val old = if (sameDay) researchPrefs.getLong("first_at_ms", 0L) else 0L
                    if (old > 0L) old else {
                        researchPrefs.edit().putString("first_date", date)
                            .putLong("first_at_ms", sweepCompletedAtMs).apply()
                        sweepCompletedAtMs
                    }
                } else if (researchPrefs.getString("first_date", "") == date)
                    researchPrefs.getLong("first_at_ms", 0L)
                else 0L
                val isPre0930Session = runCatching {
                    val ist = java.time.Instant.ofEpochMilli(sweepCompletedAtMs)
                        .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                    ist.toLocalTime() >= java.time.LocalTime.of(9, 15) &&
                        ist.toLocalTime() < java.time.LocalTime.of(9, 30)
                }.getOrDefault(false)
                val upDown = prices.entries.asSequence().mapNotNull { (symbol, px) ->
                    sessionOpenLtps[symbol]?.takeIf { it > 0.0 && it.isFinite() }
                        ?.let { open -> (px / open - 1.0) * 100.0 }
                }.toList()
                val row = mapOf(
                    "schema" to "fixed_market_path_v1",
                    "source" to "READ_ONLY_NO_TRADING",
                    "date_ist" to date,
                    "phase" to phase,
                    "scan_started_at_ms" to scanStartedAtMs,
                    "sweep_completed_at_ms" to sweepCompletedAtMs,
                    "shadow_queued_at_ms" to queuedAtMs,
                    "shadow_written_at_ms" to System.currentTimeMillis(),
                    "broker_source" to source,
                    "reference_time_is_exchange_timestamp" to false,
                    "bulk_quote_has_volume" to false,
                    "preopen_iep_verified" to false,
                    "first_observed_sweep_at_ms_research" to firstObservedAtMs.takeIf { it > 0L },
                    "baseline_is_true_nse_0915_open" to false,
                    "opening_0915_0930_window" to isPre0930Session,
                    "completed_today_15m_opening_bar_available" to !isPre0930Session,
                    "rolling_five_minute_research" to mapOf(
                        "enabled" to true,
                        "trade_or_fixed_ranking_effect" to false,
                        "uses_additional_broker_calls" to false,
                        "available" to (sample?.available == true),
                        "reference_at_ms" to sample?.referenceAtMs,
                        "lookback_ms" to sample?.lookbackMs,
                        "symbols_compared" to (sample?.measuredCount ?: 0),
                        "top_30_movers" to (sample?.topMovers ?: emptyList<Any>()),
                        "existing_ranked_candidates" to (sample?.selectedCandidates ?: emptyList<Any>())
                    ),
                    "universe_count" to universeCount,
                    "sample_count" to prices.size,
                    "valid_price_count" to packed.count { it == '~' },
                    "baseline_ready_for_fixed" to baselineReady,
                    "relative_to_first_sweep_counts" to mapOf(
                        "up" to upDown.count { it > 0.0 },
                        "down" to upDown.count { it < 0.0 },
                        "flat" to upDown.count { it == 0.0 },
                        "known" to upDown.size,
                        "baseline_is_first_observed_sweep_not_verified_open" to true
                    ),
                    "selected_premarket_or_fixed_candidates" to selected.mapIndexed { index, row ->
                        mapOf(
                            "rank" to index + 1,
                            "symbol" to row.symbol,
                            "broker_ltp" to row.ltp,
                            "pulse_since_previous_sweep_pct" to row.pulsePct,
                            "since_first_sweep_pct" to row.sessionPct,
                            "selection_score" to row.score
                        )
                    },
                    "full_market_ltp_packed_format" to "SYMBOL~LTP|SYMBOL~LTP",
                    "full_market_ltp_packed" to packed,
                    "used_for_trading" to false
                )
                val line = gson.toJson(row) + "\n"
                synchronized(lock) {
                    rotateIfNeeded(line.toByteArray(Charsets.UTF_8).size.toLong())
                    current.appendText(line)
                    written.incrementAndGet()
                }
            }.onFailure { dropped.incrementAndGet() }
        }
    }

    fun writtenCount(): Long = written.get()
    fun droppedCount(): Long = dropped.get()
    fun queuedCount(): Int = queued.queue.size

    /** User-triggered diagnostic export only; not on the Fixed order path. */
    fun appendExportEntry(zip: ZipOutputStream) {
        runCatching { queued.submit {}.get(10, TimeUnit.SECONDS) }
        zip.putNextEntry(ZipEntry("fixed_market_path.jsonl"))
        synchronized(lock) {
            for (i in ARCHIVE_COUNT downTo 1) {
                val archive = File(dir, "fixed_market_path.${i}.jsonl")
                if (archive.exists()) archive.inputStream().use { it.copyTo(zip) }
            }
            if (current.exists()) current.inputStream().use { it.copyTo(zip) }
        }
        zip.closeEntry()
    }

    private fun rotateIfNeeded(incoming: Long) {
        if (!current.exists() || current.length() + incoming <= MAX_FILE_BYTES) return
        File(dir, "fixed_market_path.${ARCHIVE_COUNT}.jsonl").delete()
        for (i in ARCHIVE_COUNT - 1 downTo 1) {
            val older = File(dir, "fixed_market_path.${i}.jsonl")
            if (older.exists()) older.renameTo(File(dir, "fixed_market_path.${i + 1}.jsonl"))
        }
        current.renameTo(File(dir, "fixed_market_path.1.jsonl"))
    }

    companion object {
        private const val QUEUE_CAPACITY = 12
        private const val MAX_FILE_BYTES = 20L * 1024L * 1024L
        private const val ARCHIVE_COUNT = 3
    }
}
