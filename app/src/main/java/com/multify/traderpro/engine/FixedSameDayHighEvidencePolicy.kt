package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Research-only fallback for a missing broker-reported day high.
 * The frozen Fixed feature sessionHighDistancePct spans multiple historical days;
 * it must NEVER be mislabeled as the current NSE session high.
 * Broker history may have nonnumeric timestamps: those candles are rejected.
 */
object FixedSameDayHighEvidencePolicy {
    private val INDIA = ZoneId.of("Asia/Kolkata")
    private val NSE_OPEN = LocalTime.of(9, 15)
    private val NSE_CLOSE = LocalTime.of(15, 30)

    fun todayHigh(snapshot: FixedFeatureSnapshot?, asOfMs: Long): Double? =
        if (snapshot == null) null else todayHighFromBars(snapshot.candles, asOfMs)

    fun todayHighFromBars(candles: List<FixedCandle>, asOfMs: Long): Double? {
        if (asOfMs <= 0L) return null
        val now = Instant.ofEpochMilli(asOfMs).atZone(INDIA)
        val points = candles.mapNotNull { bar ->
            val timestampMs = when {
                bar.ts in 1_000_000_000L..9_999_999_999L -> bar.ts * 1_000L
                bar.ts in 1_000_000_000_000L..9_999_999_999_999L -> bar.ts
                else -> return@mapNotNull null // index fallback/text time is not evidence
            }
            if (timestampMs > asOfMs || timestampMs <= 0L ||
                !bar.high.isFinite() || bar.high <= 0.0) return@mapNotNull null
            val local = Instant.ofEpochMilli(timestampMs).atZone(INDIA)
            if (local.toLocalDate() != now.toLocalDate() ||
                local.toLocalTime().isBefore(NSE_OPEN) ||
                !local.toLocalTime().isBefore(NSE_CLOSE)) return@mapNotNull null
            bar.high
        }
        return points.takeIf { it.size >= 2 }?.maxOrNull()
    }
}
