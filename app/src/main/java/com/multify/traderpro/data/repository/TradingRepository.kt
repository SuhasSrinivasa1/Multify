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
import com.multify.traderpro.data.network.BrokerStatusDto
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.DaySummaryDto
import com.multify.traderpro.data.network.EngineHealthDto
import com.multify.traderpro.data.network.ExecutionAverageDto
import com.multify.traderpro.data.network.ExecutionStatsDto
import com.multify.traderpro.data.network.ForecastChampionDto
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.ForecastLearningStatsDto
import com.multify.traderpro.data.network.FixedSnapshotDto
import com.multify.traderpro.data.network.GttCreateRequest
import com.multify.traderpro.data.network.GttModifyRequest
import com.multify.traderpro.data.network.GttOrder
import com.multify.traderpro.data.network.GrowwApiFactory
import com.multify.traderpro.data.network.GrowwApiUsageLedger
import com.multify.traderpro.data.network.GrowwRecoveryGate
import com.multify.traderpro.data.network.GrowwHolding
import com.multify.traderpro.data.network.GrowwPosition
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.LearningStatsDto
import com.multify.traderpro.data.network.OrderCancelRequest
import com.multify.traderpro.data.network.OrderCreateRequest
import com.multify.traderpro.data.network.OrderPayload
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
import com.multify.traderpro.engine.ExecutionTelemetryMath
import com.multify.traderpro.engine.ForecastAutoBuyPolicy
import com.multify.traderpro.engine.ForecastAdaptiveSelectionPolicy
import com.multify.traderpro.engine.ForecastEconomicsDecision
import com.multify.traderpro.engine.ForecastEvidenceDiagnostics
import com.multify.traderpro.engine.ForecastFamilyOutcome
import com.multify.traderpro.engine.ForecastHistoricalOutcome
import com.multify.traderpro.engine.ForecastFixedPositiveTransferPolicy
import com.multify.traderpro.engine.ForecastMultifyPositiveTransferPolicy
import com.multify.traderpro.engine.MultifyPositiveTransferCase
import com.multify.traderpro.engine.FixedPositiveTransferCase
import com.multify.traderpro.engine.ForecastQualityDecision
import com.multify.traderpro.engine.ForecastQualityPolicy
import com.multify.traderpro.engine.ForecastTargetExpansionPolicy
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
import com.multify.traderpro.engine.FixedStrategyEvaluation
import com.multify.traderpro.engine.GrowwDeliveryCostPolicy
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import com.multify.traderpro.engine.HardStopLossPolicy
import com.multify.traderpro.engine.HoldingSmartOrderPolicy
import com.multify.traderpro.engine.BrokerExecutionSafetyPolicy
import com.multify.traderpro.engine.MarketPriorityPolicy
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
    private val recoveryGate: GrowwRecoveryGate,
    private val brokerRetryGuard: BrokerExecutionRetryGuard,
    private val parser: NotificationParser,
    private val nseSymbols: NseSymbolRepository,
    private val auditLogger: AuditLogger,
    private val marketDiscoveryStateStore: MarketDiscoveryStateStore,
    private val forecastAutoBuyOrderStore: ForecastAutoBuyOrderStore,
    private val fixedEngineSnapshotStore: FixedEngineSnapshotStore,
    private val embeddedHistoricalIntelligenceStore: EmbeddedHistoricalIntelligenceStore,
    private val fixedRecommendationStore: FixedRecommendationStore,
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
    private var lastForecastScanAtMs = 0L
    private var lastFixedScanAtMs = 0L
    private var lastForecastOutcomeMonitorAtMs = 0L
    private var lastFixedOutcomeMonitorAtMs = 0L
    private var lastFixedPreMarketWarmupAtMs = 0L
    private var lastFixedHotPrewarmForDueAtMs = 0L
    private var brokerInventorySnapshot: BrokerInventorySnapshot? = null
    @Volatile private var activeGttSnapshot: ActiveGttSnapshot? = null
    private var lastClosedSessionReconcileAtMs = 0L
    private val lastPortfolioConflictAuditAtMs = ConcurrentHashMap<String, Long>()
    private val recentlyClosedSymbolsAtMs = ConcurrentHashMap<String, Long>()
    private var lastArmHotAuditAtMs = 0L
    private var lastMfeBackfillAtMs = 0L
    private var lastArmHotAuditBalance = Double.NaN
    private var lastArmHotAuditArmed: Boolean? = null
    private val closePositionMutexes = ConcurrentHashMap<Long, Mutex>()
    private val forecastAutoBuySubmissionMutex = Mutex()
    private val fixedAutoBuySubmissionMutex = Mutex()
    private val scanApiCycleMutex = Mutex()
    @Volatile private var fixedApiPriorityRequested = false
    @Volatile private var marketCriticalTradeInFlight = false
    @Volatile private var tradingRecoveryReady = false
    private val embeddedHistoricalSeedMutex = Mutex()
    private val bulkMarketDataMutex = Mutex()
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
        val close: Double
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

    suspend fun setHoldingBudget(value: Long) {
        preferences.setHoldingBudget(value)
        armHotState = null
        auditLogger.log("SETTINGS", "HOLDING_BUDGET", mapOf("rupees" to value))
    }

    suspend fun setArm(enabled: Boolean) {
        if (enabled) {
            val s = preferences.settings.first()
            require(tradingRecoveryReady) { "Wait for broker recovery and trailing protection reconciliation" }
            require(s.brokerAuthenticated) { "Authenticate Groww first" }
            require(s.staticIpMatched) { "Static IP verification failed" }
            require(s.brokerDdpiEnabled) { "DDPI is required for CNC holdings so the app can sell its own holdings later" }
            require(!s.safetyHalt) { "Safety halt is active" }
        }
        preferences.setArm(enabled)
        if (enabled) {
            val ready = runCatching { prepareArmHotPath(force = true) }.getOrDefault(false)
            if (!ready) {
                preferences.setArm(false)
                armHotState = null
                error("ARM could not pre-warm Groww token and CNC buying power")
            }
        } else {
            // Multify ARM controls Multify execution only. Fixed and Forecast have independent
            // execution toggles and must not be disabled or cancelled when Multify is disarmed.
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
        val snapshot = managedSnapshotLocal()
        val execution = executionStats()
        val forecasts = learningDao.longForecastsForDate(today).map { it.toForecastDto() }
        val fixedForecasts = fixedRecommendationStore.recommendations(today)
            .filter { it.entryPrice > FIXED_MIN_STOCK_PRICE_RUPEES }
            .map { it.toDto() }
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
            forecasts = forecasts,
            fixedForecasts = fixedForecasts,
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
                forecasts = forecasts, fixedForecasts = fixedForecasts, forecastChampions = champions,
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
        val trailArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_MULTIFY_HOLDING, symbol = symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = HardStopLossPolicy.stopPrice(entry),
                targetPrice = signal.target,
                strategy = "Multify ARM holding · hard -5% floor · v7 fixed stepped trail 0.55→0.50 then +0.10%",
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
            "ARM bought $filled $symbol into holdings @ ₹${fmt(entry)}. Fixed trailing arms when gain reaches +0.55%, protecting +0.50%, then ratchets by +0.10% steps.",
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

    suspend fun runFixedPriorityCycle(): Boolean {
        fixedApiPriorityRequested = true
        return scanApiCycleMutex.withLock {
            val cycleStartedAtMs = System.currentTimeMillis()
            try {
                val previousScanAtMs = lastFixedScanAtMs
                val recommendationStartedAtMs = System.currentTimeMillis()
                generateFixedForecasts(false)
                val recommendationCompletedAtMs = System.currentTimeMillis()
                val scanned = lastFixedScanAtMs != previousScanAtMs
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

    suspend fun runForecastOpportunisticCycle(): Boolean {
        if (!forecastMarketCapacityAvailable()) return false
        if (!scanApiCycleMutex.tryLock()) return false
        return try {
            if (!forecastMarketCapacityAvailable()) return false
            val previousScanAtMs = lastForecastScanAtMs
            generateDailyForecasts(false)
            val scanned = lastForecastScanAtMs != previousScanAtMs
            if (scanned && forecastLiveUnlocked() && forecastMarketCapacityAvailable()) {
                autoBuyForecastRecommendations()
            }
            scanned
        } finally {
            scanApiCycleMutex.unlock()
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

    private fun forecastMarketCapacityAvailable(): Boolean {
        if (fixedApiPriorityRequested || marketCriticalTradeInFlight) return false
        val usage = GrowwApiUsageLedger.snapshot()
        fun row(category: GrowwApiUsageLedger.Category) = usage.categories.first { it.category == category }
        val live = row(GrowwApiUsageLedger.Category.LIVE_DATA)
        val historical = row(GrowwApiUsageLedger.Category.HISTORICAL)
        val nonTrading = row(GrowwApiUsageLedger.Category.NON_TRADING)
        return MarketPriorityPolicy.canRunForecast(
            fixedPriorityRequested = fixedApiPriorityRequested,
            criticalTradeInFlight = marketCriticalTradeInFlight,
            millisUntilNextFixed = millisUntilNextFixedScan(),
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
    suspend fun maintainFixedHotDataCache(): Boolean {
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6 || now.toLocalTime() < LocalTime.of(9, 15) || now.toLocalTime() >= LocalTime.of(15, 30)) {
            return false
        }
        val previousScanAt = lastFixedScanAtMs
        if (previousScanAt <= 0L || fixedApiPriorityRequested) return false
        val nextDueAt = previousScanAt + FIXED_V741_FORECAST_SCAN_INTERVAL_MS
        val untilDueMs = nextDueAt - System.currentTimeMillis()
        if (untilDueMs !in FIXED_HOT_PREWARM_MIN_LEAD_MS..FIXED_HOT_PREWARM_MAX_LEAD_MS) return false
        if (lastFixedHotPrewarmForDueAtMs == nextDueAt) return false

        val today = now.toLocalDate().toString()
        val observations = fixedRecommendationStore.observations(today)
        val latestScanAt = observations.maxOfOrNull { it.scanAtMs } ?: return false
        val symbols = observations.asSequence()
            .filter { it.scanAtMs == latestScanAt }
            .map { it.symbol.uppercase(Locale.US) }
            .distinct()
            .take(FIXED_V741_RESEARCH_CAPTURE_TOP_N)
            .toList()
        if (symbols.isEmpty()) return false
        val token = ensureToken() ?: return false
        lastFixedHotPrewarmForDueAtMs = nextDueAt

        val start = now.minusDays(4).toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        var warmed = 0
        for (batch in symbols.chunked(FIXED_HOT_PREWARM_CONCURRENCY)) {
            if (fixedApiPriorityRequested) break
            val results = coroutineScope {
                batch.map { symbol ->
                    async {
                        runCatching {
                            apiFactory.groww.historicalCandles(
                                bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "15minute"
                            ).requirePayload("Fixed hot prewarm $symbol")
                        }.isSuccess
                    }
                }.awaitAll()
            }
            warmed += results.count { it }
        }
        auditLogger.log("FIXED_ENGINE", "HOT_DATA_PREWARM", mapOf(
            "symbols_requested" to symbols.size,
            "symbols_warmed" to warmed,
            "lead_ms" to untilDueMs,
            "formula_changed" to false,
            "cache_only_acceleration" to true
        ))
        return warmed > 0
    }

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
        if (!force && nowMs - lastForecastScanAtMs < FORECAST_SCAN_INTERVAL_MS) return existing.map { it.toForecastDto() }
        val token = ensureToken() ?: return existing.map { it.toForecastDto() }
        lastForecastScanAtMs = nowMs
        val scanStartedElapsedMs = SystemClock.elapsedRealtime()
        val apiBefore = GrowwApiUsageLedger.snapshot()
        val cacheHitsBefore = GrowwApiUsageLedger.cacheHitCount()
        val cacheMissesBefore = GrowwApiUsageLedger.cacheMissCount()

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
        val evaluated = mutableListOf<Pair<String, StrategyEvaluation>>()
        val detailedEvaluationStartedElapsedMs = SystemClock.elapsedRealtime()
        for (symbol in universe) {
            if (fixedApiPriorityRequested) {
                auditLogger.log("FORECAST", "YIELD_TO_FIXED_PRIORITY", mapOf("stage" to "detailed_evaluation"))
                break
            }
            val evaluation = runCatching { forecastEvaluation(symbol, token) }
                .onFailure {
                    auditLogger.log("FORECAST_RESEARCH", "EVALUATION_ERROR", mapOf(
                        "symbol" to symbol,
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
                .getOrNull() ?: continue
            if (evaluation.features.ltp < 10.0) continue
            evaluated += symbol to evaluation
            delay(FORECAST_SYMBOL_PACING_MS)
        }
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
        val targetPct = ForecastQualityPolicy.adaptiveTargetPct(learned.longAveragePct, priorMfe)

        val have = existing.map { it.symbol }.toSet()
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
        }

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
            val baseQuality = ForecastQualityPolicy.evaluate(row.second, targetPct, marketRegime)
            val expandedTargetPct = ForecastTargetExpansionPolicy.targetPct(
                baseTargetPct = targetPct,
                symbol = row.first,
                strategy = row.second.strategy,
                regime = row.second.regime,
                marketRegime = marketRegime,
                volatilityCapacityPct = baseQuality.volatilityCapacityPct,
                positives = fixedPositiveTransfer
            )
            val expandedQuality = ForecastQualityPolicy.evaluate(row.second, expandedTargetPct, marketRegime)
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
                    fixedPositiveBonus + multifyPositiveBonus + universalEvidenceBonus
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
            captureForecastResearchScan(today, nowMs, marketRegime, targetPct, ranked.take(RESEARCH_CAPTURE_TOP_N))
        }

        // v7.3.3: there is no daily numeric recommendation cap.
        // Every newly confirmed PRIMARY candidate can publish; same-day duplicate symbols remain blocked.
        val unseen = ranked.filter { it.symbol !in have }
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
        val publishable = if (liveForecastReady) unseen.filter { item ->
            val currentPrimary =
                item.quality.tier == "PRIMARY" && item.quality.publishEligible && item.quality.executionEligible &&
                    item.economics.profitableAfterCosts
            ForecastPublicationPolicy.isReady(
                discoveryBaselineReady = discoveryPass.baselineReady,
                currentPrimary = currentPrimary,
                discoveredMarketWide = item.symbol in discoveredSymbols,
                priorPrimaryHits = priorPrimaryHits(item.symbol)
            )
        } else emptyList()

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

        val discoveryPass = if (master.isEmpty()) {
            MarketDiscoveryPass(emptyList(), false, 0.0, false, 0)
        } else {
            discoverFixedMarketWideCandidates(token, master, today)
        }
        val discoveredSymbols = discoveryPass.symbols

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
            delay(FIXED_V741_FORECAST_SYMBOL_PACING_MS)
        }
        val evaluated = universe.mapNotNull { symbol ->
            evaluatedBySymbol[symbol]?.let { symbol to it }
        }

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
        val historical = withFixedHistoricalDataSlot {
            apiFactory.groww.historicalCandles(
                bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "15minute"
            ).requirePayload("Fixed forecast candles $symbol")
        }
        require(historical.candles.size >= 5) { "Insufficient fixed forecast candles" }
        val features = FixedLocalStrategyEngine.buildFeatures(quote, historical, 15)
        val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "fixed-forecast", confidence = 1.0)
        return FixedLocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
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

        for ((index, chunk) in chunks.withIndex()) {
            val payload = fetchFixedDiscoveryLtpBatch(token, chunk, index)
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

    private suspend fun captureForecastResearchScan(
        date: String,
        scanAtMs: Long,
        marketRegime: String,
        targetPct: Double,
        candidates: List<ForecastCandidate>
    ) {
        candidates.forEachIndexed { index, item ->
            val featureMap = researchFeatureMap(item.evaluation, item.factorModel) + mapOf(
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

    private suspend fun discoverMarketWideCandidates(
        token: String,
        master: List<String>,
        today: String
    ): MarketDiscoveryPass {
        val distinctMaster = master.distinct()
        val persisted = marketDiscoveryStateStore.load(today)
        val previous = persisted?.previousLtps.orEmpty()
        val sessionOpen = persisted?.sessionOpenLtps.orEmpty()
        val restored = persisted != null
        val current = linkedMapOf<String, Double>()
        var batchErrors = 0
        var consecutiveFailures = 0
        var abortedForNetwork = false
        val chunks = distinctMaster.chunked(50)

        for ((index, chunk) in chunks.withIndex()) {
            if (fixedApiPriorityRequested) {
                auditLogger.log("FORECAST_DISCOVERY", "YIELD_TO_FIXED_PRIORITY", mapOf("batch_index" to index))
                break
            }
            val payload = fetchDiscoveryLtpBatch(token, chunk, index)
            if (payload == null) {
                batchErrors++
                consecutiveFailures++
                if (consecutiveFailures >= DISCOVERY_CONSECUTIVE_FAILURE_ABORT) {
                    abortedForNetwork = true
                    auditLogger.log("FORECAST_DISCOVERY", "LTP_SCAN_ABORTED_NETWORK", mapOf(
                        "batch_index" to index,
                        "master_count" to distinctMaster.size,
                        "coverage_so_far" to current.size,
                        "consecutive_failures" to consecutiveFailures
                    ))
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
            if (index + 1 < chunks.size) delay(DISCOVERY_BATCH_PACING_MS)
        }

        val denominator = distinctMaster.size.coerceAtLeast(1)
        val coverageRatio = current.size.toDouble() / denominator.toDouble()
        val previousCoverageRatio = previous.size.toDouble() / denominator.toDouble()
        val baselineReady =
            previousCoverageRatio >= MIN_DISCOVERY_COVERAGE_RATIO &&
                coverageRatio >= MIN_DISCOVERY_COVERAGE_RATIO &&
                !abortedForNetwork

        val ranked = if (baselineReady) {
            MarketDiscoveryPolicy.rank(
                currentLtps = current,
                previousLtps = previous,
                sessionOpenLtps = sessionOpen,
                limit = DISCOVERY_DYNAMIC_LIMIT
            )
        } else emptyList()

        if (coverageRatio >= MIN_DISCOVERY_COVERAGE_RATIO && !abortedForNetwork) {
            val mergedSessionOpen = sessionOpen.toMutableMap()
            current.forEach { (symbol, ltp) -> mergedSessionOpen.putIfAbsent(symbol, ltp) }
            marketDiscoveryStateStore.save(today, mergedSessionOpen, current)
        } else {
            auditLogger.log("FORECAST_DISCOVERY", "BASELINE_NOT_OVERWRITTEN", mapOf(
                "coverage_pct" to coverageRatio * 100.0,
                "required_coverage_pct" to MIN_DISCOVERY_COVERAGE_RATIO * 100.0,
                "aborted_for_network" to abortedForNetwork,
                "persisted_previous_count" to previous.size
            ))
        }

        marketDiscoveryStateStore.recordHealth(
            date = today,
            coverageRatio = coverageRatio,
            batchErrors = batchErrors,
            abortedForNetwork = abortedForNetwork,
            baselineReady = baselineReady
        )
        preferences.recordMarketData()
        auditLogger.log("FORECAST_DISCOVERY", "MARKET_WIDE_SCAN", mapOf(
            "master_count" to distinctMaster.size,
            "ltp_coverage" to current.size,
            "coverage_pct" to coverageRatio * 100.0,
            "batch_count" to chunks.size,
            "batch_errors" to batchErrors,
            "aborted_for_network" to abortedForNetwork,
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
                    apiFactory.groww.ltp(
                        bearer(token),
                        exchangeSymbols = exchangeSymbols
                    ).requirePayload("Market-wide discovery LTP")
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

    private suspend fun <T> withFixedHistoricalDataSlot(block: suspend () -> T): T =
        fixedHistoricalDataMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val waitMs = max(
                (nextFixedHistoricalAtElapsedMs - now).coerceAtLeast(0L),
                (fixedHistoricalCooldownUntilElapsedMs - now).coerceAtLeast(0L)
            )
            if (waitMs > 0L) delay(waitMs)
            try {
                block().also {
                    nextFixedHistoricalAtElapsedMs =
                        SystemClock.elapsedRealtime() + GrowwApiBudgetPolicy.FIXED_HISTORICAL_MIN_GAP_MS
                }
            } catch (t: Throwable) {
                if (t is HttpException && t.code() == 429) {
                    fixedHistoricalCooldownUntilElapsedMs =
                        SystemClock.elapsedRealtime() + GrowwApiBudgetPolicy.RATE_LIMIT_COOLDOWN_MS
                }
                nextFixedHistoricalAtElapsedMs =
                    SystemClock.elapsedRealtime() + GrowwApiBudgetPolicy.FIXED_HISTORICAL_MIN_GAP_MS
                throw t
            }
        }

    private suspend fun <T> withForecastLiveDataSlot(block: suspend () -> T): T {
        if (!forecastMarketCapacityAvailable()) error("Forecast yielded to market priority")
        return withFixedLiveDataSlot(block)
    }

    private suspend fun <T> withForecastHistoricalDataSlot(block: suspend () -> T): T {
        if (!forecastMarketCapacityAvailable()) error("Forecast yielded to market priority")
        return withFixedHistoricalDataSlot(block)
    }

    private suspend fun forecastEvaluation(symbol: String, token: String): StrategyEvaluation {
        val now = ZonedDateTime.now(INDIA)
        val start = now.minusDays(4).toLocalDate().atTime(9,15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val quote = withForecastLiveDataSlot {
            apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
        }
        val historical = withForecastHistoricalDataSlot {
            apiFactory.groww.historicalCandles(
                bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "15minute"
            ).requirePayload("Forecast candles $symbol")
        }
        require(historical.candles.size >= 5) { "Insufficient forecast candles" }
        val features = LocalStrategyEngine.buildFeatures(quote, historical, 15)
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
            .firstOrNull { it.symbol.equals(symbol, true) } ?: error("No active LONG recommendation for $symbol")
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
    suspend fun autoBuyForecastRecommendations(): Int =
        forecastAutoBuySubmissionMutex.withLock { autoBuyForecastRecommendationsLocked() }

    private suspend fun autoBuyForecastRecommendationsLocked(): Int {
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6 || marketSession() != "OPEN") return 0
        val settings = preferences.settings.first()
        if (!settings.forecastAutoBuyEnabled || settings.safetyHalt) return 0
        preIndependentExecutionGuard(settings)
        val today = now.toLocalDate().toString()
        val rows = learningDao.longForecastsForDate(today)
            .filter { it.status == "ACTIVE" && it.executionEligible && it.qualityTier == "PRIMARY" }
            .sortedBy { it.generatedAtMs }
        var submitted = 0
        for (row in rows) {
            if (fixedApiPriorityRequested || marketCriticalTradeInFlight) break
            val live = preferences.settings.first()
            if (!live.forecastAutoBuyEnabled || live.safetyHalt) break
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
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val available = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        val sizing = CashAwareCncSizing.sizeFixedAvailable(settings.holdingBudgetRupees.toDouble(), available, ltp)
        require(sizing.quantity > 0) { "Fixed BUY skipped: remaining CNC cash cannot fund one buffered share of ${row.symbol}" }

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
        if (now.dayOfWeek.value >= 6 || marketSession() != "OPEN") return 0
        val settings = preferences.settings.first()
        if (!settings.fixedAutoBuyEnabled || settings.safetyHalt) return 0
        preIndependentExecutionGuard(settings)
        val token = ensureToken() ?: return 0
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
            if (!liveSettings.fixedAutoBuyEnabled || liveSettings.safetyHalt) break
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
            }.getOrNull() ?: continue
            val quoteElapsedMs = (System.currentTimeMillis() - quoteStartedAtMs).coerceAtLeast(0L)
            val ltp = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: continue

            val marginStartedAtMs = System.currentTimeMillis()
            val margin = runCatching { apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin") }
                .onFailure {
                    auditLogger.log("FIXED_AUTO_BUY", "MARGIN_ERROR", mapOf(
                        "symbol" to row.symbol,
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }.getOrNull() ?: continue
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
            if (!executionSettings.fixedAutoBuyEnabled || executionSettings.safetyHalt) break

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
        if (!forecastLiveUnlocked()) return emptyList()
        val settings = preferences.settings.first()
        if (settings.safetyHalt) return emptyList()
        if (settings.forecastAutoBuyEnabled) {
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
        val token = ensureToken() ?: return rows.count { it.status == "ACTIVE" }

        val ltps = fetchForecastOutcomeLtps(token, rows.map { it.symbol })
        if (ltps.isEmpty()) return rows.count { it.status == "ACTIVE" }
        preferences.recordMarketData()

        for (row in rows) {
            val ltp = ltps[row.symbol.uppercase(Locale.US)] ?: continue
            val movePct = (ltp / row.entryPrice - 1.0) * 100.0
            val adversePct = max(0.0, (row.entryPrice - ltp) / row.entryPrice * 100.0)
            val hitNow = ltp >= row.targetPrice
            val expired = now.toLocalTime() >= LocalTime.of(15, 0)
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

    suspend fun monitorFixedOutcomes(): Int {
        val nowMs = System.currentTimeMillis()
        val today = LocalDate.now(INDIA).toString()
        val rows = fixedRecommendationStore.recommendations(today)
        // Fixed outcome tracking is diagnostic only and never feeds future Fixed ranking.
        // Preserve live-data capacity for Fixed discovery/evaluation and sell protection.
        if (marketSession() == "OPEN") {
            return rows.count { it.status == "ACTIVE" }
        }
        if (rows.isEmpty()) return 0
        if (nowMs - lastFixedOutcomeMonitorAtMs < FORECAST_OUTCOME_INTERVAL_MS) {
            return rows.count { it.status == "ACTIVE" }
        }
        lastFixedOutcomeMonitorAtMs = nowMs
        val token = ensureToken() ?: return rows.count { it.status == "ACTIVE" }
        val ltps = fetchForecastOutcomeLtps(token, rows.map { it.symbol })
        if (ltps.isEmpty()) return rows.count { it.status == "ACTIVE" }
        preferences.recordMarketData()
        val now = ZonedDateTime.now(INDIA)
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
            fixedRecommendationStore.update(
                row.copy(
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
            )
        }
        return fixedRecommendationStore.recommendations(today).count { it.status == "ACTIVE" }
    }

    private suspend fun fetchForecastOutcomeLtps(token: String, symbols: List<String>): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        for ((index, chunk) in symbols.map { it.uppercase(Locale.US) }.distinct().chunked(50).withIndex()) {
            var payload: Map<String, Double>? = null
            var lastFailure: Throwable? = null
            for (attempt in 0 until FORECAST_OUTCOME_BATCH_ATTEMPTS) {
                try {
                    payload = withForecastLiveDataSlot {
                        apiFactory.groww.ltp(
                            bearer(token),
                            exchangeSymbols = chunk.joinToString(",") { "NSE_$it" }
                        ).requirePayload("Forecast outcome batch LTP")
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

    private suspend fun backfillForecastResearchOutcomes(date: String, token: String): Int {
        val pending = learningDao.pendingForecastResearchOutcomes(date)
        if (pending.isEmpty()) return 0
        val sessionDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return 0
        val sessionEndMs = sessionDate.atTime(15, 30).atZone(INDIA).toInstant().toEpochMilli()
        var updatedCount = 0
        var consecutiveNetworkFailures = 0
        val groups = pending.groupBy { it.symbol }.entries.take(RESEARCH_BACKFILL_SYMBOL_BATCH)

        for ((symbol, rows) in groups) {
            val earliest = rows.minOf { it.scanAtMs }
            val startTime = Instant.ofEpochMilli(earliest)
                .atZone(INDIA).toLocalDateTime().format(HIST_FORMAT)
            val endTime = sessionDate.atTime(15, 30).format(HIST_FORMAT)
            var failure: Throwable? = null
            val historical = try {
                apiFactory.groww.historicalCandles(
                    bearer(token),
                    growwSymbol = "NSE-$symbol",
                    startTime = startTime,
                    endTime = endTime,
                    candleInterval = "5minute"
                ).requirePayload("Forecast research outcome $symbol")
            } catch (t: Throwable) {
                failure = t
                null
            }

            if (historical == null) {
                auditLogger.log("FORECAST_RESEARCH", "OUTCOME_BACKFILL_ERROR", mapOf(
                    "date" to date, "symbol" to symbol,
                    "message" to (failure?.message ?: failure?.javaClass?.simpleName ?: "unknown")
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
            preferences.recordMarketData()
            val candles = historical.candles.mapNotNull { x ->
                if (x.size < 5) return@mapNotNull null
                val atMs = researchCandleTimestampMs(x[0]) ?: return@mapNotNull null
                val high = runCatching { x[2].asDouble }.getOrNull() ?: return@mapNotNull null
                val low = runCatching { x[3].asDouble }.getOrNull() ?: return@mapNotNull null
                val close = runCatching { x[4].asDouble }.getOrNull() ?: return@mapNotNull null
                ResearchCandle(atMs, high, low, close)
            }.sortedBy { it.atMs }
            rows.forEach { row ->
                val xs = candles.filter { it.atMs >= row.scanAtMs && it.atMs <= sessionEndMs }
                if (xs.isEmpty() || row.entryPrice <= 0.0) return@forEach
                val high = xs.maxOf { it.high }
                val low = xs.minOf { it.low }
                val close = xs.last().close
                val mfe = max(0.0, (high / row.entryPrice - 1.0) * 100.0)
                val mae = max(0.0, (row.entryPrice - low) / row.entryPrice * 100.0)
                val closePct = (close / row.entryPrice - 1.0) * 100.0
                learningDao.updateForecastResearch(
                    row.copy(
                        outcomeMfePct = mfe,
                        outcomeMaePct = mae,
                        outcomeClosePct = closePct,
                        outcomeTargetHit = high + 1e-9 >= row.entryPrice * (1.0 + row.targetPct / 100.0),
                        outcomeUpdatedAtMs = System.currentTimeMillis()
                    )
                )
                updatedCount++
            }
            delay(RESEARCH_BACKFILL_SYMBOL_PACING_MS)
        }
        if (updatedCount > 0) {
            auditLogger.log("FORECAST_RESEARCH", "OUTCOMES_BACKFILLED", mapOf(
                "date" to date, "count" to updatedCount
            ))
        }
        return updatedCount
    }

    suspend fun runAfterHoursResearch(force: Boolean = false): ResearchDto {
        ensureHistoricalSeed()
        val today = LocalDate.now(INDIA).toString()
        val token = ensureToken()
        if (token != null) {
            runCatching { backfillRecentMultifyMfe(token, MFE_AFTER_HOURS_BACKFILL_BATCH_SIZE, force = true) }
        }
        val researchUpdated = if (token != null) {
            val incompleteDate = learningDao.forecastResearchDates()
                .filter { it >= V7_RESEARCH_COHORT_START_DATE }
                .firstOrNull { learningDao.pendingForecastResearchOutcomes(it).isNotEmpty() }
            if (incompleteDate != null) {
                runCatching { backfillForecastResearchOutcomes(incompleteDate, token) }.getOrDefault(0)
            } else 0
        } else 0
        val todayCalibrationUpdated = if (token != null && learningDao.forecastResearchForDate(today).isNotEmpty()) {
            runCatching { backfillForecastResearchOutcomes(today, token) }.getOrDefault(0)
        } else 0
        val universalEvidenceUpdated = if (token != null) {
            runCatching { refreshUniversalPositiveEvidence(token) }
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

        val report = buildString {
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
        val holdings = apiFactory.groww.holdings(bearer(token)).requirePayload("Groww holdings").holdings
            .associateBy { it.tradingSymbol.uppercase(Locale.US) }
        val positions = apiFactory.groww.positions(bearer(token)).requirePayload("Groww positions").positions
            .filter { it.product.equals("CNC", true) }
            .associateBy { it.tradingSymbol.uppercase(Locale.US) }
        return BrokerInventorySnapshot(holdings, positions, now).also { brokerInventorySnapshot = it }
    }

    private suspend fun reconcilePositionWithBroker(
        token: String,
        position: ManagedPositionEntity,
        preferredMark: Double?,
        force: Boolean
    ): ManagedPositionEntity? {
        if (position.side != "LONG" || position.product != "CNC") return position
        val inventory = brokerInventory(token, force)
        val symbol = position.symbol.uppercase(Locale.US)
        val holdingQty = inventory.holdings[symbol]?.quantity?.coerceAtLeast(0) ?: 0
        val brokerPosition = inventory.positions[symbol]
        val intradayQty = brokerPosition?.quantity?.coerceAtLeast(0) ?: 0
        val visibleQty = max(holdingQty, intradayQty)
        if (visibleQty >= position.quantity) return position

        val removedQty = (position.quantity - visibleQty).coerceAtLeast(0)
        if (removedQty <= 0) return position
        val signalExit = preferredMark ?: latestMultifyExitPrice(position.symbol, position.openedAtMs)
        val exit = brokerPosition?.debitPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: signalExit
            ?: runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = position.symbol).requirePayload("Quote ${position.symbol}").lastPrice
            }.getOrNull()
            ?: position.lastPrice
        val smartStatus = position.smartOrderId?.let { id ->
            runCatching {
                apiFactory.groww.smartOrderStatus(
                    bearer(token), segment = "CASH", smartOrderType = "GTT", smartOrderId = id
                ).requirePayload("GTT status on broker reconciliation ${position.symbol}").status.uppercase(Locale.US)
            }.getOrNull()
        }
        val gttExecuted = smartStatus in setOf("TRIGGERED", "COMPLETED")
        val exitReason = when {
            gttExecuted && position.smartOrderMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "TRAILING_GTT_EXIT"
            gttExecuted && position.smartOrderMode == HoldingSmartOrderPolicy.MODE_RECOVERY_GTT -> "RECOVERY_GTT_100_NET_EXIT"
            visibleQty == 0 -> "MANUAL_BROKER_EXIT"
            else -> "MANUAL_BROKER_PARTIAL_EXIT"
        }
        recordExternalClose(position, removedQty, exit, exitReason)
        brokerInventorySnapshot = null
        if (visibleQty <= 0) {
            recentlyClosedSymbolsAtMs[position.symbol.uppercase(Locale.US)] = System.currentTimeMillis()
            runCatching { cancelKnownPositionGtt(token, position.copy(smartOrderStatus = smartStatus), "POSITION_CLOSED", clearIfTerminal = true) }
            managedDao.deletePosition(position.id)
            lastTrailStateAuditAtMs.remove(position.id)
            lastSmartOrderStatusAtMs.remove(position.id)
            auditLogger.log("HOLDING", "EXTERNAL_LONG_CLOSED", mapOf(
                "symbol" to position.symbol,
                "qty" to removedQty,
                "exit" to exit,
                "exit_reason" to exitReason,
                "gtt_id" to position.smartOrderId,
                "gtt_status" to smartStatus,
                "active_gtt_cleanup_requested" to true
            ))
            return null
        }
        var updated = position.copy(
            quantity = visibleQty,
            lastEvaluatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(updated)
        if (!updated.smartOrderId.isNullOrBlank() && updated.smartOrderTriggerPrice != null &&
            updated.smartOrderDirection in setOf("UP", "DOWN") && smartStatus !in setOf("TRIGGERED", "COMPLETED")
        ) {
            updated = runCatching {
                modifyPositionGtt(
                    token,
                    updated,
                    updated.smartOrderTriggerPrice,
                    updated.smartOrderDirection ?: "UP",
                    updated.smartOrderMode
                )
            }.getOrDefault(updated)
        }
        auditLogger.log("HOLDING", "EXTERNAL_LONG_REDUCED", mapOf(
            "symbol" to position.symbol, "removed_qty" to removedQty, "remaining_qty" to visibleQty, "exit" to exit,
            "gtt_quantity_resync" to !updated.smartOrderId.isNullOrBlank()
        ))
        return updated
    }

    private suspend fun latestMultifyExitPrice(symbol: String, openedAtMs: Long): Double? =
        dao.allNow().asReversed().firstNotNullOfOrNull { event ->
            if (event.receivedAtMs < openedAtMs || !event.symbol.equals(symbol, true) || event.signalType != SignalType.BOOK_PROFIT.name) null
            else parser.parse(event.title, event.text, event.bigText).exitPrice
        }

    private suspend fun recordExternalClose(position: ManagedPositionEntity, quantity: Int, exit: Double, reason: String) {
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
                sourceEventId = position.sourceEventId, openOrderId = position.openOrderId, closeOrderId = position.smartOrderId ?: "EXTERNAL",
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

    private suspend fun syncPortfolioHoldings(
        token: String,
        inventory: BrokerInventorySnapshot,
        activeGtts: Map<String, List<SmartOrderPayload>>
    ): Set<String> {
        val blockedSymbols = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        val existingBySymbol = managedDao.openPositions().groupBy { it.symbol.uppercase(Locale.US) }

        inventory.holdings.values.forEach { holding ->
            val symbol = holding.tradingSymbol.trim().uppercase(Locale.US)
            val quantity = holding.quantity.coerceAtLeast(0)
            val averagePrice = holding.averagePrice
            if (symbol.isBlank() || quantity <= 0) return@forEach
            if (!averagePrice.isFinite() || averagePrice <= 0.0) {
                logPortfolioConflict(symbol, "Broker holding has no valid average price; refusing to invent a trailing-stop basis.")
                blockedSymbols += symbol
                return@forEach
            }

            val existingRows = existingBySymbol[symbol].orEmpty()
            if (existingRows.size > 1) {
                blockedSymbols += symbol
                logPortfolioConflict(symbol, "Multiple local managed records exist for this stock; refusing to merge quantities automatically.", mapOf(
                    "local_position_ids" to existingRows.map { it.id },
                    "broker_quantity" to quantity
                ))
                return@forEach
            }
            val existing = existingRows.singleOrNull()
            val activeForSymbol = activeGtts[symbol].orEmpty()
            val conflictingOrders = activeForSymbol.filter { order ->
                existing?.smartOrderId.isNullOrBlank() || order.smartOrderId != existing?.smartOrderId
            }
            if (conflictingOrders.isNotEmpty()) {
                blockedSymbols += symbol
                logPortfolioConflict(symbol, "A broker-side active GTT already exists outside the local position record. It is left untouched to prevent duplicate sell orders.", mapOf(
                    "broker_quantity" to quantity,
                    "local_position_id" to existing?.id,
                    "local_gtt_id" to existing?.smartOrderId,
                    "active_gtt_ids" to conflictingOrders.map { it.smartOrderId },
                    "active_gtt_directions" to conflictingOrders.map { it.triggerDirection },
                    "active_gtt_quantities" to conflictingOrders.map { it.quantity }
                ))
                return@forEach
            }

            if (existing == null) {
                if (managedDao.tradesSince(now - 86_400_000L).any {
                    it.symbol.equals(symbol, true) && it.side == "LONG"
                }) return@forEach
                val closedAt = recentlyClosedSymbolsAtMs[symbol]
                if (closedAt != null) {
                    if (now - closedAt < PORTFOLIO_REIMPORT_COOLDOWN_MS) return@forEach
                    recentlyClosedSymbolsAtMs.remove(symbol, closedAt)
                }
                val insertedId = managedDao.insertPosition(
                    ManagedPositionEntity(
                        engine = ENGINE_PORTFOLIO_HOLDING,
                        symbol = symbol,
                        product = "CNC",
                        side = "LONG",
                        quantity = quantity,
                        entryPrice = averagePrice,
                        stopPrice = HardStopLossPolicy.stopPrice(averagePrice),
                        targetPrice = null,
                        strategy = "Existing Groww portfolio holding · common hard -5% floor + shared fixed stepped trailing sell logic",
                        regime = "PORTFOLIO_LONG",
                        confidence = 1.0,
                        sourceEventId = null,
                        openOrderId = "EXTERNAL_GROWW_HOLDING",
                        openReferenceId = stableRef("PFH", "$symbol-${holding.isin}"),
                        openedAtMs = now,
                        lastPrice = averagePrice,
                        maxFavourablePrice = averagePrice,
                        maxAdversePrice = averagePrice,
                        lastEvaluatedAtMs = now,
                        trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
                        trailingLastRatchetPrice = 0.0,
                        smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE
                    )
                )
                auditLogger.log("PORTFOLIO_MONITOR", "EXISTING_HOLDING_IMPORTED", mapOf(
                    "symbol" to symbol,
                    "position_id" to insertedId,
                    "quantity" to quantity,
                    "average_price" to averagePrice,
                    "existing_active_gtt" to false,
                    "sell_logic" to "shared_fixed_stepped_trailing_and_overnight_recovery"
                ))
                return@forEach
            }

            val quantityChanged = existing.quantity != quantity
            val basisChanged = abs(existing.entryPrice - averagePrice) > PORTFOLIO_AVERAGE_PRICE_EPSILON
            if (!quantityChanged && !basisChanged) return@forEach

            var working = existing
            if (!working.smartOrderId.isNullOrBlank()) {
                working = cancelKnownPositionGtt(token, working, "PORTFOLIO_QUANTITY_OR_COST_BASIS_CHANGED", clearIfTerminal = false)
                if (!working.smartOrderId.isNullOrBlank()) {
                    blockedSymbols += symbol
                    logPortfolioConflict(symbol, "Could not safely cancel the old GTT before rebasing portfolio quantity/cost basis.", mapOf(
                        "position_id" to existing.id,
                        "old_quantity" to existing.quantity,
                        "broker_quantity" to quantity,
                        "old_average_price" to existing.entryPrice,
                        "broker_average_price" to averagePrice,
                        "gtt_id" to working.smartOrderId,
                        "gtt_status" to working.smartOrderStatus
                    ))
                    return@forEach
                }
            }

            val referencePrice = working.lastPrice.takeIf { it.isFinite() && it > 0.0 } ?: averagePrice
            val updated = working.copy(
                quantity = quantity,
                entryPrice = averagePrice,
                stopPrice = HardStopLossPolicy.stopPrice(averagePrice),
                targetPrice = null,
                strategy = if (working.strategy.contains("aggregated Groww portfolio", ignoreCase = true)) working.strategy
                    else working.strategy + " · aggregated Groww portfolio",
                maxFavourablePrice = max(averagePrice, referencePrice),
                maxAdversePrice = min(averagePrice, referencePrice),
                openedAtMs = now,
                lastEvaluatedAtMs = now,
                trailingArmPct = SteppedTrailingPolicy.ARM_TRIGGER_PCT,
                trailingLastRatchetPrice = 0.0,
                smartOrderId = null,
                smartOrderMode = HoldingSmartOrderPolicy.MODE_NONE,
                smartOrderTriggerPrice = null,
                smartOrderDirection = null,
                smartOrderStatus = null,
                smartOrderUpdatedAtMs = now
            )
            managedDao.updatePosition(updated)
            lastTrailStateAuditAtMs.remove(updated.id)
            lastSmartOrderStatusAtMs.remove(updated.id)
            auditLogger.log("PORTFOLIO_MONITOR", "HOLDING_REBASED_TO_BROKER_PORTFOLIO", mapOf(
                "symbol" to symbol,
                "position_id" to updated.id,
                "old_quantity" to existing.quantity,
                "broker_quantity" to quantity,
                "old_average_price" to existing.entryPrice,
                "broker_average_price" to averagePrice,
                "old_gtt_cancelled" to !existing.smartOrderId.isNullOrBlank()
            ))
        }
        return blockedSymbols
    }

    private suspend fun portfolioMonitorCount(inventory: BrokerInventorySnapshot? = brokerInventorySnapshot): Int {
        val localSymbols = managedDao.openPositions().map { it.symbol.uppercase(Locale.US) }
        val brokerSymbols = inventory?.holdings?.values
            ?.filter { it.quantity > 0 && it.tradingSymbol.isNotBlank() }
            ?.map { it.tradingSymbol.uppercase(Locale.US) }
            .orEmpty()
        return (localSymbols + brokerSymbols).distinct().size.also { activePortfolioMonitorCount = it }
    }

    suspend fun monitorManagedPositions(): Int {
        val token = ensureToken() ?: return portfolioMonitorCount()
        val marketOpen = marketSession() == "OPEN"
        val now = System.currentTimeMillis()
        if (!marketOpen && now - lastClosedSessionReconcileAtMs < CLOSED_SESSION_RECONCILE_MS) {
            return portfolioMonitorCount()
        }
        if (!marketOpen) lastClosedSessionReconcileAtMs = now

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
            blockedSymbols += syncPortfolioHoldings(token, inventory, activeGttsResult.getOrThrow())
        } else {
            val error = activeGttsResult.exceptionOrNull()
            auditLogger.log("PORTFOLIO_MONITOR", "ACTIVE_GTT_DISCOVERY_ERROR", mapOf(
                "message" to (error?.message ?: error?.javaClass?.simpleName ?: "Active GTT discovery failed"),
                "note" to "New portfolio positions will not be adopted until active broker orders can be checked safely."
            ))
            // If broker GTT discovery is unavailable, do not create/modify sell orders for an
            // unprotected local row: an externally created GTT might already cover the same shares.
            managedDao.openPositions().filter { it.smartOrderId.isNullOrBlank() }
                .forEach { blockedSymbols += it.symbol.uppercase(Locale.US) }
        }

        val open = managedDao.openPositions()
        val reconciled = mutableListOf<ManagedPositionEntity>()
        for (position in open) {
            try {
                if (position.side != "LONG") {
                    if (marketOpen && position.symbol.uppercase(Locale.US) !in blockedSymbols) closeLegacyNonLong(token, position)
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

    private suspend fun monitorLongHolding(position: ManagedPositionEntity, token: String, ltp: Double) {
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
        val desiredTrailPrice = desiredTrailGainPct?.let { SteppedTrailingPolicy.stopPrice(position.entryPrice, it) }
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
                    "COMMON_HARD_STOP_MINUS_5_LOCAL_FALLBACK"
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
                                "COMMON_HARD_STOP_MINUS_5_STALE_GTT_FALLBACK"
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
                                "COMMON_HARD_STOP_MINUS_5_GTT_FAILED_FALLBACK"
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
                        "COMMON_HARD_STOP_MINUS_5_LOCAL_FALLBACK"
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
                desiredMode == HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT && previousProtected == null -> "HARD_STOP_MINUS_5_ARMED"
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
        mode: String
    ): ManagedPositionEntity {
        require(position.side == "LONG" && position.product == "CNC") { "GTT protection is only for tracked CNC LONG holdings" }
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
        val now = System.currentTimeMillis()
        val updated = position.copy(
            smartOrderId = payload.smartOrderId,
            smartOrderMode = mode,
            smartOrderTriggerPrice = roundedTrigger,
            smartOrderDirection = direction,
            smartOrderStatus = payload.status.ifBlank { "ACTIVE" },
            smartOrderUpdatedAtMs = now,
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
        if (!refreshed.smartOrderStatus.equals("COMPLETED", true)) return refreshed

        val inventory = runCatching { brokerInventory(token, force = true) }.getOrNull()
        val brokerExit = inventory?.positions?.get(refreshed.symbol.uppercase(Locale.US))?.debitPrice
            ?.takeIf { it.isFinite() && it > 0.0 }
        val exit = brokerExit ?: refreshed.smartOrderTriggerPrice ?: refreshed.lastPrice
        val reason = when (refreshed.smartOrderMode) {
            HoldingSmartOrderPolicy.MODE_HARD_STOP_GTT -> "COMMON_HARD_STOP_MINUS_5_GTT_EXIT"
            HoldingSmartOrderPolicy.MODE_TRAILING_GTT -> "TRAILING_GTT_EXIT"
            HoldingSmartOrderPolicy.MODE_RECOVERY_GTT -> "RECOVERY_GTT_100_NET_EXIT"
            else -> "GTT_EXIT"
        }
        recordExternalClose(refreshed, refreshed.quantity, exit, reason)
        runCatching { cancelKnownPositionGtt(token, refreshed, "GTT_COMPLETED_CLEANUP", clearIfTerminal = true) }
        recentlyClosedSymbolsAtMs[refreshed.symbol.uppercase(Locale.US)] = System.currentTimeMillis()
        managedDao.deletePosition(refreshed.id)
        brokerInventorySnapshot = null
        lastTrailStateAuditAtMs.remove(refreshed.id)
        lastSmartOrderStatusAtMs.remove(refreshed.id)
        auditLogger.log("HOLDING", "GTT_LONG_CLOSED", mapOf(
            "symbol" to refreshed.symbol,
            "qty" to refreshed.quantity,
            "exit" to exit,
            "exit_reason" to reason,
            "gtt_id" to refreshed.smartOrderId,
            "gtt_mode" to refreshed.smartOrderMode,
            "gtt_status" to refreshed.smartOrderStatus,
            "active_gtt_cleanup_requested" to true
        ))
        return null
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
        if (!force && now - last < TRAIL_STATE_LOG_INTERVAL_MS) return
        lastTrailStateAuditAtMs[position.id] = now
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
            "averaging_down_disabled" to true,
            "local_fallback_only_if_no_active_gtt" to true
        ))
    }

    private fun roundSmartOrderPrice(price: Double): Double =
        kotlin.math.ceil((price - 1e-9) / GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES) * GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES

    private fun smartPrice(price: Double): String = String.format(Locale.US, "%.2f", price)

    private suspend fun closeLegacyNonLong(token: String, position: ManagedPositionEntity) {
        if (marketSession() != "OPEN") return
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
        val open = managedDao.openPositions().filter { it.side == "LONG" }
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
        val open = managedDao.openPositions().filter { it.side == "LONG" }
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
        val reports: Int
    ) {
        val total: Int get() = learningCalls + researchSnapshots + forecasts + champions + reports
    }

    /**
     * Restores learning-only state from a Multify diagnostics ZIP after a debug-signature migration.
     * Trading state is intentionally excluded: no holdings, orders, execution samples, credentials,
     * ARM flags, broker state or settings are imported.
     */
    suspend fun restoreLearningFromLogs(uri: Uri): LearningRestoreResult {
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

        entries["forecast_research_snapshots.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<ForecastResearchEntity>::class.java).orEmpty()
            val existingKeys = learningDao.allForecastResearch().map {
                "${it.researchDate}|${it.scanAtMs}|${it.symbol.uppercase(Locale.US)}|${it.rank}"
            }.toHashSet()
            imported.forEach { row ->
                if (row.researchDate.isBlank() || row.symbol.isBlank() || row.entryPrice <= 0.0) return@forEach
                val key = "${row.researchDate}|${row.scanAtMs}|${row.symbol.uppercase(Locale.US)}|${row.rank}"
                if (key in existingKeys) return@forEach
                learningDao.insertForecastResearch(row.copy(id = 0, symbol = row.symbol.uppercase(Locale.US)))
                existingKeys += key
                restoredResearch++
            }
        }

        entries["long_forecasts.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<LongForecastEntity>::class.java).orEmpty()
            imported.forEach { row ->
                if (row.forecastDate.isBlank() || row.symbol.isBlank() || row.entryPrice <= 0.0) return@forEach
                val existing = learningDao.longForecastsForDate(row.forecastDate)
                    .firstOrNull { it.symbol.equals(row.symbol, true) }
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

        entries["long_champions.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<ForecastChampionEntity>::class.java).orEmpty()
            imported.forEach { row ->
                if (row.key.isBlank()) return@forEach
                learningDao.upsertForecastChampion(row)
                restoredChampions++
            }
        }

        entries["research.json"]?.let { json ->
            val imported = gson.fromJson(json, Array<com.multify.traderpro.data.local.ResearchReportEntity>::class.java).orEmpty()
            imported.forEach { row ->
                if (row.reportDate.isBlank()) return@forEach
                learningDao.insertResearchReport(row.copy(id = 0))
                restoredReports++
            }
        }

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
            "fixed_snapshot_restored_or_captured" to restoredFixedSnapshot,
            "trading_state_restored" to false,
            "credentials_restored" to false
        ))
        return LearningRestoreResult(
            learningCalls = restoredCalls,
            researchSnapshots = restoredResearch,
            forecasts = restoredForecasts,
            champions = restoredChampions,
            reports = restoredReports
        )
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
                add("api_usage_snapshot.json", GrowwApiUsageLedger.snapshot())
                add("groww_recovery_state.json", recoveryGate.status())
                add("telemetry_metadata.json", mapOf(
                    "exported_at_ms" to System.currentTimeMillis(),
                    "async_audit_dropped_total" to auditLogger.asyncDroppedCount(),
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
        return LogExportResult(if (fixedSnapshot != null) 19 else 18, signals.size, trades.size)
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
    ): OrderPayload = submitMarketOrder(token, symbol, "BUY", qty, "CNC", reference, notificationStartedNs)

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

    private fun disconnectedDashboard(settings: AppSettings, fixedSnapshot: FixedEngineSnapshot? = null) = DashboardDto(
        armed = settings.armEffective, halted = settings.safetyHalt, marketSession = marketSession(),
        asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        broker = BrokerStatusDto(configured = hasBrokerCredentials(), connected = false, detail = "Refresh & Authenticate required"),
        risk = RiskStatusDto(maxExposure = settings.holdingBudgetRupees.toDouble(), riskMode = if (settings.safetyHalt) "HALTED" else "DISARMED"),
        fixedSnapshot = fixedSnapshotDto(fixedSnapshot)
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
        private const val RESEARCH_BACKFILL_SYMBOL_PACING_MS = 300L
        private const val RESEARCH_BACKFILL_SYMBOL_BATCH = 10
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
            "fixed_engine_snapshot.json"
        )
        private const val MAX_RESTORE_ENTRY_BYTES = 32L * 1024L * 1024L
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
