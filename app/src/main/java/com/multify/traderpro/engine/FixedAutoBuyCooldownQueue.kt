package com.multify.traderpro.engine

/**
 * In-memory, bounded intent journal for temporary broker cooldowns.
 *
 * It NEVER authorizes an order. The original Fixed execution flow must still
 * re-read the persisted recommendation, verify activation, refresh the Alpha
 * quote, check liquidity, broker inventory, funds, and protection before BUY.
 * No broker API requests are made by this class.
 */
class FixedAutoBuyCooldownQueue {
    data class Pending(
        val recommendationId: Long,
        val symbol: String,
        val publishedAtMs: Long,
        val activatedAtMs: Long
    )
    data class Plan(
        val pending: List<Pending>,
        val retryAtMs: Long?,
        val expiredOrIneligible: Int,
        val retryableCount: Int
    )
    private val waiting = LinkedHashMap<Long, Pending>()

    @Synchronized
    fun afterCooldown(candidates: List<Pending>, nowMs: Long, nextAllowedAtMs: Long): Plan {
        val accepted = candidates.filter {
            AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(
                it.publishedAtMs, it.activatedAtMs
            ) && AutoBuyEntrySafetyPolicy.isFreshFixedEntry(it.publishedAtMs, nowMs)
        }
        val validIds = accepted.map { it.recommendationId }.toHashSet()
        waiting.keys.retainAll(validIds)
        accepted.takeLast(MAX_PENDING).forEach { waiting[it.recommendationId] = it }
        while (waiting.size > MAX_PENDING) waiting.remove(waiting.keys.first())

        val retry = nextAllowedAtMs.takeIf { it > nowMs }?.plus(RETRY_BUFFER_MS)
        val canRetry = retry?.let { at ->
            waiting.values.count { candidate ->
                // We must have time to re-fetch a FRESH Alpha execution quote
                // and re-run broker-side inventory before the original 5m limit.
                candidate.publishedAtMs + AutoBuyEntrySafetyPolicy.FIXED_AUTO_BUY_MAX_AGE_MS >=
                    at + MIN_REVALIDATION_HEADROOM_MS
            }
        } ?: 0
        return Plan(
            pending = waiting.values.toList(),
            retryAtMs = retry?.takeIf { canRetry > 0 },
            expiredOrIneligible = candidates.size - accepted.size,
            retryableCount = canRetry
        )
    }

    @Synchronized
    fun clear() = waiting.clear()

    @Synchronized
    fun pendingCount(): Int = waiting.size

    companion object {
        const val MAX_PENDING = 48
        private const val RETRY_BUFFER_MS = 1_000L
        private const val MIN_REVALIDATION_HEADROOM_MS = 20_000L
    }
}
