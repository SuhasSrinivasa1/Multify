package com.multify.traderpro.engine

/** Execution-only broker safeguards. Recommendation and stop-price formulas stay frozen. */
object BrokerExecutionSafetyPolicy {
    const val REJECTED_SELL_BACKOFF_MS = 86_400_000L
    const val REJECTED_GTT_BACKOFF_MS = 1_800_000L
    const val TRANSIENT_GTT_BACKOFF_MS = 300_000L
    const val CLOSED_POSITION_REIMPORT_GUARD_MS = 86_400_000L

    fun sellableQuantity(dematFree: Int, cncPosition: Int): Int =
        maxOf(dematFree.coerceAtLeast(0), cncPosition.coerceAtLeast(0))

    fun maySell(requested: Int, dematFree: Int, cncPosition: Int): Boolean =
        requested > 0 && sellableQuantity(dematFree, cncPosition) >= requested

    fun isTerminal(status: String): Boolean =
        status.uppercase() in setOf("FAILED", "REJECTED", "CANCELLED")

    fun isInsufficientShares(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("enough shares") ||
               lower.contains("insufficient shares") ||
               lower.contains("insufficient holdings") ||
               lower.contains("no shares available")
    }
}
