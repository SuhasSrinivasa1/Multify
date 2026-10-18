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
import com.multify.traderpro.data.local.LongForecastEntity
import com.multify.traderpro.data.local.LearningCallEntity
import com.multify.traderpro.data.local.LearningDao
import com.multify.traderpro.data.local.ManagedPositionEntity
import com.multify.traderpro.data.local.ManagedTradeDao
import com.multify.traderpro.data.local.ManagedTradeEntity
import com.multify.traderpro.data.local.PriceObservationEntity
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
import com.multify.traderpro.data.network.GrowwApiFactory
import com.multify.traderpro.data.network.GrowwHolding
import com.multify.traderpro.data.network.GrowwPosition
import com.multify.traderpro.data.network.LearningStatsDto
import com.multify.traderpro.data.network.OrderCreateRequest
import com.multify.traderpro.data.network.OrderPayload
import com.multify.traderpro.data.network.PositionDto
import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.RecentDecisionDto
import com.multify.traderpro.data.network.ResearchDto
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
import com.multify.traderpro.engine.ForecastQualityDecision
import com.multify.traderpro.engine.ForecastQualityPolicy
import com.multify.traderpro.engine.LearnedTrailingPolicy
import com.multify.traderpro.engine.LocalStrategyEngine
import com.multify.traderpro.engine.MultifyFamilyDecision
import com.multify.traderpro.engine.MultifyFamilyPolicy
import com.multify.traderpro.engine.MultifyReverseEngineering
import com.multify.traderpro.engine.StrategyEvaluation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
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
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.floor
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
    private val parser: NotificationParser,
    private val nseSymbols: NseSymbolRepository,
    private val auditLogger: AuditLogger,
    @ApplicationContext private val appContext: Context
) {
    val settings: Flow<AppSettings> = preferences.settings
    val recentEvents: Flow<List<SignalEventEntity>> = dao.observeRecent(100)

    @Volatile private var historicalSeedEnsured = false
    @Volatile private var armHotState: ArmHotState? = null
    private var lastForecastScanAtMs = 0L
    private var lastForecastOutcomeMonitorAtMs = 0L
    private var brokerInventorySnapshot: BrokerInventorySnapshot? = null
    private var lastClosedSessionReconcileAtMs = 0L
    private var lastOpenBrokerRefreshAtMs = 0L
    private var lastArmHotAuditAtMs = 0L
    private var lastMfeBackfillAtMs = 0L
    private var lastArmHotAuditBalance = Double.NaN
    private var lastArmHotAuditArmed: Boolean? = null
    private val oneMinuteAtrCache = mutableMapOf<String, AtrCacheEntry>()
    private val closePositionMutexes = ConcurrentHashMap<Long, Mutex>()

    private data class AtrCacheEntry(val atr: Double, val atMs: Long)

    private data class ForecastCandidate(
        val symbol: String,
        val evaluation: StrategyEvaluation,
        val quality: ForecastQualityDecision,
        val family: MultifyFamilyDecision,
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

    private data class ArmHotState(
        val token: String,
        val settings: AppSettings,
        val cncBalance: Double,
        val warmedAtMs: Long
    )

    suspend fun ensureHistoricalSeed() {
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

        return LearningStatsDto(
            rollingTradingDays = keepDates.size,
            rollingCalls = values.size,
            longAveragePct = AdaptiveLearningMath.rollingMean(values, fallback),
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
        val completed = all.filter { it.status in setOf("TARGET_HIT", "MISSED") }
        val dates = completed.mapNotNull { runCatching { LocalDate.parse(it.forecastDate) }.getOrNull() }
            .distinct().sortedDescending()
        val keepDates = dates.take(30).map { it.toString() }.toSet()
        val rolling = completed.filter { it.forecastDate in keepDates }
        val values = rolling.map { it.maxFavourablePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val today = LocalDate.now(INDIA).toString()
        val todayRows = all.filter { it.forecastDate == today }
        val researchSessions = learningDao.forecastResearchDatesForCohort(RESEARCH_COHORT_V7)
            .take(RESEARCH_SESSION_GOAL)
            .size
        return ForecastLearningStatsDto(
            rollingTradingDays = keepDates.size,
            completedForecasts = rolling.size,
            longAveragePct = AdaptiveLearningMath.rollingMean(values, 0.0),
            todayTargetAveragePct = todayRows.map { it.targetPct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            targetFloorPct = ForecastQualityPolicy.MIN_TARGET_PCT,
            researchSessionsCaptured = researchSessions,
            researchSessionGoal = RESEARCH_SESSION_GOAL,
            researchCohort = RESEARCH_COHORT_V7,
            researchPointerCount = MultifyFamilyPolicy.pointerNames.size,
            researchFamilyCount = MultifyFamilyPolicy.families.size
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
            armHotState = null
        }
        auditLogger.log("SETTINGS", if (enabled) "ARM_ENABLED" else "ARM_DISABLED")
    }

    suspend fun resetHalt() {
        preferences.setSafetyHalt(false)
        preferences.setArm(false)
        armHotState = null
        auditLogger.log("SAFETY", "HALT_RESET_ARM_OFF")
    }

    suspend fun forceLocalDisarm() {
        preferences.setSafetyHalt(true)
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

    suspend fun refreshDashboard(): DashboardDto {
        val settings = preferences.settings.first()
        val token = secretStore.getAccessToken()
        if (!settings.brokerAuthenticated || token.isNullOrBlank()) return disconnectedDashboard(settings)
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
                forecasts = forecasts, forecastChampions = champions,
                research = research,
                health = EngineHealthDto(
                    listener = if (listenerAge <= 60_000L) "HEALTHY" else "UNHEALTHY",
                    broker = "CONNECTED",
                    marketData = if (dataAge <= 15_000L) "FRESH" else "STALE",
                    symbolMaster = if (nseSymbols.isStale()) "STALE" else "FRESH",
                    foregroundService = if (heartbeatAge <= 30_000L) "RUNNING" else "NOT_HEARTBEATING",
                    lastNotificationAtMs = settings.lastNotificationAtMs,
                    reconnectCount = settings.listenerReconnectCount,
                    lastReconnectAtMs = settings.lastListenerReconnectAtMs,
                    lastEventProcessingLatencyMs = settings.lastEventProcessingLatencyMs,
                    lastOrderDispatchPrepMicros = settings.lastOrderDispatchPrepMicros,
                    lastBrokerAckLatencyMs = settings.lastBrokerAckLatencyMs,
                    marketDataAgeMs = dataAge
                ),
                positions = snapshot.positions,
                recentDecisions = (snapshot.recentDecisions + events).take(10)
            )
        } catch (t: Throwable) {
            if (t.message?.contains("401") == true || t.message?.contains("author", true) == true) preferences.updateAuthState(authenticated = false)
            disconnectedDashboard(preferences.settings.first()).copy(
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

        val symbol = parsed.symbol
        if (!symbol.isNullOrBlank() && !nseSymbols.isKnown(symbol) && !nseSymbols.isStale()) {
            dao.updateForwarding(eventId, "IGNORED", "SYMBOL_NOT_IN_NSE_MASTER", "$symbol is not in the current NSE equity master.", null)
            return
        }

        // v7 priority rule: when ARM is already hot, dispatch the real BUY before doing
        // learning/database enrichment. Learning is still guaranteed in finally.
        val hot = armHotState?.takeIf { System.currentTimeMillis() - it.warmedAtMs <= ARM_HOT_STATE_MAX_AGE_MS }
        val settings = hot?.settings ?: preferences.settings.first()

        when (parsed.type) {
            SignalType.TRADE_RELEASE -> {
                if (!settings.armEffective) {
                    recordLiveBuy(eventId, parsed)
                    val observed = multifyReferenceEntry(parsed)
                    dao.updateForwarding(
                        eventId, "ANALYZED", "LONG_CALL_CAPTURED",
                        "Multify LONG call captured${observed?.let { " at reference ₹${fmt(it)}" }.orEmpty()}. ARM is off, so no order was placed.",
                        null
                    )
                    return
                }
                val token = hot?.token ?: ensureToken()
                if (token == null) {
                    recordLiveBuy(eventId, parsed)
                    dao.updateForwarding(eventId, "CAPTURED", "AUTH_REQUIRED", "Signal and Multify learning stored. Authenticate Groww to enable ARM.", null)
                    return
                }
                try {
                    handleMultifyBuy(eventId, parsed, token, settings, notificationStartedNs)
                } finally {
                    recordLiveBuy(eventId, parsed)
                }
            }

            SignalType.BOOK_PROFIT -> {
                val closedLearningCall = if (!symbol.isNullOrBlank()) {
                    val eventAtMs = dao.byId(eventId)?.receivedAtMs ?: System.currentTimeMillis()
                    recordLiveExit(eventId, symbol, parsed.exitPrice, parsed.returnsPct, eventAtMs)
                } else null
                val token = hot?.token ?: ensureToken()
                if (token != null && closedLearningCall != null) {
                    runCatching { captureMultifyMfe(closedLearningCall, token) }
                        .onFailure { auditLogger.log("LEARNING", "MULTIFY_MFE_LIVE_ERROR", mapOf("symbol" to (symbol ?: ""), "message" to (it.message ?: it.javaClass.simpleName))) }
                }
                handleMultifyExit(eventId, parsed)
            }

            else -> Unit
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
        val sizing = CashAwareCncSizing.size(settings.holdingBudgetRupees.toDouble(), available, referencePrice)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        require(qty > 0) { "Safe CNC buying power cannot fund one share of $symbol after RMS reserve" }

        val ref = stableRef("MLH", "$eventId-$symbol")
        val order = try {
            placeCncBuy(token, symbol, qty, ref, notificationStartedNs)
        } catch (t: Throwable) {
            auditLogger.log("EXECUTION", "CNC_BUY_FAST_PATH_FAILED", mapOf(
                "symbol" to symbol,
                "quantity" to qty,
                "hot_path" to (hot != null),
                "message" to (t.message ?: t.javaClass.simpleName)
            ))
            throw t
        }
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to symbol, "available_cnc" to available, "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to referencePrice,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty,
            "hot_path" to (hot != null)
        ))
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: referencePrice
        val investedAmount = entry * filled
        val trailTriggerPrice = entry * (1.0 + LearnedTrailingPolicy.ARM_TRIGGER_PCT / 100.0)
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_MULTIFY_HOLDING, symbol = symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = entry * (1.0 - HOLDING_FAILSAFE_STOP_PCT),
                targetPrice = trailTriggerPrice,
                strategy = "Multify ARM holding · v7 fixed staircase trail",
                regime = "HOLDING_LONG", confidence = 1.0, sourceEventId = eventId,
                openOrderId = order.growwOrderId, openReferenceId = ref, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry, capitalDeployed = investedAmount, campaignBudget = capital,
                trailingArmPct = LearnedTrailingPolicy.ARM_TRIGGER_PCT, trailingLastRatchetPrice = 0.0
            )
        )
        armHotState = hot?.copy(
            cncBalance = max(0.0, hot.cncBalance - entry * filled),
            warmedAtMs = System.currentTimeMillis()
        )
        dao.updateForwarding(
            eventId, "DELIVERED", "CNC_LONG_BOUGHT",
            "ARM bought $filled $symbol into holdings @ ₹${fmt(entry)}. Trail arms at +0.55%, locks +0.50%, then ratchets +0.10% for every +0.10% high-water step.",
            null
        )
        auditLogger.log("HOLDING", "MULTIFY_LONG_OPENED", mapOf(
            "symbol" to symbol, "qty" to filled, "entry" to entry,
            "invested_amount" to investedAmount,
            "trail_trigger_pct" to LearnedTrailingPolicy.ARM_TRIGGER_PCT,
            "initial_lock_pct" to LearnedTrailingPolicy.INITIAL_LOCK_PCT,
            "ratchet_step_pct" to LearnedTrailingPolicy.RATCHET_STEP_PCT,
            "dispatch_sla_ms" to 100
        ))
    }

    private suspend fun handleMultifyExit(
        eventId: Long,
        signal: ParsedSignal
    ) {
        val symbol = signal.symbol ?: error("Book-profit signal has no symbol")
        val holding = managedDao.openPosition(ENGINE_MULTIFY_HOLDING, symbol)
        if (holding == null) {
            dao.updateForwarding(
                eventId, "ANALYZED", "MULTIFY_EXIT_LEARNING_ONLY",
                "Multify Book Profit was recorded for learning. No app-owned Multify holding was open.",
                null
            )
            return
        }
        dao.updateForwarding(
            eventId, "ANALYZED", "MULTIFY_EXIT_LEARNING_ONLY_TRAIL_CONTINUES",
            "Multify Book Profit was recorded for MFE/learning only. v7 does not sell on Multify Book Profit; the app-owned holding remains governed by the fixed staircase trailing stop.",
            null
        )
        auditLogger.log("HOLDING", "MULTIFY_BOOK_PROFIT_IGNORED_FOR_EXECUTION", mapOf(
            "symbol" to symbol,
            "position_id" to holding.id,
            "entry" to holding.entryPrice,
            "last_price" to holding.lastPrice,
            "stop_price" to holding.stopPrice,
            "high_water" to holding.maxFavourablePrice
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

    suspend fun generateDailyForecasts(force: Boolean = false): List<ForecastDto> {
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

        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }
        val recentDates = calls.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        val rolling = calls.filter { it.callDate in recentDates }
        val frequency = rolling.groupingBy { it.symbol }.eachCount()
        val recentSymbols = frequency.entries.sortedByDescending { it.value }.take(20).map { it.key }
        val dnaSymbols = MultifyReverseEngineering.positiveCases.map { it.symbol }
        val master = nseSymbols.symbols()
        val slot = (now.hour * 60 + now.minute) / 30
        val rotating = if (master.isEmpty()) emptyList() else {
            val seed = kotlin.math.abs(today.hashCode() + slot * 977)
            (0 until min(28, master.size)).map { i -> master[Math.floorMod(seed + i * 131, master.size)] }
        }
        val universe = (dnaSymbols + recentSymbols + rotating).distinct().take(48)
        val evaluated = mutableListOf<Pair<String, StrategyEvaluation>>()
        for (symbol in universe) {
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

        val breadth = evaluated.mapNotNull { it.second.features.dayChangePct }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val marketRegime = when {
            breadth >= .50 -> "BREADTH_BULL"
            breadth <= -.50 -> "BREADTH_WEAK"
            else -> "BREADTH_NEUTRAL"
        }
        val champions = learningDao.allForecastChampions().associateBy { it.key }
        val learned = learningStats()
        val priorForecasts = learningDao.allLongForecasts().filter {
            it.forecastDate < today && it.status in setOf("TARGET_HIT", "MISSED")
        }
        val priorMfe = priorForecasts.map { it.maxFavourablePct }
            .filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val targetPct = ForecastQualityPolicy.adaptiveTargetPct(learned.longAveragePct, priorMfe)

        val have = existing.map { it.symbol }.toSet()
        val priorResearchDates = learningDao.forecastResearchDatesForCohort(RESEARCH_COHORT_V7)
            .filter { it < today }
            .take(RESEARCH_SESSION_GOAL)
            .toSet()
        val priorResearch = if (priorResearchDates.isEmpty()) emptyList() else {
            learningDao.allForecastResearchForCohort(RESEARCH_COHORT_V7)
                .filter { it.researchDate in priorResearchDates && it.outcomeUpdatedAtMs != null }
                .groupBy { it.researchDate + "|" + it.symbol }
                .mapNotNull { (_, rows) -> rows.minByOrNull { it.scanAtMs } }
        }

        fun researchEvidenceBonus(evaluation: StrategyEvaluation, family: String): Double {
            val xs = priorResearch.filter { it.strategyFamily == family && it.regime == evaluation.regime }
            if (xs.size < MIN_RESEARCH_EVIDENCE_ROWS) return 0.0
            val hitRate = xs.count { it.outcomeTargetHit == true }.toDouble() / xs.size
            val avgMfe = xs.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val familyMatchRate = xs.count { it.multifyMatched }.toDouble() / xs.size
            val allMatchRate = if (priorResearch.isEmpty()) 0.0 else priorResearch.count { it.multifyMatched }.toDouble() / priorResearch.size
            val hitComponent = (hitRate - 0.50) * 0.10
            val mfeComponent = if (targetPct > 0.0)
                (((avgMfe - targetPct) / targetPct).coerceIn(-1.0, 1.0)) * 0.04
            else 0.0
            val multifyLift = (familyMatchRate - allMatchRate) * 0.20
            return (hitComponent + mfeComponent + multifyLift).coerceIn(-0.08, 0.12)
        }

        val ranked = evaluated.map { row ->
            val quality = ForecastQualityPolicy.evaluate(row.second, targetPct, marketRegime)
            val family = MultifyFamilyPolicy.classify(row.second.features, marketRegime)
            val key = championKey(marketRegime, row.second.regime, row.second.strategy, family.family)
            val champion = champions[key]
            val championWinRate = champion?.takeIf { it.sampleCount > 0 }?.let { it.wins.toDouble() / it.sampleCount } ?: 0.0
            val championBonus = when {
                champion?.frozen == true && championWinRate >= .75 -> .08
                champion != null && champion.sampleCount >= 3 && championWinRate >= .75 -> .04
                else -> 0.0
            }
            val frequencyBonus = min(.04, (frequency[row.first] ?: 0) * .004)
            val dnaBonus = if (row.first in dnaSymbols) .08 else 0.0
            val qualityBonus = ForecastQualityPolicy.rankingBonus(row.second, quality.volatilityCapacityPct, targetPct, quality.tier)
            val familyStrengthBonus = ((family.scores[family.family] ?: 0.0) * 0.10).coerceAtMost(0.10)
            val researchBonus = researchEvidenceBonus(row.second, family.family)
            ForecastCandidate(
                symbol = row.first,
                evaluation = row.second,
                quality = quality,
                family = family,
                score = (
                    row.second.confidence +
                    row.second.directionalScore.coerceAtLeast(0.0) * 0.10 +
                    championBonus + frequencyBonus + dnaBonus + qualityBonus + familyStrengthBonus + researchBonus
                ).coerceIn(0.01, .99),
                championKey = key
            )
        }.sortedWith(
            compareByDescending<ForecastCandidate> {
                when (it.quality.tier) { "PRIMARY" -> 3; "SECONDARY" -> 2; else -> 1 }
            }.thenByDescending { it.score }
        )

        val capturedResearchDates = learningDao.forecastResearchDatesForCohort(RESEARCH_COHORT_V7)
        val researchCaptureAllowed = today in capturedResearchDates || capturedResearchDates.size < RESEARCH_SESSION_GOAL
        if (researchCaptureAllowed) {
            captureForecastResearchScan(today, nowMs, marketRegime, targetPct, ranked.take(RESEARCH_CAPTURE_TOP_N))
        }

        // v7: five is a hard maximum, never a time-based quota. Every production-qualified
        // PRIMARY candidate can be published immediately until the five daily slots are full.
        val slots = (MAX_DAILY_RECOMMENDATIONS - existing.size).coerceAtLeast(0)
        val publishable = ranked.filter {
            it.symbol !in have && it.quality.tier == "PRIMARY" && it.quality.executionEligible
        }

        publishable.take(slots).forEachIndexed { index, item ->
            val price = item.evaluation.features.ltp
            val quality = item.quality
            learningDao.upsertLongForecast(
                LongForecastEntity(
                    forecastDate = today,
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
                    reason = quality.reason + " · family " + item.family.family + " · " + item.evaluation.reason +
                        " · capacity " + fmt(quality.volatilityCapacityPct) + "% for " + fmt(targetPct) + "% target",
                    generatedAtMs = nowMs,
                    lastPrice = price,
                    lastObservedAtMs = nowMs,
                    qualityTier = "PRIMARY",
                    executionEligible = true,
                    strategyFamily = item.family.family
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
            "max_daily_recommendations" to MAX_DAILY_RECOMMENDATIONS,
            "slots_remaining" to (MAX_DAILY_RECOMMENDATIONS - all.size).coerceAtLeast(0),
            "target_pct" to targetPct,
            "target_floor_pct" to ForecastQualityPolicy.MIN_TARGET_PCT,
            "multify_mfe_avg_pct" to learned.longAveragePct,
            "breadth_pct" to breadth,
            "market_regime" to marketRegime,
            "prior_research_sessions" to priorResearchDates.size,
            "prior_research_rows" to priorResearch.size,
            "research_capture_allowed" to researchCaptureAllowed,
            "research_sessions_captured" to capturedResearchDates.size,
            "research_session_goal" to RESEARCH_SESSION_GOAL,
            "family_counts" to ranked.groupingBy { it.family.family }.eachCount(),
            "research_pointer_count" to MultifyFamilyPolicy.pointerNames.size,
            "research_family_count" to MultifyFamilyPolicy.families.size,
            "top_candidates" to ranked.take(5).map {
                mapOf(
                    "symbol" to it.symbol,
                    "tier" to it.quality.tier,
                    "family" to it.family.family,
                    "family_score" to it.family.scores[it.family.family],
                    "score" to it.score,
                    "confidence" to it.evaluation.confidence,
                    "directional_score" to it.evaluation.directionalScore,
                    "reason" to it.quality.reason
                )
            }
        ))
        return all.map { it.toForecastDto() }
    }

    private suspend fun captureForecastResearchScan(
        date: String,
        scanAtMs: Long,
        marketRegime: String,
        targetPct: Double,
        candidates: List<ForecastCandidate>
    ) {
        candidates.forEachIndexed { index, item ->
            val votes = item.evaluation.votes.map {
                mapOf("name" to it.name, "score" to it.score, "note" to it.note)
            }
            learningDao.insertForecastResearch(
                ForecastResearchEntity(
                    researchDate = date,
                    researchCohort = RESEARCH_COHORT_V7,
                    scanAtMs = scanAtMs,
                    symbol = item.symbol,
                    rank = index + 1,
                    qualityTier = item.quality.tier,
                    publishEligible = item.quality.publishEligible,
                    executionEligible = item.quality.executionEligible,
                    targetPct = targetPct,
                    entryPrice = item.evaluation.features.ltp,
                    regime = item.evaluation.regime,
                    marketRegime = marketRegime,
                    strategy = item.evaluation.strategy,
                    strategyFamily = item.family.family,
                    familyScoreJson = researchGson.toJson(item.family.scores),
                    directionalScore = item.evaluation.directionalScore,
                    confidence = item.evaluation.confidence,
                    rejectionReason = item.quality.failedRules.joinToString("; ").ifBlank { item.quality.reason },
                    featureJson = researchGson.toJson(item.family.pointers),
                    voteJson = researchGson.toJson(votes)
                )
            )
        }
        auditLogger.log("FORECAST_RESEARCH", "SCAN_CAPTURED_V7", mapOf(
            "cohort" to RESEARCH_COHORT_V7,
            "date" to date,
            "scan_at_ms" to scanAtMs,
            "count" to candidates.size,
            "target_pct" to targetPct,
            "market_regime" to marketRegime,
            "pointer_count" to MultifyFamilyPolicy.pointerNames.size,
            "family_count" to MultifyFamilyPolicy.families.size
        ))
    }

    private fun researchFeatureMap(evaluation: StrategyEvaluation): Map<String, Double?> =
        MultifyFamilyPolicy.featureVector(evaluation.features)

    suspend fun captureMultifyResearchSnapshot(eventId: Long, symbol: String) {
        val token = ensureToken() ?: return
        val evaluation = runCatching { forecastEvaluation(symbol, token) }
            .onFailure {
                auditLogger.log("MULTIFY_RESEARCH", "FEATURE_CAPTURE_ERROR", mapOf(
                    "event_id" to eventId, "symbol" to symbol, "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull() ?: return
        val today = LocalDate.now(INDIA).toString()
        val latestResearch = learningDao.forecastResearchForDateAndCohort(today, RESEARCH_COHORT_V7).lastOrNull()
        val marketRegime = latestResearch?.marketRegime ?: "BREADTH_NEUTRAL"
        val family = MultifyFamilyPolicy.classify(evaluation.features, marketRegime)
        auditLogger.log("MULTIFY_RESEARCH", "FEATURE_SNAPSHOT_V7", mapOf(
            "event_id" to eventId,
            "symbol" to symbol,
            "regime" to evaluation.regime,
            "market_regime" to marketRegime,
            "strategy_family" to family.family,
            "family_scores" to family.scores,
            "pointer_count" to family.pointers.size,
            "directional_score" to evaluation.directionalScore,
            "confidence" to evaluation.confidence,
            "strategy" to evaluation.strategy,
            "features_40" to family.pointers,
            "votes" to evaluation.votes.map { mapOf("name" to it.name, "score" to it.score, "note" to it.note) }
        ))
    }

    private suspend fun forecastEvaluation(symbol: String, token: String): StrategyEvaluation {
        val now = ZonedDateTime.now(INDIA)
        val start = now.minusDays(4).toLocalDate().atTime(9,15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
        val historical = apiFactory.groww.historicalCandles(
            bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "15minute"
        ).requirePayload("Forecast candles $symbol")
        require(historical.candles.size >= 5) { "Insufficient forecast candles" }
        val features = LocalStrategyEngine.buildFeatures(quote, historical, 15)
        val signal = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "forecast", confidence = 1.0)
        return LocalStrategyEngine.evaluateLong(signal, features, includeEventPrior = false)
    }

    suspend fun executeForecast(symbol: String): String {
        val decisionStartedNs = SystemClock.elapsedRealtimeNanos()
        val now = ZonedDateTime.now(INDIA)
        require(now.dayOfWeek.value < 6 && marketSession() == "OPEN") { "Forecast BUY is available only during the NSE regular session" }

        val hot = armHotState?.takeIf { System.currentTimeMillis() - it.warmedAtMs <= ARM_HOT_STATE_MAX_AGE_MS }
        val settings = hot?.settings ?: preferences.settings.first()
        preArmGuard(settings)
        val token = hot?.token ?: ensureToken() ?: error("Authenticate Groww first")

        val row = learningDao.longForecastsForDate(now.toLocalDate().toString())
            .firstOrNull { it.symbol.equals(symbol, true) } ?: error("No active LONG recommendation for $symbol")
        require(row.status == "ACTIVE") { "$symbol forecast is no longer active" }
        require(row.executionEligible && row.qualityTier == "PRIMARY") { "$symbol is not a production-qualified PRIMARY forecast" }
        require(row.targetPct + 1e-9 >= ForecastQualityPolicy.MIN_TARGET_PCT) { "Forecast target is below the 1% quality floor" }
        if (managedDao.openPositions().any { it.symbol.equals(row.symbol, true) && it.side == "LONG" }) {
            return "${row.symbol} is already app-owned; duplicate BUY blocked"
        }

        // Fast path: use the pre-warmed CNC balance and the latest locally observed forecast price.
        // Network quote/margin calls are only fallback behavior when the hot path is unavailable.
        val referencePrice = max(row.entryPrice, row.lastPrice.takeIf { it > 0.0 } ?: row.entryPrice)
        val available = hot?.cncBalance ?: run {
            val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
            (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        }
        val sizing = CashAwareCncSizing.size(settings.holdingBudgetRupees.toDouble(), available, referencePrice)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        require(qty > 0) { "Safe CNC buying power cannot fund one share of ${row.symbol} after RMS reserve" }

        val ref = stableRef("FLH", "${row.id}-${row.symbol}")
        val order = try {
            placeCncBuy(token, row.symbol, qty, ref, decisionStartedNs)
        } catch (t: Throwable) {
            auditLogger.log("EXECUTION", "FORECAST_BUY_FAST_PATH_FAILED", mapOf(
                "symbol" to row.symbol,
                "quantity" to qty,
                "hot_path" to (hot != null),
                "message" to (t.message ?: t.javaClass.simpleName)
            ))
            throw t
        }
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED_FAST", mapOf(
            "symbol" to row.symbol,
            "available_cnc" to available,
            "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve,
            "reference_price" to referencePrice,
            "buffered_price" to sizing.bufferedPrice,
            "deployable_cash" to capital,
            "quantity" to qty,
            "hot_path" to (hot != null)
        ))
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: referencePrice
        val investedAmount = entry * filled
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING,
                symbol = row.symbol,
                product = "CNC",
                side = "LONG",
                quantity = filled,
                entryPrice = entry,
                stopPrice = entry * (1.0 - HOLDING_FAILSAFE_STOP_PCT),
                targetPrice = entry * (1.0 + row.targetPct / 100.0),
                strategy = "Forecast holding · ${row.strategy} · ${row.strategyFamily} · v7 fixed staircase trail",
                regime = row.regime,
                confidence = row.confidence,
                sourceEventId = null,
                openOrderId = order.growwOrderId,
                openReferenceId = ref,
                openedAtMs = System.currentTimeMillis(),
                lastPrice = entry,
                maxFavourablePrice = entry,
                maxAdversePrice = entry,
                lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry,
                capitalDeployed = investedAmount,
                campaignBudget = capital,
                trailingArmPct = LearnedTrailingPolicy.ARM_TRIGGER_PCT,
                trailingLastRatchetPrice = 0.0
            )
        )
        armHotState = hot?.copy(
            cncBalance = max(0.0, hot.cncBalance - entry * filled),
            warmedAtMs = System.currentTimeMillis()
        )
        auditLogger.log("FORECAST", "LONG_HOLDING_OPENED_V7", mapOf(
            "symbol" to row.symbol,
            "qty" to filled,
            "entry" to entry,
            "forecast_target_pct" to row.targetPct,
            "strategy_family" to row.strategyFamily,
            "invested_amount" to investedAmount,
            "trail_trigger_pct" to LearnedTrailingPolicy.ARM_TRIGGER_PCT,
            "initial_lock_pct" to LearnedTrailingPolicy.INITIAL_LOCK_PCT,
            "ratchet_step_pct" to LearnedTrailingPolicy.RATCHET_STEP_PCT,
            "dispatch_sla_ms" to 100
        ))
        return "BUY ${row.symbol} opened as CNC holding · qty $filled @ ₹${fmt(entry)} · target +${fmt(row.targetPct)}% · trail 0.55→0.50 then +0.10% steps"
    }

    suspend fun pendingLongForecastNotifications(): List<ForecastDto> {
        val today = LocalDate.now(INDIA).toString()
        val rows = learningDao.pendingLongForecastNotifications(today)
        rows.forEach { learningDao.markLongForecastNotified(it.id) }
        return rows.map { it.toForecastDto() }
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

        for (row in rows) {
            runCatching {
                val ltp = apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol)
                    .requirePayload("Forecast outcome quote ${row.symbol}").lastPrice ?: return@runCatching
                preferences.recordMarketData()
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
            }.onFailure {
                auditLogger.log("FORECAST", "OUTCOME_MONITOR_ERROR", mapOf(
                    "symbol" to row.symbol,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }
        }
        return learningDao.longForecastsForDate(today).count { it.status == "ACTIVE" }
    }

    private suspend fun refreshForecastChampion(row: LongForecastEntity) {
        val completed = learningDao.allLongForecasts().filter {
            it.marketRegime == row.marketRegime && it.regime == row.regime &&
                it.strategy == row.strategy && it.strategyFamily == row.strategyFamily &&
                it.status in setOf("TARGET_HIT", "MISSED")
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
                strategy = row.strategy, strategyFamily = row.strategyFamily,
                wins = wins, losses = losses, sampleCount = completed.size,
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
        val pending = learningDao.pendingForecastResearchOutcomesForCohort(date, RESEARCH_COHORT_V7)
        if (pending.isEmpty()) return 0
        val sessionDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return 0
        val sessionEndMs = sessionDate.atTime(15, 30).atZone(INDIA).toInstant().toEpochMilli()
        var updatedCount = 0
        pending.groupBy { it.symbol }.entries
            .take(RESEARCH_BACKFILL_SYMBOL_BATCH)
            .forEach { (symbol, rows) ->
            val earliest = rows.minOf { it.scanAtMs }
            val startTime = Instant.ofEpochMilli(earliest)
                .atZone(INDIA).toLocalDateTime().format(HIST_FORMAT)
            val endTime = sessionDate.atTime(15, 30).format(HIST_FORMAT)
            val historical = runCatching {
                apiFactory.groww.historicalCandles(
                    bearer(token),
                    growwSymbol = "NSE-$symbol",
                    startTime = startTime,
                    endTime = endTime,
                    candleInterval = "5minute"
                ).requirePayload("Forecast research outcome $symbol")
            }.onFailure {
                auditLogger.log("FORECAST_RESEARCH", "OUTCOME_BACKFILL_ERROR", mapOf(
                    "date" to date, "symbol" to symbol, "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }.getOrNull()
            if (historical != null) {
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
            val incompleteDate = learningDao.forecastResearchDatesForCohort(RESEARCH_COHORT_V7)
                .take(RESEARCH_SESSION_GOAL)
                .firstOrNull {
                    learningDao.pendingForecastResearchOutcomesForCohort(it, RESEARCH_COHORT_V7).isNotEmpty()
                }
            if (incompleteDate != null) {
                runCatching { backfillForecastResearchOutcomes(incompleteDate, token) }.getOrDefault(0)
            } else 0
        } else 0

        val existing = learningDao.latestResearchReport()
        if (!force && existing?.reportDate == today &&
            existing.summary.contains(RESEARCH_COHORT_V7) && researchUpdated == 0
        ) {
            return ResearchDto(existing.reportDate, existing.title, existing.summary)
        }

        val stats = learningStats()
        val forecastLearning = forecastLearningStats()
        val forecasts = learningDao.longForecastsForDate(today)
        val snapshots = learningDao.forecastResearchForDateAndCohort(today, RESEARCH_COHORT_V7)
            .filter { it.outcomeUpdatedAtMs != null }
        val wins = forecasts.count { it.status == "TARGET_HIT" }
        val matched = forecasts.count { it.multifyMatched }
        val frozen = learningDao.allForecastChampions().count { it.frozen }
        val avgTarget = forecasts.map { it.targetPct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        fun tierSummary(tier: String): String {
            val xs = snapshots.filter { it.qualityTier == tier }
            if (xs.isEmpty()) return "$tier: no completed v7 rows"
            val hitRate = xs.count { it.outcomeTargetHit == true }.toDouble() / xs.size * 100.0
            val mfe = xs.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val mae = xs.mapNotNull { it.outcomeMaePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val multifyMatches = xs.count { it.multifyMatched }
            return "$tier: ${xs.size} rows · target hit ${fmt(hitRate)}% · avg MFE ${fmt(mfe)}% · avg MAE ${fmt(mae)}% · Multify matches $multifyMatches"
        }

        fun familySummary(family: String): String {
            val xs = snapshots.filter { it.strategyFamily == family }
            if (xs.isEmpty()) return "$family: no completed v7 rows"
            val hitRate = xs.count { it.outcomeTargetHit == true }.toDouble() / xs.size * 100.0
            val mfe = xs.mapNotNull { it.outcomeMfePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val mae = xs.mapNotNull { it.outcomeMaePct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val matches = xs.count { it.multifyMatched }
            return "$family: ${xs.size} rows · hit ${fmt(hitRate)}% · MFE ${fmt(mfe)}% · MAE ${fmt(mae)}% · Multify matches $matches"
        }

        val report = buildString {
            append("$RESEARCH_COHORT_V7. ")
            append("Rolling 30-trading-day Multify MFE average: ${fmt(stats.longAveragePct)}% (median ${fmt(stats.longMedianPct)}%) from ${stats.rollingCalls} MFE-observed calls. ")
            append("This Multify average is analytics only and has no trailing-stop dependency. ")
            append("Pending historical MFE backfill: ${stats.mfeBackfillPendingCalls}. ")
            append(
                if (forecastLearning.completedForecasts > 0)
                    "Rolling published-forecast full-session MFE average: ${fmt(forecastLearning.longAveragePct)}% across ${forecastLearning.completedForecasts} completed forecasts. "
                else
                    "Published-forecast MFE is still learning. "
            )
            append("Today's average forecast target: ${fmt(avgTarget)}% with a hard 1.00% floor. ")
            append("Published target hits: $wins/${forecasts.size}. ")
            append("V7 reverse-engineering capture: ${forecastLearning.researchSessionsCaptured}/${forecastLearning.researchSessionGoal} sessions · ${forecastLearning.researchPointerCount} pointers · ${forecastLearning.researchFamilyCount} fixed families. ")
            append(tierSummary("PRIMARY")).append(". ")
            append(tierSummary("SECONDARY")).append(". ")
            append(tierSummary("RESEARCH")).append(". ")
            MultifyFamilyPolicy.families.forEach { append(familySummary(it)).append(". ") }
            append("Multify later matched $matched/${forecasts.size} published symbols. ")
            append("Frozen LONG champions: $frozen. ")
            append("Trailing is independently fixed at +0.55% high-water → +0.50% lock, then +0.10% staircase steps.")
        }
        val entity = com.multify.traderpro.data.local.ResearchReportEntity(
            reportDate = today,
            generatedAtMs = System.currentTimeMillis(),
            title = "V7 production reverse-engineering review",
            summary = report
        )
        learningDao.insertResearchReport(entity)
        auditLogger.log("RESEARCH", "AFTER_HOURS_V7_REPORT", mapOf(
            "date" to today,
            "wins" to wins,
            "total" to forecasts.size,
            "matches" to matched,
            "multify_mfe_avg_pct" to stats.longAveragePct,
            "forecast_avg_target_pct" to avgTarget,
            "research_rows_completed" to snapshots.size,
            "research_sessions" to forecastLearning.researchSessionsCaptured,
            "research_cohort" to RESEARCH_COHORT_V7,
            "pointer_count" to MultifyFamilyPolicy.pointerNames.size,
            "family_count" to MultifyFamilyPolicy.families.size
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
        recordExternalClose(position, removedQty, exit, if (visibleQty == 0) "MANUAL_BROKER_EXIT" else "MANUAL_BROKER_PARTIAL_EXIT")
        brokerInventorySnapshot = null
        if (visibleQty <= 0) {
            managedDao.deletePosition(position.id)
            auditLogger.log("HOLDING", "EXTERNAL_LONG_CLOSED", mapOf("symbol" to position.symbol, "qty" to removedQty, "exit" to exit))
            return null
        }
        val updated = position.copy(
            quantity = visibleQty,
            capitalDeployed = position.entryPrice * visibleQty,
            lastEvaluatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(updated)
        auditLogger.log("HOLDING", "EXTERNAL_LONG_REDUCED", mapOf(
            "symbol" to position.symbol, "removed_qty" to removedQty, "remaining_qty" to visibleQty, "exit" to exit
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
                sourceEventId = position.sourceEventId, openOrderId = position.openOrderId, closeOrderId = "EXTERNAL",
                openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
            )
        )
    }
    suspend fun monitorManagedPositions(): Int {
        val open = managedDao.openPositions()
        if (open.isEmpty()) return 0
        val token = ensureToken() ?: return open.size
        val marketOpen = marketSession() == "OPEN"
        val nowMs = System.currentTimeMillis()

        if (!marketOpen) {
            if (nowMs - lastClosedSessionReconcileAtMs < CLOSED_SESSION_RECONCILE_MS) return open.size
            lastClosedSessionReconcileAtMs = nowMs
            open.forEach { position ->
                try {
                    if (position.side != "LONG") return@forEach
                    reconcilePositionWithBroker(token, position, preferredMark = null, force = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    auditLogger.log("HOLDING", "CLOSED_SESSION_RECONCILE_ERROR", mapOf(
                        "symbol" to position.symbol,
                        "message" to (t.message ?: t.javaClass.simpleName)
                    ))
                }
            }
            return managedDao.openPositions().size
        }

        val longPositions = open.filter { it.side == "LONG" && it.product == "CNC" }
        open.filter { it.side != "LONG" }.forEach { legacy ->
            runCatching { closeLegacyNonLong(token, legacy) }
                .onFailure { auditLogger.log("SAFETY", "LEGACY_CLEANUP_ERROR", mapOf("symbol" to legacy.symbol, "message" to (it.message ?: it.javaClass.simpleName))) }
        }

        // One batch LTP request monitors every app-owned holding. Groww supports up to 50
        // instruments per LTP request; chunking keeps the path safe if holdings ever exceed that.
        val ltpBySymbol = mutableMapOf<String, Double>()
        longPositions.chunked(50).forEach { chunk ->
            val keys = chunk.joinToString(",") { "NSE_${it.symbol}" }
            runCatching {
                apiFactory.groww.ltp(
                    bearer(token),
                    exchangeSymbols = keys
                ).requirePayload("Batch holding LTP")
            }.onSuccess { payload ->
                payload.forEach { (key, value) ->
                    if (value.isFinite() && value > 0.0) {
                        ltpBySymbol[key.uppercase(Locale.US)] = value
                    }
                }
                preferences.recordMarketData()
            }.onFailure {
                auditLogger.log("HOLDING", "BATCH_LTP_ERROR", mapOf(
                    "count" to chunk.size,
                    "message" to (it.message ?: it.javaClass.simpleName)
                ))
            }
        }

        coroutineScope {
            longPositions.map { position ->
                async {
                    try {
                        val key = "NSE_${position.symbol}".uppercase(Locale.US)
                        val ltp = ltpBySymbol[key]
                            ?: ltpBySymbol[position.symbol.uppercase(Locale.US)]
                            ?: position.lastPrice
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

        // Refresh broker inventory after the latency-critical trail checks. The next trigger can
        // use this cached broker truth without waiting on a holdings/positions network roundtrip.
        if (nowMs - lastOpenBrokerRefreshAtMs >= OPEN_BROKER_REFRESH_INTERVAL_MS) {
            lastOpenBrokerRefreshAtMs = nowMs
            runCatching { brokerInventory(token, force = true) }
                .onSuccess {
                    managedDao.openPositions().filter { p -> p.side == "LONG" && p.product == "CNC" }.forEach { p ->
                        runCatching { reconcilePositionWithBroker(token, p, preferredMark = null, force = false) }
                    }
                }
                .onFailure {
                    auditLogger.log("HOLDING", "BROKER_CACHE_REFRESH_ERROR", mapOf(
                        "message" to (it.message ?: it.javaClass.simpleName)
                    ))
                }
        }
        return managedDao.openPositions().size
    }

    private suspend fun monitorLongHolding(
        position: ManagedPositionEntity,
        token: String,
        ltp: Double
    ) {
        if (!ltp.isFinite() || ltp <= 0.0) return
        val previousHighWater = position.maxFavourablePrice
        var updated = position.copy(
            lastPrice = ltp,
            maxFavourablePrice = max(position.maxFavourablePrice, ltp),
            maxAdversePrice = min(position.maxAdversePrice, ltp),
            trailingArmPct = LearnedTrailingPolicy.ARM_TRIGGER_PCT,
            lastEvaluatedAtMs = System.currentTimeMillis()
        )

        val currentLockedPct = LearnedTrailingPolicy.lockedPct(updated.entryPrice, updated.stopPrice)
        val desiredLockedPct = LearnedTrailingPolicy.desiredLockedPct(updated.entryPrice, updated.maxFavourablePrice)

        // One-time upgrade translation for v6.x holdings. Legacy adaptive stops are mapped
        // to the exact v7 staircase implied by the already-observed high-water. After this
        // translation, v7 profit stops only move upward.
        val v7Managed = updated.strategy.contains("v7 fixed staircase", ignoreCase = true)
        if (!v7Managed && currentLockedPct != null) {
            val translatedStop = if (desiredLockedPct != null) {
                LearnedTrailingPolicy.stopPrice(updated.entryPrice, desiredLockedPct)
            } else {
                updated.entryPrice * (1.0 - HOLDING_FAILSAFE_STOP_PCT)
            }
            updated = updated.copy(
                stopPrice = translatedStop,
                trailingLastRatchetPrice = if (desiredLockedPct != null) updated.maxFavourablePrice else 0.0,
                trailingArmPct = LearnedTrailingPolicy.ARM_TRIGGER_PCT,
                strategy = updated.strategy + " · v7 fixed staircase migrated"
            )
            auditLogger.log("TRAILING_STOP", "V7_LEGACY_TRAIL_TRANSLATED", mapOf(
                "symbol" to updated.symbol,
                "old_locked_pct" to currentLockedPct,
                "new_locked_pct" to desiredLockedPct,
                "new_stop" to translatedStop,
                "high_water_gain_pct" to LearnedTrailingPolicy.gainPct(updated.entryPrice, updated.maxFavourablePrice)
            ))
        }

        val hardStop = updated.stopPrice
        if (hardStop != null && hardStop <= updated.entryPrice && ltp <= hardStop) {
            val triggerStartedNs = SystemClock.elapsedRealtimeNanos()
            closeLongHolding(
                token = token,
                position = updated,
                mark = ltp,
                reason = "HOLDING_FAILSAFE_STOP",
                notificationStartedNs = triggerStartedNs,
                fastDispatch = true
            )
            return
        }

        val effectiveDesired = LearnedTrailingPolicy.desiredLockedPct(updated.entryPrice, updated.maxFavourablePrice)
        if (LearnedTrailingPolicy.shouldRatchet(updated.entryPrice, updated.stopPrice, effectiveDesired)) {
            val desiredStop = LearnedTrailingPolicy.stopPrice(updated.entryPrice, effectiveDesired!!)
            val existingProfitStop = updated.stopPrice?.takeIf { it > updated.entryPrice }
            val effectiveStop = max(existingProfitStop ?: updated.entryPrice, desiredStop)
            val effectiveLockedPct = LearnedTrailingPolicy.lockedPct(updated.entryPrice, effectiveStop)
            val wasArmed = LearnedTrailingPolicy.isArmed(updated.stopPrice, updated.entryPrice)
            updated = updated.copy(
                stopPrice = effectiveStop,
                trailingLastRatchetPrice = updated.maxFavourablePrice,
                trailingArmPct = LearnedTrailingPolicy.ARM_TRIGGER_PCT,
                lastEvaluatedAtMs = System.currentTimeMillis()
            )
            auditLogger.log("TRAILING_STOP", if (wasArmed) "V7_STAIRCASE_RATCHET" else "V7_STAIRCASE_ARMED", mapOf(
                "symbol" to updated.symbol,
                "ltp" to ltp,
                "old_high_water" to previousHighWater,
                "new_high_water" to updated.maxFavourablePrice,
                "high_water_gain_pct" to LearnedTrailingPolicy.gainPct(updated.entryPrice, updated.maxFavourablePrice),
                "locked_profit_pct" to effectiveLockedPct,
                "new_stop" to effectiveStop,
                "trigger_pct" to LearnedTrailingPolicy.ARM_TRIGGER_PCT,
                "initial_lock_pct" to LearnedTrailingPolicy.INITIAL_LOCK_PCT,
                "step_pct" to LearnedTrailingPolicy.RATCHET_STEP_PCT,
                "estimated_net_at_stop" to LearnedTrailingPolicy.estimatedNetProfit(updated.entryPrice, effectiveStop, updated.quantity)
            ))
        }

        val activeStop = updated.stopPrice
        if (LearnedTrailingPolicy.isArmed(activeStop, updated.entryPrice) && activeStop != null && ltp <= activeStop) {
            val triggerStartedNs = SystemClock.elapsedRealtimeNanos()
            val closed = closeLongHolding(
                token = token,
                position = updated,
                mark = ltp,
                reason = "LONG_TRAILING_PROFIT_STOP_V7_STAIRCASE",
                notificationStartedNs = triggerStartedNs,
                fastDispatch = true
            )
            auditLogger.log("TRAILING_STOP", "V7_STAIRCASE_EXIT_TRIGGERED", mapOf(
                "symbol" to updated.symbol,
                "ltp" to ltp,
                "active_stop" to activeStop,
                "locked_profit_pct" to LearnedTrailingPolicy.lockedPct(updated.entryPrice, activeStop),
                "high_water_gain_pct" to LearnedTrailingPolicy.gainPct(updated.entryPrice, updated.maxFavourablePrice),
                "closed" to (closed != null)
            ))
            return
        }

        managedDao.updatePosition(updated)
    }

    private suspend fun currentOneMinuteAtr(symbol: String, token: String, ltp: Double): Double {
        val nowMs = System.currentTimeMillis()
        oneMinuteAtrCache[symbol]?.takeIf { nowMs - it.atMs <= ONE_MINUTE_ATR_CACHE_MS }?.let { return it.atr }
        val fallback = max(ltp * .004, .05)
        val atr = runCatching {
            val now = ZonedDateTime.now(INDIA)
            val historical = apiFactory.groww.historicalCandles(
                bearer(token),
                growwSymbol = "NSE-$symbol",
                startTime = now.minusMinutes(40).toLocalDateTime().format(HIST_FORMAT),
                endTime = now.toLocalDateTime().format(HIST_FORMAT),
                candleInterval = "1minute"
            ).requirePayload("1-minute ATR candles $symbol")
            LocalStrategyEngine.buildFeatures(QuotePayload(lastPrice = ltp), historical, 1).atr14
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: fallback
        }.getOrDefault(fallback)
        oneMinuteAtrCache[symbol] = AtrCacheEntry(atr, nowMs)
        return atr
    }

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
        notificationStartedNs: Long? = null,
        fastDispatch: Boolean = false
    ): ManagedTradeEntity? {
        val positionMutex = closePositionMutexes.getOrPut(position.id) { Mutex() }
        return positionMutex.withLock {
        require(position.side == "LONG") { "Only LONG holdings are supported" }

        val local = managedDao.openPositionById(position.id)
        if (local == null) {
            auditLogger.log("SAFETY", "DUPLICATE_CLOSE_BLOCKED", mapOf(
                "symbol" to position.symbol,
                "position_id" to position.id,
                "reason" to reason,
                "stage" to "local_position_already_closed"
            ))
            return@withLock null
        }

        val snapshot = brokerInventorySnapshot
        val snapshotFresh = snapshot != null &&
            System.currentTimeMillis() - snapshot.atMs <= FAST_SELL_BROKER_CACHE_MAX_AGE_MS

        // v7 latency rule: a staircase/failsafe trigger must not wait for a fresh broker REST
        // reconciliation before POSTing the SELL. We use a recently refreshed broker snapshot
        // when available; otherwise the app-owned local quantity is dispatched immediately.
        // Normal/non-latency-critical closes retain forced broker reconciliation.
        val sellPosition = if (fastDispatch) {
            if (snapshotFresh) {
                reconcilePositionWithBroker(token, local, preferredMark = mark, force = false)
            } else {
                local
            }
        } else {
            reconcilePositionWithBroker(token, local, preferredMark = mark, force = true)
        }

        if (sellPosition == null || sellPosition.quantity <= 0) {
            auditLogger.log("SAFETY", "DUPLICATE_CLOSE_BLOCKED", mapOf(
                "symbol" to position.symbol,
                "position_id" to position.id,
                "reason" to reason,
                "stage" to "broker_quantity_already_closed"
            ))
            return@withLock null
        }

        val ref = stableRef("CLS", "${sellPosition.id}-${sellPosition.symbol}")
        val order = placeOwnedCncSell(token, sellPosition, ref, notificationStartedNs)
        val exit = order.averageFillPrice?.takeIf { it > 0.0 } ?: mark
        val gross = (exit - sellPosition.entryPrice) * sellPosition.quantity
        val costs = estimatedCashCosts(sellPosition.entryPrice, exit, sellPosition.quantity)
        val trade = ManagedTradeEntity(
            engine = sellPosition.engine, symbol = sellPosition.symbol, product = "CNC", side = "LONG",
            quantity = sellPosition.quantity, entryPrice = sellPosition.entryPrice, exitPrice = exit,
            grossPnl = gross, estimatedCosts = costs, netPnl = gross - costs, exitReason = reason,
            strategy = sellPosition.strategy, regime = sellPosition.regime, confidence = sellPosition.confidence,
            mfeRupees = max(0.0, sellPosition.maxFavourablePrice - sellPosition.entryPrice) * sellPosition.quantity,
            maeRupees = max(0.0, sellPosition.entryPrice - sellPosition.maxAdversePrice) * sellPosition.quantity,
            sourceEventId = sellPosition.sourceEventId, openOrderId = sellPosition.openOrderId, closeOrderId = order.growwOrderId,
            openedAtMs = sellPosition.openedAtMs, closedAtMs = System.currentTimeMillis()
        )
        managedDao.insertTrade(trade)
        managedDao.deletePosition(sellPosition.id)
        brokerInventorySnapshot = null
        auditLogger.log("HOLDING", "LONG_CLOSED", mapOf(
            "symbol" to sellPosition.symbol,
            "qty" to sellPosition.quantity,
            "exit" to exit,
            "reason" to reason,
            "net_pnl" to trade.netPnl,
            "fast_dispatch" to fastDispatch
        ))
        closePositionMutexes.remove(sellPosition.id, positionMutex)
        trade
        }
    }

    private suspend fun placeOwnedCncSell(
        token: String,
        position: ManagedPositionEntity,
        reference: String,
        notificationStartedNs: Long? = null
    ): OrderPayload {
        require(position.side == "LONG") { "Only app-owned LONG holdings may be sold" }
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
        val response = apiFactory.groww.placeOrder(
            bearer(token),
            OrderCreateRequest(
                tradingSymbol = symbol,
                quantity = qty,
                product = product,
                transactionType = transaction,
                orderReferenceId = reference
            )
        ).requirePayload("Place $transaction $symbol")
        val brokerAckLatencyMs = ((SystemClock.elapsedRealtimeNanos() - brokerStartedNs).coerceAtLeast(0L)) / 1_000_000L
        preferences.recordOrderLatency(dispatchPrepMicros, brokerAckLatencyMs)
        auditLogger.log("EXECUTION_LATENCY", "ORDER_ACK", mapOf(
            "symbol" to symbol,
            "transaction" to transaction,
            "dispatch_prep_micros" to dispatchPrepMicros,
            "dispatch_target_met" to (notificationStartedNs == null || dispatchPrepMicros < 100_000L),
            "broker_ack_ms" to brokerAckLatencyMs
        ))
        val id = response.growwOrderId
        require(id.isNotBlank()) { "Groww did not return an order ID" }
        repeat(10) {
            delay(450)
            val status = apiFactory.groww.orderStatus(bearer(token), id).requirePayload("Order status")
            if (status.orderStatus.equals("EXECUTED", true) || (status.filledQuantity ?: 0) >= qty) {
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
                auditLogger.log("EXECUTION_LATENCY", "ORDER_FILLED", mapOf(
                    "symbol" to symbol,
                    "transaction" to transaction,
                    "total_execution_ms" to totalExecutionMs,
                    "broker_ack_ms" to brokerAckLatencyMs
                ))
                return status.copy(growwOrderId = id, orderReferenceId = reference)
            }
            if (status.orderStatus.equals("REJECTED", true) || status.orderStatus.equals("FAILED", true) || status.orderStatus.equals("CANCELLED", true)) {
                error("Groww order ${status.orderStatus}: ${status.remark.orEmpty()}")
            }
        }
        error("Groww order $id was not confirmed filled; no duplicate retry was attempted")
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
                quantity = p.quantity,
                averagePrice = p.entryPrice,
                ltp = ltp,
                pnl = pnl,
                stopPrice = p.stopPrice,
                targetPrice = p.targetPrice,
                highWaterPrice = p.maxFavourablePrice,
                trailingLastRatchetPrice = p.trailingLastRatchetPrice.takeIf { it > 0.0 },
                trailDistance = if (LearnedTrailingPolicy.isArmed(p.stopPrice, p.entryPrice) && p.stopPrice != null)
                    (p.maxFavourablePrice - p.stopPrice).coerceAtLeast(0.0) else null,
                trailArmPct = p.trailingArmPct.takeIf { it.isFinite() && it > 0.0 },
                trailArmed = LearnedTrailingPolicy.isArmed(p.stopPrice, p.entryPrice),
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

    suspend fun exportCompleteLogs(uri: Uri): LogExportResult {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val settings = preferences.settings.first()
        val signals = dao.allNow()
        val positions = managedDao.allPositions().filter { it.side == "LONG" }
        val trades = managedDao.allTrades().filter { it.side == "LONG" }
        val executionSamples = managedDao.allExecutionSamples()
        val priceObservations = learningDao.allObservations()
        val forecastResearch = learningDao.allForecastResearch()
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
        val safeSettings = mapOf(
            "holding_budget_rupees" to settings.holdingBudgetRupees,
            "arm_effective" to settings.armEffective,
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
                add("long_learning.json", calls)
                add("long_forecasts.json", forecasts)
                add("long_champions.json", champions)
                add("research.json", research)
                addText("system_audit.jsonl", auditLogger.readAll())
            }
        } ?: error("Unable to open export destination")
        return LogExportResult(12, signals.size, trades.size)
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
        if (!settings.armEffective || settings.safetyHalt) return
        if (armHotState == null ||
            System.currentTimeMillis() - (armHotState?.warmedAtMs ?: 0L) > ARM_HOT_REFRESH_MS
        ) {
            runCatching { prepareArmHotPath(force = true) }
        }
    }

    private suspend fun ensureToken(): String? {
        armHotState?.token?.takeIf { it.isNotBlank() }?.let { return it }
        var settings = preferences.settings.first()
        var token = secretStore.getAccessToken()
        if (!settings.brokerAuthenticated || token.isNullOrBlank()) {
            if (!hasBrokerCredentials()) return null
            runCatching { authenticate() }.getOrElse { return null }
            settings = preferences.settings.first()
            token = secretStore.getAccessToken()
        }
        return token
    }

    private fun preArmGuard(settings: AppSettings) {
        require(settings.armEffective) { "ARM is OFF" }
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
        generatedAtMs = generatedAtMs, qualityTier = qualityTier, executionEligible = executionEligible,
        strategyFamily = strategyFamily
    )

    private fun championKey(marketRegime: String, regime: String, strategy: String, family: String): String =
        listOf("LONG", marketRegime, family, regime, strategy)
            .joinToString("|") { it.uppercase(Locale.US).replace("|", "/") }

    private fun disconnectedDashboard(settings: AppSettings) = DashboardDto(
        armed = settings.armEffective, halted = settings.safetyHalt, marketSession = marketSession(),
        asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        broker = BrokerStatusDto(configured = hasBrokerCredentials(), connected = false, detail = "Refresh & Authenticate required"),
        risk = RiskStatusDto(maxExposure = settings.holdingBudgetRupees.toDouble(), riskMode = if (settings.safetyHalt) "HALTED" else "DISARMED")
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

    private fun estimatedCashCosts(entry: Double, exit: Double, qty: Int): Double = (entry + exit) * qty * 0.0006
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
        private const val MAX_DAILY_RECOMMENDATIONS = 5
        private const val RESEARCH_COHORT_V7 = "V7_FIVE_SESSION_40X3"
        private const val FORECAST_SCAN_INTERVAL_MS = 5L * 60L * 1000L
        private const val FORECAST_OUTCOME_INTERVAL_MS = 60_000L
        private const val FORECAST_SYMBOL_PACING_MS = 500L
        private const val RESEARCH_CAPTURE_TOP_N = 48
        private const val RESEARCH_BACKFILL_SYMBOL_PACING_MS = 300L
        private const val RESEARCH_BACKFILL_SYMBOL_BATCH = 10
        private const val RESEARCH_SESSION_GOAL = 5
        private const val MIN_RESEARCH_EVIDENCE_ROWS = 4
        private const val MFE_BACKFILL_INTERVAL_MS = 5L * 60L * 1000L
        private const val MFE_BACKFILL_BATCH_SIZE = 4
        private const val MFE_AFTER_HOURS_BACKFILL_BATCH_SIZE = 16
        private const val HOLDING_FAILSAFE_STOP_PCT = 0.12
        private const val ARM_HOT_REFRESH_MS = 30_000L
        private const val ARM_HOT_AUDIT_INTERVAL_MS = 15L * 60L * 1000L
        private const val ARM_HOT_STATE_MAX_AGE_MS = 45_000L
        private const val ONE_MINUTE_ATR_CACHE_MS = 60_000L
        private const val EXECUTION_SAMPLE_LIMIT = 30
        private const val BROKER_INVENTORY_REFRESH_MS = 6_000L
        private const val FAST_SELL_BROKER_CACHE_MAX_AGE_MS = 6_000L
        private const val OPEN_BROKER_REFRESH_INTERVAL_MS = 5_000L
        private const val CLOSED_SESSION_RECONCILE_MS = 60_000L
        private const val MULTIFY_PACKAGE = "com.multyfi.invest"
        private const val UPLOADED_THREE_MONTH_REALIZED_PCT = 0.49019417475728155
        private const val DEFAULT_LONG_REALIZED_PCT = 0.33
        const val ENGINE_MULTIFY_HOLDING = "MULTIFY_HOLDING"
        const val ENGINE_FORECAST_HOLDING = "FORECAST_HOLDING"
    }
}
