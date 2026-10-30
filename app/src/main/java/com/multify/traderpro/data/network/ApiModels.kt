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

data class ExecutionAverageDto(
    val averageFillMs: Double = 0.0,
    val averageAppDispatchMs: Double = 0.0,
    val sampleCount: Int = 0,
    val dispatchSampleCount: Int = 0
)

data class ExecutionStatsDto(
    val buyLong: ExecutionAverageDto = ExecutionAverageDto(),
    val sellLong: ExecutionAverageDto = ExecutionAverageDto()
)

data class PositionDto(
    val symbol: String = "",
    val engine: String = "",
    val quantity: Int = 0,
    val averagePrice: Double = 0.0,
    val ltp: Double? = null,
    val pnl: Double = 0.0,
    val stopPrice: Double? = null,
    val targetPrice: Double? = null,
    val highWaterPrice: Double? = null,
    val trailingLastRatchetPrice: Double? = null,
    val trailDistance: Double? = null,
    val trailArmPct: Double? = null,
    val trailArmed: Boolean = false,
    val smartOrderId: String? = null,
    val smartOrderMode: String = "NONE",
    val smartOrderTriggerPrice: Double? = null,
    val smartOrderDirection: String? = null,
    val smartOrderStatus: String? = null,
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
    val execution: ExecutionStatsDto = ExecutionStatsDto(),
    val learning: LearningStatsDto = LearningStatsDto(),
    val forecastLearning: ForecastLearningStatsDto = ForecastLearningStatsDto(),
    val forecastReferences: ForecastReferenceStatsDto = ForecastReferenceStatsDto(),
    val fixedOutcomes: FixedOutcomeStatsDto = FixedOutcomeStatsDto(),
    val forecasts: List<ForecastDto> = emptyList(),
    val fixedForecasts: List<ForecastDto> = emptyList(),
    val forecastWatchlist: List<ForecastDto> = emptyList(),
    val forecastChampions: List<ForecastChampionDto> = emptyList(),
    val research: ResearchDto = ResearchDto(),
    val health: EngineHealthDto = EngineHealthDto(),
    val fixedHealth: EngineHealthDto = EngineHealthDto(),
    val fixedSnapshot: FixedSnapshotDto = FixedSnapshotDto(),
    val positions: List<PositionDto> = emptyList(),
    val recentDecisions: List<RecentDecisionDto> = emptyList()
)

data class FixedSnapshotDto(
    val snapshotId: String = "",
    val sourceVersion: String = "",
    val frozenAtMs: Long = 0L,
    val sha256: String = "",
    val learningCalls: Int = 0,
    val historicalForecasts: Int = 0,
    val researchSnapshots: Int = 0,
    val champions: Int = 0,
    val frozenMultifyMfeAveragePct: Double = 0.0,
    val targetFloorPct: Double = 1.0,
    val fixedTargetPct: Double = 1.0
)

data class LearningStatsDto(
    val rollingTradingDays: Int = 0,
    val rollingCalls: Int = 0,
    val longAveragePct: Double = 0.0,
    val todayAveragePct: Double = 0.0,
    val longMedianPct: Double = 0.0,
    val longTrimmedMeanPct: Double = 0.0,
    val longEwmaPct: Double = 0.0,
    val longP25Pct: Double = 0.0,
    val longP75Pct: Double = 0.0,
    val seededThreeMonthAveragePct: Double = 0.0,
    val liveCompletedCalls: Int = 0,
    val mfeBackfillPendingCalls: Int = 0
)

data class FixedOutcomeStatsDto(
    val published: Int = 0,
    val observed: Int = 0,
    val todayPublished: Int = 0,
    val todayObserved: Int = 0,
    val todayTargetAveragePct: Double? = null,
    val todayObservedAveragePct: Double? = null,
    val rollingObservedAveragePct: Double? = null,
    val armHits: Int = 0,
    val onePctHits: Int = 0,
    val fullTargetHits: Int = 0,
    val armHitRatePct: Double? = null,
    val onePctHitRatePct: Double? = null,
    val fullTargetHitRatePct: Double? = null,
    val armHitMeanMfePct: Double? = null,
    val onePctHitMeanMfePct: Double? = null,
    val fullHitMeanMfePct: Double? = null,
    val p25Pct: Double? = null,
    val medianPct: Double? = null,
    val p75Pct: Double? = null
)

data class ForecastReferenceStatsDto(
    val frozenCases: Int = 107,
    val appendedCalls: Int = 0,
    val totalCalls: Int = 107,
    val distinctSymbols: Int = 0,
    val liveCompleted: Int = 0,
    val liveMfeMeasured: Int = 0,
    val livePendingOutcome: Int = 0,
    val latestLiveCallDate: String? = null
)

data class ForecastLearningStatsDto(
    val rollingTradingDays: Int = 0,
    val completedForecasts: Int = 0,
    val longAveragePct: Double = 0.0,
    val todayTargetAveragePct: Double = 0.0,
    val targetFloorPct: Double = 1.0,
    val researchSessionsCaptured: Int = 0,
    val researchSessionGoal: Int = 5,
    val evaluatedCompletedForecasts: Int = 0,
    val trailArmHitRatePct: Double = 0.0,
    val onePctHitRatePct: Double = 0.0,
    val targetHitRatePct: Double = 0.0,
    val mfeP25Pct: Double = 0.0,
    val mfeMedianPct: Double = 0.0,
    val mfeP75Pct: Double = 0.0
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
    val generatedAtMs: Long = 0L,
    val observedAfterPublication: Boolean = false,
    val qualityTier: String = "LEGACY",
    val executionEligible: Boolean = false
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
    val discovery: String = "UNKNOWN",
    val discoveryCoveragePct: Double = 0.0,
    val discoveryBaselineAgeMs: Long = Long.MAX_VALUE,
    val discoveryBatchErrors: Int = 0,
    val discoveryBaselineReady: Boolean = false,
    val lastNotificationAtMs: Long = 0L,
    val reconnectCount: Long = 0L,
    val lastReconnectAtMs: Long = 0L,
    val lastEventProcessingLatencyMs: Long = 0L,
    val lastOrderDispatchPrepMicros: Long = 0L,
    val lastBrokerAckLatencyMs: Long = 0L,
    val marketDataAgeMs: Long = Long.MAX_VALUE
)
