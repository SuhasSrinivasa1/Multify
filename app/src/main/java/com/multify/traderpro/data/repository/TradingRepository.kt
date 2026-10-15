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
import com.multify.traderpro.engine.ForecastQualityPolicy
import com.multify.traderpro.engine.LearnedTrailingPolicy
import com.multify.traderpro.engine.LocalStrategyEngine
import com.multify.traderpro.engine.MultifyReverseEngineering
import com.multify.traderpro.engine.StrategyEvaluation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private var lastArmHotAuditAtMs = 0L
    private var lastMfeBackfillAtMs = 0L
    private var lastArmHotAuditBalance = Double.NaN
    private var lastArmHotAuditArmed: Boolean? = null
    private val oneMinuteAtrCache = mutableMapOf<String, AtrCacheEntry>()

    private data class AtrCacheEntry(val atr: Double, val atMs: Long)

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
        val fallback = LearnedTrailingPolicy.MIN_DYNAMIC_TRAIL_ARM_PCT
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
        val researchSessions = learningDao.forecastResearchDates().take(RESEARCH_SESSION_GOAL).size
        return ForecastLearningStatsDto(
            rollingTradingDays = keepDates.size,
            completedForecasts = rolling.size,
            longAveragePct = AdaptiveLearningMath.rollingMean(values, 0.0),
            todayTargetAveragePct = todayRows.map { it.targetPct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            targetFloorPct = ForecastQualityPolicy.MIN_TARGET_PCT,
            researchSessionsCaptured = researchSessions,
            researchSessionGoal = RESEARCH_SESSION_GOAL
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
        learningDao.markLongForecastMatch(LocalDate.now(INDIA).toString(), symbol)
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
                    listener = if (heartbeatAge <= 30_000L) "HEALTHY" else "UNHEALTHY",
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

        // Recommendation learning is independent of Groww execution/reconciliation.
        // A manual broker exit or a failed SELL must not erase Multify's reported result.
        var closedLearningCall: LearningCallEntity? = null
        when (parsed.type) {
            SignalType.TRADE_RELEASE -> recordLiveBuy(eventId, parsed)
            SignalType.BOOK_PROFIT -> if (!symbol.isNullOrBlank()) {
                val eventAtMs = dao.byId(eventId)?.receivedAtMs ?: System.currentTimeMillis()
                closedLearningCall = recordLiveExit(eventId, symbol, parsed.exitPrice, parsed.returnsPct, eventAtMs)
            }
            else -> Unit
        }

        val hot = armHotState?.takeIf { System.currentTimeMillis() - it.warmedAtMs <= ARM_HOT_STATE_MAX_AGE_MS }
        val token = hot?.token ?: ensureToken()
        if (token == null) {
            dao.updateForwarding(eventId, "CAPTURED", "AUTH_REQUIRED", "Signal and Multify learning stored. Authenticate Groww to enable ARM.", null)
            return
        }
        if (parsed.type == SignalType.BOOK_PROFIT && closedLearningCall != null) {
            runCatching { captureMultifyMfe(closedLearningCall!!, token) }
                .onFailure { auditLogger.log("LEARNING", "MULTIFY_MFE_LIVE_ERROR", mapOf("symbol" to (symbol ?: ""), "message" to (it.message ?: it.javaClass.simpleName))) }
        }
        val settings = hot?.settings ?: preferences.settings.first()
        when (parsed.type) {
            SignalType.TRADE_RELEASE -> handleMultifyBuy(eventId, parsed, token, settings, notificationStartedNs)
            SignalType.BOOK_PROFIT -> handleMultifyExit(eventId, parsed, token, settings, notificationStartedNs)
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
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to symbol, "available_cnc" to available, "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to referencePrice,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty
        ))
        require(qty > 0) { "Safe CNC buying power cannot fund one share of $symbol after RMS reserve" }

        val ref = stableRef("MLH", "$eventId-$symbol")
        val order = placeCncBuy(token, symbol, qty, ref, notificationStartedNs)
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: referencePrice
        val investedAmount = entry * filled
        val multifyStats = learningStats()
        val trailArmPct = LearnedTrailingPolicy.dynamicArmPct(multifyStats.longAveragePct)
        val minimumNetProfit = LearnedTrailingPolicy.minimumNetProfitRupees(entry, filled, trailArmPct)
        val minimumProfitExit = LearnedTrailingPolicy.minimumExitPriceForNetProfitPct(entry, trailArmPct)
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_MULTIFY_HOLDING, symbol = symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = entry * (1.0 - HOLDING_FAILSAFE_STOP_PCT),
                targetPrice = minimumProfitExit,
                strategy = "Multify ARM holding · dynamic Multify-MFE trail",
                regime = "HOLDING_LONG", confidence = 1.0, sourceEventId = eventId,
                openOrderId = order.growwOrderId, openReferenceId = ref, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry, capitalDeployed = investedAmount, campaignBudget = capital,
                trailingArmPct = trailArmPct, trailingLastRatchetPrice = 0.0
            )
        )
        armHotState = hot?.copy(
            cncBalance = max(0.0, hot.cncBalance - entry * filled),
            warmedAtMs = System.currentTimeMillis()
        )
        dao.updateForwarding(
            eventId, "DELIVERED", "CNC_LONG_BOUGHT",
            "ARM bought $filled $symbol into holdings @ ₹${fmt(entry)}. Dynamic trailing arms at +${fmt(trailArmPct)}% estimated net (Multify MFE average, minimum 0.50%).",
            null
        )
        auditLogger.log("HOLDING", "MULTIFY_LONG_OPENED", mapOf(
            "symbol" to symbol, "qty" to filled, "entry" to entry,
            "invested_amount" to investedAmount,
            "multify_mfe_avg_pct" to multifyStats.longAveragePct,
            "trail_arm_pct" to trailArmPct,
            "minimum_net_profit" to minimumNetProfit,
            "minimum_profit_exit_price" to minimumProfitExit
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
            dao.updateForwarding(eventId, "ANALYZED", "MULTIFY_EXIT_RECORDED", "Book Profit recorded in Multify learning; no app-owned Multify holding was open.", null)
            return
        }
        val reconciled = reconcilePositionWithBroker(token, holding, signal.exitPrice, force = true)
        if (reconciled == null) {
            armHotState = null
            dao.updateForwarding(
                eventId, "DELIVERED", "MULTIFY_EXIT_RECORDED_MANUAL_CLOSE",
                "Multify Book Profit recorded. Groww already showed the app-owned $symbol quantity as manually/external closed, so no duplicate SELL was sent.", null
            )
            return
        }
        require(settings.brokerDdpiEnabled) { "DDPI is required to sell the app-owned CNC holding" }
        val mark = signal.exitPrice ?: reconciled.lastPrice
        closeLongHolding(token, reconciled, mark, "MULTIFY_BOOK_PROFIT", notificationStartedNs)
        armHotState = null
        brokerInventorySnapshot = null
        dao.updateForwarding(eventId, "DELIVERED", "CNC_LONG_SOLD", "Multify Book Profit sold the remaining app-owned $symbol holding.", null)
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
        val recentSymbols = frequency.entries.sortedByDescending { it.value }.take(12).map { it.key }
        val dnaSymbols = MultifyReverseEngineering.positiveCases.map { it.symbol }
        val master = nseSymbols.symbols()
        val slot = (now.hour * 60 + now.minute) / 20
        val rotating = if (master.isEmpty()) emptyList() else {
            val seed = kotlin.math.abs(today.hashCode() + slot * 977)
            (0 until min(24, master.size)).map { i -> master[Math.floorMod(seed + i * 131, master.size)] }
        }
        val universe = (dnaSymbols + recentSymbols + rotating).distinct().take(42)
        val evaluated = mutableListOf<Pair<String, StrategyEvaluation>>()
        for (symbol in universe) {
            val evaluation = runCatching { forecastEvaluation(symbol, token) }.getOrNull() ?: continue
            if (evaluation.features.ltp < 10.0) continue
            evaluated += symbol to evaluation
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

        val desired = when {
            now.toLocalTime() < LocalTime.of(10, 15) -> 1
            now.toLocalTime() < LocalTime.of(11, 15) -> 2
            now.toLocalTime() < LocalTime.of(12, 15) -> 3
            now.toLocalTime() < LocalTime.of(13, 15) -> 4
            else -> 5
        }
        val have = existing.map { it.symbol }.toSet()
        var eligibleCount = 0
        val ranked = evaluated.mapNotNull { row ->
            if (row.first in have) return@mapNotNull null
            if (marketRegime == "BREADTH_WEAK") return@mapNotNull null
            val quality = ForecastQualityPolicy.evaluate(row.second, targetPct)
            if (!quality.eligible) return@mapNotNull null
            eligibleCount++
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
            val qualityBonus = ForecastQualityPolicy.rankingBonus(row.second, quality.volatilityCapacityPct, targetPct)
            Triple(
                row.first,
                row.second,
                (row.second.confidence + championBonus + frequencyBonus + dnaBonus + qualityBonus).coerceAtMost(.99)
            )
        }.sortedByDescending { it.third }

        val slots = (desired - existing.size).coerceAtLeast(0)
        ranked.take(slots).forEachIndexed { index, item ->
            val price = item.second.features.ltp
            val key = championKey(marketRegime, item.second.regime, item.second.strategy)
            val quality = ForecastQualityPolicy.evaluate(item.second, targetPct)
            learningDao.upsertLongForecast(
                LongForecastEntity(
                    forecastDate = today,
                    rank = existing.size + index + 1,
                    symbol = item.first,
                    entryPrice = price,
                    targetPct = targetPct,
                    targetPrice = price * (1.0 + targetPct / 100.0),
                    confidence = item.second.confidence,
                    score = item.third,
                    strategy = item.second.strategy,
                    regime = item.second.regime,
                    marketRegime = marketRegime,
                    championKey = key,
                    reason = item.second.reason + " · capacity " + fmt(quality.volatilityCapacityPct) + "% for " + fmt(targetPct) + "% target",
                    generatedAtMs = nowMs,
                    lastPrice = price,
                    lastObservedAtMs = nowMs
                )
            )
        }
        val all = learningDao.longForecastsForDate(today)
        auditLogger.log("FORECAST", "LONG_SCAN_QUALITY_GATED", mapOf(
            "date" to today,
            "evaluated" to evaluated.size,
            "eligible" to eligibleCount,
            "count" to all.size,
            "max_slots_by_time" to desired,
            "target_pct" to targetPct,
            "target_floor_pct" to ForecastQualityPolicy.MIN_TARGET_PCT,
            "multify_mfe_avg_pct" to learned.longAveragePct,
            "prior_forecast_mfe_samples" to priorMfe.size
        ))
        return all.map { it.toForecastDto() }
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
        val now = ZonedDateTime.now(INDIA)
        require(now.dayOfWeek.value < 6 && marketSession() == "OPEN") { "Forecast BUY is available only during the NSE regular session" }
        val settings = preferences.settings.first()
        preArmGuard(settings)
        val token = ensureToken() ?: error("Authenticate Groww first")
        val row = learningDao.longForecastsForDate(now.toLocalDate().toString())
            .firstOrNull { it.symbol.equals(symbol, true) } ?: error("No active LONG recommendation for $symbol")
        require(row.status == "ACTIVE") { "$symbol forecast is no longer active" }
        require(row.targetPct + 1e-9 >= ForecastQualityPolicy.MIN_TARGET_PCT) { "Forecast target is below the 1% quality floor" }
        if (managedDao.openPositions().any { it.symbol.equals(row.symbol, true) && it.side == "LONG" }) {
            return "${row.symbol} is already app-owned; duplicate BUY blocked"
        }
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol).requirePayload("Quote ${row.symbol}")
        val ltp = quote.lastPrice ?: error("Groww quote has no LTP")
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val available = (margin.equity?.cncBalanceAvailable ?: margin.clearCash).coerceAtLeast(0.0)
        val sizing = CashAwareCncSizing.size(settings.holdingBudgetRupees.toDouble(), available, ltp)
        val capital = sizing.deployableCash
        val qty = sizing.quantity
        auditLogger.log("EXECUTION", "CNC_BUY_SIZED", mapOf(
            "symbol" to row.symbol, "available_cnc" to available, "configured_budget" to settings.holdingBudgetRupees,
            "cash_reserve" to sizing.cashReserve, "reference_price" to ltp,
            "buffered_price" to sizing.bufferedPrice, "deployable_cash" to capital, "quantity" to qty
        ))
        require(qty > 0) { "Safe CNC buying power cannot fund one share of ${row.symbol} after RMS reserve" }
        val ref = stableRef("FLH", "${row.id}-${row.symbol}")
        val order = placeCncBuy(token, row.symbol, qty, ref)
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0.0 } ?: ltp
        val investedAmount = entry * filled
        val multifyStats = learningStats()
        val trailArmPct = LearnedTrailingPolicy.dynamicArmPct(multifyStats.longAveragePct)
        val minimumNetProfit = LearnedTrailingPolicy.minimumNetProfitRupees(entry, filled, trailArmPct)
        val minimumProfitExit = LearnedTrailingPolicy.minimumExitPriceForNetProfitPct(entry, trailArmPct)
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FORECAST_HOLDING, symbol = row.symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = entry * (1.0 - HOLDING_FAILSAFE_STOP_PCT),
                targetPrice = entry * (1.0 + row.targetPct / 100.0),
                strategy = "Forecast holding · ${row.strategy} · dynamic Multify-MFE trail",
                regime = row.regime, confidence = row.confidence,
                sourceEventId = null, openOrderId = order.growwOrderId, openReferenceId = ref,
                openedAtMs = System.currentTimeMillis(), lastPrice = entry,
                maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry, capitalDeployed = investedAmount, campaignBudget = capital,
                trailingArmPct = trailArmPct, trailingLastRatchetPrice = 0.0
            )
        )
        auditLogger.log("FORECAST", "LONG_HOLDING_OPENED", mapOf(
            "symbol" to row.symbol, "qty" to filled, "entry" to entry,
            "forecast_target_pct" to row.targetPct,
            "invested_amount" to investedAmount,
            "multify_mfe_avg_pct" to multifyStats.longAveragePct,
            "trail_arm_pct" to trailArmPct,
            "minimum_net_profit" to minimumNetProfit,
            "minimum_profit_exit_price" to minimumProfitExit
        ))
        return "BUY ${row.symbol} opened as CNC holding · qty $filled @ ₹${fmt(entry)} · forecast target +${fmt(row.targetPct)}% · trailing arms at +${fmt(trailArmPct)}% estimated net"
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
                it.strategy == row.strategy && it.status in setOf("TARGET_HIT", "MISSED")
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

    suspend fun runAfterHoursResearch(force: Boolean = false): ResearchDto {
        ensureHistoricalSeed()
        ensureToken()?.let { token ->
            runCatching { backfillRecentMultifyMfe(token, MFE_AFTER_HOURS_BACKFILL_BATCH_SIZE, force = true) }
        }
        val today = LocalDate.now(INDIA).toString()
        val existing = learningDao.latestResearchReport()
        if (!force && existing?.reportDate == today) return ResearchDto(existing.reportDate, existing.title, existing.summary)
        val stats = learningStats()
        val forecastLearning = forecastLearningStats()
        val forecasts = learningDao.longForecastsForDate(today)
        val wins = forecasts.count { it.status == "TARGET_HIT" }
        val matched = forecasts.count { it.multifyMatched }
        val frozen = learningDao.allForecastChampions().count { it.frozen }
        val avgTarget = forecasts.map { it.targetPct }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val report = buildString {
            append("Rolling 30-trading-day Multify MFE average: ${fmt(stats.longAveragePct)}% (median ${fmt(stats.longMedianPct)}%) from ${stats.rollingCalls} MFE-observed calls. ")
            append("Pending historical MFE backfill: ${stats.mfeBackfillPendingCalls}. ")
            append(
                if (forecastLearning.completedForecasts > 0)
                    "Rolling forecast full-session MFE average: ${fmt(forecastLearning.longAveragePct)}% across ${forecastLearning.completedForecasts} completed forecasts. "
                else
                    "Rolling forecast MFE is still learning. "
            )
            append("Today's average forecast target: ${fmt(avgTarget)}% with a hard 1.00% floor. ")
            append("Today target hits: $wins/${forecasts.size}. ")
            append("Multify later matched $matched/${forecasts.size} forecast symbols; matching is secondary research, not the success label. ")
            append("Frozen LONG champions: $frozen. ")
            append("Only quality-gated TREND candidates are admitted; no weak call is forced to fill a slot.")
        }
        val entity = com.multify.traderpro.data.local.ResearchReportEntity(
            reportDate = today, generatedAtMs = System.currentTimeMillis(),
            title = "After-market LONG forecast review", summary = report
        )
        learningDao.insertResearchReport(entity)
        auditLogger.log("RESEARCH", "AFTER_HOURS_LONG_REPORT", mapOf(
            "date" to today, "wins" to wins, "total" to forecasts.size, "matches" to matched,
            "multify_mfe_avg_pct" to stats.longAveragePct, "forecast_avg_target_pct" to avgTarget
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
        val now = System.currentTimeMillis()
        if (!marketOpen && now - lastClosedSessionReconcileAtMs < CLOSED_SESSION_RECONCILE_MS) return open.size
        if (!marketOpen) lastClosedSessionReconcileAtMs = now
        val multifyStats = learningStats()
        val dynamicTrailArmPct = LearnedTrailingPolicy.dynamicArmPct(multifyStats.longAveragePct)
        for (position in open) {
            runCatching {
                if (position.side != "LONG") {
                    if (marketOpen) closeLegacyNonLong(token, position)
                    return@runCatching
                }
                val reconciled = reconcilePositionWithBroker(token, position, preferredMark = null, force = !marketOpen)
                if (marketOpen && reconciled != null) monitorLongHolding(reconciled, token, dynamicTrailArmPct)
            }.onFailure {
                auditLogger.log("HOLDING", "MONITOR_ERROR", mapOf("symbol" to position.symbol, "message" to (it.message ?: it.javaClass.simpleName)))
            }
        }
        return managedDao.openPositions().size
    }

    private suspend fun monitorLongHolding(position: ManagedPositionEntity, token: String, dynamicTrailArmPct: Double) {
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = position.symbol).requirePayload("Quote ${position.symbol}")
        val ltp = quote.lastPrice ?: return
        preferences.recordMarketData()
        val previousHighWater = position.maxFavourablePrice
        var updated = position.copy(
            lastPrice = ltp,
            maxFavourablePrice = max(position.maxFavourablePrice, ltp),
            maxAdversePrice = min(position.maxAdversePrice, ltp),
            trailingArmPct = dynamicTrailArmPct,
            targetPrice = if (position.engine == ENGINE_MULTIFY_HOLDING)
                LearnedTrailingPolicy.minimumExitPriceForNetProfitPct(position.entryPrice, dynamicTrailArmPct)
            else position.targetPrice,
            lastEvaluatedAtMs = System.currentTimeMillis()
        )
        managedDao.updatePosition(updated)

        val minimumNetProfit = LearnedTrailingPolicy.minimumNetProfitRupees(
            updated.entryPrice, updated.quantity, dynamicTrailArmPct
        )
        val minimumProfitStop = LearnedTrailingPolicy.minimumExitPriceForNetProfitPct(
            updated.entryPrice, dynamicTrailArmPct
        )
        val rawStop = updated.stopPrice
        val rawArmed = LearnedTrailingPolicy.isArmed(rawStop, updated.entryPrice)
        val rawStopProtectsGate = rawStop != null &&
            LearnedTrailingPolicy.estimatedNetProfit(updated.entryPrice, rawStop, updated.quantity) + 0.01 >= minimumNetProfit

        // Any old armed stop that no longer protects the current Multify-MFE threshold
        // is reset to the emergency failsafe. This keeps upgrades and rising MFE averages safe.
        if (rawArmed && !rawStopProtectsGate) {
            val migratedStop = updated.entryPrice * (1.0 - HOLDING_FAILSAFE_STOP_PCT)
            updated = updated.copy(
                stopPrice = migratedStop,
                trailingLastRatchetPrice = 0.0,
                lastEvaluatedAtMs = System.currentTimeMillis()
            )
            managedDao.updatePosition(updated)
            auditLogger.log("TRAILING_STOP", "TRAIL_RESET_BELOW_DYNAMIC_MFE_GATE", mapOf(
                "symbol" to updated.symbol,
                "old_stop" to rawStop,
                "new_failsafe_stop" to migratedStop,
                "multify_mfe_arm_pct" to dynamicTrailArmPct,
                "minimum_net_profit" to minimumNetProfit,
                "minimum_profit_exit_price" to minimumProfitStop
            ))
        }

        val hardStop = updated.stopPrice
        if (hardStop != null && !LearnedTrailingPolicy.isArmed(hardStop, updated.entryPrice) && ltp <= hardStop) {
            closeLongHolding(token, updated, ltp, "HOLDING_FAILSAFE_STOP")
            return
        }

        val atr = currentOneMinuteAtr(updated.symbol, token, ltp)
        val highWater = updated.maxFavourablePrice
        val currentStop = updated.stopPrice
        val alreadyArmed = currentStop != null &&
            LearnedTrailingPolicy.isArmed(currentStop, updated.entryPrice) &&
            LearnedTrailingPolicy.estimatedNetProfit(updated.entryPrice, currentStop, updated.quantity) + 0.01 >= minimumNetProfit
        val profitGateReached = LearnedTrailingPolicy.profitGateReached(
            updated.entryPrice, highWater, updated.quantity, dynamicTrailArmPct
        )
        val canProtectProfitGate = highWater + 1e-9 >= minimumProfitStop + 0.01
        val thresholdReached = alreadyArmed || (profitGateReached && canProtectProfitGate)

        if (thresholdReached && LearnedTrailingPolicy.shouldRatchet(alreadyArmed, updated.trailingLastRatchetPrice, highWater)) {
            val previousRatchetHigh = updated.trailingLastRatchetPrice
            val candidateStop = LearnedTrailingPolicy.ratchetStop(
                entryPrice = updated.entryPrice,
                highWaterPrice = highWater,
                atr = atr,
                currentStop = currentStop,
                lastRatchetPrice = previousRatchetHigh,
                minimumProtectedStop = minimumProfitStop
            )
            val stopRaised = !alreadyArmed || candidateStop > (currentStop ?: updated.entryPrice) + .01
            val effectiveStop = if (stopRaised) candidateStop else currentStop
            updated = updated.copy(
                stopPrice = effectiveStop,
                trailingLastRatchetPrice = highWater,
                trailingArmPct = dynamicTrailArmPct,
                lastEvaluatedAtMs = System.currentTimeMillis()
            )
            managedDao.updatePosition(updated)
            val projectedNetAtStop = effectiveStop?.let {
                LearnedTrailingPolicy.estimatedNetProfit(updated.entryPrice, it, updated.quantity)
            }
            auditLogger.log("TRAILING_STOP", if (alreadyArmed) "LONG_RATCHET" else "LONG_ARMED_DYNAMIC_MFE", mapOf(
                "symbol" to updated.symbol,
                "ltp" to ltp,
                "old_high_water" to previousHighWater,
                "new_high_water" to highWater,
                "previous_ratchet_high" to previousRatchetHigh,
                "ratchet_advance" to if (previousRatchetHigh > 0.0) highWater - previousRatchetHigh else highWater - updated.entryPrice,
                "old_stop" to currentStop,
                "new_stop" to effectiveStop,
                "stop_raised" to stopRaised,
                "multify_mfe_arm_pct" to dynamicTrailArmPct,
                "invested_amount" to LearnedTrailingPolicy.investedAmount(updated.entryPrice, updated.quantity),
                "minimum_net_profit" to minimumNetProfit,
                "projected_net_at_stop" to projectedNetAtStop,
                "minimum_profit_exit_price" to minimumProfitStop,
                "atr_1m" to atr,
                "trail_distance" to LearnedTrailingPolicy.trailDistance(highWater, atr, updated.entryPrice)
            ))
        }

        val activeStop = updated.stopPrice
        if (LearnedTrailingPolicy.isArmed(activeStop, updated.entryPrice) && activeStop != null && ltp <= activeStop) {
            val protectedNetAtStop = LearnedTrailingPolicy.estimatedNetProfit(
                updated.entryPrice, activeStop, updated.quantity
            )
            if (protectedNetAtStop + 0.01 >= minimumNetProfit) {
                auditLogger.log("TRAILING_STOP", "LONG_TRAILING_EXIT_TRIGGERED_DYNAMIC_MFE", mapOf(
                    "symbol" to updated.symbol,
                    "ltp" to ltp,
                    "active_stop" to activeStop,
                    "protected_net_at_stop" to protectedNetAtStop,
                    "minimum_net_profit" to minimumNetProfit,
                    "multify_mfe_arm_pct" to dynamicTrailArmPct
                ))
                closeLongHolding(token, updated, ltp, "LONG_TRAILING_PROFIT_STOP_DYNAMIC_MFE")
            } else {
                auditLogger.log("TRAILING_STOP", "LONG_TRAILING_EXIT_BLOCKED_BELOW_DYNAMIC_MFE", mapOf(
                    "symbol" to updated.symbol,
                    "ltp" to ltp,
                    "active_stop" to activeStop,
                    "protected_net_at_stop" to protectedNetAtStop,
                    "minimum_net_profit" to minimumNetProfit,
                    "multify_mfe_arm_pct" to dynamicTrailArmPct
                ))
            }
        }
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
        notificationStartedNs: Long? = null
    ): ManagedTradeEntity {
        require(position.side == "LONG") { "Only LONG holdings are supported" }
        val ref = stableRef("CLS", "${position.id}-${position.symbol}-$reason")
        val order = placeOwnedCncSell(token, position, ref, notificationStartedNs)
        val exit = order.averageFillPrice?.takeIf { it > 0.0 } ?: mark
        val gross = (exit - position.entryPrice) * position.quantity
        val costs = estimatedCashCosts(position.entryPrice, exit, position.quantity)
        val trade = ManagedTradeEntity(
            engine = position.engine, symbol = position.symbol, product = "CNC", side = "LONG",
            quantity = position.quantity, entryPrice = position.entryPrice, exitPrice = exit,
            grossPnl = gross, estimatedCosts = costs, netPnl = gross - costs, exitReason = reason,
            strategy = position.strategy, regime = position.regime, confidence = position.confidence,
            mfeRupees = max(0.0, position.maxFavourablePrice - position.entryPrice) * position.quantity,
            maeRupees = max(0.0, position.entryPrice - position.maxAdversePrice) * position.quantity,
            sourceEventId = position.sourceEventId, openOrderId = position.openOrderId, closeOrderId = order.growwOrderId,
            openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
        )
        managedDao.insertTrade(trade)
        managedDao.deletePosition(position.id)
        auditLogger.log("HOLDING", "LONG_CLOSED", mapOf("symbol" to position.symbol, "reason" to reason, "exit" to exit))
        return trade
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
                apiFactory.groww.quote(bearer(token), tradingSymbol = p.symbol).requirePayload("Quote ${p.symbol}").lastPrice
            }.getOrNull() ?: p.lastPrice
            val pnl = (ltp - p.entryPrice) * p.quantity
            PositionDto(
                symbol = p.symbol, quantity = p.quantity, averagePrice = p.entryPrice, ltp = ltp, pnl = pnl,
                stopPrice = p.stopPrice, targetPrice = p.targetPrice, highWaterPrice = p.maxFavourablePrice,
                trailingLastRatchetPrice = p.trailingLastRatchetPrice.takeIf { it > 0.0 },
                trailDistance = if (LearnedTrailingPolicy.isArmed(p.stopPrice, p.entryPrice) && p.stopPrice != null)
                    (p.maxFavourablePrice - p.stopPrice).coerceAtLeast(0.0) else null,
                trailArmPct = p.trailingArmPct.takeIf { it.isFinite() && it > 0.0 },
                trailArmed = LearnedTrailingPolicy.isArmed(p.stopPrice, p.entryPrice),
                strategy = p.strategy, product = "CNC"
            )
        }
        val recent = managedDao.tradesSince(startOfIndiaDayMs()).filter { it.side == "LONG" }.take(6).map { t ->
            RecentDecisionDto(formatEventTime(t.closedAtMs), t.symbol, "LONG_CLOSED", t.exitReason, t.strategy, t.quantity)
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
        val offsets = listOf(1, 5, 30, 60, 120, 300)
        var elapsed = 0
        for (offset in offsets) {
            delay((offset - elapsed) * 1000L)
            elapsed = offset
            val price = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Signal sample $symbol").lastPrice
            }.getOrNull() ?: continue
            learningDao.insertObservation(
                PriceObservationEntity(
                    eventId = eventId, symbol = symbol,
                    phase = if (event.signalType == SignalType.TRADE_RELEASE.name) "POST_LONG_CALL" else "POST_LONG_EXIT",
                    offsetSeconds = offset, observedAtMs = System.currentTimeMillis(), price = price
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
        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }.map {
            mapOf(
                "date" to it.callDate, "symbol" to it.symbol, "source" to it.source,
                "entry_price" to it.entryPrice, "target_price" to it.targetPrice, "target_pct" to it.targetPct,
                "exit_price" to it.multifyExitPrice, "long_return_pct" to it.longRealizedPct,
                "multify_max_price" to it.multifyMaxPrice, "multify_mfe_pct" to it.multifyMfePct
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
                add("long_learning.json", calls)
                add("long_forecasts.json", forecasts)
                add("long_champions.json", champions)
                add("research.json", research)
                addText("system_audit.jsonl", auditLogger.readAll())
            }
        } ?: error("Unable to open export destination")
        return LogExportResult(10, signals.size, trades.size)
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
            auditLogger.log("RUNTIME", "ARM_HOT_PATH_READY", mapOf("cnc_balance" to cnc, "armed" to settings.armEffective))
            lastArmHotAuditAtMs = now
            lastArmHotAuditBalance = cnc
            lastArmHotAuditArmed = settings.armEffective
        }
        return true
    }

    suspend fun maintainArmHotPath() {
        val settings = preferences.settings.first()
        if (!settings.armEffective || settings.safetyHalt) return
        if (armHotState == null || System.currentTimeMillis() - (armHotState?.warmedAtMs ?: 0L) > ARM_HOT_REFRESH_MS) {
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

    private fun LongForecastEntity.toForecastDto() = ForecastDto(
        rank = rank, symbol = symbol, confidence = confidence, score = score, reason = reason,
        multifyMatched = multifyMatched, entryPrice = entryPrice, targetPrice = targetPrice, targetPct = targetPct,
        status = status, strategy = strategy, regime = regime, marketRegime = marketRegime,
        championTag = championKey, maxFavourablePct = maxFavourablePct, maxAdversePct = maxAdversePct,
        generatedAtMs = generatedAtMs
    )

    private fun championKey(marketRegime: String, regime: String, strategy: String): String =
        listOf("LONG", marketRegime, regime, strategy).joinToString("|") { it.uppercase(Locale.US).replace("|", "/") }

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
        private const val FORECAST_SCAN_INTERVAL_MS = 10L * 60L * 1000L
        private const val FORECAST_OUTCOME_INTERVAL_MS = 30_000L
        private const val MFE_BACKFILL_INTERVAL_MS = 5L * 60L * 1000L
        private const val MFE_BACKFILL_BATCH_SIZE = 4
        private const val MFE_AFTER_HOURS_BACKFILL_BATCH_SIZE = 16
        private const val HOLDING_FAILSAFE_STOP_PCT = 0.12
        private const val ARM_HOT_REFRESH_MS = 30_000L
        private const val ARM_HOT_AUDIT_INTERVAL_MS = 15L * 60L * 1000L
        private const val ARM_HOT_STATE_MAX_AGE_MS = 120_000L
        private const val ONE_MINUTE_ATR_CACHE_MS = 60_000L
        private const val EXECUTION_SAMPLE_LIMIT = 30
        private const val BROKER_INVENTORY_REFRESH_MS = 5_000L
        private const val CLOSED_SESSION_RECONCILE_MS = 60_000L
        private const val MULTIFY_PACKAGE = "com.multyfi.invest"
        private const val UPLOADED_THREE_MONTH_REALIZED_PCT = 0.49019417475728155
        private const val DEFAULT_LONG_REALIZED_PCT = 0.33
        const val ENGINE_MULTIFY_HOLDING = "MULTIFY_HOLDING"
        const val ENGINE_FORECAST_HOLDING = "FORECAST_HOLDING"
    }
}
