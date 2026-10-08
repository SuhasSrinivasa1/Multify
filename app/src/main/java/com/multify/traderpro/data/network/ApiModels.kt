package com.multify.traderpro.data.network

data class BrokerStatusDto(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val name: String = "Groww",
    val detail: String? = null
)

data class RiskStatusDto(
    val maxDailyLoss: Double = 2_500.0,
    val softDailyLoss: Double = 1_500.0,
    val profitMilestone: Double = 5_000.0,
    val riskPerTrade: Double = 0.0,
    val maxExposure: Double = 0.0,
    val riskMode: String = "NORMAL",
    val dailyPeakPnl: Double = 0.0
)

data class DaySummaryDto(
    val realisedPnl: Double = 0.0,
    val unrealisedPnl: Double = 0.0,
    val trades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val grossExposure: Double = 0.0
) {
    val winRate: Double get() = if (wins + losses == 0) 0.0 else wins.toDouble() / (wins + losses).toDouble() * 100.0
    val totalPnl: Double get() = realisedPnl + unrealisedPnl
}

data class PositionDto(
    val symbol: String = "",
    val side: String = "FLAT",
    val quantity: Int = 0,
    val averagePrice: Double = 0.0,
    val ltp: Double? = null,
    val pnl: Double = 0.0,
    val stopPrice: Double? = null,
    val targetPrice: Double? = null,
    val strategy: String? = null
)

data class RecentDecisionDto(
    val at: String = "",
    val symbol: String? = null,
    val action: String = "",
    val reason: String = "",
    val strategy: String? = null,
    val quantity: Int = 0
)

data class DashboardDto(
    val serviceStatus: String = "device",
    val mode: String = "paper",
    val armed: Boolean = false,
    val halted: Boolean = false,
    val marketSession: String = "UNKNOWN",
    val asOf: String = "",
    val broker: BrokerStatusDto = BrokerStatusDto(),
    val risk: RiskStatusDto = RiskStatusDto(),
    val summary: DaySummaryDto = DaySummaryDto(),
    val fastTrackSummary: DaySummaryDto = DaySummaryDto(),
    val combinedAppPnl: Double = 0.0,
    val shadowQualificationDays: Int = 0,
    val learning: LearningStatsDto = LearningStatsDto(),
    val forecasts: List<ForecastDto> = emptyList(),
    val research: ResearchDto = ResearchDto(),
    val strategyInsights: List<StrategyInsightDto> = emptyList(),
    val waveStats: List<WaveStatDto> = emptyList(),
    val waveSignals: List<WaveSignalDto> = emptyList(),
    val positions: List<PositionDto> = emptyList(),
    val recentDecisions: List<RecentDecisionDto> = emptyList()
)


data class WaveStatDto(
    val wave: Int = 1,
    val averageUpPct: Double? = null,
    val averageDownPct: Double? = null,
    val upSamples: Int = 0,
    val downSamples: Int = 0,
    val upSource: String = "LIVE_PIVOT",
    val downSource: String = "LIVE_PIVOT"
)

data class WaveSignalDto(
    val symbol: String = "",
    val currentSide: String = "FLAT",
    val nextWave: Int = 2,
    val triggerDistancePct: Double = 2.0,
    val greenSide: String = "HOLD",
    val longProbability: Double = 0.5,
    val shortProbability: Double = 0.5,
    val executionMode: String = "PREVIEW_ONLY",
    val focusState: String = "ACTIVE",
    val firstWaveMode: String = "AUTO",
    val learnedLongAveragePct: Double = 0.0,
    val learnedShortRetracementPct: Double = 100.0,
    val reason: String = ""
)

data class LearningStatsDto(
    val rollingCalendarDays: Int = 30,
    val rollingTradingDays: Int = 0,
    val rollingCalls: Int = 0,
    val longAveragePct: Double = 0.0,
    val longMedianPct: Double = 0.0,
    val seededThreeMonthAveragePct: Double = 0.0,
    val liveCompletedCalls: Int = 0,
    val shortObservedCalls: Int = 0,
    val shortRetracementFraction: Double = 1.0,
    val shortRetracementPct: Double = 100.0
)

data class ForecastDto(
    val rank: Int = 0,
    val symbol: String = "",
    val bias: String = "LONG",
    val confidence: Double = 0.0,
    val score: Double = 0.0,
    val reason: String = "",
    val multifyMatched: Boolean = false,
    val multifyDirectionMatched: Boolean = false
)

data class ResearchDto(
    val date: String = "",
    val title: String = "No research report yet",
    val summary: String = ""
)


data class StrategyInsightDto(
    val atMs: Long = 0L,
    val symbol: String = "",
    val side: String = "LONG",
    val strategy: String = "",
    val regime: String = "",
    val confidence: Double = 0.0,
    val score: Double = 0.0,
    val ltp: Double = 0.0,
    val rsi: Double? = null,
    val rvol: Double? = null,
    val orderBookImbalance: Double? = null,
    val votes: String = ""
)
