package com.multify.traderpro.engine

/** Remembers previously evaluated PRIMARYs only long enough for fresh confirmation.
 * Does not grant publication, relax a quality threshold, or use stale prices.
 */
object FixedPrimaryCarryForwardPolicy {
    const val MAX_AGE_MS = 6L * 60_000L
    const val MAX_FOLLOWUPS = 5

    data class Observation(
        val symbol: String,
        val atMs: Long,
        val tier: String,
        val publishEligible: Boolean,
        val executionEligible: Boolean,
        val price: Double?
    )

    fun candidates(
        observations: List<Observation>,
        nowMs: Long,
        alreadyPublished: Collection<String>,
        limit: Int = MAX_FOLLOWUPS
    ): List<String> {
        val published = alreadyPublished.map { it.uppercase() }.toSet()
        // Require the LATEST recorded observation to remain PRIMARY: a subsequent
        // SECONDARY/RESEARCH observation cancels any pending follow-up.
        return observations.asSequence()
            .filter { it.atMs > 0 && it.atMs < nowMs }
            .groupBy { it.symbol.uppercase() }
            .values.mapNotNull { rows -> rows.maxByOrNull { it.atMs } }
            .filter { it.tier == "PRIMARY" && it.publishEligible && it.executionEligible }
            .filter { nowMs - it.atMs in 1L..MAX_AGE_MS }
            .filter { it.price != null && it.price.isFinite() &&
                it.price > 200.0 && it.price < 5000.0 }
            .filter { it.symbol.uppercase() !in published }
            .sortedWith(compareByDescending<Observation> { it.atMs }.thenBy { it.symbol })
            .take(limit.coerceIn(0, MAX_FOLLOWUPS))
            .map { it.symbol.uppercase() }
            .toList()
    }

    fun wasRecentlyDiscovered(
        sourceDate: String?, sourceAtMs: Long?,
        today: String, nowMs: Long
    ): Boolean = sourceDate == today && sourceAtMs != null &&
        nowMs - sourceAtMs in 0L..MAX_AGE_MS
}
