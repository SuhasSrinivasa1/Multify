package com.multify.traderpro.data.repository

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.multify.traderpro.BuildConfig
import com.multify.traderpro.data.local.ExecutionSampleEntity
import com.multify.traderpro.data.local.ForecastChampionEntity
import com.multify.traderpro.data.local.ForecastResearchEntity
import com.multify.traderpro.data.local.ForecastPositiveEvidenceEntity
import com.multify.traderpro.data.local.LongForecastEntity
import com.multify.traderpro.data.local.LearningCallEntity
import com.multify.traderpro.data.local.LearningDao
import com.multify.traderpro.data.local.ManagedPositionEntity
import com.multify.traderpro.data.local.ManagedTradeDao
import com.multify.traderpro.data.local.ManagedTradeEntity
import com.multify.traderpro.data.local.PriceObservationEntity
import com.multify.traderpro.data.local.ResearchReportEntity
import com.multify.traderpro.data.local.SignalEventDao
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.logging.FixedFactorTelemetry
import com.multify.traderpro.data.network.BrokerStatusDto
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.DaySummaryDto
import com.multify.traderpro.data.network.EngineHealthDto
import com.multify.traderpro.data.network.ExecutionAverageDto
import com.multify.traderpro.data.network.ExecutionStatsDto
import com.multify.traderpro.data.network.ForecastChampionDto
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.ForecastLearningStatsDto
import com.multify.traderpro.data.network.ForecastReferenceStatsDto
import com.multify.traderpro.data.network.FixedSnapshotDto
import com.multify.traderpro.data.network.FixedOutcomeStatsDto
import com.multify.traderpro.data.network.GttCreateRequest
import com.multify.traderpro.data.network.GttModifyRequest
import com.multify.traderpro.data.network.GttOrder
import com.multify.traderpro.data.network.GrowwApiFactory
import com.multify.traderpro.data.network.GrowwApiUsageLedger
import com.multify.traderpro.data.network.BetaResearchGate
import com.multify.traderpro.data.network.GrowwRecoveryGate
import com.multify.traderpro.data.network.GrowwHolding
import com.multify.traderpro.data.network.GrowwPosition
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.LearningStatsDto
import com.multify.traderpro.data.network.OrderCancelRequest
import com.multify.traderpro.data.network.OrderCreateRequest
import com.multify.traderpro.data.network.OrderPayload
import com.multify.traderpro.data.network.OrderListPayload
import com.multify.traderpro.data.network.OrderTradeRecord
import com.multify.traderpro.data.network.OrderListPayload
import com.multify.traderpro.data.network.OrderTradeRecord
import com.multify.traderpro.data.network.PositionDto
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.RecentDecisionDto
import com.multify.traderpro.data.network.ResearchDto
import com.multify.traderpro.data.network.SmartOrderPayload
import com.multify.traderpro.data.network.RiskStatusDto
import com.multify.traderpro.data.network.TokenRequest
import com.multify.traderpro.data.preferences.AppPreferences
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.preferences.SecretStore
import com.multify.traderpro.data.security.TotpGenerator
import com.multify.traderpro.domain.NotificationParser
import com.multify.traderpro.domain.ParsedSignal
import com.multify.traderpro.domain.SignalType
import com.multify.traderpro.engine.AdaptiveLearningMath
import com.multify.traderpro.engine.CashAwareCncSizing
import com.multify.traderpro.engine.BrokerInventorySymbolPolicy
import com.multify.traderpro.engine.AutoBuyEntrySafetyPolicy
import com.multify.traderpro.engine.ExecutionTelemetryMath
import com.multify.traderpro.engine.ForecastAutoBuyPolicy
import com.multify.traderpro.engine.ForecastAdaptiveSelectionPolicy
import com.multify.traderpro.engine.ForecastEconomicsDecision
import com.multify.traderpro.engine.ForecastEvidenceDiagnostics
import com.multify.traderpro.engine.ForecastFamilyOutcome
import com.multify.traderpro.engine.ForecastHistoricalOutcome
import com.multify.traderpro.engine.ForecastFixedPositiveTransferPolicy
import com.multify.traderpro.engine.ForecastMultifyPositiveTransferPolicy
import com.multify.traderpro.engine.ForecastMultifyReferenceLedger
import com.multify.traderpro.engine.ForecastPreSpikeContextPolicy
import com.multify.traderpro.engine.ForecastEntryTimingPolicy
import com.multify.traderpro.engine.MultifyPositiveTransferCase
import com.multify.traderpro.engine.FixedPositiveTransferCase
import com.multify.traderpro.engine.ForecastQualityDecision
import com.multify.traderpro.engine.ForecastQualityPolicy
import com.multify.traderpro.engine.ForecastTargetExpansionPolicy
import com.multify.traderpro.engine.ForecastTargetMaturityPolicy
import com.multify.traderpro.engine.NetProfitProtectionPolicy
import com.multify.traderpro.engine.ForecastTargetMaturityPolicy
import com.multify.traderpro.engine.NetProfitProtectionPolicy
import com.multify.traderpro.engine.ForecastUniversalPositiveEvidencePolicy
import com.multify.traderpro.engine.ForecastPositiveEvidenceCase
import com.multify.traderpro.engine.ForecastPublicationPolicy
import com.multify.traderpro.engine.FixedAdaptiveLearningMath
import com.multify.traderpro.engine.FixedForecastPublicationPolicy
import com.multify.traderpro.engine.FixedFinalContract
import com.multify.traderpro.engine.FixedForecastQualityDecision
import com.multify.traderpro.engine.FixedForecastQualityPolicy
import com.multify.traderpro.engine.FixedLocalStrategyEngine
import com.multify.traderpro.engine.FixedMarketDiscoveryPolicy
import com.multify.traderpro.engine.FixedMultifyFactorFamilies
import com.multify.traderpro.engine.FixedMultifyFactorModel
import com.multify.traderpro.engine.FixedMultifyReverseEngineering
import com.multify.traderpro.engine.FixedPositiveCloseForecastPolicy
import com.multify.traderpro.engine.FixedScanPerformancePolicy
import com.multify.traderpro.engine.FixedDiscoveryWindowScheduler
import com.multify.traderpro.engine.FixedHistoricalWindowStore
import com.multify.traderpro.engine.FixedHistoricalSlotPolicy
import com.multify.traderpro.engine.FixedStrategyEvaluation
import com.multify.traderpro.engine.GrowwDeliveryCostPolicy
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import com.multify.traderpro.engine.HardStopLossPolicy
import com.multify.traderpro.engine.AppPositionOwnershipPolicy
import com.multify.traderpro.engine.HoldingSmartOrderPolicy
import com.multify.traderpro.engine.BrokerExecutionSafetyPolicy
import com.multify.traderpro.engine.MarketPriorityPolicy
import com.multify.traderpro.engine.ForecastResearchWindowPolicy
import com.multify.traderpro.engine.ForecastBetaPriorityPolicy
import com.multify.traderpro.engine.LocalStrategyEngine
import com.multify.traderpro.engine.MarketDiscoveryPolicy
import com.multify.traderpro.engine.MultifyFactorFamilies
import com.multify.traderpro.engine.MultifyFactorModel
import com.multify.traderpro.engine.MultifyReverseEngineering
import com.multify.traderpro.engine.PositiveCloseForecastPolicy
import com.multify.traderpro.engine.SteppedTrailingPolicy
import com.multify.traderpro.engine.StrategyEvaluation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AuthenticationResult(
    val authenticated: Boolean,
    val ucc: String,
    val nseCashEnabled: Boolean,
    val publicIp: String,
    val staticIpMatched: Boolean,
    val accessTokenExpiry: String,
    val cncBalanceAvailable: Double,
    val ddpiEnabled: Boolean,
    val detail: String
)

@Singleton
class TradingRepository @Inject constructor(
    private val dao: SignalEventDao,
    private val managedDao: ManagedTradeDao,
    private val learningDao: LearningDao,
    private val preferences: AppPreferences,
    private val secretStore: SecretStore,
    private val apiFactory: GrowwApiFactory,
    private val betaResearchGate: BetaResearchGate,
    private val recoveryGate: GrowwRecoveryGate,
    private val brokerRetryGuard: BrokerExecutionRetryGuard,
    private val parser: NotificationParser,
    private val nseSymbols: NseSymbolRepository,
    private val auditLogger: AuditLogger,
    private val fixedFactorTelemetry: FixedFactorTelemetry,
    private val marketDiscoveryStateStore: MarketDiscoveryStateStore,
    private val forecastAutoBuyOrderStore: ForecastAutoBuyOrderStore,
    private val fixedEngineSnapshotStore: FixedEngineSnapshotStore,
    private val embeddedHistoricalIntelligenceStore: EmbeddedHistoricalIntelligenceStore,
    private val fixedRecommendationStore: FixedRecommendationStore,
    private val fixedOutcomePerformanceStore: FixedOutcomePerformanceStore,
    private val fixedAutoBuyOrderStore: FixedAutoBuyOrderStore,
    private val fixedMarketDiscoveryStateStore: FixedMarketDiscoveryStateStore,
    @ApplicationContext private val appContext: Context
) {
    private val _fixedDeliveryEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val fixedDeliveryEvents: SharedFlow<Long> = _fixedDeliveryEvents.asSharedFlow()

    val settings: Flow<AppSettings> = preferences.settings
    val recentEvents: Flow<List<SignalEventEntity>> = dao.observeRecent(100)

    @Volatile private var historicalSeedEnsured = false
    @Volatile private var embeddedHistoricalIntelligenceEnsured = false
    @Volatile private var fixedSnapshotAudited = false
    @Volatile private var armHotState: ArmHotState? = null
    // Adaptive-only resume cursor: repeated Fixed preemption must not restart NSE sweep at symbol zero.
    private var forecastDiscoveryDate = ""
    private var forecastDiscoveryNextBatch = 0
    private val forecastDiscoveryPartialLtps = linkedMapOf<String, Double>()
    private var forecastDiscoveryBatchErrors = 0
    private var forecastDiscoveryMustClear = false
    private var forecastWaitingForSharedBaseline = false
    @Volatile private var cachedBetaResearchToken: String? = null
    @Volatile private var betaSessionInvalid = false
    private var lastForecastCapacitySkipLogAtMs = 0L
    private var lastForecastScanAtMs = 0L
    // Forecast-only checkpoint: preserve unprocessed candidates across Fixed-priority yields.
    // Do not retain evaluation/price objects: each resumed symbol receives fresh market data.
    private var forecastDetailDate = ""
    private var forecastDetailQueue: List<String> = emptyList()
    private val forecastDetailRetryAttempts = mutableMapOf<String, Int>()
    // Research-only retry memory. Transient broker timeouts must not pin the first 24
    // replay symbols forever; this does not influence production trading decisions.
    private val forecastReplayRetryAfterMs = ConcurrentHashMap<String, Long>()
    private val forecastReplayFailureCount = ConcurrentHashMap<String, Int>()
    private var forecastDetailCursor = 0

    // The scheduling timestamp remains frozen; this flag only prevents auto-BUY on incomplete scans.
    @Volatile private var fixedScanDeferredBroker = false
    // In-memory historical OHLCV reuse across Fixed cycles; live latest bars are ALWAYS refetched.
    private val fixedHistoricalWindowStore = FixedHistoricalWindowStore()
    private val fixedReuseDiagnosticLastLoggedAtMs = AtomicLong(0L)
    private val fixedHistoryFullFetches = AtomicLong(0L)
    private val fixedHistoryDeltaFetches = AtomicLong(0L)
    private val fixedHistoryStitchFallbacks = AtomicLong(0L)
    fun fixedHistoricalRequestCounters(): Triple<Long, Long, Long> = Triple(
        fixedHistoryFullFetches.get(), fixedHistoryDeltaFetches.get(), fixedHistoryStitchFallbacks.get()
    )
    @Volatile private var forecastPostFixedOpportunity = false
    private var lastFixedScanAtMs = 0L
    private var lastForecastOutcomeMonitorAtMs = 0L
    private var lastFixedOutcomeMonitorAtMs = 0L
    private var lastFixedPreMarketWarmupAtMs = 0L
    private var lastFixedHotPrewarmForDueAtMs = 0L
    private var brokerInventorySnapshot: BrokerInventorySnapshot? = null
    @Volatile private var activeGttSnapshot: ActiveGttSnapshot? = null
    @Volatile private var orderListSnapshot: OrderListSnapshot? = null
    @Volatile private var orderListSnapshot: OrderListSnapshot? = null
    private var lastClosedSessionReconcileAtMs = 0L
    private var lastIdlePortfolioAuditAtMs = 0L
    private val lastPortfolioConflictAuditAtMs = ConcurrentHashMap<String, Long>()
    private val recentlyClosedSymbolsAtMs = ConcurrentHashMap<String, Long>()
    private var lastArmHotAuditAtMs = 0L
    private var lastMfeBackfillAtMs = 0L
    private var lastArmHotAuditBalance = Double.NaN
    private var lastArmHotAuditArmed: Boolean? = null
    private val closePositionMutexes = ConcurrentHashMap<Long, Mutex>()
    private val appOwnedBuyMutex = Mutex()
    private val forecastAutoBuySubmissionMutex = Mutex()
    private val fixedAutoBuySubmissionMutex = Mutex()
    private val scanApiCycleMutex = Mutex()
    private val betaForecastCycleMutex = Mutex()
    @Volatile private var fixedApiPriorityRequested = false
    @Volatile private var marketCriticalTradeInFlight = false
    @Volatile private var tradingRecoveryReady = false
    private val embeddedHistoricalSeedMutex = Mutex()
    private val bulkMarketDataMutex = Mutex()
    private val learningRestoreMutex = Mutex() // Multiple user taps cannot interleave imports.
    private var nextBulkMarketDataAtElapsedMs = 0L
    private val fixedLiveDataRateMutex = Mutex()
    private val fixedLiveDataSecondWindowMs = mutableListOf<Long>()
    private val fixedLiveDataMinuteWindowMs = mutableListOf<Long>()
    private var fixedLiveDataCooldownUntilElapsedMs = 0L
    private val fixedHistoricalDataMutex = Mutex()
    private var nextFixedHistoricalAtElapsedMs = 0L
    private var fixedHistoricalCooldownUntilElapsedMs = 0L
    @Volatile private var activePortfolioMonitorCount = 0
    private val lastTrailStateAuditAtMs = ConcurrentHashMap<Long, Long>()
    private val lastTrailStateAuditSignature = ConcurrentHashMap<Long, String>()
    private val lastSmartOrderStatusAtMs = ConcurrentHashMap<Long, Long>()
    private val lastGttChildReconcileAuditAtMs = ConcurrentHashMap<String, Long>()
    private val lastGttChildPollAtMs = ConcurrentHashMap<String, Long>()

    private data class MarketDiscoveryPass(
        val symbols: List<String>,
        val baselineReady: Boolean,
        val coverageRatio: Double,
        val restoredFromDisk: Boolean,
        val batchErrors: Int
    )

    private data class ForecastCandidate(
        val symbol: String,
        val evaluation: StrategyEvaluation,
        val quality: ForecastQualityDecision,
        val factorModel: MultifyFactorModel,
        val score: Double,
        val championKey: String,
        val targetPct: Double,
        val economics: ForecastEconomicsDecision,
        val evidenceDiagnostics: ForecastEvidenceDiagnostics
    )

    private data class FixedCandidate(
        val symbol: String,
        val evaluation: FixedStrategyEvaluation,
        val quality: FixedForecastQualityDecision,
        val factorModel: FixedMultifyFactorModel,
        val score: Double,
        val championKey: String
    )

    private data class ResearchCandle(
        val atMs: Long,
        val high: Double,
        val low: Double,
        val close: Double,
        val open: Double = close,
        val volume: Double = 0.0
    )

    private val researchGson = GsonBuilder().disableHtmlEscaping().create()

    private data class BrokerInventorySnapshot(
        val holdings: Map<String, GrowwHolding>,
        val positions: Map<String, GrowwPosition>,
        val atMs: Long
    )

    private data class ActiveGttSnapshot(
        val bySymbol: Map<String, List<SmartOrderPayload>>,
        val atMs: Long
    )

    private data class OrderListSnapshot(
        val orders: List<OrderPayload>,
        val atMs: Long
    )

    private data class GttChildFillStats(
        val filledQuantity: Int,
        val averageFillPrice: Double?,
        val terminal: Boolean,
        val status: String,
        val order: OrderPayload
    )

    private data class OrderListSnapshot(
        val orders: List<OrderPayload>,
        val atMs: Long
    )

    private data class GttChildFillStats(
        val filledQuantity: Int,
        val averageFillPrice: Double?,
        val terminal: Boolean,
        val status: String,
        val order: OrderPayload
    )

    private data class ArmHotState(
        val token: String,
        val settings: AppSettings,
        val cncBalance: Double,
        val warmedAtMs: Long
    )

    private suspend fun ensureEmbeddedHistoricalIntelligence(): EmbeddedHistoricalIntelligencePayload {
        val payload = embeddedHistoricalIntelligenceStore.load()
        if (embeddedHistoricalIntelligenceEnsured) return payload

        embeddedHistoricalSeedMutex.withLock {
            if (!embeddedHistoricalIntelligenceEnsured) {
                var insertedCalls = 0
                var enrichedCalls = 0
                var insertedForecasts = 0
                var insertedResearch = 0
                var insertedChampions = 0
                var insertedReports = 0

                payload.fixedSnapshot.learningCalls.forEach { seed ->
                    val symbol = seed.symbol.uppercase(Locale.US)
                    val existing = learningDao.callForDateSymbolAction(seed.callDate, symbol, "BUY")
                    if (existing == null) {
                        learningDao.insertCall(
                            seed.copy(
                                id = 0,
                                symbol = symbol,
                                action = "BUY",
                                updatedAtMs = payload.latestExportAtMs
                            )
                        )
                        insertedCalls++
                    } else {
                        val merged = existing.copy(
                            targetPrice = existing.targetPrice ?: seed.targetPrice,
                            targetPct = existing.targetPct ?: seed.targetPct,
                            multifyExitPrice = existing.multifyExitPrice ?: seed.multifyExitPrice,
                            longRealizedPct = existing.longRealizedPct ?: seed.longRealizedPct,
                            multifyMaxPrice = existing.multifyMaxPrice ?: seed.multifyMaxPrice,
                            multifyMfePct = existing.multifyMfePct ?: seed.multifyMfePct
                        )
                        if (merged != existing) {
                            learningDao.updateCall(merged)
                            enrichedCalls++
                        }
                    }
                }

                val existingForecastKeys = learningDao.allLongForecasts()
                    .map { it.forecastDate + "|" + it.symbol.uppercase(Locale.US) }
                    .toHashSet()
                payload.fixedSnapshot.longForecasts.forEach { seed ->
                    val symbol = seed.symbol.uppercase(Locale.US)
                    val key = seed.forecastDate + "|" + symbol
                    if (key !in existingForecastKeys) {
                        learningDao.upsertLongForecast(
                            seed.copy(id = 0, symbol = symbol, notified = true)
                        )
                        existingForecastKeys += key
                        insertedForecasts++
                    }
                }

                val existingResearchKeys = learningDao.allForecastResearch()
                    .map {
                        it.researchDate + "|" + it.scanAtMs + "|" +
                            it.symbol.uppercase(Locale.US) + "|" + it.rank
                    }
                    .toHashSet()
                payload.fixedSnapshot.researchSnapshots.forEach { seed ->
                    val symbol = seed.symbol.uppercase(Locale.US)
                    val key = seed.researchDate + "|" + seed.scanAtMs + "|" + symbol + "|" + seed.rank
                    if (key !in existingResearchKeys) {
                        learningDao.insertForecastResearch(seed.copy(id = 0, symbol = symbol))
                        existingResearchKeys += key
                        insertedResearch++
                    }
                }

                val existingChampionKeys = learningDao.allForecastChampions().map { it.key }.toHashSet()
                payload.fixedSnapshot.champions.forEach { seed ->
                    if (seed.key.isNotBlank() && seed.key !in existingChampionKeys) {
                        learningDao.upsertForecastChampion(seed)
                        existingChampionKeys += seed.key
                        insertedChampions++
                    }
                }

                val existingReportDates = learningDao.allResearchReports().map { it.reportDate }.toHashSet()
                payload.reports.forEach { seed ->
                    if (seed.reportDate.isNotBlank() && seed.reportDate !in existingReportDates) {
                        learningDao.insertResearchReport(seed.copy(id = 0))
                        existingReportDates += seed.reportDate
                        insertedReports++
                    }
                }

                embeddedHistoricalIntelligenceEnsured = true
                auditLogger.log("MIGRATION", "EMBEDDED_HISTORIC_INTELLIGENCE_READY", mapOf(
                    "schema_version" to payload.schemaVersion,
                    "source_exports" to payload.sourceExports,
                    "latest_export_at_ms" to payload.latestExportAtMs,
                    "embedded_learning_calls" to payload.fixedSnapshot.learningCalls.size,
                    "embedded_forecasts" to payload.fixedSnapshot.longForecasts.size,
                    "embedded_research_snapshots" to payload.fixedSnapshot.researchSnapshots.size,
                    "embedded_champions" to payload.fixedSnapshot.champions.size,
                    "embedded_reports" to payload.reports.size,
                    "inserted_learning_calls" to insertedCalls,
                    "enriched_learning_calls" to enrichedCalls,
                    "inserted_forecasts" to insertedForecasts,
                    "inserted_research_snapshots" to insertedResearch,
                    "inserted_champions" to insertedChampions,
                    "inserted_reports" to insertedReports
                ))
            }
        }
        return payload
    }

    internal suspend fun ensureFixedEngineSnapshot(): FixedEngineSnapshot {
        var snapshot = fixedEngineSnapshotStore.loadOrNull()
        if (snapshot == null) {
            // Always derive Fixed from the checksum-verified build-time history seed. Never capture
            // device/adaptive Forecast state: that would make Fixed change across installations.
            val embedded = ensureEmbeddedHistoricalIntelligence()
            val canonicalCalls = embedded.fixedSnapshot.learningCalls
                .filterNot {
                    it.callDate == FixedFinalContract.EXCLUDED_LEARNING_DATE &&
                        it.symbol.equals(FixedFinalContract.EXCLUDED_LEARNING_SYMBOL, true)
                }
            require(canonicalCalls.size == FixedFinalContract.CANONICAL_LEARNING_CALLS) {
                "Final Fixed learning-call contract mismatch: ${canonicalCalls.size}"
            }
            val finalPayload = FixedEngineSnapshotPayload(
                snapshotId = FixedEngineSnapshotStore.SNAPSHOT_ID,
                frozenAtMs = FixedFinalContract.REFERENCE_EXPORT_AT_MS,
                sourceVersion = FixedEngineSnapshotStore.SOURCE_VERSION,
                learningCalls = canonicalCalls,
                // The perfect v7.4.1 session started without prior published-Forecast/champion/
                // completed-research feedback. Keep those feedback channels permanently empty.
                longForecasts = emptyList(),
                researchSnapshots = emptyList(),
                champions = emptyList()
            )
            fixedEngineSnapshotStore.clearAwaitingRestore()
            snapshot = fixedEngineSnapshotStore.restoreExactIfAbsent(finalPayload)
        }

        val frozen = requireNotNull(snapshot)
        require(frozen.payload.snapshotId == FixedFinalContract.CONTRACT_ID) {
            "Final Fixed contract id mismatch"
        }
        require(frozen.payload.learningCalls.size == FixedFinalContract.CANONICAL_LEARNING_CALLS) {
            "Final Fixed learning history changed"
        }
        val frozenMfe = fixedSnapshotMultifyMfeAverage(frozen)
        require(kotlin.math.abs(frozenMfe - FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT) < 1e-9) {
            "Final Fixed MFE calibration changed: $frozenMfe"
        }

        if (!fixedSnapshotAudited) {
            fixedSnapshotAudited = true
            auditLogger.log("FIXED_ENGINE", "FINAL_CONTRACT_READY", mapOf(
                "contract_id" to FixedFinalContract.CONTRACT_ID,
                "source_commit" to FixedFinalContract.SOURCE_COMMIT,
                "source_log_sha256" to FixedFinalContract.SOURCE_LOG_SHA256,
                "snapshot_sha256" to frozen.sha256,
                "learning_calls" to frozen.payload.learningCalls.size,
                "frozen_multify_mfe_avg_pct" to frozenMfe,
                "prior_forecast_feedback_rows" to 0,
                "prior_champion_feedback_rows" to 0,
                "prior_research_feedback_rows" to 0,
                "adaptive_updates_allowed" to false
            ))
        }
        return frozen
    }

    suspend fun ensureHistoricalSeed() {
        ensureEmbeddedHistoricalIntelligence()
        if (historicalSeedEnsured) return
        val now = System.currentTimeMillis()
        var inserted = 0
        appContext.assets.open("multify_history_seed.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val x = line.split(',')
                if (x.size < 11) return@forEach
                val callDate = x[0].trim()
                val symbol = x[1].trim()
                val source = x[2].trim().ifBlank { "SEED" }
                val action = x[3].trim().uppercase(Locale.US)
                val entry = x[4].toDoubleOrNull() ?: return@forEach
                val targetPct = x[5].toDoubleOrNull()
                val targetPrice = x[6].toDoubleOrNull()
                val exit = x[7].toDoubleOrNull()
                val realised = x[8].toDoubleOrNull()
                val buyAtMs = x[9].toLongOrNull() ?: 0L
                val sellAtMs = x[10].toLongOrNull()?.takeIf { it > 0L }
                val existing = learningDao.historicalCallForKey(callDate, symbol, action)
                if (existing == null) {
                    learningDao.insertCall(
                        LearningCallEntity(
                            callDate = callDate, symbol = symbol, source = source, action = action,
                            entryPrice = entry, targetPrice = targetPrice, targetPct = targetPct,
                            multifyExitPrice = exit, longRealizedPct = realised,
                            buyAtMs = buyAtMs, sellAtMs = sellAtMs, updatedAtMs = now
                        )
                    )
                    inserted++
                } else {
                    learningDao.updateCall(
                        existing.copy(
                            source = source, entryPrice = entry, targetPrice = targetPrice, targetPct = targetPct,
                            multifyExitPrice = exit ?: existing.multifyExitPrice,
                            longRealizedPct = realised ?: existing.longRealizedPct,
                            buyAtMs = buyAtMs.takeIf { it > 0L } ?: existing.buyAtMs,
                            sellAtMs = sellAtMs ?: existing.sellAtMs, updatedAtMs = now
                        )
                    )
                }
            }
        }
        runCatching { ensurePgIlRecoveryBenchmark() }
            .onFailure { auditLogger.log("LEARNING", "PGIL_RECOVERY_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
        historicalSeedEnsured = true
        runCatching { repairLiveLearningFromSignals() }
            .onFailure { auditLogger.log("LEARNING", "LIVE_HISTORY_REPAIR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
        if (inserted > 0) auditLogger.log("LEARNING", "LONG_HISTORY_SEEDED", mapOf("inserted" to inserted, "rows" to learningDao.callCount()))
    }

    suspend fun learningStats(): LearningStatsDto {
        ensureHistoricalSeed()
        val mfeCalls = learningDao.allCalls().filter {
            it.action.equals("BUY", true) && it.multifyMfePct != null
        }
        val dates = mfeCalls.mapNotNull { runCatching { LocalDate.parse(it.callDate) }.getOrNull() }
            .distinct().sortedDescending()
        val keepDates = dates.take(30).map { it.toString() }.toSet()
        val rolling = mfeCalls.filter { it.callDate in keepDates }
        val values = rolling.mapNotNull { it.multifyMfePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val dated = rolling
            .sortedWith(compareBy<LearningCallEntity>({ it.callDate }, { it.sellAtMs ?: Long.MAX_VALUE }))
            .mapNotNull { it.multifyMfePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val seedValues = mfeCalls.filter { it.source == "SEED" }
            .mapNotNull { it.multifyMfePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val fallback = 0.0
        val pending = learningDao.closedCallsMissingMfe(1000).size
        val today = LocalDate.now(INDIA).toString()
        val todayValues = mfeCalls
            .filter { it.callDate == today }
            .mapNotNull { it.multifyMfePct }
        val todayAverage = todayValues.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        return LearningStatsDto(
            rollingTradingDays = keepDates.size,
            rollingCalls = values.size,
            longAveragePct = AdaptiveLearningMath.rollingMean(values, fallback),
            todayAveragePct = todayAverage,
            longMedianPct = AdaptiveLearningMath.median(values, fallback),
            longTrimmedMeanPct = AdaptiveLearningMath.trimmedMean(values, fallback),
            longEwmaPct = AdaptiveLearningMath.ewma(dated, fallback),
            longP25Pct = AdaptiveLearningMath.quantile(values, .25, fallback),
            longP75Pct = AdaptiveLearningMath.quantile(values, .75, fallback),
            seededThreeMonthAveragePct = seedValues.takeIf { it.isNotEmpty() }?.average() ?: fallback,
            liveCompletedCalls = mfeCalls.count { it.source == "LIVE" },
            mfeBackfillPendingCalls = pending
        )
    }

    /** Forecast-only cumulative Multify ledger: canonical 107 + subsequent LIVE BUY events. */
    suspend fun forecastReferenceStats(): ForecastReferenceStatsDto {
        val frozen = ensureFixedEngineSnapshot().payload.learningCalls
        val count = ForecastMultifyReferenceLedger.summary(frozen, learningDao.allCalls())
        return ForecastReferenceStatsDto(
            frozenCases = count.frozenCases,
            appendedCalls = count.appendedCalls,
            totalCalls = count.totalCalls,
            distinctSymbols = count.distinctSymbols,
            liveCompleted = count.liveCompleted,
            liveMfeMeasured = count.liveMfeMeasured,
            livePendingOutcome = count.livePendingOutcome,
            latestLiveCallDate = count.latestLiveCallDate
        )
    }

    suspend fun forecastLearningStats(): ForecastLearningStatsDto {
        val all = learningDao.allLongForecasts()
        // Positive-close-only rows remain the training set, per forecasting policy.
        val completedLearning = all.filter {
            it.status in setOf("TARGET_HIT", "MISSED") &&
                PositiveCloseForecastPolicy.isPositiveClose(it.entryPrice, it.lastPrice)
        }
        val dates = completedLearning.mapNotNull { runCatching { LocalDate.parse(it.forecastDate) }.getOrNull() }
            .distinct().sortedDescending()
        val keepDates = dates.take(30).map { it.toString() }.toSet()
        val rollingLearning = completedLearning.filter { it.forecastDate in keepDates }
        val learningMfe = rollingLearning.map { it.maxFavourablePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }

        // Performance diagnostics intentionally include ALL completed forecasts so target calibration
        // cannot look better merely because negative-close examples are excluded from training.
        val completedEvaluation = all.filter { it.status in setOf("TARGET_HIT", "MISSED") }
        val evaluationDates = completedEvaluation
            .mapNotNull { runCatching { LocalDate.parse(it.forecastDate) }.getOrNull() }
            .distinct().sortedDescending()
            .take(30).map { it.toString() }.toSet()
        val rollingEvaluation = completedEvaluation.filter { it.forecastDate in evaluationDates }
        val evaluationMfe = rollingEvaluation.map { it.maxFavourablePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val evaluationCount = rollingEvaluation.size
        fun ratePct(predicate: (LongForecastEntity) -> Boolean): Double =
            if (evaluationCount == 0) 0.0
            else rollingEvaluation.count(predicate).toDouble() / evaluationCount.toDouble() * 100.0

        val today = LocalDate.now(INDIA).toString()
        val todayRows = all.filter { it.forecastDate == today }
        val researchSessions = forecastCalibrationDates().size
        return ForecastLearningStatsDto(
            rollingTradingDays = keepDates.size,
            completedForecasts = rollingLearning.size,
            longAveragePct = AdaptiveLearningMath.rollingMean(learningMfe, 0.0),
            todayTargetAveragePct = todayRows.map { it.targetPct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            targetFloorPct = ForecastQualityPolicy.MIN_TARGET_PCT,
            researchSessionsCaptured = researchSessions,
            researchSessionGoal = RESEARCH_SESSION_GOAL,
            evaluatedCompletedForecasts = evaluationCount,
            trailArmHitRatePct = ratePct { it.maxFavourablePct + 1e-9 >= SteppedTrailingPolicy.ARM_TRIGGER_PCT },
            onePctHitRatePct = ratePct { it.maxFavourablePct + 1e-9 >= 1.0 },
            targetHitRatePct = ratePct { it.status == "TARGET_HIT" },
            mfeP25Pct = AdaptiveLearningMath.quantile(evaluationMfe, 0.25, 0.0),
            mfeMedianPct = AdaptiveLearningMath.quantile(evaluationMfe, 0.50, 0.0),
            mfeP75Pct = AdaptiveLearningMath.quantile(evaluationMfe, 0.75, 0.0)
        )
    }

    private fun multifyReferenceEntry(signal: ParsedSignal, fallback: Double? = null): Double? {
        val low = signal.entryLow?.takeIf { it.isFinite() && it > 0.0 }
        val high = signal.entryHigh?.takeIf { it.isFinite() && it > 0.0 }
        return when {
            low != null && high != null -> (low + high) / 2.0
            high != null -> high
            low != null -> low
            else -> fallback?.takeIf { it.isFinite() && it > 0.0 }
        }
    }

    private suspend fun recordLiveBuy(eventId: Long, signal: ParsedSignal, fallbackPrice: Double? = null) {
        val symbol = signal.symbol ?: return
        val price = multifyReferenceEntry(signal, fallbackPrice) ?: return
        val targetPct = signal.target?.let { ((it - price) / price * 100.0).coerceAtLeast(0.0) }
        val now = System.currentTimeMillis()
        val existing = learningDao.liveCallForBuyEvent(eventId)
        if (existing == null) {
            learningDao.insertCall(
                LearningCallEntity(
                    callDate = LocalDate.now(INDIA).toString(), symbol = symbol, source = "LIVE", action = "BUY",
                    entryPrice = price, targetPrice = signal.target, targetPct = targetPct,
                    multifyMaxPrice = null, multifyMfePct = null, buyEventId = eventId,
                    buyAtMs = now, updatedAtMs = now
                )
            )
        } else {
            learningDao.updateCall(existing.copy(
                entryPrice = price, targetPrice = signal.target ?: existing.targetPrice,
                targetPct = targetPct ?: existing.targetPct, updatedAtMs = now
            ))
        }
        val today = LocalDate.now(INDIA).toString()
        learningDao.markLongForecastMatch(today, symbol)
        learningDao.markForecastResearchMultifyMatch(today, symbol, eventId, now)
    }

    private suspend fun recordLiveExit(
        eventId: Long,
        symbol: String,
        price: Double?,
        reportedReturnPct: Double?,
        eventAtMs: Long = System.currentTimeMillis()
    ): LearningCallEntity? {
        val call = learningDao.latestLiveCallBefore(symbol, eventAtMs) ?: learningDao.latestLiveCall(symbol) ?: return null
        val reported = reportedReturnPct?.takeIf { it.isFinite() && abs(it) < 25.0 }
        val pct = reported ?: AdaptiveLearningMath.realizedReturnPct(null, call.entryPrice, price)
        if (pct == null && price == null) return null
        val updated = call.copy(
            multifyExitPrice = price ?: call.multifyExitPrice,
            longRealizedPct = pct ?: call.longRealizedPct,
            sellEventId = eventId, sellAtMs = eventAtMs, updatedAtMs = System.currentTimeMillis()
        )
        learningDao.updateCall(updated)
        auditLogger.log("LEARNING", "MULTIFY_RETURN_RECORDED", mapOf(
            "symbol" to symbol, "reported_return_pct" to reported, "exit_price" to price, "event_id" to eventId
        ))
        return updated
    }

    private suspend fun captureMultifyMfe(call: LearningCallEntity, token: String): LearningCallEntity? {
        val closeAtMs = call.sellAtMs ?: return null
        if (call.buyAtMs <= 0L || closeAtMs <= call.buyAtMs || call.entryPrice <= 0.0) return null
        val start = Instant.ofEpochMilli(call.buyAtMs).atZone(INDIA).toLocalDateTime().format(HIST_FORMAT)
        val end = Instant.ofEpochMilli(closeAtMs).atZone(INDIA).toLocalDateTime().format(HIST_FORMAT)
        val historical = runCatching {
            apiFactory.groww.historicalCandles(
                bearer(token),
                growwSymbol = "NSE-${call.symbol}",
                startTime = start,
                endTime = end,
                candleInterval = "1minute"
            ).requirePayload("Multify 1-minute MFE candles ${call.symbol}")
        }.getOrElse {
            apiFactory.groww.historicalCandles(
                bearer(token),
                growwSymbol = "NSE-${call.symbol}",
                startTime = start,
                endTime = end,
                candleInterval = "5minute"
            ).requirePayload("Multify 5-minute MFE candles ${call.symbol}")
        }
        preferences.recordMarketData()
        val candleHigh = historical.candles.mapNotNull {
            runCatching { it[2].asDouble }.getOrNull()?.takeIf { px -> px.isFinite() && px > 0.0 }
        }.maxOrNull() ?: return null
        val maxPrice = max(call.entryPrice, max(candleHigh, call.multifyExitPrice ?: call.entryPrice))
        val mfePct = ((maxPrice / call.entryPrice) - 1.0) * 100.0
        if (!mfePct.isFinite() || mfePct < 0.0 || mfePct >= 25.0) return null
        val updated = call.copy(
            multifyMaxPrice = max(call.multifyMaxPrice ?: call.entryPrice, maxPrice),
            multifyMfePct = max(call.multifyMfePct ?: 0.0, mfePct),
            updatedAtMs = System.currentTimeMillis()
        )
        learningDao.updateCall(updated)
        auditLogger.log("LEARNING", "MULTIFY_MFE_RECORDED", mapOf(
            "date" to call.callDate,
            "symbol" to call.symbol,
            "entry" to call.entryPrice,
            "max_price" to updated.multifyMaxPrice,
            "mfe_pct" to updated.multifyMfePct,
            "buy_at_ms" to call.buyAtMs,
            "sell_at_ms" to closeAtMs
        ))
        return updated
    }

    private suspend fun backfillRecentMultifyMfe(token: String, limit: Int, force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        if (!force && now - lastMfeBackfillAtMs < MFE_BACKFILL_INTERVAL_MS) return 0
        lastMfeBackfillAtMs = now
        var repaired = 0
        learningDao.closedCallsMissingMfe(limit.coerceIn(1, 20)).forEach { call ->
            runCatching { captureMultifyMfe(call, token) }
                .onSuccess { if (it != null) repaired++ }
                .onFailure {
                    auditLogger.log("LEARNING", "MULTIFY_MFE_BACKFILL_ERROR", mapOf(
                        "symbol" to call.symbol, "date" to call.callDate,
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
        }
        if (repaired > 0) {
            auditLogger.log("LEARNING", "MULTIFY_MFE_BACKFILLED", mapOf("count" to repaired))
        }
        return repaired
    }

    private suspend fun ensurePgIlRecoveryBenchmark() {
        val callDate = "2026-10-12"
        val symbol = "PGIL"
        val entry = 1297.20
        val target = 1355.0
        val exit = 1321.0
        val reportedReturnPct = 1.83
        val targetPct = (target - entry) / entry * 100.0
        val buyAtMs = ZonedDateTime.of(2026, 10, 12, 9, 26, 6, 139_000_000, INDIA).toInstant().toEpochMilli()
        val sellAtMs = ZonedDateTime.of(2026, 10, 12, 9, 46, 42, 381_000_000, INDIA).toInstant().toEpochMilli()
        val existing = learningDao.callForDateSymbolAction(callDate, symbol, "BUY")
        val now = System.currentTimeMillis()
        if (existing == null) {
            learningDao.insertCall(
                LearningCallEntity(
                    callDate = callDate, symbol = symbol, source = "RECOVERY_V660", action = "BUY",
                    entryPrice = entry, targetPrice = target, targetPct = targetPct,
                    multifyExitPrice = exit, longRealizedPct = reportedReturnPct,
                    buyAtMs = buyAtMs, sellAtMs = sellAtMs, updatedAtMs = now
                )
            )
            auditLogger.log("LEARNING", "PGIL_20261012_RECOVERED", mapOf("mode" to "insert", "return_pct" to reportedReturnPct))
            return
        }
        val needsRepair = abs(existing.entryPrice - entry) > .0001 || existing.targetPrice != target ||
            existing.multifyExitPrice != exit || existing.longRealizedPct == null ||
            abs(existing.longRealizedPct - reportedReturnPct) > .0001
        if (needsRepair) {
            learningDao.updateCall(
                existing.copy(
                    entryPrice = entry, targetPrice = target, targetPct = targetPct,
                    multifyExitPrice = exit, longRealizedPct = reportedReturnPct,
                    buyAtMs = existing.buyAtMs.takeIf { it > 0L } ?: buyAtMs,
                    sellAtMs = existing.sellAtMs ?: sellAtMs, updatedAtMs = now
                )
            )
            auditLogger.log("LEARNING", "PGIL_20261012_RECOVERED", mapOf("mode" to "repair", "return_pct" to reportedReturnPct))
        }
    }

    private suspend fun repairLiveLearningFromSignals() {
        var repairedBuys = 0
        var repairedExits = 0
        dao.allNow().forEach { event ->
            if (!event.sourcePackage.equals(MULTIFY_PACKAGE, true) && event.sourcePackage != MANUAL_SOURCE) return@forEach
            val parsed = parser.parse(event.title, event.text, event.bigText)
            when (parsed.type) {
                SignalType.TRADE_RELEASE -> {
                    val before = learningDao.liveCallForBuyEvent(event.id) ?: return@forEach
                    val reference = multifyReferenceEntry(parsed) ?: return@forEach
                    val targetPct = parsed.target?.let { ((it - reference) / reference * 100.0).coerceAtLeast(0.0) }
                    if (abs(before.entryPrice - reference) > .0001 || before.targetPrice != parsed.target || before.targetPct != targetPct) {
                        learningDao.updateCall(before.copy(
                            entryPrice = reference, targetPrice = parsed.target ?: before.targetPrice,
                            targetPct = targetPct ?: before.targetPct, updatedAtMs = System.currentTimeMillis()
                        ))
                        repairedBuys++
                    }
                }
                SignalType.BOOK_PROFIT -> {
                    val symbol = parsed.symbol ?: return@forEach
                    val before = learningDao.latestLiveCallBefore(symbol, event.receivedAtMs) ?: return@forEach
                    val desired = parsed.returnsPct?.takeIf { it.isFinite() && abs(it) < 25.0 }
                        ?: AdaptiveLearningMath.realizedReturnPct(null, before.entryPrice, parsed.exitPrice)
                    if (desired != null && (before.longRealizedPct == null || abs(before.longRealizedPct - desired) > .0001 || before.sellEventId != event.id)) {
                        recordLiveExit(event.id, symbol, parsed.exitPrice, parsed.returnsPct, event.receivedAtMs)
                        repairedExits++
                    }
                }
                else -> Unit
            }
        }
        if (repairedBuys > 0 || repairedExits > 0) {
            auditLogger.log("LEARNING", "LIVE_HISTORY_REPAIRED", mapOf("buys" to repairedBuys, "exits" to repairedExits))
        }
    }
    suspend fun saveBrokerSettings(
        apiKeyOrToken: String,
        totpSecret: String,
        expectedStaticIp: String,
        packageFilter: String,
        holdingBudgetRupees: Long
    ) {
        require(holdingBudgetRupees in MIN_BUDGET..MAX_BUDGET) { "Holding budget must be between ₹10,000 and ₹2,00,000" }
        require(expectedStaticIp.isBlank() || isValidIp(expectedStaticIp)) { "Enter a valid IPv4/IPv6 static IP" }
        preferences.updateBrokerSettings(expectedStaticIp, packageFilter, holdingBudgetRupees)
        if (apiKeyOrToken.isNotBlank()) secretStore.putApiKey(apiKeyOrToken)
        if (totpSecret.isNotBlank()) secretStore.putTotpSecret(totpSecret)
        if (apiKeyOrToken.isNotBlank() || totpSecret.isNotBlank()) {
            secretStore.clearAccessToken()
            preferences.updateAuthState(authenticated = false)
        }
    }

    fun betaCredentialsConfigured(): Boolean = secretStore.hasBetaCredentials()
    fun betaResearchAuthenticated(): Boolean = secretStore.hasBetaCredentials() &&
        !betaSessionInvalid && !secretStore.getBetaAccessToken().isNullOrBlank()

    /** Beta is research-only. Credentials never enter Alpha's token, orders or inventory. */
    suspend fun saveBetaResearchSettings(apiKey: String, totpSecret: String) {
        require(apiKey.isNotBlank() || totpSecret.isNotBlank()) { "Enter Beta credentials to save" }
        if (apiKey.isNotBlank()) {
            require(apiKey.trim() != secretStore.getApiKey()) { "Beta API key must differ from Alpha" }
            secretStore.putBetaApiKey(apiKey)
        }
        if (totpSecret.isNotBlank()) secretStore.putBetaTotpSecret(totpSecret)
        secretStore.clearBetaAccessToken()
        cachedBetaResearchToken = null
        betaSessionInvalid = false
        auditLogger.log("BETA_RESEARCH", "CREDENTIALS_SAVED_AUTH_REQUIRED", mapOf(
            "read_only" to true, "trading_account" to "ALPHA", "static_ip_required_for_beta_orders" to false
        ))
    }

    suspend fun authenticateBetaResearch(): String {
        require(secretStore.hasBetaCredentials()) { "Save Beta API key and TOTP secret first" }
        val key = secretStore.getBetaApiKey() ?: error("Beta key missing")
        val totp = secretStore.getBetaTotpSecret() ?: error("Beta TOTP secret missing")
        require(key != secretStore.getApiKey()) { "Alpha and Beta API keys must differ" }
        // Authentication is explicitly initiated by the user; never auto-retry credentials.
        val payload = apiFactory.betaGroww.createAccessToken(
            "Bearer $key", TokenRequest(keyType = "totp", totp = TotpGenerator.generate(totp))
        )
        val token = payload.token?.takeIf { it.isNotBlank() } ?: error("Beta token was not returned")
        val profile = apiFactory.betaGroww.userProfile(bearer(token)).requirePayload("Beta research profile")
        val ucc = profile.ucc.orEmpty()
        val alphaUcc = preferences.settings.first().brokerUcc
        require(ucc.isNotBlank() && (alphaUcc.isBlank() || !ucc.equals(alphaUcc, true))) {
            "Beta must be a separate authorized Groww account from Alpha"
        }
        secretStore.putBetaAccessToken(token)
        secretStore.putBetaUcc(ucc)
        cachedBetaResearchToken = token
        betaSessionInvalid = false
        auditLogger.log("BETA_RESEARCH", "AUTHENTICATED_READ_ONLY", mapOf(
            "ucc_present" to true, "alpha_ip_unchanged" to true, "orders_permitted" to false
        ))
        return "Beta authenticated for market-data research only; Alpha retains all orders"
    }

    private suspend fun forecastMarketToken(): String? {
        if (!secretStore.hasBetaCredentials()) return ensureToken() // compatibility until Beta configured
        if (betaSessionInvalid) return null
        val token = cachedBetaResearchToken ?: secretStore.getBetaAccessToken()
        if (!token.isNullOrBlank()) cachedBetaResearchToken = token
        return token
    }

    private fun forecastUsesBeta(token: String): Boolean = secretStore.hasBetaCredentials() &&
        cachedBetaResearchToken == token && !betaSessionInvalid

    private suspend fun <T> betaMarketRead(token: String, type: BetaResearchGate.Type,
                                           alphaCall: suspend () -> T, betaCall: suspend () -> T): T {
        val tokenIsBeta = cachedBetaResearchToken == token || secretStore.getBetaAccessToken() == token
        if (tokenIsBeta || secretStore.hasBetaCredentials()) {
            if (!forecastUsesBeta(token)) {
                auditLogger.logAsync("BETA_RESEARCH", "REQUEST_SKIPPED_SESSION_INVALID", mapOf(
                    "category" to type.name, "alpha_fallback" to false
                ))
                error("Beta research session is unavailable; Alpha fallback is disabled while Beta is configured")
            }
        } else {
            return alphaCall()
        }
        val started = SystemClock.elapsedRealtime()
        return try {
            val result = betaResearchGate.read(type, betaCall)
            val usage = betaResearchGate.snapshot()
            auditLogger.logAsync("BETA_API", "REQUEST_PROFILE", mapOf(
                "account" to "BETA", "category" to type.name, "outcome" to "SUCCESS",
                "elapsed_ms" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                "live_requests" to usage.liveRequests, "historical_requests" to usage.historicalRequests,
                "live_429" to usage.liveRateLimits, "historical_429" to usage.historicalRateLimits,
                "live_failures" to usage.liveFailures, "historical_failures" to usage.historicalFailures
            ))
            result
        } catch (t: Throwable) {
            val code = (t as? HttpException)?.code()
            val usage = betaResearchGate.snapshot()
            auditLogger.logAsync("BETA_API", "REQUEST_PROFILE", mapOf(
                "account" to "BETA", "category" to type.name, "outcome" to "FAILURE",
                "http_code" to (code ?: 0),
                "elapsed_ms" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                "live_requests" to usage.liveRequests, "historical_requests" to usage.historicalRequests,
                "live_429" to usage.liveRateLimits, "historical_429" to usage.historicalRateLimits
            ))
            if (t is HttpException && t.code() == 401) {
                betaSessionInvalid = true
                cachedBetaResearchToken = null
                secretStore.clearBetaAccessToken()
                auditLogger.logAsync("BETA_RESEARCH", "REAUTH_REQUIRED", mapOf("code" to 401))
            }
            throw t
        }
    }

    suspend fun setHoldingBudget(value: Long) {
        preferences.setHoldingBudget(value)
        armHotState = null
        auditLogger.log("SETTINGS", "HOLDING_BUDGET", mapOf("rupees" to value))
    }

    suspend fun setArm(enabled: Boolean) {
        if (enabled) {
            val s = preferences.settings.first()
            val rejected = when {
                !tradingRecoveryReady -> "BROKER_RECOVERY_PENDING"
                !s.brokerAuthenticated -> "AUTH_REQUIRED"
                !s.staticIpMatched -> "STATIC_IP_MISMATCH"
                !s.brokerDdpiEnabled -> "DDPI_REQUIRED"
                s.safetyHalt -> "SAFETY_HALT_ACTIVE"
                recoveryGate.requiresReconnect() -> "BROKER_RECONNECT_REQUIRED"
                else -> null
            }
            if (rejected != null) {
                auditLogger.log("SETTINGS", "ARM_REJECTED", mapOf("reason" to rejected))
                error("Multify ARM unavailable: $rejected. Check Groww settings and broker recovery.")
            }
            // Validate before persisting ARM. A failed broker call must not briefly show ARM=ON.
            val cooldown = buyApiCooldownOrNull()
            if (cooldown != null) {
                auditLogger.log("SETTINGS", "ARM_REJECTED", mapOf(
                    "reason" to cooldown.reason,
                    "next_allowed_at_ms" to cooldown.nextAllowedAtMs,
                    "broker_reconnect_required" to recoveryGate.requiresReconnect()
                ))
                error("Multify ARM unavailable: ${cooldown.message}")
            }
            val warm = runCatching { prepareArmHotPath(force = true) }
            if (warm.getOrDefault(false) != true) {
                val cause = warm.exceptionOrNull()
                val reason = when (cause) {
                    is GrowwRecoveryGate.LocallyBlocked -> cause.reason
                    null -> "TOKEN_OR_MARGIN_UNAVAILABLE"
                    else -> "BROKER_PREWARM_EXCEPTION"
                }
                auditLogger.log("SETTINGS", "ARM_REJECTED", mapOf(
                    "reason" to reason,
                    "exception_type" to (cause?.javaClass?.simpleName ?: "NONE"),
                    "next_allowed_at_ms" to ((cause as? GrowwRecoveryGate.LocallyBlocked)?.nextAllowedAtMs ?: 0L),
                    "broker_reconnect_required" to recoveryGate.requiresReconnect()
                ))
                error("Multify ARM unavailable: $reason. ${cause?.message ?: "Check Groww token and CNC balance service."}")
            }
            preferences.setArm(true)
        } else {
            preferences.setArm(false)
            // The other two independent BUY toggles and all SELL monitoring stay unchanged.
            val live = preferences.settings.first()
            if (!live.fixedAutoBuyEnabled && !live.forecastAutoBuyEnabled) armHotState = null
        }
        auditLogger.log("SETTINGS", if (enabled) "ARM_ENABLED" else "ARM_DISABLED")
    }

    suspend fun prepareFixedPreMarketWarmup(): Boolean {
        val now = ZonedDateTime.now(INDIA)
        val t = now.toLocalTime()
        if (now.dayOfWeek.value >= 6 || t < LocalTime.of(9, 0) || t >= LocalTime.of(9, 15)) return false
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastFixedPreMarketWarmupAtMs < FIXED_PREMARKET_WARMUP_INTERVAL_MS) return true
        lastFixedPreMarketWarmupAtMs = nowMs

        val snapshot = ensureFixedEngineSnapshot()
        val tokenReady = ensureToken() != null
        val masterCount = nseSymbols.symbols().size
        val settings = preferences.settings.first()
        val executionWanted = settings.armEffective || settings.fixedAutoBuyEnabled || settings.forecastAutoBuyEnabled
        val armHotReady = if (executionWanted) {
            runCatching { prepareArmHotPath(force = false) }.getOrDefault(false)
        } else true

        auditLogger.log("FIXED_ENGINE", "PREMARKET_WARMUP", mapOf(
            "time_ist" to now.toString(),
            "snapshot_id" to snapshot.payload.snapshotId,
            "token_ready" to tokenReady,
            "nse_master_count" to masterCount,
            "arm_hot_ready" to armHotReady,
            "market_data_scanned" to false,
            "selection_logic_changed" to false
        ))
        return tokenReady && masterCount > 0
    }

    suspend fun setForecastScannerEnabled(enabled: Boolean) {
        preferences.setForecastScannerEnabled(enabled)
        if (!enabled) {
            preferences.setForecastAutoBuy(false)
            runCatching {
                reconcileForecastAutoBuyOrders(
                    forceCancel = true,
                    cancelReason = "FORECAST_SCANNER_PAUSED_FOR_FIXED_PRIORITY"
                )
            }.onFailure {
                auditLogger.log("FORECAST_AUTO_BUY", "PAUSE_CANCEL_ERROR", mapOf(
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }
        }
        auditLogger.log(
            "SETTINGS",
            if (enabled) "FORECAST_SCANNER_ENABLED" else "FORECAST_SCANNER_PAUSED",
            mapOf(
                "fixed_priority_default" to true,
                "background_market_data_enabled" to enabled,
                "forecast_auto_buy_forced_off_when_paused" to !enabled
            )
        )
    }

    suspend fun setForecastAutoBuy(enabled: Boolean) {
        if (enabled && !forecastLiveUnlocked()) {
            val completed = forecastCalibrationDates().size
            auditLogger.log("FORECAST_AUTO_BUY", "ENABLE_REJECTED_CALIBRATION", mapOf(
                "completed_sessions" to completed,
                "required_sessions" to MarketPriorityPolicy.LIVE_FORECAST_UNLOCK_SESSIONS
            ))
            error("Forecast Auto Buy locked: $completed/${MarketPriorityPolicy.LIVE_FORECAST_UNLOCK_SESSIONS} completed calibration sessions. Research and manual observation stay available.")
        }
        if (enabled) preferences.setForecastScannerEnabled(true) // migrate old paused-scanner installs
        preferences.setForecastAutoBuy(enabled)
        auditLogger.log(
            "SETTINGS",
            if (enabled) "FORECAST_AUTO_BUY_ENABLED" else "FORECAST_AUTO_BUY_DISABLED",
            mapOf(
                "requires_multify_arm" to false,
                "independent_execution_toggle" to true,
                "entry_gtt" to false,
                "entry_order_type" to "MARKET",
                "order_first_delivery" to true
            )
        )
        if (enabled) {
            val s = preferences.settings.first()
            if (!s.safetyHalt && marketSession() == "OPEN") {
                runCatching { prepareArmHotPath(force = false) }
                runCatching { autoBuyForecastRecommendations() }
                    .onFailure {
                        auditLogger.log("FORECAST_AUTO_BUY", "ENABLE_SWEEP_ERROR", mapOf(
                            "message" to (it.message ?: it.javaClass.simpleName)
                        ))
                    }
            }
        } else {
            runCatching { reconcileForecastAutoBuyOrders(forceCancel = true, cancelReason = "AUTO_BUY_DISABLED") }
                .onFailure {
                    auditLogger.log("FORECAST_AUTO_BUY", "DISABLE_CANCEL_ERROR", mapOf(
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
        }
    }

    /** A read-only check: never send doomed live-data or non-trading calls during broker cooldowns. */
    private fun buyApiCooldownOrNull(): GrowwRecoveryGate.LocallyBlocked? {
        for (category in listOf(GrowwApiUsageLedger.Category.LIVE_DATA, GrowwApiUsageLedger.Category.NON_TRADING)) {
            try {
                recoveryGate.checkRequestAllowed(category)
            } catch (blocked: GrowwRecoveryGate.LocallyBlocked) {
                return blocked
            }
        }
        return null
    }

    private fun auditAutoBuyBlocked(engine: String, blocked: GrowwRecoveryGate.LocallyBlocked) {
        auditLogger.logAsync(engine, "BUY_BLOCKED_BROKER_NOT_READY", mapOf(
            "reason" to blocked.reason,
            "next_allowed_at_ms" to blocked.nextAllowedAtMs,
            "broker_request_suppressed" to true,
            "safety_checks_bypassed" to false
        ))
    }

    suspend fun setFixedAutoBuy(enabled: Boolean) {
        preferences.setFixedAutoBuy(enabled)
        auditLogger.log(
            "SETTINGS",
            if (enabled) "FIXED_AUTO_BUY_ENABLED" else "FIXED_AUTO_BUY_DISABLED",
            mapOf(
                "requires_multify_arm" to false,
                "independent_execution_toggle" to true,
                "snapshot_id" to FixedEngineSnapshotStore.SNAPSHOT_ID,
                "entry_gtt" to false,
                "entry_order_type" to "MARKET"
            )
        )
        if (enabled) {
            ensureFixedEngineSnapshot()
            val s = preferences.settings.first()
            if (!s.safetyHalt && marketSession() == "OPEN") {
                runCatching { prepareArmHotPath(force = false) }
                runCatching { autoBuyFixedRecommendations() }
                    .onFailure {
                        auditLogger.log("FIXED_AUTO_BUY", "ENABLE_SWEEP_ERROR", mapOf(
                            "message" to (it.message ?: it.javaClass.simpleName)
                        ))
                    }
            }
        } else {
            runCatching { reconcileFixedAutoBuyOrders(forceCancel = true, cancelReason = "AUTO_BUY_DISABLED") }
                .onFailure {
                    auditLogger.log("FIXED_AUTO_BUY", "DISABLE_CANCEL_ERROR", mapOf(
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
        }
    }

    suspend fun resetHalt() {
        preferences.setSafetyHalt(false)
        preferences.setArm(false)
        runCatching { reconcileForecastAutoBuyOrders(forceCancel = true, cancelReason = "HALT_RESET_ARM_OFF") }
        runCatching { reconcileFixedAutoBuyOrders(forceCancel = true, cancelReason = "HALT_RESET_ARM_OFF") }
        armHotState = null
        auditLogger.log("SAFETY", "HALT_RESET_ARM_OFF")
    }

    suspend fun forceLocalDisarm() {
        preferences.setSafetyHalt(true)
        runCatching { reconcileForecastAutoBuyOrders(forceCancel = true, cancelReason = "SAFETY_HALT") }
        runCatching { reconcileFixedAutoBuyOrders(forceCancel = true, cancelReason = "SAFETY_HALT") }
        armHotState = null
        auditLogger.log("SAFETY", "LOCAL_DISARM")
    }

    suspend fun recordListenerConnected() = preferences.recordListenerConnected()
    suspend fun recordListenerReconnect() = preferences.recordListenerReconnect()
    suspend fun recordNotificationReceived() = preferences.recordNotification()
    suspend fun recordEventProcessingLatency(latencyMs: Long) = preferences.recordEventLatency(latencyMs)
    suspend fun recordServiceHeartbeat() = preferences.recordServiceHeartbeat()

    fun hasBrokerCredentials(): Boolean = secretStore.hasApiKey() && secretStore.hasTotpSecret()

    suspend fun authenticate(): AuthenticationResult {
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.AUTH)
        auditLogger.log("AUTH", "AUTHENTICATION_ATTEMPT", mapOf("method" to "TOTP"))
        val settings = preferences.settings.first()
        val apiKey = secretStore.getApiKey() ?: error("Groww TOTP token is not configured")
        val totpSecret = secretStore.getTotpSecret() ?: error("Groww TOTP secret is not configured")
        val publicIp = runCatching { apiFactory.publicIp.currentIp().ip.trim() }.getOrDefault("")
        val staticMatched = settings.expectedStaticIp.isNotBlank() && publicIp.isNotBlank() &&
            normalizeIp(publicIp) == normalizeIp(settings.expectedStaticIp)
        val tokenResponse = try {
            apiFactory.groww.createAccessToken(
                authorization = "Bearer $apiKey",
                request = TokenRequest(keyType = "totp", totp = TotpGenerator.generate(totpSecret))
            )
        } catch (e: HttpException) {
            error(growwAuthError(e))
        }
        val token = tokenResponse.token?.takeIf { it.isNotBlank() }
            ?: error("Groww did not return an access token. Verify the TOTP token/secret and phone time.")
        secretStore.putAccessToken(token)
        val profile = apiFactory.groww.userProfile(bearer(token)).requirePayload("Groww profile")
        if (profile.ucc?.isNotBlank() == true && profile.ucc.equals(secretStore.getBetaUcc(), true)) {
            secretStore.clearBetaAccessToken()
            cachedBetaResearchToken = null
            betaSessionInvalid = true
            auditLogger.logAsync("BETA_RESEARCH", "SAME_ACCOUNT_REJECTED", mapOf("beta_disabled" to true))
        }
        val cashEnabled = profile.nseEnabled && profile.activeSegments.any { it.equals("CASH", true) }
        require(cashEnabled) { "Groww profile is not enabled for NSE CASH trading" }
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val expiry = tokenResponse.expiry.orEmpty()
        preferences.updateAuthState(
            authenticated = true, authenticatedAtMs = System.currentTimeMillis(), accessTokenExpiry = expiry,
            brokerUcc = profile.ucc.orEmpty(), brokerDdpiEnabled = profile.ddpiEnabled,
            verifiedPublicIp = publicIp, staticIpMatched = staticMatched
        )
        val cnc = margin.equity?.cncBalanceAvailable ?: margin.clearCash
        val refreshedSettings = preferences.settings.first()
        armHotState = ArmHotState(token, refreshedSettings, cnc.coerceAtLeast(0.0), System.currentTimeMillis())
        auditLogger.log("AUTH", "AUTHENTICATION_SUCCESS", mapOf(
            "nse_cash_enabled" to cashEnabled, "ddpi_enabled" to profile.ddpiEnabled,
            "static_ip_matched" to staticMatched, "cnc_balance_available" to cnc
        ))
        return AuthenticationResult(
            authenticated = true, ucc = profile.ucc.orEmpty(), nseCashEnabled = cashEnabled,
            publicIp = publicIp, staticIpMatched = staticMatched, accessTokenExpiry = expiry,
            cncBalanceAvailable = cnc, ddpiEnabled = profile.ddpiEnabled,
            detail = buildString {
                append("Groww authenticated")
                if (profile.ucc?.isNotBlank() == true) append(" · UCC ${profile.ucc}")
                append(if (profile.ddpiEnabled) " · DDPI enabled" else " · DDPI required before ARM")
                if (publicIp.isNotBlank()) append(" · egress $publicIp")
                if (settings.expectedStaticIp.isNotBlank()) append(if (staticMatched) " · static IP matched" else " · STATIC IP MISMATCH")
            }
        )
    }

    suspend fun refreshDashboardLocal(): DashboardDto {
        val settings = preferences.settings.first()
        val fixedSnapshotState = runCatching { ensureFixedEngineSnapshot() }.getOrNull()
        val today = LocalDate.now(INDIA).toString()
        val learning = learningStats()
        val forecastLearning = forecastLearningStats()
        val forecastReferences = forecastReferenceStats()
        val snapshot = managedSnapshotLocal()
        val execution = executionStats()
        val forecasts = learningDao.longForecastsForDate(today).map { it.toForecastDto() }
        val fixedRecords = fixedRecommendationStore.recommendations(today)
            .filter { it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES }
        fixedOutcomePerformanceStore.record(fixedRecords)
        val fixedRolling = fixedOutcomePerformanceStore.rolling()
        val fixedDaily = fixedOutcomePerformanceStore.today()
        val fixedOutcomes = FixedOutcomeStatsDto(
            published = fixedRolling.published, observed = fixedRolling.observed,
            todayPublished = fixedDaily.published, todayObserved = fixedDaily.observed,
            todayTargetAveragePct = fixedDaily.meanTargetPct,
            todayObservedAveragePct = fixedDaily.meanObservedMfePct,
            rollingObservedAveragePct = fixedRolling.meanObservedMfePct,
            armHits = fixedRolling.armHits, onePctHits = fixedRolling.onePctHits,
            fullTargetHits = fixedRolling.fullTargetHits,
            armHitRatePct = fixedRolling.armHitRatePct,
            onePctHitRatePct = fixedRolling.onePctHitRatePct,
            fullTargetHitRatePct = fixedRolling.fullTargetHitRatePct,
            armHitMeanMfePct = fixedRolling.armHitMeanMfePct,
            onePctHitMeanMfePct = fixedRolling.onePctHitMeanMfePct,
            fullHitMeanMfePct = fixedRolling.fullHitMeanMfePct,
            p25Pct = fixedRolling.p25Pct, medianPct = fixedRolling.medianPct, p75Pct = fixedRolling.p75Pct
        )
        val fixedForecasts = fixedRecords.map { it.toDto().copy(observedAfterPublication = it.lastObservedAtMs > it.generatedAtMs) }
        val research = learningDao.latestResearchReport()?.let {
            ResearchDto(it.reportDate, it.title, it.summary)
        } ?: ResearchDto()
        val events = dao.recentNow(8).mapNotNull { e ->
            e.backendAction?.let {
                RecentDecisionDto(formatEventTime(e.receivedAtMs), e.symbol, it, e.backendReason.orEmpty())
            }
        }
        val nowMs = System.currentTimeMillis()
        val heartbeatAge = if (settings.serviceHeartbeatAtMs > 0L) nowMs - settings.serviceHeartbeatAtMs else Long.MAX_VALUE
        val listenerAge = if (settings.listenerHeartbeatAtMs > 0L) nowMs - settings.listenerHeartbeatAtMs else Long.MAX_VALUE
        val dataAge = if (settings.lastMarketDataAtMs > 0L) nowMs - settings.lastMarketDataAtMs else Long.MAX_VALUE
        val discoveryHealth = marketDiscoveryStateStore.loadHealth(today)
        val discoveryAge = discoveryHealth?.let { nowMs - it.updatedAtMs } ?: Long.MAX_VALUE
        val discoveryStatus = when {
            discoveryHealth == null -> "WAITING"
            discoveryHealth.abortedForNetwork -> "DEGRADED"
            !discoveryHealth.baselineReady -> "WARMING"
            discoveryHealth.coverageRatio < MIN_DISCOVERY_COVERAGE_RATIO -> "DEGRADED"
            discoveryAge > DISCOVERY_HEALTH_STALE_MS -> "STALE"
            else -> "HEALTHY"
        }
        val fixedDiscoveryHealth = fixedMarketDiscoveryStateStore.loadHealth(today)
        val fixedDiscoveryAge = fixedDiscoveryHealth?.let { nowMs - it.updatedAtMs } ?: Long.MAX_VALUE
        val fixedDiscoveryStatus = when {
            fixedDiscoveryHealth == null -> "WAITING"
            fixedDiscoveryHealth.abortedForNetwork -> "DEGRADED"
            !fixedDiscoveryHealth.baselineReady -> "WARMING"
            fixedDiscoveryHealth.coverageRatio < FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO -> "DEGRADED"
            fixedDiscoveryAge > DISCOVERY_HEALTH_STALE_MS -> "STALE"
            else -> "HEALTHY"
        }

        return DashboardDto(
            armed = settings.armEffective,
            halted = settings.safetyHalt,
            marketSession = marketSession(),
            asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            broker = BrokerStatusDto(
                configured = hasBrokerCredentials(),
                connected = settings.brokerAuthenticated,
                detail = if (settings.brokerAuthenticated)
                    "Cached broker state · live market-hours API reserved for Fixed + sell protection"
                else "Groww authentication required"
            ),
            risk = RiskStatusDto(
                maxExposure = settings.holdingBudgetRupees.toDouble(),
                riskMode = when {
                    settings.safetyHalt -> "HALTED"
                    settings.armEffective -> "ARMED"
                    else -> "DISARMED"
                }
            ),
            summary = snapshot.summary,
            execution = execution,
            learning = learning,
            forecastLearning = forecastLearning,
            forecastReferences = forecastReferences,
            fixedOutcomes = fixedOutcomes,
            forecasts = forecasts,
            fixedForecasts = fixedForecasts,
            forecastWatchlist = secondaryForecastWatchlist(today),
            research = research,
            health = EngineHealthDto(
                listener = if (listenerAge <= 60_000L) "HEALTHY" else "UNHEALTHY",
                broker = if (settings.brokerAuthenticated) "CONNECTED · CACHED" else "DISCONNECTED",
                marketData = if (dataAge <= 15_000L) "FRESH" else "STALE",
                symbolMaster = if (nseSymbols.isStale()) "STALE" else "FRESH",
                foregroundService = if (heartbeatAge <= 30_000L) "RUNNING" else "NOT_HEARTBEATING",
                discovery = discoveryStatus,
                discoveryCoveragePct = (discoveryHealth?.coverageRatio ?: 0.0) * 100.0,
                discoveryBaselineAgeMs = discoveryAge,
                discoveryBatchErrors = discoveryHealth?.batchErrors ?: 0,
                discoveryBaselineReady = discoveryHealth?.baselineReady ?: false,
                lastNotificationAtMs = settings.lastNotificationAtMs,
                reconnectCount = settings.listenerReconnectCount,
                lastReconnectAtMs = settings.lastListenerReconnectAtMs,
                lastEventProcessingLatencyMs = settings.lastEventProcessingLatencyMs,
                lastOrderDispatchPrepMicros = settings.lastOrderDispatchPrepMicros,
                lastBrokerAckLatencyMs = settings.lastBrokerAckLatencyMs,
                marketDataAgeMs = dataAge
            ),
            fixedHealth = EngineHealthDto(
                broker = if (settings.brokerAuthenticated) "CONNECTED · CACHED" else "DISCONNECTED",
                marketData = if (dataAge <= 15_000L) "FRESH" else "STALE",
                symbolMaster = if (nseSymbols.isStale()) "STALE" else "FRESH",
                foregroundService = if (heartbeatAge <= 30_000L) "RUNNING" else "NOT_HEARTBEATING",
                discovery = fixedDiscoveryStatus,
                discoveryCoveragePct = (fixedDiscoveryHealth?.coverageRatio ?: 0.0) * 100.0,
                discoveryBaselineAgeMs = fixedDiscoveryAge,
                discoveryBatchErrors = fixedDiscoveryHealth?.batchErrors ?: 0,
                discoveryBaselineReady = fixedDiscoveryHealth?.baselineReady ?: false,
                marketDataAgeMs = dataAge
            ),
            fixedSnapshot = fixedSnapshotDto(fixedSnapshotState),
            positions = snapshot.positions,
            recentDecisions = (snapshot.recentDecisions + events).take(10)
        )
    }

    /** Read-only research watchlist. Never writes LongForecastEntity or enables a BUY. */
    private suspend fun secondaryForecastWatchlist(date: String): List<ForecastDto> =
        learningDao.forecastResearchForDate(date).asSequence()
            .groupBy { it.symbol.uppercase(Locale.US) }
            .values.mapNotNull { rows -> rows.maxByOrNull { it.scanAtMs } }
            .filter { it.qualityTier == "SECONDARY" }
            .sortedByDescending { it.scanAtMs }
            .take(12)
            .mapIndexed { i, row ->
                ForecastDto(
                    rank = i + 1, symbol = row.symbol, confidence = row.confidence,
                    score = row.directionalScore, reason = row.rejectionReason,
                    entryPrice = row.entryPrice,
                    targetPct = row.targetPct,
                    targetPrice = row.entryPrice * (1.0 + row.targetPct / 100.0),
                    status = "WATCH_ONLY", strategy = row.strategy, regime = row.regime,
                    marketRegime = row.marketRegime, generatedAtMs = row.scanAtMs,
                    qualityTier = "SECONDARY", executionEligible = false
                )
            }.toList()

    suspend fun refreshDashboard(): DashboardDto {
        val settings = preferences.settings.first()
        // Fixed is self-contained in the APK and must initialize even when Groww is not authenticated.
        // This prevents the Fixed tab from incorrectly showing zero history / "Capturing…" merely
        // because the broker session is unavailable.
        val fixedSnapshotState = runCatching { ensureFixedEngineSnapshot() }.getOrNull()
        val token = secretStore.getAccessToken()
        if (!settings.brokerAuthenticated || token.isNullOrBlank()) {
            return disconnectedDashboard(settings, fixedSnapshotState)
        }
        return try {
            val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
            val cnc = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
            armHotState = ArmHotState(token, settings, cnc, System.currentTimeMillis())
            ensureHistoricalSeed()
            runCatching { backfillRecentMultifyMfe(token, MFE_BACKFILL_BATCH_SIZE) }
            val learning = learningStats()
            val forecastLearning = forecastLearningStats()
            val forecastReferences = forecastReferenceStats()
            val snapshot = managedSnapshot(token)
            val execution = executionStats()
            val today = LocalDate.now(INDIA).toString()
            val forecasts = learningDao.longForecastsForDate(today).map { it.toForecastDto() }
            val fixedForecasts = fixedRecommendationStore.recommendations(today)
                .filter { it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES }
                .map { it.toDto() }
            val champions = learningDao.allForecastChampions()
                .map { ForecastChampionDto(it.marketRegime, it.regime, it.strategy, it.wins, it.losses, it.sampleCount, it.distinctDays, it.distinctSymbols, it.frozen) }
            val research = learningDao.latestResearchReport()?.let { ResearchDto(it.reportDate, it.title, it.summary) } ?: ResearchDto()
            val events = dao.recentNow(8).mapNotNull { e ->
                e.backendAction?.let { RecentDecisionDto(formatEventTime(e.receivedAtMs), e.symbol, it, e.backendReason.orEmpty()) }
            }
            val symbolStatus = nseSymbols.status()
            val nowMs = System.currentTimeMillis()
            val heartbeatAge = if (settings.serviceHeartbeatAtMs > 0L) nowMs - settings.serviceHeartbeatAtMs else Long.MAX_VALUE
            val listenerAge = if (settings.listenerHeartbeatAtMs > 0L) nowMs - settings.listenerHeartbeatAtMs else Long.MAX_VALUE
            val dataAge = if (settings.lastMarketDataAtMs > 0L) nowMs - settings.lastMarketDataAtMs else Long.MAX_VALUE
            val discoveryHealth = marketDiscoveryStateStore.loadHealth(today)
            val discoveryAge = discoveryHealth?.let { nowMs - it.updatedAtMs } ?: Long.MAX_VALUE
            val discoveryStatus = when {
                discoveryHealth == null -> "WAITING"
                discoveryHealth.abortedForNetwork -> "DEGRADED"
                !discoveryHealth.baselineReady -> "WARMING"
                discoveryHealth.coverageRatio < MIN_DISCOVERY_COVERAGE_RATIO -> "DEGRADED"
                discoveryAge > DISCOVERY_HEALTH_STALE_MS -> "STALE"
                else -> "HEALTHY"
            }
            val fixedDiscoveryHealth = fixedMarketDiscoveryStateStore.loadHealth(today)
            val fixedDiscoveryAge = fixedDiscoveryHealth?.let { nowMs - it.updatedAtMs } ?: Long.MAX_VALUE
            val fixedDiscoveryStatus = when {
                fixedDiscoveryHealth == null -> "WAITING"
                fixedDiscoveryHealth.abortedForNetwork -> "DEGRADED"
                !fixedDiscoveryHealth.baselineReady -> "WARMING"
                fixedDiscoveryHealth.coverageRatio < FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO -> "DEGRADED"
                fixedDiscoveryAge > DISCOVERY_HEALTH_STALE_MS -> "STALE"
                else -> "HEALTHY"
            }
            DashboardDto(
                armed = settings.armEffective, halted = settings.safetyHalt, marketSession = marketSession(),
                asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                broker = BrokerStatusDto(
                    configured = hasBrokerCredentials(), connected = true,
                    detail = "CNC holdings only · DDPI ${if (settings.brokerDdpiEnabled) "enabled" else "required"} · available ₹${fmt(margin.equity?.cncBalanceAvailable ?: margin.clearCash)} · NSE master ${symbolStatus.count}"
                ),
                risk = RiskStatusDto(
                    maxExposure = settings.holdingBudgetRupees.toDouble(),
                    riskMode = when { settings.safetyHalt -> "HALTED"; settings.armEffective -> "ARMED"; else -> "DISARMED" }
                ),
                summary = snapshot.summary, execution = execution, learning = learning, forecastLearning = forecastLearning,
                forecastReferences = forecastReferences,
                forecasts = forecasts, fixedForecasts = fixedForecasts,
                forecastWatchlist = secondaryForecastWatchlist(today), forecastChampions = champions,
                research = research,
                health = EngineHealthDto(
                    listener = if (listenerAge <= 60_000L) "HEALTHY" else "UNHEALTHY",
                    broker = "CONNECTED",
                    marketData = if (dataAge <= 15_000L) "FRESH" else "STALE",
                    symbolMaster = if (nseSymbols.isStale()) "STALE" else "FRESH",
                    foregroundService = if (heartbeatAge <= 30_000L) "RUNNING" else "NOT_HEARTBEATING",
                    discovery = discoveryStatus,
                    discoveryCoveragePct = (discoveryHealth?.coverageRatio ?: 0.0) * 100.0,
                    discoveryBaselineAgeMs = discoveryAge,
                    discoveryBatchErrors = discoveryHealth?.batchErrors ?: 0,
                    discoveryBaselineReady = discoveryHealth?.baselineReady ?: false,
                    lastNotificationAtMs = settings.lastNotificationAtMs,
                    reconnectCount = settings.listenerReconnectCount,
                    lastReconnectAtMs = settings.lastListenerReconnectAtMs,
                    lastEventProcessingLatencyMs = settings.lastEventProcessingLatencyMs,
                    lastOrderDispatchPrepMicros = settings.lastOrderDispatchPrepMicros,
                    lastBrokerAckLatencyMs = settings.lastBrokerAckLatencyMs,
                    marketDataAgeMs = dataAge
                ),
                fixedHealth = EngineHealthDto(
                    broker = "CONNECTED",
                    marketData = if (dataAge <= 15_000L) "FRESH" else "STALE",
                    symbolMaster = if (nseSymbols.isStale()) "STALE" else "FRESH",
                    foregroundService = if (heartbeatAge <= 30_000L) "RUNNING" else "NOT_HEARTBEATING",
                    discovery = fixedDiscoveryStatus,
                    discoveryCoveragePct = (fixedDiscoveryHealth?.coverageRatio ?: 0.0) * 100.0,
                    discoveryBaselineAgeMs = fixedDiscoveryAge,
                    discoveryBatchErrors = fixedDiscoveryHealth?.batchErrors ?: 0,
                    discoveryBaselineReady = fixedDiscoveryHealth?.baselineReady ?: false,
                    marketDataAgeMs = dataAge
                ),
                fixedSnapshot = fixedSnapshotDto(fixedSnapshotState),
                positions = snapshot.positions,
                recentDecisions = (snapshot.recentDecisions + events).take(10)
            )
        } catch (t: Throwable) {
            if (t.message?.contains("401") == true || t.message?.contains("author", true) == true) preferences.updateAuthState(authenticated = false)
            disconnectedDashboard(preferences.settings.first(), fixedSnapshotState).copy(
                broker = BrokerStatusDto(configured = hasBrokerCredentials(), connected = false, detail = t.message)
            )
        }
    }

    suspend fun processEvent(
        eventId: Long,
        parsedOverride: ParsedSignal? = null,
        notificationStartedNs: Long? = null
    ) {
        val parsed = parsedOverride ?: run {
            val event = dao.byId(eventId) ?: return
            parser.parse(event.title, event.text, event.bigText)
        }
        // TRADE_RELEASE is latency critical: keep the broker BUY ahead of nonessential
        // Fixed-snapshot / learning maintenance. The notification listener still reserves
        // the event fingerprint first for idempotency so duplicate Android notifications
        // cannot create duplicate real orders.
        if (parsed.type != SignalType.TRADE_RELEASE) {
            runCatching { ensureFixedEngineSnapshot() }
        }
        when (parsed.type) {
            SignalType.AUTO_PAUSED -> {
                forceLocalDisarm()
                dao.updateForwarding(eventId, "HALTED", "SAFETY_HALT", "Multify reported a safety pause. ARM has been disabled.", null)
                return
            }
            SignalType.PRE_ALERT -> {
                val ready = runCatching { prepareArmHotPath(force = true) }.getOrDefault(false)
                dao.updateForwarding(
                    eventId, "ANALYZED", "PRE_ALERT_READY",
                    if (ready) "Groww token, connection and CNC buying power warmed for the next Multify call."
                    else "Signal captured; authenticate Groww before ARM.",
                    null
                )
                return
            }
            SignalType.BUY_SUBMITTED -> {
                dao.updateForwarding(eventId, "ANALYZED", "BROKER_ACK_CAPTURED", "Multify broker acknowledgement captured.", null)
                return
            }
            SignalType.UNKNOWN -> {
                dao.updateForwarding(eventId, "IGNORED", "IGNORED", "Unsupported notification ignored.", null)
                return
            }
            else -> Unit
        }

        val marketCritical = parsed.type == SignalType.TRADE_RELEASE || parsed.type == SignalType.BOOK_PROFIT
        if (marketCritical) marketCriticalTradeInFlight = true
        try {
        val symbol = parsed.symbol
        if (!symbol.isNullOrBlank() && !nseSymbols.isKnown(symbol) && !nseSymbols.isStale()) {
            dao.updateForwarding(eventId, "IGNORED", "SYMBOL_NOT_IN_NSE_MASTER", "$symbol is not in the current NSE equity master.", null)
            return
        }

        // Recommendation learning is independent of Groww execution/reconciliation.
        // A manual broker exit or a failed SELL must not erase Multify's reported result.
        var closedLearningCall: LearningCallEntity? = null
        if (parsed.type == SignalType.BOOK_PROFIT && !symbol.isNullOrBlank()) {
            val eventAtMs = dao.byId(eventId)?.receivedAtMs ?: System.currentTimeMillis()
            closedLearningCall = recordLiveExit(eventId, symbol, parsed.exitPrice, parsed.returnsPct, eventAtMs)
        }

        val hot = armHotState?.takeIf { System.currentTimeMillis() - it.warmedAtMs <= ARM_HOT_STATE_MAX_AGE_MS }
        val token = hot?.token ?: ensureToken()
        if (token == null) {
            if (parsed.type == SignalType.TRADE_RELEASE) runCatching { recordLiveBuy(eventId, parsed) }
            dao.updateForwarding(eventId, "CAPTURED", "AUTH_REQUIRED", "Signal and Multify learning stored. Authenticate Groww to enable ARM.", null)
            return
        }
        if (parsed.type == SignalType.BOOK_PROFIT && closedLearningCall != null) {
            auditLogger.log("LEARNING", "MULTIFY_MFE_DEFERRED_AFTER_HOURS", mapOf("symbol" to (symbol ?: "")))
        }
        val settings = hot?.settings ?: preferences.settings.first()
        when (parsed.type) {
            SignalType.TRADE_RELEASE -> {
                try {
                    handleMultifyBuy(eventId, parsed, token, settings, notificationStartedNs)
                } finally {
                    // Order path first; learning/snapshot bookkeeping follows.
                    runCatching { recordLiveBuy(eventId, parsed) }
                        .onFailure { auditLogger.log("LEARNING", "LIVE_BUY_RECORD_ERROR", mapOf("event_id" to eventId, "message" to (it.message ?: it.javaClass.simpleName))) }
                    runCatching { ensureFixedEngineSnapshot() }
                        .onFailure { auditLogger.log("FIXED", "POST_MULTIFY_SNAPSHOT_ERROR", mapOf("event_id" to eventId, "message" to (it.message ?: it.javaClass.simpleName))) }
                }
            }
            SignalType.BOOK_PROFIT -> handleMultifyExit(eventId, parsed, token, settings, notificationStartedNs)
            else -> Unit
        }
        } finally {
            if (marketCritical) marketCriticalTradeInFlight = false
        }
    }

    private suspend fun handleMultifyBuy(
        eventId: Long,
        signal: ParsedSignal,
        token: String,
        settings: AppSettings,
        notificationStartedNs: Long?
    ) {
        val symbol = signal.symbol ?: error("Trade release has no symbol")
        val signalReference = listOfNotNull(signal.entryHigh, signal.entryLow).maxOrNull()

        if (!settings.armEffective) {
            val observed = multifyReferenceEntry(signal)
            dao.updateForwarding(
                eventId, "ANALYZED", "LONG_CALL_CAPTURED",
                "Multify LONG call captured${observed?.let { " at reference ₹${fmt(it)}" }.orEmpty()}. ARM is off, so no order was placed.",
                null
            )
            return
        }
        preArmGuard(settings)
        if (managedDao.openPositionForSymbol(symbol) != null) {
            dao.updateForwarding(eventId, "ANALYZED", "HOLDING_ALREADY_OPEN", "$symbol is already app-owned; duplicate BUY blocked.", null)
            return
        }

        val hot = armHotState?.takeIf {
            it.token == token && System.currentTimeMillis() - it.warmedAtMs <= ARM_HOT_STATE_MAX_AGE_MS
        }
        val available = hot?.cncBalance ?: run {
            val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
            (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        }
        val referencePrice = signalReference ?: run {
            val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
            preferences.recordMarketData()
            quote.lastPrice ?: error("Groww quote does not contain LTP")
        }
        val sizing = CashAwareCncSizing.sizeStrictBudget(settings.holdingBudgetRupees.toDouble(), available, referencePrice)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        require(qty > 0) { "Multify BUY skipped: full configured budget ₹${settings.holdingBudgetRupees} is not available" }

        val ref = stableRef("MLH", "$eventId-$symbol")
        val order = placeCncBuy(token, symbol, qty, ref, notificationStartedNs)
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to symbol, "available_cnc" to available, "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to referencePrice,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty
        ))
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: referencePrice
        val investedAmount = entry * filled
        val trailArmPct = NetProfitProtectionPolicy.MIN_NET_RETURN_PCT
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_MULTIFY_HOLDING, symbol = symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = HardStopLossPolicy.stopPrice(entry),
                targetPrice = signal.target,
                strategy = "Multify ARM holding · hard -2% floor · cost-aware net +0.50% trail +25bps slippage reserve, +0.10% steps",
                regime = "HOLDING_LONG", confidence = 1.0, sourceEventId = eventId,
                openOrderId = order.growwOrderId, openReferenceId = ref, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                trailingArmPct = trailArmPct, trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        armHotState = hot?.copy(
            cncBalance = max(0.0, hot.cncBalance - entry * filled),
            warmedAtMs = System.currentTimeMillis()
        )
        dao.updateForwarding(
            eventId, "DELIVERED", "CNC_LONG_BOUGHT",
            "ARM bought $filled $symbol into holdings @ ₹${fmt(entry)}. Net trailing arms after estimated +0.50% net plus 25bps execution reserve; it ratchets in +0.10% net steps. The common -2% hard stop remains active until then.",
            null
        )
        auditLogger.log("HOLDING", "MULTIFY_LONG_OPENED", mapOf(
            "symbol" to symbol, "qty" to filled, "entry" to entry,
            "invested_amount" to investedAmount,
            "trail_arm_trigger_pct" to trailArmPct,
            "initial_protected_net_pct" to NetProfitProtectionPolicy.MIN_NET_RETURN_PCT,
            "slippage_allowance_bps" to NetProfitProtectionPolicy.SLIPPAGE_ALLOWANCE_BPS,
            "trail_step_pct" to SteppedTrailingPolicy.STEP_PCT
        ))
    }

    private suspend fun handleMultifyExit(
        eventId: Long,
        signal: ParsedSignal,
        token: String,
        settings: AppSettings,
        notificationStartedNs: Long?
    ) {
        val symbol = signal.symbol ?: error("Book-profit signal has no symbol")
        val holding = managedDao.openPosition(ENGINE_MULTIFY_HOLDING, symbol)
        if (holding == null) {
            dao.updateForwarding(
                eventId, "ANALYZED", "MULTIFY_EXIT_LEARNING_ONLY",
                "Multify Book Profit recorded for learning. No app-owned Multify holding was open.",
                null
            )
            return
        }

        val reconciled = reconcilePositionWithBroker(token, holding, signal.exitPrice, force = true)
        if (reconciled == null) {
            dao.updateForwarding(
                eventId, "DELIVERED", "MULTIFY_EXIT_EXTERNAL_CLOSE_OBSERVED",
                "Multify Book Profit recorded. Groww already showed the app-owned $symbol holding as externally closed; no SELL was sent.",
                null
            )
            return
        }

        dao.updateForwarding(
            eventId, "ANALYZED", "MULTIFY_EXIT_TRAIL_CONTINUES",
            "Multify Book Profit recorded for MFE learning only. $symbol remains held; the common -2% hard stop and fixed stepped profitable trail remain the mandatory sell logic.",
            null
        )
        auditLogger.log("HOLDING", "MULTIFY_BOOK_PROFIT_IGNORED_FOR_EXECUTION", mapOf(
            "symbol" to symbol,
            "position_id" to reconciled.id,
            "entry" to reconciled.entryPrice,
            "current_stop" to reconciled.stopPrice,
            "high_water" to reconciled.maxFavourablePrice,
            "notification_dispatch_suppressed" to true
        ))
    }

    suspend fun submitManualSignal(symbol: String, action: String, observedPrice: Double?): Long {
        val normalizedSymbol = symbol.trim().uppercase(Locale.US)
        require(normalizedSymbol.matches(Regex("[A-Z0-9&._-]{1,32}"))) { "Enter a valid NSE symbol" }
        val normalizedAction = action.trim().uppercase(Locale.US).replace(' ', '_')
        require(normalizedAction in setOf("BUY", "BOOK_PROFIT")) { "Manual action must be BUY or BOOK PROFIT" }
        val signalType = if (normalizedAction == "BUY") SignalType.TRADE_RELEASE else SignalType.BOOK_PROFIT
        val now = System.currentTimeMillis()
        val id = dao.insert(
            SignalEventEntity(
                fingerprint = sha256("$MANUAL_SOURCE|$normalizedSymbol|$normalizedAction|$now"),
                receivedAtMs = now, postedAtMs = now, sourcePackage = MANUAL_SOURCE, appLabel = "Manual Multify fallback",
                title = normalizedAction.replace('_', ' '), text = normalizedSymbol,
                bigText = observedPrice?.let { "$normalizedSymbol @ ₹${fmt(it)}" } ?: normalizedSymbol,
                signalType = signalType.name, symbol = normalizedSymbol, summary = "Manual $normalizedAction · $normalizedSymbol", confidence = 1.0
            )
        )
        require(id > 0L) { "Manual signal was not stored" }
        processEvent(
            id,
            parsedOverride = ParsedSignal(
                type = signalType, symbol = normalizedSymbol,
                entryLow = if (signalType == SignalType.TRADE_RELEASE) observedPrice else null,
                entryHigh = if (signalType == SignalType.TRADE_RELEASE) observedPrice else null,
                exitPrice = if (signalType == SignalType.BOOK_PROFIT) observedPrice else null,
                rawText = "Manual $normalizedAction $normalizedSymbol", confidence = 1.0
            )
        )
        return id
    }

    // Only broker availability preflight. Never delays the frozen 150s Fixed cadence
    // or bypasses trading controls. A deferred attempt is NOT a completed Fixed scan.
    private fun marketDataAvailableForFixed(): Boolean = runCatching {
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.LIVE_DATA)
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.HISTORICAL)
    }.isSuccess

    suspend fun runFixedPriorityCycle(): Boolean {
        fixedApiPriorityRequested = true
        return scanApiCycleMutex.withLock {
            val cycleStartedAtMs = System.currentTimeMillis()
            try {
                fixedScanDeferredBroker = false
                if (!marketDataAvailableForFixed()) {
                    fixedScanDeferredBroker = true
                    auditLogger.logAsync("FIXED_PERFORMANCE", "SCAN_DEFERRED_BROKER", mapOf(
                        "reason" to "LIVE_OR_HISTORICAL_CATEGORY_UNAVAILABLE",
                        "next_fixed_due_unchanged" to true,
                        "complete_scan_count_incremented" to false
                    ))
                    return@withLock false
                }
                val previousScanAtMs = lastFixedScanAtMs
                val recommendationStartedAtMs = System.currentTimeMillis()
                generateFixedForecasts(false)
                val recommendationCompletedAtMs = System.currentTimeMillis()
                val scanned = lastFixedScanAtMs != previousScanAtMs && !fixedScanDeferredBroker
                var autoBuySubmitted = 0
                var autoBuyElapsedMs = 0L
                if (scanned) {
                    val autoBuyStartedAtMs = System.currentTimeMillis()
                    autoBuySubmitted = autoBuyFixedRecommendations()
                    autoBuyElapsedMs = (System.currentTimeMillis() - autoBuyStartedAtMs).coerceAtLeast(0L)
                }
                if (scanned) {
                    auditLogger.logAsync("RUNTIME", "FIXED_PRIORITY_PHASE_PROFILE", mapOf(
                        "cycle_started_at_ms" to cycleStartedAtMs,
                        "recommendation_stage_ms" to (recommendationCompletedAtMs - recommendationStartedAtMs).coerceAtLeast(0L),
                        "auto_buy_stage_ms" to autoBuyElapsedMs,
                        "auto_buy_submitted" to autoBuySubmitted,
                        "cycle_total_ms" to (System.currentTimeMillis() - cycleStartedAtMs).coerceAtLeast(0L),
                        "order_before_notification" to true,
                        "fixed_formula_changed" to false
                    ))
                    _fixedDeliveryEvents.tryEmit(System.currentTimeMillis())
                }
                scanned
            } finally {
                fixedApiPriorityRequested = false
            }
        }
    }

    fun fixedRecommendationsForNotification(): List<ForecastDto> {
        val today = LocalDate.now(INDIA).toString()
        return fixedRecommendationStore.recommendations(today)
            .filter {
                it.status == "ACTIVE" &&
                    it.executionEligible &&
                    it.qualityTier == "PRIMARY" &&
                    it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES
            }
            .sortedBy { it.generatedAtMs }
            .map { it.toDto() }
    }

    suspend fun runForecastOpportunisticCycle(postFixedOpportunity: Boolean = false): Boolean {
        if (postFixedOpportunity) forecastPostFixedOpportunity = true
        if (secretStore.hasBetaCredentials() && fixedScanDeferredBroker &&
            !fixedApiPriorityRequested && !marketCriticalTradeInFlight &&
            millisUntilNextFixedScan() <= forecastFixedGuardMs()) {
            auditLogger.logAsync("FORECAST_RESEARCH", "BETA_FIXED_COOLDOWN_RESEARCH_ALLOWED", mapOf(
                "account" to "BETA", "fixed_due_in_ms" to millisUntilNextFixedScan(),
                "fixed_deferred_for_broker" to true, "fixed_priority" to false,
                "critical_trade_in_flight" to false, "alpha_fallback" to false
            ))
        }
        if (!forecastMarketCapacityAvailable()) {
            logForecastCapacitySkip("BROKER_CAPACITY_OR_FIXED_GUARD")
            forecastPostFixedOpportunity = false
            return false
        }
        // With separate Beta credentials the long research scan is allowed its own mutex;
        // it no longer holds Fixed's scan mutex across Beta HTTP waits. New candidate
        // requests still yield to Fixed and SELL in the existing capacity guards.
        val researchMutex = if (secretStore.hasBetaCredentials()) betaForecastCycleMutex else scanApiCycleMutex
        if (!researchMutex.tryLock()) {
            logForecastCapacitySkip(if (secretStore.hasBetaCredentials()) "BETA_FORECAST_BUSY" else "SHARED_SCAN_MUTEX_BUSY")
            forecastPostFixedOpportunity = false
            return false
        }
        return try {
            if (!forecastMarketCapacityAvailable()) {
                logForecastCapacitySkip("CAPACITY_CHANGED_BEFORE_SCAN")
                return false
            }
            val previousScanAtMs = lastForecastScanAtMs
            generateDailyForecasts(false)
            val scanned = lastForecastScanAtMs != previousScanAtMs
            if (scanned && forecastLiveUnlocked() && forecastMarketCapacityAvailable()) {
                autoBuyForecastRecommendations()
            }
            scanned
        } finally {
            forecastPostFixedOpportunity = false
            researchMutex.unlock()
        }
    }

    fun setTradingRecoveryReady(ready: Boolean) {
        tradingRecoveryReady = ready
        auditLogger.log("RUNTIME", if (ready) "BROKER_RECOVERY_READY" else "BROKER_RECOVERY_PENDING")
    }

    fun isTradingRecoveryReady(): Boolean = tradingRecoveryReady

    fun brokerReconnectRequired(): Boolean = recoveryGate.requiresReconnect()

    suspend fun brokerSessionHealthy(): Boolean =
        preferences.settings.first().brokerAuthenticated && !recoveryGate.requiresReconnect()

    private fun millisUntilNextFixedScan(): Long {
        val last = lastFixedScanAtMs
        if (last <= 0L) return 0L
        return (last + FIXED_V741_FORECAST_SCAN_INTERVAL_MS - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    suspend fun activateForecastEarlyEntryOnce() = preferences.activateForecastEarlyEntryOnce()

    suspend fun forecastBackgroundScanningEnabled(): Boolean =
        preferences.settings.first().forecastScannerEnabled

    private fun logForecastCapacitySkip(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastForecastCapacitySkipLogAtMs < 60_000L) return
        lastForecastCapacitySkipLogAtMs = now
        val usage = GrowwApiUsageLedger.snapshot()
        auditLogger.log("FORECAST_SCHEDULER", "SCAN_DEFERRED", mapOf(
            "reason" to reason,
            "fixed_priority_requested" to fixedApiPriorityRequested,
            "critical_order_in_flight" to marketCriticalTradeInFlight,
            "fixed_due_in_ms" to millisUntilNextFixedScan(),
            "forecast_scanner_enabled" to true,
            "live_usage_pct" to usage.categories.first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }.minuteUtilizationPct,
            "historical_usage_pct" to usage.categories.first { it.category == GrowwApiUsageLedger.Category.HISTORICAL }.minuteUtilizationPct,
            "forecast_only" to true,
            "quote_and_history_pair_required" to true,
            "five_minute_research_boost" to forecastBoostActive(),
            "fixed_priority_sacrificed" to false
        ))
    }

    private fun forecastBoostActive(): Boolean =
        ForecastResearchWindowPolicy.isBoostWindow(ZonedDateTime.now(INDIA).toLocalTime())

    private fun forecastBrokerHealthyForResearch(): Boolean = runCatching {
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.LIVE_DATA)
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.HISTORICAL)
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.NON_TRADING)
    }.isSuccess

    private fun forecastFixedGuardMs(): Long =
        if (secretStore.hasBetaCredentials()) BETA_FORECAST_FIXED_GUARD_MS else 35_000L

    private fun forecastMarketCapacityAvailable(): Boolean {
        if (fixedApiPriorityRequested || marketCriticalTradeInFlight) return false
        if (secretStore.hasBetaCredentials()) {
            // An overdue Fixed scan during Alpha broker cooldown must not starve independent
            // Beta research indefinitely. Never preempt an active Fixed cycle or protective trade.
            return betaResearchAuthenticated() && ForecastBetaPriorityPolicy.canResearch(
                fixedPriorityRequested = fixedApiPriorityRequested,
                criticalTradeInFlight = marketCriticalTradeInFlight,
                fixedDeferredForBroker = fixedScanDeferredBroker,
                millisUntilNextFixed = millisUntilNextFixedScan(),
                fixedGuardMs = forecastFixedGuardMs()
            )
        }
        // A cooldown is an immediate no-go. Do not repeatedly request a blocked endpoint.
        // Trading and Fixed categories are never reserved for this research engine.
        if (!forecastBrokerHealthyForResearch()) return false
        val usage = GrowwApiUsageLedger.snapshot()
        fun row(category: GrowwApiUsageLedger.Category) = usage.categories.first { it.category == category }
        val live = row(GrowwApiUsageLedger.Category.LIVE_DATA)
        val historical = row(GrowwApiUsageLedger.Category.HISTORICAL)
        val nonTrading = row(GrowwApiUsageLedger.Category.NON_TRADING)
        val fixedDueInMs = millisUntilNextFixedScan()
        val isBoost = forecastBoostActive() && ForecastResearchWindowPolicy.noRecent429(
            System.currentTimeMillis(),
            listOf(live.lastRateLimitAtMs, historical.lastRateLimitAtMs, nonTrading.lastRateLimitAtMs)
        )
        if (forecastPostFixedOpportunity || isBoost) {
            // Five minutes every half-hour may use spare capacity but not Fixed's upcoming
            // scan, the SELL path, or the hard broker governor. No broker budget is borrowed.
            return ForecastResearchWindowPolicy.canUseSpareCapacity(
                fixedDueInMs = fixedDueInMs,
                liveHeadroom = live.minuteHeadroom,
                historicalHeadroom = historical.minuteHeadroom,
                nonTradingHeadroom = nonTrading.minuteHeadroom,
                isBoost = isBoost
            )
        }
        return MarketPriorityPolicy.canRunForecast(
            fixedPriorityRequested = fixedApiPriorityRequested,
            criticalTradeInFlight = marketCriticalTradeInFlight,
            millisUntilNextFixed = fixedDueInMs,
            liveUsedPerMinute = live.usedLastMinute,
            liveAppLimitPerMinute = live.appPerMinuteLimit,
            historicalUsedPerMinute = historical.usedLastMinute,
            historicalAppLimitPerMinute = historical.appPerMinuteLimit,
            nonTradingUsedPerMinute = nonTrading.usedLastMinute,
            nonTradingAppLimitPerMinute = nonTrading.appPerMinuteLimit
        )
    }

    /**
     * Prefetch the previous Fixed scan's detailed symbols shortly before the next 2.5-minute scan.
     * This only warms the RAM HTTP cache; the frozen Fixed formula and its candidate construction
     * are untouched. If a 15-minute boundary is crossed, the cache key changes and Fixed naturally
     * falls back to fresh broker data.
     */
    // v7.9.4: previously generated up to 48 additional historical requests but zero
    // cache hits in the observed session; stop consuming Fixed's broker budget on prewarm.
    // The real Fixed scan still fetches every selected symbol at its scheduled cadence.
    suspend fun maintainFixedHotDataCache(): Boolean = false

    private data class AdaptiveFixedEvidenceRecord(
        val recommendationDate: String,
        val symbol: String,
        val entryPrice: Double,
        val targetPct: Double,
        val strategy: String,
        val regime: String,
        val marketRegime: String,
        val generatedAtMs: Long,
        val status: String,
        val lastPrice: Double,
        val maxFavourablePct: Double,
        val maxAdversePct: Double,
        val qualityTier: String
    )

    /**
     * Adaptive Forecast reads a copy of the persisted Fixed recommendation state directly.
     * The byte-locked FixedRecommendationStore is intentionally not modified or extended.
     */
    private fun adaptiveFixedEvidenceRecords(): List<AdaptiveFixedEvidenceRecord> {
        val raw = appContext
            .getSharedPreferences("fixed_recommendations_v75", Context.MODE_PRIVATE)
            .getString("state", null)
            ?: return emptyList()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
        val rows = root.getAsJsonArray("recommendations") ?: return emptyList()
        return rows.mapNotNull { element ->
            runCatching {
                val x = element.asJsonObject
                val date = x.get("recommendationDate")?.asString.orEmpty()
                val symbol = x.get("symbol")?.asString.orEmpty().uppercase(Locale.US)
                val entry = x.get("entryPrice")?.asDouble ?: return@runCatching null
                if (date.isBlank() || symbol.isBlank() || !entry.isFinite() || entry <= 0.0) return@runCatching null
                AdaptiveFixedEvidenceRecord(
                    recommendationDate = date,
                    symbol = symbol,
                    entryPrice = entry,
                    targetPct = x.get("targetPct")?.asDouble ?: 0.0,
                    strategy = x.get("strategy")?.asString.orEmpty(),
                    regime = x.get("regime")?.asString.orEmpty(),
                    marketRegime = x.get("marketRegime")?.asString.orEmpty(),
                    generatedAtMs = x.get("generatedAtMs")?.asLong ?: 0L,
                    status = x.get("status")?.asString ?: "ACTIVE",
                    lastPrice = x.get("lastPrice")?.asDouble ?: entry,
                    maxFavourablePct = x.get("maxFavourablePct")?.asDouble ?: 0.0,
                    maxAdversePct = x.get("maxAdversePct")?.asDouble ?: 0.0,
                    qualityTier = x.get("qualityTier")?.asString ?: "PRIMARY"
                )
            }.getOrNull()
        }.sortedWith(compareBy<AdaptiveFixedEvidenceRecord> { it.recommendationDate }.thenBy { it.generatedAtMs })
    }

    private fun fixedPositiveTransferCases(beforeDate: String): List<FixedPositiveTransferCase> {
        val raw = appContext
            .getSharedPreferences("fixed_recommendations_v75", Context.MODE_PRIVATE)
            .getString("state", null)
            ?: return emptyList()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
        val rows = root.getAsJsonArray("recommendations") ?: return emptyList()
        val parsed = rows.mapNotNull { element ->
            runCatching {
                val x = element.asJsonObject
                val date = x.get("recommendationDate")?.asString.orEmpty()
                if (date.isBlank() || date >= beforeDate) return@runCatching null
                val entry = x.get("entryPrice")?.asDouble ?: return@runCatching null
                val last = x.get("lastPrice")?.asDouble ?: return@runCatching null
                val observedAt = x.get("lastObservedAtMs")?.asLong ?: 0L
                val generatedAt = x.get("generatedAtMs")?.asLong ?: 0L
                val mfe = x.get("maxFavourablePct")?.asDouble ?: 0.0
                val closeGain = if (entry > 0.0 && last.isFinite()) (last / entry - 1.0) * 100.0 else Double.NaN
                if (!entry.isFinite() || entry <= 0.0 || !last.isFinite() || observedAt <= generatedAt ||
                    !ForecastUniversalPositiveEvidencePolicy.qualifies(null, closeGain, mfe, false)
                ) {
                    return@runCatching null
                }
                Triple(
                    date,
                    FixedPositiveTransferCase(
                        symbol = x.get("symbol")?.asString.orEmpty().uppercase(Locale.US),
                        strategy = x.get("strategy")?.asString.orEmpty(),
                        regime = x.get("regime")?.asString.orEmpty(),
                        marketRegime = x.get("marketRegime")?.asString.orEmpty(),
                        closeGainPct = closeGain.coerceAtLeast(0.0),
                        maxFavourablePct = mfe
                    ),
                    observedAt
                )
            }.getOrNull()
        }
        val dates = parsed.map { it.first }.distinct().sortedDescending().take(30).toSet()
        return parsed.filter { it.first in dates }.sortedByDescending { it.third }.map { it.second }
    }

    private suspend fun multifyPositiveTransferCases(beforeDate: String): List<MultifyPositiveTransferCase> {
        val positives = learningDao.allCalls().filter {
            it.action.equals("BUY", true) &&
                it.callDate < beforeDate &&
                ForecastUniversalPositiveEvidencePolicy.qualifies(
                    realizedPct = it.longRealizedPct,
                    closePct = it.longRealizedPct,
                    maxFavourablePct = it.multifyMfePct ?: 0.0,
                    targetHit = false
                )
        }
        val dates = positives.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        return positives.filter { it.callDate in dates }.map {
            val mfe = it.multifyMfePct ?: 0.0
            MultifyPositiveTransferCase(
                symbol = it.symbol.uppercase(Locale.US),
                realizedPct = (it.longRealizedPct?.takeIf { pct -> pct > 0.0 }
                    ?: ForecastUniversalPositiveEvidencePolicy.protectedTrailPct(mfe)
                    ?: 0.0),
                maxFavourablePct = mfe
            )
        }
    }

    private suspend fun universalPositiveEvidenceCases(beforeDate: String): List<ForecastPositiveEvidenceCase> {
        val rows = learningDao.allForecastPositiveEvidence()
            .filter { it.evidenceDate < beforeDate }
        val dates = rows.map { it.evidenceDate }.distinct().sortedDescending().take(30).toSet()
        return rows.filter { it.evidenceDate in dates }.map {
            ForecastPositiveEvidenceCase(
                source = it.source,
                evidenceDate = it.evidenceDate,
                symbol = it.symbol,
                strategy = it.strategy,
                regime = it.regime,
                marketRegime = it.marketRegime,
                sectorRegime = it.sectorRegime,
                capBucket = it.capBucket,
                timeBucket = it.timeBucket,
                dominantFamily = it.dominantFamily,
                realizedPct = it.realizedPct,
                closePct = it.closePct,
                maxFavourablePct = it.maxFavourablePct,
                protectedTrailPct = it.protectedTrailPct
            )
        }
    }

    private fun forecastCalibrationDates(): Set<String> =
        appContext.getSharedPreferences(FORECAST_CALIBRATION_PREFS, Context.MODE_PRIVATE)
            .getStringSet(FORECAST_CALIBRATION_DATES_KEY, emptySet())
            ?.toSet()
            .orEmpty()

    private fun markForecastCalibrationSessionComplete(date: String) {
        val prefs = appContext.getSharedPreferences(FORECAST_CALIBRATION_PREFS, Context.MODE_PRIVATE)
        val dates = prefs.getStringSet(FORECAST_CALIBRATION_DATES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (dates.add(date)) {
            prefs.edit().putStringSet(FORECAST_CALIBRATION_DATES_KEY, dates).apply()
            auditLogger.log("FORECAST", "CALIBRATION_SESSION_COMPLETED", mapOf(
                "date" to date,
                "completed_sessions" to dates.size,
                "goal" to MarketPriorityPolicy.LIVE_FORECAST_UNLOCK_SESSIONS
            ))
        }
    }

    suspend fun forecastLiveUnlocked(): Boolean =
        MarketPriorityPolicy.liveForecastUnlocked(forecastCalibrationDates().size)

    suspend fun generateDailyForecasts(force: Boolean = false): List<ForecastDto> {
        runCatching { ensureFixedEngineSnapshot() }
        ensureHistoricalSeed()
        val now = ZonedDateTime.now(INDIA)
        val today = now.toLocalDate().toString()
        val existing = learningDao.longForecastsForDate(today)
        if (now.dayOfWeek.value >= 6 || now.toLocalTime() < LocalTime.of(9, 15) || now.toLocalTime() > LocalTime.of(15, 0)) {
            return existing.map { it.toForecastDto() }
        }
        val nowMs = System.currentTimeMillis()
        val scanInterval = if (forecastDiscoveryNextBatch > 0 || forecastDiscoveryMustClear || forecastWaitingForSharedBaseline || forecastDetailQueue.isNotEmpty())
            45_000L else FORECAST_SCAN_INTERVAL_MS
        if (!force && nowMs - lastForecastScanAtMs < scanInterval) return existing.map { it.toForecastDto() }
        val token = forecastMarketToken() ?: return existing.map { it.toForecastDto() }
        lastForecastScanAtMs = nowMs
        val scanStartedElapsedMs = SystemClock.elapsedRealtime()
        val apiBefore = GrowwApiUsageLedger.snapshot()
        val cacheHitsBefore = GrowwApiUsageLedger.cacheHitCount()
        val cacheMissesBefore = GrowwApiUsageLedger.cacheMissCount()

        val forecastHistoricalReferences = ForecastMultifyReferenceLedger.build(
            ensureFixedEngineSnapshot().payload.learningCalls, learningDao.allCalls()
        )
        auditLogger.log("FORECAST_REFERENCE", "GROWING_MULTIFY_LEDGER", mapOf(
            "frozen" to FixedFinalContract.CANONICAL_LEARNING_CALLS,
            "live_appended" to forecastHistoricalReferences.size - FixedFinalContract.CANONICAL_LEARNING_CALLS,
            "total" to forecastHistoricalReferences.size,
            "fixed_model_changed" to false
        ))
        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }
        val recentDates = calls.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        val rolling = calls.filter { it.callDate in recentDates }
        val multifyPositiveTransfer = multifyPositiveTransferCases(today)
        val multifyPositiveSymbols = multifyPositiveTransfer.map { it.symbol }
        val frequency = multifyPositiveSymbols.groupingBy { it }.eachCount()
        val recentSymbols = frequency.entries.sortedByDescending { it.value }.take(12).map { it.key }
        val fixedPositiveTransfer = fixedPositiveTransferCases(today)
        val fixedPositiveSymbols = fixedPositiveTransfer.map { it.symbol }
        val dnaSymbols = (
            fixedPositiveSymbols +
                multifyPositiveSymbols +
                MultifyReverseEngineering.positiveCases.map { it.symbol }
            ).distinct()
        val master = nseSymbols.symbols()

        // v7.3 persists broad discovery state across Android process death. A truly cold pass is
        // research-only; production waits for healthy market-wide baseline coverage.
        val discoveryStartedElapsedMs = SystemClock.elapsedRealtime()
        val discoveryPass = if (master.isEmpty()) {
            MarketDiscoveryPass(emptyList(), false, 0.0, false, 0)
        } else {
            discoverMarketWideCandidates(token, master, today)
        }
        val discoveryElapsedMs = SystemClock.elapsedRealtime() - discoveryStartedElapsedMs
        val discoveredSymbols = discoveryPass.symbols
        if (!discoveryPass.baselineReady) {
            auditLogger.log("FORECAST_DISCOVERY", "AWAIT_FULL_NSE_BASELINE", mapOf(
                "date" to today, "master_count" to master.size,
                "coverage_pct" to (discoveryPass.coverageRatio * 100.0),
                "resuming_at_batch" to forecastDiscoveryNextBatch,
                "next_probe_earliest_seconds" to 60,
                "fixed_formula_changed" to false
            ))
            return existing.map { it.toForecastDto() }
        }

        val slot = (now.hour * 60 + now.minute) / 30
        val rotating = if (master.isEmpty()) emptyList() else {
            val seed = kotlin.math.abs(today.hashCode() + slot * 977)
            (0 until min(DISCOVERY_ROTATING_FALLBACK, master.size)).map { i ->
                master[Math.floorMod(seed + i * 131, master.size)]
            }
        }
        val universe = (discoveredSymbols + dnaSymbols + recentSymbols + rotating)
            .distinct()
            .take(DETAILED_FORECAST_UNIVERSE_LIMIT)
        // v7.9.4: partial Forecast work must not be thrown away whenever Fixed starts.
        // Evaluate fresh quotes/candles, one bounded tranche per opportunistic cycle.
        // Any qualified/watching candidates are captured this cycle for after-market research.
        val tranche = forecastNextEvaluationTranche(today, universe)
            .take(ForecastResearchWindowPolicy.trancheSize(forecastBoostActive(), forecastPostFixedOpportunity, FORECAST_EVALUATION_TRANCHE_SIZE))
        val evaluated = mutableListOf<Pair<String, StrategyEvaluation>>()
        val detailedEvaluationStartedElapsedMs = SystemClock.elapsedRealtime()
        var processed = 0
        for (symbol in tranche) {
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight || !forecastMarketCapacityAvailable()) {
                auditLogger.log("FORECAST", "EVALUATION_PAUSED_FOR_FIXED", mapOf(
                    "processed" to processed,
                    "remaining" to forecastDetailQueue.size - forecastDetailCursor,
                    "reason" to "FIXED_PRIORITY_OR_CAPACITY"
                ))
                break
            }
            // Advance only after attempting the symbol. A failed API request is revisited
            // in the next complete evaluation cycle; it cannot poison today's remaining queue.
            val evaluationResult = runCatching { forecastEvaluation(symbol, token) }
            // A Fixed-priority yield is not a failed stock evaluation: retain this queue
            // position and retry when spare broker capacity returns.
            if (evaluationResult.exceptionOrNull() is ForecastPriorityYieldException) {
                auditLogger.logAsync("FORECAST", "EVALUATION_PRESERVED_FOR_FIXED", mapOf(
                    "symbol" to symbol,
                    "pending" to (forecastDetailQueue.size - forecastDetailCursor).coerceAtLeast(0),
                    "retry_budget_consumed" to false
                ))
                break
            }
            val evaluation = evaluationResult.onFailure {
                auditLogger.log("FORECAST_RESEARCH", "EVALUATION_ERROR", mapOf(
                    "symbol" to symbol, "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull()
            forecastDetailCursor++
            processed++
            if (evaluation == null) {
                val attempts = (forecastDetailRetryAttempts[symbol] ?: 0) + 1
                forecastDetailRetryAttempts[symbol] = attempts
                // Move transient failures to the back so other candidates can be evaluated.
                // Retry at most twice within the same queue; no endless API hammering.
                if (attempts < 3) forecastDetailQueue = forecastDetailQueue + symbol
                auditLogger.log("FORECAST", "EVALUATION_RETRY_QUEUED", mapOf(
                    "symbol" to symbol, "attempt" to attempts,
                    "will_retry" to (attempts < 3),
                    "pending" to (forecastDetailQueue.size - forecastDetailCursor).coerceAtLeast(0)
                ))
            } else {
                forecastDetailRetryAttempts.remove(symbol)
                if (evaluation.features.ltp >= 10.0) evaluated += symbol to evaluation
            }
            delay(FORECAST_SYMBOL_PACING_MS)
        }
        if (forecastDetailCursor >= forecastDetailQueue.size) {
            forecastDetailQueue = emptyList()
            forecastDetailCursor = 0
        }
        auditLogger.log("FORECAST", "RESUMABLE_EVALUATION_PROGRESS", mapOf(
            "evaluated_this_cycle" to evaluated.size,
            "attempted_this_cycle" to processed,
            "pending" to (forecastDetailQueue.size - forecastDetailCursor).coerceAtLeast(0),
            "date" to today,
            "candle_interval" to "1minute"
        ))
        val detailedEvaluationElapsedMs = SystemClock.elapsedRealtime() - detailedEvaluationStartedElapsedMs

        val breadth = evaluated.mapNotNull { it.second.features.dayChangePct }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val marketRegime = when {
            breadth >= .50 -> "BREADTH_BULL"
            breadth <= -.50 -> "BREADTH_WEAK"
            else -> "BREADTH_NEUTRAL"
        }
        val champions = learningDao.allForecastChampions().associateBy { it.key }
        val learned = learningStats()
        val forecastSettings = preferences.settings.first()
        val priorForecasts = learningDao.allLongForecasts().filter {
            it.forecastDate < today && it.status in setOf("TARGET_HIT", "MISSED") &&
                ForecastUniversalPositiveEvidencePolicy.qualifies(
                    realizedPct = null,
                    closePct = if (it.entryPrice > 0.0) (it.lastPrice / it.entryPrice - 1.0) * 100.0 else null,
                    maxFavourablePct = it.maxFavourablePct,
                    targetHit = it.status == "TARGET_HIT"
                )
        }
        val priorForecastMfeRows = priorForecasts.filter {
            it.maxFavourablePct.isFinite() && it.maxFavourablePct >= 0.0 && it.maxFavourablePct < 25.0
        }
        val priorMfe = priorForecastMfeRows.map { it.maxFavourablePct }
        val priorForecastTargetDays = priorForecastMfeRows.map { it.forecastDate }.distinct().size
        val priorForecastTargetSymbols = priorForecastMfeRows.map { it.symbol.uppercase(Locale.US) }.distinct().size
        // Two early Forecast outcomes had been allowed to dominate the target at 2.60%.
        // Require breadth before Forecast MFE can override the rolling Multify MFE benchmark.
        val maturePriorMfe = if (ForecastTargetMaturityPolicy.isMature(
                priorMfe.size, priorForecastTargetDays, priorForecastTargetSymbols
            )) priorMfe else emptyList()
        val targetPct = ForecastQualityPolicy.adaptiveTargetPct(learned.longAveragePct, maturePriorMfe)

        val latestBySymbol = existing.groupBy { it.symbol.uppercase(Locale.US) }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.generatedAtMs }!! }
        val priorTodayResearch = learningDao.forecastResearchForDate(today)
            .filter { it.scanAtMs < nowMs && nowMs - it.scanAtMs <= PRIMARY_CONFIRMATION_WINDOW_MS }
        val priorResearchDates = learningDao.forecastResearchDates()
            .filter { it < today }
            .take(30)
            .toSet()
        val priorResearchAll = if (priorResearchDates.isEmpty()) emptyList() else {
            learningDao.allForecastResearch()
                .filter { it.researchDate in priorResearchDates && it.outcomeUpdatedAtMs != null }
                .groupBy { it.researchDate + "|" + it.symbol }
                .mapNotNull { (_, rows) -> rows.minByOrNull { it.scanAtMs } }
        }
        val priorResearch = priorResearchAll.filter {
            ForecastUniversalPositiveEvidencePolicy.qualifies(
                realizedPct = null,
                closePct = it.outcomeClosePct,
                maxFavourablePct = it.outcomeMfePct ?: 0.0,
                targetHit = it.outcomeTargetHit == true
            )
        }
        val timingHistoricalExamples = timingExamples(priorResearchAll)
        val historicalOutcomes = priorResearchAll.mapNotNull { row ->
            val mfe = row.outcomeMfePct ?: return@mapNotNull null
            val mae = row.outcomeMaePct ?: return@mapNotNull null
            val close = row.outcomeClosePct ?: return@mapNotNull null
            ForecastHistoricalOutcome(
                date = row.researchDate,
                symbol = row.symbol,
                strategy = row.strategy,
                regime = row.regime,
                marketRegime = row.marketRegime,
                maxFavourablePct = mfe,
                maxAdversePct = mae,
                closePct = close,
                targetHit = row.outcomeTargetHit == true
            )
        } + adaptiveFixedEvidenceRecords().asSequence()
            .filter { row -> row.recommendationDate < today && row.status != "ACTIVE" &&
                row.maxFavourablePct.isFinite() && row.maxAdversePct.isFinite() &&
                row.entryPrice > 0.0 && row.lastPrice.isFinite() && row.lastPrice > 0.0 }
            .map { row ->
                ForecastHistoricalOutcome(
                    date = row.recommendationDate,
                    symbol = row.symbol,
                    strategy = row.strategy,
                    regime = row.regime,
                    marketRegime = row.marketRegime,
                    maxFavourablePct = row.maxFavourablePct,
                    maxAdversePct = row.maxAdversePct,
                    closePct = (row.lastPrice / row.entryPrice - 1.0) * 100.0,
                    targetHit = row.maxFavourablePct >= row.targetPct && row.targetPct > 0.0
                )
            }.toList()
        auditLogger.logAsync("FORECAST_RESEARCH", "FIXED_MISSES_IMPORTED_TO_SHADOW", mapOf(
            "historical_outcomes_including_fixed" to historicalOutcomes.size,
            "failed_outcomes_in_shadow" to historicalOutcomes.count { !it.targetHit },
            "live_forecast_gate_changed" to false,
            "fixed_formula_changed" to false
        ))

        fun researchEvidenceBonus(evaluation: StrategyEvaluation): Double {
            val xs = priorResearch.filter {
                it.regime == evaluation.regime && it.strategy == evaluation.strategy
            }
            if (xs.size < MIN_RESEARCH_EVIDENCE_ROWS) return 0.0
            val hitRate = xs.count { it.outcomeTargetHit == true }.toDouble() / xs.size
            val avgMfe = xs.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val hitComponent = ((hitRate - 0.50) * 0.14).coerceAtLeast(0.0)
            val mfeComponent = if (targetPct > 0.0)
                ((((avgMfe - targetPct) / targetPct).coerceIn(-1.0, 1.0)) * 0.04).coerceAtLeast(0.0)
            else 0.0
            return (hitComponent + mfeComponent).coerceIn(0.0, 0.10)
        }

        val universalPositiveEvidence = universalPositiveEvidenceCases(today)

        val ranked = evaluated.map { row ->
            val minimumNetTargetPct = ForecastAdaptiveSelectionPolicy.minimumTargetPctForNetReturn(
                entryPrice = row.second.features.ltp,
                budgetRupees = forecastSettings.holdingBudgetRupees,
                spreadBps = row.second.features.spreadBps,
                targetNetReturnPct = ForecastAdaptiveSelectionPolicy.MIN_NET_RETURN_PCT
            )
            val candidateBaseTargetPct = max(targetPct, minimumNetTargetPct)
            val baseQuality = com.multify.traderpro.engine.ForecastEarlyEntryPolicy.quality(row.second, candidateBaseTargetPct)
            val expandedTargetPct = ForecastTargetExpansionPolicy.targetPct(
                baseTargetPct = candidateBaseTargetPct,
                symbol = row.first,
                strategy = row.second.strategy,
                regime = row.second.regime,
                marketRegime = marketRegime,
                volatilityCapacityPct = baseQuality.volatilityCapacityPct,
                positives = fixedPositiveTransfer
            )
            val expandedQuality = com.multify.traderpro.engine.ForecastEarlyEntryPolicy.quality(row.second, expandedTargetPct)
            val useExpandedTarget = expandedTargetPct > candidateBaseTargetPct &&
                expandedQuality.tier == baseQuality.tier &&
                expandedQuality.publishEligible == baseQuality.publishEligible &&
                expandedQuality.executionEligible == baseQuality.executionEligible
            val candidateTargetPct = if (useExpandedTarget) expandedTargetPct else candidateBaseTargetPct
            val quality = if (useExpandedTarget) expandedQuality else baseQuality
            val factorModel = MultifyFactorFamilies.evaluate(row.second, marketRegime)
            val key = championKey(marketRegime, row.second.regime, row.second.strategy)
            val champion = champions[key]
            val championWinRate = champion?.takeIf { it.sampleCount > 0 }?.let { it.wins.toDouble() / it.sampleCount } ?: 0.0
            val championBonus = when {
                champion?.frozen == true && championWinRate >= .75 -> .08
                champion != null && champion.sampleCount >= 3 && championWinRate >= .75 -> .04
                else -> 0.0
            }
            val frequencyBonus = min(.04, (frequency[row.first] ?: 0) * .004)
            val dnaBonus = if (row.first in dnaSymbols) .08 else 0.0
            val qualityBonus = ForecastQualityPolicy.rankingBonus(row.second, quality.volatilityCapacityPct, candidateTargetPct, quality.tier)
            val researchBonus = researchEvidenceBonus(row.second)
            val familyBonus = MultifyFactorFamilies.rankingBonus(factorModel)
            val fixedPositiveBonus = ForecastFixedPositiveTransferPolicy.rankingBonus(
                row.first, row.second.strategy, row.second.regime, marketRegime, fixedPositiveTransfer
            )
            val multifyPositiveBonus = ForecastMultifyPositiveTransferPolicy.rankingBonus(
                row.first, multifyPositiveTransfer
            )
            val historicalReferenceBonus = ForecastMultifyReferenceLedger.historicalContextBonus(
                row.first, today, forecastHistoricalReferences
            )
            // Only rank already-qualified EARLY ENTRY calls by pre-spike structure.
            // A historical winner, a coil or volume build cannot bypass eligibility.
            val preSpikeEvidence = ForecastPreSpikeContextPolicy.evaluate(row.second.features.candles)
            val preSpikeBonus = if (quality.publishEligible) preSpikeEvidence.rankingBonus else 0.0
            val universalEvidenceBonus = ForecastUniversalPositiveEvidencePolicy.rankingBonus(
                symbol = row.first,
                strategy = row.second.strategy,
                regime = row.second.regime,
                marketRegime = marketRegime,
                sectorRegime = "UNAVAILABLE",
                capBucket = ForecastUniversalPositiveEvidencePolicy.capBucket(row.second.features.marketCap),
                timeBucket = ForecastUniversalPositiveEvidencePolicy.timeBucket(nowMs),
                dominantFamily = factorModel.dominantFamily,
                evidence = universalPositiveEvidence
            )
            val atrPct = row.second.features.atr14?.takeIf { row.second.features.ltp > 0.0 }
                ?.let { it / row.second.features.ltp * 100.0 }
            val economics = ForecastAdaptiveSelectionPolicy.economics(
                entryPrice = row.second.features.ltp,
                targetPct = candidateTargetPct,
                budgetRupees = forecastSettings.holdingBudgetRupees,
                spreadBps = row.second.features.spreadBps,
                atrPct = atrPct
            )
            val evidenceDiagnostics = ForecastAdaptiveSelectionPolicy.diagnostics(
                strategy = row.second.strategy,
                regime = row.second.regime,
                marketRegime = marketRegime,
                outcomes = historicalOutcomes
            )
            ForecastCandidate(
                symbol = row.first,
                evaluation = row.second,
                quality = quality,
                factorModel = factorModel,
                score = (
                    row.second.confidence +
                    row.second.directionalScore.coerceAtLeast(0.0) * 0.10 +
                    championBonus + frequencyBonus + dnaBonus + qualityBonus + researchBonus + familyBonus +
                    fixedPositiveBonus + multifyPositiveBonus + historicalReferenceBonus + preSpikeBonus + universalEvidenceBonus
                ).coerceIn(0.01, .99),
                championKey = key,
                targetPct = candidateTargetPct,
                economics = economics,
                evidenceDiagnostics = evidenceDiagnostics
            )
        }.sortedWith(
            compareByDescending<ForecastCandidate> {
                when (it.quality.tier) { "PRIMARY" -> 3; "SECONDARY" -> 2; else -> 1 }
            }.thenByDescending { it.score }
                .thenByDescending { it.economics.riskAdjustedScore }
        )

        val capturedResearchDates = learningDao.forecastResearchDates()
            .filter { it >= V7_RESEARCH_COHORT_START_DATE }
        // Shadow research continues after the five-session live unlock so Forecast can keep
        // improving from positive outcomes. API-heavy outcome backfill stays after market hours.
        val researchCaptureAllowed = today >= V7_RESEARCH_COHORT_START_DATE
        if (researchCaptureAllowed) {
            captureForecastResearchScan(today, nowMs, marketRegime, targetPct, ranked.take(RESEARCH_CAPTURE_TOP_N), timingHistoricalExamples)
        }

        // v7.3.3: there is no daily numeric recommendation cap.
        // Multiple distinct intraday spikes are allowed only AFTER the preceding campaign hit
        // its target, cooled down and price rebased. The prior target-hit row stays locked.
        val unseen = ranked.filter { item ->
            val prior = latestBySymbol[item.symbol.uppercase(Locale.US)]
            com.multify.traderpro.engine.ForecastRepeatEntryPolicy.mayPublish(
                prior?.status, prior?.generatedAtMs, prior?.targetPrice,
                item.evaluation.features.ltp, nowMs
            )
        }
        fun priorPrimaryHits(symbol: String): Int = priorTodayResearch
            .asSequence()
            .filter {
                it.symbol.equals(symbol, true) &&
                    it.qualityTier == "PRIMARY" &&
                    it.publishEligible &&
                    it.executionEligible
            }
            .map { it.scanAtMs }
            .distinct()
            .count()

        val completedCalibrationSessions = forecastCalibrationDates().size
        val liveForecastReady = MarketPriorityPolicy.liveForecastUnlocked(completedCalibrationSessions)
        val publishable = unseen.filter { item ->
            val currentPrimary =
                item.quality.tier == "PRIMARY" && item.quality.publishEligible && item.quality.executionEligible &&
                    item.economics.profitableAfterCosts
            ForecastPublicationPolicy.isReady(
                discoveryBaselineReady = discoveryPass.baselineReady,
                currentPrimary = currentPrimary,
                discoveredMarketWide = item.symbol in discoveredSymbols,
                priorPrimaryHits = priorPrimaryHits(item.symbol)
            )
        }

        publishable.forEachIndexed { index, item ->
            val price = item.evaluation.features.ltp
            val quality = item.quality
            learningDao.upsertLongForecast(
                LongForecastEntity(
                    forecastDate = today,
                    rank = existing.size + index + 1,
                    symbol = item.symbol,
                    entryPrice = price,
                    targetPct = item.targetPct,
                    targetPrice = price * (1.0 + item.targetPct / 100.0),
                    confidence = item.evaluation.confidence,
                    score = item.score,
                    strategy = item.evaluation.strategy,
                    regime = item.evaluation.regime,
                    marketRegime = marketRegime,
                    championKey = item.championKey,
                    reason = quality.reason + " · " + item.evaluation.reason +
                        " · Entry intelligence " + run {
                            val context = ForecastPreSpikeContextPolicy.evaluate(item.evaluation.features.candles)
                            val advice = timingAdvice(marketRegime, nowMs, item.evaluation.features.rvol,
                                context.compressionRatio, context.risingFloor, timingHistoricalExamples, today)
                            advice.method.name + if (advice.validated) " (validated advisory)" else " (learning)"
                        } +
                        " · family " + item.factorModel.dominantFamily +
                        " · family consensus " + fmt(item.factorModel.consensusScore * 100.0) + "%" +
                        " · capacity " + fmt(quality.volatilityCapacityPct) + "% for " + fmt(item.targetPct) + "% target" +
                        " · est net ₹" + fmt(item.economics.netProfitRupees) +
                        " (" + fmt(item.economics.netReturnPct) + "%) after charges/slippage" +
                        " · risk-adjusted " + fmt(item.economics.riskAdjustedScore) +
                        " · evidence " + item.evidenceDiagnostics.sampleCount + " samples/" +
                        item.evidenceDiagnostics.distinctDays + "d/" + item.evidenceDiagnostics.distinctSymbols + " stocks" +
                        if (item.targetPct > targetPct) " · positive Fixed upside expansion +" + fmt(item.targetPct - targetPct) + "pp" else "",
                    generatedAtMs = nowMs,
                    lastPrice = price,
                    lastObservedAtMs = nowMs,
                    qualityTier = quality.tier,
                    executionEligible = quality.executionEligible
                )
            )
        }

        val all = learningDao.longForecastsForDate(today)
        val tierCounts = ranked.groupingBy { it.quality.tier }.eachCount()
        auditLogger.log("FORECAST", "LONG_SCAN_TIERED", mapOf(
            "date" to today,
            "evaluated" to evaluated.size,
            "primary" to (tierCounts["PRIMARY"] ?: 0),
            "secondary" to (tierCounts["SECONDARY"] ?: 0),
            "research" to (tierCounts["RESEARCH"] ?: 0),
            "published_count" to all.size,
            "daily_recommendation_cap" to "NONE",
            "newly_publishable_count" to publishable.size,
            "base_target_pct" to targetPct,
            "prior_forecast_target_samples" to priorMfe.size,
            "prior_forecast_target_days" to priorForecastTargetDays,
            "prior_forecast_target_symbols" to priorForecastTargetSymbols,
            "prior_forecast_target_mature" to ForecastTargetMaturityPolicy.isMature(
                priorMfe.size, priorForecastTargetDays, priorForecastTargetSymbols
            ),
            "net_return_floor_pct" to ForecastAdaptiveSelectionPolicy.MIN_NET_RETURN_PCT,
            "max_published_target_pct" to (publishable.maxOfOrNull { it.targetPct } ?: targetPct),
            "positive_target_expansion_enabled" to true,
            "target_floor_pct" to ForecastQualityPolicy.MIN_TARGET_PCT,
            "multify_mfe_avg_pct" to learned.longAveragePct,
            "breadth_pct" to breadth,
            "market_regime" to marketRegime,
            "prior_research_sessions" to priorResearchDates.size,
            "prior_research_rows" to priorResearch.size,
            "fixed_positive_transfer_cases" to fixedPositiveTransfer.size,
            "fixed_positive_transfer_symbols" to fixedPositiveSymbols.distinct().size,
            "multify_positive_transfer_cases" to multifyPositiveTransfer.size,
            "multify_positive_transfer_symbols" to multifyPositiveSymbols.distinct().size,
            "positive_transfer_only" to true,
            "universal_positive_evidence_rows" to universalPositiveEvidence.size,
            "universal_positive_sources" to universalPositiveEvidence.groupingBy { it.source }.eachCount(),
            "universal_evidence_negative_penalties" to false,
            "live_forecast_unlocked" to liveForecastReady,
            "live_forecast_completed_sessions" to completedCalibrationSessions,
            "live_forecast_session_goal" to MarketPriorityPolicy.LIVE_FORECAST_UNLOCK_SESSIONS,
            "research_capture_allowed" to researchCaptureAllowed,
            "research_sessions_captured" to capturedResearchDates.size,
            "research_session_goal" to RESEARCH_SESSION_GOAL,
            "research_cohort_start_date" to V7_RESEARCH_COHORT_START_DATE,
            "nse_master_count" to master.size,
            "market_wide_discovered" to discoveredSymbols.size,
            "discovery_baseline_ready" to discoveryPass.baselineReady,
            "discovery_coverage_pct" to (discoveryPass.coverageRatio * 100.0),
            "discovery_state_restored" to discoveryPass.restoredFromDisk,
            "discovery_batch_errors" to discoveryPass.batchErrors,
            "detailed_universe_count" to universe.size,
            "arm_effective" to preferences.settings.first().armEffective,
            "forecasting_independent_of_arm" to true,
            "publishable_after_confirmation" to publishable.size,
            "shadow_stricter_filter_pass_count" to ranked.count { ForecastAdaptiveSelectionPolicy.shadowFilterReview(it.evaluation, it.targetPct).passes },
            "shadow_stricter_filter_applied_live" to false,
            "publication_suppressed_until_discovery_ready" to !discoveryPass.baselineReady,
            "top_candidates" to ranked.take(5).map {
                mapOf("symbol" to it.symbol, "tier" to it.quality.tier, "score" to it.score,
                    "target_pct" to it.targetPct,
                    "confidence" to it.evaluation.confidence, "directional_score" to it.evaluation.directionalScore,
                    "dominant_family" to it.factorModel.dominantFamily,
                    "family_consensus" to it.factorModel.consensusScore,
                    "positive_families" to it.factorModel.positiveFamilies,
                    "market_wide_discovered" to (it.symbol in discoveredSymbols),
                    "prior_primary_confirmations" to priorPrimaryHits(it.symbol),
                    "estimated_net_profit_rupees" to it.economics.netProfitRupees,
                    "estimated_net_return_pct" to it.economics.netReturnPct,
                    "estimated_charges_rupees" to it.economics.estimatedChargesRupees,
                    "estimated_slippage_rupees" to it.economics.estimatedSlippageRupees,
                    "risk_adjusted_score" to it.economics.riskAdjustedScore,
                    "historical_sample_count" to it.evidenceDiagnostics.sampleCount,
                    "historical_distinct_days" to it.evidenceDiagnostics.distinctDays,
                    "historical_distinct_symbols" to it.evidenceDiagnostics.distinctSymbols,
                    "historical_positive_rate_pct" to it.evidenceDiagnostics.positiveRatePct,
                    "historical_target_hit_rate_pct" to it.evidenceDiagnostics.targetHitRatePct,
                    "historical_median_mfe_pct" to it.evidenceDiagnostics.medianMfePct,
                    "historical_median_mae_pct" to it.evidenceDiagnostics.medianMaePct,
                    "historical_median_close_pct" to it.evidenceDiagnostics.medianClosePct,
                    "shadow_evidence_confidence_bonus" to ForecastAdaptiveSelectionPolicy.positiveConfidenceBonus(it.evidenceDiagnostics),
                    "shadow_evidence_confidence_bonus_applied_live" to false,
                    "shadow_economics_bonus" to ForecastAdaptiveSelectionPolicy.economicsBonus(it.economics),
                    "shadow_risk_bonus" to ForecastAdaptiveSelectionPolicy.riskBonus(it.economics),
                    "shadow_economics_risk_bonus_applied_live" to false,
                    "shadow_context_target_pct" to ForecastAdaptiveSelectionPolicy.shadowTargetPct(it.targetPct, it.evidenceDiagnostics),
                    "shadow_context_target_applied_live" to false,
                    "shadow_filter_pass" to ForecastAdaptiveSelectionPolicy.shadowFilterReview(it.evaluation, it.targetPct).passes,
                    "shadow_filter_failures" to ForecastAdaptiveSelectionPolicy.shadowFilterReview(it.evaluation, it.targetPct).failedRules,
                    "shadow_filter_applied_live" to false,
                    "out_of_sample_validation_samples" to it.evidenceDiagnostics.validationSamples,
                    "out_of_sample_target_hit_rate_pct" to it.evidenceDiagnostics.validationTargetHitRatePct,
                    "shadow_negative_penalty" to it.evidenceDiagnostics.shadowPenalty,
                    "shadow_negative_penalty_applied_live" to false,
                    "publication_ready" to ForecastPublicationPolicy.isReady(
                        discoveryPass.baselineReady,
                        it.quality.tier == "PRIMARY" && it.quality.publishEligible && it.quality.executionEligible &&
                            it.economics.profitableAfterCosts,
                        it.symbol in discoveredSymbols,
                        priorPrimaryHits(it.symbol)
                    ),
                    "reason" to it.quality.reason)
            }
        ))
        val apiAfter = GrowwApiUsageLedger.snapshot()
        fun apiDelta(category: GrowwApiUsageLedger.Category): Map<String, Any?> {
            val before = apiBefore.categories.first { it.category == category }
            val after = apiAfter.categories.first { it.category == category }
            return mapOf(
                "requests" to (after.totalRequests - before.totalRequests),
                "rate_limits" to (after.rateLimitResponses - before.rateLimitResponses),
                "network_ms" to (after.networkMs - before.networkMs),
                "permit_wait_count" to (after.permitWaitCount - before.permitWaitCount),
                "permit_wait_ms" to (after.permitWaitMs - before.permitWaitMs),
                "minute_utilization_pct_end" to after.minuteUtilizationPct,
                "minute_headroom_end" to after.minuteHeadroom
            )
        }
        auditLogger.logAsync("FORECAST_TELEMETRY", "SCAN_PROFILE", mapOf(
            "date" to today,
            "scan_elapsed_ms" to (SystemClock.elapsedRealtime() - scanStartedElapsedMs),
            "discovery_elapsed_ms" to discoveryElapsedMs,
            "detailed_evaluation_elapsed_ms" to detailedEvaluationElapsedMs,
            "master_count" to master.size,
            "universe_count" to universe.size,
            "evaluated_count" to evaluated.size,
            "published_new_count" to publishable.size,
            "cache_hits" to (GrowwApiUsageLedger.cacheHitCount() - cacheHitsBefore),
            "cache_misses" to (GrowwApiUsageLedger.cacheMissCount() - cacheMissesBefore),
            "live_data" to apiDelta(GrowwApiUsageLedger.Category.LIVE_DATA),
            "historical" to apiDelta(GrowwApiUsageLedger.Category.HISTORICAL),
            "non_trading" to apiDelta(GrowwApiUsageLedger.Category.NON_TRADING),
            "fixed_guard_window_ms" to MarketPriorityPolicy.FIXED_GUARD_WINDOW_MS,
            "live_forecast_unlocked" to liveForecastReady,
            "fixed_formula_changed" to false
        ))
        return all.map { it.toForecastDto() }
    }

    // FIXED_FINAL_LOCK_BEGIN: FIXED-V741-20261021-190239-FINAL
    suspend fun generateFixedForecasts(force: Boolean = false): List<ForecastDto> {
        val snapshot = ensureFixedEngineSnapshot()
        val now = ZonedDateTime.now(INDIA)
        val today = now.toLocalDate().toString()
        val existing = fixedRecommendationStore.recommendations(today)
            .filter { it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES }
        if (now.dayOfWeek.value >= 6 || now.toLocalTime() < LocalTime.of(9, 15) || now.toLocalTime() >= LocalTime.of(15, 30)) {
            return existing.map { it.toDto() }
        }
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastFixedScanAtMs < FIXED_V741_FORECAST_SCAN_INTERVAL_MS) {
            return existing.map { it.toDto() }
        }
        val token = ensureToken() ?: return existing.map { it.toDto() }
        lastFixedScanAtMs = nowMs

        val calls = snapshot.payload.learningCalls.filter { it.action.equals("BUY", true) }
        require(calls.size == FixedFinalContract.CANONICAL_LEARNING_CALLS) { "Final Fixed learning-call count changed" }
        val recentDates = calls.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        val rolling = calls.filter { it.callDate in recentDates }
        val frequency = rolling.groupingBy { it.symbol }.eachCount()
        val recentSymbols = frequency.entries.sortedByDescending { it.value }.take(12).map { it.key }
        val dnaSymbols = FixedMultifyReverseEngineering.positiveCases.map { it.symbol }
        val master = nseSymbols.symbols()

        val scanPhaseStartedMs = SystemClock.elapsedRealtime()
        val priorFull = fixedHistoryFullFetches.get()
        val priorDelta = fixedHistoryDeltaFetches.get()
        val priorFallback = fixedHistoryStitchFallbacks.get()
        val discoveryPass = if (master.isEmpty()) {
            MarketDiscoveryPass(emptyList(), false, 0.0, false, 0)
        } else {
            discoverFixedMarketWideCandidates(token, master, today)
        }
        val discoveryPhaseMs = (SystemClock.elapsedRealtime() - scanPhaseStartedMs).coerceAtLeast(0L)
        val discoveredSymbols = discoveryPass.symbols
        if (!marketDataAvailableForFixed()) {
            fixedScanDeferredBroker = true
            auditLogger.logAsync("FIXED_PERFORMANCE", "SCAN_DEFERRED_BROKER", mapOf(
                "reason" to "BROKER_COOLDOWN_DURING_DISCOVERY",
                "published_from_incomplete_scan" to false
            ))
            return existing.map { it.toDto() }
        }

        val slot = (now.hour * 60 + now.minute) / 30
        val rotating = if (master.isEmpty()) emptyList() else {
            val seed = kotlin.math.abs(today.hashCode() + slot * 977)
            (0 until min(FIXED_V741_DISCOVERY_ROTATING_FALLBACK, master.size)).map { i ->
                master[Math.floorMod(seed + i * 131, master.size)]
            }
        }
        val universe = (discoveredSymbols + dnaSymbols + recentSymbols + rotating)
            .distinct()
            .take(FIXED_V741_DETAILED_FORECAST_UNIVERSE_LIMIT)
        // Preserve the exact v7.4.1 universe, evaluation formulas, breadth and ranking inputs,
        // but fetch independent symbol evaluations in small bounded batches. Reconstruct results in
        // original universe order so this is a latency-only change, not a selection/ranking change.
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val fixedEvaluationConcurrency =
            FixedScanPerformancePolicy.evaluationConcurrency(availableProcessors)
        val evaluatedBySymbol = linkedMapOf<String, FixedStrategyEvaluation>()
        for (batch in universe.chunked(fixedEvaluationConcurrency)) {
            val results = coroutineScope {
                batch.map { symbol ->
                    async {
                        val evaluation = runCatching { fixedForecastEvaluation(symbol, token) }
                            .onFailure {
                                auditLogger.log("FIXED_RESEARCH", "EVALUATION_ERROR", mapOf(
                                    "symbol" to symbol,
                                    "message" to (it.message ?: it.javaClass.simpleName)
                                ))
                            }
                            .getOrNull()
                        symbol to evaluation
                    }
                }.awaitAll()
            }
            results.forEach { (symbol, evaluation) ->
                if (evaluation != null && evaluation.features.ltp >= 10.0) {
                    evaluatedBySymbol[symbol] = evaluation
                }
            }
            if (!marketDataAvailableForFixed()) {
                fixedScanDeferredBroker = true
                auditLogger.logAsync("FIXED_PERFORMANCE", "SCAN_DEFERRED_BROKER", mapOf(
                    "reason" to "BROKER_COOLDOWN_DURING_DETAIL",
                    "evaluated_before_pause" to evaluatedBySymbol.size,
                    "required_candidates" to universe.size,
                    "published_from_incomplete_scan" to false
                ))
                return existing.map { it.toDto() }
            }
            delay(FIXED_V741_FORECAST_SYMBOL_PACING_MS)
        }
        val evaluated = universe.mapNotNull { symbol ->
            evaluatedBySymbol[symbol]?.let { symbol to it }
        }
        auditLogger.logAsync("FIXED_PERFORMANCE", "HISTORICAL_WINDOW_AND_PHASE_PROFILE", mapOf(
            "discovery_ms" to discoveryPhaseMs,
            "detail_ms" to (SystemClock.elapsedRealtime() - scanPhaseStartedMs - discoveryPhaseMs).coerceAtLeast(0L),
            "full_history_requests" to (fixedHistoryFullFetches.get() - priorFull).coerceAtLeast(0L),
            "incremental_history_requests" to (fixedHistoryDeltaFetches.get() - priorDelta).coerceAtLeast(0L),
            "fallback_full_requests" to (fixedHistoryStitchFallbacks.get() - priorFallback).coerceAtLeast(0L),
            "attempted" to universe.size,
            "evaluated" to evaluated.size,
            "signal_intervals" to "fixed_5minute_forecast_1minute"
        ))

        val breadth = evaluated.mapNotNull { it.second.features.dayChangePct }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val marketRegime = when {
            breadth >= .50 -> "BREADTH_BULL"
            breadth <= -.50 -> "BREADTH_WEAK"
            else -> "BREADTH_NEUTRAL"
        }
        // Final Fixed has no historical Forecast/champion/research feedback. Those channels were
        // empty when the reference v7.4.1 session produced the accepted calls and must stay empty
        // forever. Live same-day confirmation observations below are still part of v7.4.1 logic.
        val champions = emptyMap<String, com.multify.traderpro.data.local.ForecastChampionEntity>()
        val learnedAverage = fixedSnapshotMultifyMfeAverage(snapshot)
        val targetPct = FixedForecastQualityPolicy.adaptiveTargetPct(learnedAverage, emptyList())

        val have = existing.map { it.symbol }.toSet()
        val priorTodayResearch = fixedRecommendationStore.observations(today)
            .filter { it.scanAtMs < nowMs && nowMs - it.scanAtMs <= FIXED_V741_PRIMARY_CONFIRMATION_WINDOW_MS }
        val priorResearch = emptyList<com.multify.traderpro.data.local.ForecastResearchEntity>()

        fun researchEvidenceBonus(evaluation: FixedStrategyEvaluation): Double {
            val xs = priorResearch.filter {
                it.regime == evaluation.regime && it.strategy == evaluation.strategy
            }
            if (xs.size < FIXED_V741_MIN_RESEARCH_EVIDENCE_ROWS) return 0.0
            val hitRate = xs.count { it.outcomeTargetHit == true }.toDouble() / xs.size
            val avgMfe = xs.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val hitComponent = (hitRate - 0.50) * 0.14
            val mfeComponent = if (targetPct > 0.0)
                (((avgMfe - targetPct) / targetPct).coerceIn(-1.0, 1.0)) * 0.04
            else 0.0
            return (hitComponent + mfeComponent).coerceIn(-0.08, 0.10)
        }

        val ranked = evaluated.map { row ->
            val quality = FixedForecastQualityPolicy.evaluate(row.second, targetPct, marketRegime)
            val factorModel = FixedMultifyFactorFamilies.evaluate(row.second, marketRegime)
            val key = fixedChampionKey(marketRegime, row.second.regime, row.second.strategy)
            val champion = champions[key]
            val championWinRate = champion?.takeIf { it.sampleCount > 0 }?.let { it.wins.toDouble() / it.sampleCount } ?: 0.0
            val championBonus = when {
                champion?.frozen == true && championWinRate >= .75 -> .08
                champion != null && champion.sampleCount >= 3 && championWinRate >= .75 -> .04
                else -> 0.0
            }
            val frequencyBonus = min(.04, (frequency[row.first] ?: 0) * .004)
            val dnaBonus = if (row.first in dnaSymbols) .08 else 0.0
            val qualityBonus = FixedForecastQualityPolicy.rankingBonus(row.second, quality.volatilityCapacityPct, targetPct, quality.tier)
            val researchBonus = researchEvidenceBonus(row.second)
            val familyBonus = FixedMultifyFactorFamilies.rankingBonus(factorModel)
            FixedCandidate(
                symbol = row.first,
                evaluation = row.second,
                quality = quality,
                factorModel = factorModel,
                score = (
                    row.second.confidence +
                        row.second.directionalScore.coerceAtLeast(0.0) * 0.10 +
                        championBonus + frequencyBonus + dnaBonus + qualityBonus + researchBonus + familyBonus
                    ).coerceIn(0.01, .99),
                championKey = key
            )
        }.sortedWith(
            compareByDescending<FixedCandidate> {
                when (it.quality.tier) { "PRIMARY" -> 3; "SECONDARY" -> 2; else -> 1 }
            }.thenByDescending { it.score }
        )

        fixedRecommendationStore.appendObservations(
            ranked.take(FIXED_V741_RESEARCH_CAPTURE_TOP_N).map { item ->
                FixedScanObservation(
                    recommendationDate = today,
                    scanAtMs = nowMs,
                    symbol = item.symbol,
                    qualityTier = item.quality.tier,
                    publishEligible = item.quality.publishEligible,
                    executionEligible = item.quality.executionEligible
                )
            }
        )

        val unseen = ranked.filter { it.symbol !in have }
        fun priorPrimaryHits(symbol: String): Int = priorTodayResearch.asSequence()
            .filter {
                it.symbol.equals(symbol, true) &&
                    it.qualityTier == "PRIMARY" &&
                    it.publishEligible &&
                    it.executionEligible
            }
            .map { it.scanAtMs }
            .distinct()
            .count()

        val publishable = unseen.filter { item ->
            // User-requested Fixed-only overlay. Apply it here, after the full v7.4.1 universe,
            // breadth and ranking have been calculated, so the underlying model remains 1:1.
            if (item.evaluation.features.ltp <= FIXED_MIN_STOCK_PRICE_RUPEES) return@filter false
            val currentPrimary = item.quality.tier == "PRIMARY" &&
                item.quality.publishEligible && item.quality.executionEligible
            FixedForecastPublicationPolicy.isReady(
                discoveryBaselineReady = discoveryPass.baselineReady,
                currentPrimary = currentPrimary,
                discoveredMarketWide = item.symbol in discoveredSymbols,
                priorPrimaryHits = priorPrimaryHits(item.symbol)
            )
        }

        publishable.forEachIndexed { index, item ->
            val price = item.evaluation.features.ltp
            fixedRecommendationStore.upsert(
                FixedRecommendationRecord(
                    id = FixedRecommendationStore.fixedId(today, item.symbol),
                    recommendationDate = today,
                    rank = existing.size + index + 1,
                    symbol = item.symbol,
                    entryPrice = price,
                    targetPct = targetPct,
                    targetPrice = price * (1.0 + targetPct / 100.0),
                    confidence = item.evaluation.confidence,
                    score = item.score,
                    strategy = item.evaluation.strategy,
                    regime = item.evaluation.regime,
                    marketRegime = marketRegime,
                    championKey = item.championKey,
                    reason = "Frozen ${FixedEngineSnapshotStore.SNAPSHOT_ID} · " + item.quality.reason + " · " + item.evaluation.reason +
                        " · family " + item.factorModel.dominantFamily +
                        " · family consensus " + fmt(item.factorModel.consensusScore * 100.0) + "%" +
                        " · capacity " + fmt(item.quality.volatilityCapacityPct) + "% for " + fmt(targetPct) + "% target",
                    generatedAtMs = nowMs,
                    lastPrice = price,
                    lastObservedAtMs = nowMs,
                    qualityTier = item.quality.tier,
                    executionEligible = item.quality.executionEligible
                )
            )
        }

        val all = fixedRecommendationStore.recommendations(today)
            .filter { it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES }
        val tierCounts = ranked.groupingBy { it.quality.tier }.eachCount()
        val primaryTier = ranked.filter { it.quality.tier == "PRIMARY" }
        val primaryEntryEligible = primaryTier.filter { it.quality.publishEligible && it.quality.executionEligible }
        val primaryConfirmationPending = primaryEntryEligible.filter { item ->
            val hits = priorTodayResearch.count {
                it.symbol.equals(item.symbol, true) && it.qualityTier == "PRIMARY" &&
                    it.publishEligible && it.executionEligible
            }
            val discovered = item.symbol in discoveryPass.symbols
            !FixedForecastPublicationPolicy.isReady(
                discoveryBaselineReady = discoveryPass.baselineReady,
                discoveredMarketWide = discovered,
                priorPrimaryHits = hits
            )
        }
        val primarySafetyBlocked = primaryTier.filterNot { it.quality.publishEligible && it.quality.executionEligible }
        val spreadAvailable = ranked.filter { it.evaluation.features.spreadBps != null }
        val spreadMissing = ranked.filter { it.evaluation.features.spreadBps == null }
        auditLogger.log("FIXED_ENGINE", "LONG_SCAN_TIERED", mapOf(
            "snapshot_id" to snapshot.payload.snapshotId,
            "snapshot_sha256" to snapshot.sha256,
            "final_contract_id" to FixedFinalContract.CONTRACT_ID,
            "reference_log_sha256" to FixedFinalContract.SOURCE_LOG_SHA256,
            "price_gate_stage" to "PUBLICATION_ONLY",
            "date" to today,
            "evaluated" to evaluated.size,
            "primary" to (tierCounts["PRIMARY"] ?: 0),
            "secondary" to (tierCounts["SECONDARY"] ?: 0),
            "research" to (tierCounts["RESEARCH"] ?: 0),
            "published_count" to all.size,
            "daily_recommendation_cap" to "NONE",
            "newly_publishable_count" to publishable.size,
            "target_pct" to targetPct,
            "frozen_multify_mfe_avg_pct" to learnedAverage,
            "breadth_pct" to breadth,
            "market_regime" to marketRegime,
            "discovery_baseline_ready" to discoveryPass.baselineReady,
            "discovery_coverage_pct" to discoveryPass.coverageRatio * 100.0,
            "arm_effective" to preferences.settings.first().armEffective,
            "learning_enabled" to false,
            "publishable_after_confirmation" to publishable.size,
            "primary_entry_eligible" to primaryEntryEligible.size,
            "primary_entry_safety_blocked" to primarySafetyBlocked.size,
            "primary_confirmation_pending" to primaryConfirmationPending.size,
            "primary_safety_block_reasons" to primarySafetyBlocked.take(8).map {
                mapOf("symbol" to it.symbol, "reason" to it.quality.reason)
            },
            "primary_confirmation_pending_symbols" to primaryConfirmationPending.take(8).map { it.symbol },
            "spread_available_count" to spreadAvailable.size,
            "spread_missing_count" to spreadMissing.size,
            "spread_missing_symbols" to spreadMissing.take(12).map { it.symbol },
            "available_processors" to availableProcessors,
            "evaluation_concurrency" to fixedEvaluationConcurrency
        ))
        return all.map { it.toDto() }
    }

    private fun fixedSnapshotMultifyMfeAverage(snapshot: FixedEngineSnapshot): Double {
        val calls = snapshot.payload.learningCalls.filter {
            it.action.equals("BUY", true) && it.multifyMfePct != null
        }
        val dates = calls.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        val values = calls.filter { it.callDate in dates }.mapNotNull { it.multifyMfePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        return FixedAdaptiveLearningMath.rollingMean(values, 0.0)
    }

    private suspend fun fixedForecastEvaluation(symbol: String, token: String): FixedStrategyEvaluation {
        val now = ZonedDateTime.now(INDIA)
        val start = now.minusDays(4).toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val quote = withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Fixed quote $symbol")
        }
        // Token-scope the RAM snapshot so a reconnect or account change cannot reuse candles.
        val historyKey = sha256(token).take(12) + ":" + symbol
        val incremental = fixedHistoricalWindowStore.request(historyKey, start, SystemClock.elapsedRealtime())
        if (incremental == null) {
            val reason = fixedHistoricalWindowStore.ineligibilityReason(historyKey, start, SystemClock.elapsedRealtime())
            // Sample at most one diagnostic every 15 seconds; do not slow the Fixed pipeline.
            val nowElapsed = SystemClock.elapsedRealtime()
            val lastLogged = fixedReuseDiagnosticLastLoggedAtMs.get()
            if (nowElapsed - lastLogged >= 15_000L &&
                fixedReuseDiagnosticLastLoggedAtMs.compareAndSet(lastLogged, nowElapsed)) {
                auditLogger.logAsync("FIXED_PERFORMANCE", "HISTORICAL_REUSE_SKIPPED", mapOf(
                    "symbol" to symbol, "reason" to reason
                ))
            }
        }
        val raw = withFixedHistoricalDataSlot {
            apiFactory.groww.historicalCandles(
                bearer(token), growwSymbol = "NSE-$symbol", startTime = incremental?.startTime ?: start,
                endTime = end, candleInterval = "5minute"
            ).requirePayload("Fixed forecast candles $symbol")
        }
        val historical = if (incremental == null) {
            fixedHistoryFullFetches.incrementAndGet()
            raw
        } else {
            val merged = fixedHistoricalWindowStore.stitch(historyKey, start, raw, SystemClock.elapsedRealtime())
            if (merged != null) {
                fixedHistoryDeltaFetches.incrementAndGet()
                merged
            } else {
                // No guessing or partially stale candles: return to authoritative full history.
                fixedHistoryStitchFallbacks.incrementAndGet()
                fixedHistoryFullFetches.incrementAndGet()
                withFixedHistoricalDataSlot {
                    apiFactory.groww.historicalCandles(
                        bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end,
                        candleInterval = "5minute"
                    ).requirePayload("Fixed historical fallback $symbol")
                }
            }
        }
        fixedHistoricalWindowStore.remember(historyKey, start, historical, SystemClock.elapsedRealtime())
        require(historical.candles.size >= 5) { "Insufficient fixed forecast candles" }
        val features = FixedLocalStrategyEngine.buildFeatures(quote, historical, 5)
        val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "fixed-forecast", confidence = 1.0)
        val evaluation = FixedLocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
        // Diagnostic-only, O(1) non-blocking queue offer. All 40 factor calculations and disk
        // writes execute later on a low-priority worker; never delay Fixed recommendations.
        fixedFactorTelemetry.tryRecord(symbol, evaluation, lastFixedScanAtMs)
        return evaluation
    }

    private suspend fun discoverFixedMarketWideCandidates(
        token: String,
        master: List<String>,
        today: String
    ): MarketDiscoveryPass {
        // Fixed owns its discovery baseline. Do not seed it from the adaptive Forecast engine.
        val distinctMaster = master.distinct()
        val persisted = fixedMarketDiscoveryStateStore.load(today)
        val previous = persisted?.previousLtps.orEmpty()
        val sessionOpen = persisted?.sessionOpenLtps.orEmpty()
        val restored = persisted != null
        val current = linkedMapOf<String, Double>()
        var batchErrors = 0
        var consecutiveFailures = 0
        var abortedForNetwork = false
        val chunks = distinctMaster.chunked(50)

        // Fixed's frozen 40-factor score, feature inputs and target calculations are unchanged.
        // The lightweight prefilter adds a reserved pre-spike lane; full scoring and entry safety
        // still decide every publication. LTP batches remain ordered and non-blocking.
        for (windowStart in chunks.indices step FixedDiscoveryWindowScheduler.WINDOW_SIZE) {
            val results = FixedDiscoveryWindowScheduler.fetchOrdered(
                chunks, windowStart
            ) { index, chunk -> fetchFixedDiscoveryLtpBatch(token, chunk, index) }
            for ((index, payload) in results) {
                if (payload == null) {
                    batchErrors++
                    consecutiveFailures++
                    if (consecutiveFailures >= FIXED_V741_DISCOVERY_CONSECUTIVE_FAILURE_ABORT) {
                        abortedForNetwork = true
                        break
                    }
                } else {
                    consecutiveFailures = 0
                    payload.forEach { (key, value) ->
                        if (value.isFinite() && value >= 10.0) {
                            current[key.removePrefix("NSE_").uppercase(Locale.US)] = value
                        }
                    }
                }
                if (index + 1 < chunks.size) delay(FIXED_V741_DISCOVERY_BATCH_PACING_MS)
            }
            if (abortedForNetwork) break
        }

        val denominator = distinctMaster.size.coerceAtLeast(1)
        val coverageRatio = current.size.toDouble() / denominator.toDouble()
        val previousCoverageRatio = previous.size.toDouble() / denominator.toDouble()
        val baselineReady = previousCoverageRatio >= FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO &&
            coverageRatio >= FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO && !abortedForNetwork

        val ranked = if (baselineReady) {
            FixedMarketDiscoveryPolicy.rank(
                currentLtps = current,
                previousLtps = previous,
                sessionOpenLtps = sessionOpen,
                limit = FIXED_V741_DISCOVERY_DYNAMIC_LIMIT
            )
        } else emptyList()

        if (coverageRatio >= FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO && !abortedForNetwork) {
            val mergedSessionOpen = sessionOpen.toMutableMap()
            current.forEach { (symbol, ltp) -> mergedSessionOpen.putIfAbsent(symbol, ltp) }
            fixedMarketDiscoveryStateStore.save(today, mergedSessionOpen, current)
        }
        fixedMarketDiscoveryStateStore.recordHealth(today, coverageRatio, batchErrors, abortedForNetwork, baselineReady)
        preferences.recordMarketData()
        auditLogger.log("FIXED_DISCOVERY", "MARKET_WIDE_SCAN", mapOf(
            "snapshot_id" to FixedEngineSnapshotStore.SNAPSHOT_ID,
            "master_count" to distinctMaster.size,
            "ltp_coverage" to current.size,
            "coverage_pct" to coverageRatio * 100.0,
            "batch_errors" to batchErrors,
            "aborted_for_network" to abortedForNetwork,
            "baseline_ready" to baselineReady,
            "state_restored" to restored,
            "selected" to ranked.size,
            "momentum_lane_selected" to ranked.count { it.lane == "MOMENTUM" },
            "early_pre_spike_lane_selected" to ranked.count { it.lane == "EARLY_PRE_SPIKE" },
            "early_pre_spike_symbols" to ranked.filter { it.lane == "EARLY_PRE_SPIKE" }.map { it.symbol }.take(12)
        ))
        return MarketDiscoveryPass(ranked.map { it.symbol }, baselineReady, coverageRatio, restored, batchErrors)
    }

    private suspend fun fetchFixedDiscoveryLtpBatch(
        token: String,
        chunk: List<String>,
        batchIndex: Int
    ): Map<String, Double>? {
        var lastFailure: Throwable? = null
        for (attempt in 0 until FIXED_V741_DISCOVERY_BATCH_ATTEMPTS) {
            try {
                val exchangeSymbols = chunk.joinToString(",") { "NSE_$it" }
                return withFixedLiveDataSlot {
                    apiFactory.groww.ltp(bearer(token), exchangeSymbols = exchangeSymbols)
                        .requirePayload("Fixed market-wide discovery LTP")
                }
            } catch (t: Throwable) {
                lastFailure = t
                val transient = t is IOException || (t is HttpException && (t.code() == 429 || t.code() >= 500))
                if (!transient || attempt + 1 >= FIXED_V741_DISCOVERY_BATCH_ATTEMPTS) break
                delay(if (t is HttpException && t.code() == 429) 1_250L else FIXED_V741_DISCOVERY_RETRY_BACKOFF_MS)
            }
        }
        auditLogger.log("FIXED_DISCOVERY", "LTP_BATCH_ERROR", mapOf(
            "batch_index" to batchIndex,
            "batch_size" to chunk.size,
            "message" to (lastFailure?.message ?: lastFailure?.javaClass?.simpleName ?: "unknown")
        ))
        return null
    }
    // FIXED_FINAL_LOCK_END: FIXED-V741-20261021-190239-FINAL

    /** Forecast-only deterministic checkpoint. The Fixed universe/ranking is not touched. */
    private fun forecastNextEvaluationTranche(date: String, candidates: List<String>): List<String> {
        if (forecastDetailDate != date || forecastDetailQueue.isEmpty()) {
            forecastDetailDate = date
            forecastDetailQueue = candidates
            forecastDetailCursor = 0
            forecastDetailRetryAttempts.clear()
        }
        return forecastDetailQueue.drop(forecastDetailCursor).take(FORECAST_EVALUATION_TRANCHE_SIZE)
    }

    private suspend fun captureForecastResearchScan(
        date: String,
        scanAtMs: Long,
        marketRegime: String,
        targetPct: Double,
        candidates: List<ForecastCandidate>,
        timingHistory: List<ForecastEntryTimingPolicy.Example>
    ) {
        candidates.forEachIndexed { index, item ->
            val preSpike = ForecastPreSpikeContextPolicy.evaluate(item.evaluation.features.candles)
            val entryAdvice = timingAdvice(marketRegime, scanAtMs, item.evaluation.features.rvol,
                preSpike.compressionRatio, preSpike.risingFloor, timingHistory, date)
            val featureMap = researchFeatureMap(item.evaluation, item.factorModel) + mapOf(
                "forecast_pre_spike_compression_ratio" to preSpike.compressionRatio,
                "forecast_pre_spike_rising_floor" to preSpike.risingFloor,
                "forecast_pre_spike_volume_build" to preSpike.orderlyVolumeBuild,
                "forecast_pre_spike_rank_bonus" to preSpike.rankingBonus,
                "forecast_entry_timing_advice" to entryAdvice.method.name,
                "forecast_entry_timing_validated" to entryAdvice.validated,
                "forecast_entry_timing_context" to entryAdvice.context,
                "forecast_entry_timing_sample_count" to entryAdvice.sampleSize,
                "forecast_entry_timing_holdout_count" to entryAdvice.holdoutSamples,
                "forecast_entry_timing_expected_net_edge_pct" to entryAdvice.estimatedImprovementPct,
                "forecast_entry_timing_advisory_only" to true,
                "adaptive_selection_version" to "V78_SHADOW_VALIDATED",
                "estimated_net_profit_rupees" to item.economics.netProfitRupees,
                "estimated_net_return_pct" to item.economics.netReturnPct,
                "estimated_charges_rupees" to item.economics.estimatedChargesRupees,
                "estimated_slippage_rupees" to item.economics.estimatedSlippageRupees,
                "risk_adjusted_score" to item.economics.riskAdjustedScore,
                "downside_proxy_pct" to item.economics.downsideProxyPct,
                "historical_sample_count" to item.evidenceDiagnostics.sampleCount,
                "historical_distinct_days" to item.evidenceDiagnostics.distinctDays,
                "historical_distinct_symbols" to item.evidenceDiagnostics.distinctSymbols,
                "historical_positive_count" to item.evidenceDiagnostics.positiveCount,
                "historical_flat_count" to item.evidenceDiagnostics.flatCount,
                "historical_adverse_count" to item.evidenceDiagnostics.adverseCount,
                "historical_positive_rate_pct" to item.evidenceDiagnostics.positiveRatePct,
                "historical_target_hit_rate_pct" to item.evidenceDiagnostics.targetHitRatePct,
                "historical_median_mfe_pct" to item.evidenceDiagnostics.medianMfePct,
                "historical_median_mae_pct" to item.evidenceDiagnostics.medianMaePct,
                "historical_median_close_pct" to item.evidenceDiagnostics.medianClosePct,
                "evidence_confidence" to item.evidenceDiagnostics.confidence,
                "out_of_sample_validation_samples" to item.evidenceDiagnostics.validationSamples,
                "out_of_sample_target_hit_rate_pct" to item.evidenceDiagnostics.validationTargetHitRatePct,
                "shadow_negative_penalty" to item.evidenceDiagnostics.shadowPenalty,
                "shadow_negative_penalty_applied_live" to false,
                "shadow_context_target_pct" to ForecastAdaptiveSelectionPolicy.shadowTargetPct(item.targetPct, item.evidenceDiagnostics),
                "shadow_context_target_applied_live" to false,
                "shadow_filter_pass" to ForecastAdaptiveSelectionPolicy.shadowFilterReview(item.evaluation, item.targetPct).passes,
                "shadow_filter_failures" to ForecastAdaptiveSelectionPolicy.shadowFilterReview(item.evaluation, item.targetPct).failedRules,
                "shadow_filter_applied_live" to false
            )
            val votes = item.evaluation.votes.map {
                mapOf("name" to it.name, "score" to it.score, "note" to it.note)
            }
            learningDao.insertForecastResearch(
                ForecastResearchEntity(
                    researchDate = date,
                    scanAtMs = scanAtMs,
                    symbol = item.symbol,
                    rank = index + 1,
                    qualityTier = item.quality.tier,
                    publishEligible = item.quality.publishEligible,
                    executionEligible = item.quality.executionEligible,
                    targetPct = item.targetPct,
                    entryPrice = item.evaluation.features.ltp,
                    regime = item.evaluation.regime,
                    marketRegime = marketRegime,
                    strategy = item.evaluation.strategy,
                    directionalScore = item.evaluation.directionalScore,
                    confidence = item.evaluation.confidence,
                    rejectionReason = item.quality.failedRules.joinToString("; ").ifBlank { item.quality.reason },
                    featureJson = researchGson.toJson(featureMap),
                    voteJson = researchGson.toJson(votes)
                )
            )
        }
        auditLogger.log("FORECAST_RESEARCH", "SCAN_CAPTURED", mapOf(
            "date" to date, "scan_at_ms" to scanAtMs, "count" to candidates.size, "target_pct" to targetPct,
            "market_regime" to marketRegime
        ))
    }

    private fun researchFeatureMap(
        evaluation: StrategyEvaluation,
        factorModel: MultifyFactorModel = MultifyFactorFamilies.evaluate(evaluation, "BREADTH_NEUTRAL")
    ): Map<String, Any?> {
        val f = evaluation.features
        return linkedMapOf(
            "factor_model_version" to "V7_40_FACTOR_3_FAMILY",
            "dominant_family" to factorModel.dominantFamily,
            "family_consensus" to factorModel.consensusScore,
            "positive_families" to factorModel.positiveFamilies,
            "family_scores" to factorModel.families.associate { it.name to it.score },
            "forty_factors" to factorModel.factors.associate { it.name to mapOf("family" to it.family, "score" to it.score, "raw" to it.raw) },
            "ltp" to f.ltp,
            "bid" to f.bid,
            "ask" to f.ask,
            "spread_bps" to f.spreadBps,
            "order_book_imbalance" to f.orderBookImbalance,
            "day_change_pct" to f.dayChangePct,
            "market_cap" to f.marketCap,
            "range52_position" to f.range52Position,
            "vwap" to f.vwap,
            "ema9" to f.ema9,
            "ema20" to f.ema20,
            "ema50" to f.ema50,
            "ema9_slope_atr" to f.ema9Slope,
            "ema20_slope_atr" to f.ema20Slope,
            "vwap_slope_atr" to f.vwapSlope,
            "macd_histogram_slope" to f.macdHistogramSlope,
            "rsi_slope" to f.rsiSlope,
            "volume_acceleration" to f.volumeAcceleration,
            "green_volume_share" to f.greenVolumeShare,
            "structure_score" to f.structureScore,
            "session_high_distance_pct" to f.sessionHighDistancePct,
            "session_low_distance_pct" to f.sessionLowDistancePct,
            "atr14" to f.atr14,
            "atr_pct" to f.atr14?.let { if (f.ltp > 0.0) it / f.ltp * 100.0 else null },
            "rsi14" to f.rsi14,
            "boll_width" to f.bollWidth,
            "macd_histogram" to f.macdHistogram,
            "trend_slope_atr" to f.trendSlopeAtr,
            "rvol" to f.rvol,
            "donchian_high" to f.donchianHigh,
            "donchian_low" to f.donchianLow,
            "orb5_high" to f.orb5High,
            "orb5_low" to f.orb5Low,
            "orb15_high" to f.orb15High,
            "orb15_low" to f.orb15Low,
            "last_open" to f.lastOpen,
            "last_high" to f.lastHigh,
            "last_low" to f.lastLow,
            "last_close" to f.lastClose,
            "prev_open" to f.prevOpen,
            "prev_high" to f.prevHigh,
            "prev_low" to f.prevLow,
            "prev_close" to f.prevClose
        )
    }

    suspend fun captureMultifyResearchSnapshot(eventId: Long, symbol: String) {
        val token = ensureToken() ?: return
        val evaluation = runCatching { forecastEvaluation(symbol, token) }
            .onFailure {
                auditLogger.log("MULTIFY_RESEARCH", "FEATURE_CAPTURE_ERROR", mapOf(
                    "event_id" to eventId, "symbol" to symbol, "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull() ?: return
        auditLogger.log("MULTIFY_RESEARCH", "FEATURE_SNAPSHOT", mapOf(
            "event_id" to eventId,
            "symbol" to symbol,
            "regime" to evaluation.regime,
            "directional_score" to evaluation.directionalScore,
            "confidence" to evaluation.confidence,
            "strategy" to evaluation.strategy,
            "features" to researchFeatureMap(evaluation),
            "votes" to evaluation.votes.map { mapOf("name" to it.name, "score" to it.score, "note" to it.note) }
        ))
    }

    private data class EvidenceFeatureSummary(
        val dominantFamily: String = "UNKNOWN",
        val familyConsensus: Double = 0.0,
        val momentum: Double = 0.0,
        val participation: Double = 0.0,
        val structure: Double = 0.0,
        val marketCap: Double? = null
    )

    private fun evidenceFeatureSummary(featureJson: String): EvidenceFeatureSummary {
        if (featureJson.isBlank() || featureJson == "{}") return EvidenceFeatureSummary()
        return runCatching {
            val root = JsonParser.parseString(featureJson).asJsonObject
            val families = root.getAsJsonObject("family_scores")
            EvidenceFeatureSummary(
                dominantFamily = root.get("dominant_family")?.takeUnless { it.isJsonNull }?.asString ?: "UNKNOWN",
                familyConsensus = root.get("family_consensus")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
                momentum = families?.get("MOMENTUM_TREND")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
                participation = families?.get("PARTICIPATION_LIQUIDITY")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
                structure = families?.get("STRUCTURE_CONTEXT")?.takeUnless { it.isJsonNull }?.asDouble ?: 0.0,
                marketCap = root.get("market_cap")?.takeUnless { it.isJsonNull }?.asDouble
            )
        }.getOrDefault(EvidenceFeatureSummary())
    }

    private fun nearestResearchSnapshot(
        rows: List<ForecastResearchEntity>,
        date: String,
        symbol: String,
        atMs: Long
    ): ForecastResearchEntity? = rows.asSequence()
        .filter { it.researchDate == date && it.symbol.equals(symbol, true) }
        .filter { kotlin.math.abs(it.scanAtMs - atMs) <= UNIVERSAL_EVIDENCE_NEAREST_SNAPSHOT_MS }
        .minByOrNull { kotlin.math.abs(it.scanAtMs - atMs) }

    private data class ReplayOutcome(
        val mfePct: Double,
        val maePct: Double,
        val closePct: Double,
        val targetHit: Boolean
    )

    private suspend fun replayOutcomeToSessionClose(
        symbol: String,
        entryPrice: Double,
        atMs: Long,
        targetPct: Double?,
        token: String
    ): ReplayOutcome? {
        if (entryPrice <= 0.0 || atMs <= 0L) return null
        val at = Instant.ofEpochMilli(atMs).atZone(INDIA)
        val start = at.toLocalDateTime().format(HIST_FORMAT)
        val end = at.toLocalDate().atTime(15, 30).format(HIST_FORMAT)
        val historical = runCatching {
            apiFactory.groww.historicalCandles(
                bearer(token),
                growwSymbol = "NSE-$symbol",
                startTime = start,
                endTime = end,
                candleInterval = "5minute"
            ).requirePayload("After-hours evidence outcome $symbol")
        }.getOrNull() ?: return null
        val rows = historical.candles.mapNotNull { x ->
            if (x.size < 5) return@mapNotNull null
            val high = runCatching { x[2].asDouble }.getOrNull() ?: return@mapNotNull null
            val low = runCatching { x[3].asDouble }.getOrNull() ?: return@mapNotNull null
            val close = runCatching { x[4].asDouble }.getOrNull() ?: return@mapNotNull null
            Triple(high, low, close)
        }
        if (rows.isEmpty()) return null
        val high = rows.maxOf { it.first }
        val low = rows.minOf { it.second }
        val close = rows.last().third
        val mfe = max(0.0, (high / entryPrice - 1.0) * 100.0)
        val mae = max(0.0, (entryPrice - low) / entryPrice * 100.0)
        val closePct = (close / entryPrice - 1.0) * 100.0
        return ReplayOutcome(
            mfePct = mfe,
            maePct = mae,
            closePct = closePct,
            targetHit = targetPct?.let { mfe + 1e-9 >= it } == true
        )
    }

    private suspend fun replayMultifyEvaluation(
        call: LearningCallEntity,
        token: String,
        nearbyMarketRegime: String
    ): Pair<StrategyEvaluation, String>? {
        if (call.buyAtMs <= 0L || call.entryPrice <= 0.0) return null
        val at = Instant.ofEpochMilli(call.buyAtMs).atZone(INDIA)
        val start = at.minusDays(4).toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = at.toLocalDateTime().format(HIST_FORMAT)
        val historical = runCatching {
            apiFactory.groww.historicalCandles(
                bearer(token),
                growwSymbol = "NSE-${call.symbol}",
                startTime = start,
                endTime = end,
                candleInterval = "5minute"
            ).requirePayload("Multify after-hours replay ${call.symbol}")
        }.getOrNull() ?: return null

        val eligible = historical.candles.filter { candle ->
            val ts = candle.firstOrNull()?.let(::researchCandleTimestampMs) ?: return@filter true
            ts <= call.buyAtMs
        }
        if (eligible.size < 20) return null

        data class ReplayBar(val atMs: Long, val close: Double)
        val bars = eligible.mapNotNull { candle ->
            if (candle.size < 5) return@mapNotNull null
            val ts = researchCandleTimestampMs(candle[0]) ?: return@mapNotNull null
            val close = runCatching { candle[4].asDouble }.getOrNull() ?: return@mapNotNull null
            ReplayBar(ts, close)
        }.sortedBy { it.atMs }
        val lastClose = bars.lastOrNull()?.close ?: call.entryPrice
        val callDate = at.toLocalDate()
        val previousSessionClose = bars
            .filter { Instant.ofEpochMilli(it.atMs).atZone(INDIA).toLocalDate() < callDate }
            .maxByOrNull { it.atMs }
            ?.close
        val dayChangePct = previousSessionClose?.takeIf { it > 0.0 }?.let { (lastClose / it - 1.0) * 100.0 }

        val currentQuote = runCatching {
            apiFactory.groww.quote(bearer(token), tradingSymbol = call.symbol)
                .requirePayload("Multify replay context ${call.symbol}")
        }.getOrNull()
        val syntheticQuote = QuotePayload(
            lastPrice = lastClose,
            dayChangePct = dayChangePct,
            marketCap = currentQuote?.marketCap,
            week52High = currentQuote?.week52High,
            week52Low = currentQuote?.week52Low
        )
        val replayHistorical = HistoricalPayload(
            candles = eligible,
            startTime = historical.startTime,
            endTime = historical.endTime,
            intervalInMinutes = 5
        )
        val features = runCatching { LocalStrategyEngine.buildFeatures(syntheticQuote, replayHistorical, 5) }.getOrNull()
            ?: return null
        val signal = ParsedSignal(
            type = SignalType.TRADE_RELEASE,
            symbol = call.symbol,
            target = call.targetPrice,
            rawText = "AFTER_HOURS_MULTIFY_REPLAY",
            confidence = 1.0
        )
        val evaluation = LocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
        return evaluation to nearbyMarketRegime
    }

    private suspend fun refreshUniversalPositiveEvidence(token: String): Int {
        val researchRows = learningDao.allForecastResearch()
        val existingKeys = learningDao.allForecastPositiveEvidence().map { it.evidenceKey }.toMutableSet()
        var inserted = 0
        var forecastInserted = 0
        var fixedInserted = 0
        var multifyInserted = 0

        suspend fun upsert(
            key: String,
            source: String,
            date: String,
            atMs: Long,
            symbol: String,
            strategy: String,
            regime: String,
            marketRegime: String,
            sectorRegime: String,
            featureJson: String,
            voteJson: String,
            entryPrice: Double,
            targetPct: Double?,
            mfePct: Double,
            maePct: Double,
            closePct: Double?,
            realizedPct: Double?,
            targetHit: Boolean,
            qualityTier: String,
            reconstructionQuality: String
        ) {
            if (key in existingKeys) return
            if (!ForecastUniversalPositiveEvidencePolicy.qualifies(realizedPct, closePct, mfePct, targetHit)) return
            val summary = evidenceFeatureSummary(featureJson)
            val protected = ForecastUniversalPositiveEvidencePolicy.protectedTrailPct(mfePct)
            learningDao.upsertForecastPositiveEvidence(
                ForecastPositiveEvidenceEntity(
                    evidenceKey = key,
                    source = source,
                    evidenceDate = date,
                    eventAtMs = atMs,
                    symbol = symbol.uppercase(Locale.US),
                    strategy = strategy.ifBlank { "UNKNOWN" },
                    regime = regime.ifBlank { "UNKNOWN" },
                    marketRegime = marketRegime.ifBlank { "UNKNOWN" },
                    sectorRegime = sectorRegime.ifBlank { "UNAVAILABLE" },
                    capBucket = ForecastUniversalPositiveEvidencePolicy.capBucket(summary.marketCap),
                    timeBucket = ForecastUniversalPositiveEvidencePolicy.timeBucket(atMs),
                    dominantFamily = summary.dominantFamily,
                    familyConsensus = summary.familyConsensus,
                    momentumFamilyScore = summary.momentum,
                    participationFamilyScore = summary.participation,
                    structureFamilyScore = summary.structure,
                    factorJson = featureJson.ifBlank { "{}" },
                    voteJson = voteJson.ifBlank { "[]" },
                    entryPrice = entryPrice,
                    targetPct = targetPct,
                    maxFavourablePct = mfePct,
                    maxAdversePct = maePct,
                    closePct = closePct,
                    realizedPct = realizedPct,
                    protectedTrailPct = protected,
                    trailArmed = protected != null,
                    targetHit = targetHit,
                    positiveReason = ForecastUniversalPositiveEvidencePolicy.positiveReason(
                        realizedPct, closePct, mfePct, targetHit
                    ),
                    qualityTier = qualityTier,
                    reconstructionQuality = reconstructionQuality,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            existingKeys += key
            inserted++
            when (source) {
                "FORECAST" -> forecastInserted++
                "FIXED" -> fixedInserted++
                "MULTIFY" -> multifyInserted++
            }
        }

        researchRows.asSequence()
            .filter { it.outcomeUpdatedAtMs != null }
            .forEach { row ->
                upsert(
                    key = "FORECAST|${row.researchDate}|${row.scanAtMs}|${row.symbol.uppercase(Locale.US)}",
                    source = "FORECAST",
                    date = row.researchDate,
                    atMs = row.scanAtMs,
                    symbol = row.symbol,
                    strategy = row.strategy,
                    regime = row.regime,
                    marketRegime = row.marketRegime,
                    sectorRegime = "UNAVAILABLE",
                    featureJson = row.featureJson,
                    voteJson = row.voteJson,
                    entryPrice = row.entryPrice,
                    targetPct = row.targetPct,
                    mfePct = row.outcomeMfePct ?: 0.0,
                    maePct = row.outcomeMaePct ?: 0.0,
                    closePct = row.outcomeClosePct,
                    realizedPct = null,
                    targetHit = row.outcomeTargetHit == true,
                    qualityTier = row.qualityTier,
                    reconstructionQuality = "LIVE_FORECAST_SNAPSHOT"
                )
            }

        var fixedOutcomeReplays = 0
        for (row in adaptiveFixedEvidenceRecords()) {
            val key = "FIXED|${row.recommendationDate}|${row.generatedAtMs}|${row.symbol.uppercase(Locale.US)}"
            if (key in existingKeys) continue

            var mfePct = row.maxFavourablePct
            var maePct = row.maxAdversePct
            var closePct: Double? = if (row.entryPrice > 0.0 && row.lastPrice.isFinite())
                (row.lastPrice / row.entryPrice - 1.0) * 100.0 else null
            var targetHit = row.status == "TARGET_HIT"
            var outcomeQuality = "FIXED_STORED_OUTCOME"

            if (row.generatedAtMs > 0L && fixedOutcomeReplays < UNIVERSAL_EVIDENCE_FIXED_REPLAY_BATCH) {
                val replay = replayOutcomeToSessionClose(
                    symbol = row.symbol,
                    entryPrice = row.entryPrice,
                    atMs = row.generatedAtMs,
                    targetPct = row.targetPct,
                    token = token
                )
                if (replay != null) {
                    mfePct = replay.mfePct
                    maePct = replay.maePct
                    closePct = replay.closePct
                    targetHit = replay.targetHit
                    outcomeQuality = "AFTER_HOURS_FIXED_OUTCOME_REPLAY"
                    fixedOutcomeReplays++
                    delay(UNIVERSAL_EVIDENCE_REPLAY_SYMBOL_PACING_MS)
                }
            }

            if (!ForecastUniversalPositiveEvidencePolicy.qualifies(
                    realizedPct = null,
                    closePct = closePct,
                    maxFavourablePct = mfePct,
                    targetHit = targetHit
                )
            ) continue

            val near = nearestResearchSnapshot(researchRows, row.recommendationDate, row.symbol, row.generatedAtMs)
            upsert(
                key = key,
                source = "FIXED",
                date = row.recommendationDate,
                atMs = row.generatedAtMs,
                symbol = row.symbol,
                strategy = row.strategy,
                regime = row.regime,
                marketRegime = row.marketRegime,
                sectorRegime = "UNAVAILABLE",
                featureJson = near?.featureJson ?: "{}",
                voteJson = near?.voteJson ?: "[]",
                entryPrice = row.entryPrice,
                targetPct = row.targetPct,
                mfePct = mfePct,
                maePct = maePct,
                closePct = closePct,
                realizedPct = null,
                targetHit = targetHit,
                qualityTier = row.qualityTier,
                reconstructionQuality = if (near != null) "NEARBY_FORECAST_SNAPSHOT+$outcomeQuality" else outcomeQuality
            )
        }

        val allCalls = learningDao.allCalls()
        val rollingCallDates = allCalls
            .filter { it.action.equals("BUY", true) }
            .map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        allCalls.asSequence()
            .filter {
                it.action.equals("BUY", true) && it.callDate in rollingCallDates &&
                    it.buyAtMs > 0L && it.entryPrice > 0.0
            }
            .forEach { call ->
                val mfe = call.multifyMfePct ?: 0.0
                val realized = call.longRealizedPct
                if (!ForecastUniversalPositiveEvidencePolicy.qualifies(realized, realized, mfe, false)) return@forEach
                val key = "MULTIFY|${call.callDate}|${call.buyAtMs}|${call.symbol.uppercase(Locale.US)}"
                if (key in existingKeys) return@forEach
                val near = nearestResearchSnapshot(researchRows, call.callDate, call.symbol, call.buyAtMs)
                if (near != null) {
                    upsert(
                        key = key,
                        source = "MULTIFY",
                        date = call.callDate,
                        atMs = call.buyAtMs,
                        symbol = call.symbol,
                        strategy = near.strategy,
                        regime = near.regime,
                        marketRegime = near.marketRegime,
                        sectorRegime = "UNAVAILABLE",
                        featureJson = near.featureJson,
                        voteJson = near.voteJson,
                        entryPrice = call.entryPrice,
                        targetPct = call.targetPct,
                        mfePct = mfe,
                        maePct = near.outcomeMaePct ?: 0.0,
                        closePct = realized,
                        realizedPct = realized,
                        targetHit = call.targetPct?.let { mfe + 1e-9 >= it } == true,
                        qualityTier = near.qualityTier,
                        reconstructionQuality = "NEARBY_FORECAST_SNAPSHOT"
                    )
                } else {
                    val replay = replayMultifyEvaluation(call, token, "BREADTH_UNKNOWN") ?: return@forEach
                    val evaluation = replay.first
                    val marketRegime = replay.second
                    val factorModel = MultifyFactorFamilies.evaluate(evaluation, marketRegime)
                    val featureJson = researchGson.toJson(
                        researchFeatureMap(evaluation, factorModel) + mapOf(
                            "replay_reconstruction" to true,
                            "live_only_order_book_fields_available" to false,
                            "source" to "MULTIFY"
                        )
                    )
                    val voteJson = researchGson.toJson(
                        evaluation.votes.map { mapOf("name" to it.name, "score" to it.score, "note" to it.note) }
                    )
                    upsert(
                        key = key,
                        source = "MULTIFY",
                        date = call.callDate,
                        atMs = call.buyAtMs,
                        symbol = call.symbol,
                        strategy = evaluation.strategy,
                        regime = evaluation.regime,
                        marketRegime = marketRegime,
                        sectorRegime = "UNAVAILABLE",
                        featureJson = featureJson,
                        voteJson = voteJson,
                        entryPrice = call.entryPrice,
                        targetPct = call.targetPct,
                        mfePct = mfe,
                        maePct = 0.0,
                        closePct = realized,
                        realizedPct = realized,
                        targetHit = call.targetPct?.let { mfe + 1e-9 >= it } == true,
                        qualityTier = "MULTIFY_POSITIVE",
                        reconstructionQuality = "AFTER_HOURS_HISTORICAL_REPLAY"
                    )
                    delay(UNIVERSAL_EVIDENCE_REPLAY_SYMBOL_PACING_MS)
                }
            }

        val completedReplay = researchRows.filter { it.outcomeUpdatedAtMs != null }
        val replayPositive = completedReplay.count {
            ForecastUniversalPositiveEvidencePolicy.qualifies(
                null, it.outcomeClosePct, it.outcomeMfePct ?: 0.0, it.outcomeTargetHit == true
            )
        }
        auditLogger.logAsync("FORECAST_REPLAY", "UNIVERSAL_POSITIVE_EVIDENCE_REFRESH", mapOf(
            "inserted_total" to inserted,
            "forecast_inserted" to forecastInserted,
            "fixed_inserted" to fixedInserted,
            "multify_inserted" to multifyInserted,
            "completed_forecast_replay_rows" to completedReplay.size,
            "positive_forecast_replay_rows" to replayPositive,
            "diagnostic_only_forecast_rows" to (completedReplay.size - replayPositive).coerceAtLeast(0),
            "negative_examples_change_weights" to false,
            "fixed_formula_changed" to false,
            "trail_policy_changed" to false
        ))
        return inserted
    }

    /**
     * Read-only reuse of Fixed's complete NSE LTP sweep. It uses no part of Fixed's
     * scoring or recommendations and never writes into Fixed's frozen persistence.
     * Snapshot A and B must be from distinct completed Fixed sweeps; we do not invent
     * a short-term price movement by comparing the same snapshot against itself.
     */
    private fun sharedFixedDiscoveryPass(master: List<String>, today: String): MarketDiscoveryPass? {
        val fixed = fixedMarketDiscoveryStateStore.load(today) ?: return null
        val now = System.currentTimeMillis()
        if (!ForecastSharedDiscoveryPolicy.fresh(now, fixed.updatedAtMs)) return null
        val symbols = master.asSequence().map { it.uppercase(Locale.US) }.toSet()
        val current = fixed.previousLtps.filterKeys { it.uppercase(Locale.US) in symbols }
        val coverage = current.size.toDouble() / symbols.size.coerceAtLeast(1)
        if (coverage + 1e-9 < MIN_DISCOVERY_COVERAGE_RATIO) return null
        val prefs = appContext.getSharedPreferences("forecast_shared_fixed_snapshots_v792", Context.MODE_PRIVATE)
        val sameDay = prefs.getString("date", "") == today
        var previous = if (sameDay) MarketDiscoveryStateCodec.decode(prefs.getString("current", "").orEmpty())
            else emptyMap()
        var priorSourceAtMs = if (sameDay) prefs.getLong("source_ms", 0L) else 0L
        if (fixed.updatedAtMs > priorSourceAtMs) {
            // Do not promote a partial/stale read. Two distinct completed Fixed sweeps
            // are sufficient for an independent Forecast pulse baseline.
            val originalPrevious = if (sameDay) MarketDiscoveryStateCodec.decode(prefs.getString("previous", "").orEmpty())
                else emptyMap()
            if (priorSourceAtMs <= 0L) previous = emptyMap()
            prefs.edit()
                .putString("date", today)
                .putString("current", MarketDiscoveryStateCodec.encode(current))
                .putString("previous", MarketDiscoveryStateCodec.encode(previous))
                .putString("session_open", MarketDiscoveryStateCodec.encode(fixed.sessionOpenLtps))
                .putLong("source_ms", fixed.updatedAtMs)
                .putLong("previous_source_ms", priorSourceAtMs)
                .commit()
            // Suppress the unused-variable lint without relying on a stale previous.
            if (originalPrevious.isNotEmpty() && previous.isEmpty()) {
                auditLogger.log("FORECAST_DISCOVERY", "PREVIOUS_SOURCE_NOT_REUSED", mapOf("count" to originalPrevious.size))
            }
            auditLogger.log("FORECAST_DISCOVERY", "SHARED_FIXED_FULL_NSE_UPDATED", mapOf(
                "master_count" to symbols.size,
                "ltp_coverage" to current.size,
                "coverage_pct" to (coverage * 100.0),
                "source_age_ms" to (now - fixed.updatedAtMs),
                "previous_snapshot_count" to previous.size,
                "shared_requests" to 0,
                "fixed_formula_changed" to false
            ))
        } else {
            // Source unchanged: use its genuinely older sibling, never self-compare.
            previous = MarketDiscoveryStateCodec.decode(prefs.getString("previous", "").orEmpty())
            priorSourceAtMs = prefs.getLong("previous_source_ms", 0L)
        }
        val previousCoverage = previous.keys.count { it.uppercase(Locale.US) in symbols }.toDouble() / symbols.size.coerceAtLeast(1)
        val ready = ForecastSharedDiscoveryPolicy.ready(coverage, previousCoverage, fixed.updatedAtMs, priorSourceAtMs)
        forecastWaitingForSharedBaseline = !ready
        val sessionOpen = fixed.sessionOpenLtps.filterKeys { it.uppercase(Locale.US) in symbols }
        val ranked = if (ready) com.multify.traderpro.engine.ForecastEarlyDiscoveryPolicy.rank(
            current = current, previous = previous, sessionOpen = sessionOpen, limit = DISCOVERY_DYNAMIC_LIMIT
        ) else emptyList()
        if (!ready) auditLogger.log("FORECAST_DISCOVERY", "WAITING_FOR_SECOND_FIXED_FULL_NSE_SNAPSHOT", mapOf(
            "coverage_pct" to coverage * 100.0,
            "previous_coverage_pct" to previousCoverage * 100.0,
            "source_at_ms" to fixed.updatedAtMs,
            "previous_source_at_ms" to priorSourceAtMs,
            "next_probe_earliest_seconds" to 60
        ))
        marketDiscoveryStateStore.recordHealth(today, coverage, 0, false, ready)
        auditLogger.log("FORECAST_DISCOVERY", "MARKET_WIDE_SCAN", mapOf(
            "source" to "FIXED_RAW_FULL_NSE_LTPS",
            "master_count" to symbols.size,
            "ltp_coverage" to current.size,
            "coverage_pct" to coverage * 100.0,
            "baseline_ready" to ready,
            "full_sweep_completed" to true,
            "selected" to ranked.size,
            "broker_ltp_requests_avoided" to (symbols.size + 49) / 50,
            "fixed_formula_changed" to false
        ))
        return MarketDiscoveryPass(ranked.map { it.symbol }, ready, coverage, true, 0)
    }

    /**
     * Small coroutine bridge: isolate Android DEX verification of the suspend
     * network fetch loop from read-only shared Fixed snapshot access and ranking.
     * This prevents the wide register/state-machine merge that Android rejected
     * in v7.9.2's discoverMarketWideCandidates on real devices.
     */
    private suspend fun discoverMarketWideCandidates(
        token: String,
        master: List<String>,
        today: String
    ): MarketDiscoveryPass {
        val distinctMaster = master.distinct()
        val shared = sharedFixedDiscoveryPass(distinctMaster, today)
        if (shared != null) return shared
        forecastWaitingForSharedBaseline = false
        return discoverForecastDirectCandidates(token, distinctMaster, today)
    }

    /** Direct full-NSE fallback when fresh complete Fixed snapshots are unavailable. */
    private suspend fun discoverForecastDirectCandidates(
        token: String,
        distinctMaster: List<String>,
        today: String
    ): MarketDiscoveryPass {
        val persisted = marketDiscoveryStateStore.load(today)
        if (forecastDiscoveryDate != today || forecastDiscoveryMustClear) {
            forecastDiscoveryMustClear = false
            forecastDiscoveryDate = today
            forecastDiscoveryNextBatch = 0
            forecastDiscoveryPartialLtps.clear()
            forecastDiscoveryBatchErrors = 0
        }
        val chunks = distinctMaster.chunked(50)
        val errors = fetchForecastDiscoveryBatchSeries(token, chunks)
        // Ranking/persistence/telemetry deliberately outside this suspend method.
        val result = finishForecastDirectDiscovery(distinctMaster, today, persisted, chunks.size, errors)
        preferences.recordMarketData()
        return result
    }

    /** The only suspend loop; a failed batch is never consumed and is retried. */
    private suspend fun fetchForecastDiscoveryBatchSeries(
        token: String,
        chunks: List<List<String>>
    ): Int {
        var batchErrors = forecastDiscoveryBatchErrors
        for (index in forecastDiscoveryNextBatch until chunks.size) {
            if (!forecastMarketCapacityAvailable()) {
                auditLogger.log("FORECAST_DISCOVERY", "YIELD_TO_FIXED_PRIORITY", mapOf("batch_index" to index,
                    "fixed_priority" to fixedApiPriorityRequested,
                    "fixed_deferred_for_broker" to fixedScanDeferredBroker,
                    "beta_configured" to secretStore.hasBetaCredentials()))
                break
            }
            val payload = fetchDiscoveryLtpBatch(token, chunks[index], index)
            if (payload == null) {
                batchErrors++
                forecastDiscoveryBatchErrors = batchErrors
                auditLogger.log("FORECAST_DISCOVERY", "BATCH_RETRY_PENDING", mapOf(
                    "batch_index" to index,
                    "coverage_so_far" to forecastDiscoveryPartialLtps.size,
                    "attempted_errors_total" to batchErrors,
                    "resume_batch" to forecastDiscoveryNextBatch,
                    "next_probe_earliest_seconds" to 60
                ))
                break
            }
            for ((key, value) in payload) {
                if (value.isFinite() && value >= 10.0) {
                    forecastDiscoveryPartialLtps[key.removePrefix("NSE_").uppercase(Locale.US)] = value
                }
            }
            forecastDiscoveryNextBatch = index + 1
            forecastDiscoveryBatchErrors = batchErrors
            if (index + 1 < chunks.size) delay(DISCOVERY_BATCH_PACING_MS)
        }
        return batchErrors
    }

    /** Entirely synchronous post-fetch processing: no coroutine spilled registers. */
    private fun finishForecastDirectDiscovery(
        distinctMaster: List<String>,
        today: String,
        persisted: PersistedMarketDiscoveryState?,
        batchCount: Int,
        batchErrors: Int
    ): MarketDiscoveryPass {
        val previous = persisted?.previousLtps.orEmpty()
        val sessionOpen = persisted?.sessionOpenLtps.orEmpty()
        val restored = persisted != null
        val current = forecastDiscoveryPartialLtps
        val fullSweepCompleted = forecastDiscoveryNextBatch == batchCount
        val denominator = distinctMaster.size.coerceAtLeast(1)
        val coverageRatio = current.size.toDouble() / denominator.toDouble()
        val previousCoverageRatio = previous.size.toDouble() / denominator.toDouble()
        val baselineReady = fullSweepCompleted &&
            previousCoverageRatio >= MIN_DISCOVERY_COVERAGE_RATIO &&
            coverageRatio >= MIN_DISCOVERY_COVERAGE_RATIO

        val ranked = if (baselineReady) {
            com.multify.traderpro.engine.ForecastEarlyDiscoveryPolicy.rank(
                current = current, previous = previous, sessionOpen = sessionOpen,
                limit = DISCOVERY_DYNAMIC_LIMIT
            )
        } else emptyList()

        if (fullSweepCompleted && coverageRatio >= MIN_DISCOVERY_COVERAGE_RATIO) {
            val mergedSessionOpen = sessionOpen.toMutableMap()
            current.forEach { (symbol, ltp) -> mergedSessionOpen.putIfAbsent(symbol, ltp) }
            marketDiscoveryStateStore.save(today, mergedSessionOpen, current)
        } else {
            auditLogger.log("FORECAST_DISCOVERY", "BASELINE_NOT_OVERWRITTEN", mapOf(
                "coverage_pct" to coverageRatio * 100.0,
                "required_coverage_pct" to MIN_DISCOVERY_COVERAGE_RATIO * 100.0,
                "aborted_for_network" to false,
                "persisted_previous_count" to previous.size
            ))
        }
        if (fullSweepCompleted) {
            forecastDiscoveryNextBatch = 0
            forecastDiscoveryBatchErrors = 0
            forecastDiscoveryMustClear = true
        }
        marketDiscoveryStateStore.recordHealth(
            date = today,
            coverageRatio = coverageRatio,
            batchErrors = batchErrors,
            abortedForNetwork = false,
            baselineReady = baselineReady
        )
        auditLogger.log("FORECAST_DISCOVERY", "MARKET_WIDE_SCAN", mapOf(
            "master_count" to distinctMaster.size,
            "full_sweep_completed" to fullSweepCompleted,
            "resume_batch" to forecastDiscoveryNextBatch,
            "ltp_coverage" to current.size,
            "coverage_pct" to coverageRatio * 100.0,
            "batch_count" to batchCount,
            "batch_errors" to batchErrors,
            "aborted_for_network" to false,
            "baseline_ready" to baselineReady,
            "state_restored" to restored,
            "persisted_baseline_count" to previous.size,
            "selected" to ranked.size,
            "top_movers" to ranked.take(10).map {
                mapOf(
                    "symbol" to it.symbol,
                    "ltp" to it.ltp,
                    "pulse_pct" to it.pulsePct,
                    "session_pct" to it.sessionPct,
                    "discovery_score" to it.score
                )
            }
        ))
        return MarketDiscoveryPass(
            ranked.map { it.symbol }, baselineReady, coverageRatio, restored, batchErrors
        )
    }

    private suspend fun fetchDiscoveryLtpBatch(
        token: String,
        chunk: List<String>,
        batchIndex: Int
    ): Map<String, Double>? {
        var lastFailure: Throwable? = null
        for (attempt in 0 until DISCOVERY_BATCH_ATTEMPTS) {
            try {
                val exchangeSymbols = chunk.joinToString(",") { "NSE_$it" }
                return withForecastLiveDataSlot {
                    betaMarketRead(token, BetaResearchGate.Type.LIVE,
                        alphaCall = { apiFactory.groww.ltp(bearer(token), exchangeSymbols = exchangeSymbols)
                            .requirePayload("Market-wide discovery LTP") },
                        betaCall = { apiFactory.betaGroww.ltp(bearer(token), exchangeSymbols = exchangeSymbols)
                            .requirePayload("Beta market-wide discovery LTP") })
                }
            } catch (t: Throwable) {
                lastFailure = t
                if (!isTransientMarketDataFailure(t) || attempt + 1 >= DISCOVERY_BATCH_ATTEMPTS) break
                delay(if (t is HttpException && t.code() == 429) 1_250L else DISCOVERY_RETRY_BACKOFF_MS)
            }
        }
        auditLogger.log("FORECAST_DISCOVERY", "LTP_BATCH_ERROR", mapOf(
            "batch_index" to batchIndex,
            "batch_size" to chunk.size,
            "message" to (lastFailure?.message ?: lastFailure?.javaClass?.simpleName ?: "unknown")
        ))
        return null
    }

    private fun isTransientMarketDataFailure(t: Throwable): Boolean =
        t is IOException || (t is HttpException && (t.code() == 429 || t.code() >= 500))

    private suspend fun <T> withBulkMarketDataSlot(block: suspend () -> T): T =
        bulkMarketDataMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val waitMs = (nextBulkMarketDataAtElapsedMs - now).coerceAtLeast(0L)
            if (waitMs > 0L) delay(waitMs)
            try {
                block().also {
                    nextBulkMarketDataAtElapsedMs =
                        SystemClock.elapsedRealtime() + BULK_MARKET_DATA_MIN_GAP_MS
                }
            } catch (t: Throwable) {
                nextBulkMarketDataAtElapsedMs = SystemClock.elapsedRealtime() +
                    if (t is HttpException && t.code() == 429) BULK_MARKET_DATA_429_COOLDOWN_MS
                    else BULK_MARKET_DATA_MIN_GAP_MS
                throw t
            }
        }

    private suspend fun awaitFixedLiveDataSlot() {
        while (true) {
            var waitMs = 0L
            val granted = fixedLiveDataRateMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                fixedLiveDataSecondWindowMs.removeAll { now - it >= 1_000L }
                fixedLiveDataMinuteWindowMs.removeAll { now - it >= 60_000L }

                val hasOpenHoldings = activePortfolioMonitorCount > 0
                val perSecond = GrowwApiBudgetPolicy.fixedLiveDataPerSecond(hasOpenHoldings)
                val perMinute = GrowwApiBudgetPolicy.fixedLiveDataPerMinute(hasOpenHoldings)

                val cooldownWait = (fixedLiveDataCooldownUntilElapsedMs - now).coerceAtLeast(0L)
                val secondWait = if (fixedLiveDataSecondWindowMs.size >= perSecond) {
                    (fixedLiveDataSecondWindowMs.first() + 1_000L - now).coerceAtLeast(1L)
                } else 0L
                val minuteWait = if (fixedLiveDataMinuteWindowMs.size >= perMinute) {
                    (fixedLiveDataMinuteWindowMs.first() + 60_000L - now).coerceAtLeast(1L)
                } else 0L

                waitMs = maxOf(cooldownWait, secondWait, minuteWait)
                if (waitMs <= 0L) {
                    fixedLiveDataSecondWindowMs += now
                    fixedLiveDataMinuteWindowMs += now
                    true
                } else false
            }
            if (granted) return
            delay(waitMs.coerceAtLeast(10L))
        }
    }

    private suspend fun <T> withFixedLiveDataSlot(block: suspend () -> T): T {
        awaitFixedLiveDataSlot()
        return try {
            block()
        } catch (t: Throwable) {
            if (t is HttpException && t.code() == 429) {
                fixedLiveDataRateMutex.withLock {
                    fixedLiveDataCooldownUntilElapsedMs = max(
                        fixedLiveDataCooldownUntilElapsedMs,
                        SystemClock.elapsedRealtime() + GrowwApiBudgetPolicy.RATE_LIMIT_COOLDOWN_MS
                    )
                }
            }
            throw t
        }
    }

    // v7.8.5: reserve the local historical-data slot under the mutex, then release it
    // BEFORE the HTTP request. The global Groww interceptor still enforces the stricter
    // 1 request/second and 45 requests/minute; Fixed candidates and candles are unchanged.
    // This avoids serializing independent network responses behind a local mutex.
    private suspend fun <T> withFixedHistoricalDataSlot(block: suspend () -> T): T {
        while (true) {
            var reserved = false
            var retryInMs = 0L
            fixedHistoricalDataMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                retryInMs = FixedHistoricalSlotPolicy.waitMs(
                    now, nextFixedHistoricalAtElapsedMs, fixedHistoricalCooldownUntilElapsedMs
                )
                if (retryInMs <= 0L) {
                    // No I/O or suspension while holding this lock.
                    nextFixedHistoricalAtElapsedMs = FixedHistoricalSlotPolicy.reservedNextSlotMs(
                        now, GrowwApiBudgetPolicy.FIXED_HISTORICAL_MIN_GAP_MS
                    )
                    reserved = true
                }
            }
            if (reserved) break
            delay(retryInMs.coerceAtLeast(5L))
        }
        return try {
            block()
        } catch (t: Throwable) {
            if (t is HttpException && t.code() == 429) {
                fixedHistoricalDataMutex.withLock {
                    fixedHistoricalCooldownUntilElapsedMs = maxOf(
                        fixedHistoricalCooldownUntilElapsedMs,
                        SystemClock.elapsedRealtime() + GrowwApiBudgetPolicy.RATE_LIMIT_COOLDOWN_MS
                    )
                }
            }
            throw t
        }
    }

    /** Transient scheduler yield, not a candidate failure; never use up its retry budget. */
    private class ForecastPriorityYieldException : IllegalStateException("Forecast yielded to Fixed priority")

    private suspend fun <T> withForecastLiveDataSlot(block: suspend () -> T): T {
        // A quote and its 1m history must fit before the next scheduled Fixed scan.
        // Never reserve the shared scanner or broker capacity across Fixed's guard window.
        if (!forecastMarketCapacityAvailable()) {
            throw ForecastPriorityYieldException()
        }
        return if (secretStore.hasBetaCredentials()) block() else withFixedLiveDataSlot(block)
    }

    private suspend fun <T> withForecastHistoricalDataSlot(block: suspend () -> T): T {
        // The quote already passed the full budget admission check. Reapplying all
        // Forecast utilization thresholds after that quote starved the matching 1m
        // candle request. Only check Fixed, SELL priority and the approaching guard;
        // actual network requests still pass the unmodified Groww rate governor.
        if (fixedApiPriorityRequested || marketCriticalTradeInFlight ||
            !ForecastBetaPriorityPolicy.canResearch(
                fixedPriorityRequested = fixedApiPriorityRequested,
                criticalTradeInFlight = marketCriticalTradeInFlight,
                fixedDeferredForBroker = secretStore.hasBetaCredentials() && fixedScanDeferredBroker,
                millisUntilNextFixed = millisUntilNextFixedScan(),
                fixedGuardMs = forecastFixedGuardMs()
            )) {
            throw ForecastPriorityYieldException()
        }
        return if (secretStore.hasBetaCredentials()) block() else withFixedHistoricalDataSlot(block)
    }

    private suspend fun forecastEvaluation(symbol: String, token: String): StrategyEvaluation {
        val now = ZonedDateTime.now(INDIA)
        val start = now.toLocalDate().atTime(9,15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val quote = withForecastLiveDataSlot {
            betaMarketRead(token, BetaResearchGate.Type.LIVE,
                alphaCall = { apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol") },
                betaCall = { apiFactory.betaGroww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Beta quote $symbol") })
        }
        val bestBid = quote.bidPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: quote.depth?.buy?.mapNotNull { level -> level.price?.takeIf { it.isFinite() && it > 0.0 } }?.maxOrNull()
        val bestAsk = quote.offerPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: quote.depth?.sell?.mapNotNull { level -> level.price?.takeIf { it.isFinite() && it > 0.0 } }?.minOrNull()
        if (bestBid == null || bestAsk == null || bestBid <= 0.0 || bestAsk < bestBid) {
            auditLogger.logAsync("FORECAST_RESEARCH", "QUOTE_SPREAD_UNAVAILABLE", mapOf(
                "symbol" to symbol, "account" to (if (forecastUsesBeta(token)) "BETA" else "ALPHA"),
                "bid_present" to (bestBid != null), "offer_present" to (bestAsk != null),
                "depth_buy_levels" to quote.depth?.buy?.size,
                "depth_sell_levels" to quote.depth?.sell?.size
            ))
        }
        val historical = withForecastHistoricalDataSlot {
            betaMarketRead(token, BetaResearchGate.Type.HISTORICAL,
                alphaCall = { apiFactory.groww.historicalCandles(bearer(token), growwSymbol = "NSE-$symbol",
                    startTime = start, endTime = end, candleInterval = "1minute").requirePayload("Forecast candles $symbol") },
                betaCall = { apiFactory.betaGroww.historicalCandles(bearer(token), growwSymbol = "NSE-$symbol",
                    startTime = start, endTime = end, candleInterval = "1minute").requirePayload("Beta 1m candles $symbol") })
        }
        require(historical.candles.size >= 10) { "Insufficient forecast 1-minute candles" }
        val aggregated5m = com.multify.traderpro.engine.ForecastCandleShadow.aggregateFiveMinute(historical, System.currentTimeMillis())
        if (aggregated5m.completeBars > 0) auditLogger.logAsync("FORECAST_RESEARCH", "SHADOW_1M_5M", mapOf(
            "symbol" to symbol, "account" to (if (forecastUsesBeta(token)) "BETA" else "ALPHA"),
            "one_minute_bars" to historical.candles.size, "complete_five_minute_bars" to aggregated5m.completeBars,
            "last_completed_five_minute_close" to aggregated5m.lastClose, "decision_effect" to false,
            "extra_broker_requests" to 0
        ))
        val features = LocalStrategyEngine.buildFeatures(quote, historical, 1)
        val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "forecast", confidence = 1.0)
        return LocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
    }

    suspend fun executeForecast(symbol: String): String {
        val now = ZonedDateTime.now(INDIA)
        require(now.dayOfWeek.value < 6 && marketSession() == "OPEN") { "Forecast BUY is available only during the NSE regular session" }
        val settings = preferences.settings.first()
        preIndependentExecutionGuard(settings)
        val token = ensureToken() ?: error("Authenticate Groww first")
        val today = now.toLocalDate().toString()
        val row = learningDao.longForecastsForDate(today)
            .filter { it.symbol.equals(symbol, true) }
            .maxByOrNull { it.generatedAtMs } ?: error("No active LONG recommendation for $symbol")
        val pendingAuto = forecastAutoBuyOrderStore.forSymbol(today, row.symbol)
        if (pendingAuto != null && !ForecastAutoBuyPolicy.isTerminal(pendingAuto.status)) {
            return "Legacy Forecast entry order is still being migrated for ${row.symbol}; duplicate BUY blocked"
        }
        require(row.status == "ACTIVE") { "$symbol forecast is no longer active" }
        require(row.executionEligible) { "$symbol is ${row.qualityTier}; only PRIMARY forecast setups can place a real forecast BUY" }
        require(row.targetPct + 1e-9 >= ForecastQualityPolicy.MIN_TARGET_PCT) { "Forecast target is below the 1% quality floor" }
        if (managedDao.openPosition(ENGINE_FORECAST_HOLDING, row.symbol) != null) {
            return "${row.symbol} already has a Forecast holding; duplicate Forecast BUY blocked"
        }
        val quote = withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol).requirePayload("Quote ${row.symbol}")
        }
        val ltp = quote.lastPrice ?: error("Groww quote has no LTP")
        require(ltp < row.targetPrice) { "Forecast target already reached; BUY disabled" }
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val available = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        val sizing = CashAwareCncSizing.sizeStrictBudget(settings.holdingBudgetRupees.toDouble(), available, ltp)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        require(qty > 0) { "Forecast BUY skipped: full configured budget ₹${settings.holdingBudgetRupees} is not available" }
        val ref = stableRef("FLH", "${row.id}-${row.symbol}")
        val order = placeCncBuy(token, row.symbol, qty, ref)
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to row.symbol, "available_cnc" to available, "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to ltp,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty,
            "order_first" to true
        ))
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: ltp
        val investedAmount = entry * filled
        val trailArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING, symbol = row.symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = HardStopLossPolicy.stopPrice(entry),
                targetPrice = entry * (1.0 + row.targetPct / 100.0),
                strategy = "Forecast holding · ${row.strategy} · hard -2% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%",
                regime = row.regime, confidence = row.confidence,
                sourceEventId = null, openOrderId = order.growwOrderId, openReferenceId = ref,
                openedAtMs = System.currentTimeMillis(), lastPrice = entry,
                maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                trailingArmPct = trailArmPct, trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        auditLogger.log("FORECAST", "LONG_HOLDING_OPENED", mapOf(
            "symbol" to row.symbol, "qty" to filled, "entry" to entry,
            "forecast_target_pct" to row.targetPct,
            "invested_amount" to investedAmount,
            "trail_arm_trigger_pct" to trailArmPct,
            "initial_protected_gain_pct" to SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT,
            "trail_step_pct" to SteppedTrailingPolicy.STEP_PCT
        ))
        return "BUY ${row.symbol} opened as CNC holding · qty $filled @ ₹${fmt(entry)} · forecast target +${fmt(row.targetPct)}% · fixed trailing arms at +0.55% and protects +0.50%"
    }

    /**
     * Forecast auto-buy is direct execution. Each eligible PRIMARY row is sent as a CNC MARKET
     * BUY using the fresh quote and actual fill logic already used by executeForecast().
     */
    suspend fun autoBuyForecastRecommendations(): Int =
        forecastAutoBuySubmissionMutex.withLock { autoBuyForecastRecommendationsLocked() }

    private suspend fun autoBuyForecastRecommendationsLocked(): Int {
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6 || marketSession() != "OPEN") return 0
        val settings = preferences.settings.first()
        if (!settings.forecastAutoBuyEnabled || settings.safetyHalt) return 0
        // A restored or legacy ON setting must never bypass five completed research sessions.
        if (!forecastLiveUnlocked()) {
            auditLogger.logAsync("FORECAST_AUTO_BUY", "BUY_BLOCKED_CALIBRATION", mapOf(
                "completed_sessions" to forecastCalibrationDates().size,
                "required_sessions" to MarketPriorityPolicy.LIVE_FORECAST_UNLOCK_SESSIONS
            ))
            return 0
        }
        val preflightCooldown = buyApiCooldownOrNull()
        if (preflightCooldown != null) {
            auditAutoBuyBlocked("FORECAST_AUTO_BUY", preflightCooldown)
            return 0
        }
        preIndependentExecutionGuard(settings)
        val today = now.toLocalDate().toString()
        val rows = learningDao.longForecastsForDate(today)
            .filter { it.status == "ACTIVE" && it.executionEligible && it.qualityTier == "PRIMARY" }
            .sortedBy { it.generatedAtMs }
        var submitted = 0
        for (row in rows) {
            val cooldown = buyApiCooldownOrNull()
            if (cooldown != null) {
                auditAutoBuyBlocked("FORECAST_AUTO_BUY", cooldown)
                break
            }
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight) break
            val live = preferences.settings.first()
            if (!live.forecastAutoBuyEnabled || live.safetyHalt || !forecastLiveUnlocked()) break
            if (managedDao.openPosition(ENGINE_FORECAST_HOLDING, row.symbol) != null) continue
            if (forecastAutoBuyOrderStore.forSymbol(today, row.symbol) != null) continue
            val ok = runCatching { executeForecast(row.symbol) }
                .onFailure {
                    auditLogger.log("FORECAST_AUTO_BUY", "DIRECT_MARKET_BUY_ERROR", mapOf(
                        "symbol" to row.symbol,
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }.isSuccess
            if (ok) {
                submitted++
                auditLogger.log("FORECAST_AUTO_BUY", "ENTRY_MARKET_FILLED", mapOf("symbol" to row.symbol))
            }
            delay(FORECAST_AUTO_BUY_ORDER_PACING_MS)
        }
        return submitted
    }
    suspend fun reconcileForecastAutoBuyOrders(
        forceCancel: Boolean = false,
        cancelReason: String = ""
    ): Int {
        val today = LocalDate.now(INDIA).toString()
        val states = forecastAutoBuyOrderStore.all()
        if (states.isEmpty()) return 0
        val token = ensureToken() ?: return states.count { !ForecastAutoBuyPolicy.isTerminal(it.status) }
        val settings = preferences.settings.first()
        val forecastsById = learningDao.allLongForecasts().associateBy { it.id }

        var pending = 0
        for (state in states) {
            if (state.brokerKind == ForecastAutoBuyPolicy.BROKER_KIND_LEGACY_LIMIT) {
                reconcileLegacyForecastLimitOrder(token, state, cancelReason)
                continue
            }

            val existingPosition = managedDao.openPosition(ENGINE_FORECAST_HOLDING, state.symbol)
            if (existingPosition != null) {
                cancelForecastEntryGttIfActive(token, state, "HOLDING_ALREADY_EXISTS")
                forecastAutoBuyOrderStore.remove(state.date, state.symbol)
                continue
            }

            val broker = runCatching {
                apiFactory.groww.smartOrderStatus(
                    bearer(token),
                    segment = "CASH",
                    smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                    smartOrderId = state.orderId
                ).requirePayload("Forecast auto-buy GTT status ${state.symbol}")
            }.onFailure {
                auditLogger.log("FORECAST_AUTO_BUY", "GTT_STATUS_ERROR", mapOf(
                    "symbol" to state.symbol,
                    "smart_order_id" to state.orderId,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull()
            if (broker == null) {
                pending++
                continue
            }

            val forecast = forecastsById[state.forecastId]
            val forecastActive = forecast?.status == "ACTIVE"
            val executionEligible = forecast?.executionEligible == true
            val cancelRequired = ForecastAutoBuyPolicy.shouldCancel(
                stateDate = state.date,
                today = today,
                autoBuyEnabled = settings.forecastAutoBuyEnabled,
                safetyHalt = settings.safetyHalt,
                forecastActive = forecastActive,
                executionEligible = executionEligible,
                forceCancel = forceCancel
            )
            val status = broker.status.uppercase(Locale.US)

            if (ForecastAutoBuyPolicy.isCompleted(status)) {
                openCompletedForecastEntryGttPosition(state, forecast, token)
                forecastAutoBuyOrderStore.remove(state.date, state.symbol)
                continue
            }

            if (ForecastAutoBuyPolicy.isTerminal(status)) {
                forecastAutoBuyOrderStore.remove(state.date, state.symbol)
                auditLogger.log("FORECAST_AUTO_BUY", "GTT_TERMINAL_NO_FILL", mapOf(
                    "symbol" to state.symbol,
                    "smart_order_id" to state.orderId,
                    "status" to status
                ))
                continue
            }

            if (cancelRequired && ForecastAutoBuyPolicy.canCancelBeforeTrigger(status)) {
                val reason = when {
                    state.date != today -> "STALE_TRADING_DATE"
                    cancelReason.isNotBlank() -> cancelReason
                    forecast == null -> "FORECAST_MISSING"
                    forecast.status != "ACTIVE" -> "FORECAST_NO_LONGER_ACTIVE"
                    !forecast.executionEligible -> "AUTO_BUY_NOT_EXECUTION_ELIGIBLE"
                    !settings.forecastAutoBuyEnabled -> "AUTO_BUY_DISABLED"
                    settings.safetyHalt -> "SAFETY_HALT"
                    else -> "CANCEL_REQUIRED"
                }
                val cancelled = runCatching {
                    apiFactory.groww.cancelSmartOrder(
                        bearer(token),
                        segment = "CASH",
                        smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                        smartOrderId = state.orderId
                    ).requirePayload("Cancel forecast entry GTT ${state.symbol}")
                }.onFailure {
                    auditLogger.log("FORECAST_AUTO_BUY", "GTT_CANCEL_ERROR", mapOf(
                        "symbol" to state.symbol,
                        "smart_order_id" to state.orderId,
                        "reason" to reason,
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }.getOrNull()

                if (cancelled != null) {
                    forecastAutoBuyOrderStore.remove(state.date, state.symbol)
                    auditLogger.log("FORECAST_AUTO_BUY", "GTT_CANCELLED", mapOf(
                        "symbol" to state.symbol,
                        "smart_order_id" to state.orderId,
                        "reason" to reason,
                        "status" to cancelled.status
                    ))
                    continue
                }
            }

            if (cancelRequired && status == "TRIGGERED") {
                auditLogger.log("FORECAST_AUTO_BUY", "GTT_ALREADY_TRIGGERED", mapOf(
                    "symbol" to state.symbol,
                    "smart_order_id" to state.orderId,
                    "reason" to if (cancelReason.isNotBlank()) cancelReason else "EXECUTION_SWITCH_CHANGED",
                    "note" to "Broker trigger already fired; waiting for terminal broker status"
                ))
            }

            forecastAutoBuyOrderStore.upsert(state.copy(status = status.ifBlank { state.status }))
            pending++
        }
        return pending
    }

    private suspend fun submitForecastEntryGtt(
        token: String,
        row: LongForecastEntity,
        qty: Int,
        reference: String
    ): SmartOrderPayload {
        require(qty > 0) { "Forecast auto-buy quantity is zero" }
        require(row.entryPrice.isFinite() && row.entryPrice > 0.0) { "Forecast entry price is invalid" }
        require(row.targetPrice.isFinite() && row.targetPrice > row.entryPrice) { "Forecast target price is invalid" }

        val brokerStartedNs = SystemClock.elapsedRealtimeNanos()
        val placed = try {
            apiFactory.groww.createGtt(
                bearer(token),
                GttCreateRequest(
                    referenceId = reference,
                    smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                    segment = "CASH",
                    tradingSymbol = row.symbol,
                    quantity = qty,
                    triggerPrice = fmt(row.entryPrice),
                    triggerDirection = ForecastAutoBuyPolicy.TRIGGER_DIRECTION,
                    order = GttOrder(
                        orderType = ForecastAutoBuyPolicy.CHILD_ORDER_TYPE,
                        price = null,
                        transactionType = "BUY"
                    ),
                    productType = "CNC",
                    exchange = "NSE",
                    duration = "DAY"
                )
            ).requirePayload("Forecast entry GTT ${row.symbol}")
        } catch (t: Throwable) {
            recoverForecastEntryGttByReference(token, reference)
                ?: throw t
        }

        val brokerAckLatencyMs =
            ((SystemClock.elapsedRealtimeNanos() - brokerStartedNs).coerceAtLeast(0L)) / 1_000_000L
        preferences.recordOrderLatency(0L, brokerAckLatencyMs)
        auditLogger.logAsync("EXECUTION_LATENCY", "FORECAST_GTT_ACK", mapOf(
            "symbol" to row.symbol,
            "broker_ack_ms" to brokerAckLatencyMs,
            "fixed_entry_trigger" to row.entryPrice,
            "trigger_direction" to ForecastAutoBuyPolicy.TRIGGER_DIRECTION,
            "child_order_type" to ForecastAutoBuyPolicy.CHILD_ORDER_TYPE,
            "fixed_target_price" to row.targetPrice,
            "product" to "CNC"
        ))
        return placed
    }

    private suspend fun recoverForecastEntryGttByReference(
        token: String,
        reference: String
    ): SmartOrderPayload? {
        val statuses = listOf("ACTIVE", "TRIGGERED", "COMPLETED", "CANCELLED")
        for (status in statuses) {
            for (page in 0..4) {
                val payload = runCatching {
                    apiFactory.groww.listSmartOrders(
                        bearer(token),
                        segment = "CASH",
                        smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                        status = status,
                        page = page,
                        pageSize = 50
                    ).requirePayload("Recover forecast entry GTT by reference")
                }.getOrNull() ?: break
                payload.orders.firstOrNull { it.referenceId == reference }?.let { return it }
                if (payload.orders.size < 50) break
            }
        }
        return null
    }

    private suspend fun cancelForecastEntryGttIfActive(
        token: String,
        state: ForecastAutoBuyOrderState,
        reason: String
    ) {
        val status = runCatching {
            apiFactory.groww.smartOrderStatus(
                bearer(token),
                segment = "CASH",
                smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                smartOrderId = state.orderId
            ).requirePayload("Forecast auto-buy duplicate GTT status ${state.symbol}")
        }.getOrNull() ?: return
        if (!ForecastAutoBuyPolicy.canCancelBeforeTrigger(status.status)) return
        runCatching {
            apiFactory.groww.cancelSmartOrder(
                bearer(token),
                segment = "CASH",
                smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                smartOrderId = state.orderId
            ).requirePayload("Cancel duplicate forecast entry GTT ${state.symbol}")
        }.onSuccess {
            auditLogger.log("FORECAST_AUTO_BUY", "DUPLICATE_GTT_CANCELLED", mapOf(
                "symbol" to state.symbol,
                "smart_order_id" to state.orderId,
                "reason" to reason
            ))
        }
    }

    private suspend fun openCompletedForecastEntryGttPosition(
        state: ForecastAutoBuyOrderState,
        forecast: LongForecastEntity?,
        token: String
    ) {
        if (managedDao.openPosition(ENGINE_FORECAST_HOLDING, state.symbol) != null) return

        // The forecast entry remains the strategy's immutable entry reference. Groww's smart-order
        // response does not expose the child MARKET fill price. We record the broker's same-day debit
        // price as diagnostic evidence when available, without changing the fixed forecast reference.
        val observedBrokerFill = runCatching {
            apiFactory.groww.positions(bearer(token), segment = "CASH")
                .requirePayload("Forecast GTT fill position ${state.symbol}")
                .positions
                .firstOrNull { it.tradingSymbol.equals(state.symbol, true) }
                ?.debitPrice
                ?.takeIf { it.isFinite() && it > 0.0 }
        }.getOrNull()

        val now = System.currentTimeMillis()
        val trailArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING,
                symbol = state.symbol,
                product = "CNC",
                side = "LONG",
                quantity = state.quantity,
                entryPrice = state.fixedEntryPrice,
                stopPrice = HardStopLossPolicy.stopPrice(state.fixedEntryPrice),
                targetPrice = state.fixedTargetPrice,
                strategy = buildString {
                    append("Forecast auto-buy · fixed GTT UP trigger ₹")
                    append(fmt(state.fixedEntryPrice))
                    append(" · MARKET child · fixed target ₹")
                    append(fmt(state.fixedTargetPrice))
                    forecast?.strategy?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
                    append(" · hard -2% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%")
                },
                regime = forecast?.regime ?: "FORECAST_AUTO_BUY",
                confidence = forecast?.confidence ?: 1.0,
                sourceEventId = null,
                openOrderId = state.orderId,
                openReferenceId = state.referenceId,
                openedAtMs = now,
                lastPrice = state.fixedEntryPrice,
                maxFavourablePrice = state.fixedEntryPrice,
                maxAdversePrice = state.fixedEntryPrice,
                lastEvaluatedAtMs = now,
                trailingArmPct = trailArmPct,
                trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        brokerInventorySnapshot = null
        auditLogger.log("FORECAST_AUTO_BUY", "ENTRY_GTT_COMPLETED", mapOf(
            "symbol" to state.symbol,
            "smart_order_id" to state.orderId,
            "quantity" to state.quantity,
            "fixed_entry_trigger" to state.fixedEntryPrice,
            "observed_broker_debit_price" to observedBrokerFill,
            "fixed_target_price" to state.fixedTargetPrice,
            "target_recalculated" to false,
            "trail_arm_trigger_pct" to trailArmPct
        ))
    }

    private suspend fun reconcileLegacyForecastLimitOrder(
        token: String,
        state: ForecastAutoBuyOrderState,
        reason: String
    ) {
        val broker = runCatching {
            apiFactory.groww.orderStatus(bearer(token), state.orderId)
                .requirePayload("Legacy forecast LIMIT status ${state.symbol}")
        }.getOrNull() ?: return
        var status = broker.orderStatus.uppercase(Locale.US)
        var filled = (broker.filledQuantity ?: 0).coerceIn(0, state.quantity)

        if (!ForecastAutoBuyPolicy.isTerminal(status) && filled < state.quantity) {
            val cancelled = runCatching {
                apiFactory.groww.cancelOrder(
                    bearer(token),
                    OrderCancelRequest(growwOrderId = state.orderId)
                ).requirePayload("Cancel legacy forecast LIMIT ${state.symbol}")
            }.getOrNull()
            if (cancelled != null) {
                delay(FORECAST_AUTO_BUY_CANCEL_RECHECK_MS)
                val refreshed = runCatching {
                    apiFactory.groww.orderStatus(bearer(token), state.orderId)
                        .requirePayload("Legacy forecast LIMIT post-cancel ${state.symbol}")
                }.getOrDefault(cancelled)
                status = refreshed.orderStatus.uppercase(Locale.US)
                filled = (refreshed.filledQuantity ?: filled).coerceIn(0, state.quantity)
                if (filled > 0) {
                    openLegacyForecastLimitFill(state, refreshed, filled)
                }
            }
        } else if (filled > 0) {
            openLegacyForecastLimitFill(state, broker, filled)
        }

        forecastAutoBuyOrderStore.remove(state.date, state.symbol)
        auditLogger.log("FORECAST_AUTO_BUY", "LEGACY_LIMIT_RECONCILED", mapOf(
            "symbol" to state.symbol,
            "order_id" to state.orderId,
            "filled_quantity" to filled,
            "status" to status,
            "reason" to reason.ifBlank { "MIGRATE_TO_GTT" }
        ))
    }

    private suspend fun openLegacyForecastLimitFill(
        state: ForecastAutoBuyOrderState,
        broker: OrderPayload,
        filledQuantity: Int
    ) {
        if (managedDao.openPosition(ENGINE_FORECAST_HOLDING, state.symbol) != null) return
        val actualFill = broker.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: state.fixedEntryPrice
        val now = System.currentTimeMillis()
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING,
                symbol = state.symbol,
                product = "CNC",
                side = "LONG",
                quantity = filledQuantity,
                entryPrice = actualFill,
                stopPrice = HardStopLossPolicy.stopPrice(actualFill),
                targetPrice = state.fixedTargetPrice,
                strategy = "Migrated legacy forecast LIMIT fill · fixed target ₹${fmt(state.fixedTargetPrice)} · hard -2% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%",
                regime = "FORECAST_AUTO_BUY",
                confidence = 1.0,
                sourceEventId = null,
                openOrderId = state.orderId,
                openReferenceId = state.referenceId,
                openedAtMs = now,
                lastPrice = actualFill,
                maxFavourablePrice = actualFill,
                maxAdversePrice = actualFill,
                lastEvaluatedAtMs = now,
                trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
                trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        brokerInventorySnapshot = null
    }

    suspend fun executeFixed(symbol: String): String {
        val now = ZonedDateTime.now(INDIA)
        require(now.dayOfWeek.value < 6 && marketSession() == "OPEN") { "Fixed BUY is available only during the NSE regular session" }
        val settings = preferences.settings.first()
        preIndependentExecutionGuard(settings)
        val token = ensureToken() ?: error("Authenticate Groww first")
        val today = now.toLocalDate().toString()
        val row = fixedRecommendationStore.recommendations(today)
            .firstOrNull { it.symbol.equals(symbol, true) } ?: error("No active Fixed recommendation for $symbol")

        // Legacy entry GTTs migrate in the maintenance worker, never on the BUY hot path.
        val pendingLegacy = fixedAutoBuyOrderStore.forSymbol(today, row.symbol)
        if (pendingLegacy != null && !ForecastAutoBuyPolicy.isTerminal(pendingLegacy.status)) {
            return "Legacy Fixed entry GTT is still being reconciled for ${row.symbol}; no duplicate BUY was sent"
        }

        require(row.status == "ACTIVE") { "$symbol Fixed recommendation is no longer active" }
        require(row.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES) { "Fixed only executes recommendations above ₹${fmt(FIXED_MIN_STOCK_PRICE_RUPEES)}" }
        require(row.executionEligible && row.qualityTier == "PRIMARY") { "Only PRIMARY Fixed recommendations can place a real BUY" }
        if (managedDao.openPosition(ENGINE_FIXED_HOLDING, row.symbol) != null) {
            return "${row.symbol} already has a Fixed holding; duplicate Fixed BUY blocked"
        }

        val quote = withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol).requirePayload("Quote ${row.symbol}")
        }
        val ltp = quote.lastPrice ?: error("Groww quote has no LTP")
        if (ltp >= row.targetPrice) {
            fixedRecommendationStore.update(row.copy(
                status = "TARGET_HIT", targetHitAtMs = System.currentTimeMillis(),
                lastPrice = ltp,
                maxFavourablePct = maxOf(row.maxFavourablePct, (ltp / row.entryPrice - 1.0) * 100.0),
                lastObservedAtMs = System.currentTimeMillis()
            ))
            fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            error("Fixed target already reached for $symbol; BUY disabled")
        }
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val available = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        val sizing = CashAwareCncSizing.sizeFixedAvailable(settings.holdingBudgetRupees.toDouble(), available, ltp)
        require(sizing.quantity > 0) { "Fixed BUY skipped: remaining CNC cash cannot fund one buffered share of ${row.symbol}" }

        require(marketSession() == "OPEN") { "Fixed BUY disabled: NSE closed at 15:30 IST" }
        require(fixedRecommendationStore.recommendations(today).firstOrNull { it.id == row.id }?.status == "ACTIVE") {
            "Fixed BUY disabled: recommendation is no longer active"
        }
        val ref = stableRef("FXH", "${row.recommendationDate}-${row.id}-${row.symbol}")
        val order = placeCncBuy(token, row.symbol, sizing.quantity, ref)
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: sizing.quantity
        val actualFill = order.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 } ?: ltp
        val executionTarget = actualFill * (1.0 + row.targetPct / 100.0)
        val nowMs = System.currentTimeMillis()
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FIXED_HOLDING,
                symbol = row.symbol,
                product = "CNC",
                side = "LONG",
                quantity = filled,
                entryPrice = actualFill,
                stopPrice = HardStopLossPolicy.stopPrice(actualFill),
                targetPrice = executionTarget,
                strategy = "Fixed ${FixedEngineSnapshotStore.SNAPSHOT_ID} manual direct MARKET BUY · published entry ₹${fmt(row.entryPrice)} · broker fill ₹${fmt(actualFill)} · execution target ₹${fmt(executionTarget)} · ${row.strategy} · hard -2% floor · v7 stepped trail 0.55→0.50 then +0.10%",
                regime = row.regime,
                confidence = row.confidence,
                sourceEventId = null,
                openOrderId = order.growwOrderId,
                openReferenceId = ref,
                openedAtMs = nowMs,
                lastPrice = actualFill,
                maxFavourablePrice = actualFill,
                maxAdversePrice = actualFill,
                lastEvaluatedAtMs = nowMs,
                trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
                trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        brokerInventorySnapshot = null
        auditLogger.log("FIXED_ENGINE", "LONG_HOLDING_OPENED", mapOf(
            "symbol" to row.symbol,
            "quantity" to filled,
            "published_entry" to row.entryPrice,
            "broker_fill" to actualFill,
            "published_target" to row.targetPrice,
            "execution_target" to executionTarget,
            "target_pct" to row.targetPct,
            "entry_order_type" to "MARKET",
            "snapshot_id" to FixedEngineSnapshotStore.SNAPSHOT_ID
        ))
        return "Fixed BUY ${row.symbol} opened · qty $filled · fill ₹${fmt(actualFill)} · target ₹${fmt(executionTarget)}"
    }
    suspend fun autoBuyFixedRecommendations(): Int =
        fixedAutoBuySubmissionMutex.withLock { autoBuyFixedRecommendationsLocked() }

    private suspend fun autoBuyFixedRecommendationsLocked(): Int {
        ensureFixedEngineSnapshot()
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6 || marketSession() != "OPEN") return 0
        val settings = preferences.settings.first()
        if (!settings.fixedAutoBuyEnabled || settings.safetyHalt) return 0
        val preflightCooldown = buyApiCooldownOrNull()
        if (preflightCooldown != null) {
            auditAutoBuyBlocked("FIXED_AUTO_BUY", preflightCooldown)
            return 0
        }
        preIndependentExecutionGuard(settings)
        val token = ensureToken() ?: run {
            auditLogger.logAsync("FIXED_AUTO_BUY", "BUY_BLOCKED_BROKER_NOT_READY", mapOf("reason" to "TOKEN_NOT_READY"))
            return 0
        }
        val today = now.toLocalDate().toString()

        // Legacy entry GTTs migrate in the maintenance worker; normal direct buys do no pre-order broker reconciliation.
        val rows = fixedRecommendationStore.recommendations(today)
            .filter {
                it.status == "ACTIVE" &&
                    it.executionEligible &&
                    it.qualityTier == "PRIMARY" &&
                    it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES
            }
            .sortedBy { it.generatedAtMs }

        var submitted = 0
        for (row in rows) {
            val symbolStartedAtMs = System.currentTimeMillis()
            val recommendationAgeAtStartMs = (symbolStartedAtMs - row.generatedAtMs).coerceAtLeast(0L)
            val liveSettings = preferences.settings.first()
            if (!liveSettings.fixedAutoBuyEnabled || liveSettings.safetyHalt || marketSession() != "OPEN") break
            if (!AutoBuyEntrySafetyPolicy.isFreshFixedEntry(row.generatedAtMs, symbolStartedAtMs)) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_SKIPPED_STALE_RECOMMENDATION", mapOf(
                    "symbol" to row.symbol,
                    "recommendation_age_ms" to recommendationAgeAtStartMs,
                    "max_age_ms" to AutoBuyEntrySafetyPolicy.FIXED_AUTO_BUY_MAX_AGE_MS
                ))
                continue
            }
            val cooldown = buyApiCooldownOrNull()
            if (cooldown != null) {
                auditAutoBuyBlocked("FIXED_AUTO_BUY", cooldown)
                break
            }
            if (managedDao.openPosition(ENGINE_FIXED_HOLDING, row.symbol) != null) continue

            // If broker reconciliation cannot prove an old entry GTT is gone, skip only this symbol
            // rather than risk a duplicate position. Other recommendations in the same scan continue.
            val pendingLegacy = fixedAutoBuyOrderStore.forSymbol(today, row.symbol)
            if (pendingLegacy != null && !ForecastAutoBuyPolicy.isTerminal(pendingLegacy.status)) {
                auditLogger.log("FIXED_AUTO_BUY", "DIRECT_BUY_BLOCKED_BY_LEGACY_GTT", mapOf(
                    "symbol" to row.symbol,
                    "smart_order_id" to pendingLegacy.orderId,
                    "status" to pendingLegacy.status
                ))
                continue
            }

            val quoteStartedAtMs = System.currentTimeMillis()
            val quote = runCatching {
                withFixedLiveDataSlot {
                    apiFactory.groww.quote(
                        bearer(token),
                        tradingSymbol = row.symbol
                    ).requirePayload("Fixed direct quote ${row.symbol}")
                }
            }.onFailure {
                auditLogger.log("FIXED_AUTO_BUY", "DIRECT_QUOTE_ERROR", mapOf(
                    "symbol" to row.symbol,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull()
            if (quote == null) {
                val cooldown = buyApiCooldownOrNull()
                if (cooldown != null) {
                    auditAutoBuyBlocked("FIXED_AUTO_BUY", cooldown)
                    break
                }
                continue
            }
            val quoteElapsedMs = (System.currentTimeMillis() - quoteStartedAtMs).coerceAtLeast(0L)
            val ltp = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            if (ltp >= row.targetPrice) {
                fixedRecommendationStore.update(row.copy(
                    status = "TARGET_HIT", targetHitAtMs = System.currentTimeMillis(),
                    lastPrice = ltp,
                    maxFavourablePct = maxOf(row.maxFavourablePct, (ltp / row.entryPrice - 1.0) * 100.0),
                    lastObservedAtMs = System.currentTimeMillis()
                ))
                fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
                auditLogger.log("FIXED_OUTCOME", "BUY_PREVENTED_TARGET_ALREADY_HIT", mapOf("symbol" to row.symbol))
                continue
            }

            val marginCooldown = buyApiCooldownOrNull()
            if (marginCooldown != null) {
                auditAutoBuyBlocked("FIXED_AUTO_BUY", marginCooldown)
                break
            }
            val marginStartedAtMs = System.currentTimeMillis()
            val margin = runCatching { apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin") }
                .onFailure {
                    auditLogger.log("FIXED_AUTO_BUY", "MARGIN_ERROR", mapOf(
                        "symbol" to row.symbol,
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }.getOrNull()
            if (margin == null) {
                val cooldown = buyApiCooldownOrNull()
                if (cooldown != null) {
                    auditAutoBuyBlocked("FIXED_AUTO_BUY", cooldown)
                    break
                }
                continue
            }
            val marginElapsedMs = (System.currentTimeMillis() - marginStartedAtMs).coerceAtLeast(0L)
            val available = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
            val sizing = CashAwareCncSizing.sizeFixedAvailable(
                liveSettings.holdingBudgetRupees.toDouble(),
                available,
                ltp
            )
            if (sizing.quantity <= 0) {
                auditLogger.log("FIXED_AUTO_BUY", "SKIPPED_NO_CASH", mapOf(
                    "symbol" to row.symbol,
                    "published_entry" to row.entryPrice,
                    "fresh_ltp" to ltp,
                    "available_cnc" to available
                ))
                continue
            }

            val executionSettings = preferences.settings.first()
            if (!executionSettings.fixedAutoBuyEnabled || executionSettings.safetyHalt || marketSession() != "OPEN") break
            if (!AutoBuyEntrySafetyPolicy.isFreshFixedEntry(row.generatedAtMs, System.currentTimeMillis())) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_SKIPPED_EXPIRED_DURING_PREP", mapOf("symbol" to row.symbol))
                continue
            }
            if (fixedRecommendationStore.recommendations(today).firstOrNull { it.id == row.id }?.status != "ACTIVE") continue

            val reference = stableRef("FXM", "${row.recommendationDate}-${row.id}-${row.symbol}")
            val orderStartedAtMs = System.currentTimeMillis()
            val order = runCatching {
                placeCncBuy(token, row.symbol, sizing.quantity, reference)
            }.onFailure {
                auditLogger.log("FIXED_AUTO_BUY", "MARKET_BUY_ERROR", mapOf(
                    "symbol" to row.symbol,
                    "reference" to reference,
                    "fresh_ltp" to ltp,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull() ?: continue

            val orderElapsedMs = (System.currentTimeMillis() - orderStartedAtMs).coerceAtLeast(0L)
            val filled = order.filledQuantity?.takeIf { it > 0 } ?: sizing.quantity
            val actualFill = order.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 } ?: ltp
            val executionTarget = actualFill * (1.0 + row.targetPct / 100.0)
            val openedAtMs = System.currentTimeMillis()
            managedDao.insertPosition(
                ManagedPositionEntity(
                    engine = ENGINE_FIXED_HOLDING,
                    symbol = row.symbol,
                    product = "CNC",
                    side = "LONG",
                    quantity = filled,
                    entryPrice = actualFill,
                    stopPrice = HardStopLossPolicy.stopPrice(actualFill),
                    targetPrice = executionTarget,
                    strategy = "Fixed ${FixedEngineSnapshotStore.SNAPSHOT_ID} auto-buy · direct MARKET · published entry ₹${fmt(row.entryPrice)} · fill ₹${fmt(actualFill)} · execution target ₹${fmt(executionTarget)} · ${row.strategy} · hard -2% floor · v7 stepped trail 0.55→0.50 then +0.10%",
                    regime = row.regime,
                    confidence = row.confidence,
                    sourceEventId = null,
                    openOrderId = order.growwOrderId,
                    openReferenceId = reference,
                    openedAtMs = openedAtMs,
                    lastPrice = actualFill,
                    maxFavourablePrice = actualFill,
                    maxAdversePrice = actualFill,
                    lastEvaluatedAtMs = openedAtMs,
                    trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
                    trailingLastRatchetPrice = 0.0,
                    smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
                )
            )
            brokerInventorySnapshot = null
            auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_LATENCY_PROFILE", mapOf(
                "symbol" to row.symbol,
                "recommendation_generated_at_ms" to row.generatedAtMs,
                "recommendation_age_at_auto_buy_start_ms" to recommendationAgeAtStartMs,
                "quote_ms" to quoteElapsedMs,
                "margin_ms" to marginElapsedMs,
                "order_to_fill_ms" to orderElapsedMs,
                "symbol_total_ms" to (System.currentTimeMillis() - symbolStartedAtMs).coerceAtLeast(0L),
                "notification_before_order" to false,
                "fresh_quote_preserved" to true,
                "fresh_margin_preserved" to true,
                "fixed_formula_changed" to false
            ))
            auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_MARKET_FILLED", mapOf(
                "symbol" to row.symbol,
                "order_id" to order.growwOrderId,
                "reference" to reference,
                "quantity" to filled,
                "fresh_ltp_before_order" to ltp,
                "published_entry" to row.entryPrice,
                "actual_fill" to actualFill,
                "published_target" to row.targetPrice,
                "execution_target" to executionTarget,
                "target_pct" to row.targetPct,
                "snapshot_id" to FixedEngineSnapshotStore.SNAPSHOT_ID
            ))
            submitted++
            delay(FIXED_DIRECT_AUTO_BUY_ORDER_PACING_MS)
        }
        return submitted
    }
    suspend fun reconcileFixedAutoBuyOrders(
        forceCancel: Boolean = false,
        cancelReason: String = ""
    ): Int {
        val today = LocalDate.now(INDIA).toString()
        val states = fixedAutoBuyOrderStore.all()
        if (states.isEmpty()) return 0
        val token = ensureToken() ?: return states.count { !ForecastAutoBuyPolicy.isTerminal(it.status) }
        val settings = preferences.settings.first()
        val forecasts = fixedRecommendationStore.recommendations(today).associateBy { it.id }
        var pending = 0

        for (state in states) {
            val existing = managedDao.openPosition(ENGINE_FIXED_HOLDING, state.symbol)
            if (existing != null) {
                cancelFixedEntryGttIfActive(token, state, "FIXED_HOLDING_ALREADY_EXISTS")
                fixedAutoBuyOrderStore.remove(state.date, state.symbol)
                continue
            }
            val broker = runCatching {
                apiFactory.groww.smartOrderStatus(
                    bearer(token), segment = "CASH", smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                    smartOrderId = state.orderId
                ).requirePayload("Fixed auto-buy GTT status ${state.symbol}")
            }.onFailure {
                auditLogger.log("FIXED_AUTO_BUY", "GTT_STATUS_ERROR", mapOf(
                    "symbol" to state.symbol,
                    "smart_order_id" to state.orderId,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull()
            if (broker == null) { pending++; continue }

            val row = if (state.date == today) forecasts[state.forecastId] else null
            val cancelRequired = ForecastAutoBuyPolicy.shouldCancel(
                stateDate = state.date,
                today = today,
                autoBuyEnabled = settings.fixedAutoBuyEnabled,
                safetyHalt = settings.safetyHalt,
                forecastActive = row?.status == "ACTIVE",
                executionEligible = row?.executionEligible == true,
                forceCancel = forceCancel
            )
            val status = broker.status.uppercase(Locale.US)
            if (ForecastAutoBuyPolicy.isCompleted(status)) {
                openCompletedFixedEntryGttPosition(state, row, token)
                fixedAutoBuyOrderStore.remove(state.date, state.symbol)
                continue
            }
            if (ForecastAutoBuyPolicy.isTerminal(status)) {
                fixedAutoBuyOrderStore.remove(state.date, state.symbol)
                continue
            }
            if (cancelRequired && ForecastAutoBuyPolicy.canCancelBeforeTrigger(status)) {
                val reason = when {
                    state.date != today -> "STALE_TRADING_DATE"
                    cancelReason.isNotBlank() -> cancelReason
                    row == null -> "FIXED_RECOMMENDATION_MISSING"
                    row.status != "ACTIVE" -> "FIXED_RECOMMENDATION_NO_LONGER_ACTIVE"
                    !row.executionEligible -> "FIXED_NOT_EXECUTION_ELIGIBLE"
                    !settings.fixedAutoBuyEnabled -> "FIXED_AUTO_BUY_DISABLED"
                    settings.safetyHalt -> "SAFETY_HALT"
                    else -> "CANCEL_REQUIRED"
                }
                val cancelled = runCatching {
                    apiFactory.groww.cancelSmartOrder(
                        bearer(token), segment = "CASH", smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                        smartOrderId = state.orderId
                    ).requirePayload("Cancel Fixed entry GTT ${state.symbol}")
                }.getOrNull()
                if (cancelled != null) {
                    fixedAutoBuyOrderStore.remove(state.date, state.symbol)
                    auditLogger.log("FIXED_AUTO_BUY", "GTT_CANCELLED", mapOf(
                        "symbol" to state.symbol,
                        "smart_order_id" to state.orderId,
                        "reason" to reason
                    ))
                    continue
                }
            }
            fixedAutoBuyOrderStore.upsert(state.copy(status = status.ifBlank { state.status }))
            pending++
        }
        return pending
    }

    private suspend fun submitFixedEntryGtt(
        token: String,
        row: FixedRecommendationRecord,
        qty: Int,
        reference: String
    ): SmartOrderPayload {
        require(qty > 0) { "Fixed auto-buy quantity is zero" }
        require(row.entryPrice.isFinite() && row.entryPrice > 0.0) { "Fixed entry price is invalid" }
        val brokerStartedNs = SystemClock.elapsedRealtimeNanos()
        val placed = try {
            apiFactory.groww.createGtt(
                bearer(token),
                GttCreateRequest(
                    referenceId = reference,
                    smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                    segment = "CASH",
                    tradingSymbol = row.symbol,
                    quantity = qty,
                    triggerPrice = fmt(row.entryPrice),
                    triggerDirection = ForecastAutoBuyPolicy.TRIGGER_DIRECTION,
                    order = GttOrder(
                        orderType = ForecastAutoBuyPolicy.CHILD_ORDER_TYPE,
                        price = null,
                        transactionType = "BUY"
                    ),
                    productType = "CNC",
                    exchange = "NSE",
                    duration = "DAY"
                )
            ).requirePayload("Fixed entry GTT ${row.symbol}")
        } catch (t: Throwable) {
            recoverFixedEntryGttByReference(token, reference) ?: throw t
        }
        preferences.recordOrderLatency(0L, ((SystemClock.elapsedRealtimeNanos() - brokerStartedNs).coerceAtLeast(0L)) / 1_000_000L)
        return placed
    }

    private suspend fun recoverFixedEntryGttByReference(token: String, reference: String): SmartOrderPayload? {
        for (status in listOf("ACTIVE", "TRIGGERED", "COMPLETED", "CANCELLED")) {
            for (page in 0..4) {
                val payload = runCatching {
                    apiFactory.groww.listSmartOrders(
                        bearer(token), segment = "CASH", smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                        status = status, page = page, pageSize = 50
                    ).requirePayload("Recover Fixed entry GTT")
                }.getOrNull() ?: break
                payload.orders.firstOrNull { it.referenceId == reference }?.let { return it }
                if (payload.orders.size < 50) break
            }
        }
        return null
    }

    private suspend fun cancelFixedEntryGttIfActive(token: String, state: ForecastAutoBuyOrderState, reason: String) {
        val broker = runCatching {
            apiFactory.groww.smartOrderStatus(
                bearer(token), segment = "CASH", smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                smartOrderId = state.orderId
            ).requirePayload("Fixed duplicate GTT status ${state.symbol}")
        }.getOrNull() ?: return
        if (!ForecastAutoBuyPolicy.canCancelBeforeTrigger(broker.status)) return
        runCatching {
            apiFactory.groww.cancelSmartOrder(
                bearer(token), segment = "CASH", smartOrderType = ForecastAutoBuyPolicy.SMART_ORDER_TYPE,
                smartOrderId = state.orderId
            ).requirePayload("Cancel duplicate Fixed entry GTT ${state.symbol}")
        }.onSuccess {
            auditLogger.log("FIXED_AUTO_BUY", "DUPLICATE_GTT_CANCELLED", mapOf(
                "symbol" to state.symbol,
                "smart_order_id" to state.orderId,
                "reason" to reason
            ))
        }
    }

    private suspend fun openCompletedFixedEntryGttPosition(
        state: ForecastAutoBuyOrderState,
        row: FixedRecommendationRecord?,
        token: String
    ) {
        if (managedDao.openPosition(ENGINE_FIXED_HOLDING, state.symbol) != null) return
        val observedBrokerFill = runCatching {
            apiFactory.groww.positions(bearer(token), segment = "CASH")
                .requirePayload("Fixed legacy GTT fill position ${state.symbol}").positions
                .firstOrNull { it.tradingSymbol.equals(state.symbol, true) }?.debitPrice
                ?.takeIf { it.isFinite() && it > 0.0 }
        }.getOrNull()
        val actualEntry = observedBrokerFill ?: state.fixedEntryPrice
        val targetPct = row?.targetPct?.takeIf { it.isFinite() && it > 0.0 }
            ?: (((state.fixedTargetPrice / state.fixedEntryPrice) - 1.0) * 100.0).coerceAtLeast(0.0)
        val executionTarget = actualEntry * (1.0 + targetPct / 100.0)
        val now = System.currentTimeMillis()
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FIXED_HOLDING,
                symbol = state.symbol,
                product = "CNC",
                side = "LONG",
                quantity = state.quantity,
                entryPrice = actualEntry,
                stopPrice = HardStopLossPolicy.stopPrice(actualEntry),
                targetPrice = executionTarget,
                strategy = "Fixed ${FixedEngineSnapshotStore.SNAPSHOT_ID} legacy GTT migrated · actual entry ₹${fmt(actualEntry)} · execution target ₹${fmt(executionTarget)} · ${row?.strategy.orEmpty()} · hard -2% floor · v7 stepped trail 0.55→0.50 then +0.10%",
                regime = row?.regime ?: "FIXED_ENGINE",
                confidence = row?.confidence ?: 1.0,
                sourceEventId = null,
                openOrderId = state.orderId,
                openReferenceId = state.referenceId,
                openedAtMs = now,
                lastPrice = actualEntry,
                maxFavourablePrice = actualEntry,
                maxAdversePrice = actualEntry,
                lastEvaluatedAtMs = now,
                trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
                trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        brokerInventorySnapshot = null
        auditLogger.log("FIXED_AUTO_BUY", "LEGACY_ENTRY_GTT_ADOPTED", mapOf(
            "symbol" to state.symbol,
            "smart_order_id" to state.orderId,
            "quantity" to state.quantity,
            "published_entry" to state.fixedEntryPrice,
            "observed_broker_debit_price" to observedBrokerFill,
            "actual_entry" to actualEntry,
            "published_target" to state.fixedTargetPrice,
            "execution_target" to executionTarget,
            "snapshot_id" to FixedEngineSnapshotStore.SNAPSHOT_ID
        ))
    }
    suspend fun pendingLongForecastNotifications(): List<ForecastDto> {
        val today = LocalDate.now(INDIA).toString()
        val rows = learningDao.pendingLongForecastNotifications(today)
        rows.forEach { learningDao.markLongForecastNotified(it.id) }
        return rows.map { it.toForecastDto() }
    }

    /**
     * Forecast presentation is order-first. If Forecast auto-buy is enabled, pending PRIMARY rows
     * are not marked notified until the direct market-order pass has completed without being
     * pre-empted by Multify or Fixed priority. A higher-priority arrival simply defers delivery.
     */
    suspend fun pendingForecastNotificationsAfterOrderPath(): List<ForecastDto> {
        val settings = preferences.settings.first()
        if (settings.safetyHalt) return emptyList()
        if (settings.forecastAutoBuyEnabled && forecastLiveUnlocked()) {
            if (!forecastMarketCapacityAvailable()) return emptyList()
            autoBuyForecastRecommendations()
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight) return emptyList()
        }
        return pendingLongForecastNotifications()
            .filter { it.qualityTier == "PRIMARY" && it.executionEligible }
    }

    suspend fun monitorForecastOutcomes(): Int {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastForecastOutcomeMonitorAtMs < FORECAST_OUTCOME_INTERVAL_MS) {
            return learningDao.activeLongForecasts().size
        }
        lastForecastOutcomeMonitorAtMs = nowMs
        val now = ZonedDateTime.now(INDIA)
        val today = now.toLocalDate().toString()
        val rows = learningDao.longForecastsForDate(today)
        if (rows.isEmpty()) return 0
        // Outcomes are Forecast research, not order execution. Use Beta if configured;
        // an unauthenticated Beta must never silently borrow Alpha's request budget.
        val token = forecastMarketToken() ?: return rows.count { it.status == "ACTIVE" }

        val ltps = fetchForecastOutcomeLtps(token, rows.map { it.symbol })
        if (ltps.isEmpty()) return rows.count { it.status == "ACTIVE" }
        preferences.recordMarketData()

        for (row in rows) {
            val ltp = ltps[row.symbol.uppercase(Locale.US)] ?: continue
            val movePct = (ltp / row.entryPrice - 1.0) * 100.0
            val adversePct = max(0.0, (row.entryPrice - ltp) / row.entryPrice * 100.0)
            val hitNow = ltp >= row.targetPrice
            val expired = now.toLocalTime() >= LocalTime.of(15, 30)
            val status = when {
                row.status == "TARGET_HIT" -> "TARGET_HIT"
                hitNow -> "TARGET_HIT"
                expired -> "MISSED"
                else -> "ACTIVE"
            }
            val statusChanged = status != row.status
            val updated = row.copy(
                status = status,
                targetHitAtMs = when {
                    row.targetHitAtMs != null -> row.targetHitAtMs
                    hitNow -> nowMs
                    else -> null
                },
                lastPrice = ltp,
                maxFavourablePct = max(row.maxFavourablePct, max(0.0, movePct)),
                maxAdversePct = max(row.maxAdversePct, adversePct),
                lastObservedAtMs = nowMs
            )
            learningDao.updateLongForecast(updated)
            if (statusChanged && status != "ACTIVE") refreshForecastChampion(updated)
        }
        return learningDao.longForecastsForDate(today).count { it.status == "ACTIVE" }
    }

    /**
     * Diagnostic-only Fixed outcome observer. Not a Fixed model input and never touches order/SELL policies.
     * Observe DURING the session: after-close LTP cannot reconstruct intraday highs.
     * Priority: shared Fixed bulk scan snapshot first, then one small Fixed-priority LTP batch
     * only when neither the Fixed scan nor a critical order is using broker bandwidth.
     */
    suspend fun monitorFixedOutcomes(): Int {
        val now = ZonedDateTime.now(INDIA)
        val nowMs = System.currentTimeMillis()
        val today = now.toLocalDate().toString()
        val rows = fixedRecommendationStore.recommendations(today)
        fixedOutcomePerformanceStore.record(rows)
        if (rows.isEmpty()) return 0

        val open = marketSession() == "OPEN"
        if (!open) {
            // Do not hallucinate intraday target hits from a stale post-close quote.
            rows.filter { it.status == "ACTIVE" }.forEach { row ->
                fixedRecommendationStore.update(row.copy(status = "SESSION_ENDED"))
            }
            fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            return 0
        }

        // Reuse fresh market-wide Fixed discovery samples, without extra API calls.
        val discovery = fixedMarketDiscoveryStateStore.load(today)
        val cachedPrices = discovery?.takeIf { nowMs - it.updatedAtMs in 0L..75_000L }
            ?.previousLtps.orEmpty()
        val cachedAt = discovery?.updatedAtMs ?: 0L
        var observed = 0
        for (row in rows) {
            // The published generatedAtMs is scan START, not delivery. Use a full scan-spacing
            // delay before accepting the shared discovery snapshot as post-publication evidence.
            if (cachedAt < row.generatedAtMs + FIXED_V741_FORECAST_SCAN_INTERVAL_MS ||
                cachedAt <= row.lastObservedAtMs) continue
            val price = cachedPrices[row.symbol.uppercase(Locale.US)] ?: continue
            if (price.isFinite() && price > 0.0) {
                updateFixedObservedOutcome(row, price, cachedAt, "DISCOVERY_CACHE")
                observed++
            }
        }

        if (nowMs - lastFixedOutcomeMonitorAtMs < FORECAST_OUTCOME_INTERVAL_MS ||
            fixedApiPriorityRequested || marketCriticalTradeInFlight) {
            if (observed > 0) fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            return fixedRecommendationStore.recommendations(today).count { it.status == "ACTIVE" }
        }
        // Avoid delaying protected trades and the high-priority Fixed scanner when Live Data is saturated.
        val liveUsage = GrowwApiUsageLedger.snapshot().categories
            .firstOrNull { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }
        if (liveUsage == null || liveUsage.minuteHeadroom <= 5) {
            if (observed > 0) fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            return fixedRecommendationStore.recommendations(today).count { it.status == "ACTIVE" }
        }
        lastFixedOutcomeMonitorAtMs = nowMs
        val token = ensureToken() ?: return fixedRecommendationStore.recommendations(today).count { it.status == "ACTIVE" }
        val symbols = rows.map { it.symbol.uppercase(Locale.US) }.distinct()
        val fetched = runCatching {
            withFixedLiveDataSlot {
                apiFactory.groww.ltp(
                    bearer(token), exchangeSymbols = symbols.joinToString(",") { "NSE_$it" }
                ).requirePayload("Fixed outcome live LTP")
            }
        }.onFailure {
            auditLogger.log("FIXED_OUTCOME", "LIVE_SAMPLE_ERROR", mapOf(
                "symbols" to symbols.size, "message" to (it.message ?: it.javaClass.simpleName)
            ))
        }.getOrNull().orEmpty()
        val observedAt = System.currentTimeMillis()
        for (row in fixedRecommendationStore.recommendations(today)) {
            val price = fetched["NSE_${row.symbol.uppercase(Locale.US)}"]
                ?: fetched[row.symbol.uppercase(Locale.US)] ?: continue
            if (price.isFinite() && price > 0.0) {
                updateFixedObservedOutcome(row, price, observedAt, "LIVE_LTP")
                observed++
            }
        }
        if (observed > 0) {
            fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            _fixedDeliveryEvents.tryEmit(observedAt) // Repaint Fixed badges and Averages promptly.
        }
        return fixedRecommendationStore.recommendations(today).count { it.status == "ACTIVE" }
    }

    private fun updateFixedObservedOutcome(
        row: FixedRecommendationRecord, price: Double, atMs: Long, source: String
    ) {
        if (atMs <= row.lastObservedAtMs || atMs <= row.generatedAtMs) return
        val move = maxOf(0.0, (price / row.entryPrice - 1.0) * 100.0)
        val maxMfe = maxOf(row.maxFavourablePct, move)
        val targetHit = row.status == "TARGET_HIT" || price >= row.targetPrice ||
            maxMfe >= row.targetPct - 1e-9
        val nextStatus = if (targetHit) "TARGET_HIT" else row.status
        fixedRecommendationStore.update(row.copy(
            status = nextStatus,
            targetHitAtMs = row.targetHitAtMs ?: if (targetHit) atMs else null,
            lastPrice = price,
            maxFavourablePct = maxMfe,
            maxAdversePct = maxOf(row.maxAdversePct, maxOf(0.0, (row.entryPrice - price) / row.entryPrice * 100.0)),
            lastObservedAtMs = atMs
        ))
        if (targetHit && row.status != "TARGET_HIT") {
            auditLogger.log("FIXED_OUTCOME", "TARGET_HIT", mapOf(
                "symbol" to row.symbol, "target_pct" to row.targetPct,
                "mfe_pct" to maxMfe, "observed_price" to price, "source" to source
            ))
        }
    }

    private suspend fun fetchForecastOutcomeLtps(token: String, symbols: List<String>): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        for ((index, chunk) in symbols.map { it.uppercase(Locale.US) }.distinct().chunked(50).withIndex()) {
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight) break
            var payload: Map<String, Double>? = null
            var lastFailure: Throwable? = null
            for (attempt in 0 until FORECAST_OUTCOME_BATCH_ATTEMPTS) {
                try {
                    payload = withForecastLiveDataSlot {
                        val symbols = chunk.joinToString(",") { "NSE_$it" }
                        betaMarketRead(token, BetaResearchGate.Type.LIVE,
                            alphaCall = { apiFactory.groww.ltp(bearer(token), exchangeSymbols = symbols)
                                .requirePayload("Forecast outcome batch LTP") },
                            betaCall = { apiFactory.betaGroww.ltp(bearer(token), exchangeSymbols = symbols)
                                .requirePayload("Beta Forecast outcome batch LTP") }
                        )
                    }
                    break
                } catch (t: Throwable) {
                    lastFailure = t
                    if (!isTransientMarketDataFailure(t) || attempt + 1 >= FORECAST_OUTCOME_BATCH_ATTEMPTS) break
                    delay(FORECAST_OUTCOME_RETRY_BACKOFF_MS)
                }
            }
            if (payload == null) {
                auditLogger.log("FORECAST", "OUTCOME_BATCH_ERROR", mapOf(
                    "account" to (if (secretStore.hasBetaCredentials()) "BETA" else "ALPHA"),
                    "alpha_fallback" to false,
                    "batch_index" to index,
                    "symbols" to chunk,
                    "message" to (lastFailure?.message ?: lastFailure?.javaClass?.simpleName ?: "unknown")
                ))
                continue
            }
            payload.forEach { (key, value) ->
                if (value.isFinite() && value > 0.0) {
                    result[key.removePrefix("NSE_").uppercase(Locale.US)] = value
                }
            }
        }
        return result
    }

    private suspend fun refreshForecastChampion(row: LongForecastEntity) {
        val completed = learningDao.allLongForecasts().filter {
            it.marketRegime == row.marketRegime && it.regime == row.regime &&
                it.strategy == row.strategy && it.status in setOf("TARGET_HIT", "MISSED") &&
                ForecastUniversalPositiveEvidencePolicy.qualifies(
                    realizedPct = null,
                    closePct = if (it.entryPrice > 0.0) (it.lastPrice / it.entryPrice - 1.0) * 100.0 else null,
                    maxFavourablePct = it.maxFavourablePct,
                    targetHit = it.status == "TARGET_HIT"
                )
        }
        if (completed.isEmpty()) return
        val wins = completed.count { it.status == "TARGET_HIT" }
        val losses = completed.count { it.status == "MISSED" }
        val days = completed.map { it.forecastDate }.distinct().size
        val symbols = completed.map { it.symbol }.distinct().size
        val previous = learningDao.forecastChampion(row.championKey)
        val frozen = previous?.frozen == true || (wins >= 5 && days >= 3 && symbols >= 3)
        learningDao.upsertForecastChampion(
            ForecastChampionEntity(
                key = row.championKey, marketRegime = row.marketRegime, regime = row.regime,
                strategy = row.strategy, wins = wins, losses = losses, sampleCount = completed.size,
                distinctDays = days, distinctSymbols = symbols, frozen = frozen,
                firstSeenAtMs = previous?.firstSeenAtMs ?: completed.minOf { it.generatedAtMs },
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun researchCandleTimestampMs(value: com.google.gson.JsonElement): Long? {
        val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (primitive.isNumber) {
            val raw = runCatching { primitive.asLong }.getOrNull() ?: return null
            return if (raw in 1_000_000_000L..9_999_999_999L) raw * 1000L else raw
        }
        val text = runCatching { primitive.asString.trim() }.getOrNull().orEmpty()
        text.toLongOrNull()?.let { raw ->
            return if (raw in 1_000_000_000L..9_999_999_999L) raw * 1000L else raw
        }
        runCatching { LocalDateTime.parse(text, HIST_FORMAT).atZone(INDIA).toInstant().toEpochMilli() }
            .getOrNull()?.let { return it }
        runCatching { ZonedDateTime.parse(text).toInstant().toEpochMilli() }
            .getOrNull()?.let { return it }
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    }

    /** Serialized inside the existing, backup/exported Forecast snapshot JSON. No Room migration. */
    private fun timingStudy(row: ForecastResearchEntity): ForecastEntryTimingPolicy.Study? = runCatching {
        val json = JsonParser.parseString(row.featureJson).asJsonObject
        val studyJson = json.get("entry_timing_v1") ?: return@runCatching null
        researchGson.fromJson(studyJson, ForecastEntryTimingPolicy.Study::class.java)
            ?.takeIf { it.version == ForecastEntryTimingPolicy.VERSION && it.dataQuality == "OK" }
    }.getOrNull()

    private fun timingExamples(rows: List<ForecastResearchEntity>): List<ForecastEntryTimingPolicy.Example> =
        rows.asSequence().filter { it.outcomeUpdatedAtMs != null }
            // Repeated scanner snapshots are correlated. Retain one per symbol and 30-minute window.
            .groupBy { "${it.researchDate}|${it.symbol.uppercase(Locale.US)}|${it.scanAtMs / 1_800_000L}" }
            .values.mapNotNull { xs -> xs.minByOrNull { it.scanAtMs } }
            .mapNotNull { row ->
                val study = timingStudy(row) ?: return@mapNotNull null
                val features = runCatching { JsonParser.parseString(row.featureJson).asJsonObject }.getOrNull()
                fun numeric(name: String) = runCatching { features?.get(name)?.asDouble }.getOrNull()
                fun bool(name: String) = runCatching { features?.get(name)?.asBoolean }.getOrNull() == true
                ForecastEntryTimingPolicy.Example(
                    row.researchDate, row.symbol.uppercase(Locale.US), row.scanAtMs,
                    row.marketRegime, ForecastEntryTimingPolicy.timeBucket(row.scanAtMs),
                    ForecastEntryTimingPolicy.volumeBucket(numeric("rvol")),
                    ForecastEntryTimingPolicy.structureBucket(numeric("forecast_pre_spike_compression_ratio"),
                        bool("forecast_pre_spike_rising_floor")), study.outcomes
                )
            }.sortedWith(compareBy({ it.date }, { it.atMs })).toList()

    private fun timingAdvice(
        market: String, scanAtMs: Long, rvol: Double?,
        compression: Double?, rising: Boolean,
        history: List<ForecastEntryTimingPolicy.Example>, date: String
    ): ForecastEntryTimingPolicy.Advice = ForecastEntryTimingPolicy.advise(
        market, ForecastEntryTimingPolicy.timeBucket(scanAtMs),
        ForecastEntryTimingPolicy.volumeBucket(rvol),
        ForecastEntryTimingPolicy.structureBucket(compression, rising), history, date
    )

    private suspend fun backfillForecastResearchOutcomes(date: String, token: String): Int {
        val pending = learningDao.pendingForecastResearchOutcomes(date)
        if (pending.isEmpty()) return 0
        val now = ZonedDateTime.now(INDIA)
        // No partial-session replay; every alternative must have the same completed outcome window.
        if (date == now.toLocalDate().toString() && now.toLocalTime() < LocalTime.of(15, 35)) return 0
        val sessionDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return 0
        val sessionEndMs = sessionDate.atTime(15, 30).atZone(INDIA).toInstant().toEpochMilli()
        var updatedCount = 0
        var consecutiveNetworkFailures = 0
        val nowElapsed = SystemClock.elapsedRealtime()
        val groups = pending.groupBy { it.symbol }.entries
            .filter { (symbol, _) -> (forecastReplayRetryAfterMs["$date|$symbol"] ?: 0L) <= nowElapsed }
            .take(RESEARCH_BACKFILL_SYMBOL_BATCH)
        if (groups.isEmpty()) {
            auditLogger.logAsync("FORECAST_RESEARCH", "OUTCOME_REPLAY_DEFERRED", mapOf(
                "date" to date, "pending_rows" to pending.size,
                "reason" to "RESEARCH_ONLY_SYMBOL_BACKOFF", "live_forecast_changed" to false
            ))
            return 0
        }

        for ((symbol, rows) in groups) {
            val earliest = rows.minOf { it.scanAtMs }
            val startTime = Instant.ofEpochMilli(earliest)
                .atZone(INDIA).toLocalDateTime().format(HIST_FORMAT)
            val endTime = sessionDate.atTime(15, 30).format(HIST_FORMAT)
            var failure: Throwable? = null
            val historical = try {
                betaMarketRead(token, BetaResearchGate.Type.HISTORICAL,
                    alphaCall = { apiFactory.groww.historicalCandles(bearer(token), growwSymbol = "NSE-$symbol",
                        startTime = startTime, endTime = endTime, candleInterval = "1minute")
                        .requirePayload("Forecast research outcome $symbol") },
                    betaCall = { apiFactory.betaGroww.historicalCandles(bearer(token), growwSymbol = "NSE-$symbol",
                        startTime = startTime, endTime = endTime, candleInterval = "1minute")
                        .requirePayload("Beta Forecast research outcome $symbol") })
            } catch (t: Throwable) {
                failure = t
                null
            }

            if (historical == null) {
                val retryKey = "$date|$symbol"
                val failures = (forecastReplayFailureCount[retryKey] ?: 0) + 1
                forecastReplayFailureCount[retryKey] = failures
                val retryMs = (30_000L * (1L shl (failures - 1).coerceIn(0, 5)))
                    .coerceAtMost(15L * 60_000L)
                forecastReplayRetryAfterMs[retryKey] = SystemClock.elapsedRealtime() + retryMs
                auditLogger.log("FORECAST_RESEARCH", "OUTCOME_BACKFILL_ERROR", mapOf(
                    "date" to date, "symbol" to symbol,
                    "message" to (failure?.message ?: failure?.javaClass?.simpleName ?: "unknown"),
                    "research_retry_ms" to retryMs,
                    "attempt" to failures
                ))
                if (failure != null && isTransientMarketDataFailure(failure)) {
                    consecutiveNetworkFailures++
                    if (consecutiveNetworkFailures >= RESEARCH_BACKFILL_NETWORK_ABORT_AFTER) {
                        auditLogger.log("FORECAST_RESEARCH", "OUTCOME_BACKFILL_NETWORK_ABORT", mapOf(
                            "date" to date,
                            "updated_before_abort" to updatedCount,
                            "remaining_rows" to pending.size,
                            "consecutive_network_failures" to consecutiveNetworkFailures
                        ))
                        break
                    }
                } else {
                    consecutiveNetworkFailures = 0
                }
                delay(RESEARCH_BACKFILL_SYMBOL_PACING_MS)
                continue
            }

            consecutiveNetworkFailures = 0
            forecastReplayFailureCount.remove("$date|$symbol")
            forecastReplayRetryAfterMs.remove("$date|$symbol")
            preferences.recordMarketData()
            val candles = historical.candles.mapNotNull { x ->
                if (x.size < 5) return@mapNotNull null
                val atMs = researchCandleTimestampMs(x[0]) ?: return@mapNotNull null
                val open = runCatching { x[1].asDouble }.getOrNull() ?: return@mapNotNull null
                val high = runCatching { x[2].asDouble }.getOrNull() ?: return@mapNotNull null
                val low = runCatching { x[3].asDouble }.getOrNull() ?: return@mapNotNull null
                val close = runCatching { x[4].asDouble }.getOrNull() ?: return@mapNotNull null
                val volume = x.getOrNull(5)?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0
                ResearchCandle(atMs, high, low, close, open, volume)
            }.sortedBy { it.atMs }
            rows.forEach { row ->
                // Never count the partly observed one-minute bar at notification time.
                val xs = candles.filter { it.atMs > row.scanAtMs && it.atMs < sessionEndMs }
                if (xs.isEmpty() || row.entryPrice <= 0.0) return@forEach
                val high = xs.maxOf { it.high }
                val low = xs.minOf { it.low }
                val close = xs.last().close
                val mfe = max(0.0, (high / row.entryPrice - 1.0) * 100.0)
                val mae = max(0.0, (row.entryPrice - low) / row.entryPrice * 100.0)
                val closePct = (close / row.entryPrice - 1.0) * 100.0
                val study = ForecastEntryTimingPolicy.reconstruct(
                    signalAtMs = row.scanAtMs,
                    referencePrice = row.entryPrice,
                    targetPct = row.targetPct,
                    bars = candles.map { bar -> ForecastEntryTimingPolicy.Bar(
                        bar.atMs, bar.open, bar.high, bar.low, bar.close, bar.volume
                    ) }
                )
                val enriched = runCatching { JsonParser.parseString(row.featureJson).asJsonObject }
                    .getOrElse { com.google.gson.JsonObject() }
                enriched.add("entry_timing_v1", researchGson.toJsonTree(study))
                enriched.addProperty("entry_timing_model_applied_live", false)
                learningDao.updateForecastResearch(
                    row.copy(
                        featureJson = researchGson.toJson(enriched),
                        outcomeMfePct = mfe,
                        outcomeMaePct = mae,
                        outcomeClosePct = closePct,
                        outcomeTargetHit = high + 1e-9 >= row.entryPrice * (1.0 + row.targetPct / 100.0),
                        outcomeUpdatedAtMs = System.currentTimeMillis()
                    )
                )
                updatedCount++
                if (study.dataQuality == "OK") {
                    auditLogger.log("FORECAST_ENTRY_TIMING", "ONE_MINUTE_STUDY", mapOf(
                        "date" to date, "symbol" to symbol, "scan_at_ms" to row.scanAtMs,
                        "context" to row.marketRegime, "best_hindsight_method" to study.hindsightBest,
                        "methods" to study.outcomes.map { mapOf("method" to it.method.name,
                            "filled" to it.filled, "net_pct" to it.netPct, "target_hit" to it.targetHit) },
                        "hindsight_not_live_selection" to true
                    ))
                }
            }
            delay(RESEARCH_BACKFILL_SYMBOL_PACING_MS)
        }
        if (updatedCount > 0) {
            auditLogger.log("FORECAST_RESEARCH", "OUTCOMES_BACKFILLED", mapOf(
                "date" to date, "count" to updatedCount, "candle_interval" to "1minute", "timing_study_version" to ForecastEntryTimingPolicy.VERSION
            ))
        }
        return updatedCount
    }

    suspend fun runAfterHoursResearch(force: Boolean = false): ResearchDto {
        ensureHistoricalSeed()
        val today = LocalDate.now(INDIA).toString()
        val alphaToken = ensureToken()
        val token = forecastMarketToken()
        if (alphaToken != null) {
            runCatching { backfillRecentMultifyMfe(alphaToken, MFE_AFTER_HOURS_BACKFILL_BATCH_SIZE, force = true) }
        }
        var researchUpdated = 0
        var researchBatches = 0
        val replayStartedAt = SystemClock.elapsedRealtime()
        if (token != null) {
            while (researchBatches < RESEARCH_BACKFILL_MAX_BATCHES_PER_RUN &&
                SystemClock.elapsedRealtime() - replayStartedAt < RESEARCH_BACKFILL_BUDGET_MS) {
                val incompleteDate = learningDao.forecastResearchDates()
                    .filter { it >= V7_RESEARCH_COHORT_START_DATE }
                    .firstOrNull { learningDao.pendingForecastResearchOutcomes(it).isNotEmpty() }
                    ?: break
                val pendingBefore = learningDao.pendingForecastResearchOutcomes(incompleteDate).size
                val updated = runCatching { backfillForecastResearchOutcomes(incompleteDate, token) }.getOrDefault(0)
                researchUpdated += updated
                researchBatches++
                val pendingAfter = learningDao.pendingForecastResearchOutcomes(incompleteDate).size
                if (updated <= 0 || pendingAfter >= pendingBefore) break
            }
        }
        val betaUsage = betaResearchGate.snapshot()
        auditLogger.logAsync("FORECAST_RESEARCH", "BACKFILL_RUN_PROFILE", mapOf(
            "batches" to researchBatches, "updated_rows" to researchUpdated,
            "elapsed_ms" to (SystemClock.elapsedRealtime() - replayStartedAt).coerceAtLeast(0L),
            "beta_live_requests" to betaUsage.liveRequests,
            "beta_historical_requests" to betaUsage.historicalRequests,
            "beta_live_429" to betaUsage.liveRateLimits,
            "beta_historical_429" to betaUsage.historicalRateLimits
        ))
        val todayCalibrationUpdated = if (token != null && learningDao.forecastResearchForDate(today).isNotEmpty()) {
            runCatching { backfillForecastResearchOutcomes(today, token) }.getOrDefault(0)
        } else 0
        val universalEvidenceUpdated = if (alphaToken != null) {
            runCatching { refreshUniversalPositiveEvidence(alphaToken) }
                .onFailure {
                    auditLogger.log("FORECAST_REPLAY", "UNIVERSAL_EVIDENCE_REFRESH_ERROR", mapOf(
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
                .getOrDefault(0)
        } else 0
        val todayCompletedResearchRows = learningDao.forecastResearchForDate(today)
            .count { it.outcomeUpdatedAtMs != null }
        if (todayCompletedResearchRows >= FORECAST_CALIBRATION_MIN_COMPLETED_ROWS) {
            markForecastCalibrationSessionComplete(today)
        }

        val existing = learningDao.latestResearchReport()
        if (!force && existing?.reportDate == today && researchUpdated == 0 &&
            todayCalibrationUpdated == 0 && universalEvidenceUpdated == 0
        ) {
            return ResearchDto(existing.reportDate, existing.title, existing.summary)
        }

        val stats = learningStats()
        val forecastLearning = forecastLearningStats()
        val forecasts = learningDao.longForecastsForDate(today)
        val snapshots = learningDao.forecastResearchForDate(today).filter { it.outcomeUpdatedAtMs != null }
        val positiveCloseSnapshots = snapshots.filter {
            ForecastUniversalPositiveEvidencePolicy.qualifies(
                realizedPct = null,
                closePct = it.outcomeClosePct,
                maxFavourablePct = it.outcomeMfePct ?: 0.0,
                targetHit = it.outcomeTargetHit == true
            )
        }
        val universalEvidence = learningDao.forecastPositiveEvidenceForDate(today)
        val wins = forecasts.count { it.status == "TARGET_HIT" }
        val matched = forecasts.count { it.multifyMatched }
        val frozen = learningDao.allForecastChampions().count { it.frozen }
        val avgTarget = forecasts.map { it.targetPct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val allCompletedHistoricalRows = learningDao.allForecastResearch()
            .filter { it.researchDate < today && it.outcomeUpdatedAtMs != null }
            .groupBy { it.researchDate + "|" + it.symbol }
            .mapNotNull { (_, rows) -> rows.minByOrNull { it.scanAtMs } }
        val historicalOutcomes = allCompletedHistoricalRows.mapNotNull { row ->
            ForecastHistoricalOutcome(
                date = row.researchDate,
                symbol = row.symbol,
                strategy = row.strategy,
                regime = row.regime,
                marketRegime = row.marketRegime,
                maxFavourablePct = row.outcomeMfePct ?: return@mapNotNull null,
                maxAdversePct = row.outcomeMaePct ?: return@mapNotNull null,
                closePct = row.outcomeClosePct ?: return@mapNotNull null,
                targetHit = row.outcomeTargetHit == true
            )
        }
        val broadDiagnostics = ForecastAdaptiveSelectionPolicy.diagnostics(
            strategy = "__ALL__",
            regime = "__ALL__",
            marketRegime = "__ALL__",
            outcomes = historicalOutcomes
        ).takeIf { it.sampleCount > 0 } ?: run {
            if (historicalOutcomes.isEmpty()) ForecastEvidenceDiagnostics() else {
                // Force a broad aggregate without changing the policy's live context matching.
                val normalized = historicalOutcomes.map {
                    it.copy(strategy = "ALL", regime = "ALL", marketRegime = "ALL")
                }
                ForecastAdaptiveSelectionPolicy.diagnostics("ALL", "ALL", "ALL", normalized)
            }
        }
        val familyOutcomes = allCompletedHistoricalRows.mapNotNull { row ->
            val summary = evidenceFeatureSummary(row.featureJson)
            val close = row.outcomeClosePct ?: return@mapNotNull null
            ForecastFamilyOutcome(
                date = row.researchDate,
                symbol = row.symbol,
                marketRegime = row.marketRegime,
                momentumScore = summary.momentum,
                participationScore = summary.participation,
                structureScore = summary.structure,
                positive = row.outcomeTargetHit == true || close > 0.05
            )
        }
        val shadowFamilyWeights = listOf("BREADTH_BULL", "BREADTH_NEUTRAL", "BREADTH_WEAK")
            .associateWith { regime -> ForecastAdaptiveSelectionPolicy.shadowFamilyWeights(regime, familyOutcomes) }
        val fixedToday = adaptiveFixedEvidenceRecords().filter { it.recommendationDate == today }
        val fixedHitRate = if (fixedToday.isEmpty()) 0.0 else
            fixedToday.count { it.maxFavourablePct + 1e-9 >= it.targetPct }.toDouble() / fixedToday.size * 100.0
        val fixedAvgMfe = fixedToday.map { it.maxFavourablePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val forecastResearchHitRate = if (snapshots.isEmpty()) 0.0 else
            snapshots.count { it.outcomeTargetHit == true }.toDouble() / snapshots.size * 100.0
        val forecastResearchAvgMfe = snapshots.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val forecastVsFixedMfeDelta = forecastResearchAvgMfe - fixedAvgMfe
        val forecastVsFixedHitDelta = forecastResearchHitRate - fixedHitRate
        val forecastTradesToday = managedDao.allTrades().filter { trade ->
            trade.engine == ENGINE_FORECAST_HOLDING &&
                Instant.ofEpochMilli(trade.closedAtMs).atZone(INDIA).toLocalDate().toString() == today
        }
        val avgRealizedNetPct = forecastTradesToday.mapNotNull { trade ->
            val invested = trade.entryPrice * trade.quantity
            if (invested > 0.0) trade.netPnl / invested * 100.0 else null
        }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgMfeTradePct = forecastTradesToday.mapNotNull { trade ->
            val invested = trade.entryPrice * trade.quantity
            if (invested > 0.0) trade.mfeRupees / invested * 100.0 else null
        }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgProfitCapturePct = forecastTradesToday.mapNotNull { trade ->
            if (trade.mfeRupees > 0.0) (trade.grossPnl / trade.mfeRupees * 100.0).coerceIn(-500.0, 500.0) else null
        }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val timeBucketPerformance = snapshots
            .groupBy { ForecastUniversalPositiveEvidencePolicy.timeBucket(it.scanAtMs) }
            .mapValues { (_, rows) ->
                val hitRate = if (rows.isEmpty()) 0.0 else rows.count { it.outcomeTargetHit == true }.toDouble() / rows.size * 100.0
                val positiveRate = if (rows.isEmpty()) 0.0 else rows.count {
                    (it.outcomeClosePct ?: 0.0) > 0.05 || it.outcomeTargetHit == true
                }.toDouble() / rows.size * 100.0
                val avgMfe = rows.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                val avgMae = rows.mapNotNull { it.outcomeMaePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                mapOf(
                    "count" to rows.size,
                    "target_hit_rate_pct" to hitRate,
                    "positive_rate_pct" to positiveRate,
                    "avg_mfe_pct" to avgMfe,
                    "avg_mae_pct" to avgMae
                )
            }

        fun tierSummary(tier: String): String {
            val xs = positiveCloseSnapshots.filter { it.qualityTier == tier }
            if (xs.isEmpty()) return "$tier: no completed research rows"
            val hitRate = xs.count { it.outcomeTargetHit == true }.toDouble() / xs.size * 100.0
            val mfe = xs.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val mae = xs.mapNotNull { it.outcomeMaePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val multifyMatches = xs.count { it.multifyMatched }
            return "$tier: ${xs.size} rows · target hit ${fmt(hitRate)}% · avg MFE ${fmt(mfe)}% · avg MAE ${fmt(mae)}% · Multify matches $multifyMatches"
        }

        val entryTimingStudies = timingExamples(learningDao.allForecastResearch())
        val entryTimingDates = entryTimingStudies.map { it.date }.distinct().size
        val entryTimingSymbols = entryTimingStudies.map { it.symbol }.distinct().size
        val entryTimingToday = entryTimingStudies.filter { it.date == today }
        val entryTimingNowNet = entryTimingToday.mapNotNull {
            it.outcomes.firstOrNull { o -> o.method == ForecastEntryTimingPolicy.Method.NOW }?.netPct
        }
        val entryTimingBestNet = entryTimingToday.mapNotNull { example ->
            example.outcomes.mapNotNull { it.netPct }.maxOrNull()
        }
        val entryTimingAdvisories = entryTimingToday.map { example ->
            ForecastEntryTimingPolicy.advise(example.market, example.time,
                example.volume, example.structure, entryTimingStudies, example.date)
        }
        val entryTimingValidated = entryTimingAdvisories.count { it.validated }
        val entryTimingPending = learningDao.pendingForecastResearchOutcomes(today).size
        val report = buildString {
            append("Forecast 1m entry-timing replay: ${entryTimingToday.size} independent setups today, " +
                "${entryTimingStudies.size} historical independent setups across $entryTimingDates days/$entryTimingSymbols stocks. " +
                "NOW mean net ${fmt(entryTimingNowNet.takeIf { it.isNotEmpty() }?.average() ?: 0.0)}%, " +
                "best hindsight alternative mean ${fmt(entryTimingBestNet.takeIf { it.isNotEmpty() }?.average() ?: 0.0)}% " +
                "(diagnostic only, not achievable live). " +
                "Walk-forward validated context advisories: $entryTimingValidated/${entryTimingAdvisories.size}. " +
                "Pending one-minute outcome snapshots: $entryTimingPending. " +
                "Research backfill batches are resumable and do not modify Fixed or SELL. ")
            append("Rolling 30-trading-day Multify MFE average: ${fmt(stats.longAveragePct)}% (median ${fmt(stats.longMedianPct)}%) from ${stats.rollingCalls} MFE-observed calls. ")
            append("Pending historical MFE backfill: ${stats.mfeBackfillPendingCalls}. ")
            append(
                if (forecastLearning.completedForecasts > 0)
                    "Rolling published-forecast full-session MFE average: ${fmt(forecastLearning.longAveragePct)}% across ${forecastLearning.completedForecasts} completed forecasts. "
                else
                    "Published-forecast MFE is still learning. "
            )
            append("Today's average target: ${fmt(avgTarget)}% with a hard 1.00% floor. ")
            append("Published target hits: $wins/${forecasts.size}. ")
            append("Five-session live calibration: ${forecastLearning.researchSessionsCaptured}/${forecastLearning.researchSessionGoal}. ")
            append("Universal positive evidence today: ${universalEvidence.size} rows (Forecast/Fixed/Multify), with target misses still positive when the shared stepped trail armed/protected profit. ")
            append("All-outcome diagnostics: ${broadDiagnostics.sampleCount} historical rows across ${broadDiagnostics.distinctDays} days/${broadDiagnostics.distinctSymbols} stocks; positive ${fmt(broadDiagnostics.positiveRatePct)}%, target-hit ${fmt(broadDiagnostics.targetHitRatePct)}%, median MFE ${fmt(broadDiagnostics.medianMfePct)}%, median MAE ${fmt(broadDiagnostics.medianMaePct)}%, median close ${fmt(broadDiagnostics.medianClosePct)}%. ")
            if (broadDiagnostics.validationSamples > 0) append("Chronological out-of-sample holdout: ${broadDiagnostics.validationSamples} rows, target-hit ${fmt(broadDiagnostics.validationTargetHitRatePct)}%. ")
            append("Shadow negative penalty proposal ${fmt(broadDiagnostics.shadowPenalty)} is diagnostic only and is NOT applied to live ranking. ")
            val nonNeutralShadowWeights = shadowFamilyWeights.filterValues { weights -> weights.values.any { kotlin.math.abs(it - 1.0) > 0.001 } }
            if (nonNeutralShadowWeights.isNotEmpty()) append("Shadow family-weight proposals: " + nonNeutralShadowWeights.entries.joinToString("; ") { (regime, weights) -> regime + " " + weights.entries.joinToString(",") { (family, weight) -> family + "=" + fmt(weight) } } + ". These are research-only; live 40-factor family weights are unchanged. ")
            if (fixedToday.isNotEmpty() || snapshots.isNotEmpty()) append("Forecast vs frozen Fixed today: research MFE ${fmt(forecastResearchAvgMfe)}% vs Fixed ${fmt(fixedAvgMfe)}% (Δ ${fmt(forecastVsFixedMfeDelta)}pp); target-hit ${fmt(forecastResearchHitRate)}% vs Fixed ${fmt(fixedHitRate)}% (Δ ${fmt(forecastVsFixedHitDelta)}pp). ")
            if (forecastTradesToday.isNotEmpty()) append("Forecast exits: ${forecastTradesToday.size} closed holdings · avg realised net ${fmt(avgRealizedNetPct)}% · avg MFE ${fmt(avgMfeTradePct)}% · avg gross profit capture ${fmt(avgProfitCapturePct)}%. ")
            if (timeBucketPerformance.isNotEmpty()) append("Time-of-day diagnostics: " + timeBucketPerformance.entries.sortedBy { it.key }.joinToString("; ") { (bucket, stats) ->
                bucket + " n=" + stats["count"] + ", hit=" + fmt(stats["target_hit_rate_pct"] as Double) + "%" +
                    ", positive=" + fmt(stats["positive_rate_pct"] as Double) + "%" +
                    ", MFE=" + fmt(stats["avg_mfe_pct"] as Double) + "%" +
                    ", MAE=" + fmt(stats["avg_mae_pct"] as Double) + "%"
            } + ". ")
            append(tierSummary("PRIMARY")).append(". ")
            append(tierSummary("SECONDARY")).append(". ")
            append(tierSummary("RESEARCH")).append(". ")
            append("Multify later matched $matched/${forecasts.size} published symbols. ")
            append("Frozen LONG champions: $frozen. ")
            append("Forecast learning is positive-only: realized-positive, positive-close, target-hit, or common-trailing-protected cases may become evidence after maturity; negative/flat outcomes remain diagnostic and never become ranking penalties. ")
            append("Production execution remains PRIMARY-only while SECONDARY/RESEARCH rows collect evidence.")
        }
        val entity = com.multify.traderpro.data.local.ResearchReportEntity(
            reportDate = today, generatedAtMs = System.currentTimeMillis(),
            title = "After-market production forecast review", summary = report
        )
        learningDao.insertResearchReport(entity)
        auditLogger.log("RESEARCH", "AFTER_HOURS_PRODUCTION_REPORT", mapOf(
            "date" to today, "wins" to wins, "total" to forecasts.size, "matches" to matched,
            "entry_timing_version" to ForecastEntryTimingPolicy.VERSION,
            "entry_timing_independent_setups_today" to entryTimingToday.size,
            "entry_timing_independent_setups_total" to entryTimingStudies.size,
            "entry_timing_distinct_days" to entryTimingDates,
            "entry_timing_validated_contexts_today" to entryTimingValidated,
            "entry_timing_pending_rows_today" to entryTimingPending,
            "entry_timing_immediate_avg_net_pct" to entryTimingNowNet.takeIf { it.isNotEmpty() }?.average(),
            "entry_timing_hindsight_avg_net_pct" to entryTimingBestNet.takeIf { it.isNotEmpty() }?.average(),
            "entry_timing_research_only" to true,
            "multify_mfe_avg_pct" to stats.longAveragePct, "forecast_avg_target_pct" to avgTarget,
            "research_rows_completed" to snapshots.size,
            "positive_close_learning_rows" to positiveCloseSnapshots.size,
            "negative_or_flat_rows_excluded" to (snapshots.size - positiveCloseSnapshots.size),
            "all_outcome_sample_count" to broadDiagnostics.sampleCount,
            "all_outcome_positive_rate_pct" to broadDiagnostics.positiveRatePct,
            "all_outcome_target_hit_rate_pct" to broadDiagnostics.targetHitRatePct,
            "all_outcome_median_mfe_pct" to broadDiagnostics.medianMfePct,
            "all_outcome_median_mae_pct" to broadDiagnostics.medianMaePct,
            "out_of_sample_validation_samples" to broadDiagnostics.validationSamples,
            "out_of_sample_target_hit_rate_pct" to broadDiagnostics.validationTargetHitRatePct,
            "shadow_negative_penalty" to broadDiagnostics.shadowPenalty,
            "shadow_negative_penalty_applied_live" to false,
            "shadow_family_weights" to shadowFamilyWeights,
            "shadow_family_weights_applied_live" to false,
            "fixed_today_count" to fixedToday.size,
            "fixed_today_avg_mfe_pct" to fixedAvgMfe,
            "fixed_today_target_hit_rate_pct" to fixedHitRate,
            "forecast_today_research_avg_mfe_pct" to forecastResearchAvgMfe,
            "forecast_today_research_target_hit_rate_pct" to forecastResearchHitRate,
            "forecast_vs_fixed_mfe_delta_pp" to forecastVsFixedMfeDelta,
            "forecast_vs_fixed_hit_delta_pp" to forecastVsFixedHitDelta,
            // 20% relative lift is a validation objective, not a guarantee or new live gate.
            "forecast_20pct_relative_hit_goal_pct" to (fixedHitRate * 1.20).coerceAtMost(100.0),
            "forecast_20pct_relative_goal_met_research" to (fixedToday.size >= 30 && snapshots.size >= 30 &&
                forecastResearchHitRate >= (fixedHitRate * 1.20).coerceAtMost(100.0)),
            "forecast_20pct_goal_is_provisional" to (fixedToday.size < 30 || snapshots.size < 30),
            "forecast_closed_trades_today" to forecastTradesToday.size,
            "forecast_avg_realized_net_pct" to avgRealizedNetPct,
            "forecast_avg_trade_mfe_pct" to avgMfeTradePct,
            "forecast_avg_profit_capture_pct" to avgProfitCapturePct,
            "time_bucket_performance" to timeBucketPerformance,
            "time_bucket_analysis_applied_live" to false,
            "research_sessions" to forecastLearning.researchSessionsCaptured
        ))
        return ResearchDto(today, entity.title, report)
    }

    private suspend fun brokerInventory(token: String, force: Boolean): BrokerInventorySnapshot {
        val now = System.currentTimeMillis()
        brokerInventorySnapshot?.takeIf { !force && now - it.atMs <= BROKER_INVENTORY_REFRESH_MS }?.let { return it }
        // Gson can decode a null trading_symbol despite the non-null model declaration.
        // Never crash portfolio monitoring; omit malformed rows and fail closed during
        // app-owned ownership reconciliation instead of assuming an unknown symbol.
        val holdings = apiFactory.groww.holdings(bearer(token)).requirePayload("Groww holdings").holdings
            .mapNotNull { holding ->
                BrokerInventorySymbolPolicy.normalize(holding.tradingSymbol)?.let { symbol -> symbol to holding }
            }.toMap()
        val positions = apiFactory.groww.positions(bearer(token)).requirePayload("Groww positions").positions
            .filter { it.product.equals("CNC", true) }
            .mapNotNull { position ->
                BrokerInventorySymbolPolicy.normalize(position.tradingSymbol)?.let { symbol -> symbol to position }
            }.toMap()
        return BrokerInventorySnapshot(holdings, positions, now).also { brokerInventorySnapshot = it }
    }

    private suspend fun reconcilePositionWithBroker(
        token: String,
        position: ManagedPositionEntity,
        preferredMark: Double?,
        force: Boolean
    ): ManagedPositionEntity? {
        if (position.side != "LONG" || position.product != "CNC") return position
        if (!appOwned(position)) return position
        val inventory = brokerInventory(token, force)
        val symbol = position.symbol.uppercase(Locale.US)
        val holdingQty = inventory.holdings[symbol]?.quantity?.coerceAtLeast(0) ?: 0
        val brokerPosition = inventory.positions[symbol]
        val intradayQty = brokerPosition?.quantity?.coerceAtLeast(0) ?: 0
        val visibleQty = max(holdingQty, intradayQty)
        if (visibleQty == position.quantity) return position
        // Never rebase app-owned share quantities to an aggregate, externally editable
        // Groww position. Such a rebase could accidentally make third-party shares sellable.
        auditLogger.log("SAFETY", "RECONCILIATION_OWNERSHIP_MISMATCH", mapOf(
            "symbol" to symbol, "app_qty" to position.quantity, "broker_qty" to visibleQty
        ))
        return position

    }

    private suspend fun latestMultifyExitPrice(symbol: String, openedAtMs: Long): Double? =
        dao.allNow().asReversed().firstNotNullOfOrNull { event ->
            if (event.receivedAtMs < openedAtMs || !event.symbol.equals(symbol, true) || event.signalType != SignalType.BOOK_PROFIT.name) null
            else parser.parse(event.title, event.text, event.bigText).exitPrice
        }

    private fun buildExternalCloseTrade(
        position: ManagedPositionEntity,
        quantity: Int,
        exit: Double,
        reason: String,
        closeOrderId: String
    ): ManagedTradeEntity {
        val gross = (exit - position.entryPrice) * quantity
        val costs = estimatedCashCosts(position.entryPrice, exit, quantity)
        return ManagedTradeEntity(
            engine = position.engine, symbol = position.symbol, product = "CNC", side = "LONG",
            quantity = quantity, entryPrice = position.entryPrice, exitPrice = exit,
            grossPnl = gross, estimatedCosts = costs, netPnl = gross - costs, exitReason = reason,
            strategy = position.strategy, regime = position.regime, confidence = position.confidence,
            mfeRupees = max(0.0, position.maxFavourablePrice - position.entryPrice) * quantity,
            maeRupees = max(0.0, position.entryPrice - position.maxAdversePrice) * quantity,
            sourceEventId = position.sourceEventId, openOrderId = position.openOrderId,
            closeOrderId = closeOrderId,
            openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
        )
    }

    private fun parseGrowwTimestampMs(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(INDIA).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text, HIST_FORMAT).atZone(INDIA).toInstant().toEpochMilli() }.getOrNull()
    }

    private suspend fun growwOrderList(token: String, force: Boolean = false): List<OrderPayload> {
        val now = System.currentTimeMillis()
        orderListSnapshot?.takeIf { !force && now - it.atMs < 5_000L }?.let { return it.orders }
        val all = mutableListOf<OrderPayload>()
        for (page in 0 until 5) {
            val rows = apiFactory.groww.orderList(
                bearer(token), segment = "CASH", page = page, pageSize = 100
            ).requirePayload("Groww order list for GTT reconciliation").orderList
            all += rows
            if (rows.size < 100) break
        }
        orderListSnapshot = OrderListSnapshot(all, now)
        return all
    }

    private fun isMatchingGttChildOrder(order: OrderPayload, position: ManagedPositionEntity): Boolean =
        order.growwOrderId.isNotBlank() &&
            order.tradingSymbol.equals(position.symbol, true) &&
            order.transactionType.equals("SELL", true) &&
            order.product.equals("CNC", true) &&
            (order.quantity == null || order.quantity in 1..position.quantity)

    private suspend fun findTriggeredGttChildOrder(
        token: String,
        position: ManagedPositionEntity
    ): OrderPayload? {
        val orders = growwOrderList(token, force = false)
        val reference = position.smartOrderReferenceId
        val exact = if (!reference.isNullOrBlank()) {
            orders.filter { isMatchingGttChildOrder(it, position) && it.orderReferenceId == reference }
        } else emptyList()
        if (exact.size == 1) return exact.single()
        if (exact.size > 1) {
            auditLogger.log("GTT", "GTT_CHILD_ORDER_REFERENCE_AMBIGUOUS", mapOf(
                "symbol" to position.symbol, "gtt_id" to position.smartOrderId,
                "reference_id" to reference, "candidate_order_ids" to exact.map { it.growwOrderId }
            ))
            return null
        }
        val triggerAt = position.smartOrderTriggeredAtMs ?: position.smartOrderUpdatedAtMs ?: position.openedAtMs
        val fallback = orders.filter { order ->
            if (!isMatchingGttChildOrder(order, position)) return@filter false
            val createdAt = parseGrowwTimestampMs(order.createdAt) ?: return@filter false
            createdAt >= triggerAt - 60_000L && createdAt <= System.currentTimeMillis() + 10_000L &&
                createdAt >= position.openedAtMs
        }
        if (fallback.size == 1) return fallback.single()
        val auditKey = position.symbol.uppercase(Locale.US)
        val auditNow = System.currentTimeMillis()
        val auditLast = lastGttChildReconcileAuditAtMs[auditKey] ?: 0L
        if (auditNow - auditLast >= 30_000L) {
            lastGttChildReconcileAuditAtMs[auditKey] = auditNow
            auditLogger.log("GTT", if (fallback.isEmpty()) "GTT_CHILD_ORDER_NOT_YET_VISIBLE"
                else "GTT_CHILD_ORDER_MATCH_AMBIGUOUS", mapOf(
                "symbol" to position.symbol, "gtt_id" to position.smartOrderId,
                "reference_id" to reference, "triggered_at_ms" to triggerAt,
                "candidate_order_ids" to fallback.map { it.growwOrderId },
                "candidate_count" to fallback.size,
                "note" to "No SELL or new GTT is submitted while the broker-triggered child order is unresolved."
            ))
        }
        return null
    }

    private suspend fun fetchGttChildFillStats(
        token: String,
        orderId: String,
        symbol: String
    ): GttChildFillStats {
        val order = apiFactory.groww.orderStatus(
            bearer(token), orderId = orderId, segment = "CASH"
        ).requirePayload("GTT child order status $symbol")
        val trades = mutableListOf<OrderTradeRecord>()
        for (page in 0 until 5) {
            val rows = apiFactory.groww.orderTrades(
                bearer(token), orderId = orderId, segment = "CASH", page = page, pageSize = 50
            ).requirePayload("GTT child order trades $symbol").tradeList
            trades += rows
            if (rows.size < 50) break
        }
        val validTrades = trades.filter {
            it.quantity > 0 && it.price.isFinite() && it.price > 0.0 &&
                (it.tradeStatus.isBlank() || it.tradeStatus.uppercase(Locale.US) in setOf("COMPLETED", "EXECUTED", "TRADED")) &&
                (it.tradingSymbol.isBlank() || it.tradingSymbol.equals(symbol, true)) &&
                (it.transactionType.isBlank() || it.transactionType.equals("SELL", true)) &&
                (it.product.isBlank() || it.product.equals("CNC", true))
        }
        val tradeQuantity = validTrades.sumOf { it.quantity }
        val tradeAverage = if (tradeQuantity > 0) {
            validTrades.sumOf { it.price * it.quantity } / tradeQuantity.toDouble()
        } else null
        val filledQuantity = max(tradeQuantity, order.filledQuantity ?: 0)
        val averageFillPrice = tradeAverage ?: order.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 }
        val status = order.orderStatus.uppercase(Locale.US)
        val terminal = status in setOf("EXECUTED", "COMPLETED", "CANCELLED", "REJECTED", "FAILED", "EXPIRED") ||
            (order.remainingQuantity == 0 && filledQuantity > 0)
        if ((order.filledQuantity ?: 0) <= 0 && tradeQuantity == 0 && !terminal) {
            return GttChildFillStats(0, null, false, status, order)
        }
        return GttChildFillStats(filledQuantity, averageFillPrice, terminal, status, order)
    }

    private suspend fun reconcileTriggeredGttChildOrder(
        token: String,
        position: ManagedPositionEntity
    ): ManagedPositionEntity? {
        val pollKey = position.symbol.uppercase(Locale.US)
        val pollNow = System.currentTimeMillis()
        val lastPoll = lastGttChildPollAtMs[pollKey] ?: 0L
        if (pollNow - lastPoll < 5_000L) return null
        lastGttChildPollAtMs[pollKey] = pollNow
        val child = position.smartOrderChildOrderId?.let { id ->
            OrderPayload(growwOrderId = id)
        } ?: findTriggeredGttChildOrder(token, position)
        val childId = child?.growwOrderId?.takeIf { it.isNotBlank() }
        if (childId == null) {
            val pending = position.copy(
                status = "EXIT_PENDING_RECONCILIATION",
                smartOrderChildStatus = position.smartOrderChildStatus ?: "AWAITING_CHILD_ORDER"
            )
            managedDao.updatePosition(pending)
            return null
        }
        val stats = fetchGttChildFillStats(token, childId, position.symbol)
        val working = position.copy(
            status = "EXIT_PENDING_RECONCILIATION",
            smartOrderChildOrderId = childId,
            smartOrderChildStatus = stats.status
        )
        managedDao.updatePosition(working)
        if (!stats.terminal) {
            val auditKey = position.symbol.uppercase(Locale.US)
            val auditNow = System.currentTimeMillis()
            val auditLast = lastGttChildReconcileAuditAtMs[auditKey] ?: 0L
            if (auditNow - auditLast >= 30_000L) {
                lastGttChildReconcileAuditAtMs[auditKey] = auditNow
                auditLogger.log("GTT", "GTT_CHILD_ORDER_PENDING", mapOf(
                    "symbol" to position.symbol, "gtt_id" to position.smartOrderId,
                    "child_order_id" to childId, "status" to stats.status,
                    "filled_quantity" to stats.filledQuantity,
                    "requested_quantity" to position.quantity
                ))
            }
            return null
        }
        val filled = stats.filledQuantity
        if (filled > position.quantity) {
            auditLogger.log("SAFETY", "GTT_CHILD_FILL_EXCEEDS_APP_POSITION", mapOf(
                "symbol" to position.symbol, "child_order_id" to childId,
                "filled_quantity" to filled, "app_quantity" to position.quantity,
                "action" to "manual_review_no_additional_sell"
            ))
            return null
        }
        if (filled > 0 && (stats.averageFillPrice == null || !stats.averageFillPrice.isFinite() ||
                stats.averageFillPrice <= 0.0)) {
            auditLogger.log("GTT", "GTT_CHILD_FILL_PRICE_PENDING", mapOf(
                "symbol" to position.symbol, "child_order_id" to childId,
                "filled_quantity" to filled, "status" to stats.status
            ))
            return null
        }
        val reason = when (position.smartOrderMode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "COMMON_HARD_STOP_MINUS_2_GTT_EXIT"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "NET_050_TRAILING_GTT_EXIT"
            HoldingSmartOrderPolicy.MODE_RECOVERY_GTT -> "RECOVERY_GTT_EXIT"
            else -> "GTT_EXIT"
        }
        if (filled >= position.quantity && filled > 0) {
            val exit = stats.averageFillPrice!!
            val trade = buildExternalCloseTrade(position, position.quantity, exit, reason, childId)
            managedDao.recordFullExit(position.id, trade)
            val gross = trade.grossPnl
            val netReturnPct = if (position.entryPrice * position.quantity > 0.0)
                trade.netPnl / (position.entryPrice * position.quantity) * 100.0 else 0.0
            val trigger = position.smartOrderTriggerPrice
            val slippageBps = if (trigger != null && trigger > 0.0)
                ((trigger - exit) / trigger * 10_000.0).coerceAtLeast(0.0) else 0.0
            recentlyClosedSymbolsAtMs[position.symbol.uppercase(Locale.US)] = System.currentTimeMillis()
            brokerInventorySnapshot = null
            activeGttSnapshot = null
            orderListSnapshot = null
            lastTrailStateAuditAtMs.remove(position.id)
            lastSmartOrderStatusAtMs.remove(position.id)
            lastGttChildPollAtMs.remove(pollKey)
            lastGttChildReconcileAuditAtMs.remove(pollKey)
            auditLogger.log("HOLDING", "GTT_LONG_CLOSED", mapOf(
                "symbol" to position.symbol, "qty" to position.quantity,
                "exit" to exit, "gross_pnl" to gross, "estimated_costs" to trade.estimatedCosts,
                "net_pnl" to trade.netPnl, "net_return_pct" to netReturnPct,
                "trigger" to trigger, "execution_slippage_bps" to slippageBps,
                "slippage_allowance_bps" to NetProfitProtectionPolicy.SLIPPAGE_ALLOWANCE_BPS,
                "close_order_id" to childId, "gtt_id" to position.smartOrderId,
                "exit_reason" to reason, "gtt_status" to position.smartOrderStatus
            ))
            if (position.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT &&
                netReturnPct + 1e-9 < NetProfitProtectionPolicy.MIN_NET_RETURN_PCT) {
                auditLogger.log("TRAILING_STOP", "NET_PROFIT_SLIPPAGE_BREACH", mapOf(
                    "symbol" to position.symbol, "close_order_id" to childId,
                    "net_return_pct" to netReturnPct, "minimum_net_return_pct" to NetProfitProtectionPolicy.MIN_NET_RETURN_PCT,
                    "trigger" to trigger, "actual_average_fill" to exit, "slippage_bps" to slippageBps
                ))
            }
            return null
        }
        if (filled > 0) {
            val exit = stats.averageFillPrice!!
            val trade = buildExternalCloseTrade(position, filled, exit, reason + "_PARTIAL", childId)
            val remaining = position.quantity - filled
            val reopened = clearManagedGttState(working).copy(
                status = "OPEN", quantity = remaining,
                stopPrice = position.smartOrderTriggerPrice ?: position.stopPrice
                    ?: HardStopLossPolicy.stopPrice(position.entryPrice),
                lastPrice = position.lastPrice
            )
            managedDao.recordPartialExitAndReopen(reopened, trade)
            brokerInventorySnapshot = null
            activeGttSnapshot = null
            orderListSnapshot = null
            auditLogger.log("HOLDING", "GTT_PARTIAL_FILL_RECONCILED", mapOf(
                "symbol" to position.symbol, "child_order_id" to childId,
                "filled_quantity" to filled, "remaining_quantity" to remaining,
                "actual_average_fill" to exit, "gross_pnl" to trade.grossPnl,
                "estimated_costs" to trade.estimatedCosts, "net_pnl" to trade.netPnl,
                "next_action" to "rearm_remaining_quantity_after_broker_inventory_verification"
            ))
            return reopened
        }
        val reopened = clearManagedGttState(working).copy(
            status = "OPEN",
            stopPrice = position.smartOrderTriggerPrice ?: position.stopPrice
                ?: HardStopLossPolicy.stopPrice(position.entryPrice)
        )
        managedDao.updatePosition(reopened)
        brokerInventorySnapshot = null
        activeGttSnapshot = null
        orderListSnapshot = null
        auditLogger.log("GTT", "GTT_CHILD_ORDER_TERMINAL_NO_FILL_REPROTECT", mapOf(
            "symbol" to position.symbol, "child_order_id" to childId,
            "status" to stats.status, "action" to "rearm_or_local_emergency_fallback",
            "stop_price" to reopened.stopPrice
        ))
        return reopened
    }
    private suspend fun activeGttsBySymbol(token: String, force: Boolean = false): Map<String, List<SmartOrderPayload>> {
        val now = System.currentTimeMillis()
        activeGttSnapshot?.takeIf { !force && now - it.atMs < ACTIVE_GTT_REFRESH_MS }?.let { return it.bySymbol }

        val all = mutableListOf<SmartOrderPayload>()
        var complete = false
        for (page in 0 until MAX_ACTIVE_GTT_PAGES) {
            val orders = apiFactory.groww.listSmartOrders(
                bearer(token),
                segment = "CASH",
                smartOrderType = "GTT",
                status = "ACTIVE",
                page = page,
                pageSize = ACTIVE_GTT_PAGE_SIZE
            ).requirePayload("Active Groww GTT list").orders
            all += orders
            if (orders.size < ACTIVE_GTT_PAGE_SIZE) {
                complete = true
                break
            }
        }
        check(complete) { "Active Groww GTT list exceeded the safe pagination limit; portfolio import paused to avoid duplicate sell orders" }
        val grouped = all.filter { it.tradingSymbol.isNotBlank() }
            .groupBy { it.tradingSymbol.uppercase(Locale.US) }
        activeGttSnapshot = ActiveGttSnapshot(grouped, now)
        return grouped
    }

    private fun logPortfolioConflict(symbol: String, message: String, extra: Map<String, Any?> = emptyMap()) {
        val now = System.currentTimeMillis()
        val last = lastPortfolioConflictAuditAtMs[symbol] ?: 0L
        if (now - last < PORTFOLIO_CONFLICT_AUDIT_INTERVAL_MS) return
        lastPortfolioConflictAuditAtMs[symbol] = now
        auditLogger.log("PORTFOLIO_MONITOR", "EXISTING_GTT_CONFLICT", mapOf(
            "symbol" to symbol,
            "message" to message
        ) + extra)
    }

    private fun appOwned(position: ManagedPositionEntity): Boolean = AppPositionOwnershipPolicy.appOpened(
        engine = position.engine, side = position.side, product = position.product,
        openingOrderId = position.openOrderId, openingReference = position.openReferenceId,
        strategy = position.strategy, quantity = position.quantity
    )

    /**
     * Quarantine already-imported Groww holdings from prior releases. Never adopt new broker
     * holdings. If an older app build created a GTT against an external holding, only cancel
     * that GTT after the broker confirms our own MHS/MTG/MTR order reference.
     */
    private suspend fun quarantineExternalHoldings(token: String) {
        managedDao.openPositions().filterNot(::appOwned).forEach { position ->
            var working = position
            val gttId = working.smartOrderId
            if (!gttId.isNullOrBlank()) {
                val brokerOrder = runCatching {
                    apiFactory.groww.smartOrderStatus(
                        bearer(token), segment = "CASH", smartOrderType = "GTT", smartOrderId = gttId
                    ).requirePayload("Verify external holding legacy GTT ${position.symbol}")
                }.getOrNull()
                if (brokerOrder == null ||
                    !brokerOrder.tradingSymbol.equals(position.symbol, true) ||
                    !AppPositionOwnershipPolicy.isAppProtectionReference(brokerOrder.referenceId)
                ) {
                    auditLogger.log("SAFETY", "EXTERNAL_GTT_REQUIRES_MANUAL_REVIEW", mapOf(
                        "symbol" to position.symbol, "gtt_id" to gttId,
                        "message" to "Cannot confirm app-generated protection GTT; no broker order modified"
                    ))
                    return@forEach
                }
                working = cancelKnownPositionGtt(token, working, "REMOVE_UNOWNED_PORTFOLIO_PROTECTION", clearIfTerminal = true)
                if (!working.smartOrderId.isNullOrBlank()) {
                    auditLogger.log("SAFETY", "EXTERNAL_GTT_CANCEL_RETRY", mapOf(
                        "symbol" to position.symbol, "gtt_id" to working.smartOrderId
                    ))
                    return@forEach
                }
            }
            managedDao.deletePosition(working.id)
            auditLogger.log("SAFETY", "EXTERNAL_HOLDING_EXCLUDED_FROM_SELL_MONITOR", mapOf(
                "symbol" to position.symbol, "engine" to position.engine,
                "broker_order_untouched" to gttId.isNullOrBlank()
            ))
        }
    }

    /** Broker inventory must consist entirely of this app's one tracked BUY for the symbol. */
    private suspend fun safeAppSellScope(
        token: String, position: ManagedPositionEntity,
        inventory: BrokerInventorySnapshot? = null,
        forceGttRefresh: Boolean = false
    ): Boolean {
        if (!appOwned(position)) return false
        val openSameSymbol = managedDao.openPositions().filter { it.symbol.equals(position.symbol, true) }
        if (openSameSymbol.size != 1 || openSameSymbol.single().id != position.id) return false
        val broker = inventory ?: brokerInventory(token, force = true)
        val key = position.symbol.uppercase(Locale.US)
        val demat = broker.holdings[key]?.quantity ?: 0
        val cnc = broker.positions[key]?.quantity ?: 0
        if (!AppPositionOwnershipPolicy.exclusiveBrokerQuantity(position.quantity, demat, cnc)) return false
        val otherActiveGtt = activeGttsBySymbol(token, force = forceGttRefresh).getOrDefault(key, emptyList())
            .any { it.smartOrderId != position.smartOrderId }
        return !otherActiveGtt
    }

    private suspend fun portfolioMonitorCount(inventory: BrokerInventorySnapshot? = brokerInventorySnapshot): Int {
        // Broker holdings NOT opened by this app must never count as managed holdings.
        return managedDao.openPositions().count(::appOwned).also { activePortfolioMonitorCount = it }
    }

    suspend fun monitorManagedPositions(): Int {
        val token = ensureToken() ?: return portfolioMonitorCount()
        val marketOpen = marketSession() == "OPEN"
        val now = System.currentTimeMillis()
        val managedOpen = managedDao.openPositions().any(::appOwned)
        val pendingExitAtStart = managedDao.pendingExitReconciliations()
        val confirmedEmpty = brokerInventorySnapshot?.takeIf { now - it.atMs < 5L * 60_000L }
            ?.let { it.holdings.isEmpty() && it.positions.isEmpty() } == true &&
            activeGttSnapshot?.takeIf { now - it.atMs < 5L * 60_000L }
                ?.bySymbol?.values?.all { it.isEmpty() } == true
        // A verified empty portfolio needs a slow inventory heartbeat, not fresh holdings,
        // positions and GTT calls every five seconds. Never apply this to any tracked
        // position, pending BUY/GTT, unknown broker state or order in flight.
        val noTrackedPositions = !managedOpen && pendingExitAtStart.isEmpty() && managedDao.openPositions().isEmpty()
        val pendingEntry = fixedAutoBuyOrderStore.all().any { !ForecastAutoBuyPolicy.isTerminal(it.status) } ||
            forecastAutoBuyOrderStore.all().any { !ForecastAutoBuyPolicy.isTerminal(it.status) }
        if (marketOpen && noTrackedPositions && !pendingEntry && !marketCriticalTradeInFlight &&
            confirmedEmpty && brokerInventorySnapshot != null &&
            now - brokerInventorySnapshot!!.atMs < EMPTY_PORTFOLIO_BROKER_RECHECK_MS
        ) {
            val auditAt = lastIdlePortfolioAuditAtMs
            if (now - auditAt >= 60_000L) {
                lastIdlePortfolioAuditAtMs = now
                auditLogger.logAsync("PORTFOLIO_MONITOR", "VERIFIED_EMPTY_BROKER_POLL_SKIPPED", mapOf(
                    "next_reverify_after_ms" to EMPTY_PORTFOLIO_BROKER_RECHECK_MS,
                    "app_owned_holdings" to 0,
                    "pending_entries" to 0,
                    "active_gtts" to 0,
                    "protective_sell_priority_preserved" to true
                ))
            }
            return portfolioMonitorCount()
        }
        val reconcileGap = if (!marketOpen && !managedOpen && pendingExitAtStart.isEmpty() && confirmedEmpty) 5L * 60_000L
            else CLOSED_SESSION_RECONCILE_MS
        if (!marketOpen && now - lastClosedSessionReconcileAtMs < reconcileGap) {
            return portfolioMonitorCount()
        }
        if (!marketOpen) lastClosedSessionReconcileAtMs = now
        // Maintain external-GTT quarantine when any exposure is possible. This check
        // must precede reconciliation, but need not run every service heartbeat after close.
        quarantineExternalHoldings(token)

        val inventory = runCatching { brokerInventory(token, force = !marketOpen) }
            .onFailure {
                auditLogger.log("PORTFOLIO_MONITOR", "BROKER_INVENTORY_ERROR", mapOf(
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }
            .getOrElse { return portfolioMonitorCount() }

        val blockedSymbols = mutableSetOf<String>()
        val gttReconciliationCandidates = (
            managedDao.openPositions().filter { appOwned(it) && !it.smartOrderId.isNullOrBlank() } +
                managedDao.pendingExitReconciliations()
            ).distinctBy { it.id }
        for (position in gttReconciliationCandidates) {
            try {
                val after = finalizeCompletedGttIfNeeded(token, position)
                if (after == null || after.status == "EXIT_PENDING_RECONCILIATION" ||
                    after.quantity != position.quantity || after.smartOrderId != position.smartOrderId) {
                    blockedSymbols += position.symbol.uppercase(Locale.US)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                blockedSymbols += position.symbol.uppercase(Locale.US)
                auditLogger.log("GTT", "GTT_CHILD_RECONCILIATION_ERROR", mapOf(
                    "symbol" to position.symbol, "gtt_id" to position.smartOrderId,
                    "message" to (t.message ?: t.javaClass.simpleName)
                ))
            }
        }
        val activeGttsResult = runCatching { activeGttsBySymbol(token) }
        if (activeGttsResult.isSuccess) {
            val activeGtts = activeGttsResult.getOrThrow()
            val open = managedDao.openPositions()
            for (position in open) {
                val symbol = position.symbol.uppercase(Locale.US)
                val conflict = activeGtts[symbol].orEmpty().any { it.smartOrderId != position.smartOrderId }
                val exclusive = safeAppSellScope(token, position, inventory)
                if (conflict || !exclusive) {
                    blockedSymbols += symbol
                    logPortfolioConflict(symbol, "Sell protection BLOCKED: not exclusively app-owned or another broker GTT exists", mapOf(
                        "app_qty" to position.quantity,
                        "broker_holding_qty" to inventory.holdings[symbol]?.quantity,
                        "broker_cnc_qty" to inventory.positions[symbol]?.quantity,
                        "different_gtt_present" to conflict
                    ))
                    // An app-created protection GTT might still fire against fungible external shares.
                    // Cancel only an explicitly verified app-created GTT, never a user's GTT.
                    val knownId = position.smartOrderId
                    if (!knownId.isNullOrBlank()) {
                        val gtt = activeGtts[symbol].orEmpty().firstOrNull { it.smartOrderId == knownId }
                        if (gtt != null && AppPositionOwnershipPolicy.isAppProtectionReference(gtt.referenceId)) {
                            runCatching { cancelKnownPositionGtt(token, position, "OWNERSHIP_CONFLICT_STOP_PROTECTION", clearIfTerminal = false) }
                        } else {
                            auditLogger.log("SAFETY", "GTT_OWNERSHIP_CONFLICT_MANUAL_REVIEW", mapOf(
                                "symbol" to symbol, "gtt_id" to knownId
                            ))
                        }
                    }
                }
            }
        } else {
            val error = activeGttsResult.exceptionOrNull()
            auditLogger.log("PORTFOLIO_MONITOR", "ACTIVE_GTT_DISCOVERY_ERROR", mapOf(
                "message" to (error?.message ?: error?.javaClass?.simpleName ?: "Active GTT discovery failed"),
                "note" to "No automatic SELL or new protective GTT is permitted without GTT conflict verification."
            ))
            // If broker GTT discovery is unavailable, do not create/modify sell orders for an
            // unprotected local row: an externally created GTT might already cover the same shares.
            managedDao.openPositions().forEach { blockedSymbols += it.symbol.uppercase(Locale.US) }
        }

        val open = managedDao.openPositions()
        val reconciled = mutableListOf<ManagedPositionEntity>()
        for (position in open) {
            if (!appOwned(position) || position.symbol.uppercase(Locale.US) in blockedSymbols) continue
            try {
                if (position.side != "LONG") {
                    continue
                }
                val afterGtt = finalizeCompletedGttIfNeeded(token, position) ?: continue
                reconcilePositionWithBroker(token, afterGtt, preferredMark = null, force = !marketOpen)?.let(reconciled::add)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                auditLogger.log("HOLDING", "RECONCILE_ERROR", mapOf(
                    "symbol" to position.symbol,
                    "type" to t.javaClass.simpleName,
                    "message" to (t.message ?: t.javaClass.simpleName)
                ))
            }
        }

        val marks = if (reconciled.isNotEmpty()) runCatching { batchManagedLtps(token, reconciled) }
            .onFailure {
                auditLogger.log("MARKET_DATA", "BATCH_LTP_ERROR", mapOf(
                    "symbols" to reconciled.map { it.symbol },
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrDefault(emptyMap()) else emptyMap()

        val unpricedSymbols = reconciled.filter { marks[it.symbol.uppercase(Locale.US)] == null }
            .map { it.symbol.uppercase(Locale.US) }
        blockedSymbols += unpricedSymbols
        unpricedSymbols.forEach { symbol ->
            logPortfolioConflict(symbol, "Fresh LTP unavailable; this pass will not create or adjust sell orders using a stale price.")
        }

        val marked = reconciled.map { position ->
            val ltp = marks[position.symbol.uppercase(Locale.US)]
            if (ltp != null && ltp.isFinite() && ltp > 0.0 && abs(ltp - position.lastPrice) > 1e-9) {
                position.copy(lastPrice = ltp, lastEvaluatedAtMs = System.currentTimeMillis()).also {
                    managedDao.updatePosition(it)
                }
            } else position
        }

        if (!marketOpen) {
            // Keep every managed LONG protected overnight. Existing profitable trailing GTTs are
            // preserved; an unarmed holding receives the common -2% server-side hard stop. This
            // also self-recovers protection after an app/service restart or a prior GTT failure.
            var hardStopReady = 0
            var hardStopFailures = 0
            for (position in marked) {
                if (position.side != "LONG" || position.symbol.uppercase(Locale.US) in blockedSymbols) continue
                val result = runCatching { ensureHardStopProtection(token, position, "AFTER_HOURS_HARD_STOP") }
                if (result.isSuccess) hardStopReady++ else {
                    hardStopFailures++
                    val error = result.exceptionOrNull()
                    auditLogger.log("GTT", "AFTER_HOURS_HARD_STOP_ERROR", mapOf(
                        "symbol" to position.symbol,
                        "message" to (error?.message ?: error?.javaClass?.simpleName ?: "Unknown error")
                    ))
                }
            }
            auditLogger.log("GTT", "OVERNIGHT_PROTECTION_PRESERVED", mapOf(
                "tracked_holdings" to marked.size,
                "hard_stop_or_trail_ready" to hardStopReady,
                "hard_stop_failures" to hardStopFailures,
                "hard_stop_pct" to -HardStopLossPolicy.MAX_LOSS_PCT,
                "hard_stop_gtts" to marked.count { it.smartOrderMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT },
                "armed_trailing_gtts" to marked.count { it.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT },
                "legacy_recovery_rows" to marked.count { it.smartOrderMode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT }
            ))
            return portfolioMonitorCount(inventory)
        }

        // One-time migration for positions left in the legacy recovery mode by older builds.
        // Cancel that recovery GTT first; only a successfully cleared row is handed back to the
        // shared trailing monitor, preventing duplicate SELL orders.
        val trailingCandidates = coroutineScope {
            marked.map { position ->
                async {
                    if (position.smartOrderMode != HoldingSmartOrderPolicy.MODE_RECOVERY_GTT ||
                        position.symbol.uppercase(Locale.US) in blockedSymbols
                    ) return@async position

                    val migrated = runCatching {
                        cancelKnownPositionGtt(
                            token,
                            position,
                            "RESTORE_PERSISTENT_TRAILING_FROM_LEGACY_RECOVERY",
                            clearIfTerminal = false
                        )
                    }.onFailure {
                        auditLogger.log("GTT", "RECOVERY_TO_TRAIL_MIGRATION_ERROR", mapOf(
                            "symbol" to position.symbol,
                            "message" to (it.message ?: it.javaClass.simpleName)
                        ))
                    }.getOrDefault(position)

                    if (migrated.smartOrderId == null) {
                        auditLogger.log("GTT", "RECOVERY_TO_TRAIL_MIGRATED", mapOf(
                            "symbol" to migrated.symbol,
                            "high_water" to migrated.maxFavourablePrice,
                            "entry" to migrated.entryPrice
                        ))
                    }
                    migrated
                }
            }.awaitAll()
        }

        val liveTrailing = trailingCandidates.filter {
            it.smartOrderMode != HoldingSmartOrderPolicy.MODE_RECOVERY_GTT &&
                it.symbol.uppercase(Locale.US) !in blockedSymbols
        }
        if (liveTrailing.isNotEmpty()) {
            coroutineScope {
                liveTrailing.map { position ->
                    async {
                        try {
                            val ltp = marks[position.symbol.uppercase(Locale.US)] ?: position.lastPrice
                            monitorLongHolding(position, token, ltp)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (t: Throwable) {
                            auditLogger.log("HOLDING", "MONITOR_ERROR", mapOf(
                                "symbol" to position.symbol,
                                "type" to t.javaClass.simpleName,
                                "message" to (t.message ?: t.javaClass.simpleName)
                            ))
                        }
                    }
                }.awaitAll()
            }
        }
        return portfolioMonitorCount(inventory)
    }

    private suspend fun batchManagedLtps(
        token: String,
        positions: List<ManagedPositionEntity>
    ): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        positions.map { it.symbol.uppercase(Locale.US) }.distinct().chunked(50).forEach { chunk ->
            val exchangeSymbols = chunk.joinToString(",") { "NSE_$it" }
            val payload = withBulkMarketDataSlot {
                apiFactory.groww.ltp(
                    bearer(token),
                    exchangeSymbols = exchangeSymbols
                ).requirePayload("Batch holding LTP")
            }
            payload.forEach { (key, value) ->
                if (value.isFinite() && value > 0.0) {
                    result[key.removePrefix("NSE_").uppercase(Locale.US)] = value
                }
            }
        }
        preferences.recordMarketData()
        return result
    }

    private suspend fun ensureHardStopProtection(
        token: String,
        position: ManagedPositionEntity,
        reason: String
    ): ManagedPositionEntity {
        if (position.side != "LONG" || position.product != "CNC") return position
        val hardStop = HardStopLossPolicy.stopPrice(position.entryPrice)
        val existingTrigger = position.smartOrderTriggerPrice
        val existingProtected = HoldingSmartOrderPolicy.isServerProtected(position.smartOrderMode, position.smartOrderId)
        if (existingProtected && position.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT &&
            existingTrigger != null && existingTrigger + SMART_ORDER_PRICE_EPSILON >= hardStop
        ) return position
        if (existingProtected && position.smartOrderMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT &&
            existingTrigger != null && existingTrigger + SMART_ORDER_PRICE_EPSILON >= hardStop
        ) return position

        var working = position
        if (!working.smartOrderId.isNullOrBlank()) {
            working = cancelKnownPositionGtt(token, working, "$reason-REPLACE_EXISTING_GTT", clearIfTerminal = false)
            if (!working.smartOrderId.isNullOrBlank()) return working
        }
        working = clearManagedGttState(working).copy(
            status = "OPEN",
            stopPrice = hardStop,
            smartOrderUpdatedAtMs = System.currentTimeMillis(),
            lastEvaluatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(working)
        return createPositionGtt(token, working, hardStop, "DOWN", HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)
    }

    private suspend fun monitorLongHolding(position: ManagedPositionEntity, token: String, ltp: Double) {
        // Common CNC LONG protection. The -2% hard stop remains until estimated net +0.50%
        // plus a 25 bps execution reserve has been reached. Existing 0.10% ratchet steps remain.
        // The BUY submission path is deliberately unchanged.
        val decisionStartedNs = SystemClock.elapsedRealtimeNanos()
        val previousHighWater = position.maxFavourablePrice
        val highWater = max(position.maxFavourablePrice, ltp)
        val lowWater = min(position.maxAdversePrice, ltp)
        val hardStopPrice = HardStopLossPolicy.stopPrice(position.entryPrice)
        val netDecision = NetProfitProtectionPolicy.evaluate(position.entryPrice, position.quantity, highWater)
        val legacyTrailing = position.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT
        val desiredTrailGainPct = netDecision.netTargetPct.takeIf { netDecision.armed } ?: position.netProfitTargetPct
        val desiredTrailPrice = netDecision.triggerPrice.takeIf { netDecision.armed }
            ?: position.smartOrderTriggerPrice.takeIf { legacyTrailing }
        val desiredMode = if (netDecision.armed || legacyTrailing) {
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT
        } else HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT
        val previousProtected = listOfNotNull(
            position.smartOrderTriggerPrice?.takeIf {
                position.smartOrderMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT ||
                    position.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT
            },
            position.stopPrice
        ).maxOrNull()
        val desiredProtectedPrice = maxOf(
            hardStopPrice,
            desiredTrailPrice ?: hardStopPrice,
            previousProtected ?: hardStopPrice
        )
        val netFloorPrice = netDecision.netFloorPrice.takeIf { netDecision.armed } ?: position.netProfitFloorPrice

        var working = position.copy(
            lastPrice = ltp,
            maxFavourablePrice = highWater,
            maxAdversePrice = lowWater,
            stopPrice = previousProtected ?: hardStopPrice,
            trailingArmPct = NetProfitProtectionPolicy.MIN_NET_RETURN_PCT,
            trailingLastRatchetPrice = desiredTrailPrice ?: position.trailingLastRatchetPrice,
            netProfitFloorPrice = netFloorPrice,
            netProfitTargetPct = desiredTrailGainPct,
            lastEvaluatedAtMs = System.currentTimeMillis()
        )

        val serverProtected = HoldingSmartOrderPolicy.isServerProtected(
            working.smartOrderMode,
            working.smartOrderId
        ) && working.smartOrderMode in setOf(
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT
        )

        // If the server-side stop is missing and price is already through the required floor,
        // exit immediately rather than creating a trigger above current price after the fact.
        if (!serverProtected && ltp <= desiredProtectedPrice + SMART_ORDER_PRICE_EPSILON) {
            working = working.copy(stopPrice = desiredProtectedPrice)
            managedDao.updatePosition(working)
            auditTrailingState(working, ltp, desiredTrailGainPct, force = true)
            closeLongHolding(
                token,
                working,
                ltp,
                if (desiredMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)
                    "COMMON_HARD_STOP_MINUS_2_LOCAL_FALLBACK"
                else "NET_050_TRAILING_GTT_LOCAL_FALLBACK",
                decisionStartedNs
            )
            return
        }

        working = if (serverProtected) {
            val currentServerStop = working.smartOrderTriggerPrice ?: working.stopPrice ?: desiredProtectedPrice
            val modeUpgradeNeeded = desiredMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT &&
                working.smartOrderMode != HoldingSmartOrderPolicy.MODE_TRAILING_GTT
            if (desiredProtectedPrice > currentServerStop + SMART_ORDER_PRICE_EPSILON || modeUpgradeNeeded) {
                // A stale lower GTT must be cancelled before a direct exit if price already crossed
                // the newly required higher stop. This prevents duplicate SELL orders.
                if (ltp <= desiredProtectedPrice + SMART_ORDER_PRICE_EPSILON) {
                    val cancelled = cancelKnownPositionGtt(token, working, "STALE_PROTECTION_BEFORE_DIRECT_EXIT", clearIfTerminal = false)
                    if (cancelled.smartOrderId == null) {
                        val protected = cancelled.copy(stopPrice = desiredProtectedPrice)
                        managedDao.updatePosition(protected)
                        auditTrailingState(protected, ltp, desiredTrailGainPct, force = true)
                        closeLongHolding(
                            token,
                            protected,
                            ltp,
                            if (desiredMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)
                                "COMMON_HARD_STOP_MINUS_2_STALE_GTT_FALLBACK"
                            else "NET_050_TRAILING_GTT_STALE_PROTECTION_FALLBACK",
                            decisionStartedNs
                        )
                        return
                    }
                    cancelled
                } else {
                    runCatching {
                        modifyPositionGtt(token, working, desiredProtectedPrice, "DOWN", desiredMode)
                    }.onFailure {
                        auditLogger.log("GTT", "PROTECTIVE_GTT_RATCHET_FAILED", mapOf(
                            "symbol" to working.symbol,
                            "gtt_id" to working.smartOrderId,
                            "old_trigger" to currentServerStop,
                            "desired_trigger" to desiredProtectedPrice,
                            "desired_mode" to desiredMode,
                            "message" to (it.message ?: it.javaClass.simpleName)
                        ))
                    }.getOrDefault(working.copy(stopPrice = currentServerStop))
                }
            } else {
                working.copy(stopPrice = currentServerStop)
            }
        } else {
            val localProtected = working.copy(stopPrice = desiredProtectedPrice)
            runCatching {
                createPositionGtt(token, localProtected, desiredProtectedPrice, "DOWN", desiredMode)
            }.onFailure {
                if (it !is BrokerExecutionRetryGuard.Deferred) auditLogger.log("GTT", "PROTECTIVE_GTT_CREATE_FAILED_LOCAL_FALLBACK_ACTIVE", mapOf(
                    "symbol" to working.symbol,
                    "desired_trigger" to desiredProtectedPrice,
                    "desired_mode" to desiredMode,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrDefault(localProtected)
        }

        managedDao.updatePosition(working)
        val activeStop = working.smartOrderTriggerPrice ?: working.stopPrice
        val nowServerProtected = HoldingSmartOrderPolicy.isServerProtected(
            working.smartOrderMode,
            working.smartOrderId
        ) && working.smartOrderMode in setOf(
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT
        )

        if (activeStop != null && ltp <= activeStop + SMART_ORDER_PRICE_EPSILON) {
            if (nowServerProtected) {
                val refreshed = refreshKnownSmartOrderStatus(token, working, force = true)
                when (refreshed.smartOrderStatus?.uppercase(Locale.US)) {
                    "FAILED", "CANCELLED", "EXPIRED" -> {
                        val cleared = refreshed.copy(
                            smartOrderId = null,
                            smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE,
                            smartOrderTriggerPrice = null,
                            smartOrderDirection = null,
                            smartOrderStatus = null,
                            smartOrderUpdatedAtMs = System.currentTimeMillis(),
                            stopPrice = activeStop
                        )
                        managedDao.updatePosition(cleared)
                        auditTrailingState(cleared, ltp, desiredTrailGainPct, force = true)
                        closeLongHolding(
                            token,
                            cleared,
                            ltp,
                            if (working.smartOrderMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)
                                "COMMON_HARD_STOP_MINUS_2_GTT_FAILED_FALLBACK"
                            else "NET_050_TRAILING_GTT_FAILED_FALLBACK",
                            decisionStartedNs
                        )
                    }
                    else -> auditLogger.log("GTT", "PROTECTIVE_GTT_TRIGGER_EXPECTED", mapOf(
                        "symbol" to working.symbol,
                        "gtt_id" to working.smartOrderId,
                        "ltp" to ltp,
                        "trigger" to activeStop,
                        "mode" to working.smartOrderMode,
                        "status" to refreshed.smartOrderStatus
                    ))
                }
            } else {
                auditTrailingState(working, ltp, desiredTrailGainPct, force = true)
                closeLongHolding(
                    token,
                    working,
                    ltp,
                    if (desiredMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)
                        "COMMON_HARD_STOP_MINUS_2_LOCAL_FALLBACK"
                    else "LONG_TRAILING_PROFIT_STOP_V7_1_LOCAL_FALLBACK",
                    decisionStartedNs
                )
            }
            return
        }

        val stopRaised = previousProtected == null ||
            (activeStop != null && activeStop > previousProtected + SMART_ORDER_PRICE_EPSILON)
        val trailJustArmed = desiredMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT &&
            position.smartOrderMode != HoldingSmartOrderPolicy.MODE_TRAILING_GTT
        if (stopRaised || trailJustArmed) {
            val event = when {
                desiredMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT && previousProtected == null -> "HARD_STOP_MINUS_2_ARMED"
                trailJustArmed -> "NET_050_TRAIL_ARMED"
                else -> "NET_PROFIT_STEP_RATCHET"
            }
            auditLogger.log("TRAILING_STOP", event, mapOf(
                "symbol" to working.symbol,
                "engine" to working.engine,
                "entry" to working.entryPrice,
                "ltp" to ltp,
                "hard_stop_pct" to -HardStopLossPolicy.MAX_LOSS_PCT,
                "hard_stop_price" to hardStopPrice,
                "old_high_water" to previousHighWater,
                "new_high_water" to highWater,
                "high_water_gain_pct" to SteppedTrailingPolicy.gainPct(working.entryPrice, highWater),
                "net_profit_target_pct" to working.netProfitTargetPct,
                "net_profit_floor_price" to working.netProfitFloorPrice,
                "net_profit_trigger_price" to activeStop,
                "net_slippage_allowance_bps" to NetProfitProtectionPolicy.SLIPPAGE_ALLOWANCE_BPS,
                "protected_gain_pct" to activeStop?.let { SteppedTrailingPolicy.gainPct(working.entryPrice, it) },
                "active_stop" to activeStop,
                "gtt_id" to working.smartOrderId,
                "gtt_mode" to working.smartOrderMode,
                "next_trigger_gain_pct" to activeStop?.takeIf { it > working.entryPrice }?.let {
                    SteppedTrailingPolicy.nextTriggerGainPct(SteppedTrailingPolicy.gainPct(working.entryPrice, it))
                }
            ))
        }
        auditTrailingState(working, ltp, desiredTrailGainPct, force = stopRaised || trailJustArmed)
    }

    private suspend fun createPositionGtt(
        token: String,
        position: ManagedPositionEntity,
        triggerPrice: Double,
        direction: String,
        mode: String
    ): ManagedPositionEntity {
        require(position.side == "LONG" && position.product == "CNC") { "GTT protection is only for tracked CNC LONG holdings" }
        require(safeAppSellScope(token, position, forceGttRefresh = true)) { "GTT SELL blocked: ${position.symbol} is not exclusively app-bought" }
        require(direction in setOf("UP", "DOWN"))
        require(mode in setOf(
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT,
            HoldingSmartOrderPolicy.MODE_RECOVERY_GTT
        ))
        require(position.smartOrderId.isNullOrBlank()) { "Refusing to create a second GTT for ${position.symbol}" }
        val gttBlockedUntil = brokerRetryGuard.gttBlockedUntil(position.symbol)
        if (gttBlockedUntil > System.currentTimeMillis()) {
            throw BrokerExecutionRetryGuard.Deferred("GTT on ${position.symbol} is on broker-rejection cooldown")
        }
        val roundedTrigger = roundSmartOrderPrice(triggerPrice)
        val referencePrefix = when (mode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "MHS"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "MTG"
            else -> "MTR"
        }
        val reference = stableRef(referencePrefix, "${position.id}-${position.symbol}-${position.openedAtMs}")
        val postTriggerOrder = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
            GttOrder(orderType = "LIMIT", price = smartPrice(roundedTrigger), transactionType = "SELL")
        else
            GttOrder(orderType = "MARKET", price = null, transactionType = "SELL")
        val payload = try {
            apiFactory.groww.createGtt(
                bearer(token),
                GttCreateRequest(
                    referenceId = reference,
                    tradingSymbol = position.symbol,
                    quantity = position.quantity,
                    triggerPrice = smartPrice(roundedTrigger),
                    triggerDirection = direction,
                    order = postTriggerOrder
                )
            ).requirePayload("Create GTT ${position.symbol}")
        } catch (t: Throwable) {
            val rejected = t.message.orEmpty().contains("HTTP 400") ||
                BrokerExecutionSafetyPolicy.isInsufficientShares(t.message.orEmpty())
            val until = brokerRetryGuard.blockGtt(position.symbol, rejected)
            auditLogger.log("GTT", "GTT_CREATE_COOLDOWN_APPLIED", mapOf(
                "symbol" to position.symbol, "until_ms" to until,
                "reason" to (t.message ?: t.javaClass.simpleName)
            ))
            throw t
        }
        brokerRetryGuard.clearGtt(position.symbol)
        require(payload.smartOrderId.isNotBlank()) { "Groww did not return a GTT id for ${position.symbol}" }
        activeGttSnapshot = null
        orderListSnapshot = null
        val now = System.currentTimeMillis()
        val updated = position.copy(
            smartOrderId = payload.smartOrderId,
            smartOrderMode = mode,
            smartOrderTriggerPrice = roundedTrigger,
            smartOrderDirection = direction,
            smartOrderStatus = payload.status.ifBlank { "ACTIVE" },
            smartOrderUpdatedAtMs = now,
            smartOrderReferenceId = reference,
            smartOrderTriggeredAtMs = null,
            smartOrderChildOrderId = null,
            smartOrderChildStatus = null,
            netProfitFloorPrice = position.netProfitFloorPrice.takeIf { mode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT },
            netProfitTargetPct = position.netProfitTargetPct.takeIf { mode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT },
            stopPrice = if (mode in setOf(HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, HoldingSmartOrderPolicy.MODE_TRAILING_GTT)) roundedTrigger else null,
            targetPrice = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT) roundedTrigger else position.targetPrice,
            lastEvaluatedAtMs = now
        )
        managedDao.updatePosition(updated)
        val createdEvent = when (mode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "HARD_STOP_GTT_CREATED"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "TRAIL_GTT_CREATED"
            else -> "RECOVERY_GTT_CREATED"
        }
        auditLogger.log("GTT", createdEvent, mapOf(
            "symbol" to position.symbol,
            "gtt_id" to payload.smartOrderId,
            "reference_id" to reference,
            "quantity" to position.quantity,
            "trigger" to roundedTrigger,
            "direction" to direction,
            "transaction" to "SELL",
            "product" to "CNC",
            "post_trigger_order_type" to postTriggerOrder.orderType,
            "post_trigger_limit_price" to postTriggerOrder.price,
            "mode" to mode,
            "status" to updated.smartOrderStatus
        ))
        return updated
    }

    private suspend fun modifyPositionGtt(
        token: String,
        position: ManagedPositionEntity,
        triggerPrice: Double,
        direction: String,
        mode: String
    ): ManagedPositionEntity {
        require(mode in setOf(
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT,
            HoldingSmartOrderPolicy.MODE_RECOVERY_GTT
        ))
        require(safeAppSellScope(token, position, forceGttRefresh = true)) { "GTT modification blocked: ownership not exclusive" }
        val id = position.smartOrderId ?: return createPositionGtt(token, position, triggerPrice, direction, mode)
        if (brokerRetryGuard.gttBlockedUntil(position.symbol) > System.currentTimeMillis()) {
            throw BrokerExecutionRetryGuard.Deferred("GTT modification deferred on ${position.symbol}")
        }
        val roundedTrigger = roundSmartOrderPrice(triggerPrice)
        val postTriggerOrder = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
            GttOrder(orderType = "LIMIT", price = smartPrice(roundedTrigger), transactionType = "SELL")
        else
            GttOrder(orderType = "MARKET", price = null, transactionType = "SELL")
        val payload = try {
            apiFactory.groww.modifyGtt(
                bearer(token),
                id,
                GttModifyRequest(
                    quantity = position.quantity,
                    triggerPrice = smartPrice(roundedTrigger),
                    triggerDirection = direction,
                    order = postTriggerOrder
                )
            ).requirePayload("Modify GTT ${position.symbol}")
        } catch (t: Throwable) {
            val rejected = t.message.orEmpty().contains("HTTP 400") ||
                BrokerExecutionSafetyPolicy.isInsufficientShares(t.message.orEmpty())
            val until = brokerRetryGuard.blockGtt(position.symbol, rejected)
            auditLogger.log("GTT", "GTT_MODIFY_COOLDOWN_APPLIED", mapOf(
                "symbol" to position.symbol, "until_ms" to until
            ))
            throw t
        }
        brokerRetryGuard.clearGtt(position.symbol)
        activeGttSnapshot = null
        orderListSnapshot = null
        val now = System.currentTimeMillis()
        val referencePrefix = when (mode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "MHS"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "MTG"
            else -> "MTR"
        }
        val updated = position.copy(
            smartOrderMode = mode,
            smartOrderTriggerPrice = roundedTrigger,
            smartOrderDirection = direction,
            smartOrderStatus = payload.status.ifBlank { position.smartOrderStatus ?: "ACTIVE" },
            smartOrderUpdatedAtMs = now,
            smartOrderReferenceId = position.smartOrderReferenceId ?: stableRef(referencePrefix, "${position.id}-${position.symbol}-${position.openedAtMs}"),
            smartOrderTriggeredAtMs = null,
            smartOrderChildOrderId = null,
            smartOrderChildStatus = null,
            netProfitFloorPrice = position.netProfitFloorPrice.takeIf { mode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT },
            netProfitTargetPct = position.netProfitTargetPct.takeIf { mode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT },
            stopPrice = if (mode in setOf(HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, HoldingSmartOrderPolicy.MODE_TRAILING_GTT)) roundedTrigger else null,
            targetPrice = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT) roundedTrigger else position.targetPrice,
            lastEvaluatedAtMs = now
        )
        managedDao.updatePosition(updated)
        val updatedEvent = when (mode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "HARD_STOP_GTT_UPDATED"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "TRAIL_GTT_RATCHETED"
            else -> "RECOVERY_GTT_UPDATED"
        }
        auditLogger.log("GTT", updatedEvent, mapOf(
            "symbol" to position.symbol,
            "gtt_id" to id,
            "old_trigger" to position.smartOrderTriggerPrice,
            "new_trigger" to roundedTrigger,
            "direction" to direction,
            "quantity" to position.quantity,
            "post_trigger_order_type" to postTriggerOrder.orderType,
            "post_trigger_limit_price" to postTriggerOrder.price,
            "mode" to mode,
            "status" to updated.smartOrderStatus
        ))
        return updated
    }

    private suspend fun refreshKnownSmartOrderStatus(
        token: String,
        position: ManagedPositionEntity,
        force: Boolean = false
    ): ManagedPositionEntity {
        val id = position.smartOrderId ?: return position
        val now = System.currentTimeMillis()
        val last = lastSmartOrderStatusAtMs[position.id] ?: 0L
        if (!force && now - last < SMART_ORDER_STATUS_REFRESH_MS) return position
        lastSmartOrderStatusAtMs[position.id] = now
        val payload = apiFactory.groww.smartOrderStatus(
            bearer(token),
            segment = "CASH",
            smartOrderType = "GTT",
            smartOrderId = id
        ).requirePayload("GTT status ${position.symbol}")
        val nextStatus = payload.status.ifBlank { position.smartOrderStatus ?: "" }
        val triggered = nextStatus.uppercase(Locale.US) in setOf("TRIGGERED", "COMPLETED")
        val triggeredAtMs = if (triggered) {
            orderListSnapshot = null
            position.smartOrderTriggeredAtMs ?: parseGrowwTimestampMs(payload.triggeredAt) ?: now
        } else position.smartOrderTriggeredAtMs
        val updated = position.copy(
            status = if (triggered) "EXIT_PENDING_RECONCILIATION" else position.status,
            smartOrderStatus = nextStatus,
            smartOrderUpdatedAtMs = now,
            smartOrderReferenceId = payload.referenceId ?: position.smartOrderReferenceId,
            smartOrderTriggeredAtMs = triggeredAtMs
        )
        managedDao.updatePosition(updated)
        auditLogger.log("GTT", "GTT_STATUS", mapOf(
            "symbol" to position.symbol,
            "gtt_id" to id,
            "mode" to position.smartOrderMode,
            "status" to updated.smartOrderStatus,
            "trigger" to position.smartOrderTriggerPrice,
            "direction" to position.smartOrderDirection
        ))
        return updated
    }

    private suspend fun finalizeCompletedGttIfNeeded(
        token: String,
        position: ManagedPositionEntity
    ): ManagedPositionEntity? {
        if (position.smartOrderId.isNullOrBlank() && position.status != "EXIT_PENDING_RECONCILIATION") return position
        val refreshed = if (!position.smartOrderId.isNullOrBlank()) {
            runCatching { refreshKnownSmartOrderStatus(token, position, force = false) }.getOrDefault(position)
        } else position
        val status = refreshed.smartOrderStatus?.uppercase(Locale.US).orEmpty()
        if (status in setOf("TRIGGERED", "COMPLETED") ||
            refreshed.status == "EXIT_PENDING_RECONCILIATION") {
            val pending = refreshed.copy(
                status = "EXIT_PENDING_RECONCILIATION",
                smartOrderTriggeredAtMs = refreshed.smartOrderTriggeredAtMs
                    ?: refreshed.smartOrderUpdatedAtMs ?: System.currentTimeMillis()
            )
            managedDao.updatePosition(pending)
            return reconcileTriggeredGttChildOrder(token, pending)
        }
        if (status in setOf("FAILED", "CANCELLED", "EXPIRED")) {
            val cleared = clearManagedGttState(refreshed).copy(
                status = "OPEN",
                stopPrice = refreshed.stopPrice ?: HardStopLossPolicy.stopPrice(refreshed.entryPrice)
            )
            managedDao.updatePosition(cleared)
            auditLogger.log("GTT", "TERMINAL_GTT_RECONCILED_FOR_REPROTECTION", mapOf(
                "symbol" to cleared.symbol, "old_gtt_id" to refreshed.smartOrderId,
                "old_status" to status
            ))
            return cleared
        }
        return refreshed
    }

    private fun clearManagedGttState(position: ManagedPositionEntity): ManagedPositionEntity =
        position.copy(
            smartOrderId = null,
            smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE,
            smartOrderTriggerPrice = null,
            smartOrderDirection = null,
            smartOrderStatus = null,
            smartOrderUpdatedAtMs = null,
            smartOrderReferenceId = null,
            smartOrderTriggeredAtMs = null,
            smartOrderChildOrderId = null,
            smartOrderChildStatus = null,
            netProfitFloorPrice = null,
            netProfitTargetPct = null
        )

    private suspend fun cancelKnownPositionGtt(
        token: String,
        position: ManagedPositionEntity,
        reason: String,
        clearIfTerminal: Boolean
    ): ManagedPositionEntity {
        val id = position.smartOrderId ?: return position
        activeGttSnapshot = null
        orderListSnapshot = null
        val statusPayload = runCatching {
            apiFactory.groww.smartOrderStatus(
                bearer(token), segment = "CASH", smartOrderType = "GTT", smartOrderId = id
            ).requirePayload("GTT status before cancel ${position.symbol}")
        }.getOrNull()
        val status = statusPayload?.status?.uppercase(Locale.US)
        if (status in setOf("TRIGGERED", "COMPLETED")) {
            val waiting = position.copy(
                status = "EXIT_PENDING_RECONCILIATION",
                smartOrderStatus = status,
                smartOrderUpdatedAtMs = System.currentTimeMillis(),
                smartOrderReferenceId = statusPayload?.referenceId ?: position.smartOrderReferenceId,
                smartOrderTriggeredAtMs = position.smartOrderTriggeredAtMs
                    ?: parseGrowwTimestampMs(statusPayload?.triggeredAt) ?: System.currentTimeMillis()
            )
            managedDao.updatePosition(waiting)
            auditLogger.log("GTT", "GTT_EXECUTION_IN_PROGRESS", mapOf(
                "symbol" to position.symbol,
                "gtt_id" to id,
                "mode" to position.smartOrderMode,
                "reason" to reason,
                "status" to status
            ))
            return waiting
        }
        val alreadyTerminal = status in setOf("COMPLETED", "TRIGGERED", "CANCELLED", "EXPIRED", "FAILED")
        if (!alreadyTerminal) {
            val cancelled = runCatching {
                apiFactory.groww.cancelSmartOrder(
                    bearer(token), segment = "CASH", smartOrderType = "GTT", smartOrderId = id
                ).requirePayload("Cancel GTT ${position.symbol}")
            }.getOrElse { error ->
                auditLogger.log("GTT", "GTT_CANCEL_FAILED", mapOf(
                    "symbol" to position.symbol,
                    "gtt_id" to id,
                    "mode" to position.smartOrderMode,
                    "reason" to reason,
                    "known_status" to status,
                    "message" to (error.message ?: error.javaClass.simpleName)
                ))
                return position
            }
            auditLogger.log("GTT", "GTT_CANCELLED", mapOf(
                "symbol" to position.symbol,
                "gtt_id" to id,
                "mode" to position.smartOrderMode,
                "reason" to reason,
                "status" to cancelled.status
            ))
        } else {
            auditLogger.log("GTT", "GTT_ALREADY_TERMINAL", mapOf(
                "symbol" to position.symbol,
                "gtt_id" to id,
                "mode" to position.smartOrderMode,
                "reason" to reason,
                "status" to status
            ))
        }
        val cleared = clearManagedGttState(position).copy(
            status = "OPEN",
            smartOrderUpdatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(cleared)
        lastSmartOrderStatusAtMs.remove(position.id)
        return cleared
    }

    private suspend fun ensureOvernightRecoveryGtt(token: String, position: ManagedPositionEntity): ManagedPositionEntity {
        if (position.smartOrderMode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT) {
            return maintainRecoveryGtt(token, position)
        }
        val target = GrowwDeliveryCostPolicy.recoverySellPrice(
            position.entryPrice,
            position.quantity,
            RECOVERY_NET_PROFIT_RUPEES
        )
        val direction = HoldingSmartOrderPolicy.recoveryDirection(position.lastPrice, target)
        val estimate = GrowwDeliveryCostPolicy.estimateCharges(position.entryPrice, target, position.quantity)
        val estimatedNet = GrowwDeliveryCostPolicy.estimatedNetProfit(position.entryPrice, target, position.quantity)

        var working = position
        if (!working.smartOrderId.isNullOrBlank()) {
            working = cancelKnownPositionGtt(token, working, "END_OF_DAY_SWITCH_TO_RECOVERY", clearIfTerminal = false)
            if (!working.smartOrderId.isNullOrBlank()) return working
        }
        working = working.copy(
            stopPrice = null,
            targetPrice = target,
            smartOrderMode = HoldingSmartOrderPolicy.MODE_RECOVERY_GTT,
            smartOrderId = null,
            smartOrderTriggerPrice = target,
            smartOrderDirection = direction,
            smartOrderStatus = "PENDING_CREATE",
            smartOrderUpdatedAtMs = System.currentTimeMillis(),
            lastEvaluatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(working)
        auditLogger.log("GTT", "RECOVERY_GTT_PLAN", mapOf(
            "symbol" to working.symbol,
            "entry" to working.entryPrice,
            "last_price" to working.lastPrice,
            "quantity" to working.quantity,
            "net_profit_target_rupees" to RECOVERY_NET_PROFIT_RUPEES,
            "trigger_price" to target,
            "trigger_direction" to direction,
            "estimated_gross_profit" to ((target - working.entryPrice) * working.quantity),
            "estimated_total_charges" to estimate.total,
            "estimated_net_profit" to estimatedNet,
            "brokerage_buy" to estimate.buyBrokerage,
            "brokerage_sell" to estimate.sellBrokerage,
            "stt" to estimate.stt,
            "stamp_duty" to estimate.stampDuty,
            "exchange_charges" to estimate.exchangeCharges,
            "sebi_charges" to estimate.sebiCharges,
            "ipft_charges" to estimate.ipftCharges,
            "dp_charges" to estimate.dpCharges,
            "gst" to estimate.gst,
            "downward_sell_disabled" to true
        ))
        return runCatching { createPositionGtt(token, working, target, direction, HoldingSmartOrderPolicy.MODE_RECOVERY_GTT) }
            .onFailure {
                auditLogger.log("GTT", "RECOVERY_GTT_CREATE_FAILED_RETRY_PENDING", mapOf(
                    "symbol" to working.symbol,
                    "trigger_price" to target,
                    "direction" to direction,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }
            .getOrDefault(working)
    }

    private suspend fun maintainRecoveryGtt(token: String, position: ManagedPositionEntity): ManagedPositionEntity {
        val target = position.targetPrice ?: GrowwDeliveryCostPolicy.recoverySellPrice(
            position.entryPrice,
            position.quantity,
            RECOVERY_NET_PROFIT_RUPEES
        )
        if (position.smartOrderId.isNullOrBlank()) {
            val livePrice = if (marketSession() == "OPEN") runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = position.symbol)
                    .requirePayload("Recovery GTT quote ${position.symbol}").lastPrice
            }.getOrNull() ?: position.lastPrice else position.lastPrice
            val direction = HoldingSmartOrderPolicy.recoveryDirection(livePrice, target)
            val pending = position.copy(
                targetPrice = target,
                stopPrice = null,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_RECOVERY_GTT,
                smartOrderTriggerPrice = target,
                smartOrderDirection = direction,
                smartOrderStatus = "PENDING_CREATE",
                lastPrice = livePrice
            )
            managedDao.updatePosition(pending)
            return createPositionGtt(token, pending, target, direction, HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
        }

        val refreshed = refreshKnownSmartOrderStatus(token, position, force = false)
        return when (refreshed.smartOrderStatus?.uppercase(Locale.US)) {
            "FAILED", "CANCELLED", "EXPIRED" -> {
                val cleared = refreshed.copy(
                    smartOrderId = null,
                    smartOrderTriggerPrice = target,
                    smartOrderDirection = HoldingSmartOrderPolicy.recoveryDirection(refreshed.lastPrice, target),
                    smartOrderStatus = "PENDING_RECREATE",
                    smartOrderUpdatedAtMs = System.currentTimeMillis(),
                    stopPrice = null,
                    smartOrderMode = HoldingSmartOrderPolicy.MODE_RECOVERY_GTT
                )
                managedDao.updatePosition(cleared)
                auditLogger.log("GTT", "RECOVERY_GTT_RECREATE", mapOf(
                    "symbol" to refreshed.symbol,
                    "previous_gtt_id" to refreshed.smartOrderId,
                    "previous_status" to refreshed.smartOrderStatus,
                    "trigger" to target
                ))
                createPositionGtt(
                    token,
                    cleared,
                    target,
                    cleared.smartOrderDirection ?: "UP",
                    HoldingSmartOrderPolicy.MODE_RECOVERY_GTT
                )
            }
            "TRIGGERED", "COMPLETED" -> refreshed
            else -> {
                val expectedDirection = HoldingSmartOrderPolicy.recoveryDirection(refreshed.lastPrice, target)
                val triggerMismatch = refreshed.smartOrderTriggerPrice == null ||
                    abs(refreshed.smartOrderTriggerPrice - target) > SMART_ORDER_PRICE_EPSILON
                if (triggerMismatch) {
                    modifyPositionGtt(token, refreshed, target, expectedDirection, HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
                } else refreshed
            }
        }
    }

    private fun auditTrailingState(
        position: ManagedPositionEntity,
        ltp: Double,
        desiredStopGainPct: Double?,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val last = lastTrailStateAuditAtMs[position.id] ?: 0L
        // Logging-only suppression: preserve 1-second monitoring and every sell/GTT decision.
        // A stable protection state needs only one diagnostic heartbeat per minute.
        val signature = listOf(position.stopPrice, position.smartOrderId, position.smartOrderMode,
            position.smartOrderStatus, position.quantity, desiredStopGainPct,
            position.maxFavourablePrice).joinToString("|")
        val sameState = lastTrailStateAuditSignature[position.id] == signature
        if (!force && now - last < TRAIL_STATE_LOG_INTERVAL_MS) return
        if (!force && sameState && now - last < 60_000L) return
        lastTrailStateAuditAtMs[position.id] = now
        lastTrailStateAuditSignature[position.id] = signature
        val gainPct = SteppedTrailingPolicy.gainPct(position.entryPrice, ltp)
        val highGainPct = SteppedTrailingPolicy.gainPct(position.entryPrice, position.maxFavourablePrice)
        val protectedGainPct = position.stopPrice?.takeIf { it > position.entryPrice }
            ?.let { SteppedTrailingPolicy.gainPct(position.entryPrice, it) }
        val nextNetTargetPct = (position.netProfitTargetPct ?: NetProfitProtectionPolicy.MIN_NET_RETURN_PCT) +
            SteppedTrailingPolicy.STEP_PCT
        val nextNetTriggerPrice = GrowwDeliveryCostPolicy.recoverySellPrice(
            position.entryPrice, position.quantity,
            position.entryPrice * position.quantity *
                (nextNetTargetPct + NetProfitProtectionPolicy.SLIPPAGE_ALLOWANCE_BPS / 100.0) / 100.0
        )
        auditLogger.log("TRAILING_STATE", "STOCK_TRAIL_STATE", mapOf(
            "symbol" to position.symbol,
            "engine" to position.engine,
            "entry" to position.entryPrice,
            "ltp" to ltp,
            "gain_pct" to gainPct,
            "high_water" to position.maxFavourablePrice,
            "high_water_gain_pct" to highGainPct,
            "legacy_gross_arm_trigger_pct" to SteppedTrailingPolicy.ARM_TRIGGER_PCT,
            "net_profit_minimum_pct" to NetProfitProtectionPolicy.MIN_NET_RETURN_PCT,
            "net_profit_target_pct" to position.netProfitTargetPct,
            "net_profit_floor_price" to position.netProfitFloorPrice,
            "net_profit_trigger_price" to position.smartOrderTriggerPrice,
            "net_slippage_allowance_bps" to NetProfitProtectionPolicy.SLIPPAGE_ALLOWANCE_BPS,
            "armed" to (position.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT),
            "desired_net_protected_pct" to desiredStopGainPct,
            "active_stop_price" to position.stopPrice,
            "protected_gain_pct" to protectedGainPct,
            "next_net_ratchet_target_pct" to nextNetTargetPct,
            "next_net_ratchet_trigger_price" to nextNetTriggerPrice,
            "gtt_id" to position.smartOrderId,
            "gtt_mode" to position.smartOrderMode,
            "gtt_status" to position.smartOrderStatus,
            "gtt_trigger_price" to position.smartOrderTriggerPrice,
            "gtt_direction" to position.smartOrderDirection,
            "hard_stop_loss_pct" to -HardStopLossPolicy.MAX_LOSS_PCT,
            "hard_stop_price" to HardStopLossPolicy.stopPrice(position.entryPrice),
            "hard_stop_enabled" to true,
            "averaging_down_disabled" to true,
            "local_fallback_only_if_no_active_gtt" to true
        ))
    }

    private fun roundSmartOrderPrice(price: Double): Double =
        kotlin.math.ceil((price - 1e-9) / GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES) * GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES

    private fun smartPrice(price: Double): String = String.format(Locale.US, "%.2f", price)

    private suspend fun closeLegacyNonLong(token: String, position: ManagedPositionEntity) {
        if (marketSession() != "OPEN" || !appOwned(position)) return
        val ltp = apiFactory.groww.quote(bearer(token), tradingSymbol = position.symbol)
            .requirePayload("Quote ${position.symbol}").lastPrice ?: position.lastPrice
        val ref = stableRef("LGC", "${position.id}-${position.symbol}")
        val order = placeLegacyExposureFlatten(token, position, ref)
        val exit = order.averageFillPrice?.takeIf { it > 0.0 } ?: ltp
        managedDao.insertTrade(
            ManagedTradeEntity(
                engine = "LEGACY_CLEANUP", symbol = position.symbol, product = position.product, side = "LONG",
                quantity = position.quantity, entryPrice = position.entryPrice, exitPrice = exit,
                grossPnl = 0.0, estimatedCosts = 0.0, netPnl = 0.0,
                exitReason = "UPGRADE_TO_LONG_ONLY", strategy = "Legacy exposure cleanup",
                regime = "LONG_ONLY_MIGRATION", confidence = 1.0, mfeRupees = 0.0, maeRupees = 0.0,
                sourceEventId = position.sourceEventId, openOrderId = position.openOrderId, closeOrderId = order.growwOrderId,
                openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
            )
        )
        managedDao.deletePosition(position.id)
        auditLogger.log("SAFETY", "LEGACY_NON_LONG_CLOSED", mapOf("symbol" to position.symbol, "qty" to position.quantity))
    }

    private suspend fun closeLongHolding(
        token: String,
        position: ManagedPositionEntity,
        mark: Double,
        reason: String,
        notificationStartedNs: Long? = null
    ): ManagedTradeEntity? {
        val closeMutex = closePositionMutexes.computeIfAbsent(position.id) { Mutex() }
        return closeMutex.withLock {
        require(position.side == "LONG") { "Only LONG holdings are supported" }

        var local = managedDao.openPositionById(position.id)
        if (local == null) {
            auditLogger.log("SAFETY", "DUPLICATE_CLOSE_BLOCKED", mapOf(
                "symbol" to position.symbol,
                "position_id" to position.id,
                "reason" to reason,
                "stage" to "local_position_already_closed"
            ))
            closePositionMutexes.remove(position.id, closeMutex)
            return@withLock null
        }

        if (!safeAppSellScope(token, local, forceGttRefresh = true)) {
            auditLogger.log("SAFETY", "SELL_BLOCKED_UNOWNED_OR_MIXED", mapOf(
                "symbol" to local.symbol, "position_id" to local.id, "app_qty" to local.quantity
            ))
            closePositionMutexes.remove(local.id, closeMutex)
            return@withLock null
        }
        if (brokerRetryGuard.sellBlockedUntil(local.symbol) > System.currentTimeMillis()) {
            closePositionMutexes.remove(local.id, closeMutex)
            return@withLock null
        }

        if (!local.smartOrderId.isNullOrBlank()) {
            val cleared = cancelKnownPositionGtt(token, local, "BEFORE_DIRECT_SELL", clearIfTerminal = false)
            if (!cleared.smartOrderId.isNullOrBlank()) {
                auditLogger.log("SAFETY", "DIRECT_SELL_BLOCKED_BY_ACTIVE_OR_TRIGGERED_GTT", mapOf(
                    "symbol" to local.symbol,
                    "gtt_id" to local.smartOrderId,
                    "gtt_status" to cleared.smartOrderStatus,
                    "reason" to reason
                ))
                closePositionMutexes.remove(local.id, closeMutex)
                return@withLock null
            }
            local = cleared
        }

        val snapshot = try { brokerInventory(token, force = true) } catch (t: Throwable) {
            closePositionMutexes.remove(local.id, closeMutex)
            return@withLock null
        }
        val symbolKey = local.symbol.uppercase(Locale.US)
        val free = snapshot.holdings[symbolKey]?.dematFreeQuantity ?: 0
        val intraday = snapshot.positions[symbolKey]?.quantity ?: 0
        if (!BrokerExecutionSafetyPolicy.maySell(local.quantity, free, intraday)) {
            val until = brokerRetryGuard.blockSell(local.symbol)
            auditLogger.log("EXECUTION", "SELL_PAUSED_SELLABLE_QUANTITY_MISMATCH", mapOf(
                "symbol" to local.symbol, "requested" to local.quantity,
                "demat_free" to free, "cnc_position" to intraday, "blocked_until_ms" to until
            ))
            closePositionMutexes.remove(local.id, closeMutex)
            return@withLock null
        }
        val tradingDay = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDate()
        val ref = stableRef("CLS", "${local.id}-${local.symbol}-${tradingDay}")
        val order = try {
            placeOwnedCncSell(token, local, ref, notificationStartedNs)
        } catch (t: Throwable) {
            brokerInventorySnapshot = null
            val brokerError = t.message.orEmpty()
            if (BrokerExecutionSafetyPolicy.isInsufficientShares(brokerError) ||
                brokerError.contains("Groww order FAILED", true) ||
                brokerError.contains("Groww order REJECTED", true) ||
                brokerError.contains("Groww order CANCELLED", true)) {
                val until = brokerRetryGuard.blockSell(local.symbol)
                auditLogger.log("EXECUTION", "TERMINAL_SELL_FAILURE_COOLDOWN", mapOf(
                    "symbol" to local.symbol, "blocked_until_ms" to until, "message" to brokerError
                ))
            }
            runCatching { reconcilePositionWithBroker(token, local, preferredMark = mark, force = true) }
            auditLogger.log("EXECUTION", "FAST_SELL_DISPATCH_FAILED", mapOf(
                "symbol" to local.symbol,
                "reason" to reason,
                "message" to (t.message ?: t.javaClass.simpleName)
            ))
            throw t
        }

        val exit = order.averageFillPrice?.takeIf { it > 0.0 } ?: mark
        val gross = (exit - local.entryPrice) * local.quantity
        val costs = estimatedCashCosts(local.entryPrice, exit, local.quantity)
        val trade = ManagedTradeEntity(
            engine = local.engine, symbol = local.symbol, product = "CNC", side = "LONG",
            quantity = local.quantity, entryPrice = local.entryPrice, exitPrice = exit,
            grossPnl = gross, estimatedCosts = costs, netPnl = gross - costs, exitReason = reason,
            strategy = local.strategy, regime = local.regime, confidence = local.confidence,
            mfeRupees = max(0.0, local.maxFavourablePrice - local.entryPrice) * local.quantity,
            maeRupees = max(0.0, local.entryPrice - local.maxAdversePrice) * local.quantity,
            sourceEventId = local.sourceEventId, openOrderId = local.openOrderId, closeOrderId = order.growwOrderId,
            openedAtMs = local.openedAtMs, closedAtMs = System.currentTimeMillis()
        )
        managedDao.insertTrade(trade)
        recentlyClosedSymbolsAtMs[local.symbol.uppercase(Locale.US)] = System.currentTimeMillis()
        managedDao.deletePosition(local.id)
        closePositionMutexes.remove(local.id, closeMutex)
        lastTrailStateAuditAtMs.remove(local.id)
        lastSmartOrderStatusAtMs.remove(local.id)
        brokerInventorySnapshot = null
        auditLogger.log("HOLDING", "LONG_CLOSED", mapOf(
            "symbol" to local.symbol,
            "qty" to local.quantity,
            "exit" to exit,
            "reason" to reason,
            "net_pnl" to trade.netPnl
        ))
        trade
        }
    }

    private suspend fun placeOwnedCncSell(
        token: String,
        position: ManagedPositionEntity,
        reference: String,
        notificationStartedNs: Long? = null
    ): OrderPayload {
        require(position.side == "LONG") { "Only tracked LONG holdings may be sold" }
        require(position.product == "CNC") { "Only CNC holdings may be sold by the long-only engine" }
        require(safeAppSellScope(token, position, forceGttRefresh = true)) { "SELL blocked: ${position.symbol} is not exclusively app-bought" }
        return submitMarketOrder(token, position.symbol, "SELL", position.quantity, "CNC", reference, notificationStartedNs)
    }

    private suspend fun placeLegacyExposureFlatten(token: String, position: ManagedPositionEntity, reference: String): OrderPayload {
        require(position.side != "LONG") { "Legacy cleanup is only for pre-long-only exposure" }
        return submitMarketOrder(token, position.symbol, "BUY", position.quantity, position.product, reference)
    }

    private suspend fun submitMarketOrder(
        token: String,
        symbol: String,
        transaction: String,
        qty: Int,
        product: String,
        reference: String,
        notificationStartedNs: Long? = null
    ): OrderPayload {
        require(qty > 0) { "Order quantity is zero" }
        val dispatchStartedNs = SystemClock.elapsedRealtimeNanos()
        val dispatchPrepMicros = notificationStartedNs?.let { ((dispatchStartedNs - it).coerceAtLeast(0L)) / 1_000L } ?: 0L
        val submittedAtMs = System.currentTimeMillis()
        val brokerStartedNs = SystemClock.elapsedRealtimeNanos()
        val response = try {
            apiFactory.groww.placeOrder(
                bearer(token),
                OrderCreateRequest(
                    tradingSymbol = symbol,
                    quantity = qty,
                    product = product,
                    transactionType = transaction,
                    orderReferenceId = reference
                )
            ).requirePayload("Place $transaction $symbol")
        } catch (original: Throwable) {
            val recovered = runCatching {
                apiFactory.groww.orderStatusByReference(
                    bearer(token),
                    referenceId = reference
                ).requirePayload("Recover $transaction $symbol by reference")
            }.getOrNull()
            if (recovered == null) throw original
            auditLogger.log("EXECUTION", "ORDER_RECOVERED_BY_REFERENCE", mapOf(
                "symbol" to symbol,
                "transaction" to transaction,
                "reference" to reference,
                "order_id" to recovered.growwOrderId,
                "status" to recovered.orderStatus
            ))
            recovered
        }
        val brokerAckLatencyMs = ((SystemClock.elapsedRealtimeNanos() - brokerStartedNs).coerceAtLeast(0L)) / 1_000_000L
        preferences.recordOrderLatency(dispatchPrepMicros, brokerAckLatencyMs)
        auditLogger.logAsync("EXECUTION_LATENCY", "ORDER_ACK", mapOf(
            "symbol" to symbol,
            "transaction" to transaction,
            "dispatch_prep_micros" to dispatchPrepMicros,
            "dispatch_target_met" to (notificationStartedNs == null || dispatchPrepMicros < 100_000L),
            "broker_ack_ms" to brokerAckLatencyMs
        ))
        val id = response.growwOrderId
        require(id.isNotBlank()) { "Groww did not return an order ID" }

        suspend fun acceptFilled(status: OrderPayload): OrderPayload {
            val filledAtMs = System.currentTimeMillis()
            val totalExecutionMs = ((SystemClock.elapsedRealtimeNanos() - brokerStartedNs).coerceAtLeast(0L)) / 1_000_000L
            if (product == "CNC" && transaction in setOf("BUY", "SELL") && !reference.startsWith("LGC")) {
                managedDao.insertExecutionSample(
                    ExecutionSampleEntity(
                        transactionType = transaction,
                        symbol = symbol,
                        quantity = qty,
                        product = product,
                        orderId = id,
                        submittedAtMs = submittedAtMs,
                        filledAtMs = filledAtMs,
                        totalExecutionMs = totalExecutionMs,
                        appDispatchMicros = dispatchPrepMicros,
                        brokerAckMs = brokerAckLatencyMs
                    )
                )
            }
            brokerInventorySnapshot = null
            auditLogger.logAsync("EXECUTION_LATENCY", "ORDER_FILLED", mapOf(
                "symbol" to symbol,
                "transaction" to transaction,
                "total_execution_ms" to totalExecutionMs,
                "broker_ack_ms" to brokerAckLatencyMs
            ))
            return status.copy(growwOrderId = id, orderReferenceId = reference)
        }

        if (response.orderStatus.equals("EXECUTED", true) || (response.filledQuantity ?: 0) >= qty) {
            return acceptFilled(response)
        }
        if (response.orderStatus.uppercase(Locale.US) in setOf("REJECTED", "FAILED", "CANCELLED")) {
            error("Groww order ${response.orderStatus}: ${response.remark.orEmpty()}")
        }
        for (pollDelayMs in MARKET_ORDER_FILL_POLL_DELAYS_MS) {
            delay(pollDelayMs)
            val status = apiFactory.groww.orderStatus(bearer(token), id).requirePayload("Order status")
            if (status.orderStatus.equals("EXECUTED", true) || (status.filledQuantity ?: 0) >= qty) {
                return acceptFilled(status)
            }
            if (status.orderStatus.uppercase(Locale.US) in setOf("REJECTED", "FAILED", "CANCELLED")) {
                error("Groww order ${status.orderStatus}: ${status.remark.orEmpty()}")
            }
        }
        error("Groww order $id was not confirmed filled; deterministic reference $reference prevents duplicate resubmission")
    }
    private data class Snapshot(
        val summary: DaySummaryDto,
        val positions: List<PositionDto>,
        val recentDecisions: List<RecentDecisionDto>
    )

    private suspend fun executionStats(): ExecutionStatsDto {
        fun aggregate(rows: List<ExecutionSampleEntity>): ExecutionAverageDto {
            val dispatchRows = rows.filter { it.appDispatchMicros > 0L }
            return ExecutionAverageDto(
                averageFillMs = ExecutionTelemetryMath.averageMillis(rows.map { it.totalExecutionMs }),
                averageAppDispatchMs = ExecutionTelemetryMath.averageDispatchMillis(rows.map { it.appDispatchMicros }),
                sampleCount = rows.size,
                dispatchSampleCount = dispatchRows.size
            )
        }
        return ExecutionStatsDto(
            buyLong = aggregate(managedDao.recentExecutionSamples("BUY", EXECUTION_SAMPLE_LIMIT)),
            sellLong = aggregate(managedDao.recentExecutionSamples("SELL", EXECUTION_SAMPLE_LIMIT))
        )
    }

    private suspend fun managedSnapshotLocal(): Snapshot {
        val open = managedDao.openPositions().filter { it.side == "LONG" && appOwned(it) }
        val positions = open.map { p ->
            val ltp = p.lastPrice.takeIf { it.isFinite() && it > 0.0 } ?: p.entryPrice
            val pnl = (ltp - p.entryPrice) * p.quantity
            PositionDto(
                symbol = p.symbol,
                engine = p.engine,
                quantity = p.quantity,
                averagePrice = p.entryPrice,
                ltp = ltp,
                pnl = pnl,
                stopPrice = p.stopPrice,
                targetPrice = p.targetPrice,
                highWaterPrice = p.maxFavourablePrice,
                trailingLastRatchetPrice = p.trailingLastRatchetPrice.takeIf { it > 0.0 },
                trailDistance = if (p.stopPrice != null && p.stopPrice > p.entryPrice)
                    (p.maxFavourablePrice - p.stopPrice).coerceAtLeast(0.0) else null,
                trailArmPct = p.trailingArmPct.takeIf { it.isFinite() && it > 0.0 },
                trailArmed = p.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT ||
                    (p.stopPrice?.let { it > p.entryPrice } == true),
                smartOrderId = p.smartOrderId,
                smartOrderMode = p.smartOrderMode,
                smartOrderTriggerPrice = p.smartOrderTriggerPrice,
                smartOrderDirection = p.smartOrderDirection,
                smartOrderStatus = p.smartOrderStatus,
                strategy = p.strategy,
                product = "CNC"
            )
        }
        val recent = managedDao.tradesSince(startOfIndiaDayMs())
            .filter { it.side == "LONG" }
            .take(6)
            .map { t ->
                RecentDecisionDto(
                    formatEventTime(t.closedAtMs),
                    t.symbol,
                    "LONG_CLOSED",
                    t.exitReason,
                    t.strategy,
                    t.quantity
                )
            }
        return Snapshot(
            summary = DaySummaryDto(
                unrealisedPnl = positions.sumOf { it.pnl },
                grossExposure = positions.sumOf { it.quantity * (it.ltp ?: it.averagePrice) },
                openPositions = positions.size
            ),
            positions = positions,
            recentDecisions = recent
        )
    }

    private suspend fun managedSnapshot(token: String): Snapshot {
        val open = managedDao.openPositions().filter { it.side == "LONG" && appOwned(it) }
        val positions = open.map { p ->
            val ltp = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = p.symbol)
                    .requirePayload("Quote ${p.symbol}").lastPrice
            }.getOrNull() ?: p.lastPrice
            val pnl = (ltp - p.entryPrice) * p.quantity
            PositionDto(
                symbol = p.symbol,
                engine = p.engine,
                quantity = p.quantity,
                averagePrice = p.entryPrice,
                ltp = ltp,
                pnl = pnl,
                stopPrice = p.stopPrice,
                targetPrice = p.targetPrice,
                highWaterPrice = p.maxFavourablePrice,
                trailingLastRatchetPrice = p.trailingLastRatchetPrice.takeIf { it > 0.0 },
                trailDistance = if (p.stopPrice != null && p.stopPrice > p.entryPrice)
                    (p.maxFavourablePrice - p.stopPrice).coerceAtLeast(0.0) else null,
                trailArmPct = p.trailingArmPct.takeIf { it.isFinite() && it > 0.0 },
                trailArmed = p.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT ||
                    (p.stopPrice?.let { it > p.entryPrice } == true),
                smartOrderId = p.smartOrderId,
                smartOrderMode = p.smartOrderMode,
                smartOrderTriggerPrice = p.smartOrderTriggerPrice,
                smartOrderDirection = p.smartOrderDirection,
                smartOrderStatus = p.smartOrderStatus,
                strategy = p.strategy,
                product = "CNC"
            )
        }
        val recent = managedDao.tradesSince(startOfIndiaDayMs())
            .filter { it.side == "LONG" }
            .take(6)
            .map { t ->
                RecentDecisionDto(
                    formatEventTime(t.closedAtMs),
                    t.symbol,
                    "LONG_CLOSED",
                    t.exitReason,
                    t.strategy,
                    t.quantity
                )
            }
        return Snapshot(
            summary = DaySummaryDto(
                unrealisedPnl = positions.sumOf { it.pnl },
                grossExposure = positions.sumOf { it.quantity * (it.ltp ?: it.averagePrice) },
                openPositions = positions.size
            ),
            positions = positions,
            recentDecisions = recent
        )
    }

    suspend fun sampleSignal(eventId: Long) {
        val event = dao.byId(eventId) ?: return
        val symbol = event.symbol ?: return
        val token = ensureToken() ?: return
        val offsets = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900)
        var elapsed = 0
        for (offset in offsets) {
            delay((offset - elapsed) * 1000L)
            elapsed = offset
            val price = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = symbol)
                    .requirePayload("Signal sample $symbol").lastPrice
            }.getOrNull() ?: continue
            preferences.recordMarketData()
            learningDao.insertObservation(
                PriceObservationEntity(
                    eventId = eventId,
                    symbol = symbol,
                    phase = if (event.signalType == SignalType.TRADE_RELEASE.name) "POST_LONG_CALL" else "POST_LONG_EXIT",
                    offsetSeconds = offset,
                    observedAtMs = System.currentTimeMillis(),
                    price = price
                )
            )
        }
    }

    data class LogExportResult(val entries: Int, val signalCount: Int, val tradeCount: Int)

    data class LearningRestoreResult(
        val learningCalls: Int,
        val researchSnapshots: Int,
        val forecasts: Int,
        val champions: Int,
        val reports: Int,
        val positiveEvidence: Int = 0,
        val archivedFixedOutcomes: Int = 0,
        val fixedFactorSnapshots: Int = 0
    ) {
        val total: Int get() = learningCalls + researchSnapshots + forecasts + champions + reports +
            positiveEvidence + archivedFixedOutcomes + fixedFactorSnapshots
    }

    /**
     * Restores learning-only state from a Multify diagnostics ZIP after a debug-signature migration.
     * Trading state is intentionally excluded: no holdings, orders, execution samples, credentials,
     * ARM flags, broker state or settings are imported.
     */
    /** User-driven ZIP import runs entirely on IO, never on the UI or Fixed scanner thread. */
    suspend fun restoreLearningFromLogs(uri: Uri): LearningRestoreResult = withContext(Dispatchers.IO) {
        learningRestoreMutex.withLock { restoreLearningFromLogsOnIo(uri) }
    }

    private suspend fun restoreLearningFromLogsOnIo(uri: Uri): LearningRestoreResult {
        var restoreStage = "opening learning ZIP"
        try {
        val entries = linkedMapOf<String, String>()
        var totalBytes = 0L
        appContext.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (name in LEARNING_RESTORE_FILES) {
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= MAX_RESTORE_ENTRY_BYTES) { "Restore entry $name is too large" }
                            require(totalBytes <= MAX_RESTORE_TOTAL_BYTES) { "Restore ZIP is too large" }
                            out.write(buffer, 0, read)
                        }
                        entries[name] = out.toString(Charsets.UTF_8.name())
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Unable to open restore ZIP")

        require(entries.isNotEmpty()) { "This ZIP does not contain Multify learning files" }
        val gson = GsonBuilder().disableHtmlEscaping().create()
        restoreStage = "Fixed baseline snapshot"
        var restoredFixedSnapshot = false
        entries["fixed_engine_snapshot.json"]?.let { json ->
            if (fixedEngineSnapshotStore.loadOrNull() == null) {
                val payload = gson.fromJson(json, FixedEngineSnapshotPayload::class.java)
                fixedEngineSnapshotStore.restoreExactIfAbsent(payload)
                fixedSnapshotAudited = false
                restoredFixedSnapshot = true
            }
        }
        var restoredCalls = 0
        var restoredResearch = 0
        var restoredForecasts = 0
        var restoredChampions = 0
        var restoredReports = 0
        var restoredPositiveEvidence = 0
        var restoredFixedOutcomes = 0
        var restoredFixedFactors = 0
        restoreStage = "40-factor Fixed evidence"
        entries["fixed_factor_snapshots.jsonl"]?.let { jsonl ->
            restoredFixedFactors = fixedFactorTelemetry.importArchivedSnapshots(jsonl)
        }

        restoreStage = "Multify learning calls"
        entries["long_learning.json"]?.let { json ->
            val rows = JsonParser.parseString(json).asJsonArray
            rows.forEach { node ->
                val x = node.asJsonObject
                val callDate = x.get("date")?.takeUnless { it.isJsonNull }?.asString ?: return@forEach
                val symbol = x.get("symbol")?.takeUnless { it.isJsonNull }?.asString?.uppercase(Locale.US) ?: return@forEach
                val source = x.get("source")?.takeUnless { it.isJsonNull }?.asString ?: "RESTORED"
                val entryPrice = x.get("entry_price")?.takeUnless { it.isJsonNull }?.asDouble ?: return@forEach
                if (!entryPrice.isFinite() || entryPrice <= 0.0) return@forEach
                val existing = learningDao.callForDateSymbolAction(callDate, symbol, "BUY")
                val targetPrice = x.get("target_price")?.takeUnless { it.isJsonNull }?.asDouble
                val targetPct = x.get("target_pct")?.takeUnless { it.isJsonNull }?.asDouble
                val exitPrice = x.get("exit_price")?.takeUnless { it.isJsonNull }?.asDouble
                val longReturn = x.get("long_return_pct")?.takeUnless { it.isJsonNull }?.asDouble
                val maxPrice = x.get("multify_max_price")?.takeUnless { it.isJsonNull }?.asDouble
                val mfe = x.get("multify_mfe_pct")?.takeUnless { it.isJsonNull }?.asDouble
                val updated = if (existing == null) {
                    LearningCallEntity(
                        callDate = callDate,
                        symbol = symbol,
                        source = source,
                        action = "BUY",
                        entryPrice = entryPrice,
                        targetPrice = targetPrice,
                        targetPct = targetPct,
                        multifyExitPrice = exitPrice,
                        longRealizedPct = longReturn,
                        multifyMaxPrice = maxPrice,
                        multifyMfePct = mfe,
                        updatedAtMs = System.currentTimeMillis()
                    )
                } else {
                    existing.copy(
                        source = existing.source.ifBlank { source },
                        entryPrice = entryPrice,
                        targetPrice = targetPrice ?: existing.targetPrice,
                        targetPct = targetPct ?: existing.targetPct,
                        multifyExitPrice = exitPrice ?: existing.multifyExitPrice,
                        longRealizedPct = longReturn ?: existing.longRealizedPct,
                        multifyMaxPrice = maxPrice ?: existing.multifyMaxPrice,
                        multifyMfePct = mfe ?: existing.multifyMfePct,
                        updatedAtMs = System.currentTimeMillis()
                    )
                }
                if (existing == null) learningDao.insertCall(updated) else learningDao.updateCall(updated)
                restoredCalls++
            }
        }

        restoreStage = "Forecast research snapshots"
        entries["forecast_research_snapshots.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<ForecastResearchEntity>::class.java).orEmpty()
            val existingKeys = learningDao.allForecastResearch().map {
                "${it.researchDate}|${it.scanAtMs}|${it.symbol.uppercase(Locale.US)}|${it.rank}"
            }.toHashSet()
            // A single Room transaction per bounded batch, rather than >4,000 UI-bound inserts.
            // Re-running a partially completed restore only imports rows not yet present.
            val pending = ArrayList<ForecastResearchEntity>(256)
            imported.forEach { row ->
                if (row.researchDate.isBlank() || row.symbol.isBlank() ||
                    !row.entryPrice.isFinite() || row.entryPrice <= 0.0) return@forEach
                val symbol = row.symbol.uppercase(Locale.US)
                val key = "${row.researchDate}|${row.scanAtMs}|$symbol|${row.rank}"
                if (!existingKeys.add(key)) return@forEach
                pending += row.copy(id = 0, symbol = symbol)
                if (pending.size >= 256) {
                    restoredResearch += learningDao.insertForecastResearchBatch(pending).count { it > 0L }
                    pending.clear()
                }
            }
            if (pending.isNotEmpty()) {
                restoredResearch += learningDao.insertForecastResearchBatch(pending).count { it > 0L }
            }
        }

        restoreStage = "Forecast recommendation history"
        entries["long_forecasts.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<LongForecastEntity>::class.java).orEmpty()
            imported.forEach { row ->
                if (row.forecastDate.isBlank() || row.symbol.isBlank() || row.entryPrice <= 0.0) return@forEach
                val existing = learningDao.longForecastsForDate(row.forecastDate)
                    .firstOrNull { it.symbol.equals(row.symbol, true) && it.generatedAtMs == row.generatedAtMs }
                learningDao.upsertLongForecast(
                    row.copy(
                        id = existing?.id ?: 0,
                        symbol = row.symbol.uppercase(Locale.US),
                        notified = true
                    )
                )
                restoredForecasts++
            }
        }

        restoreStage = "Forecast champions"
        entries["long_champions.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<ForecastChampionEntity>::class.java).orEmpty()
            imported.forEach { row ->
                if (row.key.isBlank()) return@forEach
                learningDao.upsertForecastChampion(row)
                restoredChampions++
            }
        }

        restoreStage = "Forecast research reports"
        entries["research.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<com.multify.traderpro.data.local.ResearchReportEntity>::class.java).orEmpty()
            imported.forEach { row ->
                if (row.reportDate.isBlank()) return@forEach
                learningDao.insertResearchReport(row.copy(id = 0))
                restoredReports++
            }
        }

        // Earlier releases exported these two datasets but silently did not restore them.
        // Keep original evidence keys and only insert missing rows: historical observation is
        // never permission to trade or to re-arm a broker account.
        restoreStage = "Forecast positive evidence"
        entries["forecast_positive_evidence.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<ForecastPositiveEvidenceEntity>::class.java).orEmpty()
            val known = learningDao.allForecastPositiveEvidence().map { it.evidenceKey }.toHashSet()
            imported.forEach { row ->
                if (row.evidenceKey.isBlank() || row.evidenceDate.isBlank() ||
                    row.symbol.isBlank() || !row.entryPrice.isFinite() || row.entryPrice <= 0.0 ||
                    row.evidenceKey in known) return@forEach
                learningDao.upsertForecastPositiveEvidence(row.copy(symbol = row.symbol.uppercase(Locale.US)))
                known += row.evidenceKey
                restoredPositiveEvidence++
            }
        }

        // The Fixed engine and its immutable scorer are never rewritten here. We archive the
        // observed outcomes, never replay active recommendation/order state. Importing the
        // current day's rows is safe ONLY after market close.
        restoreStage = "archived Fixed outcomes"
        entries["fixed_recommendations.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<FixedRecommendationRecord>::class.java).orEmpty()
            val now = ZonedDateTime.now(INDIA)
            val today = now.toLocalDate().toString()
            val currentSessionClosed = now.toLocalTime() >= LocalTime.of(15, 30)
            val knownByDate = mutableMapOf<String, MutableSet<String>>()
            imported.forEach { row ->
                if (row.recommendationDate.isBlank() || row.symbol.isBlank() ||
                    row.recommendationDate > today ||
                    (row.recommendationDate == today && !currentSessionClosed) ||
                    !row.entryPrice.isFinite() || row.entryPrice <= 0.0 ||
                    !row.maxFavourablePct.isFinite() || !row.maxAdversePct.isFinite()) return@forEach
                val symbol = row.symbol.uppercase(Locale.US)
                val known = knownByDate.getOrPut(row.recommendationDate) {
                    fixedRecommendationStore.recommendations(row.recommendationDate)
                        .map { it.symbol.uppercase(Locale.US) }.toMutableSet()
                }
                if (!known.add(symbol)) return@forEach
                fixedRecommendationStore.upsert(row.copy(
                    id = FixedRecommendationStore.fixedId(row.recommendationDate, symbol),
                    symbol = symbol,
                    status = if (row.status == "TARGET_HIT") "TARGET_HIT" else "SESSION_ENDED",
                    executionEligible = false
                ))
                restoredFixedOutcomes++
            }
        }
        // Only verified, completed historical research sessions may count toward the
        // five-session calibration ledger, never a bare scan or imported outcome claim.
        restoreStage = "Forecast calibration reconciliation"
        val verifiedCompletedSessions = learningDao.allForecastResearch()
            .filter { it.outcomeUpdatedAtMs != null &&
                it.outcomeMfePct?.isFinite() == true &&
                it.outcomeMaePct?.isFinite() == true &&
                it.outcomeClosePct?.isFinite() == true &&
                it.researchDate < LocalDate.now(INDIA).toString() }
            .groupBy { it.researchDate }
            .filterValues { rows -> rows.map { it.symbol }.distinct().size >= FORECAST_CALIBRATION_MIN_COMPLETED_ROWS }
            .keys
        verifiedCompletedSessions.forEach { markForecastCalibrationSessionComplete(it) }

        restoreStage = "historical seed and Fixed snapshot validation"
        historicalSeedEnsured = false
        ensureHistoricalSeed()
        if (fixedEngineSnapshotStore.loadOrNull() == null && fixedEngineSnapshotStore.isAwaitingRestore()) {
            fixedEngineSnapshotStore.clearAwaitingRestore()
            runCatching { ensureFixedEngineSnapshot() }
                .onSuccess { restoredFixedSnapshot = true }
                .onFailure { fixedEngineSnapshotStore.markAwaitingRestore() }
        }
        auditLogger.log("MIGRATION", "LEARNING_RESTORE_COMPLETED", mapOf(
            "learning_calls" to restoredCalls,
            "research_snapshots" to restoredResearch,
            "forecasts" to restoredForecasts,
            "champions" to restoredChampions,
            "reports" to restoredReports,
            "positive_evidence" to restoredPositiveEvidence,
            "archived_fixed_outcomes" to restoredFixedOutcomes,
            "archived_fixed_factor_rows" to restoredFixedFactors,
            "verified_completed_calibration_sessions" to verifiedCompletedSessions.size,
            "fixed_snapshot_restored_or_captured" to restoredFixedSnapshot,
            "fixed_factor_rows_never_used_for_trading" to true,
            "trading_state_restored" to false,
            "credentials_restored" to false
        ))
        return LearningRestoreResult(
            learningCalls = restoredCalls,
            researchSnapshots = restoredResearch,
            forecasts = restoredForecasts,
            champions = restoredChampions,
            reports = restoredReports,
            positiveEvidence = restoredPositiveEvidence,
            archivedFixedOutcomes = restoredFixedOutcomes,
            fixedFactorSnapshots = restoredFixedFactors
        )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            auditLogger.logAsync("MIGRATION", "LEARNING_RESTORE_FAILED", mapOf(
                "stage" to restoreStage,
                "exception" to error.javaClass.simpleName,
                "message" to (error.message ?: "unknown").take(180),
                "historical_only" to true
            ))
            throw IllegalStateException("Restore failed during $restoreStage: " +
                (error.message ?: error.javaClass.simpleName).take(130) + ". Previously restored rows are safe to retry.", error)
        }
    }

    suspend fun exportCompleteLogs(uri: Uri): LogExportResult {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val settings = preferences.settings.first()
        val signals = dao.allNow()
        val positions = managedDao.allPositions().filter { it.side == "LONG" }
        val trades = managedDao.allTrades().filter { it.side == "LONG" }
        val executionSamples = managedDao.allExecutionSamples()
        val priceObservations = learningDao.allObservations()
        val forecastResearch = learningDao.allForecastResearch()
        val positiveEvidence = learningDao.allForecastPositiveEvidence()
        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }.map {
            mapOf(
                "date" to it.callDate,
                "symbol" to it.symbol,
                "source" to it.source,
                "entry_price" to it.entryPrice,
                "target_price" to it.targetPrice,
                "target_pct" to it.targetPct,
                "exit_price" to it.multifyExitPrice,
                "long_return_pct" to it.longRealizedPct,
                "multify_max_price" to it.multifyMaxPrice,
                "multify_mfe_pct" to it.multifyMfePct
            )
        }
        val forecasts = learningDao.allLongForecasts()
        val champions = learningDao.allForecastChampions()
        val research = learningDao.allResearchReports()
        val today = LocalDate.now(INDIA).toString()
        val forecastAutoBuyOrders = forecastAutoBuyOrderStore.all(today)
        val fixedAutoBuyOrders = fixedAutoBuyOrderStore.all(today)
        val fixedRecommendations = fixedRecommendationStore.recommendations(today)
        val fixedSnapshot = fixedEngineSnapshotStore.loadOrNull()
        val safeSettings = mapOf(
            "holding_budget_rupees" to settings.holdingBudgetRupees,
            "arm_effective" to settings.armEffective,
            "forecast_auto_buy_enabled" to settings.forecastAutoBuyEnabled,
            "fixed_auto_buy_enabled" to settings.fixedAutoBuyEnabled,
            "package_filter" to settings.packageFilter,
            "broker_authenticated" to settings.brokerAuthenticated,
            "ddpi_enabled" to settings.brokerDdpiEnabled,
            "static_ip_matched" to settings.staticIpMatched,
            "safety_halt" to settings.safetyHalt,
            "version" to BuildConfig.VERSION_NAME
        )
        appContext.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                fun add(name: String, value: Any) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(gson.toJson(value).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                fun addText(name: String, value: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                add("settings.json", safeSettings)
                add("signals.json", signals)
                add("holdings.json", positions)
                add("closed_long_trades.json", trades)
                add("execution_samples.json", executionSamples)
                add("signal_price_observations.json", priceObservations)
                add("forecast_research_snapshots.json", forecastResearch)
                add("forecast_positive_evidence.json", positiveEvidence)
                add("long_learning.json", calls)
                add("long_forecasts.json", forecasts)
                add("long_champions.json", champions)
                add("research.json", research)
                add("forecast_auto_buy_orders.json", forecastAutoBuyOrders)
                add("fixed_recommendations.json", fixedRecommendations)
                add("fixed_auto_buy_orders.json", fixedAutoBuyOrders)
                fixedFactorTelemetry.appendExportEntry(zip)
                add("api_usage_snapshot.json", GrowwApiUsageLedger.snapshot())
                add("groww_recovery_state.json", recoveryGate.status())
                add("telemetry_metadata.json", mapOf(
                    "exported_at_ms" to System.currentTimeMillis(),
                    "async_audit_dropped_total" to auditLogger.asyncDroppedCount(),
                    "fixed_factor_telemetry_dropped_total" to fixedFactorTelemetry.droppedCount(),
                    "fixed_factor_telemetry_written_total" to fixedFactorTelemetry.writtenCount(),
                    "fixed_factor_telemetry_nonblocking" to true,
                    "fixed_factor_telemetry_file" to "fixed_factor_snapshots.jsonl",
                    "fixed_scan_profile_event" to "FIXED_SCAN_PROFILE",
                    "forecast_scan_profile_event" to "FORECAST_SCAN_PROFILE",
                    "trailing_profile_event" to "TRAILING_MONITOR_PROFILE",
                    "multify_pipeline_event" to "MULTIFY_PIPELINE_PROFILE",
                    "broker_rate_limit_event" to "HTTP_429",
                    "client_governor_wait_event" to "CLIENT_GOVERNOR_WAIT",
                    "forecast_replay_event" to "UNIVERSAL_POSITIVE_EVIDENCE_REFRESH",
                    "positive_evidence_file" to "forecast_positive_evidence.json",
                    "fixed_priority_phase_event" to "FIXED_PRIORITY_PHASE_PROFILE",
                    "fixed_entry_latency_event" to "ENTRY_LATENCY_PROFILE",
                    "fixed_notification_latency_event" to "FIXED_NOTIFICATION_POSTED"
                ))
                fixedSnapshot?.let { add("fixed_engine_snapshot.json", it.payload) }
                addText("system_audit.jsonl", auditLogger.readAll())
            }
        } ?: error("Unable to open export destination")
        return LogExportResult(if (fixedSnapshot != null) 20 else 19, signals.size, trades.size)
    }

    suspend fun shouldCapturePackage(packageName: String): Boolean {
        val filter = (armHotState?.settings ?: preferences.settings.first()).packageFilter.trim()
        return if (filter.isBlank()) packageName.equals(MULTIFY_PACKAGE, ignoreCase = true)
        else packageName.contains(filter, ignoreCase = true)
    }

    suspend fun prepareArmHotPath(force: Boolean = false): Boolean {
        val current = armHotState
        if (!force && current != null && System.currentTimeMillis() - current.warmedAtMs <= ARM_HOT_REFRESH_MS) return true
        val token = ensureToken() ?: return false
        val settings = preferences.settings.first()
        if (!settings.brokerAuthenticated || settings.safetyHalt) return false
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val cnc = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        val now = System.currentTimeMillis()
        armHotState = ArmHotState(token, settings, cnc, now)
        val auditDue = now - lastArmHotAuditAtMs >= ARM_HOT_AUDIT_INTERVAL_MS
        val balanceChanged = !lastArmHotAuditBalance.isFinite() || abs(cnc - lastArmHotAuditBalance) >= 1.0
        val armedChanged = lastArmHotAuditArmed == null || lastArmHotAuditArmed != settings.armEffective
        if (auditDue || balanceChanged || armedChanged) {
            auditLogger.log("RUNTIME", "ARM_HOT_PATH_READY", mapOf(
                "cnc_balance" to cnc,
                "armed" to settings.armEffective
            ))
            lastArmHotAuditAtMs = now
            lastArmHotAuditBalance = cnc
            lastArmHotAuditArmed = settings.armEffective
        }
        return true
    }

    suspend fun maintainArmHotPath() {
        val settings = preferences.settings.first()
        val executionWanted = settings.armEffective || settings.fixedAutoBuyEnabled || settings.forecastAutoBuyEnabled
        if (!executionWanted || settings.safetyHalt) return
        if (armHotState == null ||
            System.currentTimeMillis() - (armHotState?.warmedAtMs ?: 0L) > ARM_HOT_REFRESH_MS
        ) {
            runCatching { prepareArmHotPath(force = true) }
        }
    }

    private suspend fun ensureToken(): String? {
        // Never silently regenerate an expired Groww token. A manual Authenticate action is
        // required after HTTP 401; this prevents background workers from consuming auth quota.
        if (recoveryGate.requiresReconnect()) return null
        val settings = preferences.settings.first()
        if (!settings.brokerAuthenticated) return null
        val token = armHotState?.token?.takeIf { it.isNotBlank() }
            ?: secretStore.getAccessToken()?.takeIf { it.isNotBlank() }
        return token
    }

    private fun preArmGuard(settings: AppSettings) {
        require(tradingRecoveryReady) { "Broker recovery is still reconciling holdings and trailing protection" }
        require(settings.armEffective) { "ARM is OFF" }
        require(settings.brokerAuthenticated) { "Groww is not authenticated" }
        require(settings.staticIpMatched) { "Static IP verification failed" }
        require(settings.brokerDdpiEnabled) { "DDPI is required for CNC holdings" }
        require(!settings.safetyHalt) { "Safety halt is active" }
        require(marketSession() == "OPEN") { "NSE regular market session is not open" }
    }

    private fun preIndependentExecutionGuard(settings: AppSettings) {
        require(tradingRecoveryReady) { "Broker recovery is still reconciling holdings and sell protection" }
        require(settings.brokerAuthenticated) { "Groww is not authenticated" }
        require(settings.staticIpMatched) { "Static IP verification failed" }
        require(settings.brokerDdpiEnabled) { "DDPI is required for CNC holdings" }
        require(!settings.safetyHalt) { "Safety halt is active" }
        require(marketSession() == "OPEN") { "NSE regular market session is not open" }
    }

    private suspend fun placeCncBuy(
        token: String,
        symbol: String,
        qty: Int,
        reference: String,
        notificationStartedNs: Long? = null
    ): OrderPayload = appOwnedBuyMutex.withLock {
        // Serialize all application CNC entries so two independent engines cannot both buy
        // the same symbol while the first order is still awaiting broker acknowledgement.
        val key = symbol.uppercase(Locale.US)
        val broker = brokerInventory(token, force = true)
        val demat = broker.holdings[key]?.quantity ?: 0
        val cnc = broker.positions[key]?.quantity ?: 0
        require(demat <= 0 && cnc <= 0 && managedDao.openPositions().none { it.symbol.equals(symbol, true) }) {
            "BUY blocked: $symbol already exists in Groww/app holdings; ownership must stay separate"
        }
        submitMarketOrder(token, symbol, "BUY", qty, "CNC", reference, notificationStartedNs)
    }

    private fun LongForecastEntity.toForecastDto() = ForecastDto(
        rank = rank, symbol = symbol, confidence = confidence, score = score, reason = reason,
        multifyMatched = multifyMatched, entryPrice = entryPrice, targetPrice = targetPrice, targetPct = targetPct,
        status = status, strategy = strategy, regime = regime, marketRegime = marketRegime,
        championTag = championKey, maxFavourablePct = maxFavourablePct, maxAdversePct = maxAdversePct,
        generatedAtMs = generatedAtMs, qualityTier = qualityTier, executionEligible = executionEligible
    )

    private fun championKey(marketRegime: String, regime: String, strategy: String): String =
        listOf("LONG", marketRegime, regime, strategy).joinToString("|") { it.uppercase(Locale.US).replace("|", "/") }

    private fun fixedChampionKey(marketRegime: String, regime: String, strategy: String): String =
        listOf("LONG", marketRegime, regime, strategy).joinToString("|") { it.uppercase(Locale.US).replace("|", "/") }

    private fun fixedSnapshotDto(snapshot: FixedEngineSnapshot?): FixedSnapshotDto {
        val frozenAverage = snapshot?.let { fixedSnapshotMultifyMfeAverage(it) }
            ?: FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT
        val floor = FixedForecastQualityPolicy.MIN_TARGET_PCT
        return snapshot?.let { fixed ->
            FixedSnapshotDto(
                snapshotId = fixed.payload.snapshotId,
                sourceVersion = fixed.payload.sourceVersion,
                frozenAtMs = fixed.payload.frozenAtMs,
                sha256 = fixed.sha256,
                learningCalls = fixed.payload.learningCalls.size,
                historicalForecasts = fixed.payload.longForecasts.size,
                researchSnapshots = fixed.payload.researchSnapshots.size,
                champions = fixed.payload.champions.size,
                frozenMultifyMfeAveragePct = frozenAverage,
                targetFloorPct = floor,
                fixedTargetPct = max(floor, frozenAverage)
            )
        } ?: FixedSnapshotDto(
            snapshotId = FixedFinalContract.CONTRACT_ID,
            sourceVersion = FixedEngineSnapshotStore.SOURCE_VERSION,
            frozenAtMs = FixedFinalContract.REFERENCE_EXPORT_AT_MS,
            learningCalls = FixedFinalContract.CANONICAL_LEARNING_CALLS,
            historicalForecasts = 0,
            researchSnapshots = 0,
            champions = 0,
            frozenMultifyMfeAveragePct = frozenAverage,
            targetFloorPct = floor,
            fixedTargetPct = max(floor, frozenAverage)
        )
    }

    private suspend fun disconnectedDashboard(settings: AppSettings, fixedSnapshot: FixedEngineSnapshot? = null) = DashboardDto(
        armed = settings.armEffective, halted = settings.safetyHalt, marketSession = marketSession(),
        asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        broker = BrokerStatusDto(configured = hasBrokerCredentials(), connected = false, detail = "Refresh & Authenticate required"),
        risk = RiskStatusDto(maxExposure = settings.holdingBudgetRupees.toDouble(), riskMode = if (settings.safetyHalt) "HALTED" else "DISARMED"),
        fixedSnapshot = fixedSnapshotDto(fixedSnapshot),
        forecastReferences = runCatching { forecastReferenceStats() }.getOrDefault(ForecastReferenceStatsDto()),
        forecastWatchlist = secondaryForecastWatchlist(LocalDate.now(INDIA).toString())
    )

    private fun marketSession(): String {
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6) return "CLOSED"
        val t = now.toLocalTime()
        return if (!t.isBefore(LocalTime.of(9, 15)) && t.isBefore(LocalTime.of(15, 30))) "OPEN" else "CLOSED"
    }

    private fun growwAuthError(e: HttpException): String {
        val raw = runCatching { e.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val parsed = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        val error = parsed?.getAsJsonObject("error")
        val code = error?.get("code")?.asString.orEmpty()
        val message = error?.get("message")?.asString.orEmpty()
        val detail = when {
            message.isNotBlank() && code.isNotBlank() -> "$code · $message"
            message.isNotBlank() -> message
            raw.isNotBlank() -> raw.take(180)
            else -> "Bad request"
        }
        return "Groww TOTP authentication failed (HTTP ${e.code()}): $detail"
    }

    private fun estimatedCashCosts(entry: Double, exit: Double, qty: Int): Double = GrowwDeliveryCostPolicy.estimateCharges(entry, exit, qty).total
    private fun startOfIndiaDayMs(): Long = LocalDate.now(INDIA).atStartOfDay(INDIA).toInstant().toEpochMilli()
    private fun bearer(token: String) = "Bearer $token"
    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun stableRef(prefix: String, seed: String): String = (prefix + sha256(seed).take(14)).take(18)
    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun normalizeIp(v: String) = v.trim().lowercase(Locale.US)
    private fun isValidIp(v: String): Boolean = v.trim().matches(Regex("^[0-9a-fA-F:.]+$")) && v.length in 3..45
    private fun formatEventTime(ms: Long): String = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ms))

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private val HIST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val MIN_BUDGET = 10_000L
        private const val MAX_BUDGET = 200_000L
        private const val MANUAL_SOURCE = "com.multify.traderpro.manual"
        private const val FORECAST_SCAN_INTERVAL_MS = 5L * 60L * 1000L
        private const val FORECAST_OUTCOME_INTERVAL_MS = 60_000L
        private const val FORECAST_OUTCOME_BATCH_ATTEMPTS = 2
        private const val FORECAST_OUTCOME_RETRY_BACKOFF_MS = 750L
        private const val FORECAST_SYMBOL_PACING_MS = 150L
        private const val FORECAST_EVALUATION_TRANCHE_SIZE = 12
        private const val FORECAST_AUTO_BUY_ORDER_PACING_MS = 175L
        private const val FIXED_DIRECT_AUTO_BUY_ORDER_PACING_MS = 175L
        private const val FORECAST_AUTO_BUY_CANCEL_RECHECK_MS = 300L
        private const val FIXED_MIN_STOCK_PRICE_RUPEES = 100.0
        private const val FIXED_PREMARKET_WARMUP_INTERVAL_MS = 5L * 60L * 1000L
        private const val FIXED_V741_FORECAST_SCAN_INTERVAL_MS = 150_000L
        private const val FIXED_V741_FORECAST_SYMBOL_PACING_MS = 25L
        private const val FIXED_V741_PRIMARY_CONFIRMATION_WINDOW_MS = 20L * 60L * 1000L
        private const val FIXED_V741_DETAILED_FORECAST_UNIVERSE_LIMIT = 48
        private const val FIXED_V741_DISCOVERY_DYNAMIC_LIMIT = 28
        private const val FIXED_V741_DISCOVERY_ROTATING_FALLBACK = 16
        private const val FIXED_V741_DISCOVERY_BATCH_PACING_MS = 25L
        private const val FIXED_V741_DISCOVERY_BATCH_ATTEMPTS = 2
        private const val FIXED_V741_DISCOVERY_RETRY_BACKOFF_MS = 750L
        private const val FIXED_V741_DISCOVERY_CONSECUTIVE_FAILURE_ABORT = 3
        private const val FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO = 0.85
        private const val FIXED_V741_RESEARCH_CAPTURE_TOP_N = 48
        private const val FIXED_HOT_PREWARM_MIN_LEAD_MS = 13_000L
        private const val FIXED_HOT_PREWARM_MAX_LEAD_MS = 18_000L
        private const val FIXED_HOT_PREWARM_CONCURRENCY = 8
        private val MARKET_ORDER_FILL_POLL_DELAYS_MS = longArrayOf(125L, 175L, 250L, 350L, 450L, 600L, 750L, 900L, 1_100L, 1_300L)
        private const val FIXED_V741_RESEARCH_SESSION_GOAL = 5
        private const val FIXED_V741_MIN_RESEARCH_EVIDENCE_ROWS = 4
        private const val PRIMARY_CONFIRMATION_WINDOW_MS = 20L * 60L * 1000L
        private const val DISCOVERY_HEALTH_STALE_MS = 12L * 60L * 1000L
        private const val DETAILED_FORECAST_UNIVERSE_LIMIT = 48
        private const val DISCOVERY_DYNAMIC_LIMIT = 28
        private const val DISCOVERY_ROTATING_FALLBACK = 16
        private const val DISCOVERY_BATCH_PACING_MS = 200L
        private const val DISCOVERY_BATCH_ATTEMPTS = 2
        private const val DISCOVERY_RETRY_BACKOFF_MS = 750L
        private const val DISCOVERY_CONSECUTIVE_FAILURE_ABORT = 3
        private const val MIN_DISCOVERY_COVERAGE_RATIO = 0.85
        private const val RESEARCH_CAPTURE_TOP_N = 48
        private const val RESEARCH_BACKFILL_SYMBOL_PACING_MS = 2_500L
        private const val RESEARCH_BACKFILL_SYMBOL_BATCH = 12
        private const val RESEARCH_BACKFILL_MAX_BATCHES_PER_RUN = 3
        private const val RESEARCH_BACKFILL_BUDGET_MS = 150_000L
        private const val BETA_FORECAST_FIXED_GUARD_MS = 10_000L
        private const val RESEARCH_BACKFILL_NETWORK_ABORT_AFTER = 3
        private const val UNIVERSAL_EVIDENCE_NEAREST_SNAPSHOT_MS = 30L * 60L * 1000L
        private const val UNIVERSAL_EVIDENCE_REPLAY_SYMBOL_PACING_MS = 400L
        private const val UNIVERSAL_EVIDENCE_FIXED_REPLAY_BATCH = 12
        private val LEARNING_RESTORE_FILES = setOf(
            "long_learning.json",
            "forecast_research_snapshots.json",
            "long_forecasts.json",
            "long_champions.json",
            "research.json",
            "forecast_positive_evidence.json",
            "fixed_recommendations.json",
            "fixed_engine_snapshot.json",
            "fixed_factor_snapshots.jsonl"
        )
        private const val MAX_RESTORE_ENTRY_BYTES = 48L * 1024L * 1024L
        private const val MAX_RESTORE_TOTAL_BYTES = 64L * 1024L * 1024L
        private const val RESEARCH_SESSION_GOAL = 5
        private const val FORECAST_CALIBRATION_MIN_COMPLETED_ROWS = 5
        private const val FORECAST_CALIBRATION_PREFS = "forecast_v770_live_calibration"
        private const val FORECAST_CALIBRATION_DATES_KEY = "completed_session_dates"
        private const val V7_RESEARCH_COHORT_START_DATE = "2026-10-19"
        private const val MIN_RESEARCH_EVIDENCE_ROWS = 4
        private const val MFE_BACKFILL_INTERVAL_MS = 5L * 60L * 1000L
        private const val MFE_BACKFILL_BATCH_SIZE = 4
        private const val MFE_AFTER_HOURS_BACKFILL_BATCH_SIZE = 16
        private const val ARM_HOT_REFRESH_MS = 20_000L
        private const val ARM_HOT_AUDIT_INTERVAL_MS = 15L * 60L * 1000L
        private const val ARM_HOT_STATE_MAX_AGE_MS = 45_000L
        private const val EXECUTION_SAMPLE_LIMIT = 30
        private const val BULK_MARKET_DATA_MIN_GAP_MS = 210L
        private const val BULK_MARKET_DATA_429_COOLDOWN_MS = 1_500L
        private const val BROKER_INVENTORY_REFRESH_MS = 5_000L
        private const val EMPTY_PORTFOLIO_BROKER_RECHECK_MS = 45_000L
        private const val ACTIVE_GTT_REFRESH_MS = 30_000L
        private const val ACTIVE_GTT_PAGE_SIZE = 50
        private const val MAX_ACTIVE_GTT_PAGES = 20
        private const val PORTFOLIO_AVERAGE_PRICE_EPSILON = 0.005
        private const val PORTFOLIO_CONFLICT_AUDIT_INTERVAL_MS = 60_000L
        private const val PORTFOLIO_REIMPORT_COOLDOWN_MS = 86_400_000L
        private const val CLOSED_SESSION_RECONCILE_MS = 60_000L
        private const val TRAIL_STATE_LOG_INTERVAL_MS = 5_000L
        private const val SMART_ORDER_STATUS_REFRESH_MS = 15_000L
        private const val SMART_ORDER_PRICE_EPSILON = 0.001
        private const val RECOVERY_NET_PROFIT_RUPEES = 100.0
        private const val MULTIFY_PACKAGE = "com.multyfi.invest"
        private const val UPLOADED_THREE_MONTH_REALIZED_PCT = 0.49019417475728155
        private const val DEFAULT_LONG_REALIZED_PCT = 0.33
        const val ENGINE_MULTIFY_HOLDING = "MULTIFY_HOLDING"
        const val ENGINE_FORECAST_HOLDING = "FORECAST_HOLDING"
        const val ENGINE_FIXED_HOLDING = "FIXED_HOLDING"
        const val ENGINE_PORTFOLIO_HOLDING = "PORTFOLIO_HOLDING"
    }
}
