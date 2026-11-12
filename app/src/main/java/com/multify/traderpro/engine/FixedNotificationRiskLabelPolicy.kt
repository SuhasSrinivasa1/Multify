package com.multify.traderpro.engine

/** Display only; never interprets caution as authorization to place an order. */
object FixedNotificationRiskLabelPolicy {
    enum class Level { BUY, CAUTION, RESEARCH_ONLY }
    fun level(executionEligible: Boolean, reason: String): Level = when {
        !executionEligible || reason.startsWith("FIXED RESEARCH ONLY") -> Level.RESEARCH_ONLY
        reason.startsWith("FIXED ENTRY CAUTION") -> Level.CAUTION
        else -> Level.BUY
    }
    fun title(level: Level, symbol: String): String = when (level) {
        Level.BUY -> "Fixed BUY · $symbol"
        Level.CAUTION -> "Fixed BUY · CAUTION · $symbol"
        Level.RESEARCH_ONLY -> "Fixed WATCH · $symbol"
    }
}
