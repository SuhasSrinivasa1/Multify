package com.multify.traderpro.data.network

data class BrokerStatusDto(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val name: String = "Groww",
    val detail: String? = null
)

data class RiskStatusDto(
    val maxExposure: Double = 0.0,
    val riskMode: String = "NORMAL"
)

data class DaySummaryDto(
    val unrealisedPnl: Double = 0.0,
    val grossExposure: Double = 0.0,
    val openPositions: Int = 0
)

data class PositionDto(
    val symbol: String = "",
    val quantity: Int = 0,
    val averagePrice: Double = 0.0,
    val ltp: Double? = null,
    val pnl: Double = 0.0,
    val stopPrice: Double? = null,
    val targetPrice: Double? = null,
    val strategy: String? = null,
    val product: String = "CNC"
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
    val armed: Boolean = false,
    val halted: Boolean = false,
    val marketSession: String = "UNKNOWN",
    val asOf: String = "",
    val broker: BrokerStatusDto = BrokerStatusDto(),
    val risk: RiskStatusDto = RiskStatusDto(),
    val summary: DaySummaryDto = DaySummaryDto(),
    val learning: LearningStatsDto = LearningStatsDto(),
    val forecastLearning: ForecastLearningStatsDto = ForecastLearningStatsDto(),
    val forecasts: List<ForecastDto> = emptyList(),
    val forecastChampions: List<ForecastChampionDto> = emptyList(),
    val research: ResearchDto = ResearchDto(),
    val health: EngineHealthDto = EngineHealthDto(),
    val positions: List<PositionDto> = emptyList(),
    val recentDecisions: List<RecentDecisionDto> = emptyList()
)

data class LearningStatsDto(
    val rollingTradingDays: Int = 0,
    val rollingCalls: Int = 0,
    val longAveragePct: Double = 0.0,
    val longMedianPct: Double = 0.0,
    val longTrimmedMeanPct: Double = 0.0,
    val longEwmaPct: Double = 0.0,
    val longP25Pct: Double = 0.0,
    val longP75Pct: Double = 0.0,
    val seededThreeMonthAveragePct: Double = 0.0,
    val liveCompletedCalls: Int = 0
)

data class ForecastLearningStatsDto(
    val rollingTradingDays: Int = 0,
    val completedForecasts: Int = 0,
    val longAveragePct: Double = 0.0
)

data class ForecastDto(
    val rank: Int = 0,
    val symbol: String = "",
    val confidence: Double = 0.0,
    val score: Double = 0.0,
    val reason: String = "",
    val multifyMatched: Boolean = false,
    val entryPrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val targetPct: Double = 0.0,
    val status: String = "ACTIVE",
    val strategy: String = "",
    val regime: String = "UNKNOWN",
    val marketRegime: String = "UNKNOWN",
    val championTag: String = "",
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val generatedAtMs: Long = 0L
)

data class ForecastChampionDto(
    val marketRegime: String = "UNKNOWN",
    val regime: String = "UNKNOWN",
    val strategy: String = "",
    val wins: Int = 0,
    val losses: Int = 0,
    val sampleCount: Int = 0,
    val distinctDays: Int = 0,
    val distinctSymbols: Int = 0,
    val frozen: Boolean = false
)

data class ResearchDto(
    val date: String = "",
    val title: String = "No research report yet",
    val summary: String = ""
)

data class EngineHealthDto(
    val listener: String = "UNKNOWN",
    val broker: String = "UNKNOWN",
    val marketData: String = "UNKNOWN",
    val symbolMaster: String = "UNKNOWN",
    val foregroundService: String = "UNKNOWN",
    val lastNotificationAtMs: Long = 0L,
    val reconnectCount: Long = 0L,
    val lastReconnectAtMs: Long = 0L,
    val lastEventProcessingLatencyMs: Long = 0L,
    val lastOrderDispatchPrepMicros: Long = 0L,
    val lastBrokerAckLatencyMs: Long = 0L,
    val marketDataAgeMs: Long = Long.MAX_VALUE
)
