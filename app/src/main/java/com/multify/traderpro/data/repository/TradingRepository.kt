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
import com.multify.traderpro.data.logging.DecisionEvidenceJournal
import com.multify.traderpro.data.logging.FixedFactorTelemetry
import com.multify.traderpro.data.logging.FixedMarketPathTelemetry
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
import com.multify.traderpro.data.network.FixedEarlyWatchDto
import com.multify.traderpro.data.network.FixedPulseResearchDto
import com.multify.traderpro.data.network.FixedOutcomeStatsDto
import com.multify.traderpro.data.network.FixedQuoteTimingRegistry
import com.multify.traderpro.data.network.FixedResearchSafetyDto
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
import com.multify.traderpro.data.network.GrowwOrderBookRow
import com.multify.traderpro.data.network.PositionDto
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.RecentDecisionDto
import com.multify.traderpro.data.network.ResearchDto
import com.multify.traderpro.data.network.UnivestResearchDto
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
import com.multify.traderpro.engine.ExecutionFillQuantityPolicy
import com.multify.traderpro.engine.FixedScanBuyIsolationPolicy
import com.multify.traderpro.engine.IdleBrokerPollingPolicy
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
import com.multify.traderpro.engine.ForecastNewsPatternPolicy
import com.multify.traderpro.engine.ForecastNewsPublicationGate
import com.multify.traderpro.engine.ForecastExpectedAverageGainPolicy
import com.multify.traderpro.engine.ForecastNotificationFreshnessPolicy
import com.multify.traderpro.engine.ForecastUnivestLearningPolicy
import com.multify.traderpro.engine.ForecastQualityPolicy
import com.multify.traderpro.engine.ForecastTargetExpansionPolicy
import com.multify.traderpro.engine.ForecastUniversalPositiveEvidencePolicy
import com.multify.traderpro.engine.ForecastPositiveEvidenceCase
import com.multify.traderpro.engine.ForecastConfirmationPriorityPolicy
import com.multify.traderpro.engine.ForecastDeterministicDeferralPolicy
import com.multify.traderpro.engine.ExecutionEntryPriceGuardPolicy
import com.multify.traderpro.engine.ResearchRateLimitQuietPolicy
import com.multify.traderpro.engine.ReadOnlyFiveMinuteResearchPolicy
import com.multify.traderpro.engine.ResearchCandleFirstTouchPolicy
import com.multify.traderpro.engine.ForecastPublicationPolicy
import com.multify.traderpro.engine.FixedAdaptiveLearningMath
import com.multify.traderpro.engine.FixedForecastPublicationPolicy
import com.multify.traderpro.engine.FixedRepeatQualificationPolicy
import com.multify.traderpro.engine.FixedEntryActionabilityPolicy
import com.multify.traderpro.engine.FixedEarlyWatchPolicy
import com.multify.traderpro.engine.FixedFinalContract
import com.multify.traderpro.engine.RecommendationPriceBandPolicy
import com.multify.traderpro.engine.CircuitAwareBuyPolicy
import com.multify.traderpro.engine.ExecutionQuoteLiquidityPolicy
import com.multify.traderpro.engine.FixedExecutionThinLiquidityPolicy
import com.multify.traderpro.engine.FixedShadowSamplingPolicy
import com.multify.traderpro.engine.FixedForecastQualityDecision
import com.multify.traderpro.engine.FixedForecastQualityPolicy
import com.multify.traderpro.engine.FixedLocalStrategyEngine
import com.multify.traderpro.engine.FixedMarketDiscoveryPolicy
import com.multify.traderpro.engine.FixedMultifyFactorFamilies
import com.multify.traderpro.engine.FixedMultifyFactorModel
import com.multify.traderpro.engine.FixedMultifyReverseEngineering
import com.multify.traderpro.engine.FixedPositiveCloseForecastPolicy
import com.multify.traderpro.engine.FixedScanPerformancePolicy
import com.multify.traderpro.engine.FixedFastCandidateSelectionPolicy
import com.multify.traderpro.engine.FixedPrimaryCarryForwardPolicy
import com.multify.traderpro.engine.FixedFastCandidateSamplingPolicy
import com.multify.traderpro.engine.FixedDiscoveryWindowScheduler
import com.multify.traderpro.engine.FixedHistoricalWindowStore
import com.multify.traderpro.engine.FixedHistoricalSlotPolicy
import com.multify.traderpro.engine.FixedStrategyEvaluation
import com.multify.traderpro.engine.GrowwDeliveryCostPolicy
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import com.multify.traderpro.engine.MarketDataPriorityAdmissionPolicy
import com.multify.traderpro.engine.HardStopLossPolicy
import com.multify.traderpro.engine.AppPositionOwnershipPolicy
import com.multify.traderpro.engine.HoldingSmartOrderPolicy
import com.multify.traderpro.engine.BrokerExecutionSafetyPolicy
import com.multify.traderpro.engine.BrokerBuyFailureMessagePolicy
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
import com.multify.traderpro.engine.NetGainTrailingPolicy
import com.multify.traderpro.engine.ForecastCalibrationCompletionPolicy
import com.multify.traderpro.engine.GttExitReconciliationPolicy
import com.multify.traderpro.engine.StrategyEvaluation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.atomic.AtomicInteger
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.multify.traderpro.R
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
    private val decisionEvidenceJournal: DecisionEvidenceJournal,
    private val fixedFactorTelemetry: FixedFactorTelemetry,
    private val fixedMarketPathTelemetry: FixedMarketPathTelemetry,
    private val forecastNewsMonitor: ForecastNewsMonitor,
    private val forecastNewsJournal: ForecastNewsJournal,
    private val nseMarketCapResearch: NseMarketCapResearch,
    private val forecastUnivestJournal: ForecastUnivestJournal,
    private val forecastSourceNotificationJournal: ForecastSourceNotificationJournal,
    private val researchOutcomeChronologyJournal: ResearchOutcomeChronologyJournal,
    private val marketDiscoveryStateStore: MarketDiscoveryStateStore,
    private val forecastAutoBuyOrderStore: ForecastAutoBuyOrderStore,
    private val fixedEngineSnapshotStore: FixedEngineSnapshotStore,
    private val embeddedHistoricalIntelligenceStore: EmbeddedHistoricalIntelligenceStore,
    private val fixedRecommendationStore: FixedRecommendationStore,
    private val fixedEarlyWatchJournal: FixedEarlyWatchJournal,
    private val fixedMarketPulseResearchStore: FixedMarketPulseResearchStore,
    private val fixedFastCandidatePriceStore: FixedFastCandidatePriceStore,
    private val fixedOutcomePerformanceStore: FixedOutcomePerformanceStore,
    private val fixedHistoricalEvidenceArchive: FixedHistoricalEvidenceArchive,
    private val fixedImportedSameDayDedupJournal: FixedImportedSameDayDedupJournal,
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
    private var forecastBackfillDateCursor = 0
    private var forecastDetailCursor = 0

    // The scheduling timestamp remains frozen; this flag only prevents auto-BUY on incomplete scans.
    @Volatile private var fixedScanDeferredBroker = false
    // In-memory historical OHLCV reuse across Fixed cycles; live latest bars are ALWAYS refetched.
    private val fixedHistoricalWindowStore = FixedHistoricalWindowStore()
    private val fixedPreviouslyDiscoveredPrimaryAt = ConcurrentHashMap<String, Pair<String, Long>>()
    private val fixedReuseDiagnosticLastLoggedAtMs = AtomicLong(0L)
    private val fixedHistoryFullFetches = AtomicLong(0L)
    private val fixedHistoryDeltaFetches = AtomicLong(0L)
    private val fixedHistoryStitchFallbacks = AtomicLong(0L)
    fun fixedHistoricalRequestCounters(): Triple<Long, Long, Long> = Triple(
        fixedHistoryFullFetches.get(), fixedHistoryDeltaFetches.get(), fixedHistoryStitchFallbacks.get()
    )
    @Volatile private var forecastPostFixedOpportunity = false
    private var lastFixedScanAtMs = 0L
    /** A single full-NSE sweep prepared for the NEXT scan, not a separate strategy. */
    private data class PrecomputedFixedSweep(
        val prices: Map<String, Double>,
        val errors: Int, val aborted: Boolean,
        val completedAtMs: Long, val requestCount: Int
    )
    private data class PendingFixedSweep(
        val date: String, val tokenHash: String, val masterHash: String,
        val dueAtMs: Long, val completion: CompletableDeferred<PrecomputedFixedSweep?>
    )
    private val pendingFixedSweepMutex = Mutex()
    private var pendingFixedSweep: PendingFixedSweep? = null
    @Volatile private var fixedSweepPreparationActive = false

    private var lastForecastOutcomeMonitorAtMs = 0L
    private var lastFixedOutcomeMonitorAtMs = 0L
    private var lastFixedCandidateOnlyLtpAtMs = 0L
    private var lastFixedPreMarketWarmupAtMs = 0L
    private var lastFixedHotPrewarmForDueAtMs = 0L
    private var brokerInventorySnapshot: BrokerInventorySnapshot? = null
    @Volatile private var activeGttSnapshot: ActiveGttSnapshot? = null
    private var lastClosedSessionReconcileAtMs = 0L
    private var lastIdlePortfolioAuditAtMs = 0L
    private val lastPortfolioConflictAuditAtMs = ConcurrentHashMap<String, Long>()
    private val gttExitReconcileLastAttemptAtMs = ConcurrentHashMap<Long, Long>()
    private val recentlyClosedSymbolsAtMs = ConcurrentHashMap<String, Long>()
    private var lastArmHotAuditAtMs = 0L
    private var lastMfeBackfillAtMs = 0L
    private var lastArmHotAuditBalance = Double.NaN
    private var lastArmHotAuditArmed: Boolean? = null
    private val closePositionMutexes = ConcurrentHashMap<Long, Mutex>()
    private val appOwnedBuyMutex = Mutex()
    private val protectiveGttCreationMutex = Mutex()
    // One symbol's confirmed fill/protection lifecycle cannot race trailing management.
    private val positionLifecycleMutexes = ConcurrentHashMap<String, Mutex>()
    private val multifyBuyLifecycleMutex = Mutex()
    private val forecastAutoBuySubmissionMutex = Mutex()
    private val fixedAutoBuySubmissionMutex = Mutex()
    private val scanApiCycleMutex = Mutex()
    private val betaForecastCycleMutex = Mutex()
    @Volatile private var fixedApiPriorityRequested = false
    private val marketCriticalTradeCount = AtomicInteger(0)
    private val marketCriticalTradeInFlight: Boolean
        get() = marketCriticalTradeCount.get() > 0
    @Volatile private var tradingRecoveryReady = false
    private val embeddedHistoricalSeedMutex = Mutex()
    private val bulkMarketDataMutex = Mutex()
    private val learningRestoreMutex = Mutex() // Multiple user taps cannot interleave imports.
    private var nextBulkMarketDataAtElapsedMs = 0L
    private val fixedLiveDataRateMutex = Mutex()
    private val fixedLiveDataSecondWindowMs = mutableListOf<Long>()
    private val fixedLiveDataMinuteWindowMs = mutableListOf<Long>()
    private var fixedLiveDataCooldownUntilElapsedMs = 0L
    // Fixed indicator arithmetic runs on a bounded CPU pool; leave at least one
    // processor's worth of headroom for Android, trading safety and UI work.
    // A dispatcher limit does not physically reserve a CPU core.
    private val fixedCpuDispatcher = Dispatchers.Default.limitedParallelism(
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 6)
    )
    private val fixedHistoricalDataMutex = Mutex()
    private val fixedFiveMinuteShadowMutex = Mutex()
    private val fixedFiveMinuteShadowJournal = FixedFiveMinuteShadowJournal(appContext)
    private data class FixedShadowReference(
        val quote: QuotePayload,
        val evaluation: FixedStrategyEvaluation,
        val quoteCapturedAtMs: Long
    )
    private val fixedShadowReferences = ConcurrentHashMap<String, FixedShadowReference>()
    private val fixedShadowPrefs = appContext.getSharedPreferences("fixed_alpha_shadow_schedule_v2", Context.MODE_PRIVATE)
    private var nextFixedHistoricalAtElapsedMs = 0L
    private var fixedHistoricalCooldownUntilElapsedMs = 0L
    @Volatile private var activePortfolioMonitorCount = 0
    private val lastTrailStateAuditAtMs = ConcurrentHashMap<Long, Long>()
    private val lastTrailStateAuditSignature = ConcurrentHashMap<Long, String>()
    private val lastSmartOrderStatusAtMs = ConcurrentHashMap<Long, Long>()

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
        val evidenceDiagnostics: ForecastEvidenceDiagnostics,
        val expectedGain: ForecastExpectedAverageGainPolicy.Estimate
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
        val call = learningDao.latestLiveCallBefore(symbol, eventAtMs) ?: return null
        // Never assign an official close to a stale/past-day or already closed BUY.
        if (call.buyAtMs <= 0L || call.sellEventId != null ||
            !ForecastUnivestLearningPolicy.sameTradingDay(call.buyAtMs, eventAtMs)) {
            auditLogger.logAsync("MULTIFY_RESEARCH", "UNMATCHED_OFFICIAL_CLOSE", mapOf(
                "event_id" to eventId, "symbol" to symbol,
                "reason" to "NO_OPEN_SAME_DAY_BUY", "research_only" to true
            ))
            return null
        }
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

    /**
     * Beta is usable by Fixed only after explicit manual authentication of a
     * separate Groww UCC. Missing, expired or same-account credentials mean
     * Alpha-only Fixed; no automatic Beta login or Alpha order-token borrowing.
     */
    private suspend fun fixedBetaMarketTokenOrNull(): String? {
        if (!secretStore.hasBetaCredentials() || betaSessionInvalid) return null
        val betaToken = secretStore.getBetaAccessToken()?.takeIf { it.isNotBlank() } ?: return null
        val betaUcc = secretStore.getBetaUcc()?.takeIf { it.isNotBlank() } ?: return null
        val alphaUcc = preferences.settings.first().brokerUcc
        if (betaToken == secretStore.getAccessToken() ||
            (alphaUcc.isNotBlank() && betaUcc.equals(alphaUcc, true))) return null
        cachedBetaResearchToken = betaToken
        return betaToken
    }

    private suspend fun fixedBetaLtp(
        token: String, chunk: List<String>
    ): Map<String, Double> = betaMarketRead(
        token, BetaResearchGate.Type.LIVE,
        alphaCall = { error("Fixed Beta lane may not borrow Alpha credentials") },
        betaCall = {
            apiFactory.betaGroww.ltp(bearer(token),
                exchangeSymbols = chunk.joinToString(",") { "NSE_$it" })
                .requirePayload("Fixed Beta NSE LTP")
        }
    )

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
                "origin" to (if (code != null) "BROKER_HTTP_RESPONSE" else if (
                    t is IOException && t.message.orEmpty().startsWith("BETA_")
                ) "LOCAL_ADMISSION" else "TRANSPORT_OR_PRE_DISPATCH"),
                "type" to t.javaClass.simpleName,
                "elapsed_ms" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L),
                "live_requests" to usage.liveRequests, "historical_requests" to usage.historicalRequests,
                "live_429" to usage.liveRateLimits, "historical_429" to usage.historicalRateLimits
            ))
            if (code == null && t is IOException && t.message.orEmpty().startsWith("BETA_")) {
                com.multify.traderpro.data.network.GrowwHttpTraceLedger.localBlock(
                    auditLogger, "BETA", type.name, "GATE_BEFORE_ENDPOINT",
                    t.message.orEmpty().take(120))
            }
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
                    null -> "TOKEN_OR_LOCAL_STATE_UNAVAILABLE"
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

    /**
     * Strictly observational pre-open pass. The regular NSE auction provides
     * indicative equilibrium prices, but Groww's LTP/quote endpoint does NOT
     * document an IEP field or exchange timestamp. These values may be yesterday's
     * closing trade. Never promote this snapshot into official Fixed discovery,
     * prior PRIMARY confirmation, recommendations, notifications or orders.
     *
     * One full read-only LTP sample + up to six frozen 15m model evaluations per
     * date is bounded; actual Fixed publication waits for continuous trading.
     */
    /**
     * Pre-open model: identical frozen 15-minute factor functions, isolated
     * broker I/O and NO changes to primary-observation caches, first-PRIMARY
     * references, historical rolling store, recommendation state or GTTs.
     */
    private suspend fun fixedPreOpenEvaluationReadOnly(symbol: String, alphaToken: String): FixedStrategyEvaluation {
        val betaToken = fixedBetaMarketTokenOrNull()?.takeIf {
            com.multify.traderpro.engine.FixedDualAccountPolicy.betaDetailedEvaluation(symbol, true)
        }
        val useBeta = betaToken != null
        val quote = if (useBeta) {
            betaMarketRead(betaToken!!, BetaResearchGate.Type.LIVE,
                alphaCall = { error("Pre-open Beta quote cannot borrow Alpha") },
                betaCall = { apiFactory.betaGroww.quote(bearer(betaToken), tradingSymbol = symbol)
                    .requirePayload("Preopen quote $symbol") })
        } else withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(alphaToken), tradingSymbol = symbol)
                .requirePayload("Preopen quote $symbol")
        }
        require(RecommendationPriceBandPolicy.eligible(quote.lastPrice)) { "FIXED_PRICE_BAND_OUTSIDE" }
        val now = ZonedDateTime.now(INDIA)
        val start = now.minusDays(4).toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val historical = if (useBeta) {
            betaMarketRead(betaToken!!, BetaResearchGate.Type.HISTORICAL,
                alphaCall = { error("Pre-open Beta historical cannot borrow Alpha") },
                betaCall = { apiFactory.betaGroww.historicalCandles(
                    bearer(betaToken), growwSymbol = "NSE-$symbol", startTime = start,
                    endTime = end, candleInterval = "15minute"
                ).requirePayload("Preopen 15m candles $symbol") })
        } else withFixedHistoricalDataSlot {
            apiFactory.groww.historicalCandles(
                bearer(alphaToken), growwSymbol = "NSE-$symbol", startTime = start,
                endTime = end, candleInterval = "15minute"
            ).requirePayload("Preopen 15m candles $symbol")
        }
        require(historical.candles.size >= 5) { "PREOPEN_INSUFFICIENT_HISTORICAL_15M_CANDLES" }
        return withContext(fixedCpuDispatcher) {
            val features = FixedLocalStrategyEngine.buildFeatures(quote, historical, 15)
            val signal = ParsedSignal(SignalType.TRADE_RELEASE,
                symbol = symbol, rawText = "fixed-preopen-research-only", confidence = 1.0)
            FixedLocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
        }
    }

    suspend fun captureFixedPreOpenResearchOnly(): Boolean {
        val now = ZonedDateTime.now(INDIA)
        val sessionCalendar = com.multify.traderpro.engine.NseEquitySessionCalendar
        if (!sessionCalendar.isPreOpenAuctionWindow(now) ||
            !sessionCalendar.isPreOpenAuctionWindow(now.plusMinutes(5))) return false
        val token = ensureToken() ?: return false
        val master = nseSymbols.symbols().distinct()
        if (master.isEmpty()) return false
        val date = now.toLocalDate().toString()
        val prefs = appContext.getSharedPreferences("fixed_preopen_research_v832", Context.MODE_PRIVATE)
        // Mark attempt before broker calls: a process restart must not multiply
        // pre-open sweeps or compete with the real 09:15/14:20 production scan.
        if (prefs.getString("attempt_date", null) == date ||
            !prefs.edit().putString("attempt_date", date).commit()) return false
        val scanStarted = SystemClock.elapsedRealtime()
        auditLogger.logAsync("FIXED_PREOPEN", "RESEARCH_STARTED", mapOf(
            "date" to date, "phase" to "AUCTION_RESEARCH_ONLY",
            "reference" to "GROWW_LTP_NOT_VERIFIED_NSE_IEP",
            "contains_official_recommendations" to false,
            "real_fixed_formula_changed" to false,
            "special_muhurat" to sessionCalendar.isSpecialMuhurat2026(now)
        ))
        return try {
            val snapshot = ensureFixedEngineSnapshot()
            // The same 53-batch 50-stock full-market data transport is used,
            // but this result is never cached as a valid 09:15/14:20 snapshot.
            val sweep = collectFixedWideLtp(token, master, preparing = false)
            // Audit the actual pre-open bulk LTP path without making new requests.
            // Broker LTP is NOT verified NSE indicative auction equilibrium price.
            fixedMarketPathTelemetry.tryRecord(
                date = date, phase = "PREOPEN_UNVERIFIED_BROKER_LTP",
                scanStartedAtMs = now.toInstant().toEpochMilli(),
                sweepCompletedAtMs = sweep.completedAtMs,
                prices = sweep.prices, universeCount = master.size,
                baselineReady = false
            )
            auditLogger.logAsync("FIXED_PREOPEN", "MARKET_SNAPSHOT_RESEARCH_ONLY", mapOf(
                "date" to date,
                "universe_count" to master.size, "price_count" to sweep.prices.size,
                "account_data_batches" to sweep.requestCount,
                "coverage_pct" to if (master.isNotEmpty()) sweep.prices.size * 100.0 / master.size else 0.0,
                "prices_are_exchange_iep" to false,
                "prices_may_be_previous_session_ltp" to true,
                "usable_for_official_primary_confirmation" to false,
                "usable_for_buy" to false
            ))
            // CPU model uses the exact same original 15m feature and signal
            // implementation, with no effect on the official ranked universe.
            val watch = snapshot.payload.learningCalls
                .asSequence().filter { it.action.equals("BUY", true) }
                .sortedByDescending { it.callDate }
                .map { it.symbol }.filter { it in master }
                .distinct().take(6).toList()
            val targetPct = FixedForecastQualityPolicy.adaptiveTargetPct(
                fixedSnapshotMultifyMfeAverage(snapshot), emptyList()
            )
            val observations = mutableListOf<Map<String, Any?>>()
            for (symbol in watch) {
                if (!sessionCalendar.isPreOpenAuctionWindow(ZonedDateTime.now(INDIA))) break
                try {
                    val evaluation = fixedPreOpenEvaluationReadOnly(symbol, token)
                    val quality = FixedForecastQualityPolicy.evaluate(
                        evaluation, targetPct, "BREADTH_NEUTRAL"
                    )
                    observations += mapOf(
                        "symbol" to symbol,
                        "broker_last_price_unverified_auction" to evaluation.features.ltp,
                        "confidence" to evaluation.confidence,
                        "quality_tier_provisional" to quality.tier,
                        "strategy" to evaluation.strategy,
                        "candle_interval_minutes" to 15,
                        "valid_iep_verified" to false,
                        "eligible_to_publish_or_order" to false
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: Throwable) {
                    observations += mapOf(
                        "symbol" to symbol,
                        "sample_error" to (failed.message ?: failed.javaClass.simpleName).take(140),
                        "eligible_to_publish_or_order" to false
                    )
                }
            }
            auditLogger.logAsync("FIXED_PREOPEN", "ORIGINAL_FIXED_FORMULA_RESEARCH_SAMPLE", mapOf(
                "date" to date,
                "selected_watch_count" to watch.size,
                "observations" to observations,
                "elapsed_ms" to (SystemClock.elapsedRealtime() - scanStarted),
                "full_market_ranking_calculated" to false,
                "data_quality" to "UNVERIFIED_AUCTION_PRICE_AND_PREVIOUS_COMPLETED_15M_CANDLES",
                "production_score_and_target_changed" to false,
                "recommendations_published" to 0,
                "orders_submitted" to 0
            ))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Throwable) {
            auditLogger.logAsync("FIXED_PREOPEN", "RESEARCH_FAILED_NO_TRADE", mapOf(
                "reason" to (failed.message ?: failed.javaClass.simpleName).take(160),
                "orders_submitted" to 0
            ))
            false
        }
    }

    /**
     * November 8 15:15–15:30: NSE non-CAS stocks may still trade, while
     * CAS equities have entered their closing-auction sequence. Without a
     * verified instrument CAS flag, no new recommendation or BUY is allowed.
     * Retain a small, low-frequency read-only quote sample for diagnostics.
     */
    suspend fun captureMuhuratClosingResearchOnly(): Boolean {
        val now = ZonedDateTime.now(INDIA)
        val calendar = com.multify.traderpro.engine.NseEquitySessionCalendar
        if (!calendar.isMuhuratResearchTail(now)) return false
        val prefs = appContext.getSharedPreferences("muhurat_20261108_tail_research", Context.MODE_PRIVATE)
        val bucket = "${now.toLocalDate()}-${now.minute / 2}"
        if (prefs.getString("last_bucket", null) == bucket) return false
        val token = ensureToken() ?: return false
        if (!prefs.edit().putString("last_bucket", bucket).commit()) return false
        val watch = ensureFixedEngineSnapshot().payload.learningCalls.asSequence()
            .filter { it.action.equals("BUY", true) }
            .sortedByDescending { it.callDate }
            .map { it.symbol }.distinct()
            .filter { nseSymbols.isKnown(it) }
            .take(8).toList()
        if (watch.isEmpty()) return false
        return try {
            val betaToken = fixedBetaMarketTokenOrNull()
            val quotes = if (betaToken != null) {
                fixedBetaLtp(betaToken, watch)
            } else {
                fetchFixedDiscoveryLtpBatch(token, watch, 0)
            }
            auditLogger.logAsync("FIXED_MUHURAT", "NONCAS_TAIL_READONLY", mapOf(
                "date" to now.toLocalDate().toString(),
                "sampled_symbols" to watch,
                "sample_size" to watch.size,
                "raw_quote_symbols" to (quotes?.size ?: 0),
                "cas_membership_verified" to false,
                "nse_cas_closed_at" to "15:15_IST",
                "nse_noncas_closes_at" to "15:30_IST",
                "prices_may_be_stale_for_cas" to true,
                "recommendations_published" to 0,
                "orders_submitted" to 0
            ))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Throwable) {
            auditLogger.logAsync("FIXED_MUHURAT", "TAIL_READONLY_FAILED", mapOf(
                "error" to (failed.message ?: failed.javaClass.simpleName).take(140)
            ))
            false
        }
    }

    suspend fun prepareFixedPreMarketWarmup(): Boolean {
        val now = ZonedDateTime.now(INDIA)
        val t = now.toLocalTime()
        if (!com.multify.traderpro.engine.NseEquitySessionCalendar.isWarmupWindow(now)) return false
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
        require(!enabled) { "Forecast scanning is frozen in v8.3; Beta is reserved for Fixed." }
        preferences.setForecastScannerEnabled(false)
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
        require(!enabled) { "Forecast Auto Buy is frozen in v8.3; only Fixed and Multify may trade." }
        // Training-session count is diagnostic only; existing execution and risk guards remain.
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
    private fun buyApiCooldownOrNull(
        vararg categories: GrowwApiUsageLedger.Category
    ): GrowwRecoveryGate.LocallyBlocked? {
        val checked = if (categories.isEmpty())
            listOf(GrowwApiUsageLedger.Category.LIVE_DATA, GrowwApiUsageLedger.Category.NON_TRADING)
        else categories.toList()
        for (category in checked) {
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
                "entry_order_type" to "MARKET",
                "activated_at_ms" to preferences.settings.first().fixedAutoBuyActivatedAtMs,
                "pre_activation_recommendations_ineligible" to true
            )
        )
        if (enabled) {
            ensureFixedEngineSnapshot()
            // Never sweep today's already published ACTIVE recommendations on enable.
            // The next newly published Fixed event is handled by the ordinary scan path.
            val s = preferences.settings.first()
            if (!s.safetyHalt && marketSession() == "OPEN") {
                runCatching { prepareArmHotPath(force = false) }
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
        // No wallet query during authentication. Account identity/DDPI remain verified.
        val expiry = tokenResponse.expiry.orEmpty()
        preferences.updateAuthState(
            authenticated = true, authenticatedAtMs = System.currentTimeMillis(), accessTokenExpiry = expiry,
            brokerUcc = profile.ucc.orEmpty(), brokerDdpiEnabled = profile.ddpiEnabled,
            verifiedPublicIp = publicIp, staticIpMatched = staticMatched
        )
        val cnc = 0.0 // Unknown, never used for execution sizing or reported as wallet cash.
        val refreshedSettings = preferences.settings.first()
        armHotState = ArmHotState(token, refreshedSettings, cnc.coerceAtLeast(0.0), System.currentTimeMillis())
        auditLogger.log("AUTH", "AUTHENTICATION_SUCCESS", mapOf(
            "nse_cash_enabled" to cashEnabled, "ddpi_enabled" to profile.ddpiEnabled,
            "static_ip_matched" to staticMatched, "wallet_balance_not_queried" to true
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
        val forecasts = learningDao.longForecastsForDate(today)
            .filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }.map { it.toForecastDto() }
        val fixedRecords = fixedRecommendationStore.recommendations(today)
            .filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }
        fixedOutcomePerformanceStore.record(fixedRecords)
        fixedHistoricalEvidenceArchive.capture(fixedRecords)
        val fixedHistoricalSummary = fixedHistoricalEvidenceArchive.summary()
        val fixedFirstTouches = researchOutcomeChronologyJournal.recent(2500)
            .filter { it.source == "FIXED" }
        val fixedResearchSafety = FixedResearchSafetyDto(
            historicalSnapshots = fixedHistoricalSummary.rows,
            historicalDates = fixedHistoricalSummary.days,
            historicalObservedTargets = fixedHistoricalSummary.targetObserved,
            historicalUnresolved = fixedHistoricalSummary.unresolved,
            liveSampledTargetFirst = fixedFirstTouches.count { it.firstTouch == "TARGET_OBSERVED_FIRST" },
            liveSampledStopFirst = fixedFirstTouches.count { it.firstTouch == "STOP_OBSERVED_FIRST" },
            liveSampledUnresolved = fixedFirstTouches.count { it.firstTouch == "UNRESOLVED" }
        )
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
        val fixedForecasts = fixedRecords.map {
            fixedPresentationDto(it).copy(observedAfterPublication = it.lastObservedAtMs > it.generatedAtMs)
        }
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
            fixedResearchSafety = fixedResearchSafety,
            forecasts = forecasts,
            fixedForecasts = fixedForecasts,
            forecastWatchlist = secondaryForecastWatchlist(today),
            fixedWatchlist = emptyList(),
            fixedEarlyWatch = fixedEarlyWatchJournal.recent(today),
            fixedPulseResearch = fixedMarketPulseResearchStore.recent(today),
            fixedFastCandidatePrices = fixedFastCandidatePriceStore.recent(today),
            research = research,
            univestResearch = forecastUnivestJournal.recent().map { row ->
                UnivestResearchDto(row.symbol, row.status, row.entryPrice, row.exitPrice,
                    row.outcomePct, row.observedFactorCount,
                    row.entryPriceSource, row.exitPriceSource)
            },
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

    /** Read-only near-term candidates. Never writes orders or alters either scoring engine. */
    private suspend fun secondaryForecastWatchlist(date: String): List<ForecastDto> {
        val published = learningDao.longForecastsForDate(date)
            .map { it.symbol.uppercase(Locale.US) }.toSet()
        val freshAfter = System.currentTimeMillis() - 20L * 60_000L
        return learningDao.forecastResearchForDate(date).asSequence()
            .groupBy { it.symbol.uppercase(Locale.US) }
            .values.mapNotNull { it.maxByOrNull { row -> row.scanAtMs } }
            .filter { it.scanAtMs >= freshAfter && it.qualityTier in setOf("PRIMARY", "SECONDARY", "RESEARCH") }
            .filterNot { it.qualityTier == "PRIMARY" && it.symbol.uppercase(Locale.US) in published }
            .sortedWith(compareByDescending<com.multify.traderpro.data.local.ForecastResearchEntity> {
                it.qualityTier == "PRIMARY"
            }.thenByDescending { it.scanAtMs })
            .take(12)
            .mapIndexed { i, row ->
                ForecastDto(
                    rank = i + 1, symbol = row.symbol, confidence = row.confidence,
                    score = row.directionalScore,
                    reason = if (row.qualityTier == "RESEARCH")
                        "RESEARCH ONLY · Not a BUY recommendation · " + row.rejectionReason
                    else row.rejectionReason,
                    entryPrice = row.entryPrice,
                    targetPct = row.targetPct,
                    targetPrice = row.entryPrice * (1.0 + row.targetPct / 100.0),
                    status = when (row.qualityTier) {
                        "PRIMARY" -> "AWAITING_CONFIRMATION"
                        "SECONDARY" -> "WATCH_ONLY"
                        else -> "RESEARCH_ONLY"
                    },
                    strategy = row.strategy, regime = row.regime,
                    marketRegime = row.marketRegime, generatedAtMs = row.scanAtMs,
                    qualityTier = row.qualityTier, executionEligible = false
                )
            }.toList()
    }

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
            // Dashboard does not poll Groww wallet. All BUYs use the configured budget,
            // and Groww itself decides whether adequate funds exist.
            armHotState = ArmHotState(token, settings, 0.0, System.currentTimeMillis())
            ensureHistoricalSeed()
            runCatching { backfillRecentMultifyMfe(token, MFE_BACKFILL_BATCH_SIZE) }
            val learning = learningStats()
            val forecastLearning = forecastLearningStats()
            val forecastReferences = forecastReferenceStats()
            val snapshot = managedSnapshot(token)
            val execution = executionStats()
            val today = LocalDate.now(INDIA).toString()
            val forecasts = learningDao.longForecastsForDate(today)
            .filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }.map { it.toForecastDto() }
            val fixedForecasts = fixedRecommendationStore.recommendations(today)
                .filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }
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
                    detail = "CNC holdings only · DDPI ${if (settings.brokerDdpiEnabled) "enabled" else "required"} · wallet checked in Groww · NSE master ${symbolStatus.count}"
                ),
                risk = RiskStatusDto(
                    maxExposure = settings.holdingBudgetRupees.toDouble(),
                    riskMode = when { settings.safetyHalt -> "HALTED"; settings.armEffective -> "ARMED"; else -> "DISARMED" }
                ),
                summary = snapshot.summary, execution = execution, learning = learning, forecastLearning = forecastLearning,
                forecastReferences = forecastReferences,
                forecasts = forecasts, fixedForecasts = fixedForecasts,
                forecastWatchlist = secondaryForecastWatchlist(today),
            fixedWatchlist = emptyList(),
            fixedEarlyWatch = fixedEarlyWatchJournal.recent(today),
            fixedPulseResearch = fixedMarketPulseResearchStore.recent(today),
            forecastChampions = champions,
                research = research,
            univestResearch = forecastUnivestJournal.recent().map { row ->
                UnivestResearchDto(row.symbol, row.status, row.entryPrice, row.exitPrice,
                    row.outcomePct, row.observedFactorCount,
                    row.entryPriceSource, row.exitPriceSource)
            },
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
        if (marketCritical) marketCriticalTradeCount.incrementAndGet()
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
                    // Distinct notification coroutines can run concurrently, but broker cash
                    // admission -> confirmed fill -> local holding -> server GTT is atomic.
                    // The next Multify BUY waits, rather than being silently discarded.
                    multifyBuyLifecycleMutex.withLock {
                        handleMultifyBuy(eventId, parsed, token, settings, notificationStartedNs)
                    }
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
            if (marketCritical) marketCriticalTradeCount.decrementAndGet()
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
        val signalAt = ZonedDateTime.now(INDIA)
        if (com.multify.traderpro.engine.NseEquitySessionCalendar.isSpecialMuhurat2026(signalAt) &&
            !com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedNewEntryWindow(signalAt)) {
            dao.updateForwarding(eventId, "ANALYZED", "MUHURAT_ENTRY_WINDOW_CLOSED",
                "Special-session BUY safely blocked outside 14:20–15:00 IST; Multify recommendation still recorded.", null)
            auditLogger.logAsync("EXECUTION", "MUHURAT_MULTIFY_BUY_BLOCKED", mapOf(
                "symbol" to symbol, "at_ist" to signalAt.toString(),
                "new_order_submitted" to false, "sell_monitor_unaffected" to true
            ))
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
        // No pre-BUY funds call. Broker evaluates available cash on order submission.
        val quoteStartedAtMs = SystemClock.elapsedRealtime()
        val entryQuote = apiFactory.groww.quote(
            bearer(token), tradingSymbol = symbol
        ).requirePayload("Multify entry quote $symbol")
        preferences.recordMarketData()
        val quoteElapsedMs = (SystemClock.elapsedRealtime() - quoteStartedAtMs).coerceAtLeast(0L)
        val quoteReceivedAtMs = System.currentTimeMillis()
        val livePrice = entryQuote.lastPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: error("Groww quote does not contain a valid LTP for $symbol")
        val referencePrice = signalReference ?: livePrice
        val bandReference = signal.entryHigh ?: signal.entryLow
        if (bandReference != null) {
            val rejection = ExecutionEntryPriceGuardPolicy.rejectionReason(
                bandReference, livePrice, quoteElapsedMs
            )
            if (rejection != null) {
                auditLogger.log("EXECUTION", "MULTIFY_ENTRY_PRICE_GUARD_REJECTED", mapOf(
                    "symbol" to symbol,
                    "entry_low" to signal.entryLow,
                    "entry_high" to signal.entryHigh,
                    "guard_reference" to bandReference,
                    "fresh_quote" to livePrice,
                    "extension_pct" to ((livePrice / bandReference - 1.0) * 100.0),
                    "maximum_extension_pct" to ExecutionEntryPriceGuardPolicy.MAX_EXTENSION_PCT,
                    "quote_elapsed_ms" to quoteElapsedMs,
                    "reason" to rejection,
                    "buy_submitted" to false
                ))
                error("Multify BUY blocked: entry price moved beyond the allowed 0.40% band ($rejection)")
            }
        }
        auditLogger.log("EXECUTION", "MULTIFY_ENTRY_PRICE_CHECK_PASSED", mapOf(
            "symbol" to symbol,
            "entry_low" to signal.entryLow,
            "entry_high" to signal.entryHigh,
            "reference_price" to referencePrice,
            "fresh_quote" to livePrice,
            "extension_pct" to if (bandReference != null) ((livePrice / bandReference - 1.0) * 100.0) else null,
            "maximum_extension_pct" to ExecutionEntryPriceGuardPolicy.MAX_EXTENSION_PCT,
            "quote_elapsed_ms" to quoteElapsedMs,
            "market_order_fill_can_slip_after_quote" to true
        ))
        val sizing = com.multify.traderpro.engine.BudgetOnlyCncSizing.size(settings.holdingBudgetRupees.toDouble(), referencePrice)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        require(qty > 0) { "Multify BUY skipped: budget cannot fund one buffered share of $symbol" }

        val ref = stableRef("MLH", "$eventId-$symbol")
        val order = placeCncBuy(token, symbol, qty, ref, notificationStartedNs, entryQuote, quoteReceivedAtMs)
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to symbol, "wallet_api_calls" to 0, "broker_funds_verdict_deferred_to_order" to true,
            "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to referencePrice,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty,
            "fresh_quote" to livePrice, "quote_elapsed_ms" to quoteElapsedMs,
            "entry_band_low" to signal.entryLow, "entry_band_high" to signal.entryHigh
        ))
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: referencePrice
        val fillDeviationPct = signal.entryHigh?.takeIf { it > 0.0 }
            ?.let { (entry / it - 1.0) * 100.0 }
        auditLogger.log("EXECUTION", "MULTIFY_ENTRY_FILL_PRICE_DEVIATION", mapOf(
            "symbol" to symbol, "quantity" to filled, "signal_entry_high" to signal.entryHigh,
            "fresh_pre_order_quote" to livePrice, "actual_average_fill" to entry,
            "fill_deviation_from_entry_high_pct" to fillDeviationPct,
            "within_0_40_pct_reference_band" to (fillDeviationPct == null ||
                fillDeviationPct <= ExecutionEntryPriceGuardPolicy.MAX_EXTENSION_PCT),
            "market_order_slippage_cannot_be_capped_after_fill" to true
        ))
        val investedAmount = entry * filled
        val trailArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT
        registerBoughtPositionAndProtect(
            ManagedPositionEntity(
                engine = ENGINE_MULTIFY_HOLDING, symbol = symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = HardStopLossPolicy.stopPrice(entry),
                targetPrice = signal.target,
                strategy = "Multify ARM holding · hard -5% floor · common trail 0.55→0.50, 0.75→0.60, 0.85→0.70 then +0.10%",
                regime = "HOLDING_LONG", confidence = 1.0, sourceEventId = eventId,
                openOrderId = order.growwOrderId, openReferenceId = ref, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                trailingArmPct = trailArmPct, trailingLastRatchetPrice = 0.0,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
            )
        )
        armHotState = hot?.copy(warmedAtMs = System.currentTimeMillis())
        val protectedPosition = managedDao.openPosition(ENGINE_MULTIFY_HOLDING, symbol)
        val protectionVerified = protectedPosition?.let {
            com.multify.traderpro.engine.GttProtectionReadinessPolicy.verified(
                it.smartOrderMode, it.smartOrderId, it.smartOrderStatus)
        } == true
        dao.updateForwarding(
            eventId, "DELIVERED",
            if (protectionVerified) "CNC_LONG_BOUGHT_PROTECTED" else "CNC_LONG_BOUGHT_UNPROTECTED_CRITICAL",
            if (protectionVerified)
                "ARM bought $filled $symbol @ ₹${fmt(entry)}; broker SELL GTT verified. Trail +0.55→+0.50, +0.75→+0.60, +0.85→+0.70, then +0.10."
            else
                "ARM bought $filled $symbol @ ₹${fmt(entry)}, BUT protective SELL GTT was NOT VERIFIED. Check Groww immediately; new BUYs blocked.",
            null
        )
        auditLogger.log("HOLDING", "MULTIFY_LONG_OPENED", mapOf(
            "symbol" to symbol, "qty" to filled, "entry" to entry,
            "invested_amount" to investedAmount,
            "trail_arm_trigger_pct" to trailArmPct,
            "initial_protected_gain_pct" to SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT,
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
            "Multify Book Profit recorded for MFE learning only. $symbol remains held; the common -5% hard stop and fixed stepped profitable trail remain the mandatory sell logic.",
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

    // Only broker availability preflight. Never delays the configured 90s Fixed cadence
    // or bypasses trading controls. A deferred attempt is NOT a completed Fixed scan.
    private fun marketDataAvailableForFixed(): Boolean = runCatching {
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.LIVE_DATA)
        recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.HISTORICAL)
    }.isSuccess

    private fun fixedBrokerCooldownAuditDetails(): Map<String, Any> {
        val state = recoveryGate.status()
        val now = System.currentTimeMillis()
        val historicalUntil = state.categoryCooldownUntilMs[GrowwApiUsageLedger.Category.HISTORICAL] ?: 0L
        val liveUntil = state.categoryCooldownUntilMs[GrowwApiUsageLedger.Category.LIVE_DATA] ?: 0L
        val blocked = buildList {
            if (liveUntil > now) add("LIVE_DATA")
            if (historicalUntil > now) add("HISTORICAL")
            if (state.reconnectRequired) add("AUTH_RECONNECT_REQUIRED")
        }
        val retryAt = maxOf(historicalUntil, liveUntil)
        return mapOf(
            "scan_outcome" to "DEFERRED_NOT_FAILED",
            "blocked_api_categories" to blocked,
            "live_data_cooldown_until_ms" to liveUntil,
            "historical_cooldown_until_ms" to historicalUntil,
            "retry_at_ms" to retryAt,
            "remaining_cooldown_ms" to (retryAt - now).coerceAtLeast(0L),
            "last_completed_fixed_scan_at_ms" to lastFixedScanAtMs,
            "last_completed_fixed_scan_age_ms" to (if (lastFixedScanAtMs > 0L) (now - lastFixedScanAtMs).coerceAtLeast(0L) else -1L),
            "stale_snapshot_never_used_as_fresh_live_data" to true,
            "retry_after_broker_cooldown_not_an_extra_fixed_scan" to true,
            "reconnect_required" to state.reconnectRequired
        )
    }

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
                    ) + fixedBrokerCooldownAuditDetails())
                    return@withLock false
                }
                val previousScanAtMs = lastFixedScanAtMs
                val recommendationStartedAtMs = System.currentTimeMillis()
                generateFixedForecasts(false)
                val recommendationCompletedAtMs = System.currentTimeMillis()
                val scanned = lastFixedScanAtMs != previousScanAtMs && !fixedScanDeferredBroker
                if (scanned) {
                    // Evidence-based execution actionability is outside frozen Fixed scoring.
                    auditFixedActionabilityAfterScan()
                    // Outside FIXED_FINAL_LOCK: read-only publication diagnostics.
                    // Observations include the original tier and the separate safety verdict.
                    val currentDate = LocalDate.now(INDIA).toString()
                    val observations = fixedRecommendationStore.observations(currentDate)
                    val latestScanTime = observations.maxOfOrNull { it.scanAtMs }
                    val latest = observations.filter { it.scanAtMs == latestScanTime }
                    val tierPrimary = latest.filter { it.qualityTier == "PRIMARY" }
                    val prior = observations.filter { it.scanAtMs != latestScanTime }
                    auditLogger.log("FIXED_ENGINE", "PUBLICATION_GUARD_PROFILE", mapOf(
                        "date" to currentDate,
                        "primary_before_overlay" to tierPrimary.size,
                        "primary_entry_safety_held" to tierPrimary.count { !it.publishEligible },
                        "primary_entry_safe" to tierPrimary.count { it.publishEligible },
                        "primary_needing_repeat_confirmation" to tierPrimary.count { item ->
                            item.publishEligible && prior.none { old ->
                                old.symbol.equals(item.symbol, true) &&
                                    old.qualityTier == "PRIMARY" && old.publishEligible
                            }
                        },
                        "published_count" to fixedRecommendationStore.recommendations(currentDate).size,
                        "primary_candidate_diagnostics" to tierPrimary.map { item ->
                            val previous = prior.filter { old ->
                                old.symbol.equals(item.symbol, true) &&
                                    latestScanTime != null && old.scanAtMs < latestScanTime &&
                                    latestScanTime - old.scanAtMs <= FIXED_V741_PRIMARY_CONFIRMATION_WINDOW_MS
                            }
                            val priorConfirmed = previous.filter {
                                it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible
                            }.map { it.scanAtMs }.distinct().size
                            mapOf(
                                "symbol" to item.symbol,
                                "price" to item.observedPrice,
                                "current_primary" to (item.qualityTier == "PRIMARY"),
                                "publish_eligible" to item.publishEligible,
                                "execution_eligible" to item.executionEligible,
                                "prior_primary_scans_in_20m" to priorConfirmed,
                                "had_prior_nonprimary_observation" to prior.any { old ->
                                    old.symbol.equals(item.symbol, true) &&
                                        (old.qualityTier != "PRIMARY" ||
                                            !old.publishEligible || !old.executionEligible)
                                },
                                "previously_published" to fixedRecommendationStore.recommendations(currentDate)
                                    .any { it.symbol.equals(item.symbol, true) }
                            )
                        },
                        "explanatory_detail" to "read-only counts and per-symbol confirmation evidence; original gates unchanged",
                        "pre_spike_discovery_slots" to 0, // original v7.9.17 has no pre-spike bucket,
                        "scoring_or_publication_changed" to false
                    ))
                    // Comprehensive candidate-stage timeline for ALL tiers, not
                    // just PRIMARY. This reports existing observations only:
                    // no factor recalculation, new API request or publication.
                    auditLogger.logAsync("FIXED_EVIDENCE", "ALL_CANDIDATE_CONFIRMATION_TIMELINE", mapOf(
                        "scan_at_ms" to latestScanTime,
                        "source" to "PERSISTED_FIXED_SCAN_OBSERVATIONS",
                        "timestamp_is_scan_start_not_quote_exchange_time" to true,
                        "candidate_count" to latest.size,
                        "candidates" to latest.mapIndexed { index, item ->
                            val history = prior.filter { old ->
                                old.symbol.equals(item.symbol, true) &&
                                    latestScanTime != null && old.scanAtMs < latestScanTime &&
                                    latestScanTime - old.scanAtMs <= FIXED_V741_PRIMARY_CONFIRMATION_WINDOW_MS
                            }
                            val primary = history.filter {
                                it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible
                            }
                            mapOf(
                                "rank_in_observed_top_48" to index + 1,
                                "symbol" to item.symbol,
                                "observed_price" to item.observedPrice,
                                "quality_tier" to item.qualityTier,
                                "publish_eligible" to item.publishEligible,
                                "execution_eligible" to item.executionEligible,
                                "observed_primary_before_current" to primary.size,
                                "first_prior_primary_scan_at_ms" to primary.minOfOrNull { it.scanAtMs },
                                "last_prior_primary_scan_at_ms" to primary.maxOfOrNull { it.scanAtMs },
                                "first_prior_nonprimary_scan_at_ms" to history
                                    .filter { it.qualityTier != "PRIMARY" || !it.publishEligible || !it.executionEligible }
                                    .minOfOrNull { it.scanAtMs },
                                "prior_scans_within_20_min" to history.size,
                                "previously_published" to fixedRecommendationStore.recommendations(currentDate)
                                    .any { it.symbol.equals(item.symbol, true) }
                            )
                        },
                        "for_diagnostics_only" to true,
                        "scoring_and_orders_unchanged" to true
                    ))
                }
                var autoBuySubmitted = 0
                var autoBuyElapsedMs = 0L
                if (scanned) {
                    val autoBuyStartedAtMs = System.currentTimeMillis()
                    // Execution readiness is independent of the already completed frozen
                    // Fixed scan. A GTT exit awaiting broker child confirmation can
                    // block BUY without marking fresh Fixed selection as failed.
                    autoBuySubmitted = FixedScanBuyIsolationPolicy.execute(
                        buy = { autoBuyFixedRecommendations() },
                        onFailure = { cause ->
                            auditLogger.log("FIXED_AUTO_BUY", "EXECUTION_DEFERRED_SCAN_PRESERVED", mapOf(
                                "reason" to (cause.message ?: cause.javaClass.simpleName),
                                "exception_type" to cause.javaClass.simpleName,
                                "fixed_scan_completed" to true,
                                "fixed_selection_or_filtering_changed" to false,
                                "duplicate_buy_attempted" to false,
                                "next_fixed_due_unchanged" to true
                            ))
                        }
                    )
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

    /**
     * Called only AFTER the unchanged Fixed scan and BUY path complete. Uses the
     * original stored first-PRIMARY observations and already fetched quote objects.
     * This is NOT an early BUY or a new qualification rule; no network calls occur.
     */
    fun captureFixedEarlyWatchAfterScan(): Int {
        val date = LocalDate.now(INDIA).toString()
        val observations = fixedRecommendationStore.observations(date)
        val newest = observations.maxOfOrNull { it.scanAtMs } ?: return 0
        // The frozen session-open vector may originate from a fresh installation's
        // first afternoon sweep. Do not label this reference the official NSE open.
        val baselineAuditPrefs = appContext.getSharedPreferences(
            "fixed_shadow_baseline_source_v80", Context.MODE_PRIVATE)
        if (baselineAuditPrefs.getString("date", "") != date) {
            baselineAuditPrefs.edit().putString("date", date)
                .putLong("first_app_observed_scan_at_ms", newest).commit()
            auditLogger.logAsync("FIXED_DIAGNOSTIC", "DISCOVERY_BASELINE_PROVENANCE",
                mapOf("date" to date, "first_app_observed_scan_at_ms" to newest,
                    "source" to "FIRST_APP_OBSERVATION_NOT_VERIFIED_0915_NSE_OPEN",
                    "affects_fixed_discovery_inputs" to false,
                    "new_broker_requests" to 0))
        }
        val official = fixedRecommendationStore.recommendations(date)
        val bySymbol = official.associateBy { it.symbol.uppercase(Locale.US) }
        var captured = 0
        for (o in observations.filter { it.scanAtMs == newest &&
                it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible }) {
            val reference = fixedShadowReferences[o.symbol] ?: continue
            if (!FixedEarlyWatchPolicy.quoteMatchesScan(
                    o.scanAtMs, reference.quoteCapturedAtMs)) continue
            fixedEarlyWatchJournal.observe(date, o, reference.quoteCapturedAtMs,
                reference.quote, bySymbol[o.symbol.uppercase(Locale.US)])
            captured++
        }
        fixedEarlyWatchJournal.reconcileOfficial(date, official)
        if (captured > 0) auditLogger.logAsync("FIXED_EARLY_WATCH", "FIRST_PRIMARY_EVIDENCE_RECONCILED",
            mapOf("scan_started_at_ms" to newest, "qualified_primary_in_last_scan" to captured,
                "network_requests_added" to 0, "early_buy_allowed" to false,
                "frozen_publication_changed" to false))
        return captured
    }

    /** Research-only market pulse, collected AFTER Fixed order path using saved LTPs. */
    fun captureFixedMarketPulseAfterScan(): Int {
        val date = LocalDate.now(INDIA).toString()
        val snapshot = fixedMarketDiscoveryStateStore.load(date)
        val published = fixedRecommendationStore.recommendations(date)
        val newPulses = fixedMarketPulseResearchStore.capture(date, snapshot, published)
        if (newPulses > 0) auditLogger.logAsync("FIXED_PULSE_RESEARCH", "MARKET_PULSE_CAPTURED",
            mapOf("date" to date, "new_symbols" to newPulses,
                "source" to "SUCCESSIVE_FULL_NSE_FIXED_LTP_SWEEPS",
                "verified_nse_open" to false, "new_network_requests" to 0,
                "early_trade_allowed" to false, "fixed_strategy_changed" to false))
        return newPulses
    }

    private fun fixedFirstPrimaryPrice(row: FixedRecommendationRecord): Double? {
        val day = row.recommendationDate
        val allEvents = fixedRecommendationStore.recommendations(day)
        val previousAt = listOfNotNull(
            allEvents.filter {
                it.symbol.equals(row.symbol, true) && it.generatedAtMs < row.generatedAtMs
            }.maxOfOrNull { it.generatedAtMs },
            fixedImportedSameDayDedupJournal.latestImportedAtMs(day, row.symbol)
                ?.takeIf { it < row.generatedAtMs }
        ).maxOrNull()
        val observations = fixedRecommendationStore.observations(day)
        val boundary = previousAt?.let {
            FixedRepeatQualificationPolicy.resetAt(row.symbol, it, observations)
        }
        val firstFresh = observations.asSequence().filter {
            it.symbol.equals(row.symbol, true) &&
                it.scanAtMs <= row.generatedAtMs &&
                (boundary == null && previousAt == null || boundary != null && it.scanAtMs > boundary) &&
                it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible &&
                it.observedPrice?.isFinite() == true && (it.observedPrice ?: 0.0) > 0.0
        }.minByOrNull { it.scanAtMs }?.observedPrice
        // A repeat can never use the first PRIMARY price from a previous setup.
        return firstFresh ?: if (previousAt == null)
            fixedEarlyWatchJournal.recent(day, 1000)
                .firstOrNull { it.symbol.equals(row.symbol, true) }?.firstPrimaryPrice
        else null
    }

    private fun fixedEntryVerdict(
        row: FixedRecommendationRecord, quote: QuotePayload?,
        nowMs: Long = System.currentTimeMillis()
    ): FixedEntryActionabilityPolicy.Verdict {
        // Only use the current scan's existing detailed evaluation for optional
        // quote-high/VWAP/ATR diagnostics. Never fetch another candle or LTP.
        val reference = fixedShadowReferences[row.symbol.uppercase(Locale.US)]?.takeIf {
            it.quoteCapturedAtMs in row.generatedAtMs..nowMs &&
                nowMs - it.quoteCapturedAtMs <= 5L * 60_000L
        }
        val snapshot = reference?.evaluation?.features?.takeIf {
            quote?.lastPrice != null && quote.lastPrice.isFinite() &&
                it.ltp > 0.0 && kotlin.math.abs(quote.lastPrice / it.ltp - 1.0) <= 0.01
        }
        return FixedEntryActionabilityPolicy.evaluate(
            nowMs = nowMs, quote = quote,
            referencePrice = fixedFirstPrimaryPrice(row),
            fixedTargetPct = row.targetPct,
            priorSessionDuplicate = fixedImportedSameDayDedupJournal.contains(
                row.recommendationDate, row.symbol, row.generatedAtMs),
            snapshot = snapshot,
            verifiedSameDayCandleHigh =
                com.multify.traderpro.engine.FixedSameDayHighEvidencePolicy.todayHigh(snapshot, nowMs)
        )
    }

    private fun fixedPublishedQuote(row: FixedRecommendationRecord, nowMs: Long): QuotePayload? =
        fixedShadowReferences[row.symbol.uppercase(Locale.US)]?.takeIf {
            it.quoteCapturedAtMs >= row.generatedAtMs &&
                it.quoteCapturedAtMs <= nowMs &&
                nowMs - it.quoteCapturedAtMs <= 5L * 60_000L
        }?.quote

    private fun fixedPresentationDto(row: FixedRecommendationRecord,
        nowMs: Long = System.currentTimeMillis()): ForecastDto {
        val verdict = fixedEntryVerdict(row, fixedPublishedQuote(row, nowMs), nowMs)
        val riskPrefix = when (verdict.status) {
            "RESEARCH_ONLY" -> "FIXED RESEARCH ONLY · BUY BLOCKED: " + verdict.reasons.joinToString(",") + " · "
            "CAUTION" -> "FIXED ENTRY CAUTION: " + verdict.reasons.joinToString(",") + " · "
            else -> ""
        }
        return row.toDto().copy(
            reason = riskPrefix + row.reason,
            executionEligible = row.executionEligible && verdict.actionable
        )
    }

    /** Separate from the protected Fixed publication/score. No extra API requests. */
    fun auditFixedActionabilityAfterScan() {
        val date = LocalDate.now(INDIA).toString()
        val nowMs = System.currentTimeMillis()
        fixedRecommendationStore.recommendations(date).filter {
            it.generatedAtMs > 0L && nowMs - it.generatedAtMs in 0L..300_000L
        }.forEach { row ->
            val verdict = fixedEntryVerdict(row, fixedPublishedQuote(row, nowMs), nowMs)
            val accuracy = fixedShadowReferences[row.symbol.uppercase(Locale.US)]?.takeIf {
                it.quoteCapturedAtMs >= row.generatedAtMs &&
                    nowMs - it.quoteCapturedAtMs in 0L..300_000L
            }?.evaluation?.features?.let {
                com.multify.traderpro.engine.FixedAccuracyChallengerPolicy.assess(it)
            }
            auditLogger.logAsync("FIXED_ACCURACY_SHADOW", "ENTRY_COMBINATION_AT_PUBLICATION", mapOf(
                "symbol" to row.symbol, "recommendation_id" to row.id,
                "macd_slope" to accuracy?.macdSlope,
                "rsi_slope" to accuracy?.rsiSlope,
                "book_imbalance" to accuracy?.orderBookImbalance,
                "session_vwap_extension_atr" to accuracy?.vwapExtensionAtr,
                "three_factor_pass" to accuracy?.threeFactorConfirmed,
                "peak_extension_warning" to accuracy?.extendedFromSessionVwap,
                "rsi_momentum_exhaustion" to accuracy?.rsiMomentumExhaustion,
                "verified_inputs_available" to accuracy?.dataReady,
                "validation_status" to "SHADOW_NOT_FOR_EXECUTION",
                "added_api_requests" to 0
            ))
            auditLogger.logAsync("FIXED_ENTRY_QUALITY", "PUBLICATION_ACTIONABILITY", mapOf(
                "symbol" to row.symbol, "scan_started_at_ms" to row.generatedAtMs,
                "actual_evaluated_at_ms" to nowMs, "status" to verdict.status,
                "reasons" to verdict.reasons, "first_primary_to_entry_pct" to verdict.firstPrimaryRunupPct,
                "distance_from_broker_or_15m_candle_high_pct" to verdict.distanceFromQuoteHighPct,
                "day_high_evidence_source" to verdict.highEvidenceSource,
                "multi_day_15m_window_high_distance_pct_NOT_day_high" to verdict.multiDayWindowHighDistancePct,
                "vwap_atr_source" to "VERIFIED_CURRENT_SESSION_COMPLETED_BARS",
                "distance_above_vwap_in_atr" to verdict.vwapAtrExtension,
                "first_primary_runup_minus_target_pct" to verdict.firstPrimaryRunupBeyondTargetPct,
                "fixed_scoring_changed" to false, "fixed_stored_recommendation_changed" to false,
                "buy_guard_enabled" to true))
        }
    }

    /** Client-side response receipt only, NOT provider exchange-tick timestamp. */
    fun fixedQuoteReceivedAtForNotification(
        symbol: String, scanStartedAtMs: Long
    ): Long? {
        val reference = fixedShadowReferences[symbol.uppercase(Locale.US)] ?: return null
        if (reference.quoteCapturedAtMs < scanStartedAtMs) return null
        return FixedQuoteTimingRegistry.closestCompletedQuote(
            symbol, scanStartedAtMs, reference.quoteCapturedAtMs
        )?.responseReceivedAtMs
    }

    fun fixedRecommendationsForNotification(): List<ForecastDto> {
        val today = LocalDate.now(INDIA).toString()
        return fixedRecommendationStore.recommendations(today)
            .filter {
                it.status == "ACTIVE" &&
                    it.executionEligible &&
                    it.qualityTier == "PRIMARY" &&
                    RecommendationPriceBandPolicy.eligible(it.entryPrice) &&
                    !fixedImportedSameDayDedupJournal.contains(today, it.symbol, it.generatedAtMs)
            }
            .sortedBy { it.generatedAtMs }
            .map { fixedPresentationDto(it) }
    }

    suspend fun runForecastOpportunisticCycle(postFixedOpportunity: Boolean = false): Boolean {
        // v8.3 hard freeze: no Forecast scanner or auto-buy, even from legacy UI/actions.
        return false
        @Suppress("UNREACHABLE_CODE")
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
        preferences.settings.first().brokerAuthenticated && !recoveryGate.requiresReconnect() &&
            managedDao.openPositions().filter(::appOwned).all {
                com.multify.traderpro.engine.GttProtectionReadinessPolicy.verified(
                    it.smartOrderMode, it.smartOrderId, it.smartOrderStatus)
            }

    private fun millisUntilNextFixedScan(): Long {
        val last = lastFixedScanAtMs
        if (last <= 0L) return 0L
        return (last + FIXED_V741_FORECAST_SCAN_INTERVAL_MS - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    suspend fun activateForecastEarlyEntryOnce() = preferences.activateForecastEarlyEntryOnce()

    suspend fun forecastBackgroundScanningEnabled(): Boolean = false // v8.3 hard freeze: never issue Forecast reads

    /** Research-only pacing; avoids waking Forecast every poll during active broker cooldown.
     * Beta uses independent credentials and remains unaffected by Alpha cooldowns.
     */
    fun forecastBrokerCooldownRemainingMs(): Long {
        if (secretStore.hasBetaCredentials()) return 0L
        val status = recoveryGate.status()
        if (status.reconnectRequired) return 30_000L
        val now = System.currentTimeMillis()
        val until = listOf(GrowwApiUsageLedger.Category.LIVE_DATA,
            GrowwApiUsageLedger.Category.HISTORICAL, GrowwApiUsageLedger.Category.NON_TRADING)
            .maxOf { status.categoryCooldownUntilMs[it] ?: 0L }
        return (until - now).coerceAtLeast(0L)
    }

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
        if (marketCriticalTradeInFlight) return false
        if (secretStore.hasBetaCredentials()) {
            // Beta uses a separate credential, quota, and mutex. Permit a bounded research
            // tranche during an Alpha Fixed scan instead of starving Forecast for the whole cycle.
            return betaResearchAuthenticated() && ForecastBetaPriorityPolicy.canResearch(
                fixedPriorityRequested = fixedApiPriorityRequested,
                criticalTradeInFlight = marketCriticalTradeInFlight,
                fixedDeferredForBroker = fixedScanDeferredBroker,
                millisUntilNextFixed = millisUntilNextFixedScan(),
                fixedGuardMs = forecastFixedGuardMs(),
                allowIndependentBetaDuringFixed = true
            )
        }
        if (fixedApiPriorityRequested) return false
        // A cooldown is an immediate no-go. Do not repeatedly request a blocked endpoint.
        // Trading and Fixed categories are never reserved for this research engine.
        if (!forecastBrokerHealthyForResearch()) return false
        val usage = GrowwApiUsageLedger.snapshot()
        fun row(category: GrowwApiUsageLedger.Category) = usage.categories.first { it.category == category }
        val live = row(GrowwApiUsageLedger.Category.LIVE_DATA)
        val historical = row(GrowwApiUsageLedger.Category.HISTORICAL)
        val nonTrading = row(GrowwApiUsageLedger.Category.NON_TRADING)
        // Discretionary Alpha research remains quiet for five minutes after any
        // Alpha 429. Protection, BUY/SELL and frozen Fixed are unaffected.
        val latestAlpha429 = maxOf(live.lastRateLimitAtMs,
            historical.lastRateLimitAtMs, nonTrading.lastRateLimitAtMs)
        if (!ResearchRateLimitQuietPolicy.canRun(System.currentTimeMillis(), latestAlpha429)) return false
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

    /** Make completed official same-day Multify winners part of cross-symbol
     * Forecast factor learning. Only previously stored entry-time features qualify.
     * This is offline/local and never changes Fixed or requests a broker quote.
     */
    private suspend fun materializeOfficialMultifyWinnerFeatures(): Int {
        val calls = learningDao.allCalls().filter {
            ForecastMultifyPositiveTransferPolicy.qualifiesVerifiedClose(
                it.source, it.action, it.buyEventId, it.sellEventId,
                it.buyAtMs, it.sellAtMs, it.longRealizedPct
            )
        }
        if (calls.isEmpty()) return 0
        val references = learningDao.allForecastResearch()
            .filter { it.qualityTier == "MULTIFY_REFERENCE" && it.multifyEventId != null }
            .groupBy { it.multifyEventId }
        var count = 0
        for (call in calls) {
            val ref = references[call.buyEventId]?.filter {
                it.scanAtMs >= call.buyAtMs && it.scanAtMs <= (call.sellAtMs ?: 0L)
            }?.minByOrNull { it.scanAtMs } ?: continue
            if (ref.featureJson.isBlank() || ref.featureJson == "{}") continue
            val info = evidenceFeatureSummary(ref.featureJson)
            val gain = call.longRealizedPct ?: continue
            val mfe = call.multifyMfePct ?: gain
            learningDao.upsertForecastPositiveEvidence(ForecastPositiveEvidenceEntity(
                evidenceKey = "MULTIFY_OFFICIAL_SAME_DAY|${call.buyEventId}",
                source = "MULTIFY_OFFICIAL_CLOSE", evidenceDate = call.callDate,
                eventAtMs = ref.scanAtMs, symbol = call.symbol.uppercase(Locale.US),
                strategy = ref.strategy, regime = ref.regime, marketRegime = ref.marketRegime,
                dominantFamily = info.dominantFamily, familyConsensus = info.familyConsensus,
                momentumFamilyScore = info.momentum, participationFamilyScore = info.participation,
                structureFamilyScore = info.structure, factorJson = ref.featureJson,
                voteJson = ref.voteJson, entryPrice = call.entryPrice,
                targetPct = call.targetPct, maxFavourablePct = mfe,
                closePct = gain, realizedPct = gain, protectedTrailPct = null,
                trailArmed = false, targetHit = false,
                positiveReason = "OFFICIAL_SAME_DAY_BOOK_PROFIT",
                qualityTier = "EVIDENCE", reconstructionQuality = "ENTRY_FACTORS_OFFICIAL_EXIT",
                updatedAtMs = System.currentTimeMillis()
            ))
            count++
        }
        if (count > 0) auditLogger.logAsync("MULTIFY_RESEARCH", "OFFICIAL_SAME_DAY_FACTOR_LEARNING", mapOf(
            "cases_with_entry_factors" to count, "all_official_positive_closes" to calls.size,
            "fixed_formula_changed" to false
        ))
        return count
    }

    private suspend fun multifyPositiveTransferCases(beforeDate: String): List<MultifyPositiveTransferCase> {
        // Confirmed same-session Multify BOOK PROFIT only. Hypothetical MFE/trailing
        // movements stay in a separate research pool and cannot masquerade as a close.
        val positives = learningDao.allCalls().filter {
            it.callDate < beforeDate &&
                ForecastMultifyPositiveTransferPolicy.qualifiesVerifiedClose(
                    it.source, it.action, it.buyEventId, it.sellEventId,
                    it.buyAtMs, it.sellAtMs, it.longRealizedPct
                )
        }
        val dates = positives.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        return positives.filter { it.callDate in dates }.map {
            val mfe = it.multifyMfePct ?: 0.0
            MultifyPositiveTransferCase(
                symbol = it.symbol.uppercase(Locale.US),
                realizedPct = it.longRealizedPct ?: 0.0,
                maxFavourablePct = mfe
            )
        }
    }

    private suspend fun universalPositiveEvidenceCases(beforeDate: String): List<ForecastPositiveEvidenceCase> {
        val rows = learningDao.allForecastPositiveEvidence()
            .filter { it.evidenceDate < beforeDate }
        val dates = rows.map { it.evidenceDate }.distinct().sortedDescending().take(30).toSet()
        val eligible = rows.filter { it.evidenceDate in dates }
        val independent = eligible.groupBy { it.source.uppercase(Locale.US) + "|" +
            it.evidenceDate + "|" + it.symbol.uppercase(Locale.US) }
            .values.mapNotNull { cases -> cases.minByOrNull { it.eventAtMs } }
        if (eligible.size != independent.size) auditLogger.logAsync("FORECAST_LEARNING",
            "POSITIVE_EVIDENCE_DEDUPED", mapOf(
                "raw_rows" to eligible.size, "independent_rows" to independent.size,
                "duplicate_rows_removed_from_ranking" to (eligible.size - independent.size),
                "raw_evidence_preserved_in_export" to true))
        return independent.map {
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
                protectedTrailPct = it.protectedTrailPct,
                observedAtMs = it.eventAtMs
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

    // Forecast signals and optional Auto Buy are no longer locked behind five sessions.
    // Broker readiness, qualifying PRIMARY entry, spread, economics and common SELL remain mandatory.
    suspend fun forecastLiveUnlocked(): Boolean = true

    suspend fun generateDailyForecasts(force: Boolean = false): List<ForecastDto> {
        runCatching { ensureFixedEngineSnapshot() }
        ensureHistoricalSeed()
        val now = ZonedDateTime.now(INDIA)
        val today = now.toLocalDate().toString()
        val existing = learningDao.longForecastsForDate(today)
        if (now.dayOfWeek.value >= 6 || now.toLocalTime() < LocalTime.of(9, 15) || now.toLocalTime() > LocalTime.of(15, 0)) {
            return existing.filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }.map { it.toForecastDto() }
        }
        val nowMs = System.currentTimeMillis()
        val scanInterval = if (forecastDiscoveryNextBatch > 0 || forecastDiscoveryMustClear || forecastWaitingForSharedBaseline || forecastDetailQueue.isNotEmpty())
            45_000L else FORECAST_SCAN_INTERVAL_MS
        if (!force && nowMs - lastForecastScanAtMs < scanInterval) return existing.filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }.map { it.toForecastDto() }
        val token = forecastMarketToken() ?: return existing.filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }.map { it.toForecastDto() }
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
            // Keep researching from a bounded symbol universe even on a cold or
            // incomplete market-wide pass. ForecastPublicationPolicy still requires
            // a READY baseline: no orders or production BUYs from incomplete data.
        }

        val slot = (now.hour * 60 + now.minute) / 30
        val rotating = if (master.isEmpty()) emptyList() else {
            val seed = kotlin.math.abs(today.hashCode() + slot * 977)
            (0 until min(DISCOVERY_ROTATING_FALLBACK, master.size)).map { i ->
                master[Math.floorMod(seed + i * 131, master.size)]
            }
        }
        // Re-evaluate recent PRIMARY stocks while their 20-minute confirmation
        // window is still open. No trading/quality gate is relaxed.
        val publishedSymbols = existing.map { it.symbol.uppercase(Locale.US) }.toSet()
        val confirmationFollowups = learningDao.forecastResearchForDate(today)
            .asSequence()
            .filter {
                it.scanAtMs < nowMs && nowMs - it.scanAtMs in 0L..PRIMARY_CONFIRMATION_WINDOW_MS &&
                    it.qualityTier == "PRIMARY" && it.publishEligible && it.executionEligible &&
                    it.symbol.uppercase(Locale.US) !in publishedSymbols &&
                    it.symbol.uppercase(Locale.US) !in discoveredSymbols
            }
            .sortedByDescending { it.scanAtMs }
            .map { it.symbol.uppercase(Locale.US) }
            .distinct().take(8).toList()
        val universe = (discoveredSymbols + confirmationFollowups + dnaSymbols + recentSymbols + rotating)
            .distinct()
            .take(DETAILED_FORECAST_UNIVERSE_LIMIT)
        // v7.9.4: partial Forecast work must not be thrown away whenever Fixed starts.
        // Evaluate fresh quotes/candles, one bounded tranche per opportunistic cycle.
        // Any qualified/watching candidates are captured this cycle for after-market research.
        val betaConcurrentWithFixed = secretStore.hasBetaCredentials() && fixedApiPriorityRequested
        val normalTrancheSize = ForecastResearchWindowPolicy.trancheSize(
            forecastBoostActive(), forecastPostFixedOpportunity, FORECAST_EVALUATION_TRANCHE_SIZE
        )
        // Keep concurrent Beta work small so Fixed retains CPU and Alpha API priority.
        val trancheSize = if (betaConcurrentWithFixed) normalTrancheSize.coerceAtMost(2) else normalTrancheSize
        val tranche = forecastNextEvaluationTranche(today, universe, confirmationFollowups).take(trancheSize)
        val evaluated = mutableListOf<Pair<String, StrategyEvaluation>>()
        val detailedEvaluationStartedElapsedMs = SystemClock.elapsedRealtime()
        var processed = 0
        // Reuse a fresh Fixed NSE sweep to avoid expensive repeated historical
        // requests for stocks far outside the allowed Forecast price band.
        val priceSnapshot = fixedMarketDiscoveryStateStore.load(today)
        for (symbol in tranche) {
            if (marketCriticalTradeInFlight ||
                (fixedApiPriorityRequested && !secretStore.hasBetaCredentials()) ||
                !forecastMarketCapacityAvailable()
            ) {
                auditLogger.log("FORECAST", "EVALUATION_PAUSED_FOR_FIXED", mapOf(
                    "processed" to processed,
                    "remaining" to forecastDetailQueue.size - forecastDetailCursor,
                    "reason" to "FIXED_PRIORITY_OR_CAPACITY"
                ))
                break
            }
            // Advance only after attempting the symbol. A failed API request is revisited
            // in the next complete evaluation cycle; it cannot poison today's remaining queue.
            val knownPrice = priceSnapshot?.previousLtps?.get(symbol.uppercase(Locale.US))
            if (ForecastDeterministicDeferralPolicy.defer(
                    knownPrice, priceSnapshot?.updatedAtMs ?: 0L, System.currentTimeMillis())) {
                forecastDetailCursor++
                processed++
                auditLogger.logAsync("FORECAST_RESEARCH", "KNOWN_PRICE_BAND_DEFERRED", mapOf(
                    "symbol" to symbol, "cached_price" to knownPrice, "snapshot_age_ms" to
                        (System.currentTimeMillis() - (priceSnapshot?.updatedAtMs ?: 0L)),
                    "fresh_quote_on_borderline_or_stale" to true,
                    "fixed_model_changed" to false))
                continue
            }
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
                val deterministic = it is IllegalArgumentException &&
                    it.message == "FORECAST_PRICE_BAND_OUTSIDE"
                auditLogger.log("FORECAST_RESEARCH",
                    if (deterministic) "EVALUATION_FILTERED_PRICE_BAND" else "EVALUATION_ERROR", mapOf(
                    "symbol" to symbol, "message" to (it.message ?: it.javaClass.simpleName),
                    "broker_error" to !deterministic))
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
        val priorMfe = priorForecasts.map { it.maxFavourablePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val priorForecastDistinctDays = priorForecasts.map { it.forecastDate }.distinct().size
        val priorForecastDistinctSymbols = priorForecasts.map { it.symbol.uppercase(Locale.US) }.distinct().size
        val targetPct = ForecastQualityPolicy.adaptiveTargetPct(
            learned.longAveragePct, priorMfe, priorForecastDistinctDays, priorForecastDistinctSymbols
        )

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
        val completedForecastOutcomes = priorResearchAll.mapNotNull { row ->
            val mfe = row.outcomeMfePct ?: return@mapNotNull null
            if (!mfe.isFinite() || mfe < 0.0 || mfe >= 25.0) return@mapNotNull null
            ForecastHistoricalOutcome(
                date = row.researchDate, symbol = row.symbol,
                strategy = row.strategy, regime = row.regime, marketRegime = row.marketRegime,
                maxFavourablePct = mfe, maxAdversePct = row.outcomeMaePct ?: 0.0,
                closePct = row.outcomeClosePct ?: 0.0, targetHit = row.outcomeTargetHit == true
            )
        }
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
        val univestCompleted = univestHistoricalCases(today)
        val univestResults = univestCompleted.map { it.date }.distinct().size
        auditLogger.logAsync("FORECAST_UNIVEST", "SAME_DAY_LEARNING_PROFILE", mapOf(
            "completed_cases_previous_days" to univestCompleted.size,
            "distinct_days" to univestResults,
            "positive_cases" to univestCompleted.count { it.returnPct > 0.0 },
            "positive_hit_rate" to (univestCompleted.count { it.returnPct > 0.0 }.toDouble() /
                univestCompleted.size.coerceAtLeast(1)),
            "minimum_closed" to ForecastUnivestLearningPolicy.MIN_CLOSED_COHORT,
            "minimum_hit_rate" to ForecastUnivestLearningPolicy.MIN_WIN_RATE,
            "training_source" to "OFFICIAL_1_TO_3_MONTH_ENTRY_SAME_DAY_EXIT_ONLY",
            "feature_count" to 48, "fixed_engine_changed" to false))

        val ranked = evaluated.map { row ->
            val baseQuality = com.multify.traderpro.engine.ForecastEarlyEntryPolicy.quality(row.second, targetPct)
            val expandedTargetPct = ForecastTargetExpansionPolicy.targetPct(
                baseTargetPct = targetPct,
                symbol = row.first,
                strategy = row.second.strategy,
                regime = row.second.regime,
                marketRegime = marketRegime,
                volatilityCapacityPct = baseQuality.volatilityCapacityPct,
                positives = fixedPositiveTransfer
            )
            val expandedQuality = com.multify.traderpro.engine.ForecastEarlyEntryPolicy.quality(row.second, expandedTargetPct)
            val useExpandedTarget = expandedTargetPct > targetPct &&
                expandedQuality.tier == baseQuality.tier &&
                expandedQuality.publishEligible == baseQuality.publishEligible &&
                expandedQuality.executionEligible == baseQuality.executionEligible
            val candidateTargetPct = if (useExpandedTarget) expandedTargetPct else targetPct
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
            val univestBonus = if (quality.publishEligible) ForecastUnivestLearningPolicy.evaluate(
                univestCompleted,
                ForecastUnivestLearningPolicy.fingerprint(factorModel, row.second, marketRegime), today
            ).bonus else 0.0
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
            val expectedGain = ForecastExpectedAverageGainPolicy.estimate(
                row.second.strategy, row.second.regime, marketRegime, completedForecastOutcomes)
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
                    fixedPositiveBonus + multifyPositiveBonus + historicalReferenceBonus + preSpikeBonus + universalEvidenceBonus + univestBonus
                ).coerceIn(0.01, .99),
                championKey = key,
                targetPct = candidateTargetPct,
                economics = economics,
                evidenceDiagnostics = evidenceDiagnostics,
                expectedGain = expectedGain
            )
        }.sortedWith(
            compareByDescending<ForecastCandidate> {
                when (it.quality.tier) { "PRIMARY" -> 3; "SECONDARY" -> 2; else -> 1 }
            }.thenByDescending { it.score }
                .thenByDescending { it.economics.riskAdjustedScore }
        )

        // Research-only visibility: never relax PRIMARY or enable a research BUY.
        auditLogger.log("FORECAST_RESEARCH", "PRODUCTION_REJECTION_PROFILE", mapOf(
            "date" to today,
            "evaluated" to evaluated.size,
            "primary" to ranked.count { it.quality.tier == "PRIMARY" },
            "secondary" to ranked.count { it.quality.tier == "SECONDARY" },
            "research" to ranked.count { it.quality.tier == "RESEARCH" },
            "spread_unavailable" to ranked.count { it.evaluation.features.spreadBps == null },
            "net_economics_unprofitable" to ranked.count { !it.economics.profitableAfterCosts },
            "expected_avg_gain_gt_1pct" to ranked.count { it.expectedGain.eligible },
            "average_gain_not_yet_validated" to ranked.count { it.expectedGain.averageMfePct == null },
            "average_gain_pct_threshold_exclusive" to 1.0,
            "rupee_profit_publication_threshold" to "NONE",
            "adaptive_target_pct" to targetPct,
            "multify_baseline_mfe_pct" to learned.longAveragePct,
            "prior_forecast_mfe_samples" to priorMfe.size,
            "prior_forecast_distinct_days" to priorForecastDistinctDays,
            "prior_forecast_distinct_symbols" to priorForecastDistinctSymbols,
            "forecast_mfe_calibration_enabled" to (
                priorMfe.size >= ForecastQualityPolicy.TARGET_CALIBRATION_MIN_ROWS &&
                    priorForecastDistinctDays >= ForecastQualityPolicy.TARGET_CALIBRATION_MIN_DISTINCT_DAYS &&
                    priorForecastDistinctSymbols >= ForecastQualityPolicy.TARGET_CALIBRATION_MIN_DISTINCT_SYMBOLS
            ),
            "five_minute_capacity_rejections" to ranked.count {
                it.quality.failedRules.any { rule -> rule.contains("volatility capacity") || rule.contains("five-minute ATR") }
            },
            "five_minute_context_rejections" to ranked.count {
                it.quality.failedRules.any { rule -> rule.contains("completed 5-minute context") }
            },
            "multi_peak_retest_candidates" to ranked.count { it.quality.reason.contains("multi-peak retest", true) },
            "quality_rejection_counts" to ranked.flatMap { it.quality.failedRules }.groupingBy { it }.eachCount(),
            "top_quality_reasons" to ranked.asSequence()
                .filter { it.quality.tier != "PRIMARY" }.map { it.quality.reason.take(150) }
                .take(6).toList(),
            "production_quality_relaxed" to false,
            "research_account" to if (secretStore.hasBetaCredentials()) "BETA" else "ALPHA"
        ))
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
        val liveForecastReady = true // Forecast-only: no five-session recommendation gate
        val publishable = unseen.filter { item ->
            if (!RecommendationPriceBandPolicy.eligible(item.evaluation.features.ltp)) return@filter false
            val currentPrimary =
                item.quality.tier == "PRIMARY" && item.quality.publishEligible && item.quality.executionEligible &&
                    item.expectedGain.eligible
            ForecastPublicationPolicy.isReady(
                discoveryBaselineReady = discoveryPass.baselineReady,
                currentPrimary = currentPrimary,
                discoveredMarketWide = item.symbol in discoveredSymbols,
                priorPrimaryHits = priorPrimaryHits(item.symbol),
                researchOnly = !liveForecastReady
            )
        }

        var publishedFresh = 0
        publishable.forEach { item ->
            // A fresh quote is mandatory before publishing; never bypass Fixed/order priority.
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight ||
                !forecastMarketCapacityAvailable()) {
                auditLogger.logAsync("FORECAST", "FRESH_QUOTE_DEFERRED", mapOf(
                    "symbol" to item.symbol, "reason" to "BROKER_PRIORITY_OR_CAPACITY"))
                return@forEach
            }
            val fresh = runCatching {
                withForecastLiveDataSlot {
                    betaMarketRead(token, BetaResearchGate.Type.LIVE,
                        alphaCall = { apiFactory.groww.quote(bearer(token), tradingSymbol = item.symbol)
                            .requirePayload("Fresh Forecast publish quote ${item.symbol}") },
                        betaCall = { apiFactory.betaGroww.quote(bearer(token), tradingSymbol = item.symbol)
                            .requirePayload("Fresh Beta Forecast publish quote ${item.symbol}") })
                }
            }.getOrNull()
            val price = fresh?.lastPrice?.takeIf { RecommendationPriceBandPolicy.eligible(it) }
            val oldPrice = item.evaluation.features.ltp
            val oldTargetPrice = oldPrice * (1.0 + item.targetPct / 100.0)
            val moveFromSignalPct = price?.let { (it / oldPrice - 1.0) * 100.0 }
            val remainingUpsidePct = price?.let { (oldTargetPrice / it - 1.0) * 100.0 }
            if (price == null || moveFromSignalPct == null || remainingUpsidePct == null ||
                !moveFromSignalPct.isFinite() || !remainingUpsidePct.isFinite() ||
                moveFromSignalPct !in -0.50..0.40 || remainingUpsidePct <= 1.0) {
                auditLogger.logAsync("FORECAST", "PUBLICATION_FRESHNESS_REJECTED", mapOf(
                    "symbol" to item.symbol, "original_price" to oldPrice,
                    "fresh_price" to price, "signal_move_pct" to moveFromSignalPct,
                    "remaining_upside_pct" to remainingUpsidePct,
                    "min_remaining_upside_exclusive_pct" to 1.0))
                return@forEach
            }
            val publishAtMs = System.currentTimeMillis()
            publishedFresh++
            val quality = item.quality
            learningDao.upsertLongForecast(
                LongForecastEntity(
                    forecastDate = today,
                    rank = existing.size + publishedFresh,
                    symbol = item.symbol,
                    entryPrice = price,
                    targetPct = remainingUpsidePct,
                    targetPrice = oldTargetPrice,
                    confidence = item.evaluation.confidence,
                    score = item.score,
                    strategy = item.evaluation.strategy,
                    regime = item.evaluation.regime,
                    marketRegime = marketRegime,
                    championKey = item.championKey,
                    reason = (if (!liveForecastReady)
                        "RESEARCH ONLY · no auto BUY unless fresh PRIMARY with broker checks · "
                        else "") + quality.reason + " · " + item.evaluation.reason +
                        " · Entry intelligence " + run {
                            val context = ForecastPreSpikeContextPolicy.evaluate(item.evaluation.features.candles)
                            val advice = timingAdvice(marketRegime, nowMs, item.evaluation.features.rvol,
                                context.compressionRatio, context.risingFloor, timingHistoricalExamples, today)
                            advice.method.name + if (advice.validated) " (validated advisory)" else " (learning)"
                        } +
                        " · family " + item.factorModel.dominantFamily +
                        " · family consensus " + fmt(item.factorModel.consensusScore * 100.0) + "%" +
                        " · capacity " + fmt(quality.volatilityCapacityPct) + "% for " + fmt(item.targetPct) + "% target" +
                        " · expected prior average price upside " + fmt(item.expectedGain.averageMfePct ?: 0.0) +
                         "% (" + item.expectedGain.observations + " comparisons/" +
                         item.expectedGain.distinctDays + " days)" +
                         " · fresh quote change " + fmt(moveFromSignalPct) + "%" +
                         " · est net ₹" + fmt(item.economics.netProfitRupees) +
                        " (" + fmt(item.economics.netReturnPct) + "%) after charges/slippage" +
                        " · risk-adjusted " + fmt(item.economics.riskAdjustedScore) +
                        " · evidence " + item.evidenceDiagnostics.sampleCount + " samples/" +
                        item.evidenceDiagnostics.distinctDays + "d/" + item.evidenceDiagnostics.distinctSymbols + " stocks" +
                        if (item.targetPct > targetPct) " · positive Fixed upside expansion +" + fmt(item.targetPct - targetPct) + "pp" else "",
                    generatedAtMs = publishAtMs,
                    lastPrice = price,
                    lastObservedAtMs = publishAtMs,
                    qualityTier = quality.tier,
                    executionEligible = quality.executionEligible && liveForecastReady
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
            "price_min_exclusive" to 200,
            "price_max_exclusive" to 5000,
            "newly_publishable_count" to publishedFresh,
            "pre_freshness_publishable_count" to publishable.size,
            "price_revalidated_before_publish" to true,
            "base_target_pct" to targetPct,
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
            "live_forecast_session_goal" to 0, // calibration sessions remain research diagnostics only
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
                            it.expectedGain.eligible,
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
        return all.filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }.map { it.toForecastDto() }
    }

    // FIXED_FINAL_LOCK_BEGIN: FIXED-V741-20261021-190239-FINAL
    suspend fun generateFixedForecasts(force: Boolean = false): List<ForecastDto> {
        val snapshot = ensureFixedEngineSnapshot()
        val now = ZonedDateTime.now(INDIA)
        val today = now.toLocalDate().toString()
        val existing = fixedRecommendationStore.recommendations(today)
            .filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }
        if (!com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedScanWindow(now)) {
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
        // V8321_FROZEN_FOLLOWUP_EXCEPTION_BEGIN
        val carriedPrimarySymbols = FixedPrimaryCarryForwardPolicy.candidates(
            fixedRecommendationStore.observations(today).map { row ->
                FixedPrimaryCarryForwardPolicy.Observation(
                    row.symbol, row.scanAtMs, row.qualityTier,
                    row.publishEligible, row.executionEligible, row.observedPrice)
            }, nowMs, existing.map { it.symbol })
        // V8321_FROZEN_FOLLOWUP_EXCEPTION_END
        if (!marketDataAvailableForFixed()) {
            fixedScanDeferredBroker = true
            auditLogger.logAsync("FIXED_PERFORMANCE", "SCAN_DEFERRED_BROKER", mapOf(
                "reason" to "BROKER_COOLDOWN_DURING_DISCOVERY",
                "published_from_incomplete_scan" to false
            ) + fixedBrokerCooldownAuditDetails())
            return existing.map { it.toDto() }
        }

        val slot = (now.hour * 60 + now.minute) / 30
        val rotating = if (master.isEmpty()) emptyList() else {
            val seed = kotlin.math.abs(today.hashCode() + slot * 977)
            (0 until min(FIXED_V741_DISCOVERY_ROTATING_FALLBACK, master.size)).map { i ->
                master[Math.floorMod(seed + i * 131, master.size)]
            }
        }
        // V8321_FROZEN_FOLLOWUP_EXCEPTION_BEGIN
        val universe = (carriedPrimarySymbols + discoveredSymbols + dnaSymbols + recentSymbols + rotating)
            .distinct()
            .take(FIXED_V741_DETAILED_FORECAST_UNIVERSE_LIMIT)
        // V8321_FROZEN_FOLLOWUP_EXCEPTION_END
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
                ) + fixedBrokerCooldownAuditDetails())
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
            "signal_intervals" to "fixed_15minute_forecast_1minute"
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

        // V8322_FROZEN_SHADOW_EXCEPTION_BEGIN
        // Exact unbounded pre-clamp score for the FROZEN 107-call state.
        // champions/research bonuses are exactly zero above; this recomputation
        // is observational only, never used to sort, publish, buy or sell.
        val uncappedDiagnosticCandidates = ranked.take(FIXED_V741_DETAILED_FORECAST_UNIVERSE_LIMIT).map { item ->
            val confidenceComponent = item.evaluation.confidence
            val directionalComponent =
                item.evaluation.directionalScore.coerceAtLeast(0.0) * 0.10
            val frequencyComponent = min(.04, (frequency[item.symbol] ?: 0) * .004)
            val dnaComponent = if (item.symbol in dnaSymbols) .08 else 0.0
            val qualityComponent = FixedForecastQualityPolicy.rankingBonus(
                item.evaluation, item.quality.volatilityCapacityPct, targetPct, item.quality.tier)
            val familyComponent = FixedMultifyFactorFamilies.rankingBonus(item.factorModel)
            val raw = confidenceComponent + directionalComponent + frequencyComponent +
                dnaComponent + qualityComponent + familyComponent
            mapOf(
                "symbol" to item.symbol,
                "quality_tier" to item.quality.tier,
                "uncapped_exact_fixed_score_for_current_frozen_champions_and_research" to raw,
                "capped_production_score" to item.score,
                "capped_at_0_99" to (raw > .99),
                "score_reconstruction_delta" to (raw.coerceIn(.01,.99) - item.score),
                "confidence" to confidenceComponent,
                "directional_bonus" to directionalComponent,
                "frozen_frequency_bonus" to frequencyComponent,
                "frozen_dna_bonus" to dnaComponent,
                "fixed_quality_bonus" to qualityComponent,
                "factor_family_bonus" to familyComponent
            )
        }
        auditLogger.logAsync("FIXED_SHADOW", "UNCAPPED_RANKING_COMPONENTS", mapOf(
            "scan_start_at_ms" to nowMs,
            "source" to "EXACT_STATIC_FROZEN_CONTRACT_NO_BROKER_REQUESTS",
            "champions_bonus" to 0.0,
            "prior_research_bonus" to 0.0,
            "changes_trading_score_or_rank" to false,
            "candidates" to uncappedDiagnosticCandidates
        ))
        // V8322_FROZEN_SHADOW_EXCEPTION_END

        fixedRecommendationStore.appendObservations(
            ranked.take(FIXED_V741_RESEARCH_CAPTURE_TOP_N).map { item ->
                FixedScanObservation(
                    recommendationDate = today,
                    scanAtMs = nowMs,
                    symbol = item.symbol,
                    qualityTier = item.quality.tier,
                    publishEligible = item.quality.publishEligible,
                    executionEligible = item.quality.executionEligible,
                    observedPrice = item.evaluation.features.ltp
                )
            }
        )

        // Explicitly authorized v8.0.6 publication-identity exception to the
        // frozen v7.4.1 one-symbol-per-day restriction. Rank/score/target untouched.
        // After a prior event, require a witnessed non-PRIMARY reset followed by
        // a distinct, fresh confirmed PRIMARY, not a continuing old setup.
        val allTodayFixedObservations = fixedRecommendationStore.observations(today)
        // V8321_FROZEN_FOLLOWUP_EXCEPTION_BEGIN
        // Only same-day, recent, previously-discovered PRIMARYs retain discovery
        // provenance. All current quality/confirmation and execution gates apply.
        ranked.forEach { candidate ->
            if (candidate.quality.tier != "PRIMARY" ||
                !candidate.quality.publishEligible || !candidate.quality.executionEligible) {
                fixedPreviouslyDiscoveredPrimaryAt.remove(candidate.symbol)
            } else if (candidate.symbol in discoveredSymbols) {
                fixedPreviouslyDiscoveredPrimaryAt[candidate.symbol] = today to nowMs
            }
        }
        fixedPreviouslyDiscoveredPrimaryAt.entries.removeIf { (_, value) ->
            !FixedPrimaryCarryForwardPolicy.wasRecentlyDiscovered(
                value.first, value.second, today, nowMs)
        }
        // V8321_FROZEN_FOLLOWUP_EXCEPTION_END
        val unseen = ranked.filter { item ->
            FixedRepeatQualificationPolicy.qualifiedForNewEvent(
                item.symbol, nowMs, existing, allTodayFixedObservations,
                importedLastEventAtMs = fixedImportedSameDayDedupJournal.latestImportedAtMs(
                    today, item.symbol))
        }
        fun priorPrimaryHits(symbol: String): Int =
            FixedRepeatQualificationPolicy.primaryHitsSinceReset(
                symbol,
                listOfNotNull(
                    existing.filter { it.symbol.equals(symbol, true) }
                        .maxOfOrNull { it.generatedAtMs },
                    fixedImportedSameDayDedupJournal.latestImportedAtMs(today, symbol)
                ).maxOrNull(),
                priorTodayResearch + allTodayFixedObservations.filter {
                    // Reset may predate the 20-minute confirmation window; include
                    // its reset evidence, but not stale old PRIMARY confirmations.
                    it.symbol.equals(symbol, true) &&
                        (it.qualityTier != "PRIMARY" || !it.publishEligible || !it.executionEligible)
                }
            )

        val publishable = unseen.filter { item ->
            // User-requested Fixed-only overlay. Apply it here, after the full v7.4.1 universe,
            // breadth and ranking have been calculated, so the underlying model remains 1:1.
            if (!RecommendationPriceBandPolicy.eligible(item.evaluation.features.ltp)) return@filter false
            val currentPrimary = item.quality.tier == "PRIMARY" &&
                item.quality.publishEligible && item.quality.executionEligible
            FixedForecastPublicationPolicy.isReady(
                discoveryBaselineReady = discoveryPass.baselineReady,
                currentPrimary = currentPrimary,
                // V8321_FROZEN_FOLLOWUP_EXCEPTION_BEGIN
                discoveredMarketWide = item.symbol in discoveredSymbols ||
                    (item.symbol in carriedPrimarySymbols &&
                        fixedPreviouslyDiscoveredPrimaryAt[item.symbol]?.let {
                            FixedPrimaryCarryForwardPolicy.wasRecentlyDiscovered(
                                it.first, it.second, today, nowMs)
                        } == true),
                // V8321_FROZEN_FOLLOWUP_EXCEPTION_END
                priorPrimaryHits = priorPrimaryHits(item.symbol)
            )
        }

        publishable.forEachIndexed { index, item ->
            val price = item.evaluation.features.ltp
            fixedRecommendationStore.upsert(
                FixedRecommendationRecord(
                    id = FixedRecommendationStore.fixedEventId(today, item.symbol, nowMs),
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
            .filter { RecommendationPriceBandPolicy.eligible(it.entryPrice) }
        val tierCounts = ranked.groupingBy { it.quality.tier }.eachCount()
        auditLogger.log("FIXED_ENGINE", "LONG_SCAN_TIERED", mapOf(
            "snapshot_id" to snapshot.payload.snapshotId,
            "snapshot_sha256" to snapshot.sha256,
            "final_contract_id" to FixedFinalContract.CONTRACT_ID,
            "reference_log_sha256" to FixedFinalContract.SOURCE_LOG_SHA256,
            "price_gate_stage" to "EARLY_HISTORICAL_SKIP_AND_PUBLICATION",
            "price_min_exclusive" to 200,
            "price_max_exclusive" to 5000,
            "date" to today,
            "evaluated" to evaluated.size,
            "primary" to (tierCounts["PRIMARY"] ?: 0),
            "secondary" to (tierCounts["SECONDARY"] ?: 0),
            "research" to (tierCounts["RESEARCH"] ?: 0),
            "published_count" to all.size,
            "carried_recent_primary_symbols" to carriedPrimarySymbols,
            "carried_primary_evaluated" to carriedPrimarySymbols.count { it in evaluatedBySymbol },
            "followup_not_auto_publication" to true,
            "fixed_detail_candidate_limit" to FIXED_V741_DETAILED_FORECAST_UNIVERSE_LIMIT,
            "daily_recommendation_cap" to "NONE",
            "price_min_exclusive" to 200,
            "price_max_exclusive" to 5000,
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
        val beta = fixedBetaMarketTokenOrNull()?.takeIf {
            com.multify.traderpro.engine.FixedDualAccountPolicy.betaDetailedEvaluation(symbol, true)
        }
        if (beta != null) {
            try {
                return fixedForecastEvaluationUsingAccount(symbol, beta, useBeta = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Throwable) {
                // A correctly enforced Fixed price-band veto is not a Beta transport outage.
                // Returning to Alpha here repeats a known-invalid quote and wastes requests.
                if (!com.multify.traderpro.engine.FixedBetaDetailFallbackPolicy.shouldRetryOnAlpha(failed)) {
                    auditLogger.logAsync("FIXED_DUAL_ACCOUNT", "BETA_DETAIL_NO_ALPHA_FALLBACK", mapOf(
                        "symbol" to symbol, "reason" to (failed.message ?: failed.javaClass.simpleName).take(120),
                        "alpha_fallback_attempted" to false,
                        "account_cooldown_amplification_prevented" to true,
                        "fixed_scoring_changed" to false
                    ))
                    throw failed
                }
                auditLogger.logAsync("FIXED_DUAL_ACCOUNT", "BETA_DETAIL_FALLBACK_ALPHA", mapOf(
                    "symbol" to symbol,
                    "error" to failed.javaClass.simpleName,
                    "reason" to (failed.message ?: "UNSPECIFIED").take(180),
                    "fixed_scoring_changed" to false,
                    "broker_execution_account" to "ALPHA"
                ))
            }
        }
        return fixedForecastEvaluationUsingAccount(symbol, token, useBeta = false)
    }

    private suspend fun fixedForecastEvaluationUsingAccount(
        symbol: String, token: String, useBeta: Boolean
    ): FixedStrategyEvaluation {
        val now = ZonedDateTime.now(INDIA)
        val start = now.minusDays(4).toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val quote = if (useBeta) {
            betaMarketRead(
                token, BetaResearchGate.Type.LIVE,
                alphaCall = { error("Beta Fixed quote cannot use Alpha credentials") },
                betaCall = {
                    apiFactory.betaGroww.quote(bearer(token), tradingSymbol = symbol)
                        .requirePayload("Fixed Beta quote $symbol")
                }
            )
        } else withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Fixed quote $symbol")
        }
        require(RecommendationPriceBandPolicy.eligible(quote.lastPrice)) { "FIXED_PRICE_BAND_OUTSIDE" }
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
        val raw = if (useBeta) {
            betaMarketRead(
                token, BetaResearchGate.Type.HISTORICAL,
                alphaCall = { error("Beta Fixed 15m history cannot use Alpha credentials") },
                betaCall = {
                    apiFactory.betaGroww.historicalCandles(
                        bearer(token), growwSymbol = "NSE-$symbol", startTime = incremental?.startTime ?: start,
                        endTime = end, candleInterval = "15minute"
                    ).requirePayload("Fixed Beta 15m candles $symbol")
                }
            )
        } else withFixedHistoricalDataSlot {
            apiFactory.groww.historicalCandles(
                bearer(token), growwSymbol = "NSE-$symbol", startTime = incremental?.startTime ?: start,
                endTime = end, candleInterval = "15minute"
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
                if (useBeta) {
                    betaMarketRead(
                        token, BetaResearchGate.Type.HISTORICAL,
                        alphaCall = { error("No Alpha borrowed for Beta candle fallback") },
                        betaCall = {
                            apiFactory.betaGroww.historicalCandles(
                                bearer(token), growwSymbol = "NSE-$symbol",
                                startTime = start, endTime = end, candleInterval = "15minute"
                            ).requirePayload("Fixed Beta historical fallback $symbol")
                        }
                    )
                } else withFixedHistoricalDataSlot {
                    apiFactory.groww.historicalCandles(
                        bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end,
                        candleInterval = "15minute"
                    ).requirePayload("Fixed historical fallback $symbol")
                }
            }
        }
        fixedHistoricalWindowStore.remember(historyKey, start, historical, SystemClock.elapsedRealtime())
        require(historical.candles.size >= 5) { "Insufficient fixed forecast candles" }
        // Broker I/O is completed before scheduling CPU-only feature/scoring work.
        // Identical inputs and scoring; only the execution dispatcher differs.
        val evaluation = withContext(fixedCpuDispatcher) {
            val features = FixedLocalStrategyEngine.buildFeatures(quote, historical, 15)
            val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "fixed-forecast", confidence = 1.0)
            FixedLocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
        }
        // Diagnostic-only, O(1) non-blocking queue offer. All 40 factor calculations and disk
        // writes execute later on a low-priority worker; never delay Fixed recommendations.
        val referenceAt = System.currentTimeMillis()
        fixedShadowReferences[symbol] = FixedShadowReference(quote, evaluation, referenceAt)
        fixedFactorTelemetry.tryRecord(symbol, evaluation, lastFixedScanAtMs, quote, referenceAt)
        return evaluation
    }

    /**
     * Schedule the NEXT original full-universe LTP sweep while the Fixed worker
     * is otherwise idle. Requests are not duplicated on a successful cycle.
     * Never feed a partial or stale sweep into official Fixed discovery.
     */
    suspend fun prepareNextFixedDiscoverySweep(): Boolean {
        val clock = ZonedDateTime.now(INDIA)
        if (!com.multify.traderpro.engine.NseEquitySessionCalendar.mayPrepareNextFixedSweep(clock) || lastFixedScanAtMs <= 0L ||
            marketCriticalTradeInFlight || !marketDataAvailableForFixed()) return false
        val nowMs = System.currentTimeMillis()
        val dueAtMs = lastFixedScanAtMs + FIXED_V741_FORECAST_SCAN_INTERVAL_MS
        if (!com.multify.traderpro.engine.FixedSweepPrefetchPolicy.canStart(
                nowMs, dueAtMs, marketCriticalTradeInFlight, marketDataAvailableForFixed()
            )) return false
        val token = ensureToken() ?: return false
        val master = nseSymbols.symbols().distinct()
        if (master.isEmpty()) return false
        val date = clock.toLocalDate().toString()
        val pending = pendingFixedSweepMutex.withLock {
            if (pendingFixedSweep?.completion?.isCompleted == false) return@withLock null
            PendingFixedSweep(date, sha256(token + "|" + (fixedBetaMarketTokenOrNull() ?: "ALPHA_ONLY")).take(12),
                sha256(master.joinToString("|")).take(16),
                dueAtMs, CompletableDeferred<PrecomputedFixedSweep?>()).also {
                pendingFixedSweep = it
            }
        } ?: return false
        // Fetch just ahead of the next official scan, keeping the sample age bounded.
        val startDelay = (dueAtMs - System.currentTimeMillis() -
            com.multify.traderpro.engine.FixedSweepPrefetchPolicy.EXPECTED_SWEEP_MS).coerceAtLeast(0L)
        try {
            if (startDelay > 0L) delay(startDelay)
            if (marketCriticalTradeInFlight || !marketDataAvailableForFixed() ||
                ZonedDateTime.now(INDIA).toLocalDate().toString() != date ||
                System.currentTimeMillis() > dueAtMs +
                    com.multify.traderpro.engine.FixedSweepPrefetchPolicy.MAX_OVERRUN_MS) {
                pending.completion.complete(null)
                return false
            }
            fixedSweepPreparationActive = true
            val started = SystemClock.elapsedRealtime()
            val sweep = collectFixedWideLtp(token, master, preparing = true)
            val usable = !sweep.aborted &&
                sweep.prices.size.toDouble() / master.size >=
                    FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO && marketDataAvailableForFixed()
            auditLogger.logAsync("FIXED_PERFORMANCE", "NEXT_FULL_NSE_SWEEP_PREPARED", mapOf(
                "elapsed_ms" to (SystemClock.elapsedRealtime() - started),
                "ltp_batches" to sweep.requestCount,
                "coverage" to sweep.prices.size, "batch_errors" to sweep.errors,
                "usable" to usable, "same_broker_pacing" to true,
                "fixed_logic_changed" to false
            ))
            pending.completion.complete(if (usable) sweep else null)
            return usable
        } catch (cancelled: CancellationException) {
            pending.completion.complete(null)
            throw cancelled
        } catch (t: Throwable) {
            pending.completion.complete(null)
            auditLogger.logAsync("FIXED_PERFORMANCE", "NEXT_FULL_NSE_SWEEP_ERROR",
                mapOf("error" to (t.message ?: t.javaClass.simpleName)))
            return false
        } finally {
            fixedSweepPreparationActive = false
        }
    }

    private suspend fun takeNextFixedDiscoverySweep(
        token: String, master: List<String>, date: String
    ): PrecomputedFixedSweep? {
        val pending = pendingFixedSweepMutex.withLock {
            pendingFixedSweep.also { pendingFixedSweep = null }
        } ?: return null
        if (pending.date != date || pending.tokenHash != sha256(token + "|" + (fixedBetaMarketTokenOrNull() ?: "ALPHA_ONLY")).take(12) ||
            pending.masterHash != sha256(master.joinToString("|")).take(16)) return null
        val started = SystemClock.elapsedRealtime()
        // Await already-started batches instead of issuing a duplicate full sweep.
        val sweep = withTimeoutOrNull(
            com.multify.traderpro.engine.FixedSweepPrefetchPolicy.MAX_WAIT_MS
        ) { pending.completion.await() }
        val waitedMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        if (sweep == null || !com.multify.traderpro.engine.FixedSweepPrefetchPolicy.canConsume(
            sweep.completedAtMs, System.currentTimeMillis(), sweep.prices.size,
            master.size, sweep.aborted
        )) {
            // Do not issue a second full market sweep following an invalid
            // prefetch. Treat this attempt as deferred and scan anew next cycle.
            auditLogger.logAsync("FIXED_PERFORMANCE", "PREFETCH_INVALID_SCAN_DEFERRED",
                mapOf("reason" to "FAILED_STALE_OR_INCOMPLETE", "waited_ms" to waitedMs,
                    "duplicate_full_sweep_prevented" to true, "fixed_scoring_unchanged" to true))
            return sweep?.copy(aborted = true)
                ?: PrecomputedFixedSweep(emptyMap(), 1, true, System.currentTimeMillis(), 0)
        }
        auditLogger.logAsync("FIXED_PERFORMANCE", "PREFETCHED_SWEEP_CONSUMED", mapOf(
            "blocking_wait_ms" to waitedMs,
            "sweep_age_ms" to (System.currentTimeMillis() - sweep.completedAtMs),
            "nse_coverage" to sweep.prices.size,
            "ltp_batches_moved_off_critical_scan" to sweep.requestCount,
            "extra_full_market_scans" to 0,
            "fixed_formula_changed" to false
        ))
        return sweep
    }

    /** Non-scoring diagnostics only: count missing and invalid broker LTP responses
     * without an additional market API call or a change to publication criteria. */
    private fun alphaCanTakeBetaDiscoveryFallback(): Boolean {
        val ready = runCatching {
            recoveryGate.checkRequestAllowed(GrowwApiUsageLedger.Category.LIVE_DATA)
        }.isSuccess
        val used = GrowwApiUsageLedger.snapshot().categories
            .first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }.usedLastMinute
        val appLimit = com.multify.traderpro.engine.GrowwApiBudgetPolicy
            .fixedLiveDataPerMinute(
                com.multify.traderpro.engine.MarketDataPriorityAdmissionPolicy.hasManagedHoldings
            )
        return com.multify.traderpro.engine.FixedAccountTransportAdmissionPolicy
            .allowAlphaFallback(ready, used, appLimit)
    }

    private suspend fun collectFixedWideLtp(
        token: String, master: List<String>, preparing: Boolean = false
    ): PrecomputedFixedSweep {
        val current = linkedMapOf<String, Double>()
        var errors = 0
        var failures = 0
        var aborted = false
        val chunks = master.chunked(50)
        val betaToken = fixedBetaMarketTokenOrNull()
        val betaStopped = AtomicBoolean(false)
        val betaSuccess = AtomicInteger(0)
        val alphaSuccess = AtomicInteger(0)
        val alphaFallbackBatches = AtomicInteger(0)
        var missingFromBroker = 0
        var invalidBrokerPrices = 0
        val missingSample = mutableListOf<String>()
        for (windowStart in chunks.indices step FixedDiscoveryWindowScheduler.WINDOW_SIZE) {
            if (preparing && (marketCriticalTradeInFlight || !marketDataAvailableForFixed() ||
                !com.multify.traderpro.engine.NseEquitySessionCalendar.mayContinuePreparedFixedSweep(ZonedDateTime.now(INDIA)))) {
                aborted = true
                break
            }
            val results = FixedDiscoveryWindowScheduler.fetchOrdered(
                chunks, windowStart
            ) { index, chunk ->
                val betaAssigned = betaToken != null &&
                    com.multify.traderpro.engine.FixedDualAccountPolicy.betaDiscoveryBatch(index, true)
                val useBeta = betaAssigned && !betaStopped.get() &&
                    betaResearchGate.healthyForFixed(BetaResearchGate.Type.LIVE)
                if (useBeta) {
                    try {
                        fixedBetaLtp(betaToken!!, chunk).also { betaSuccess.incrementAndGet() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Throwable) {
                        // Beta's independent 429 must NOT pause Alpha. Switch
                        // only if Alpha is ready and has protected category headroom.
                        // Never issue an unbounded immediate retry burst.
                        betaStopped.set(true)
                        // No Alpha spillover during a Beta cooldown: incomplete
                        // market coverage is deferred rather than repaired with a burst.
                        auditLogger.logAsync("FIXED_DUAL_ACCOUNT",
                            "BETA_DISCOVERY_COOLDOWN_NO_ALPHA_FALLBACK",
                            mapOf("batch_index" to index,
                                "beta_http_429" to ((failed as? HttpException)?.code() == 429),
                                "beta_error" to failed.javaClass.simpleName,
                                "beta_paused_for_rest_of_sweep" to true,
                                "alpha_fallback_requests" to 0,
                                "partial_coverage_must_not_publish" to true))
                        null
                    }
                } else {
                    // Never move Beta-owned discovery traffic onto Alpha while Beta
                    // is paused. Alpha-owned batches still proceed independently.
                    if (betaAssigned) null
                    else fetchFixedDiscoveryLtpBatch(token, chunk, index)
                        ?.also { alphaSuccess.incrementAndGet() }
                }
            }
            for ((index, payload) in results) {
                if (payload == null) {
                    errors++
                    failures++
                    if (failures >= FIXED_V741_DISCOVERY_CONSECUTIVE_FAILURE_ABORT) {
                        aborted = true
                        break
                    }
                } else {
                    failures = 0
                    val returned = payload.keys.map {
                        it.removePrefix("NSE_").uppercase(Locale.US)
                    }.toHashSet()
                    for (symbol in chunks[index]) {
                        if (symbol.uppercase(Locale.US) !in returned) {
                            missingFromBroker++
                            if (missingSample.size < 20) missingSample.add(symbol)
                        }
                    }
                    payload.forEach { (key, value) ->
                        if (value.isFinite() && value >= 10.0) {
                            current[key.removePrefix("NSE_").uppercase(Locale.US)] = value
                        } else {
                            invalidBrokerPrices++
                        }
                    }
                }
                if (index + 1 < chunks.size) delay(FIXED_V741_DISCOVERY_BATCH_PACING_MS)
            }
            if (aborted) break
        }
        auditLogger.logAsync("FIXED_DUAL_ACCOUNT", "FULL_NSE_SWEEP_ACCOUNT_PROFILE", mapOf(
            "master_symbols" to master.size,
            "total_batches" to chunks.size,
            "alpha_success_batches" to alphaSuccess.get(),
            "beta_success_batches" to betaSuccess.get(),
            "alpha_fallback_batches" to alphaFallbackBatches.get(),
            "broker_omitted_symbols" to missingFromBroker,
            "broker_invalid_ltp_values" to invalidBrokerPrices,
            "broker_omission_examples" to missingSample,
            "extra_ltp_requests_for_diagnostics" to 0,
            "beta_authenticated" to (betaToken != null),
            "beta_disabled_during_sweep" to betaStopped.get(),
            "valid_symbols" to current.size,
            "batch_errors" to errors,
            "aborted" to aborted,
            "alpha_order_account_unchanged" to true,
            "alpha_and_beta_quota_independent" to true,
            "beta_429_does_not_pause_alpha" to true
        ))
        return PrecomputedFixedSweep(current, errors, aborted, System.currentTimeMillis(), chunks.size)
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
        val staged = takeNextFixedDiscoverySweep(token, distinctMaster, today)
        val sweep = staged ?: collectFixedWideLtp(token, distinctMaster)
        val current = sweep.prices
        val batchErrors = sweep.errors
        val abortedForNetwork = sweep.aborted

        val denominator = distinctMaster.size.coerceAtLeast(1)
        val coverageRatio = current.size.toDouble() / denominator.toDouble()
        val previousCoverageRatio = previous.size.toDouble() / denominator.toDouble()
        val baselineReady = previousCoverageRatio >= FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO &&
            coverageRatio >= FIXED_V741_MIN_DISCOVERY_COVERAGE_RATIO && !abortedForNetwork

        // v8.3.21: apply the EXISTING ₹200–₹5,000 Fixed eligibility
        // before consuming the unchanged 28 dynamic candidate slots.
        // The authoritative full-universe LTP sweep, health coverage, stored
        // baselines and 65/35 momentum ranking formula are untouched.
        val priceEligibleForDetail = current.filterValues {
            RecommendationPriceBandPolicy.eligible(it)
        }
        val ranked = if (baselineReady) {
            FixedMarketDiscoveryPolicy.rank(
                currentLtps = priceEligibleForDetail,
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
        // Full-universe price evidence for missed movers. Off-thread, bounded and
        // strictly non-trading: NO new live/historical request and NO model change.
        fixedMarketPathTelemetry.tryRecord(
            date = today, phase = "OFFICIAL_FIXED_MARKET_WIDE_DISCOVERY",
            scanStartedAtMs = lastFixedScanAtMs,
            sweepCompletedAtMs = sweep.completedAtMs,
            prices = current, universeCount = distinctMaster.size,
            previousLtps = previous, sessionOpenLtps = sessionOpen,
            selected = ranked, baselineReady = baselineReady
        )
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
            "selected" to ranked.size
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


    /**
     * Research-only: an already sampled production 15m evaluation is compared to a 5m
     * historical view ending at the SAME quote timestamp. One additional Alpha 5m
     * history call, never an extra quote or full-market scan. No execution or ranking effect.
     */
    suspend fun sampleFixedFiveMinuteShadow(completedScanNumber: Int) {
        if (!fixedFiveMinuteShadowMutex.tryLock()) return
        try {
            val now = ZonedDateTime.now(INDIA)
            if (now.dayOfWeek.value >= 6 || now.toLocalTime() !in LocalTime.of(9, 15)..LocalTime.of(15, 25)) return
            val nowMs = System.currentTimeMillis()
            val lastSuccess = fixedShadowPrefs.getLong("last_successful_at_ms", 0L)
            val api = GrowwApiUsageLedger.snapshot()
            val live = api.categories.first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }
            val historical = api.categories.first { it.category == GrowwApiUsageLedger.Category.HISTORICAL }
            val brokerReady = marketDataAvailableForFixed() && !recoveryGate.requiresReconnect()
            val status = recoveryGate.status(nowMs)
            val latestCooldownUntilMs = maxOf(
                status.categoryCooldownUntilMs[GrowwApiUsageLedger.Category.LIVE_DATA] ?: 0L,
                status.categoryCooldownUntilMs[GrowwApiUsageLedger.Category.HISTORICAL] ?: 0L
            )
            val lastObserved429AtMs = maxOf(live.lastRateLimitAtMs, historical.lastRateLimitAtMs)
            val fixedDueIn = millisUntilNextFixedScan()
            if (!FixedShadowSamplingPolicy.canCollect(
                    nowMs, lastSuccess, fixedApiPriorityRequested, marketCriticalTradeInFlight,
                    fixedDueIn, live.usedLastMinute, live.appPerMinuteLimit,
                    historical.usedLastMinute, historical.appPerMinuteLimit, brokerReady,
                    latestBrokerCooldownUntilMs = latestCooldownUntilMs,
                    lastObserved429AtMs = lastObserved429AtMs
                )
            ) {
                auditLogger.logAsync("FIXED_5M_SHADOW", "IDLE_CAPACITY_SKIPPED", mapOf(
                    "scan_number" to completedScanNumber, "alpha_only" to true,
                    "fixed_due_in_ms" to fixedDueIn, "fixed_priority" to fixedApiPriorityRequested,
                    "critical_trade" to marketCriticalTradeInFlight, "broker_ready" to brokerReady,
                    "live_minute" to live.usedLastMinute,
                    "historical_minute" to historical.usedLastMinute,
                    "latest_cooldown_until_ms" to latestCooldownUntilMs,
                    "last_429_at_ms" to lastObserved429AtMs,
                    "research_429_quiet_period_ms" to FixedShadowSamplingPolicy.POST_429_RESEARCH_QUIET_PERIOD_MS
                ))
                return
            }
            val today = now.toLocalDate().toString()
            // Rotate across eligible evaluated symbols (including non-recommendations).
            // Otherwise a single published symbol would dominate all shadow samples.
            val preferred = fixedRecommendationStore.recommendations(today).map { it.symbol }
            val candidates = (preferred + fixedShadowReferences.keys.sorted()).distinct().mapNotNull { symbol ->
                fixedShadowReferences[symbol]?.takeIf {
                    RecommendationPriceBandPolicy.eligible(it.quote.lastPrice) &&
                        (nowMs - it.quoteCapturedAtMs) in 0L..FixedShadowSamplingPolicy.MAX_REFERENCE_AGE_MS
                }?.let { symbol to it }
            }
            val candidate = if (candidates.isEmpty()) null
                else candidates[Math.floorMod(completedScanNumber, candidates.size)]
            if (candidate == null) {
                auditLogger.logAsync("FIXED_5M_SHADOW", "NO_FRESH_PRODUCTION_15M_REFERENCE")
                return
            }
            val (symbol, reference) = candidate
            val token = ensureToken() ?: return // Alpha only: no Beta account, no fallback.
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight ||
                millisUntilNextFixedScan() < FixedShadowSamplingPolicy.MIN_FIXED_HEADROOM_MS
            ) return
            val referenceTime = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(reference.quoteCapturedAtMs), INDIA
            )
            val start = referenceTime.minusDays(4).toLocalDate().atTime(9, 15).format(HIST_FORMAT)
            val end = referenceTime.toLocalDateTime().format(HIST_FORMAT)
            val before = System.currentTimeMillis()
            // Never occupy Fixed's request slot waiting for a full network timeout.
            val history = withTimeoutOrNull(
                (millisUntilNextFixedScan() - 1_500L).coerceIn(500L, 4_000L)
            ) {
                if (fixedApiPriorityRequested || marketCriticalTradeInFlight || !marketDataAvailableForFixed()) null
                else apiFactory.groww.historicalCandles(
                    bearer(token), growwSymbol = "NSE-$symbol",
                    startTime = start, endTime = end, candleInterval = "5minute"
                ).requirePayload("Alpha idle Fixed shadow 5m $symbol")
            }
            if (history == null || history.candles.size < 5) {
                auditLogger.logAsync("FIXED_5M_SHADOW", "HISTORICAL_UNAVAILABLE_OR_TIMED_OUT", mapOf(
                    "symbol" to symbol, "alpha_only" to true,
                    "elapsed_ms" to (System.currentTimeMillis() - before),
                    "extra_historical_requests_attempted" to 1
                ))
                return
            }
            val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol,
                rawText = "fixed-5m-idle-research", confidence = 1.0)
            val five = FixedLocalStrategyEngine.evaluateLong(
                signal, FixedLocalStrategyEngine.buildFeatures(reference.quote, history, 5),
                includeEventPrior = false
            )
            val fifteen = reference.evaluation
            val targetPct = FixedForecastQualityPolicy.adaptiveTargetPct(
                fixedSnapshotMultifyMfeAverage(ensureFixedEngineSnapshot()), emptyList()
            )
            val regime = "BREADTH_NEUTRAL" // Same neutral reference for both variants.
            val tier15 = FixedForecastQualityPolicy.evaluate(fifteen, targetPct, regime).tier
            val tier5 = FixedForecastQualityPolicy.evaluate(five, targetPct, regime).tier
            val price = reference.quote.lastPrice ?: return
            val pair = FixedFiveMinuteShadowPair(
                date = today, atMs = reference.quoteCapturedAtMs, symbol = symbol,
                entryPrice = price, targetPct = targetPct,
                fifteenMinuteTier = tier15, fiveMinuteTier = tier5,
                fifteenMinuteConfidence = fifteen.confidence, fiveMinuteConfidence = five.confidence,
                fifteenMinuteDirection = fifteen.directionalScore, fiveMinuteDirection = five.directionalScore,
                fifteenMinuteStrategy = fifteen.strategy, fiveMinuteStrategy = five.strategy
            )
            fixedFiveMinuteShadowJournal.append(pair)
            fixedShadowPrefs.edit().putLong("last_successful_at_ms", System.currentTimeMillis()).apply()
            auditLogger.logAsync("FIXED_5M_SHADOW", "PAIRED_SIGNAL_CAPTURED", mapOf(
                "date" to today, "symbol" to symbol,
                "entry_price" to price, "reference_quote_at_ms" to reference.quoteCapturedAtMs,
                "reference_age_ms" to (nowMs - reference.quoteCapturedAtMs),
                "target_pct" to targetPct, "tier_15m" to tier15, "tier_5m" to tier5,
                "confidence_15m" to fifteen.confidence, "confidence_5m" to five.confidence,
                "direction_15m" to fifteen.directionalScore, "direction_5m" to five.directionalScore,
                "volume_15m_last_bar" to fifteen.features.candles.lastOrNull()?.volume,
                "volume_5m_last_bar" to five.features.candles.lastOrNull()?.volume,
                "bid_price" to reference.quote.bidPrice, "ask_price" to reference.quote.offerPrice,
                "bid_quantity" to reference.quote.bidQuantity,
                "ask_quantity" to reference.quote.offerQuantity,
                "total_buy_quantity" to reference.quote.totalBuyQuantity,
                "total_sell_quantity" to reference.quote.totalSellQuantity,
                "day_traded_volume" to reference.quote.volume,
                "account" to "ALPHA", "extra_quote_requests" to 0,
                "extra_historical_requests" to 1,
                "one_in_ten_sampling_removed" to true,
                "orders_allowed" to false, "production_fixed_candles" to "15minute",
                "comparison_group" to pair.comparisonGroup,
                "session_bucket" to pair.sessionBucket,
                "both_variants_same_15m_reference_quote" to true,
                "5m_research_only" to true, "same_production_quote" to true,
                "outcomes_are_sampled_not_realised" to true
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (err: Throwable) {
            auditLogger.logAsync("FIXED_5M_SHADOW", "SAMPLE_ERROR", mapOf(
                "error_type" to err.javaClass.simpleName, "error" to (err.message ?: "")
            ))
        } finally {
            fixedFiveMinuteShadowMutex.unlock()
        }
    }

    /** Reuse Fixed's existing full-market LTP capture; never generate extra broker requests. */
    suspend fun updateFixedFiveMinuteShadowOutcomes() {
        val today = LocalDate.now(INDIA).toString()
        val market = fixedMarketDiscoveryStateStore.load(today) ?: return
        val updated = fixedFiveMinuteShadowJournal.updateObservedPrices(today, market.previousLtps, System.currentTimeMillis())
        updated.forEach { pair ->
            auditLogger.logAsync("FIXED_5M_SHADOW", "PAIRED_OUTCOME_UPDATED", mapOf(
                "date" to today, "symbol" to pair.symbol, "at_ms" to pair.atMs,
                "max_favourable_pct" to pair.maxFavourablePct, "max_adverse_pct" to pair.maxAdversePct,
                "target_observed" to pair.targetObserved, "observations" to pair.observations,
                "target_before_sampled_2pct_adverse" to pair.sampledTargetBeforeRisk,
                "first_target_sample_at_ms" to pair.firstTargetObservedAtMs,
                "first_two_pct_risk_sample_at_ms" to pair.firstTwoPctRiskObservedAtMs,
                "comparison_group" to pair.comparisonGroup, "session_bucket" to pair.sessionBucket,
                "15m_primary" to pair.fifteenMinutePrimary, "5m_primary" to pair.fiveMinutePrimary,
                "outcome_basis" to "SAMPLED_FIXED_FULL_MARKET_LTP", "realised_pnl" to false
            ))
        }
    }



    /** Forecast-only deterministic checkpoint. The Fixed universe/ranking is not touched. */
    private fun forecastNextEvaluationTranche(
        date: String, candidates: List<String>, confirmationFollowups: List<String>
    ): List<String> {
        if (forecastDetailDate != date || forecastDetailQueue.isEmpty()) {
            forecastDetailDate = date
            forecastDetailQueue = candidates
            forecastDetailCursor = 0
            forecastDetailRetryAttempts.clear()
        }
        // Preserve processed cursor entries and all pending names; reorder only
        // the unprocessed tranche. Follow-ups receive a fresh broker evaluation.
        val prefix = forecastDetailQueue.take(forecastDetailCursor)
        val pending = ForecastConfirmationPriorityPolicy.prioritize(
            forecastDetailQueue.drop(forecastDetailCursor), confirmationFollowups, candidates
        )
        forecastDetailQueue = prefix + pending
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
                "expected_average_gain_pct" to item.expectedGain.averageMfePct,
                "expected_gain_observations" to item.expectedGain.observations,
                "expected_gain_distinct_days" to item.expectedGain.distinctDays,
                "expected_gain_gt_one_pct" to item.expectedGain.eligible,
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

    /**
     * Independent, official-package-only Univest RESEARCH input. No order is ever placed.
     * Only 1–3 month ENTRY + official same-NSE-session EXIT with two observed quote
     * prices and 48 entry-time features may join Forecast learning. Never feeds Fixed.
     */
    suspend fun captureUnivestNotification(title: String, body: String, postedAtMs: Long) {
        val at = postedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val received = System.currentTimeMillis()
        val combined = (title + "\n" + body).take(2500)
        val capturePrefs = appContext.getSharedPreferences(
            "notification_listener_state_v801", Context.MODE_PRIVATE)
        fun captureState(status: String, symbol: String? = null) {
            capturePrefs.edit().putLong("last_univest_parsed_at_ms", received)
                .putString("last_univest_parse_status", status)
                .putString("last_univest_parsed_symbol", symbol.orEmpty()).apply()
        }
        val signal = ForecastUnivestLearningPolicy.parse(ForecastUnivestLearningPolicy.OFFICIAL_PACKAGE, combined)
        if (signal == null) {
            captureState("PARSER_UNMATCHED")
            forecastSourceNotificationJournal.append(at, received, "UNKNOWN", null,
                "PARSER_UNMATCHED", title, body)
            auditLogger.logAsync("FORECAST_UNIVEST", "NOTIFICATION_IGNORED", mapOf(
                "reason" to "NOT_1_TO_3_MONTH_ENTRY_OR_OFFICIAL_EXIT", "source_verified" to true))
            return
        }
        // Don't infer ticker from a company name. The NSE master is the identity authority.
        if (signal.symbol !in nseSymbols.symbols()) {
            captureState("NSE_SYMBOL_UNVERIFIED", signal.symbol)
            forecastSourceNotificationJournal.append(at, received, signal.type.name, signal.symbol,
                "NSE_SYMBOL_UNVERIFIED", title, body)
            auditLogger.logAsync("FORECAST_UNIVEST", "SYMBOL_NOT_VERIFIED_NSE", mapOf(
                "symbol" to signal.symbol, "type" to signal.type.name, "nse_only" to true))
            return
        }
        val date = ForecastUnivestLearningPolicy.date(at)
        val key = "$date|${signal.symbol}"
        val candidate = if (signal.type == ForecastUnivestLearningPolicy.Type.ENTRY) {
            forecastUnivestJournal.enter(signal.symbol, at, received, signal.durationMonths ?: 0.0)
        } else {
            forecastUnivestJournal.claimExit(signal.symbol, at, received)
        }
        if (candidate == null) {
            captureState("DUPLICATE_OR_NO_SAME_DAY_ENTRY", signal.symbol)
            forecastSourceNotificationJournal.append(at, received, signal.type.name, signal.symbol,
                "DUPLICATE_OR_NO_SAME_DAY_ENTRY", title, body)
            auditLogger.logAsync("FORECAST_UNIVEST", "PAIRING_SKIPPED", mapOf(
                "type" to signal.type.name, "symbol" to signal.symbol, "date" to date,
                "reason" to "DUPLICATE_ENTRY_OR_NO_MATCHING_SAME_DAY_ENTRY", "no_order_path" to true))
            return
        }
        captureState(
            if (signal.type == ForecastUnivestLearningPolicy.Type.ENTRY) "ENTRY_CAPTURED" else "SAME_DAY_EXIT_CAPTURED",
            signal.symbol)
        forecastSourceNotificationJournal.append(at, received, signal.type.name, signal.symbol,
            if (signal.type == ForecastUnivestLearningPolicy.Type.ENTRY) "ENTRY_CAPTURED" else "SAME_DAY_EXIT_CAPTURED",
            title, body)
        auditLogger.logAsync("FORECAST_UNIVEST", "OFFICIAL_NOTIFICATION_CAPTURED", mapOf(
            "symbol" to signal.symbol, "type" to signal.type.name,
            "duration_months" to signal.durationMonths, "source_package" to ForecastUnivestLearningPolicy.OFFICIAL_PACKAGE,
            "posted_ms" to at, "received_ms" to received,
            "same_day_pair" to (signal.type == ForecastUnivestLearningPolicy.Type.EXIT),
            "entry_price_recorded" to (candidate.entryPrice != null), "no_order_path" to true))
        val token = runCatching { forecastMarketToken() }.getOrNull()
        val betaConfigured = secretStore.hasBetaCredentials()
        if (token.isNullOrBlank() || (betaConfigured && !forecastUsesBeta(token))) {
            auditLogger.logAsync("FORECAST_UNIVEST", "RESEARCH_QUOTE_DEFERRED", mapOf(
                "symbol" to signal.symbol, "type" to signal.type.name,
                "reason" to "RESEARCH_ACCOUNT_UNAVAILABLE", "beta_configured" to betaConfigured,
                "alpha_quota_borrowed" to false))
            return
        }
        val quote = runCatching { withForecastLiveDataSlot {
            betaMarketRead(token, BetaResearchGate.Type.LIVE,
                alphaCall = { apiFactory.groww.quote(bearer(token), tradingSymbol = signal.symbol)
                    .requirePayload("Univest entry/exit research quote") },
                betaCall = { apiFactory.betaGroww.quote(bearer(token), tradingSymbol = signal.symbol)
                    .requirePayload("Beta Univest research quote") })
        } }.onFailure { auditLogger.logAsync("FORECAST_UNIVEST", "RESEARCH_QUOTE_FAILED", mapOf(
            "symbol" to signal.symbol, "type" to signal.type.name,
            "error" to it.javaClass.simpleName)) }.getOrNull() ?: return
        val price = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: run {
            auditLogger.logAsync("FORECAST_UNIVEST", "RESEARCH_QUOTE_MISSING_LTP", mapOf("symbol" to signal.symbol))
            return
        }
        val quoteAt = System.currentTimeMillis()
        if (signal.type == ForecastUnivestLearningPolicy.Type.EXIT) {
            val closed = forecastUnivestJournal.quoteExit(key, price, quoteAt)
            auditLogger.logAsync("FORECAST_UNIVEST", "SAME_DAY_EXIT_OUTCOME", mapOf(
                "symbol" to signal.symbol, "entry_at_ms" to candidate.entryNotificationAtMs,
                "exit_at_ms" to at, "entry_price" to closed?.entryPrice,
                "exit_price" to closed?.exitPrice, "return_pct" to closed?.outcomePct,
                "entry_price_source" to closed?.entryPriceSource,
                "exit_price_source" to closed?.exitPriceSource,
                "capture_latency_ms" to (quoteAt - at),
                "training_eligible" to (closed?.status == "CLOSED_SAME_DAY_VERIFIED"),
                "status" to (closed?.status ?: "EXIT_PRICE_NOT_VERIFIED"),
                "outcome_is_broker_trade_pnl" to false))
            return
        }
        val entry = forecastUnivestJournal.quoteEntry(key, price, quoteAt)
        if (entry == null) {
            auditLogger.logAsync("FORECAST_UNIVEST", "ENTRY_QUOTE_NOT_ELIGIBLE", mapOf(
                "symbol" to signal.symbol, "price" to price,
                "quote_latency_ms" to quoteAt - at, "max_latency_ms" to 180_000L))
            return
        }
        // Feature I/O is research-only and can fail without affecting official Multify signals.
        val eval = runCatching { forecastEvaluation(signal.symbol, token) }
            .onFailure { auditLogger.logAsync("FORECAST_UNIVEST", "48_FACTOR_CAPTURE_FAILED", mapOf(
                "symbol" to signal.symbol, "error" to it.javaClass.simpleName)) }
            .getOrNull() ?: return
        val model = MultifyFactorFamilies.evaluate(eval, "BREADTH_NEUTRAL")
        val factors = ForecastUnivestLearningPolicy.fingerprint(model, eval, "BREADTH_NEUTRAL")
        val captured = System.currentTimeMillis()
        val snapshot = forecastUnivestJournal.snapshot(key, researchGson.toJson(factors), captured,
            factors.values.count { it != null })
        auditLogger.logAsync("FORECAST_UNIVEST", "ENTRY_48_FACTOR_SNAPSHOT", mapOf(
            "symbol" to signal.symbol, "entry_at_ms" to at, "price" to entry.entryPrice,
            "price_source" to entry.entryPriceSource,
            "feature_count" to factors.size, "observed_factor_count" to factors.values.count { it != null },
            "capture_at_ms" to captured, "capture_latency_ms" to captured - at,
            "within_three_minutes" to (snapshot != null), "research_account" to
                (if (forecastUsesBeta(token)) "BETA" else "ALPHA_FALLBACK"),
            "entry_features_used_only_after_verified_same_day_exit" to true))
    }

    private suspend fun recoverUnivestExitPrices(token: String): Int {
        if (!secretStore.hasBetaCredentials() || !forecastUsesBeta(token)) return 0
        var count = 0
        for (record in forecastUnivestJournal.pendingExitPricing()) {
            val exit = record.exitNotificationAtMs ?: continue
            val t = Instant.ofEpochMilli(exit).atZone(INDIA)
            val from = t.minusMinutes(3).toLocalDateTime().format(HIST_FORMAT)
            val until = t.plusMinutes(4).toLocalDateTime().format(HIST_FORMAT)
            val history = runCatching {
                apiFactory.betaGroww.historicalCandles(
                    bearer(token), growwSymbol = "NSE-${record.symbol}",
                    startTime = from, endTime = until, candleInterval = "1minute"
                ).requirePayload("Univest exit market evidence")
            }.getOrNull() ?: continue
            val estimate = history.candles.mapNotNull { c ->
                if (c.size < 5) return@mapNotNull null
                val ts = researchCandleTimestampMs(c[0]) ?: return@mapNotNull null
                val closeAt = ts + 60_000L
                val price = runCatching { c[4].asDouble }.getOrNull()
                if (closeAt < exit || closeAt - exit > 180_000L ||
                    price == null || !price.isFinite() || price <= 0.0) null
                else Pair(closeAt, price)
            }.minByOrNull { it.first } ?: continue
            if (forecastUnivestJournal.quoteExit(
                record.key, estimate.second, estimate.first,
                "GROWW_BETA_1M_CANDLE_PROXY"
            ) != null) {
                count++
                auditLogger.logAsync("FORECAST_UNIVEST", "EXIT_MARKET_ESTIMATE_NOT_TRAINED", mapOf(
                    "symbol" to record.symbol, "market_price" to estimate.second,
                    "training_eligible" to false
                ))
            }
        }
        return count
    }

    private fun univestHistoricalCases(beforeDate: String): List<ForecastUnivestLearningPolicy.ClosedCase> {
        val completed = forecastUnivestJournal.training(beforeDate)
        val days = completed.map { ForecastUnivestLearningPolicy.date(it.entryNotificationAtMs) }
            .distinct().sortedDescending().take(30).toSet()
        return completed.filter { ForecastUnivestLearningPolicy.date(it.entryNotificationAtMs) in days }
            .mapNotNull { row ->
                val features = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    researchGson.fromJson(row.features48Json, Map::class.java) as Map<String, Any?>
                }.getOrNull() ?: return@mapNotNull null
                ForecastUnivestLearningPolicy.ClosedCase(row.symbol,
                    ForecastUnivestLearningPolicy.date(row.entryNotificationAtMs),
                    row.outcomePct ?: return@mapNotNull null, features)
            }
    }

    suspend fun captureMultifyResearchSnapshot(eventId: Long, symbol: String) {
        // This is Forecast research, not Multify order/execution tracking.
        // Use the same Beta-first token routing as normal Forecast scans and news evaluation.
        // If Beta is configured but unavailable, never borrow Alpha's market-data quota.
        val researchAccount = if (secretStore.hasBetaCredentials()) "BETA" else "ALPHA_FALLBACK"
        val token = forecastMarketToken()
        if (token.isNullOrBlank() || (researchAccount == "BETA" && !forecastUsesBeta(token))) {
            auditLogger.log("MULTIFY_RESEARCH", "FEATURE_CAPTURE_SKIPPED_NO_RESEARCH_ACCOUNT", mapOf(
                "event_id" to eventId, "symbol" to symbol,
                "research_account" to researchAccount, "beta_configured" to secretStore.hasBetaCredentials(),
                "alpha_fallback_used" to false
            ))
            return
        }
        val evaluation = runCatching { forecastEvaluation(symbol, token) }
            .onFailure {
                auditLogger.log("MULTIFY_RESEARCH", "FEATURE_CAPTURE_ERROR", mapOf(
                    "event_id" to eventId, "symbol" to symbol,
                    "research_account" to researchAccount,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull() ?: return
        val capturedAt = System.currentTimeMillis()
        val model = MultifyFactorFamilies.evaluate(evaluation, "BREADTH_NEUTRAL")
        val featureJson = researchGson.toJson(researchFeatureMap(evaluation, model))
        learningDao.insertForecastResearch(ForecastResearchEntity(
            researchDate = LocalDate.now(INDIA).toString(), scanAtMs = capturedAt,
            symbol = symbol.uppercase(Locale.US), rank = 0, qualityTier = "MULTIFY_REFERENCE",
            publishEligible = false, executionEligible = false, targetPct = 1.0,
            entryPrice = evaluation.features.ltp, regime = evaluation.regime,
            marketRegime = "BREADTH_NEUTRAL", strategy = evaluation.strategy,
            directionalScore = evaluation.directionalScore, confidence = evaluation.confidence,
            rejectionReason = "Evidence only: pending 15-minute post-call outcome",
            featureJson = featureJson,
            voteJson = researchGson.toJson(evaluation.votes.map { mapOf(
                "name" to it.name, "score" to it.score, "note" to it.note) }),
            multifyMatched = true, multifyEventId = eventId, multifyAtMs = capturedAt
        ))
        auditLogger.log("MULTIFY_RESEARCH", "FEATURE_SNAPSHOT", mapOf(
            "event_id" to eventId,
            "symbol" to symbol,
            "research_account" to researchAccount,
            "beta_configured" to secretStore.hasBetaCredentials(),
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
        val independentBeta = secretStore.hasBetaCredentials() && betaResearchAuthenticated()
        if (marketCriticalTradeInFlight ||
            (fixedApiPriorityRequested && !independentBeta) ||
            !ForecastBetaPriorityPolicy.canResearch(
                fixedPriorityRequested = fixedApiPriorityRequested,
                criticalTradeInFlight = marketCriticalTradeInFlight,
                fixedDeferredForBroker = independentBeta && fixedScanDeferredBroker,
                millisUntilNextFixed = millisUntilNextFixedScan(),
                fixedGuardMs = forecastFixedGuardMs(),
                allowIndependentBetaDuringFixed = independentBeta
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
        // Avoid candle-API use for an out-of-band Forecast quote.
        if (!RecommendationPriceBandPolicy.eligible(quote.lastPrice)) {
            auditLogger.logAsync("FORECAST_RESEARCH", "PRICE_BAND_SKIPPED_HISTORICAL", mapOf(
                "symbol" to symbol, "ltp" to quote.lastPrice, "min_exclusive" to 200, "max_exclusive" to 5000
            ))
            throw IllegalArgumentException("FORECAST_PRICE_BAND_OUTSIDE")
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
        // Use only already downloaded 1m candles; research-only, zero extra API calls.
        val five = ReadOnlyFiveMinuteResearchPolicy.evaluate(features.candles, System.currentTimeMillis())
        if (five.completeBars >= 3) auditLogger.logAsync("FORECAST_5M_RESEARCH", "ENTRY_CONTEXT", mapOf(
            "symbol" to symbol, "complete_five_minute_bars" to five.completeBars,
            "close" to five.lastFiveMinuteClose, "ema9" to five.ema9,
            "ema20" to five.ema20, "vwap" to five.vwap,
            "relative_volume" to five.relativeVolume, "bullish_candle" to five.bullishCandle,
            "above_vwap" to five.aboveVwap, "ema_bullish" to five.emaBullish,
            "three_bar_breakout" to five.threeBarBreakout,
            "no_extra_broker_requests" to true, "trading_effect" to "NONE"))
        val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "forecast", confidence = 1.0)
        return LocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
    }

    /** NSE-only: capture announcements even outside session; publish immediately if a
     * previously validated catalyst pattern remains unpriced at the open or on arrival.
     * Beta provides read-only quotes. No minimum upward move or breakout confirmation.
     */
    suspend fun monitorForecastNews(): Int {
        val newlyCaptured = forecastNewsMonitor.poll()
        val enabled = forecastBackgroundScanningEnabled()
        if (!enabled) {
            auditLogger.logAsync("FORECAST_NEWS", "CAPTURE_ONLY_SCANNER_DISABLED", mapOf(
                "captured" to newlyCaptured, "exchange" to "NSE", "orders_enabled" to false))
            return newlyCaptured
        }
        val now = ZonedDateTime.now(INDIA)
        val today = now.toLocalDate().toString()
        if (marketSession() != "OPEN" || now.dayOfWeek.value >= 6) return newlyCaptured
        val token = forecastMarketToken() ?: run {
            auditLogger.logAsync("FORECAST_NEWS", "NO_BETA_MARKET_TOKEN_OR_FORECAST_TOKEN")
            return newlyCaptured
        }
        val guard = if (secretStore.hasBetaCredentials()) betaForecastCycleMutex else scanApiCycleMutex
        if (!guard.tryLock()) return newlyCaptured
        try {
            val cutoff = now.toLocalDate().atStartOfDay(INDIA).toInstant().toEpochMilli()
            val stats = forecastNewsJournal.completedPatternStats(cutoff).associateBy { it.key }
            val pending = forecastNewsJournal.dueForEvaluation(System.currentTimeMillis(), limit = 5)
            for (item in pending) {
                if (fixedApiPriorityRequested || marketCriticalTradeInFlight ||
                    !forecastMarketCapacityAvailable()) {
                    auditLogger.logAsync("FORECAST_NEWS", "EVALUATION_DEFERRED_BROKER_CAPACITY", mapOf(
                        "pending" to pending.size, "fixed_priority_sacrificed" to false))
                    break
                }
                // An after-hours report may be actionable at the next open, but stale
                // intraday reports must NOT become retroactive recommendations.
                val evaluatedAt = System.currentTimeMillis()
                val ageMs = evaluatedAt - item.publishedAtMs
                val carry = com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.isCarryForward(
                    item.publishedAtMs, evaluatedAt)
                val marketOpenAgeMinutes = java.time.Duration.between(
                    java.time.LocalTime.of(9, 15), now.toLocalTime()).toMinutes()
                if ((ageMs > 20L * 60_000L && !carry) || (carry && marketOpenAgeMinutes > 90L)) {
                    forecastNewsJournal.update(item.copy(status = "EXPIRED", rationale = "STALE_OR_PREOPEN_WINDOW_EXPIRED"))
                    auditLogger.logAsync("FORECAST_NEWS", "NEWS_RECOMMENDATION_EXPIRED", mapOf(
                        "symbol" to item.symbol, "category" to item.category,
                        "age_ms" to ageMs, "carry_forward" to carry, "exchange" to "NSE"))
                    continue
                }
                // The latest independent NSE events, not repeated publications of the
                // same filing, form the joint-catalyst context of this announcement.
                val related = forecastNewsJournal.recent(1000).filter { other ->
                    other.symbol.equals(item.symbol, true) &&
                        other.direction == "POSITIVE_POSSIBLE" &&
                        kotlin.math.abs(other.publishedAtMs - item.publishedAtMs) <= 30L * 60_000L
                }.distinctBy { it.headline.uppercase(java.util.Locale.ROOT)
                    .replace(Regex("[^A-Z0-9]"), "").take(180) }
                val categories = (related.map { it.category } + item.category).distinct().sorted()
                val distinctCatalysts = related.size.coerceAtLeast(1)
                val conflicting = forecastNewsJournal.recent(1000).any { other ->
                    other.symbol.equals(item.symbol, true) &&
                        other.direction == "NEGATIVE_OR_AMBIGUOUS" &&
                        kotlin.math.abs(other.publishedAtMs - item.publishedAtMs) <= 30L * 60_000L
                }
                val catalystKey = categories.joinToString("+")
                // One fresh Beta quote is enough for the trained fast path. We do not
                // wait for a 1-minute/5-minute breakout or a +1% price reaction.
                val quote = runCatching {
                    withForecastLiveDataSlot {
                        betaMarketRead(token, BetaResearchGate.Type.LIVE,
                            alphaCall = { apiFactory.groww.quote(bearer(token), tradingSymbol = item.symbol)
                                .requirePayload("NSE news quote ${item.symbol}") },
                            betaCall = { apiFactory.betaGroww.quote(bearer(token), tradingSymbol = item.symbol)
                                .requirePayload("Beta NSE news quote ${item.symbol}") })
                    }
                }.onFailure { auditLogger.logAsync("FORECAST_NEWS", "BETA_NEWS_QUOTE_RETRY", mapOf(
                    "id" to item.id, "symbol" to item.symbol, "exception" to it.javaClass.simpleName))
                }.getOrNull() ?: continue
                val ltp = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: continue
                val bid = quote.bidPrice?.takeIf { it.isFinite() && it > 0.0 }
                    ?: quote.depth?.buy?.mapNotNull { it.price?.takeIf { p -> p.isFinite() && p > 0.0 } }?.maxOrNull()
                val ask = quote.offerPrice?.takeIf { it.isFinite() && it > 0.0 }
                    ?: quote.depth?.sell?.mapNotNull { it.price?.takeIf { p -> p.isFinite() && p > 0.0 } }?.minOrNull()
                val spreadBps = if (bid != null && ask != null && ask >= bid && bid > 0.0)
                    (ask - bid) / ((ask + bid) / 2.0) * 10_000.0 else null
                val rawFeatures = runCatching {
                    com.google.gson.JsonParser.parseString(item.featureJson).asJsonObject
                }.getOrNull()
                val amountCrore = runCatching {
                    rawFeatures?.get("reported_amount_crore")?.takeUnless { it.isJsonNull }?.asDouble
                }.getOrNull()
                val growwCap = quote.marketCap?.takeIf { it.isFinite() && it >= 100_000_000.0 }
                val fallbackCap = if (growwCap == null && amountCrore != null) {
                    nseMarketCapResearch.marketCapRupees(item.symbol)
                } else null
                val cap = growwCap ?: fallbackCap
                auditLogger.logAsync("FORECAST_NEWS", "MARKET_CAP_PROVENANCE", mapOf(
                    "symbol" to item.symbol, "provider" to when {
                        growwCap != null -> "GROWW_QUOTE"
                        fallbackCap != null -> "NSE_TRADE_INFO"
                        else -> "UNAVAILABLE"
                    }, "market_cap_rupees" to cap,
                    "required_for_materiality" to (amountCrore != null),
                    "missing_not_estimated" to (cap == null)))
                val policy = com.multify.traderpro.engine.ForecastNewsOpportunityPolicy
                val materiality = policy.materialityPct(amountCrore, cap)
                val patternKey = policy.patternKey(catalystKey, policy.capBucket(cap), distinctCatalysts, materiality)
                val history = stats[patternKey]
                val evidence = com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.Evidence(history?.observations ?: 0,
                    history?.distinctDays ?: 0, history?.hitRate ?: 0.0, history?.averageMfe ?: 0.0)
                val decision = policy.decide(com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.Context(quote.dayChangePct, spreadBps, ltp,
                    amountCrore, cap, distinctCatalysts, catalystKey, ageMs / 60_000L, evidence))
                val settings = preferences.settings.first()
                val economics = ForecastAdaptiveSelectionPolicy.economics(
                    entryPrice = ltp, targetPct = 1.0, budgetRupees = settings.holdingBudgetRupees,
                    spreadBps = spreadBps, atrPct = null)
                val qualifies = item.direction == "POSITIVE_POSSIBLE" &&
                    decision.eligible && !conflicting
                val provisionalWatch = item.direction == "POSITIVE_POSSIBLE" &&
                    !decision.eligible && decision.reason == "PATTERN_NOT_VALIDATED" &&
                    !conflicting && RecommendationPriceBandPolicy.eligible(ltp) &&
                    (quote.dayChangePct ?: 99.0) <= 2.0 &&
                    spreadBps != null && spreadBps in 0.0..40.0
                val existing = learningDao.longForecastsForDate(today)
                val alreadyRecommended = existing.any { it.symbol.equals(item.symbol, true) }
                val publish = ForecastNewsPublicationGate.mayPublish(ltp, qualifies, alreadyRecommended)
                val state = when {
                    !RecommendationPriceBandPolicy.eligible(ltp) -> "OUTSIDE_200_5000_EXCLUSIVE_PRICE_BAND"
                    conflicting -> "CONFLICTING_NEWS"
                    decision.reason == "AVERAGE_GAIN_NOT_ABOVE_1PCT" -> "AVERAGE_GAIN_NOT_ABOVE_1PCT"
                    alreadyRecommended -> "DUPLICATE_SAME_DAY"
                    else -> decision.reason
                }
                // Keep the canonical 40 headline features, and append verified contextual
                // fields for historical outcome learning. UNKNOWN remains null.
                val enriched = linkedMapOf<String, Any?>(
                    "news_pattern_key" to patternKey, "exchange" to "NSE",
                    "news_category_combo" to catalystKey, "distinct_catalysts" to distinctCatalysts,
                    "market_cap_rupees" to cap, "reported_amount_crore" to amountCrore,
                    "news_amount_to_market_cap_pct" to materiality, "cap_bucket" to decision.capBucket,
                    "news_age_minutes" to (ageMs / 60_000L), "carry_forward" to carry,
                    "day_change_pct_at_evaluation" to quote.dayChangePct,
                    "spread_bps_at_evaluation" to spreadBps,
                    "evidence_samples" to evidence.observations,
                    "evidence_distinct_days" to evidence.distinctDays,
                    "evidence_one_pct_hit_rate" to evidence.hitRate,
                    "evidence_proven" to evidence.proven, "entry_decision" to state,
                    "news_price_band_eligible" to RecommendationPriceBandPolicy.eligible(ltp),
                    "average_gain_pct_required_exclusive" to 1.0,
                    "historical_pattern_average_gain_pct" to evidence.averageMfePct,
                    "provisional_watch" to provisionalWatch,
                    "no_price_spike_required" to true, "news_features_40" to item.featureJson)
                forecastNewsJournal.update(item.copy(
                    featureJson = researchGson.toJson(enriched),
                    status = if (publish) "PUBLISHED" else if (provisionalWatch) "PROVISIONAL_WATCH" else "RESEARCH_ONLY",
                    rationale = state, baselinePrice = ltp, baselineAtMs = evaluatedAt,
                    lastObservedPrice = ltp, lastObservedAtMs = evaluatedAt))
                // All signals, including unsuccessful ones, enter the learning denominator.
                learningDao.insertForecastResearch(ForecastResearchEntity(
                    researchDate = today, scanAtMs = evaluatedAt, symbol = item.symbol,
                    rank = 0, qualityTier = when {
                        publish -> "NEWS_PRIMARY"
                        provisionalWatch -> "NEWS_PROVISIONAL_WATCH"
                        else -> "NEWS_RESEARCH"
                    },
                    publishEligible = publish, executionEligible = publish,
                    targetPct = 1.0, entryPrice = ltp, regime = "NEWS_PRE_SPIKE",
                    marketRegime = "NSE_ONLY", strategy = "NEWS_" + item.category,
                    directionalScore = evidence.hitRate, confidence = item.classificationConfidence,
                    rejectionReason = state, featureJson = researchGson.toJson(enriched), voteJson = "[]"))
                if (publish) {
                    learningDao.upsertLongForecast(LongForecastEntity(
                        forecastDate = today, rank = (existing.maxOfOrNull { it.rank } ?: 0) + 1,
                        symbol = item.symbol, entryPrice = ltp, targetPct = 1.01,
                        targetPrice = ltp * 1.0101,
                        confidence = evidence.hitRate.coerceIn(0.0, 0.95),
                        score = evidence.hitRate.coerceIn(0.0, 0.95),
                        strategy = "NEWS_" + item.category, regime = "NEWS_PRE_SPIKE",
                        marketRegime = "NSE_ONLY", reason = "Validated pre-spike NSE catalyst: " +
                            catalystKey + " · $state · learned " + evidence.observations +
                            " samples on " + evidence.distinctDays + " days · no price move required",
                        generatedAtMs = evaluatedAt, lastPrice = ltp,
                        lastObservedAtMs = evaluatedAt, qualityTier = "PRIMARY", executionEligible = true))
                }
                auditLogger.logAsync("FORECAST_NEWS", "EARLY_CATALYST_DECISION", mapOf(
                    "id" to item.id, "symbol" to item.symbol, "source" to item.source,
                    "exchange" to "NSE", "category" to item.category, "catalyst_combo" to catalystKey,
                    "pattern_key" to patternKey, "distinct_catalysts" to distinctCatalysts,
                    "age_ms" to ageMs, "carry_forward" to carry,
                    "quote_research_account" to (if (forecastUsesBeta(token)) "BETA" else "ALPHA_FALLBACK"),
                    "quote_ltp" to ltp, "market_cap_rupees" to cap, "amount_crore" to amountCrore,
                    "materiality_pct" to materiality, "day_change_pct" to quote.dayChangePct,
                    "spread_bps" to spreadBps, "conflicting_news" to conflicting,
                    "samples" to evidence.observations, "distinct_days" to evidence.distinctDays,
                    "hit_rate" to evidence.hitRate, "validated" to evidence.proven,
                    "historical_avg_favourable_gain_pct" to evidence.averageMfePct,
                    "historical_avg_gain_gt_1pct" to (evidence.averageMfePct > 1.0),
                    "provisional_watch" to provisionalWatch,
                    "provisional_cannot_place_orders" to true,
                    "rupee_profit_requirement" to "NONE",
                    "economics_net_rupees" to economics.netProfitRupees,
                    "result" to state, "published" to publish,
                    "price_band_eligible" to RecommendationPriceBandPolicy.eligible(ltp),
                    "price_min_exclusive_rupees" to 200, "price_max_exclusive_rupees" to 5000,
                    "frozen_fixed_untouched" to true, "no_spike_confirmation" to true))
            }
        } finally { guard.unlock() }
        return newlyCaptured
    }
    /** Completed outcome study: can run outside market hours without broker requests. */
    fun auditForecastNewsLearning() {
        val cutoff = LocalDate.now(INDIA).atStartOfDay(INDIA).toInstant().toEpochMilli()
        val stats = forecastNewsJournal.completedCategoryStats(cutoff)
        auditLogger.logAsync("FORECAST_NEWS", "CHRONOLOGICAL_CATEGORY_LEARNING", mapOf(
            "as_of_before_ms" to cutoff,
            "categories" to stats.map { row -> mapOf(
                "category" to row.category, "observations" to row.observations,
                "one_pct_hits" to row.hitsOnePct, "hit_rate" to row.hitRate,
                "distinct_days" to row.distinctDays,
                "average_mfe_pct" to row.averageMfe) },
            "needs_out_of_sample_validation" to true,
            "fixed_engine_changed" to false))
        val patterns = forecastNewsJournal.completedPatternStats(cutoff)
        auditLogger.logAsync("FORECAST_NEWS", "CONTEXTUAL_PATTERN_LEARNING", mapOf(
            "exchange" to "NSE", "observed_patterns" to patterns.size,
            "minimum_samples" to com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.MIN_PATTERN_SAMPLES,
            "minimum_distinct_days" to com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.MIN_PATTERN_DAYS,
            "minimum_one_pct_hit_rate" to com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.MIN_PATTERN_HIT_RATE,
            "patterns" to patterns.take(100).map { pattern -> mapOf(
                "key" to pattern.key, "observations" to pattern.observations,
                "distinct_days" to pattern.distinctDays, "wins" to pattern.wins,
                "hit_rate" to pattern.hitRate, "average_mfe_pct" to pattern.averageMfe,
                "validated" to (pattern.observations >= com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.MIN_PATTERN_SAMPLES &&
                    pattern.distinctDays >= com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.MIN_PATTERN_DAYS &&
                    pattern.hitRate >= com.multify.traderpro.engine.ForecastNewsOpportunityPolicy.MIN_PATTERN_HIT_RATE)) },
            "current_day_leakage" to false, "fixed_engine_changed" to false))
    }

    /** One-minute outcome snapshots, bounded to avoid stealing API calls from Fixed or SELL. */
    suspend fun monitorForecastNewsOutcomes() {
        if (marketSession() != "OPEN" || fixedApiPriorityRequested || marketCriticalTradeInFlight ||
            !forecastMarketCapacityAvailable()) return
        val token = forecastMarketToken() ?: return
        for (item in forecastNewsJournal.dueForOutcome(System.currentTimeMillis(), 2)) {
            val initial = item.baselinePrice?.takeIf { it > 0.0 } ?: continue
            val quote = runCatching {
                withForecastLiveDataSlot {
                    betaMarketRead(token, BetaResearchGate.Type.LIVE,
                        alphaCall = { apiFactory.groww.quote(bearer(token), tradingSymbol = item.symbol)
                            .requirePayload("News outcome ${item.symbol}") },
                        betaCall = { apiFactory.betaGroww.quote(bearer(token), tradingSymbol = item.symbol)
                            .requirePayload("Beta news outcome ${item.symbol}") })
                }
            }.getOrNull() ?: continue
            val price = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val pct = (price / initial - 1.0) * 100.0
            val now = System.currentTimeMillis()
            val hitTime = item.firstOnePctAtMs ?: if (pct >= 1.0) now else null
            val complete = now - (item.baselineAtMs ?: now) >= 60L * 60_000L
            forecastNewsJournal.update(item.copy(maxFavourablePct = maxOf(item.maxFavourablePct, pct),
                maxAdversePct = minOf(item.maxAdversePct, pct),
                lastObservedPrice = price, lastObservedAtMs = now,
                firstOnePctAtMs = hitTime, completedAtMs = if (complete) now else null))
            auditLogger.logAsync("FORECAST_NEWS", "POST_NEWS_PRICE", mapOf(
                "id" to item.id, "symbol" to item.symbol, "move_pct" to pct,
                "max_favourable_pct" to maxOf(item.maxFavourablePct, pct),
                "max_adverse_pct" to minOf(item.maxAdversePct, pct),
                "hit_one_pct" to (hitTime != null), "completed" to complete,
                "time_since_news_ms" to (now - item.publishedAtMs)))
        }
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
        require(RecommendationPriceBandPolicy.eligible(row.entryPrice)) { "Forecast BUY requires published price >₹200 and <₹5,000" }
        require(ForecastNotificationFreshnessPolicy.fresh(row.generatedAtMs, System.currentTimeMillis())) {
            "Forecast BUY blocked: recommendation older than 120 seconds"
        }
        require(forecastLiveUnlocked()) { "Forecast BUY requires five completed outcome-validated research sessions" }
        require(row.executionEligible) { "$symbol is ${row.qualityTier}; only PRIMARY forecast setups can place a real forecast BUY" }
        require(row.targetPct + 1e-9 >= ForecastQualityPolicy.MIN_TARGET_PCT) { "Forecast target is below the 1% quality floor" }
        if (managedDao.openPosition(ENGINE_FORECAST_HOLDING, row.symbol) != null) {
            return "${row.symbol} already has a Forecast holding; duplicate Forecast BUY blocked"
        }
        val quoteRequestStartedAtMs = System.currentTimeMillis()
        val quote = withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol).requirePayload("Quote ${row.symbol}")
        }
        val quoteRequestElapsedMs = System.currentTimeMillis() - quoteRequestStartedAtMs
        val ltp = quote.lastPrice ?: error("Groww quote has no LTP")
        require(RecommendationPriceBandPolicy.eligible(ltp)) { "Forecast BUY requires fresh price >₹200 and <₹5,000" }
        val forecastLiquidityReject = ExecutionQuoteLiquidityPolicy.rejectionReason(quote)
        if (forecastLiquidityReject != null) {
            auditLogger.logAsync("FORECAST_AUTO_BUY", "ENTRY_LIQUIDITY_UNVERIFIED", mapOf(
                "symbol" to row.symbol, "reason" to forecastLiquidityReject,
                "source" to "FRESH_EXECUTION_QUOTE", "fixed_forecast_models_changed" to false
            ))
            error("Forecast BUY blocked: broker book not verified ($forecastLiquidityReject)")
        }
        val forecastExtensionReject = ExecutionEntryPriceGuardPolicy.rejectionReason(
            row.entryPrice, ltp, quoteRequestElapsedMs)
        if (forecastExtensionReject != null) {
            auditLogger.logAsync("FORECAST_AUTO_BUY", "ENTRY_PRICE_GUARD_REJECTED",
                mapOf("symbol" to row.symbol, "reason" to forecastExtensionReject,
                    "quote_network_elapsed_ms" to quoteRequestElapsedMs, "reference" to row.entryPrice,
                    "fresh_ltp" to ltp, "trading_signal_changed" to false))
            error("Forecast BUY blocked: entry quote guard ($forecastExtensionReject)")
        }
        require(ltp < row.targetPrice) { "Forecast target already reached; BUY disabled" }
        val sizing = com.multify.traderpro.engine.BudgetOnlyCncSizing.size(settings.holdingBudgetRupees.toDouble(), ltp)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        require(qty > 0) { "Forecast BUY skipped: budget cannot fund one buffered share of ${row.symbol}" }
        val ref = stableRef("FLH", "${row.id}-${row.symbol}")
        val order = placeCncBuy(token, row.symbol, qty, ref, entryQuote = quote, quoteReceivedAtMs = quoteRequestStartedAtMs + quoteRequestElapsedMs)
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to row.symbol, "wallet_api_calls" to 0, "broker_funds_verdict_deferred_to_order" to true,
            "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to ltp,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty,
            "order_first" to true
        ))
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: ltp
        val investedAmount = entry * filled
        val trailArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT
        registerBoughtPositionAndProtect(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING, symbol = row.symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = HardStopLossPolicy.stopPrice(entry),
                targetPrice = entry * (1.0 + row.targetPct / 100.0),
                strategy = "Forecast holding · ${row.strategy} · hard -5% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%",
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
    suspend fun autoBuyForecastRecommendations(): Int = 0 // Forecast is frozen; no new orders

    private suspend fun autoBuyForecastRecommendationsLocked(): Int {
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6 || marketSession() != "OPEN") return 0
        val settings = preferences.settings.first()
        if (!settings.forecastAutoBuyEnabled || settings.safetyHalt) return 0
        // No session-count BUY lock; production PRIMARY and broker safeguards below are unchanged.
        val preflightCooldown = buyApiCooldownOrNull()
        if (preflightCooldown != null) {
            auditAutoBuyBlocked("FORECAST_AUTO_BUY", preflightCooldown)
            return 0
        }
        preIndependentExecutionGuard(settings)
        val today = now.toLocalDate().toString()
        val rows = learningDao.longForecastsForDate(today)
            .filter { it.status == "ACTIVE" && it.executionEligible && it.qualityTier == "PRIMARY" && RecommendationPriceBandPolicy.eligible(it.entryPrice) }
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
        registerBoughtPositionAndProtect(
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
                    append(" · hard -5% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%")
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
        registerBoughtPositionAndProtect(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING,
                symbol = state.symbol,
                product = "CNC",
                side = "LONG",
                quantity = filledQuantity,
                entryPrice = actualFill,
                stopPrice = HardStopLossPolicy.stopPrice(actualFill),
                targetPrice = state.fixedTargetPrice,
                strategy = "Migrated legacy forecast LIMIT fill · fixed target ₹${fmt(state.fixedTargetPrice)} · hard -5% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%",
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
        require(com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedNewEntryWindow(now) && marketSession() == "OPEN") { "Fixed BUY is unavailable outside the NSE entry window" }
        val settings = preferences.settings.first()
        preIndependentExecutionGuard(settings)
        val token = ensureToken() ?: error("Authenticate Groww first")
        val today = now.toLocalDate().toString()
        // Most recent qualifying event wins for manual entry; older events stay
        // in research history but can never replace a fresh event for BUY.
        val row = fixedRecommendationStore.recommendations(today)
            .filter { it.symbol.equals(symbol, true) && it.status == "ACTIVE" }
            .maxByOrNull { it.generatedAtMs }
            ?: error("No active Fixed recommendation for $symbol")

        // Legacy entry GTTs migrate in the maintenance worker, never on the BUY hot path.
        val pendingLegacy = fixedAutoBuyOrderStore.forSymbol(today, row.symbol)
        if (pendingLegacy != null && !ForecastAutoBuyPolicy.isTerminal(pendingLegacy.status)) {
            return "Legacy Fixed entry GTT is still being reconciled for ${row.symbol}; no duplicate BUY was sent"
        }

        require(row.status == "ACTIVE") { "$symbol Fixed recommendation is no longer active" }
        require(fixedEntryVerdict(row, null).actionable) {
            "Fixed BUY blocked: late session or imported earlier recommendation"
        }
        require(RecommendationPriceBandPolicy.eligible(row.entryPrice)) { "Fixed executes only stocks strictly between ₹200 and ₹5,000" }
        require(row.executionEligible && row.qualityTier == "PRIMARY") { "Only PRIMARY Fixed recommendations can place a real BUY" }
        if (managedDao.openPosition(ENGINE_FIXED_HOLDING, row.symbol) != null) {
            return "${row.symbol} already has a Fixed holding; duplicate Fixed BUY blocked"
        }

        val quoteRequestStartedAtMs = System.currentTimeMillis()
        val quote = withFixedLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol).requirePayload("Quote ${row.symbol}")
        }
        val quoteRequestElapsedMs = System.currentTimeMillis() - quoteRequestStartedAtMs
        val ltp = quote.lastPrice ?: error("Groww quote has no LTP")
        require(RecommendationPriceBandPolicy.eligible(ltp)) { "Fixed BUY requires fresh price >₹200 and <₹5,000" }
        val fixedLiquidityReject = ExecutionQuoteLiquidityPolicy.rejectionReason(quote)
        if (fixedLiquidityReject != null) {
            auditLogger.logAsync("FIXED_BUY", "ENTRY_LIQUIDITY_UNVERIFIED", mapOf(
                "symbol" to row.symbol, "reason" to fixedLiquidityReject,
                "source" to "FRESH_EXECUTION_QUOTE", "fixed_scoring_changed" to false
            ))
            error("Fixed BUY blocked: broker book not verified ($fixedLiquidityReject)")
        }
        val thinLiquidityReject = FixedExecutionThinLiquidityPolicy.rejectionReason(quote)
        if (thinLiquidityReject != null) {
            auditLogger.logAsync("FIXED_BUY", "ENTRY_MICRO_LIQUIDITY_REJECTED", mapOf(
                "symbol" to row.symbol, "reason" to thinLiquidityReject,
                "reported_day_shares" to quote.volume,
                "daily_turnover_estimate" to (ltp * (quote.volume ?: 0L)),
                "fixed_scoring_changed" to false))
            error("Fixed BUY blocked: extremely thin reported turnover ($thinLiquidityReject)")
        }
        val entryActionability = fixedEntryVerdict(row, quote)
        if (!entryActionability.actionable) {
            auditLogger.logAsync("FIXED_BUY", "ENTRY_QUALITY_BLOCKED", mapOf(
                "symbol" to row.symbol, "reasons" to entryActionability.reasons,
                "first_primary_to_entry_pct" to entryActionability.firstPrimaryRunupPct,
                "distance_from_day_high_pct" to entryActionability.distanceFromQuoteHighPct,
                "fixed_model_unchanged" to true))
            error("Fixed BUY held for research: " + entryActionability.reasons.joinToString(","))
        }
        val fixedExtensionReject = ExecutionEntryPriceGuardPolicy.rejectionReason(
            row.entryPrice, ltp, quoteRequestElapsedMs)
        if (fixedExtensionReject != null) {
            auditLogger.logAsync("FIXED_BUY", "ENTRY_PRICE_GUARD_REJECTED",
                mapOf("symbol" to row.symbol, "reason" to fixedExtensionReject,
                    "quote_network_elapsed_ms" to quoteRequestElapsedMs, "reference" to row.entryPrice,
                    "fresh_ltp" to ltp, "fixed_scoring_changed" to false))
            error("Fixed BUY blocked: entry quote guard ($fixedExtensionReject)")
        }
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
        val sizing = com.multify.traderpro.engine.BudgetOnlyCncSizing.size(settings.holdingBudgetRupees.toDouble(), ltp)
        require(sizing.quantity > 0) { "Fixed BUY skipped: budget cannot fund one buffered share of ${row.symbol}" }

        require(com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedNewEntryWindow(ZonedDateTime.now(INDIA)) && marketSession() == "OPEN") {
            "Fixed BUY disabled: NSE special-session/regular entry cutoff reached"
        }
        require(fixedRecommendationStore.recommendations(today).firstOrNull { it.id == row.id }?.status == "ACTIVE") {
            "Fixed BUY disabled: recommendation is no longer active"
        }
        val ref = stableRef("FXH", "${row.recommendationDate}-${row.id}-${row.symbol}")
        val order = placeCncBuy(token, row.symbol, sizing.quantity, ref, entryQuote = quote, quoteReceivedAtMs = quoteRequestStartedAtMs + quoteRequestElapsedMs)
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: sizing.quantity
        val actualFill = order.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 } ?: ltp
        val executionTarget = actualFill * (1.0 + row.targetPct / 100.0)
        val nowMs = System.currentTimeMillis()
        registerBoughtPositionAndProtect(
            ManagedPositionEntity(
                engine = ENGINE_FIXED_HOLDING,
                symbol = row.symbol,
                product = "CNC",
                side = "LONG",
                quantity = filled,
                entryPrice = actualFill,
                stopPrice = HardStopLossPolicy.stopPrice(actualFill),
                targetPrice = executionTarget,
                strategy = "Fixed ${FixedEngineSnapshotStore.SNAPSHOT_ID} manual direct MARKET BUY · published entry ₹${fmt(row.entryPrice)} · broker fill ₹${fmt(actualFill)} · execution target ₹${fmt(executionTarget)} · ${row.strategy} · hard -5% floor · v7 stepped trail 0.55→0.50 then +0.10%",
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
        if (!com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedNewEntryWindow(now) || marketSession() != "OPEN") return 0
        val settings = preferences.settings.first()
        if (!settings.fixedAutoBuyEnabled || settings.safetyHalt) return 0
        // Old installations can contain ON=true without the new activation timestamp.
        // Fail closed until the user explicitly switches OFF and then ON again.
        if (settings.fixedAutoBuyActivatedAtMs <= 0L) {
            auditLogger.logAsync("FIXED_AUTO_BUY", "BUY_BLOCKED_ACTIVATION_TIMESTAMP_MISSING",
                mapOf("action" to "TOGGLE_OFF_THEN_ON", "old_recommendations_ineligible" to true))
            return 0
        }
        val today = now.toLocalDate().toString()

        // Legacy entry GTTs migrate in the maintenance worker; normal direct buys do no pre-order broker reconciliation.
        val rows = fixedRecommendationStore.recommendations(today)
            .filter {
                it.status == "ACTIVE" &&
                    it.executionEligible &&
                    it.qualityTier == "PRIMARY" &&
                    RecommendationPriceBandPolicy.eligible(it.entryPrice) &&
                    AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(
                        it.generatedAtMs, settings.fixedAutoBuyActivatedAtMs)
            }
            .groupBy { it.symbol.uppercase(Locale.US) }
            .values.mapNotNull { events -> events.maxByOrNull { it.generatedAtMs } }
            .sortedBy { it.generatedAtMs }

        // An activation with only previously published recommendations should not
        // trigger a broker token refresh or any order-preparation API traffic.
        if (rows.isEmpty()) return 0
        val preflightCooldown = buyApiCooldownOrNull(GrowwApiUsageLedger.Category.LIVE_DATA)
        if (preflightCooldown != null) {
            auditAutoBuyBlocked("FIXED_AUTO_BUY", preflightCooldown)
            return 0
        }
        preFixedAutoBuyExecutionGuard(settings)
        val token = ensureToken() ?: run {
            auditLogger.logAsync("FIXED_AUTO_BUY", "BUY_BLOCKED_BROKER_NOT_READY", mapOf("reason" to "TOKEN_NOT_READY"))
            return 0
        }

        var submitted = 0
        for (row in rows) {
            val symbolStartedAtMs = System.currentTimeMillis()
            val recommendationAgeAtStartMs = (symbolStartedAtMs - row.generatedAtMs).coerceAtLeast(0L)
            val liveSettings = preferences.settings.first()
            if (!liveSettings.fixedAutoBuyEnabled || liveSettings.safetyHalt || marketSession() != "OPEN") break
            if (!AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(
                    row.generatedAtMs, liveSettings.fixedAutoBuyActivatedAtMs)) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_SKIPPED_PRE_ACTIVATION", mapOf(
                    "symbol" to row.symbol, "recommendation_at_ms" to row.generatedAtMs,
                    "activated_at_ms" to liveSettings.fixedAutoBuyActivatedAtMs))
                continue
            }
            val preEntryVerdict = fixedEntryVerdict(row, null, symbolStartedAtMs)
            if (!preEntryVerdict.actionable) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_QUALITY_BLOCKED_BEFORE_QUOTE",
                    mapOf("symbol" to row.symbol, "reasons" to preEntryVerdict.reasons,
                        "fixed_model_unchanged" to true))
                continue
            }
            if (!AutoBuyEntrySafetyPolicy.isFreshFixedEntry(row.generatedAtMs, symbolStartedAtMs)) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_SKIPPED_STALE_RECOMMENDATION", mapOf(
                    "symbol" to row.symbol,
                    "recommendation_age_ms" to recommendationAgeAtStartMs,
                    "max_age_ms" to AutoBuyEntrySafetyPolicy.FIXED_AUTO_BUY_MAX_AGE_MS
                ))
                continue
            }
            val cooldown = buyApiCooldownOrNull(GrowwApiUsageLedger.Category.LIVE_DATA)
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
                val cooldown = buyApiCooldownOrNull(GrowwApiUsageLedger.Category.LIVE_DATA)
                if (cooldown != null) {
                    auditAutoBuyBlocked("FIXED_AUTO_BUY", cooldown)
                    break
                }
                continue
            }
            val quoteElapsedMs = (System.currentTimeMillis() - quoteStartedAtMs).coerceAtLeast(0L)
            val ltp = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            if (!RecommendationPriceBandPolicy.eligible(ltp)) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "PRICE_BAND_REJECTED", mapOf("symbol" to row.symbol, "fresh_ltp" to ltp))
                continue
            }
            val liquidityReject = ExecutionQuoteLiquidityPolicy.rejectionReason(quote)
            if (liquidityReject != null) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_LIQUIDITY_UNVERIFIED", mapOf(
                    "symbol" to row.symbol, "reason" to liquidityReject,
                    "fresh_quote" to true, "scoring_changed" to false
                ))
                continue
            }
            val thinLiquidityReject = FixedExecutionThinLiquidityPolicy.rejectionReason(quote)
            if (thinLiquidityReject != null) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_MICRO_LIQUIDITY_REJECTED", mapOf(
                    "symbol" to row.symbol, "reason" to thinLiquidityReject,
                    "reported_day_shares" to quote.volume,
                    "daily_turnover_estimate" to (ltp * (quote.volume ?: 0L)),
                    "fixed_scoring_changed" to false))
                continue
            }
            val entryActionability = fixedEntryVerdict(row, quote)
            if (!entryActionability.actionable) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_QUALITY_BLOCKED", mapOf(
                    "symbol" to row.symbol, "reasons" to entryActionability.reasons,
                    "first_primary_to_entry_pct" to entryActionability.firstPrimaryRunupPct,
                    "distance_from_day_high_pct" to entryActionability.distanceFromQuoteHighPct,
                    "fixed_model_unchanged" to true))
                continue
            }
            val autoExtensionReject = ExecutionEntryPriceGuardPolicy.rejectionReason(
                row.entryPrice, ltp, quoteElapsedMs)
            if (autoExtensionReject != null) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_PRICE_GUARD_REJECTED",
                    mapOf("symbol" to row.symbol, "reason" to autoExtensionReject,
                        "reference" to row.entryPrice, "fresh_ltp" to ltp,
                        "quote_network_elapsed_ms" to quoteElapsedMs, "fixed_scoring_changed" to false))
                continue
            }
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

            // A previously acquired fresh execution quote remains valid even if
            // unrelated live-data scanning subsequently enters cooldown.
            // Inventory verification still needs NON_TRADING, and broker orders
            // remain subject to the independent ORDERS category.
            val executionCooldown = buyApiCooldownOrNull(
                GrowwApiUsageLedger.Category.NON_TRADING,
                GrowwApiUsageLedger.Category.ORDERS
            )
            if (executionCooldown != null) {
                auditAutoBuyBlocked("FIXED_AUTO_BUY", executionCooldown)
                break
            }
            val sizing = com.multify.traderpro.engine.BudgetOnlyCncSizing.size(
                liveSettings.holdingBudgetRupees.toDouble(), ltp
            )
            // ₹30k is a ceiling for THIS stock/order only. No wallet or daily cap.
            auditLogger.logAsync("FIXED_AUTO_BUY", "BUDGET_PER_ORDER_SIZED_NO_WALLET_API", mapOf(
                "symbol" to row.symbol,
                "per_order_cap_rupees" to liveSettings.holdingBudgetRupees,
                "requested_quantity" to sizing.quantity,
                "buffered_price" to sizing.bufferedPrice,
                "wallet_api_calls" to 0,
                "daily_budget_limit_applied" to false,
                "broker_funds_verdict_deferred_to_order" to true
            ))
            if (sizing.quantity <= 0) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "SKIPPED_INVALID_BUDGET_OR_PRICE", mapOf(
                    "symbol" to row.symbol, "fresh_ltp" to ltp,
                    "per_order_cap_rupees" to liveSettings.holdingBudgetRupees
                ))
                continue
            }

            val executionSettings = preferences.settings.first()
            if (!executionSettings.fixedAutoBuyEnabled || executionSettings.safetyHalt ||
                !com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedNewEntryWindow(ZonedDateTime.now(INDIA)) ||
                marketSession() != "OPEN") break
            // Recheck immediately before the order: toggling OFF/ON while quotes load
            // must never make a previously published call newly executable.
            if (!AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(
                    row.generatedAtMs, executionSettings.fixedAutoBuyActivatedAtMs)) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_SKIPPED_ACTIVATION_CHANGED", mapOf(
                    "symbol" to row.symbol, "recommendation_at_ms" to row.generatedAtMs,
                    "activated_at_ms" to executionSettings.fixedAutoBuyActivatedAtMs))
                continue
            }
            if (!AutoBuyEntrySafetyPolicy.isFreshFixedEntry(row.generatedAtMs, System.currentTimeMillis())) {
                auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_SKIPPED_EXPIRED_DURING_PREP", mapOf("symbol" to row.symbol))
                continue
            }
            if (fixedRecommendationStore.recommendations(today).firstOrNull { it.id == row.id }?.status != "ACTIVE") continue

            val reference = stableRef("FXM", "${row.recommendationDate}-${row.id}-${row.symbol}")
            val orderStartedAtMs = System.currentTimeMillis()
            val order = runCatching {
                placeCncBuy(token, row.symbol, sizing.quantity, reference, entryQuote = quote,
                    quoteReceivedAtMs = quoteStartedAtMs + quoteElapsedMs,
                    allowOtherSymbolProtectionRecovery = true)
            }.onFailure {
                // Response body is already available locally; this does not request wallet
                // balance or make another broker call. Never persist the raw broker body.
                val brokerError = runCatching {
                    (it as? HttpException)?.response()?.errorBody()?.string()?.take(1_024)
                }.getOrNull()
                val insufficient = BrokerBuyFailureMessagePolicy.insufficientFunds(
                    it.message.orEmpty() + " " + brokerError.orEmpty())
                auditLogger.log("FIXED_AUTO_BUY", "MARKET_BUY_ERROR", mapOf(
                    "symbol" to row.symbol,
                    "reference" to reference,
                    "fresh_ltp" to ltp,
                    "message" to (it.message ?: it.javaClass.simpleName),
                    "insufficient_funds" to insufficient,
                    "wallet_api_calls" to 0
                ))
                if (insufficient) notifyInsufficientFundsIfConfirmed(row.symbol)
            }.getOrNull() ?: continue

            val orderElapsedMs = (System.currentTimeMillis() - orderStartedAtMs).coerceAtLeast(0L)
            val filled = order.filledQuantity?.takeIf { it > 0 } ?: sizing.quantity
            val actualFill = order.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 } ?: ltp
            val executionTarget = actualFill * (1.0 + row.targetPct / 100.0)
            val openedAtMs = System.currentTimeMillis()
            registerBoughtPositionAndProtect(
                ManagedPositionEntity(
                    engine = ENGINE_FIXED_HOLDING,
                    symbol = row.symbol,
                    product = "CNC",
                    side = "LONG",
                    quantity = filled,
                    entryPrice = actualFill,
                    stopPrice = HardStopLossPolicy.stopPrice(actualFill),
                    targetPrice = executionTarget,
                    strategy = "Fixed ${FixedEngineSnapshotStore.SNAPSHOT_ID} auto-buy · direct MARKET · published entry ₹${fmt(row.entryPrice)} · fill ₹${fmt(actualFill)} · execution target ₹${fmt(executionTarget)} · ${row.strategy} · hard -5% floor · v7 stepped trail 0.55→0.50 then +0.10%",
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
                ),
                allowFreshPostFillProof = true,
                deferProtectionRetryToMonitor = true
            )
            brokerInventorySnapshot = null
            auditLogger.logAsync("FIXED_AUTO_BUY", "ENTRY_LATENCY_PROFILE", mapOf(
                "symbol" to row.symbol,
                "recommendation_generated_at_ms" to row.generatedAtMs,
                "recommendation_age_at_auto_buy_start_ms" to recommendationAgeAtStartMs,
                "quote_ms" to quoteElapsedMs,
                "wallet_api_calls" to 0,
                "order_to_fill_ms" to orderElapsedMs,
                "symbol_total_ms" to (System.currentTimeMillis() - symbolStartedAtMs).coerceAtLeast(0L),
                "notification_before_order" to false,
                "fresh_quote_preserved" to true,
                "broker_funds_verdict_deferred_to_order" to true,
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
        registerBoughtPositionAndProtect(
            ManagedPositionEntity(
                engine = ENGINE_FIXED_HOLDING,
                symbol = state.symbol,
                product = "CNC",
                side = "LONG",
                quantity = state.quantity,
                entryPrice = actualEntry,
                stopPrice = HardStopLossPolicy.stopPrice(actualEntry),
                targetPrice = executionTarget,
                strategy = "Fixed ${FixedEngineSnapshotStore.SNAPSHOT_ID} legacy GTT migrated · actual entry ₹${fmt(actualEntry)} · execution target ₹${fmt(executionTarget)} · ${row?.strategy.orEmpty()} · hard -5% floor · v7 stepped trail 0.55→0.50 then +0.10%",
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
        val nowMs = System.currentTimeMillis()
        val rows = learningDao.pendingLongForecastNotifications(today)
        rows.forEach { row ->
            learningDao.markLongForecastNotified(row.id)
            if (!ForecastNotificationFreshnessPolicy.fresh(row.generatedAtMs, nowMs))
                auditLogger.logAsync("FORECAST", "STALE_NOTIFICATION_RESEARCH_ONLY", mapOf(
                    "symbol" to row.symbol, "generated_at_ms" to row.generatedAtMs,
                    "age_ms" to (nowMs - row.generatedAtMs), "orders_sent" to false))
        }
        return rows.filter {
            RecommendationPriceBandPolicy.eligible(it.entryPrice) &&
                ForecastNotificationFreshnessPolicy.fresh(it.generatedAtMs, nowMs)
        }.map { it.toForecastDto() }
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
        // During closed hours, repeated LTP requests cannot reconstruct the full intraday
        // move. They previously generated 131 Fixed-priority errors after 15:30.
        // Close the displayed session once, without fabricating a winning/losing label.
        // Full-session research outcomes are recovered from mature candles off-hours.
        val regularSession = now.dayOfWeek.value < 6 &&
            !now.toLocalTime().isBefore(LocalTime.of(9, 15)) &&
            now.toLocalTime().isBefore(LocalTime.of(15, 30))
        if (!regularSession) {
            val unfinished = rows.filter { it.status == "ACTIVE" }
            unfinished.forEach { learningDao.updateLongForecast(it.copy(status = "SESSION_ENDED")) }
            if (unfinished.isNotEmpty()) {
                auditLogger.logAsync("FORECAST_RESEARCH", "SESSION_CLOSED_AWAITING_OUTCOME_REPLAY", mapOf(
                    "date" to today,
                    "closed_without_claiming_target_miss" to unfinished.size,
                    "intraday_history_required" to true,
                    "live_ltp_requests_sent" to 0,
                    "forecast_trading_eligibility_changed" to false
                ))
            }
            return 0
        }
        // Outcomes are Forecast research, not order execution. Use Beta if configured;
        // an unauthenticated Beta must never silently borrow Alpha's request budget.
        val token = forecastMarketToken() ?: return rows.count { it.status == "ACTIVE" }

        val ltps = fetchForecastOutcomeLtps(token, rows.map { it.symbol })
        if (ltps.isEmpty()) return rows.count { it.status == "ACTIVE" }
        preferences.recordMarketData()

        for (row in rows) {
            val ltp = ltps[row.symbol.uppercase(Locale.US)] ?: continue
            researchOutcomeChronologyJournal.observe("FORECAST", row.symbol.uppercase(Locale.US),
                row.generatedAtMs, row.entryPrice, row.targetPrice, ltp, nowMs, "BULK_LIVE_LTP")
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
        fixedHistoricalEvidenceArchive.capture(rows)
        // No research-only candidates: monitor official Fixed published stocks only.
        if (rows.isEmpty()) return 0

        val open = marketSession() == "OPEN"
        if (!open) {
            // Do not hallucinate intraday target hits from a stale post-close quote.
            rows.filter { it.status == "ACTIVE" }.forEach { row ->
                fixedRecommendationStore.update(row.copy(status = "SESSION_ENDED"))
            }
            fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            fixedHistoricalEvidenceArchive.capture(fixedRecommendationStore.recommendations(today))
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
        if (symbols.isEmpty()) return 0
        // Groww accepts at most 50 NSE instruments in a single LTP call.
        // Every chunk goes through the existing Alpha Live Data governor.
        val fetched = linkedMapOf<String, Double>()
        val batches = symbols.chunked(50)
        for ((batchIndex, chunk) in batches.withIndex()) {
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight) break
            if (buyApiCooldownOrNull(GrowwApiUsageLedger.Category.LIVE_DATA) != null) break
            val prices = runCatching {
                withFixedLiveDataSlot {
                    apiFactory.groww.ltp(
                        bearer(token), exchangeSymbols = chunk.joinToString(",") { "NSE_$it" }
                    ).requirePayload("Fixed outcome live LTP batch")
                }
            }.onFailure {
                auditLogger.log("FIXED_OUTCOME", "LIVE_SAMPLE_ERROR", mapOf(
                    "batch_index" to batchIndex, "batch_size" to chunk.size,
                    "total_symbols" to symbols.size,
                    "http_code" to (it as? HttpException)?.code(),
                    "message" to (it.message ?: it.javaClass.simpleName).take(160)
                ))
            }.getOrNull() ?: continue
            fetched.putAll(prices)
        }
        auditLogger.logAsync("FIXED_OUTCOME", "BATCH_MONITOR_PROFILE", mapOf(
            "distinct_symbols" to symbols.size, "batch_count" to batches.size,
            "maximum_per_request" to 50, "prices_received" to fetched.size,
            "extra_wallet_api_calls" to 0
        ))
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
            if (observed > 0) fixedOutcomePerformanceStore.record(fixedRecommendationStore.recommendations(today))
            fixedHistoricalEvidenceArchive.capture(fixedRecommendationStore.recommendations(today))
            _fixedDeliveryEvents.tryEmit(observedAt) // Repaint Fixed badges and Averages promptly.
        }
        return fixedRecommendationStore.recommendations(today).count { it.status == "ACTIVE" }
    }

    private fun updateFixedObservedOutcome(
        row: FixedRecommendationRecord, price: Double, atMs: Long, source: String
    ) {
        if (atMs <= row.lastObservedAtMs || atMs <= row.generatedAtMs) return
        researchOutcomeChronologyJournal.observe("FIXED", row.symbol.uppercase(Locale.US),
            row.generatedAtMs, row.entryPrice, row.targetPrice, price, atMs, source)
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

    private fun researchCandleTimestampMs(value: com.google.gson.JsonElement): Long? =
        com.multify.traderpro.engine.MarketCandleTimestampParser.toEpochMillis(value)

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
            // Forecast-only research: decode Groww 1m OHLC using the same robust
            // epoch/ISO timestamp rules already used by live Forecast candle aggregation.
            val decoded = com.multify.traderpro.engine.ForecastReplayCandleDecoder.decode(historical.candles)
            val candles = decoded.bars.map { bar ->
                ResearchCandle(bar.atMs, bar.high, bar.low, bar.close, bar.open, bar.volume)
            }
            auditLogger.log("FORECAST_RESEARCH", "OUTCOME_REPLAY_DECODE_PROFILE", mapOf(
                "date" to date, "symbol" to symbol,
                "source_rows" to decoded.sourceRows,
                "decoded_rows" to candles.size,
                "bad_timestamps" to decoded.badTimestampRows,
                "bad_ohlc" to decoded.badOhlcRows,
                "short_rows" to decoded.shortRows,
                "first_timestamp_shape" to decoded.firstTimestampShape,
                "research_only" to true
            ))
            if (candles.isEmpty()) {
                auditLogger.log("FORECAST_RESEARCH", "OUTCOME_REPLAY_NO_CANDLES", mapOf(
                    "date" to date, "symbol" to symbol,
                    "historical_payload_rows" to decoded.sourceRows,
                    "bad_timestamps" to decoded.badTimestampRows,
                    "bad_ohlc" to decoded.badOhlcRows,
                    "short_rows" to decoded.shortRows,
                    "first_timestamp_shape" to decoded.firstTimestampShape,
                    "outcome_written" to false,
                    "five_session_unlock_affected" to true
                ))
            }
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
                val chronological = ResearchCandleFirstTouchPolicy.evaluate(
                    row.scanAtMs, row.entryPrice,
                    row.entryPrice * (1.0 + row.targetPct / 100.0),
                    decoded.bars, sessionEndMs
                )
                val enriched = runCatching { JsonParser.parseString(row.featureJson).asJsonObject }
                    .getOrElse { com.google.gson.JsonObject() }
                enriched.add("entry_timing_v1", researchGson.toJsonTree(study))
                enriched.add("v802_chronological_first_touch", researchGson.toJsonTree(chronological))
                auditLogger.logAsync("FORECAST_RESEARCH", "CHRONOLOGICAL_OUTCOME_REPLAY",
                    mapOf("symbol" to symbol, "date" to date,
                        "first_touch" to chronological.firstTouch,
                        "first_touch_at_ms" to chronological.firstTouchAtMs,
                        "complete_sequence" to chronological.completeSequence,
                        "research_only_not_realized_pnl" to true))
                enriched.addProperty("entry_timing_model_applied_live", false)
                learningDao.updateForecastResearch(
                    row.copy(
                        featureJson = researchGson.toJson(enriched),
                        outcomeMfePct = mfe,
                        outcomeMaePct = mae,
                        outcomeClosePct = closePct,
                        // Do not label a historical Forecast success when the -2% research stop
                        // was observed first or the one-minute sequence is ambiguous.
                        outcomeTargetHit = chronological.firstTouch == "TARGET_FIRST_1M",
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
        // Alpha availability must not block independent, read-only Beta replay.
        val alphaToken = runCatching { ensureToken() }.getOrNull()
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
                // The DAO sorts dates newest first; always choosing first starved older
                // sessions and prevented the five-distinct-day calibration cohort.
                // Rotate eligible completed market dates, at most one batch per date.
                val incompleteDates = learningDao.forecastResearchDates()
                    .filter { it >= V7_RESEARCH_COHORT_START_DATE }
                    .filter { it < today || java.time.LocalTime.now(INDIA) >= LocalTime.of(15, 35) }
                    .filter { learningDao.pendingForecastResearchOutcomes(it).isNotEmpty() }
                    .sorted()
                if (incompleteDates.isEmpty()) break
                val incompleteDate = incompleteDates[forecastBackfillDateCursor % incompleteDates.size]
                forecastBackfillDateCursor = (forecastBackfillDateCursor + 1) % incompleteDates.size
                val pendingBefore = learningDao.pendingForecastResearchOutcomes(incompleteDate).size
                val updated = runCatching { backfillForecastResearchOutcomes(incompleteDate, token) }.getOrDefault(0)
                researchUpdated += updated
                researchBatches++
                val pendingAfter = learningDao.pendingForecastResearchOutcomes(incompleteDate).size
                if (updated <= 0 || pendingAfter >= pendingBefore) {
                    auditLogger.logAsync("FORECAST_RESEARCH", "OUTCOME_REPLAY_DATE_NO_PROGRESS", mapOf(
                        "date" to incompleteDate, "pending" to pendingAfter,
                        "next_date_rotates" to true, "no_fabricated_outcomes" to true
                    ))
                    // Continue to another date: an unavailable symbol/date cannot starve all research.
                }
            }
        }
        // Complete previously captured Univest exits from contemporaneous Beta candles
        // only after trading. The candle result is explicitly an estimate, not a fill.
        if (token != null && !marketCriticalTradeInFlight) {
            runCatching { recoverUnivestExitPrices(token) }
                .onFailure { auditLogger.logAsync("FORECAST_UNIVEST", "EXIT_REPLAY_ERROR", mapOf(
                    "error" to it.javaClass.simpleName
                )) }
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
        // Converts official same-day winners into entry-factor learning only after
        // the order path and market session are over; no new network requests.
        runCatching { materializeOfficialMultifyWinnerFeatures() }
            .onFailure { auditLogger.logAsync("MULTIFY_RESEARCH", "OFFICIAL_FACTOR_REPLAY_ERROR",
                mapOf("error" to it.javaClass.simpleName)) }
        val universalEvidenceUpdated = if (alphaToken != null) {
            runCatching { refreshUniversalPositiveEvidence(alphaToken) }
                .onFailure {
                    auditLogger.log("FORECAST_REPLAY", "UNIVERSAL_EVIDENCE_REFRESH_ERROR", mapOf(
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
                .getOrDefault(0)
        } else 0
        // Reconcile ALL mature days, not only today's outcomes. v7.9.25 already
        // rotates replay across historical dates; those completed sessions must count.
        // A mere captured research scan never advances calibration.
        val qualifiedDates = ForecastCalibrationCompletionPolicy.qualifyingDates(
            rows = learningDao.allForecastResearch().map { row ->
                ForecastCalibrationCompletionPolicy.CompletedOutcome(
                    date = row.researchDate,
                    symbol = row.symbol,
                    updatedAtMs = row.outcomeUpdatedAtMs,
                    mfePct = row.outcomeMfePct,
                    maePct = row.outcomeMaePct,
                    closePct = row.outcomeClosePct
                )
            },
            today = today,
            todayFullSessionMature = ZonedDateTime.now(INDIA).toLocalTime() >= LocalTime.of(15, 35),
            minimumDistinctSymbols = FORECAST_CALIBRATION_MIN_COMPLETED_ROWS
        )
        val completedBefore = forecastCalibrationDates()
        qualifiedDates.minus(completedBefore).sorted().forEach(::markForecastCalibrationSessionComplete)
        auditLogger.log("FORECAST_RESEARCH", "CALIBRATION_HISTORICAL_DATES_RECONCILED", mapOf(
            "qualified_dates" to qualifiedDates.sorted(),
            "newly_completed_sessions" to (qualifiedDates - completedBefore).size,
            "completed_sessions_after" to forecastCalibrationDates().size,
            "goal" to MarketPriorityPolicy.LIVE_FORECAST_UNLOCK_SESSIONS,
            "captured_only_not_counted" to true,
            "synthetic_outcomes_added" to false
        ))

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

    private suspend fun recordExternalClose(
        position: ManagedPositionEntity, quantity: Int, exit: Double, reason: String,
        confirmedChildOrderId: String? = null
    ) {
        val gross = (exit - position.entryPrice) * quantity
        val costs = estimatedCashCosts(position.entryPrice, exit, quantity)
        managedDao.insertTrade(
            ManagedTradeEntity(
                engine = position.engine, symbol = position.symbol, product = "CNC", side = "LONG",
                quantity = quantity, entryPrice = position.entryPrice, exitPrice = exit,
                grossPnl = gross, estimatedCosts = costs, netPnl = gross - costs, exitReason = reason,
                strategy = position.strategy, regime = position.regime, confidence = position.confidence,
                mfeRupees = max(0.0, position.maxFavourablePrice - position.entryPrice) * quantity,
                maeRupees = max(0.0, position.entryPrice - position.maxAdversePrice) * quantity,
                sourceEventId = position.sourceEventId, openOrderId = position.openOrderId, closeOrderId = confirmedChildOrderId ?: position.smartOrderId ?: "EXTERNAL",
                openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
            )
        )
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

    /**
     * Normal SELL scope requires broker inventory to equal the one app-owned BUY.
     * A just-filled Fixed Auto Buy may instead use its verified empty pre-trade snapshot
     * briefly while Groww's post-fill holdings/positions view catches up.
     */
    private suspend fun safeAppSellScope(
        token: String, position: ManagedPositionEntity,
        inventory: BrokerInventorySnapshot? = null,
        forceGttRefresh: Boolean = false,
        knownActiveGtts: Map<String, List<SmartOrderPayload>>? = null,
        allowFreshPostFillProof: Boolean = false
    ): Boolean {
        if (!appOwned(position)) return false
        val openSameSymbol = managedDao.openPositions().filter { it.symbol.equals(position.symbol, true) }
        if (openSameSymbol.size != 1 || openSameSymbol.single().id != position.id) return false
        // The Fixed Auto Buy caller can supply this proof only after placeCncBuy has
        // just verified zero pre-existing broker quantity for this symbol and received
        // a confirmed app BUY fill. Groww's holdings/positions endpoints may lag that fill.
        val key = position.symbol.uppercase(Locale.US)
        val freshPostFillProof = inventory == null && allowFreshPostFillProof &&
            AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(
                position.openedAtMs, System.currentTimeMillis())
        if (!freshPostFillProof) {
            val broker = inventory ?: brokerInventory(token, force = true)
            val demat = broker.holdings[key]?.quantity ?: 0
            val cnc = broker.positions[key]?.quantity ?: 0
            if (!AppPositionOwnershipPolicy.exclusiveBrokerQuantity(position.quantity, demat, cnc)) return false
        }
        val activeGtts = knownActiveGtts ?: activeGttsBySymbol(token, force = forceGttRefresh)
        val otherActiveGtt = activeGtts.getOrDefault(key, emptyList())
            .any { it.smartOrderId != position.smartOrderId }
        return !otherActiveGtt
    }

    private suspend fun portfolioMonitorCount(inventory: BrokerInventorySnapshot? = brokerInventorySnapshot): Int {
        // Broker holdings NOT opened by this app must never count as managed holdings.
        return managedDao.openPositions().count(::appOwned).also {
            activePortfolioMonitorCount = it
            MarketDataPriorityAdmissionPolicy.updateManagedHoldings(it)
        }
    }

    suspend fun monitorManagedPositions(): Int {
        val token = ensureToken() ?: return portfolioMonitorCount()
        val marketOpen = marketSession() == "OPEN"
        val now = System.currentTimeMillis()
        val managedOpen = managedDao.openPositions().any(::appOwned)
        // Refresh this immediately so quote admission reserves SELL capacity
        // before the first portfolio market-data request of each monitoring pass.
        MarketDataPriorityAdmissionPolicy.updateManagedHoldings(if (managedOpen) 1 else 0)
        val nonTradingCooldownUntil = recoveryGate.status().categoryCooldownUntilMs[GrowwApiUsageLedger.Category.NON_TRADING] ?: 0L
        if (nonTradingCooldownUntil > now) {
            val cooldownAuditKey = "__NON_TRADING_COOLDOWN__"
            val lastCooldownAudit = lastPortfolioConflictAuditAtMs[cooldownAuditKey] ?: 0L
            if (now - lastCooldownAudit >= 60_000L) {
                lastPortfolioConflictAuditAtMs[cooldownAuditKey] = now
                auditLogger.logAsync("PORTFOLIO_MONITOR", "NON_TRADING_COOLDOWN_DEFERRED", mapOf(
                    "retry_after_ms" to (nonTradingCooldownUntil - now).coerceAtLeast(0L),
                    "tracked_positions" to managedDao.openPositions().count(::appOwned),
                    "broker_requests_suppressed" to true,
                    "existing_server_gtt_left_untouched" to true,
                    "no_direct_sell_submitted" to true,
                    "fixed_priority_preserved" to true
                ))
            }
            // brokerInventory() would be locally rejected until this cooldown expires.
            // The broker-side GTT remains the active protection; do not spam retries or logs.
            return portfolioMonitorCount()
        }
        val confirmedEmpty = brokerInventorySnapshot?.takeIf { now - it.atMs < 5L * 60_000L }
            ?.let { it.holdings.isEmpty() && it.positions.isEmpty() } == true &&
            activeGttSnapshot?.takeIf { now - it.atMs < 5L * 60_000L }
                ?.bySymbol?.values?.all { it.isEmpty() } == true
        // A verified empty portfolio needs a slow inventory heartbeat, not fresh holdings,
        // positions and GTT calls every five seconds. Never apply this to any tracked
        // position, pending BUY/GTT, unknown broker state or order in flight.
        val noTrackedPositions = !managedOpen && managedDao.openPositions().isEmpty()
        val pendingEntry = fixedAutoBuyOrderStore.all().any { !ForecastAutoBuyPolicy.isTerminal(it.status) } ||
            forecastAutoBuyOrderStore.all().any { !ForecastAutoBuyPolicy.isTerminal(it.status) }
        // An external/manual holding or GTT must never become app-owned exposure.
        // Once both snapshots were obtained, skip repeat *idle* polls even when
        // they contain somebody else's positions/GTTs. The per-BUY guard independently
        // refreshes broker holdings and active GTTs; app-owned rows or unresolved
        // entries always retain the original fast SELL-monitor cadence.
        if (IdleBrokerPollingPolicy.maySkip(
            marketOpen = marketOpen,
            localOpenRows = if (noTrackedPositions) 0 else 1,
            pendingEntry = pendingEntry,
            criticalTradeInFlight = marketCriticalTradeInFlight,
            inventoryObservedAtMs = brokerInventorySnapshot?.atMs,
            gttObservedAtMs = activeGttSnapshot?.atMs,
            nowMs = now,
            maxRecheckIntervalMs = EMPTY_PORTFOLIO_BROKER_RECHECK_MS
        )) {
            val auditAt = lastIdlePortfolioAuditAtMs
            if (now - auditAt >= 60_000L) {
                lastIdlePortfolioAuditAtMs = now
                auditLogger.logAsync("PORTFOLIO_MONITOR", "VERIFIED_EMPTY_BROKER_POLL_SKIPPED", mapOf(
                    "next_reverify_after_ms" to EMPTY_PORTFOLIO_BROKER_RECHECK_MS,
                    "app_owned_holdings" to 0,
                    "pending_entries" to 0,
                    "external_broker_inventory_may_be_nonempty" to true,
                    "no_app_owned_or_unresolved_rows" to true,
                    "per_buy_fresh_inventory_check_preserved" to true,
                    "protective_sell_priority_preserved" to true
                ))
            }
            return portfolioMonitorCount()
        }
        val reconcileGap = if (!marketOpen && !managedOpen && confirmedEmpty) 5L * 60_000L
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
        val activeGttsResult = runCatching { activeGttsBySymbol(token) }
        if (activeGttsResult.isSuccess) {
            val activeGtts = activeGttsResult.getOrThrow()
            val open = managedDao.openPositions()
            for (observedPosition in open) {
                var position = observedPosition
                val symbol = position.symbol.uppercase(Locale.US)

                // A prior app GTT may be active even if an older monitor snapshot lost its ID.
                // Recover its exact app reference before treating it as an external/manual conflict.
                if (position.smartOrderId.isNullOrBlank()) {
                    val recovered = com.multify.traderpro.engine.AppProtectionGttRecoveryPolicy.recover(
                        position.id, position.symbol, position.openedAtMs, position.quantity,
                        activeGtts[symbol].orEmpty()
                    )
                    if (recovered != null) {
                        val recoveredMode = recovered.mode
                        val recoveredAtMs = System.currentTimeMillis()
                        position = position.copy(
                            smartOrderId = recovered.payload.smartOrderId,
                            smartOrderMode = recoveredMode,
                            smartOrderTriggerPrice = recovered.triggerPrice,
                            smartOrderDirection = recovered.direction,
                            smartOrderStatus = recovered.payload.status.ifBlank { "ACTIVE" },
                            smartOrderUpdatedAtMs = recoveredAtMs,
                            stopPrice = if (recoveredMode in setOf(
                                HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
                                HoldingSmartOrderPolicy.MODE_TRAILING_GTT
                            )) recovered.triggerPrice else position.stopPrice,
                            targetPrice = if (recoveredMode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
                                recovered.triggerPrice else position.targetPrice,
                            lastEvaluatedAtMs = recoveredAtMs
                        )
                        managedDao.updatePosition(position)
                        auditLogger.log("GTT", "APP_PROTECTION_GTT_RECOVERED_DURING_MONITOR", mapOf(
                            "symbol" to symbol,
                            "position_id" to position.id,
                            "gtt_id" to recovered.payload.smartOrderId,
                            "reference_id" to recovered.payload.referenceId,
                            "mode" to recoveredMode,
                            "trigger" to recovered.triggerPrice,
                            "direction" to recovered.direction,
                            "status" to position.smartOrderStatus,
                            "duplicate_creation_blocked" to true
                        ))
                    }
                }

                val brokerQty = max(
                    inventory.holdings[symbol]?.quantity ?: 0,
                    inventory.positions[symbol]?.quantity ?: 0
                )
                val gttStatus = position.smartOrderStatus.orEmpty().uppercase(Locale.US)
                // Broker child fills must be reconciled before ordinary ownership checks:
                // a successful GTT SELL legitimately reduces the broker's share count to zero.
                // No direct SELL may fire while a GTT child could still be pending.
                if (GttExitReconciliationPolicy.shouldQuarantinePendingChild(
                    position.smartOrderMode, position.smartOrderId,
                    gttStatus, brokerQty, position.quantity
                )) {
                    blockedSymbols += symbol
                    runCatching { reconcileTriggeredGttExit(token, position, brokerQty) }
                        .onFailure { error ->
                            auditLogger.log("GTT", "BROKER_CHILD_RECONCILIATION_ERROR", mapOf(
                                "symbol" to symbol,
                                "message" to (error.message ?: error.javaClass.simpleName),
                                "duplicate_sell_allowed" to false
                            ))
                        }
                    continue
                }
                val conflict = activeGtts[symbol].orEmpty().any { it.smartOrderId != position.smartOrderId }
                val exclusive = safeAppSellScope(token, position, inventory, knownActiveGtts = activeGtts)
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
                            val manualAuditKey = "GTT_MANUAL_REVIEW:$symbol"
                            val lastManualAudit = lastPortfolioConflictAuditAtMs[manualAuditKey] ?: 0L
                            if (now - lastManualAudit >= 60_000L) {
                                lastPortfolioConflictAuditAtMs[manualAuditKey] = now
                                auditLogger.logAsync("SAFETY", "GTT_OWNERSHIP_CONFLICT_MANUAL_REVIEW", mapOf(
                                    "symbol" to symbol, "gtt_id" to knownId,
                                    "audit_throttled_to_once_per_minute" to true
                                ))
                            }
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

        val marked = reconciled.map { observed ->
            val ltp = marks[observed.symbol.uppercase(Locale.US)]
            if (ltp != null && ltp.isFinite() && ltp > 0.0 && abs(ltp - observed.lastPrice) > 1e-9) {
                val markAtMs = System.currentTimeMillis()
                // Update only price/time columns; never clobber a newer GTT ID or status.
                managedDao.updatePriceObservation(observed.id, ltp, markAtMs)
            }
            managedDao.openPositionById(observed.id) ?: observed
        }

        if (!marketOpen) {
            // Keep every managed LONG protected overnight. Existing profitable trailing GTTs are
            // preserved; an unarmed holding receives the common -5% server-side hard stop. This
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
                    exchangeSymbols = exchangeSymbols,
                    trafficPriority = MarketDataPriorityAdmissionPolicy.PROTECTION
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

    /**
     * Executed immediately after every confirmed direct or reconciled BUY fill, regardless
     * of recommendation source. A local stop alone is NOT server-side protection.
     * An unverified GTT is retained for reconciliation, never duplicated.
     */
    private suspend fun registerBoughtPositionAndProtect(
        position: ManagedPositionEntity,
        allowFreshPostFillProof: Boolean = false,
        deferProtectionRetryToMonitor: Boolean = false
    ): Long {
        val lifecycleMutex = positionLifecycleMutexes.computeIfAbsent(position.symbol.uppercase(Locale.US)) { Mutex() }
        return lifecycleMutex.withLock {
        val id = managedDao.insertPosition(position)
        brokerInventorySnapshot = null
        activeGttSnapshot = null
        auditLogger.log("PROTECTION", "POST_FILL_PROTECTION_STARTED", mapOf(
            "symbol" to position.symbol, "source" to position.engine,
            "quantity" to position.quantity, "entry" to position.entryPrice,
            "hard_stop" to HardStopLossPolicy.stopPrice(position.entryPrice),
            "position_id" to id
        ))
        val token = runCatching { ensureToken() }.getOrNull()
        if (token == null) {
            reportCriticalUnprotected(position, id, "AUTH_UNAVAILABLE_AFTER_CONFIRMED_BUY")
            return@withLock id
        }
        var lastFailure = "BROKER_PROTECTION_NOT_VERIFIED"
        // Fixed Auto Buy makes one immediate protection attempt, then hands any
        // broker-visibility delay to the independent 1-second position-monitor worker.
        val maxImmediateProtectionAttempts = if (deferProtectionRetryToMonitor) 1 else 3
        for (attempt in 1..maxImmediateProtectionAttempts) {
            try {
                val current = managedDao.openPositionById(id) ?: return@withLock id
                val protected = if (!current.smartOrderId.isNullOrBlank()) {
                    refreshKnownSmartOrderStatus(token, current, force = true)
                } else {
                    createPositionGtt(
                        token, current, HardStopLossPolicy.stopPrice(current.entryPrice),
                        "DOWN", HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
                        allowFreshPostFillProof = allowFreshPostFillProof
                    ).let { refreshKnownSmartOrderStatus(token, it, force = true) }
                }
                if (com.multify.traderpro.engine.GttProtectionReadinessPolicy.verified(
                        protected.smartOrderMode, protected.smartOrderId, protected.smartOrderStatus)) {
                    auditLogger.log("PROTECTION", "POST_FILL_SERVER_STOP_VERIFIED", mapOf(
                        "symbol" to protected.symbol, "engine" to protected.engine,
                        "gtt_id" to protected.smartOrderId,
                        "trigger" to protected.smartOrderTriggerPrice,
                        "status" to protected.smartOrderStatus,
                        "attempt" to attempt
                    ))
                    return@withLock id
                }
                lastFailure = "UNVERIFIED_GTT_STATUS_${protected.smartOrderStatus}"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                lastFailure = (t.message ?: t.javaClass.simpleName).take(170)
                auditLogger.log("PROTECTION", "POST_FILL_GTT_ATTEMPT_FAILED", mapOf(
                    "symbol" to position.symbol, "engine" to position.engine,
                    "attempt" to attempt, "reason" to lastFailure,
                    "new_buy_allowed_before_protection" to false
                ))
            }
            if (attempt < maxImmediateProtectionAttempts) delay(700L * attempt)
        }
        if (deferProtectionRetryToMonitor) auditLogger.log("PROTECTION",
            "POST_FILL_PROTECTION_HANDED_TO_BACKGROUND_MONITOR", mapOf(
                "symbol" to position.symbol, "engine" to position.engine,
                "position_id" to id, "immediate_attempts" to maxImmediateProtectionAttempts,
                "portfolio_monitor_retry_interval_ms" to 1_000L
            ))
        reportCriticalUnprotected(position, id, lastFailure)
        id
        }
    }

    /** Inform the user about explicit broker funds rejection; never queries wallet or changes order flow. */
    private fun notifyInsufficientFundsIfConfirmed(symbol: String) {
        auditLogger.logAsync("BROKER_BUY", "INSUFFICIENT_FUNDS_REJECTED", mapOf(
            "symbol" to symbol, "source" to "BROKER_REJECTION_MESSAGE",
            "wallet_api_calls" to 0, "buy_was_not_placed" to true
        ))
        runCatching {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            val channel = "multify_funds_rejection"
            manager.createNotificationChannel(NotificationChannel(
                channel, "Groww order rejections", NotificationManager.IMPORTANCE_DEFAULT
            ))
            val alert = NotificationCompat.Builder(appContext, channel)
                .setSmallIcon(R.drawable.ic_stat_trader)
                .setContentTitle("BUY not placed · " + symbol)
                .setContentText("Groww rejected the order: insufficient available funds.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            manager.notify(520_000 + (symbol.hashCode() and 0x7fff), alert)
        }.onFailure {
            auditLogger.logAsync("BROKER_BUY", "FUNDS_ALERT_FAILED", mapOf(
                "symbol" to symbol, "type" to it.javaClass.simpleName))
        }
    }

    private fun reportCriticalUnprotected(position: ManagedPositionEntity, id: Long, reason: String) {
        tradingRecoveryReady = false
        auditLogger.log("SAFETY", "UNPROTECTED_CONFIRMED_BUY_CRITICAL", mapOf(
            "symbol" to position.symbol, "engine" to position.engine,
            "position_id" to id, "quantity" to position.quantity,
            "reason" to reason, "new_buys_blocked" to true,
            "fixed_auto_buy_distinct_symbols_may_continue" to true,
            "manual_broker_review_required" to true
        ))
        runCatching {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            val channelId = "multify_protection_critical"
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Unprotected trade alerts", NotificationManager.IMPORTANCE_HIGH
            ))
            val alert = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_trader)
                .setContentTitle("ACTION NEEDED: ${position.symbol} SELL stop unverified")
                .setContentText("BUY filled but broker GTT is not verified. Review Groww directly.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "A ${position.engine} BUY was confirmed, but its protective SELL GTT could not be verified. " +
                    "Do not rely on an in-app stop. Check Groww's holdings and active GTT orders. " +
                    "Fixed Auto Buy may continue for distinct eligible symbols while this holding is reconciled."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(510_000 + (id % 10_000L).toInt(), alert)
        }.onFailure {
            auditLogger.log("SAFETY", "UNPROTECTED_ALERT_DELIVERY_FAILED", mapOf(
                "symbol" to position.symbol, "type" to it.javaClass.simpleName))
        }
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
        working = working.copy(
            stopPrice = hardStop,
            smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE,
            smartOrderTriggerPrice = null,
            smartOrderDirection = null,
            smartOrderStatus = null,
            smartOrderUpdatedAtMs = System.currentTimeMillis(),
            lastEvaluatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(working)
        return createPositionGtt(token, working, hardStop, "DOWN", HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT)
    }

    private suspend fun monitorLongHolding(observedPosition: ManagedPositionEntity, token: String, ltp: Double) {
        val lifecycleMutex = positionLifecycleMutexes.computeIfAbsent(observedPosition.symbol.uppercase(Locale.US)) { Mutex() }
        lifecycleMutex.withLock {
            val current = managedDao.openPositionById(observedPosition.id) ?: return@withLock
            monitorLongHoldingLocked(current, token, ltp)
        }
    }

    private suspend fun monitorLongHoldingLocked(position: ManagedPositionEntity, token: String, ltp: Double) {
        // Common sell protection for every managed CNC LONG, regardless of source:
        // 1) hard -5% loss floor from the actual holding cost basis;
        // 2) once +0.55% high-water is reached, the existing stepped profitable trail takes over;
        // 3) the active stop only moves upward. There is no averaging-down path.
        val decisionStartedNs = SystemClock.elapsedRealtimeNanos()
        val previousHighWater = position.maxFavourablePrice
        val highWater = max(position.maxFavourablePrice, ltp)
        val lowWater = min(position.maxAdversePrice, ltp)
        val hardStopPrice = HardStopLossPolicy.stopPrice(position.entryPrice)
        val desiredTrailGainPct = SteppedTrailingPolicy.stopGainPctForHighWater(position.entryPrice, highWater)
        val desiredTrailPrice = desiredTrailGainPct?.let { gain ->
            // Add net-profit protection only when reached; never loosen the proven gross trail.
            NetGainTrailingPolicy.additionalNetGainProtection(
                position.entryPrice, position.quantity, highWater,
                SteppedTrailingPolicy.stopPrice(position.entryPrice, gain)
            )
        }
        val desiredMode = if (desiredTrailPrice != null) {
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT
        } else {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT
        }
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

        var working = position.copy(
            lastPrice = ltp,
            maxFavourablePrice = highWater,
            maxAdversePrice = lowWater,
            stopPrice = previousProtected ?: hardStopPrice,
            trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
            trailingLastRatchetPrice = desiredTrailGainPct?.let {
                position.entryPrice * (1.0 + (it + SteppedTrailingPolicy.TRIGGER_BUFFER_PCT) / 100.0)
            } ?: position.trailingLastRatchetPrice,
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
                else "LONG_TRAILING_PROFIT_STOP_V7_1_LOCAL_FALLBACK",
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
                            else "LONG_TRAILING_PROFIT_STOP_V7_1_STALE_GTT_FALLBACK",
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
                            else "LONG_TRAILING_PROFIT_STOP_V7_1_GTT_FAILED_FALLBACK",
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
                trailJustArmed -> "V7_1_TRAIL_ARMED"
                else -> "V7_1_STEP_RATCHET"
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
        mode: String,
        allowFreshPostFillProof: Boolean = false
    ): ManagedPositionEntity = protectiveGttCreationMutex.withLock {
        // Always use the latest Room row; callers may hold an older portfolio snapshot.
        val current = managedDao.openPositionById(position.id)
            ?: error("Managed position ${position.id} disappeared before GTT protection")
        if (!current.smartOrderId.isNullOrBlank()) return@withLock current
        require(current.side == "LONG" && current.product == "CNC") { "GTT protection is only for tracked CNC LONG holdings" }

        // Recover only an exact, active app-generated SELL GTT for this exact position event.
        // Symbol/quantity similarity alone is deliberately insufficient.
        val active = activeGttsBySymbol(token, force = true)
        val recovered = com.multify.traderpro.engine.AppProtectionGttRecoveryPolicy.recover(
            current.id, current.symbol, current.openedAtMs, current.quantity,
            active.getOrDefault(current.symbol.uppercase(Locale.US), emptyList())
        )
        if (recovered != null) {
            val now = System.currentTimeMillis()
            val recoveredMode = recovered.mode
            val updated = current.copy(
                smartOrderId = recovered.payload.smartOrderId,
                smartOrderMode = recoveredMode,
                smartOrderTriggerPrice = recovered.triggerPrice,
                smartOrderDirection = recovered.direction,
                smartOrderStatus = recovered.payload.status.ifBlank { "ACTIVE" },
                smartOrderUpdatedAtMs = now,
                stopPrice = if (recoveredMode in setOf(
                    HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
                    HoldingSmartOrderPolicy.MODE_TRAILING_GTT
                )) recovered.triggerPrice else current.stopPrice,
                targetPrice = if (recoveredMode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
                    recovered.triggerPrice else current.targetPrice,
                lastEvaluatedAtMs = now
            )
            managedDao.updatePosition(updated)
            auditLogger.log("GTT", "APP_PROTECTION_GTT_RECOVERED", mapOf(
                "symbol" to current.symbol,
                "position_id" to current.id,
                "gtt_id" to recovered.payload.smartOrderId,
                "reference_id" to recovered.payload.referenceId,
                "mode" to recoveredMode,
                "trigger" to recovered.triggerPrice,
                "direction" to recovered.direction,
                "status" to updated.smartOrderStatus,
                "duplicate_creation_blocked" to true,
                "other_active_gtt_count" to active.getOrDefault(current.symbol.uppercase(Locale.US), emptyList())
                    .count { it.smartOrderId != recovered.payload.smartOrderId }
            ))
            return@withLock updated
        }

        val freshPostFillProof = allowFreshPostFillProof &&
            AppPositionOwnershipPolicy.freshPostFillInventoryLagAllowed(
                current.openedAtMs, System.currentTimeMillis())
        require(safeAppSellScope(
            token, current, forceGttRefresh = false, knownActiveGtts = active,
            allowFreshPostFillProof = freshPostFillProof
        )) { "GTT SELL blocked: ${current.symbol} is not exclusively app-bought or another GTT exists" }
        if (freshPostFillProof) auditLogger.log("PROTECTION", "POST_FILL_INVENTORY_LAG_PROOF_USED", mapOf(
            "symbol" to current.symbol, "quantity" to current.quantity,
            "pre_buy_broker_inventory_verified_empty" to true,
            "same_symbol_app_position_exclusive" to true,
            "active_gtt_conflict_checked" to true,
            "broker_inventory_recheck_deferred" to true
        ))
        require(direction in setOf("UP", "DOWN"))
        require(mode in setOf(
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT,
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT,
            HoldingSmartOrderPolicy.MODE_RECOVERY_GTT
        ))
        require(current.smartOrderId.isNullOrBlank()) { "Refusing to create a second GTT for ${current.symbol}" }
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
        val reference = stableRef(referencePrefix, "${current.id}-${current.symbol}-${current.openedAtMs}")
        val postTriggerOrder = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT)
            GttOrder(orderType = "LIMIT", price = smartPrice(roundedTrigger), transactionType = "SELL")
        else
            GttOrder(orderType = "MARKET", price = null, transactionType = "SELL")
        val payload = try {
            apiFactory.groww.createGtt(
                bearer(token),
                GttCreateRequest(
                    referenceId = reference,
                    tradingSymbol = current.symbol,
                    quantity = current.quantity,
                    triggerPrice = smartPrice(roundedTrigger),
                    triggerDirection = direction,
                    order = postTriggerOrder
                )
            ).requirePayload("Create GTT ${current.symbol}")
        } catch (t: Throwable) {
            val rejected = t.message.orEmpty().contains("HTTP 400") ||
                BrokerExecutionSafetyPolicy.isInsufficientShares(t.message.orEmpty())
            val until = brokerRetryGuard.blockGtt(current.symbol, rejected)
            auditLogger.log("GTT", "GTT_CREATE_COOLDOWN_APPLIED", mapOf(
                "symbol" to current.symbol, "until_ms" to until,
                "reason" to (t.message ?: t.javaClass.simpleName)
            ))
            throw t
        }
        brokerRetryGuard.clearGtt(current.symbol)
        require(payload.smartOrderId.isNotBlank()) { "Groww did not return a GTT id for ${current.symbol}" }
        activeGttSnapshot = null
        val now = System.currentTimeMillis()
        val updated = current.copy(
            smartOrderId = payload.smartOrderId,
            smartOrderMode = mode,
            smartOrderTriggerPrice = roundedTrigger,
            smartOrderDirection = direction,
            smartOrderStatus = payload.status.ifBlank { "ACTIVE" },
            smartOrderUpdatedAtMs = now,
            stopPrice = if (mode in setOf(HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT, HoldingSmartOrderPolicy.MODE_TRAILING_GTT)) roundedTrigger else null,
            targetPrice = if (mode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT) roundedTrigger else current.targetPrice,
            lastEvaluatedAtMs = now
        )
        managedDao.updatePosition(updated)
        val createdEvent = when (mode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "HARD_STOP_GTT_CREATED"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "TRAIL_GTT_CREATED"
            else -> "RECOVERY_GTT_CREATED"
        }
        auditLogger.log("GTT", createdEvent, mapOf(
            "symbol" to current.symbol,
            "gtt_id" to payload.smartOrderId,
            "reference_id" to reference,
            "quantity" to current.quantity,
            "trigger" to roundedTrigger,
            "direction" to direction,
            "transaction" to "SELL",
            "product" to "CNC",
            "post_trigger_order_type" to postTriggerOrder.orderType,
            "post_trigger_limit_price" to postTriggerOrder.price,
            "mode" to mode,
            "status" to updated.smartOrderStatus
        ))
        return@withLock updated
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
        val now = System.currentTimeMillis()
        val updated = position.copy(
            smartOrderMode = mode,
            smartOrderTriggerPrice = roundedTrigger,
            smartOrderDirection = direction,
            smartOrderStatus = payload.status.ifBlank { position.smartOrderStatus ?: "ACTIVE" },
            smartOrderUpdatedAtMs = now,
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
        val updated = position.copy(
            smartOrderStatus = payload.status.ifBlank { position.smartOrderStatus },
            smartOrderUpdatedAtMs = now
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

    /**
     * Read-only broker order confirmation. A GTT TRIGGERED event is not a fill.
     * Never submits/cancels SELL, and never guesses that the trigger equals the fill.
     * Partial or ambiguous evidence is quarantined until broker status resolves.
     */
    private suspend fun reconcileTriggeredGttExit(
        token: String, position: ManagedPositionEntity, brokerQuantity: Int
    ): Boolean {
        if (!appOwned(position) || position.smartOrderId.isNullOrBlank()) return false
        val closeLock = closePositionMutexes.computeIfAbsent(position.id) { Mutex() }
        return closeLock.withLock {
            if (managedDao.openPositionById(position.id) == null) return@withLock false
            val now = System.currentTimeMillis()
            if (now - (gttExitReconcileLastAttemptAtMs[position.id] ?: 0L) < 8_000L)
                return@withLock false
            gttExitReconcileLastAttemptAtMs[position.id] = now

            val parent = apiFactory.groww.smartOrderStatus(
                bearer(token),
                segment = "CASH",
                smartOrderType = "GTT",
                smartOrderId = position.smartOrderId
            ).requirePayload("Groww triggered GTT parent status ${position.symbol}")
            val parentStatus = parent.status.uppercase(Locale.US)
            val confirmed = position.copy(
                smartOrderStatus = parent.status.ifBlank { position.smartOrderStatus },
                smartOrderUpdatedAtMs = now,
                smartOrderTriggerPrice = parent.triggerPrice?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                    ?: position.smartOrderTriggerPrice
            )
            managedDao.updatePosition(confirmed)
            if (parentStatus !in setOf("TRIGGERED", "COMPLETED")) {
                auditLogger.log("GTT", "EXIT_WAITING_PARENT_TRIGGER", mapOf(
                    "symbol" to position.symbol,
                    "parent_status" to parentStatus,
                    "broker_quantity" to brokerQuantity,
                    "duplicate_sell_allowed" to false
                ))
                return@withLock false
            }
            // Groww's GTT status can omit reference_id even when the child SELL executes.
            // Match the deterministic app reference when present; otherwise require a
            // uniquely executed time-linked broker SELL plus confirmed zero inventory.
            // This is read-only: never send a second SELL or infer fill from trigger alone.
            if (parent.tradingSymbol.isNotBlank() && !parent.tradingSymbol.equals(confirmed.symbol, true) ||
                parent.quantity > 0 && parent.quantity != confirmed.quantity ||
                parent.productType.isNotBlank() && !parent.productType.equals("CNC", true) ||
                parent.order != null && !parent.order.transactionType.equals("SELL", true)) {
                auditLogger.log("GTT", "PARENT_IDENTITY_UNVERIFIED_MANUAL_REVIEW", mapOf(
                    "symbol" to confirmed.symbol, "gtt_id" to confirmed.smartOrderId,
                    "broker_quantity" to brokerQuantity, "duplicate_sell_allowed" to false
                ))
                return@withLock false
            }
            if (brokerQuantity != 0) {
                auditLogger.log("GTT", "BROKER_FILL_PENDING_OR_PARTIAL_QUARANTINE", mapOf(
                    "symbol" to confirmed.symbol, "parent_status" to parentStatus,
                    "broker_quantity" to brokerQuantity, "tracked_quantity" to confirmed.quantity,
                    "duplicate_sell_allowed" to false
                ))
                return@withLock false
            }
            val brokerOrders = mutableListOf<GrowwOrderBookRow>()
            for (page in 0..2) {
                val pageRows = apiFactory.groww.orderList(bearer(token), page = page)
                    .requirePayload("Groww same-day SELL order book").orderList
                brokerOrders.addAll(pageRows)
                if (pageRows.size < 100) break
            }
            val match = GttExitReconciliationPolicy.resolveConfirmedTriggeredExit(
                orders = brokerOrders, symbol = confirmed.symbol, quantity = confirmed.quantity,
                openingOrderId = confirmed.openOrderId, positionId = confirmed.id,
                openedAtMs = confirmed.openedAtMs, parentReferenceId = parent.referenceId,
                parentTriggeredAt = parent.triggeredAt, brokerQuantity = brokerQuantity,
                observedAtMs = System.currentTimeMillis()
            )
            if (match == null) {
                auditLogger.log("GTT", "BROKER_FILL_UNVERIFIED_MANUAL_REVIEW", mapOf(
                    "symbol" to confirmed.symbol, "parent_status" to parentStatus,
                    "gtt_id" to confirmed.smartOrderId, "parent_reference_present" to !parent.referenceId.isNullOrBlank(),
                    "triggered_at_present" to !parent.triggeredAt.isNullOrBlank(),
                    "broker_quantity" to brokerQuantity, "tracked_quantity" to confirmed.quantity,
                    "same_symbol_sell_count" to brokerOrders.count {
                        it.tradingSymbol.equals(confirmed.symbol, true) &&
                            it.transactionType.equals("SELL", true) && it.product.equals("CNC", true)
                    },
                    "manual_review_if_persistent" to true, "duplicate_sell_allowed" to false
                ))
                return@withLock false
            }
            val full = match.order
            val price = full.averageFillPrice ?: return@withLock false
            val exitReason = when (confirmed.smartOrderMode) {
                HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "TRAILING_GTT_CONFIRMED_BROKER_FILL"
                HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "HARD_STOP_GTT_CONFIRMED_BROKER_FILL"
                else -> "GTT_CONFIRMED_BROKER_FILL"
            }
            recordExternalClose(
                confirmed, confirmed.quantity, price, exitReason,
                confirmedChildOrderId = full.growwOrderId
            )
            recentlyClosedSymbolsAtMs[confirmed.symbol.uppercase(Locale.US)] = now
            managedDao.deletePosition(confirmed.id)
            brokerInventorySnapshot = null
            gttExitReconcileLastAttemptAtMs.remove(confirmed.id)
            lastTrailStateAuditAtMs.remove(confirmed.id)
            lastSmartOrderStatusAtMs.remove(confirmed.id)
            auditLogger.log("HOLDING", "GTT_BROKER_SELL_RECONCILED", mapOf(
                "symbol" to confirmed.symbol,
                "parent_gtt_id" to confirmed.smartOrderId,
                "gtt_reference_id" to parent.referenceId,
                "sell_order_id" to full.growwOrderId,
                "broker_average_fill" to price,
                "quantity" to confirmed.quantity,
                "trigger_price" to confirmed.smartOrderTriggerPrice,
                "trigger_to_fill_slippage_bps" to confirmed.smartOrderTriggerPrice
                    ?.takeIf { it > 0.0 }?.let { (it - price) / it * 10_000.0 },
                "estimated_net_pnl" to GrowwDeliveryCostPolicy.estimatedNetProfit(
                    confirmed.entryPrice, price, confirmed.quantity
                ),
                "broker_order_match_note" to match.evidence,
                "duplicate_sell_sent" to false
            ))
            true
        }
    }

    private suspend fun finalizeCompletedGttIfNeeded(
        token: String,
        position: ManagedPositionEntity
    ): ManagedPositionEntity? {
        if (position.smartOrderId.isNullOrBlank()) return position
        val refreshed = runCatching { refreshKnownSmartOrderStatus(token, position, force = false) }
            .getOrDefault(position)
        if (refreshed.smartOrderStatus?.uppercase(Locale.US) in setOf("FAILED", "CANCELLED", "EXPIRED")) {
            val cleared = refreshed.copy(
                smartOrderId = null,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE,
                smartOrderTriggerPrice = null,
                smartOrderDirection = null,
                smartOrderStatus = null,
                smartOrderUpdatedAtMs = System.currentTimeMillis()
            )
            managedDao.updatePosition(cleared)
            auditLogger.log("GTT", "TERMINAL_GTT_RECONCILED_FOR_REPROTECTION", mapOf(
                "symbol" to cleared.symbol, "old_gtt_id" to refreshed.smartOrderId,
                "old_status" to refreshed.smartOrderStatus
            ))
            return cleared
        }
        // NEVER book a GTT trigger price as an actual broker fill.
        // The parent can be COMPLETED before its child fill is visible in the order book.
        // First-loop quarantine will reconcile when unique full broker SELL evidence exists.
        if (refreshed.smartOrderStatus.equals("COMPLETED", true)) return null
        return refreshed
    }

    private suspend fun cancelKnownPositionGtt(
        token: String,
        position: ManagedPositionEntity,
        reason: String,
        clearIfTerminal: Boolean
    ): ManagedPositionEntity {
        val id = position.smartOrderId ?: return position
        activeGttSnapshot = null
        val statusPayload = runCatching {
            apiFactory.groww.smartOrderStatus(
                bearer(token), segment = "CASH", smartOrderType = "GTT", smartOrderId = id
            ).requirePayload("GTT status before cancel ${position.symbol}")
        }.getOrNull()
        val status = statusPayload?.status?.uppercase(Locale.US)
        if (!clearIfTerminal && status in setOf("TRIGGERED", "COMPLETED")) {
            val waiting = position.copy(smartOrderStatus = status, smartOrderUpdatedAtMs = System.currentTimeMillis())
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
        val cleared = position.copy(
            smartOrderId = null,
            smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE,
            smartOrderTriggerPrice = null,
            smartOrderDirection = null,
            smartOrderStatus = null,
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
        val nextGain = SteppedTrailingPolicy.nextTriggerGainPct(protectedGainPct)
        auditLogger.log("TRAILING_STATE", "STOCK_TRAIL_STATE", mapOf(
            "symbol" to position.symbol,
            "engine" to position.engine,
            "entry" to position.entryPrice,
            "ltp" to ltp,
            "gain_pct" to gainPct,
            "high_water" to position.maxFavourablePrice,
            "high_water_gain_pct" to highGainPct,
            "arm_trigger_pct" to SteppedTrailingPolicy.ARM_TRIGGER_PCT,
            "armed" to (protectedGainPct != null),
            "desired_protected_gain_pct" to desiredStopGainPct,
            "active_stop_price" to position.stopPrice,
            "protected_gain_pct" to protectedGainPct,
            "next_ratchet_trigger_gain_pct" to nextGain,
            "next_ratchet_trigger_price" to (position.entryPrice * (1.0 + nextGain / 100.0)),
            "gtt_id" to position.smartOrderId,
            "gtt_mode" to position.smartOrderMode,
            "gtt_status" to position.smartOrderStatus,
            "gtt_trigger_price" to position.smartOrderTriggerPrice,
            "gtt_direction" to position.smartOrderDirection,
            "hard_stop_loss_pct" to -HardStopLossPolicy.MAX_LOSS_PCT,
            "hard_stop_price" to HardStopLossPolicy.stopPrice(position.entryPrice),
            "hard_stop_enabled" to true,
            "estimated_net_pnl_at_high_water" to GrowwDeliveryCostPolicy.estimatedNetProfit(
                position.entryPrice, position.maxFavourablePrice, position.quantity),
            "estimated_net_pnl_at_active_stop" to position.stopPrice?.let {
                GrowwDeliveryCostPolicy.estimatedNetProfit(position.entryPrice, it, position.quantity)
            },
            "net_half_percent_slippage_adjusted_stop" to NetGainTrailingPolicy.minimumNetTargetTrigger(
                position.entryPrice, position.quantity),
            "net_target_pct" to NetGainTrailingPolicy.MIN_NET_RETURN_PCT,
            "net_slippage_allowance_bps" to NetGainTrailingPolicy.DEFAULT_SLIPPAGE_ALLOWANCE_BPS,
            "net_floor_is_market_not_guarantee" to true,
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
        notificationStartedNs: Long? = null,
        buyLimitPrice: Double? = null
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
                    orderReferenceId = reference,
                    orderType = if (transaction == "BUY" && buyLimitPrice != null) "LIMIT" else "MARKET",
                    price = if (transaction == "BUY") (buyLimitPrice ?: 0.0) else 0.0
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
            val effectiveFilled = ExecutionFillQuantityPolicy.confirmedFilledQuantity(
                requestedQuantity = qty,
                reportedFilledQuantity = status.filledQuantity,
                terminalStatus = status.orderStatus
            ) ?: error("BROKER_FILLED_QUANTITY_UNVERIFIED: $symbol order $id; manual reconciliation required")
            if (product == "CNC" && transaction in setOf("BUY", "SELL") && !reference.startsWith("LGC")) {
                managedDao.insertExecutionSample(
                    ExecutionSampleEntity(
                        transactionType = transaction,
                        symbol = symbol,
                        quantity = effectiveFilled,
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
                "requested_quantity" to qty,
                "actual_filled_quantity" to effectiveFilled,
                "quantity_basis" to if (status.filledQuantity != null) "BROKER_REPORTED" else "EXECUTED_STATUS_FULL_ORDER",
                "partial_fill" to (effectiveFilled < qty),
                "total_execution_ms" to totalExecutionMs,
                "broker_ack_ms" to brokerAckLatencyMs
            ))
            return status.copy(growwOrderId = id, orderReferenceId = reference, filledQuantity = effectiveFilled)
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
        if (transaction == "BUY" && buyLimitPrice != null) {
            // Groww documents DAY validity, not IOC. Never leave a timed-out LIMIT BUY
            // open and unprotected. Cancel then reconcile before reporting any fill.
            val cancel = runCatching {
                apiFactory.groww.cancelOrder(bearer(token), OrderCancelRequest(growwOrderId = id))
                    .requirePayload("Cancel unfilled BUY $symbol")
            }.getOrNull()
            val settled = runCatching {
                apiFactory.groww.orderStatus(bearer(token), id)
                    .requirePayload("Reconcile cancelled BUY $symbol")
            }.getOrNull()
            val terminal = settled?.orderStatus?.uppercase(Locale.US) in
                setOf("CANCELLED", "REJECTED", "FAILED", "EXECUTED", "COMPLETED")
            if (terminal && (settled?.filledQuantity ?: 0) > 0) {
                auditLogger.log("EXECUTION", "PARTIAL_LIMIT_BUY_RECONCILED", mapOf(
                    "symbol" to symbol, "quantity" to settled?.filledQuantity,
                    "status" to settled?.orderStatus))
                return acceptFilled(settled!!)
            }
            if (terminal && (settled?.filledQuantity ?: 0) == 0) {
                error("LIMIT_BUY_UNFILLED_CANCELLED: $symbol")
            }
            auditLogger.log("SAFETY", "LIMIT_BUY_CANCEL_UNCONFIRMED", mapOf(
                "symbol" to symbol, "order_id" to id,
                "cancel_status" to (cancel?.orderStatus ?: "UNKNOWN"),
                "reconciled_status" to (settled?.orderStatus ?: "UNKNOWN"),
                "manual_broker_reconciliation_required" to true))
            error("LIMIT_BUY_UNRESOLVED: $symbol order $id; check broker before enabling new buys")
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
        finalizeMultifyPositivePattern(eventId, symbol)
    }

    /** Only a verified >=1% rise within 15 minutes enters positive Forecast training. */
    private suspend fun finalizeMultifyPositivePattern(eventId: Long, symbol: String) {
        val call = learningDao.liveCallForBuyEvent(eventId) ?: return
        val observations = learningDao.observationsForEventPhase(eventId, "POST_LONG_CALL")
            .filter { it.offsetSeconds in 1..900 && it.price.isFinite() && it.price > 0.0 }
        val peak = observations.maxOfOrNull { it.price } ?: return
        val mfePct = if (call.entryPrice > 0.0) (peak / call.entryPrice - 1.0) * 100.0 else 0.0
        val firstOnePct = observations.firstOrNull {
            call.entryPrice > 0.0 && (it.price / call.entryPrice - 1.0) * 100.0 >= 1.0
        }
        auditLogger.logAsync("MULTIFY_RESEARCH", "FIFTEEN_MINUTE_OUTCOME", mapOf(
            "event_id" to eventId, "symbol" to symbol, "mfe_pct" to mfePct,
            "hit_one_pct" to (firstOnePct != null), "first_hit_seconds" to firstOnePct?.offsetSeconds,
            "observations" to observations.size))
        if (firstOnePct == null) return // unsuccessful examples remain in the research record.
        val snapshot = learningDao.allForecastResearch().filter {
            it.multifyEventId == eventId && it.qualityTier == "MULTIFY_REFERENCE"
        }.minByOrNull { it.scanAtMs } ?: return
        val summary = evidenceFeatureSummary(snapshot.featureJson)
        learningDao.upsertForecastPositiveEvidence(ForecastPositiveEvidenceEntity(
            evidenceKey = "MULTIFY_FAST_1PCT|$eventId", source = "MULTIFY_FAST_15M",
            evidenceDate = snapshot.researchDate, eventAtMs = snapshot.scanAtMs,
            symbol = symbol.uppercase(Locale.US), strategy = snapshot.strategy,
            regime = snapshot.regime, marketRegime = snapshot.marketRegime,
            dominantFamily = summary.dominantFamily, familyConsensus = summary.familyConsensus,
            momentumFamilyScore = summary.momentum,
            participationFamilyScore = summary.participation,
            structureFamilyScore = summary.structure,
            factorJson = snapshot.featureJson, voteJson = snapshot.voteJson,
            entryPrice = call.entryPrice, targetPct = 1.0,
            maxFavourablePct = mfePct,
            maxAdversePct = observations.minOf { (it.price / call.entryPrice - 1.0) * 100.0 },
            targetHit = true, positiveReason = "Multify +1% within ${firstOnePct.offsetSeconds}s",
            reconstructionQuality = "LIVE_EVENT_PRE_SPIKE_SNAPSHOT",
            updatedAtMs = System.currentTimeMillis()
        ))
        auditLogger.logAsync("FORECAST_LEARNING", "MULTIFY_FAST_POSITIVE_CAPTURED", mapOf(
            "event_id" to eventId, "symbol" to symbol, "feature_json_retained" to true,
            "time_to_one_pct_seconds" to firstOnePct.offsetSeconds,
            "mfe_15m_pct" to mfePct))
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

        restoreStage = "Forecast news event journal"
        val restoredNews = entries["forecast_news_events.jsonl"]?.let { forecastNewsJournal.restore(it) } ?: 0
        restoreStage = "Univest same-day research journal"
        val restoredUnivest = entries["forecast_univest_same_day.jsonl"]?.let { forecastUnivestJournal.restore(it) } ?: 0
        // Older fixed_recommendations.json is imported into research ONLY, never into
        // FixedRecommendationStore, broker inventory, managed holdings, or order queues.
        val historicalFixedImported = entries["fixed_historical_research.json"]
            ?.let { fixedHistoricalEvidenceArchive.restoreHistoricalJson(it) } ?: 0
        val legacyFixedImported = entries["fixed_recommendations.json"]
            ?.let { fixedHistoricalEvidenceArchive.restorePriorRecommendations(it) } ?: 0
        auditLogger.logAsync("MIGRATION", "FIXED_READ_ONLY_HISTORY_IMPORTED", mapOf(
            "read_only_history_rows" to historicalFixedImported,
            "legacy_fixed_snapshot_rows" to legacyFixedImported,
            "trading_state_restored" to false, "auto_buy_eligible" to false,
            "historical_status_is_not_realized_pnl" to true))
        val restoredResearchChronology = entries["research_outcome_chronology.jsonl"]
            ?.let { researchOutcomeChronologyJournal.restore(it) } ?: 0
        if (restoredResearchChronology > 0) auditLogger.logAsync("FORECAST_RESEARCH",
            "CHRONOLOGY_RESTORED", mapOf("rows" to restoredResearchChronology,
                "broker_order_state_changed" to false))
        auditLogger.log("MIGRATION", "FORECAST_UNIVEST_RESTORED", mapOf("records" to restoredUnivest))
        restoreStage = "Decision evidence journal"
        val restoredDecisions = entries["decision_evidence.jsonl"]?.let {
            decisionEvidenceJournal.restoreArchived(it)
        } ?: 0
        auditLogger.log("MIGRATION", "DECISION_EVIDENCE_RESTORED", mapOf(
            "records" to restoredDecisions, "trading_state_restored" to false
        ))
        restoreStage = "Fixed fast candidate research journal"
        entries["fixed_fast_candidates.json"]?.let { json ->
            val imported = fixedFastCandidatePriceStore.restoreArchived(json)
            auditLogger.log("MIGRATION", "FIXED_FAST_CANDIDATE_RESEARCH_RESTORED", mapOf(
                "rows_imported" to imported, "trading_state_restored" to false))
        }
        restoreStage = "Fixed market-pulse research journal"
        entries["fixed_market_pulse_research.json"]?.let { pulseJson ->
            val imported = fixedMarketPulseResearchStore.restoreArchived(pulseJson)
            auditLogger.log("MIGRATION", "FIXED_PULSE_RESEARCH_RESTORED", mapOf(
                "research_rows_imported" to imported, "trading_state_restored" to false))
        }
        restoreStage = "Fixed first-PRIMARY research journal"
        val restoredEarlyWatch = entries["fixed_early_watch.json"]?.let {
            fixedEarlyWatchJournal.restoreArchived(it)
        } ?: 0
        auditLogger.log("MIGRATION", "FIXED_EARLY_WATCH_RESTORED", mapOf(
            "records" to restoredEarlyWatch, "trading_state_restored" to false
        ))
        restoreStage = "Historical audit events"
        val restoredAudit = entries["system_audit.jsonl"]?.let { auditLogger.restoreArchived(it) } ?: 0
        auditLogger.log("MIGRATION", "FORECAST_NEWS_AND_AUDIT_RESTORED", mapOf(
            "news_records" to restoredNews, "audit_rows" to restoredAudit))
        restoreStage = "Forecast research snapshots"
        entries["forecast_research_snapshots.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<ForecastResearchEntity>::class.java).orEmpty()
            val existingByKey = learningDao.allForecastResearch().associateBy {
                "${it.researchDate}|${it.scanAtMs}|${it.symbol.uppercase(Locale.US)}|${it.rank}"
            }.toMutableMap()
            // A single Room transaction per bounded batch, rather than >4,000 UI-bound inserts.
            // Re-running a partially completed restore only imports rows not yet present.
            val pending = ArrayList<ForecastResearchEntity>(256)
            imported.forEach { row ->
                if (row.researchDate.isBlank() || row.symbol.isBlank() ||
                    !row.entryPrice.isFinite() || row.entryPrice <= 0.0) return@forEach
                val symbol = row.symbol.uppercase(Locale.US)
                val key = "${row.researchDate}|${row.scanAtMs}|$symbol|${row.rank}"
                val local = existingByKey[key]
                if (local != null) {
                    // ZIP restore must never erase a completed historical outcome.
                    // It may enrich an unlabeled row or replace an older completed
                    // outcome with a demonstrably newer measured outcome.
                    if (row.outcomeUpdatedAtMs != null &&
                        (local.outcomeUpdatedAtMs == null ||
                            row.outcomeUpdatedAtMs > local.outcomeUpdatedAtMs)) {
                        val merged = local.copy(
                            outcomeMfePct = row.outcomeMfePct ?: local.outcomeMfePct,
                            outcomeMaePct = row.outcomeMaePct ?: local.outcomeMaePct,
                            outcomeClosePct = row.outcomeClosePct ?: local.outcomeClosePct,
                            outcomeTargetHit = row.outcomeTargetHit ?: local.outcomeTargetHit,
                            outcomeUpdatedAtMs = row.outcomeUpdatedAtMs
                        )
                        learningDao.updateForecastResearch(merged)
                        existingByKey[key] = merged
                        restoredResearch++
                    }
                    return@forEach
                }
                existingByKey[key] = row.copy(id = 0, symbol = symbol)
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
        // Import same-day recommendation identity EVEN DURING an open session.
        // Never create trading state. This prevents a reinstalled app from
        // re-notifying or buying a stock recommended by a previous installation.
        restoreStage = "Fixed same-day duplicate identity"
        entries["fixed_imported_same_day_dedup.json"]?.let { json ->
            val imported = fixedImportedSameDayDedupJournal.restore(json)
            auditLogger.log("MIGRATION", "FIXED_DUPLICATE_IDENTITIES_RESTORED",
                mapOf("prior_import_keys" to imported, "broker_trading_state_restored" to false))
        }
        entries["fixed_recommendations.json"]?.let { json ->
            val older = runCatching {
                gson.fromJson(json, Array<FixedRecommendationRecord>::class.java)?.toList().orEmpty()
            }.getOrDefault(emptyList())
            val imported = fixedImportedSameDayDedupJournal.markRestoredFromLegacy(
                older, LocalDate.now(INDIA).toString())
            auditLogger.log("MIGRATION", "FIXED_CURRENT_DAY_DUPLICATES_BLOCKED",
                mapOf("restored_prior_publications" to imported,
                    "auto_buy_allowed" to false, "broker_trading_state_restored" to false))
        }
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

    suspend fun exportCompleteLogs(
        uri: Uri,
        progress: ((String, Int) -> Unit)? = null
    ): LogExportResult = withContext(Dispatchers.IO) {
        // Serialization and SAF writes must not run on Android's main dispatcher.
        // This export does not change trading or Fixed publication.
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
        val fixedRecommendations = fixedRecommendationStore.archivedRecommendations()
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
        var fileCount = 0
        appContext.contentResolver.openOutputStream(uri, "w")?.use { raw ->
            ZipOutputStream(java.io.BufferedOutputStream(raw, 64 * 1024)).use { zip ->
                fun written(name: String) {
                    fileCount++
                    progress?.invoke(name, fileCount)
                }
                fun add(name: String, value: Any) {
                    zip.putNextEntry(ZipEntry(name))
                    // Stream JSON directly to the ZIP: no giant intermediate String.
                    val writer = java.io.BufferedWriter(
                        java.io.OutputStreamWriter(zip, Charsets.UTF_8), 32 * 1024)
                    gson.toJson(value, writer)
                    writer.flush() // Do not close the writer before the ZIP entry.
                    zip.closeEntry()
                    written(name)
                }
                fun addText(name: String, value: String) {
                    zip.putNextEntry(ZipEntry(name))
                    val writer = java.io.BufferedWriter(
                        java.io.OutputStreamWriter(zip, Charsets.UTF_8), 32 * 1024)
                    writer.write(value)
                    writer.flush()
                    zip.closeEntry()
                    written(name)
                }
                add("settings.json", safeSettings)
                add("signals.json", signals)
                add("holdings.json", positions)
                add("closed_long_trades.json", trades)
                add("execution_samples.json", executionSamples)
                add("signal_price_observations.json", priceObservations)
                add("forecast_research_snapshots.json", forecastResearch)
                add("forecast_positive_evidence.json", positiveEvidence)
                addText("forecast_news_events.jsonl", forecastNewsJournal.archive())
                addText("forecast_univest_same_day.jsonl", forecastUnivestJournal.archive())
                addText("research_outcome_chronology.jsonl", researchOutcomeChronologyJournal.archive())
                addText("forecast_univest_notification_capture.jsonl", forecastSourceNotificationJournal.archive())
                decisionEvidenceJournal.appendExportEntry(zip)
                written("decision_evidence.jsonl")
                add("long_learning.json", calls)
                add("long_forecasts.json", forecasts)
                add("long_champions.json", champions)
                add("research.json", research)
                add("forecast_auto_buy_orders.json", forecastAutoBuyOrders)
                add("fixed_recommendations.json", fixedRecommendations)
                // Include ALL recorded 5-day per-scan tier observations; without
                // this file offline studies cannot reconstruct confirmations.
                add("fixed_scan_observations.json", fixedRecommendationStore.archivedObservations())
                fixedHistoricalEvidenceArchive.capture(fixedRecommendations)
                addText("fixed_historical_research.json", fixedHistoricalEvidenceArchive.archive())
                addText("fixed_imported_same_day_dedup.json", fixedImportedSameDayDedupJournal.archive())
                addText("fixed_early_watch.json", fixedEarlyWatchJournal.archive())
                addText("fixed_market_pulse_research.json", fixedMarketPulseResearchStore.archive())
                addText("fixed_fast_candidates.json", fixedFastCandidatePriceStore.archive())
                add("fixed_5m_shadow_pairs.json", fixedFiveMinuteShadowJournal.all())
                add("fixed_5m_shadow_comparison_summary.json", mapOf(
                    "source" to "READ_ONLY_SAME_ENTRY_SAMPLED_LTP",
                    "session_bucket_open" to "09:15-10:15 IST",
                    "shared_target_same_price_path" to true,
                    "success_not_realised_profit" to true,
                    "intrabar_highs_unknown" to true,
                    "trading_15m_fixed_unchanged" to true,
                    "cohorts" to fixedFiveMinuteShadowJournal.cohortSummary(),
                    "pair_count" to fixedFiveMinuteShadowJournal.all().size
                ))
                add("fixed_auto_buy_orders.json", fixedAutoBuyOrders)
                fixedFactorTelemetry.appendExportEntry(zip)
                written("fixed_factor_snapshots.jsonl")
                fixedMarketPathTelemetry.appendExportEntry(zip)
                written("fixed_market_path.jsonl")
                add("fixed_sampled_maturity_and_repeat_research.json",
                    fixedMarketPathTelemetry.exportSampledOutcomeStudy(
                        fixedRecommendations, fixedRecommendationStore.archivedObservations()))
                add("api_usage_snapshot.json", GrowwApiUsageLedger.snapshot())
                add("groww_recovery_state.json", recoveryGate.status())
                add("telemetry_metadata.json", mapOf(
                    "exported_at_ms" to System.currentTimeMillis(),
                    "async_audit_dropped_total" to auditLogger.asyncDroppedCount(),
                    "fixed_factor_telemetry_dropped_total" to fixedFactorTelemetry.droppedCount(),
                    "fixed_factor_telemetry_written_total" to fixedFactorTelemetry.writtenCount(),
                    "fixed_factor_telemetry_nonblocking" to true,
                    "fixed_market_path_file" to "fixed_market_path.jsonl",
                    "rolling_five_minute_shadow_from_existing_sweeps" to true,
                    "rolling_five_minute_additional_broker_calls" to 0,
                    "five_minute_market_sweep_added" to false,
                    "original_session_baseline_modified" to false,
                    "opening_0915_0930_candle_research_only" to true,
                    "sampled_maturity_research_file" to "fixed_sampled_maturity_and_repeat_research.json",
                    "sampled_maturity_conservative_postscan_ms" to 90_000L,
                    "sampled_maturity_realized_trade_profit" to false,
                    "fixed_market_path_schema" to "fixed_market_path_v1",
                    "fixed_market_path_written_total" to fixedMarketPathTelemetry.writtenCount(),
                    "fixed_market_path_dropped_total" to fixedMarketPathTelemetry.droppedCount(),
                    "fixed_market_path_queue_pending" to fixedMarketPathTelemetry.queuedCount(),
                    "fixed_market_path_no_extra_broker_calls" to true,
                    "fixed_market_path_quote_exchange_timestamp_verified" to false,
                    "fixed_market_path_has_intraday_volume" to false,
                    "fixed_90s_schedule_target_not_guaranteed" to true,
                    "fixed_5m_10m_alternative_candles_captured" to false,
                    "fixed_5m_shadow_collection_active" to false,
                    "full_market_path_is_sampled_ltp_not_intrabar_high_low" to true,
                    "fixed_scan_observation_file" to "fixed_scan_observations.json",
                    "publication_candidate_timeline_event" to "ALL_CANDIDATE_CONFIRMATION_TIMELINE",
                    "fixed_factor_telemetry_file" to "fixed_factor_snapshots.jsonl",
                    "fixed_early_watch_file" to "fixed_early_watch.json",
                    "fixed_early_watch_read_only" to true,
                    "fixed_quality_shadow_no_buy_filter" to true,
                    "quote_http_timing_exchange_age_unknown" to true,
                    "fixed_peak_research_feed_enabled" to false,
                    "fixed_scan_profile_event" to "FIXED_SCAN_PROFILE",
                    "forecast_scan_profile_event" to "FORECAST_SCAN_PROFILE",
                    "trailing_profile_event" to "TRAILING_MONITOR_PROFILE",
                    "multify_pipeline_event" to "MULTIFY_PIPELINE_PROFILE",
                    "broker_rate_limit_event" to "HTTP_429",
                    "client_governor_wait_event" to "CLIENT_GOVERNOR_WAIT",
                    "forecast_replay_event" to "UNIVERSAL_POSITIVE_EVIDENCE_REFRESH",
                    "positive_evidence_file" to "forecast_positive_evidence.json",
                    "forecast_news_file" to "forecast_news_events.jsonl",
                    "forecast_univest_file" to "forecast_univest_same_day.jsonl",
                    "research_chronology_file" to "research_outcome_chronology.jsonl",
                    "fixed_historical_research_file" to "fixed_historical_research.json",
                    "fixed_imported_same_day_dedup_file" to "fixed_imported_same_day_dedup.json",
                    "duplicate_identity_can_trigger_buy" to false,
                    "fixed_market_pulse_research_file" to "fixed_market_pulse_research.json",
                    "fixed_fast_candidate_research_file" to "fixed_fast_candidates.json",
                    "fixed_fast_candidate_trade_effect" to "NONE",
                    "fixed_market_pulse_new_requests" to 0,
                    "fixed_market_pulse_trade_effect" to "NONE",
                    "fixed_historical_research_rows" to fixedHistoricalEvidenceArchive.summary().rows,
                    "fixed_historical_research_not_broker_trades" to true,
                    "research_chronology_scope" to "SAMPLED_PRICE_ONLY_NOT_REALIZED_PNL",
                    "research_chronology_rows" to researchOutcomeChronologyJournal.recent().size,
                    "decision_evidence_file" to "decision_evidence.jsonl",
                    "decision_evidence_rows" to decisionEvidenceJournal.lineCount(),
                    "decision_evidence_write_failures" to decisionEvidenceJournal.failedWriteCount(),
                    "decision_evidence_trading_effect" to "NONE",
                    "univest_48_factor_cohort" to "official 1-3 month entry + same-day exit + both Groww quote prices; no broker trade assumed",
                    "univest_cohort_stats" to forecastUnivestJournal.summary(),
            "univest_notification_captures" to forecastSourceNotificationJournal.count(),
                    "news_event_outcome_label" to ">=1% measured within 60m from first observed baseline",
                    "fixed_priority_phase_event" to "FIXED_PRIORITY_PHASE_PROFILE",
                    "fixed_entry_latency_event" to "ENTRY_LATENCY_PROFILE",
                    "fixed_notification_latency_event" to "FIXED_NOTIFICATION_POSTED",
                    "research_delay_buckets" to "QUOTE_CLIENT_RESPONSE_TO_NOTIFICATION_ONLY",
                    "research_slowness_is_not_an_entry_veto" to true,
                    "export_streaming_json" to true,
                    "export_runs_on_io_dispatcher" to true
                ))
                fixedSnapshot?.let { add("fixed_engine_snapshot.json", it.payload) }
                auditLogger.appendExportEntry(zip)
                written("system_audit.jsonl")
                add("export_manifest.json", mapOf(
                    "version" to BuildConfig.VERSION_NAME,
                    "completed_at_ms" to System.currentTimeMillis(),
                    "files_including_manifest" to (fileCount + 1),
                    "status" to "WRITTEN_COMPLETE_ZIP_STREAM",
                    "historical_research_only" to true,
                    "broker_orders_not_inferred_from_prices" to true,
                    "fixed_selection_or_trailing_changed_by_export" to false,
                    "no_automatic_delete_or_truncate" to true
                ))
                zip.finish()
            }
        } ?: error("Unable to open export destination")
        LogExportResult(fileCount, signals.size, trades.size)
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
        val now = System.currentTimeMillis()
        armHotState = ArmHotState(token, settings, 0.0, now)
        val auditDue = now - lastArmHotAuditAtMs >= ARM_HOT_AUDIT_INTERVAL_MS
        val armedChanged = lastArmHotAuditArmed == null || lastArmHotAuditArmed != settings.armEffective
        if (auditDue || armedChanged) {
            auditLogger.log("RUNTIME", "ARM_HOT_PATH_READY", mapOf(
                "wallet_balance_not_queried" to true,
                "armed" to settings.armEffective
            ))
            lastArmHotAuditAtMs = now
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

    /**
     * Fixed Auto Buy is independent of asynchronous portfolio recovery. Every new symbol
     * still passes its own fresh broker-inventory/duplicate check, account readiness,
     * price/liquidity safeguards and immediate post-fill GTT attempt.
     */
    private fun preFixedAutoBuyExecutionGuard(settings: AppSettings) {
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
        notificationStartedNs: Long? = null,
        entryQuote: QuotePayload,
        quoteReceivedAtMs: Long,
        allowOtherSymbolProtectionRecovery: Boolean = false
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
        val unprotected = managedDao.openPositions().filter(::appOwned).filterNot {
            com.multify.traderpro.engine.GttProtectionReadinessPolicy.verified(
                it.smartOrderMode, it.smartOrderId, it.smartOrderStatus)
        }
        if (unprotected.isNotEmpty()) {
            auditLogger.log("SAFETY",
                if (allowOtherSymbolProtectionRecovery)
                    "FIXED_BUY_CONTINUED_DURING_OTHER_SYMBOL_PROTECTION_RECOVERY"
                else "NEW_BUY_BLOCKED_UNPROTECTED_HOLDING",
                mapOf("symbol" to symbol, "unprotected_symbols" to unprotected.map { it.symbol },
                    "critical_trading_priority" to true,
                    "new_symbol_ownership_check_passed" to true,
                    "buy_allowed" to allowOtherSymbolProtectionRecovery))
        }
        require(allowOtherSymbolProtectionRecovery || unprotected.isEmpty()) {
            "New BUY blocked: app-owned holding lacks verified broker GTT"
        }
        // Reuse the quote fetched on the signal path. If broker inventory checks made
        // it stale, refresh only this one symbol once, never the full NSE universe.
        val age = System.currentTimeMillis() - quoteReceivedAtMs
        val freshQuote: QuotePayload
        val freshAt: Long
        if (age < 0L || age > CircuitAwareBuyPolicy.MAX_QUOTE_AGE_MS) {
            freshQuote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol)
                .requirePayload("Circuit refresh $symbol")
            freshAt = System.currentTimeMillis()
            preferences.recordMarketData()
        } else {
            freshQuote = entryQuote
            freshAt = quoteReceivedAtMs
        }
        val price = CircuitAwareBuyPolicy.decide(freshQuote, freshAt, System.currentTimeMillis())
        auditLogger.log("EXECUTION", "CIRCUIT_SAFE_LIMIT_BUY", mapOf(
            "symbol" to symbol, "qty" to qty, "limit_price" to price.limitPrice,
            "upper_circuit" to price.upperCircuit, "best_offer" to price.bestOffer,
            "last_price" to price.lastPrice, "quote_age_ms" to price.quoteAgeMs,
            "single_symbol_quote_refresh" to (freshAt != quoteReceivedAtMs),
            "full_market_refresh" to false, "max_slippage_bps" to CircuitAwareBuyPolicy.MAX_BUY_SLIPPAGE_BPS
        ))
        submitMarketOrder(token, symbol, "BUY", qty, "CNC", reference,
            notificationStartedNs, buyLimitPrice = price.limitPrice)
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
        univestResearch = forecastUnivestJournal.recent().map { row ->
            UnivestResearchDto(row.symbol, row.status, row.entryPrice, row.exitPrice,
                row.outcomePct, row.observedFactorCount, row.entryPriceSource, row.exitPriceSource)
        },
        forecastWatchlist = secondaryForecastWatchlist(LocalDate.now(INDIA).toString()),
        fixedWatchlist = emptyList(),
        fixedEarlyWatch = fixedEarlyWatchJournal.recent(LocalDate.now(INDIA).toString()),
        fixedPulseResearch = fixedMarketPulseResearchStore.recent(LocalDate.now(INDIA).toString()),
        fixedFastCandidatePrices = fixedFastCandidatePriceStore.recent(LocalDate.now(INDIA).toString())
    )

    private fun marketSession(): String {
        val now = ZonedDateTime.now(INDIA)
        return if (com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(now)) "OPEN" else "CLOSED"
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
        private const val FIXED_MIN_STOCK_PRICE_RUPEES = 200.0
        private const val FIXED_PREMARKET_WARMUP_INTERVAL_MS = 5L * 60L * 1000L
        private const val FIXED_V741_FORECAST_SCAN_INTERVAL_MS = 90_000L
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
            "fixed_historical_research.json",
            "fixed_imported_same_day_dedup.json",
            "fixed_market_pulse_research.json",
            "fixed_fast_candidates.json",
            "fixed_early_watch.json",
            "fixed_engine_snapshot.json",
            "fixed_factor_snapshots.jsonl",
            "forecast_news_events.jsonl",
            "forecast_univest_same_day.jsonl",
            "research_outcome_chronology.jsonl",
            "decision_evidence.jsonl",
            "system_audit.jsonl"
        )
        private const val MAX_RESTORE_ENTRY_BYTES = 128L * 1024L * 1024L
        private const val MAX_RESTORE_TOTAL_BYTES = 256L * 1024L * 1024L
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
