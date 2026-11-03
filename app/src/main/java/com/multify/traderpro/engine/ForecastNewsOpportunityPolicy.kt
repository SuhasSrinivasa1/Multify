package com.multify.traderpro.engine

import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime

/** NSE-only Forecast decision at news receipt or next market open; no price-spike prerequisite. */
object ForecastNewsOpportunityPolicy {
    private val INDIA = ZoneId.of("Asia/Kolkata")
    const val MAX_ALREADY_MOVED_PCT = 2.0
    const val MAX_CARRY_HOURS = 96L
    const val MIN_PATTERN_SAMPLES = 30
    const val MIN_PATTERN_DAYS = 5
    const val MIN_PATTERN_HIT_RATE = 0.75

    data class Evidence(val observations: Int = 0, val distinctDays: Int = 0, val hitRate: Double = 0.0, val averageMfePct: Double = 0.0) {
        val proven: Boolean get() = observations >= MIN_PATTERN_SAMPLES &&
            distinctDays >= MIN_PATTERN_DAYS && hitRate >= MIN_PATTERN_HIT_RATE
    }
    data class Context(
        val dayChangePct: Double?, val spreadBps: Double?, val lastPrice: Double,
        val amountCrore: Double?, val marketCapRupees: Double?, val distinctCatalysts: Int,
        val category: String, val ageMinutes: Long, val evidence: Evidence
    )
    data class Decision(val eligible: Boolean, val reason: String,
        val materialityPct: Double?, val capBucket: String, val patternKey: String)

    fun capBucket(cap: Double?): String = when {
        cap == null || !cap.isFinite() || cap <= 0 -> "UNKNOWN"
        cap < 50_000_000_000.0 -> "SMALL"   // below ₹5,000 crore
        cap < 200_000_000_000.0 -> "MID"    // ₹5,000–20,000 crore
        else -> "LARGE"
    }
    /** Groww market_cap is treated as rupees; reject implausible values rather than invent ratios. */
    fun materialityPct(amountCrore: Double?, marketCapRupees: Double?): Double? {
        if (amountCrore == null || !amountCrore.isFinite() || amountCrore <= 0.0) return null
        if (marketCapRupees == null || !marketCapRupees.isFinite() || marketCapRupees < 100_000_000.0) return null
        val pct = amountCrore * 10_000_000.0 / marketCapRupees * 100.0
        return pct.takeIf { it.isFinite() && it in 0.0..1000.0 }
    }
    fun patternKey(category: String, cap: String, catalysts: Int, materiality: Double?): String {
        val band = when {
            materiality == null -> "AMOUNT_UNKNOWN"
            materiality < 0.5 -> "LOW_MATERIALITY"
            materiality < 3.0 -> "MEDIUM_MATERIALITY"
            else -> "HIGH_MATERIALITY"
        }
        return listOf(category, cap, if (catalysts >= 2) "MULTI" else "SINGLE", band).joinToString("|")
    }
    fun isCarryForward(publishedAtMs: Long, nowMs: Long): Boolean {
        if (publishedAtMs > nowMs || nowMs - publishedAtMs > MAX_CARRY_HOURS * 3_600_000L) return false
        val published = Instant.ofEpochMilli(publishedAtMs).atZone(INDIA)
        val now = Instant.ofEpochMilli(nowMs).atZone(INDIA)
        val time = published.toLocalTime()
        val wasOffMarket = published.dayOfWeek.value >= 6 ||
            time >= LocalTime.of(15, 15) || time < LocalTime.of(9, 15)
        return wasOffMarket && (published.toLocalDate() != now.toLocalDate() ||
            time < LocalTime.of(9, 15))
    }
    fun decide(c: Context): Decision {
        val material = materialityPct(c.amountCrore, c.marketCapRupees)
        val cap = capBucket(c.marketCapRupees)
        val key = patternKey(c.category, cap, c.distinctCatalysts, material)
        val reason = when {
            !c.evidence.proven -> "PATTERN_NOT_VALIDATED"
            c.evidence.averageMfePct <= 1.0 -> "AVERAGE_GAIN_NOT_ABOVE_1PCT"
            c.lastPrice <= 0.0 || !c.lastPrice.isFinite() -> "INVALID_QUOTE"
            c.dayChangePct == null || !c.dayChangePct.isFinite() -> "PRICE_REACTION_UNKNOWN"
            c.dayChangePct > MAX_ALREADY_MOVED_PCT -> "ALREADY_MOVED"
            c.spreadBps == null || !c.spreadBps.isFinite() || c.spreadBps !in 0.0..40.0 -> "SPREAD_UNAVAILABLE_OR_WIDE"
            c.ageMinutes < 0 || c.ageMinutes > MAX_CARRY_HOURS * 60 -> "STALE_ANNOUNCEMENT"
            else -> "VALIDATED_PRE_SPIKE_CATALYST"
        }
        return Decision(reason == "VALIDATED_PRE_SPIKE_CATALYST", reason, material, cap, key)
    }
}
