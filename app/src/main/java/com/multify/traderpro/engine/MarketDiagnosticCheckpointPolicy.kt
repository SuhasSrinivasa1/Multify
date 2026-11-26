package com.multify.traderpro.engine

/** Pure scheduling gate for diagnostics only; never feeds recommendation or execution logic. */
object MarketDiagnosticCheckpointPolicy {
    const val RECOMMENDATION_BATCH_SIZE = 20
    const val TIME_INTERVAL_MS = 3L * 60L * 60L * 1000L

    fun shouldCheckpoint(
        trigger: String,
        currentRecommendationCount: Int,
        archivedRecommendationCount: Int,
        lastCheckpointAtMs: Long,
        nowMs: Long,
        marketOpen: Boolean
    ): Boolean = when (trigger) {
        "RECOMMENDATIONS" ->
            currentRecommendationCount - archivedRecommendationCount >= RECOMMENDATION_BATCH_SIZE
        "TIME" -> marketOpen && (lastCheckpointAtMs <= 0L ||
            nowMs - lastCheckpointAtMs >= TIME_INTERVAL_MS)
        else -> false
    }
}
