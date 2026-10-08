package com.multify.traderpro.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.gson.GsonBuilder
import com.multify.traderpro.BuildConfig
import com.multify.traderpro.data.logging.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.google.gson.JsonParser
import com.multify.traderpro.data.local.ManagedPositionEntity
import com.multify.traderpro.data.local.ManagedTradeDao
import com.multify.traderpro.data.local.ManagedTradeEntity
import com.multify.traderpro.data.local.LearningDao
import com.multify.traderpro.data.local.LearningCallEntity
import com.multify.traderpro.data.local.PriceObservationEntity
import com.multify.traderpro.data.local.StrategySnapshotEntity
import com.multify.traderpro.data.local.ForecastEntity
import com.multify.traderpro.data.local.ResearchReportEntity
import com.multify.traderpro.data.local.ShadowDao
import com.multify.traderpro.data.local.ShadowPositionEntity
import com.multify.traderpro.data.local.ShadowTradeEntity
import com.multify.traderpro.data.local.SignalEventDao
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.local.WaveCampaignEntity
import com.multify.traderpro.data.local.WaveObservationEntity
import com.multify.traderpro.data.network.BrokerStatusDto
import com.multify.traderpro.data.network.DashboardDto
import com.multify.traderpro.data.network.DaySummaryDto
import com.multify.traderpro.data.network.GrowwApiFactory
import com.multify.traderpro.data.network.GrowwPosition
import com.multify.traderpro.data.network.OcoCreateRequest
import com.multify.traderpro.data.network.OcoLeg
import com.multify.traderpro.data.network.OcoModifyLeg
import com.multify.traderpro.data.network.OcoModifyRequest
import com.multify.traderpro.data.network.OrderCreateRequest
import com.multify.traderpro.data.network.OrderPayload
import com.multify.traderpro.data.network.PositionDto
import com.multify.traderpro.data.network.RecentDecisionDto
import com.multify.traderpro.data.network.RiskStatusDto
import com.multify.traderpro.data.network.LearningStatsDto
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.ResearchDto
import com.multify.traderpro.data.network.StrategyInsightDto
import com.multify.traderpro.data.network.WaveSignalDto
import com.multify.traderpro.data.network.WaveStatDto
import com.multify.traderpro.data.network.TokenRequest
import com.multify.traderpro.data.preferences.AppPreferences
import com.multify.traderpro.data.preferences.AppSettings
import com.multify.traderpro.data.preferences.SecretStore
import com.multify.traderpro.data.security.TotpGenerator
import com.multify.traderpro.domain.NotificationParser
import com.multify.traderpro.domain.ParsedSignal
import com.multify.traderpro.domain.SignalType
import com.multify.traderpro.engine.BudgetAllocator
import com.multify.traderpro.engine.FixedCapitalPolicy
import com.multify.traderpro.engine.AdaptiveLearningMath
import com.multify.traderpro.engine.LocalStrategyEngine
import com.multify.traderpro.engine.MarketTrajectoryMath
import com.multify.traderpro.engine.StrategyEvaluation
import com.multify.traderpro.engine.WaveDirectionDecision
import com.multify.traderpro.engine.WaveDirectionMath
import com.multify.traderpro.engine.WavePivotMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val misBalanceAvailable: Double,
    val ddpiEnabled: Boolean,
    val detail: String
)

@Singleton
class TradingRepository @Inject constructor(
    private val dao: SignalEventDao,
    private val shadowDao: ShadowDao,
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
    private var lastHistoricalShortBackfillAttemptMs: Long = 0L
    @Volatile private var historicalSeedEnsured: Boolean = false


    suspend fun ensureHistoricalSeed() {
        if (historicalSeedEnsured) return
        val now = System.currentTimeMillis()
        var inserted = 0
        var refreshed = 0
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
                val historicalRealized = x[8].toDoubleOrNull()
                val buyAtMs = x[9].toLongOrNull() ?: 0L
                val sellAtMs = x[10].toLongOrNull()?.takeIf { it > 0L }
                val existing = learningDao.historicalCallForKey(callDate, symbol, action)
                if (existing == null) {
                    learningDao.insertCall(
                        LearningCallEntity(
                            callDate = callDate, symbol = symbol, source = source, action = action,
                            entryPrice = entry, targetPrice = targetPrice, targetPct = targetPct,
                            multifyExitPrice = exit, longRealizedPct = historicalRealized,
                            buyAtMs = buyAtMs, sellAtMs = sellAtMs, updatedAtMs = now
                        )
                    )
                    inserted++
                } else {
                    learningDao.updateCall(
                        existing.copy(
                            source = source, entryPrice = entry, targetPrice = targetPrice, targetPct = targetPct,
                            multifyExitPrice = exit ?: existing.multifyExitPrice,
                            longRealizedPct = historicalRealized ?: existing.longRealizedPct,
                            buyAtMs = buyAtMs.takeIf { it > 0L } ?: existing.buyAtMs,
                            sellAtMs = sellAtMs ?: existing.sellAtMs,
                            updatedAtMs = now
                        )
                    )
                    refreshed++
                }
            }
        }
        historicalSeedEnsured = true
        if (inserted > 0 || refreshed > 0) {
            auditLogger.log(
                "LEARNING", "HISTORY_SEEDED",
                mapOf("inserted" to inserted, "refreshed" to refreshed, "rows" to learningDao.callCount(), "source" to "uploaded_3_month_history")
            )
        }
    }

    suspend fun learningStats(): LearningStatsDto {
        ensureHistoricalSeed()
        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }
        val dateOrder = calls.mapNotNull { runCatching { LocalDate.parse(it.callDate) }.getOrNull() }.distinct().sortedDescending()
        val keepDates = dateOrder.take(30).map { it.toString() }.toSet()
        val rolling = calls.filter { it.callDate in keepDates }
        fun targetPct(c: LearningCallEntity): Double? = c.targetPct ?: c.targetPrice?.let { target ->
            if (c.entryPrice > 0.0) (target - c.entryPrice) / c.entryPrice * 100.0 else null
        }
        val longValues = rolling.mapNotNull(::targetPct).filter { it > 0.0 && it < 25.0 }
        val sorted = longValues.sorted()
        val longAvg = if (longValues.isEmpty()) DEFAULT_LONG_TARGET_PCT else longValues.average()
        val longMedian = when {
            sorted.isEmpty() -> DEFAULT_LONG_TARGET_PCT
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size/2 - 1] + sorted[sorted.size/2]) / 2.0
        }
        val seed3m = calls.filter { it.source == "SEED" }.mapNotNull(::targetPct).filter { it > 0.0 }.let {
            if (it.isEmpty()) UPLOADED_THREE_MONTH_TARGET_PCT else it.average()
        }
        val shortRows = rolling.filter { it.shortObservationFinalized }.mapNotNull { call ->
            call.postSellRetracementFraction?.takeIf { it >= 0.0 && (call.multifyExitPrice ?: 0.0) > call.entryPrice }
                ?.let { call.callDate to it.coerceIn(0.0, 1.0) }
        }
        // Each trading day gets one vote so a day with several Multify calls cannot dominate the learner.
        val shortDaily = shortRows.groupBy({ it.first }, { it.second }).values.map { day -> day.average() }
        val shortFraction = AdaptiveLearningMath.shortRetracementFraction(shortDaily)
        return LearningStatsDto(
            rollingTradingDays = keepDates.size,
            rollingCalls = rolling.size,
            longAveragePct = longAvg,
            longMedianPct = longMedian,
            seededThreeMonthAveragePct = seed3m,
            liveCompletedCalls = calls.count { it.source == "LIVE" && it.longRealizedPct != null },
            shortObservedCalls = shortRows.size,
            shortRetracementFraction = shortFraction,
            shortRetracementPct = shortFraction * 100.0
        )
    }

    private suspend fun recordLiveBuy(eventId: Long, signal: ParsedSignal, price: Double) {
        val symbol = signal.symbol ?: return
        ensureHistoricalSeed()
        val targetPct = signal.target?.let { ((it - price) / price * 100.0).coerceAtLeast(0.0) }
        learningDao.insertCall(
            LearningCallEntity(
                callDate = LocalDate.now(INDIA).toString(), symbol = symbol, source = "LIVE", action = "BUY",
                entryPrice = price, targetPrice = signal.target, targetPct = targetPct, buyEventId = eventId,
                buyAtMs = System.currentTimeMillis(), updatedAtMs = System.currentTimeMillis()
            )
        )
        learningDao.markForecastMatch(LocalDate.now(INDIA).toString(), symbol, "LONG")
    }

    private suspend fun recordLiveSell(eventId: Long, symbol: String, price: Double) {
        learningDao.markForecastMatch(LocalDate.now(INDIA).toString(), symbol, "SHORT")
        val call = learningDao.latestLiveCall(symbol) ?: return
        if (call.sellAtMs != null) return
        val pct = if (call.entryPrice > 0) (price - call.entryPrice) / call.entryPrice * 100.0 else null
        learningDao.updateCall(
            call.copy(
                multifyExitPrice = price, longRealizedPct = pct, sellEventId = eventId,
                sellAtMs = System.currentTimeMillis(), minPostSellPrice = price,
                postSellRetracementFraction = 0.0, updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun recordAdaptiveExit(symbol: String, price: Double) {
        val call = learningDao.latestLiveCall(symbol) ?: return
        if (call.sellAtMs != null) return
        val pct = if (call.entryPrice > 0) (price - call.entryPrice) / call.entryPrice * 100.0 else null
        learningDao.updateCall(
            call.copy(
                multifyExitPrice = price, longRealizedPct = pct, sellAtMs = System.currentTimeMillis(),
                minPostSellPrice = price, postSellRetracementFraction = 0.0, updatedAtMs = System.currentTimeMillis()
            )
        )
        auditLogger.log("LEARNING", "ADAPTIVE_LONG_EXIT_ANCHOR", mapOf("symbol" to symbol, "exit" to price, "return_pct" to pct))
    }

    suspend fun monitorLearningObservations(): Int {
        ensureHistoricalSeed()
        maybeBackfillHistoricalShortLearning()
        val active = learningDao.activePostSellObservations()
        if (active.isEmpty()) return 0
        val token = ensureToken() ?: return active.size
        val now = ZonedDateTime.now(INDIA)
        active.forEach { call ->
            val sell = call.multifyExitPrice ?: return@forEach
            val ltp = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = call.symbol).requirePayload("Quote ${call.symbol}").lastPrice
            }.getOrNull() ?: return@forEach
            val low = min(call.minPostSellPrice ?: sell, ltp)
            val longMove = sell - call.entryPrice
            val fraction = if (longMove > 0.0) ((sell - low) / longMove).coerceIn(0.0, 1.0) else null
            val callDate = runCatching { LocalDate.parse(call.callDate) }.getOrNull()
            val finalize = callDate != now.toLocalDate() || now.toLocalTime() >= LocalTime.of(15, 22)
            learningDao.updateCall(call.copy(
                minPostSellPrice = low,
                postSellRetracementFraction = fraction,
                shortObservationFinalized = finalize,
                updatedAtMs = System.currentTimeMillis()
            ))
        }
        return learningDao.activePostSellObservations().size
    }

    private suspend fun maybeBackfillHistoricalShortLearning() {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastHistoricalShortBackfillAttemptMs < HISTORICAL_SHORT_BACKFILL_INTERVAL_MS) return
        lastHistoricalShortBackfillAttemptMs = nowMs
        val token = ensureToken() ?: return
        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }
        val keepDates = calls.mapNotNull { runCatching { LocalDate.parse(it.callDate) }.getOrNull() }
            .distinct().sortedDescending().take(30).map { it.toString() }.toSet()
        val pending = calls.asSequence()
            .filter { it.source == "SEED" && it.callDate in keepDates && !it.shortObservationFinalized }
            .filter { (it.sellAtMs ?: 0L) > 0L && (it.multifyExitPrice ?: 0.0) > it.entryPrice }
            .take(MAX_HISTORICAL_SHORT_BACKFILLS_PER_PASS)
            .toList()
        var completed = 0
        for (call in pending) {
            val sellAt = call.sellAtMs ?: continue
            val sell = call.multifyExitPrice ?: continue
            val sessionDate = runCatching { LocalDate.parse(call.callDate) }.getOrNull() ?: continue
            val start = Instant.ofEpochMilli(sellAt).atZone(INDIA).toLocalDateTime().format(HIST_FORMAT)
            val end = sessionDate.atTime(15, 30).format(HIST_FORMAT)
            val payload = runCatching {
                apiFactory.groww.historicalCandles(
                    authorization = bearer(token), growwSymbol = "NSE-${call.symbol}",
                    startTime = start, endTime = end, candleInterval = "5minute"
                ).requirePayload("Historical post-sell candles ${call.symbol}")
            }.getOrNull() ?: continue
            val lows = payload.candles.mapNotNull { candle ->
                if (candle.size < 4) null else runCatching { candle[3].asDouble }.getOrNull()
            }
            if (lows.isEmpty()) continue
            val low = min(sell, lows.minOrNull() ?: sell)
            val longMove = sell - call.entryPrice
            if (longMove <= 0.0) continue
            val fraction = ((sell - low) / longMove).coerceIn(0.0, 1.0)
            learningDao.updateCall(call.copy(
                minPostSellPrice = low, postSellRetracementFraction = fraction,
                shortObservationFinalized = true, updatedAtMs = System.currentTimeMillis()
            ))
            completed++
            delay(HISTORICAL_SHORT_BACKFILL_REQUEST_DELAY_MS)
        }
        if (completed > 0) {
            auditLogger.log("LEARNING", "HISTORICAL_SHORT_BACKFILL", mapOf("completed" to completed, "window_days" to keepDates.size))
        }
    }

    suspend fun sampleSignal(eventId: Long) {
        val event = dao.byId(eventId) ?: return
        val symbol = event.symbol ?: return
        val type = runCatching { SignalType.valueOf(event.signalType) }.getOrNull() ?: return
        if (type != SignalType.TRADE_RELEASE && type != SignalType.BOOK_PROFIT) return
        val token = ensureToken() ?: return
        val phase = if (type == SignalType.TRADE_RELEASE) "POST_BUY" else "POST_SELL"
        val offsets = listOf(1,2,5,10,15,30,60,120,300)
        var elapsed = 0
        for (offset in offsets) {
            delay((offset - elapsed) * 1000L)
            elapsed = offset
            val price = runCatching { apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol").lastPrice }.getOrNull() ?: continue
            learningDao.insertObservation(PriceObservationEntity(eventId = eventId, symbol = symbol, phase = phase, offsetSeconds = offset, observedAtMs = System.currentTimeMillis(), price = price))
        }
    }

    suspend fun prepareHotMode(): String {
        ensureHistoricalSeed()
        val token = ensureToken()
        runCatching { if (nseSymbols.isStale()) nseSymbols.refresh() }
        val result = if (token != null) "HOT · Groww session ready · NSE master checked" else "HOT · signal listener ready · Groww authentication pending"
        auditLogger.log("RUNTIME", "PRE_ALERT_HOT_MODE", mapOf("result" to result))
        return result
    }

    suspend fun generateDailyForecasts(force: Boolean = false): List<ForecastDto> {
        ensureHistoricalSeed()
        val today = LocalDate.now(INDIA).toString()
        if (!force) {
            val existing = learningDao.forecastsForDate(today)
            if (existing.size >= 5) return existing.map { it.toDto() }
        }
        val token = ensureToken() ?: return learningDao.forecastsForDate(today).map { it.toDto() }
        val calls = learningDao.allCalls().filter { it.action.equals("BUY", true) }
        val dates = calls.map { it.callDate }.distinct().sortedDescending().take(30).toSet()
        val rolling = calls.filter { it.callDate in dates }
        val candidates = rolling.groupBy { it.symbol }
            .map { (symbol, xs) -> Triple(symbol, xs.size, xs.maxOf { it.callDate }) }
            .sortedWith(compareByDescending<Triple<String,Int,String>> { it.second }.thenByDescending { it.third })
            .take(18)
        val scored = mutableListOf<ForecastEntity>()
        for ((symbol, count, _) in candidates) {
            val eval = runCatching { forecastEvaluation(symbol, token) }.getOrNull() ?: continue
            val chosen = if (eval.first.confidence >= eval.second.confidence) eval.first else eval.second
            val frequencyBonus = min(.08, count * .008)
            val score = (chosen.confidence + frequencyBonus).coerceAtMost(.99)
            scored += ForecastEntity(
                forecastDate = today, rank = 0, symbol = symbol, bias = chosen.side,
                confidence = chosen.confidence, score = score,
                reason = "${chosen.strategy} · ${chosen.regime} · ${chosen.reason}", generatedAtMs = System.currentTimeMillis()
            )
        }
        val ranked = scored.sortedByDescending { it.score }.toMutableList()
        if (ranked.size < 5) {
            val have = ranked.map { it.symbol }.toSet()
            candidates.filter { it.first !in have }.take(5 - ranked.size).forEach { (symbol, count, _) ->
                ranked += ForecastEntity(
                    forecastDate = today, rank = 0, symbol = symbol, bias = "LONG",
                    confidence = .50, score = (.50 + min(.08, count * .008)).coerceAtMost(.58),
                    reason = "Historical Multify-frequency fallback · live strategy snapshot unavailable",
                    generatedAtMs = System.currentTimeMillis()
                )
            }
        }
        val top = ranked.sortedByDescending { it.score }.take(5).mapIndexed { i, x -> x.copy(rank = i + 1) }
        learningDao.deleteForecasts(today)
        if (top.isNotEmpty()) learningDao.insertForecasts(top)
        auditLogger.log("FORECAST", "DAILY_FORECAST_GENERATED", mapOf("date" to today, "count" to top.size, "universe" to candidates.size))
        return top.map { it.toDto() }
    }

    private suspend fun forecastEvaluation(symbol: String, token: String): Pair<StrategyEvaluation, StrategyEvaluation> {
        val now = ZonedDateTime.now(INDIA)
        val start = now.minusDays(4).toLocalDate().atTime(9,15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
        val historical = apiFactory.groww.historicalCandles(bearer(token), growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "15minute").requirePayload("Forecast candles $symbol")
        require(historical.candles.size >= 5) { "Insufficient forecast candles" }
        val f = LocalStrategyEngine.buildFeatures(quote, historical, 15)
        val synthetic = ParsedSignal(SignalType.TRADE_RELEASE, symbol=symbol, rawText="forecast", confidence=1.0)
        return LocalStrategyEngine.evaluateLong(synthetic, f, includeEventPrior = false) to LocalStrategyEngine.evaluateShort(f, includeEventPrior = false)
    }

    private fun ForecastEntity.toDto() = ForecastDto(rank, symbol, bias, confidence, score, reason, multifyMatched, multifyDirectionMatched)

    suspend fun runAfterHoursResearch(force: Boolean = false): ResearchDto {
        ensureHistoricalSeed()
        val today = LocalDate.now(INDIA).toString()
        val existing = learningDao.latestResearchReport()
        if (!force && existing?.reportDate == today) return ResearchDto(existing.reportDate, existing.title, existing.summary)
        val stats = learningStats()
        val since = startOfIndiaDayMs()
        val shadowTrades = shadowDao.tradesSince(since)
        val strategyRows = shadowTrades.groupBy { it.strategy }.map { (name, xs) -> name to xs.sumOf { it.netPnl } }.sortedByDescending { it.second }
        val best = strategyRows.firstOrNull()
        val forecasts = learningDao.forecastsForDate(today)
        val matched = forecasts.count { it.multifyMatched }
        val report = buildString {
            append("Rolling 30-trading-day long reference: ${fmt(stats.longAveragePct)}% (median ${fmt(stats.longMedianPct)}%). ")
            append("Short retracement target: ${fmt(stats.shortRetracementPct)}% of preceding long move; ${stats.shortObservedCalls} learned observations. ")
            append("Forecast match today: $matched/${forecasts.size}. ")
            if (best != null) append("Best shadow strategy today: ${best.first} with net ₹${fmt(best.second)}. ")
            if (shadowTrades.isEmpty()) append("No closed shadow trades yet; next session remains a data-collection priority. ")
            append("Research keeps a rolling memory and does not promote a strategy from a single day.")
        }
        val entity = ResearchReportEntity(reportDate=today, generatedAtMs=System.currentTimeMillis(), title="After-market strategy review", summary=report)
        learningDao.insertResearchReport(entity)
        auditLogger.log("RESEARCH", "AFTER_HOURS_REPORT", mapOf("date" to today, "forecast_matches" to matched, "shadow_trades" to shadowTrades.size))
        return ResearchDto(today, entity.title, report)
    }

    suspend fun saveBrokerSettings(
        apiKeyOrToken: String,
        totpSecret: String,
        expectedStaticIp: String,
        packageFilter: String,
        dailyBudgetRupees: Long
    ) {
        require(dailyBudgetRupees in MIN_BUDGET..MAX_BUDGET) { "Intraday budget must be between ₹10,000 and ₹2,00,000" }
        require(expectedStaticIp.isBlank() || isValidIp(expectedStaticIp)) { "Enter a valid IPv4/IPv6 static IP" }
        preferences.updateBrokerSettings(expectedStaticIp, packageFilter, dailyBudgetRupees)
        if (apiKeyOrToken.isNotBlank()) secretStore.putApiKey(apiKeyOrToken)
        if (totpSecret.isNotBlank()) secretStore.putTotpSecret(totpSecret)
        if (apiKeyOrToken.isNotBlank() || totpSecret.isNotBlank()) {
            secretStore.clearAccessToken()
            preferences.updateAuthState(authenticated = false)
        }
        auditLogger.log("SYSTEM", "SETTINGS_SAVED", mapOf(
            "static_ip_configured" to expectedStaticIp.isNotBlank(),
            "package_filter" to packageFilter.trim(),
            "intraday_budget" to dailyBudgetRupees,
            "credentials_replaced" to (apiKeyOrToken.isNotBlank() || totpSecret.isNotBlank())
        ))
    }

    suspend fun setIntradayBudget(value: Long) {
        preferences.setIntradayBudget(value)
        auditLogger.log("RISK", "INTRADAY_BUDGET_CHANGED", mapOf("budget" to value.coerceIn(MIN_BUDGET, MAX_BUDGET)))
    }
    suspend fun setFastTrackBudget(value: Long) {
        preferences.setFastTrackBudget(value)
        auditLogger.log("FAST_TRACK", "BUDGET_CHANGED", mapOf("budget" to value.coerceIn(MIN_BUDGET, MAX_BUDGET)))
    }
    suspend fun setPostSellShortEnabled(value: Boolean) {
        preferences.setPostSellShortEnabled(value)
        auditLogger.log("FAST_TRACK", "POST_SELL_SHORT_CHANGED", mapOf("enabled" to value))
    }
    suspend fun setExecutionMode(value: String) {
        preferences.setExecutionMode(value)
        val normalized = preferences.settings.first().executionMode
        auditLogger.log("SETTINGS", "EXECUTION_MODE", mapOf("mode" to normalized))
    }
    suspend fun setWaveCount(value: Int) {
        preferences.setWaveCount(value)
        auditLogger.log("SETTINGS", "WAVE_COUNT", mapOf("waves" to value.coerceIn(1, 20)))
    }
    suspend fun setFirstWaveMode(value: String) = setExecutionMode(value)
    suspend fun setActiveWaveCount(value: Long) = setWaveCount(value.toInt())

    fun hasBrokerCredentials(): Boolean = secretStore.hasApiKey() && secretStore.hasTotpSecret()
    fun hasAccessToken(): Boolean = secretStore.hasAccessToken()

    suspend fun authenticate(): AuthenticationResult {
        auditLogger.log("AUTH", "AUTHENTICATION_ATTEMPT", mapOf("method" to "TOTP"))
        val settings = preferences.settings.first()
        val apiKey = secretStore.getApiKey() ?: error("Groww TOTP token is not configured")
        val totpSecret = secretStore.getTotpSecret() ?: error("Groww TOTP secret is not configured")
        val publicIp = runCatching { apiFactory.publicIp.currentIp().ip.trim() }.getOrDefault("")
        val staticMatched = settings.expectedStaticIp.isNotBlank() && publicIp.isNotBlank() && normalizeIp(publicIp) == normalizeIp(settings.expectedStaticIp)

        val totp = TotpGenerator.generate(totpSecret)
        val tokenResponse = try {
            apiFactory.groww.createAccessToken(
                authorization = "Bearer $apiKey",
                request = TokenRequest(keyType = "totp", totp = totp)
            )
        } catch (e: HttpException) {
            error(growwAuthError(e))
        }
        val token = tokenResponse.token?.takeIf { it.isNotBlank() }
            ?: error(buildString {
                append("Groww did not return an access token")
                tokenResponse.error?.code?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                tokenResponse.error?.message?.takeIf { it.isNotBlank() }?.let { append(": $it") }
                append(". Verify the TOTP token/secret and keep Automatic date & time enabled.")
            })
        secretStore.putAccessToken(token)

        val profile = apiFactory.groww.userProfile(bearer(token)).requirePayload("Groww profile")
        val cashEnabled = profile.nseEnabled && profile.activeSegments.any { it.equals("CASH", true) }
        require(cashEnabled) { "Groww profile is not enabled for NSE CASH trading" }
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val expiry = tokenResponse.expiry.orEmpty()
        preferences.updateAuthState(
            authenticated = true,
            authenticatedAtMs = System.currentTimeMillis(),
            accessTokenExpiry = expiry,
            brokerUcc = profile.ucc.orEmpty(),
            brokerDdpiEnabled = profile.ddpiEnabled,
            verifiedPublicIp = publicIp,
            staticIpMatched = staticMatched
        )
        auditLogger.log("AUTH", "AUTHENTICATION_SUCCESS", mapOf(
            "nse_cash_enabled" to cashEnabled,
            "ddpi_enabled" to profile.ddpiEnabled,
            "static_ip_matched" to staticMatched,
            "mis_balance_available" to (margin.equity?.misBalanceAvailable ?: 0.0)
        ))
        return AuthenticationResult(
            authenticated = true,
            ucc = profile.ucc.orEmpty(),
            nseCashEnabled = cashEnabled,
            publicIp = publicIp,
            staticIpMatched = staticMatched,
            accessTokenExpiry = expiry,
            misBalanceAvailable = margin.equity?.misBalanceAvailable ?: 0.0,
            ddpiEnabled = profile.ddpiEnabled,
            detail = buildString {
                append("Groww authenticated")
                if (profile.ucc?.isNotBlank() == true) append(" · UCC ${profile.ucc}")
                append(if (profile.ddpiEnabled) " · DDPI enabled" else " · DDPI not enabled")
                if (publicIp.isNotBlank()) append(" · egress $publicIp")
                if (settings.expectedStaticIp.isNotBlank()) append(if (staticMatched) " · static IP matched" else " · STATIC IP MISMATCH")
            }
        )
    }

    suspend fun refreshDashboard(): DashboardDto {
        var settings = preferences.settings.first()
        val token = secretStore.getAccessToken()
        if (!settings.brokerAuthenticated || token.isNullOrBlank()) return disconnectedDashboard(settings)
        return try {
            val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
            ensureHistoricalSeed()
            val shadow = paperSnapshot()
            val live = managedSnapshot(token, setOf(ENGINE_INTRADAY))
            val fast = managedSnapshot(token, setOf(ENGINE_FAST_TRACK, ENGINE_FAST_SHORT))
            val learning = learningStats()
            val forecasts = learningDao.forecastsForDate(LocalDate.now(INDIA).toString()).map { it.toDto() }
            val latestResearch = learningDao.latestResearchReport()?.let { ResearchDto(it.reportDate, it.title, it.summary) } ?: ResearchDto()
            val strategyInsights = learningDao.recentStrategySnapshots(12).map { x ->
                StrategyInsightDto(
                    atMs = x.atMs, symbol = x.symbol, side = x.side, strategy = x.strategy, regime = x.regime,
                    confidence = x.confidence, score = x.directionalScore, ltp = x.ltp, rsi = x.rsi14, rvol = x.rvol,
                    orderBookImbalance = x.orderBookImbalance, votes = x.votes
                )
            }
            val waveStats = waveStats()
            val waveSignals = buildLiveWaveSignals(token, settings, learning)
            val main = if (settings.liveExecutionEffective) live else shadow
            if (settings.liveExecutionEffective) {
                preferences.updateLivePeakPnl(main.totalPnl)
            } else {
                preferences.updateShadowPeakPnl(main.totalPnl)
            }
            settings = preferences.settings.first()
            val mainPeakPnl = if (settings.liveExecutionEffective) settings.livePeakPnl else settings.shadowPeakPnl

            val eventRecent = dao.recentNow(8).mapNotNull { e ->
                e.backendAction?.let {
                    RecentDecisionDto(
                        at = formatEventTime(e.receivedAtMs), symbol = e.symbol, action = it,
                        reason = e.backendReason.orEmpty(), strategy = null, quantity = 0
                    )
                }
            }
            val recent = (main.recentTrades + eventRecent).sortedByDescending { it.at }.take(10)
            val symbolStatus = nseSymbols.status()
            DashboardDto(
                serviceStatus = "device",
                mode = if (settings.liveExecutionEffective) "live" else "paper",
                armed = settings.liveExecutionEffective,
                halted = settings.safetyHalt,
                marketSession = marketSession(),
                asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                broker = BrokerStatusDto(
                    configured = hasBrokerCredentials(), connected = true, name = "Groww",
                    detail = "Direct API · app-owned P&L ledger · UCC ${settings.brokerUcc.ifBlank { "verified" }} · MIS available ₹${(margin.equity?.misBalanceAvailable ?: 0.0).toInt()} · NSE master ${symbolStatus.count} symbols"
                ),
                risk = riskStatus(settings, main.totalPnl, mainPeakPnl),
                summary = main.summary,
                fastTrackSummary = fast.summary,
                combinedAppPnl = live.totalPnl + fast.totalPnl,
                shadowQualificationDays = shadowQualificationDays(),
                learning = learning,
                forecasts = forecasts,
                research = latestResearch,
                strategyInsights = strategyInsights,
                waveStats = waveStats,
                waveSignals = waveSignals,
                positions = main.positions,
                recentDecisions = recent
            )
        } catch (t: Throwable) {
            if (t.message?.contains("401") == true || t.message?.contains("author", true) == true) {
                preferences.updateAuthState(authenticated = false)
            }
            disconnectedDashboard(preferences.settings.first()).copy(
                broker = BrokerStatusDto(configured = hasBrokerCredentials(), connected = false, name = "Groww", detail = t.message)
            )
        }
    }

    suspend fun setLiveExecution(enabled: Boolean) {
        if (!enabled) {
            preferences.setLiveExecution(false)
            auditLogger.log("LIVE", "LIVE_EXECUTION_DISABLED")
            return
        }
        val s = preferences.settings.first()
        require(s.brokerAuthenticated && secretStore.hasAccessToken()) { "Refresh & Authenticate before enabling live execution" }
        require(s.expectedStaticIp.isNotBlank()) { "Configure the Groww-whitelisted static IP first" }
        require(s.staticIpMatched) { "Current internet egress does not match the configured static IP" }
        require(s.dailyBudgetRupees in MIN_BUDGET..MAX_BUDGET) { "Choose an intraday budget from ₹10,000 to ₹2,00,000" }
        require(!s.safetyHalt) { "Reset the safety halt before enabling live execution" }
        preferences.setLiveExecution(true)
        auditLogger.log("LIVE", "LIVE_EXECUTION_ENABLED", mapOf("budget" to s.dailyBudgetRupees, "min_confidence" to s.minLiveConfidence))
    }

    suspend fun setFastTrackExecution(enabled: Boolean) {
        if (!enabled) {
            preferences.setFastTrack(false)
            auditLogger.log("FAST_TRACK", "FAST_TRACK_DISABLED")
            return
        }
        val s = preferences.settings.first()
        require(s.brokerAuthenticated && secretStore.hasAccessToken()) { "Refresh & Authenticate before enabling notification auto buy/sell" }
        require(s.staticIpMatched) { "Current internet egress does not match the configured static IP" }
        require(s.brokerDdpiEnabled) { "Groww DDPI is required for unattended CNC delivery sells" }
        require(s.fastTrackBudgetRupees in MIN_BUDGET..MAX_BUDGET) { "Choose a Manual budget from ₹10,000 to ₹2,00,000" }
        require(!s.safetyHalt) { "Reset the safety halt first" }
        preferences.setFastTrack(true)
        auditLogger.log("FAST_TRACK", "FAST_TRACK_ENABLED", mapOf("budget" to s.fastTrackBudgetRupees, "post_sell_short" to s.postSellShortEnabled))
    }

    suspend fun resetHalt() {
        preferences.setSafetyHalt(false)
        preferences.setLiveExecution(false)
        preferences.setFastTrack(false)
        auditLogger.log("SAFETY", "HALT_RESET", mapOf("live_remains_off" to true, "fast_track_remains_off" to true))
    }

    suspend fun forceLocalDisarm() {
        preferences.setSafetyHalt(true)
        preferences.setLiveExecution(false)
        preferences.setFastTrack(false)
        auditLogger.log("SAFETY", "LOCAL_DISARM", mapOf("reason" to "safety_event_or_protection_failure"))
    }

    suspend fun shouldCapturePackage(packageName: String): Boolean {
        val filter = preferences.settings.first().packageFilter.trim()
        return filter.isBlank() || packageName.contains(filter, ignoreCase = true)
    }

    suspend fun isLiveExecutionEnabled(): Boolean = preferences.settings.first().liveExecutionEffective

    suspend fun processEvent(eventId: Long) {
        ensureHistoricalSeed()
        val event = dao.byId(eventId) ?: return
        auditLogger.log("SIGNAL", "PROCESS_EVENT", mapOf("event_id" to eventId, "signal_type" to event.signalType, "symbol" to event.symbol))
        dao.updateForwarding(eventId, "ANALYZING", null, null, null)
        val parsed = parser.parse(event.title, event.text, event.bigText)
        when (parsed.type) {
            SignalType.AUTO_PAUSED -> {
                forceLocalDisarm()
                dao.updateForwarding(eventId, "HALTED", "SAFETY_HALT", "Multify reported paused/unprotected quantity. All app live engines disabled.", null)
                return
            }
            SignalType.PRE_ALERT -> {
                val hot = runCatching { prepareHotMode() }.getOrElse { "HOT preparation partial: ${it.message}" }
                dao.updateForwarding(eventId, "ANALYZED", "PRE_ALERT_HOT", "$hot · direct execution path armed for the next paid equity release.", null)
                return
            }
            SignalType.BUY_SUBMITTED -> {
                val dash = refreshDashboard()
                dao.updateForwarding(eventId, "ANALYZED", "RECONCILED", "App ledger reconciled · ${dash.positions.size} app-managed position(s).", null)
                return
            }
            SignalType.UNKNOWN -> {
                dao.updateForwarding(eventId, "IGNORED", "IGNORED", "Free/non-equity/unsupported notification ignored.", null)
                return
            }
            else -> Unit
        }

        val symbol = parsed.symbol
        if (!symbol.isNullOrBlank() && !nseSymbols.isKnown(symbol) && !nseSymbols.isStale()) {
            dao.updateForwarding(eventId, "IGNORED", "SYMBOL_NOT_IN_NSE_MASTER", "$symbol is not in the current NSE equity master; no order was sent.", null)
            return
        }

        var settings = preferences.settings.first()
        var token = secretStore.getAccessToken()
        if (!settings.brokerAuthenticated || token.isNullOrBlank()) {
            if (hasBrokerCredentials()) {
                val autoAuth = runCatching { authenticate() }
                if (autoAuth.isSuccess) {
                    settings = preferences.settings.first()
                    token = secretStore.getAccessToken()
                } else {
                    dao.updateForwarding(eventId, "CAPTURED", "AUTO_AUTH_FAILED", "Signal stored. Automatic Groww TOTP refresh failed: ${autoAuth.exceptionOrNull()?.message.orEmpty()}", null)
                    return
                }
            } else {
                dao.updateForwarding(eventId, "CAPTURED", "AUTH_REQUIRED", "Signal stored. Save Groww TOTP credentials to enable market analysis.", null)
                return
            }
        }
        val accessToken = token ?: return

        try {
            when (parsed.type) {
                SignalType.TRADE_RELEASE -> {
                    parsed.symbol?.let { prioritizeNewRecommendation(it, accessToken, settings) }
                    // New Multify stock gets the fastest path: Fast Track entry is attempted before
                    // any slower strategy qualification. The intelligent intraday engine then evaluates
                    // the same signal, but never blocks notification-follow execution.
                    if (settings.fastTrackEffective) handleFastTrackBuy(eventId, parsed, accessToken, settings)
                    handleBuyRelease(eventId, parsed, accessToken, settings)
                }
                SignalType.BOOK_PROFIT -> {
                    if (settings.fastTrackEffective) handleFastTrackSell(eventId, parsed, accessToken, settings)
                    handleBookProfit(eventId, parsed, accessToken, settings)
                }
                else -> Unit
            }
        } catch (t: Throwable) {
            auditLogger.log("ENGINE", "PROCESS_EVENT_ERROR", mapOf("event_id" to eventId, "type" to t.javaClass.simpleName, "message" to (t.message ?: "")))
            dao.updateForwarding(eventId, "ERROR", "ENGINE_ERROR", t.message ?: t.javaClass.simpleName, t.javaClass.simpleName)
            if (t.message?.contains("401") == true || t.message?.contains("author", true) == true) preferences.updateAuthState(authenticated = false)
            throw t
        }
    }

    data class LogExportResult(val entries: Int, val signalCount: Int, val tradeCount: Int)

    suspend fun exportCompleteLogs(uri: Uri): LogExportResult {
        val settings = preferences.settings.first()
        val signals = dao.allNow()
        val shadowPositions = shadowDao.allPositions()
        val shadowTrades = shadowDao.allTrades()
        val managedPositions = managedDao.allPositions()
        val managedTrades = managedDao.allTrades()
        val learningCalls = learningDao.allCalls()
        val priceObservations = learningDao.allObservations()
        val strategySnapshots = learningDao.allStrategySnapshots()
        val forecasts = learningDao.allForecasts()
        val researchReports = learningDao.allResearchReports()
        val waveCampaigns = learningDao.allWaveCampaigns()
        val waveObservations = learningDao.allWaveObservations()
        val currentWaveStats = waveStats()
        val nseStatus = nseSymbols.status()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val now = System.currentTimeMillis()
        val zone = ZoneId.of("Asia/Kolkata")

        val safeSettings = linkedMapOf<String, Any?>(
            "intraday_budget_rupees" to settings.dailyBudgetRupees,
            "fast_track_budget_rupees" to settings.fastTrackBudgetRupees,
            "shadow_budget_rupees" to SHADOW_BUDGET,
            "live_execution_effective" to settings.liveExecutionEffective,
            "fast_track_effective" to settings.fastTrackEffective,
            "post_sell_short_enabled" to settings.postSellShortEnabled,
            "execution_mode" to settings.executionMode,
            "wave_count" to settings.waveCount,
            "package_filter" to settings.packageFilter,
            "credentials_configured" to hasBrokerCredentials(),
            "broker_authenticated" to settings.brokerAuthenticated,
            "authenticated_at_ms" to settings.authenticatedAtMs,
            "access_token_expiry" to settings.accessTokenExpiry,
            "ddpi_enabled" to settings.brokerDdpiEnabled,
            "static_ip_configured" to settings.expectedStaticIp.isNotBlank(),
            "static_ip_matched" to settings.staticIpMatched,
            "safety_halt" to settings.safetyHalt,
            "min_live_confidence" to settings.minLiveConfidence,
            "shadow_peak_pnl" to settings.shadowPeakPnl,
            "live_peak_pnl" to settings.livePeakPnl
        )
        val meta = linkedMapOf<String, Any?>(
            "app" to "Multify Trader Pro",
            "version_name" to BuildConfig.VERSION_NAME,
            "version_code" to BuildConfig.VERSION_CODE,
            "application_id" to BuildConfig.APPLICATION_ID,
            "debug" to BuildConfig.DEBUG,
            "exported_at_ms" to now,
            "exported_at_utc" to Instant.ofEpochMilli(now).toString(),
            "android_sdk" to Build.VERSION.SDK_INT,
            "device_manufacturer" to Build.MANUFACTURER,
            "device_model" to Build.MODEL,
            "security_note" to "TOTP token, TOTP secret, access token, authorization headers, UCC and exact IP addresses are intentionally excluded."
        )
        val state = linkedMapOf<String, Any?>(
            "meta" to meta,
            "settings" to safeSettings,
            "nse_symbol_master" to linkedMapOf("count" to nseStatus.count, "updated_at_ms" to nseStatus.updatedAtMs, "stale" to nseSymbols.isStale()),
            "counts" to linkedMapOf(
                "signals" to signals.size,
                "shadow_positions" to shadowPositions.size,
                "shadow_trades" to shadowTrades.size,
                "managed_positions" to managedPositions.size,
                "managed_trades" to managedTrades.size,
                "learning_calls" to learningCalls.size,
                "price_observations" to priceObservations.size,
                "strategy_snapshots" to strategySnapshots.size,
                "forecasts" to forecasts.size,
                "research_reports" to researchReports.size,
                "wave_campaigns" to waveCampaigns.size,
                "wave_observations" to waveObservations.size
            )
        )

        val output = appContext.contentResolver.openOutputStream(uri, "w") ?: error("Could not open the selected export file")
        var entries = 0
        ZipOutputStream(output.buffered()).use { zip ->
            fun add(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                entries++
            }
            add("README.txt", exportReadme())
            add("app/current_state.json", gson.toJson(state))
            add("app/audit_timeline.jsonl", auditLogger.readAll())
            add("signals/signal_events.csv", signalCsv(signals))
            add("shadow/open_and_historical_positions.csv", shadowPositionCsv(shadowPositions))
            add("shadow/closed_trades.csv", shadowTradeCsv(shadowTrades))
            add("managed/open_and_historical_positions.csv", managedPositionCsv(managedPositions))
            add("managed/closed_trades.csv", managedTradeCsv(managedTrades))
            add("learning/rolling_calls.json", gson.toJson(learningCalls))
            add("learning/high_resolution_price_observations.json", gson.toJson(priceObservations))
            add("learning/strategy_snapshots.json", gson.toJson(strategySnapshots))
            add("forecast/all_forecasts.json", gson.toJson(forecasts))
            add("research/after_market_reports.json", gson.toJson(researchReports))
            add("waves/campaigns.json", gson.toJson(waveCampaigns))
            add("waves/confirmed_pivots.json", gson.toJson(waveObservations))
            add("waves/current_20_wave_averages.json", gson.toJson(currentWaveStats))
            add("learning/current_30day_stats.json", gson.toJson(learningStats()))
            add("analysis/strategy_catalog.txt", strategyCatalogText())
            add("analysis/strategy_performance.csv", strategyPerformanceCsv(shadowTrades, managedTrades))
            add("analysis/daily_pnl.csv", dailyPnlCsv(shadowTrades, managedTrades, zone))
            add("analysis/decision_timeline.csv", decisionTimelineCsv(signals, shadowTrades, managedTrades))
        }
        auditLogger.log("DIAGNOSTICS", "LOG_EXPORT_COMPLETED", mapOf("entries" to entries, "signals" to signals.size, "trades" to (shadowTrades.size + managedTrades.size)))
        return LogExportResult(entries, signals.size, shadowTrades.size + managedTrades.size)
    }

    private fun strategyCatalogText(): String = """
        Active dynamic strategy families
        ================================
        - Multify event prior + anchored VWAP
        - Rolling 30-trading-day learned long target (seeded from uploaded history)
        - Post-sell retracement learner: starts at 100%, capped at 100%, adapts downward after observations
        - High-resolution +1s/+2s/+5s/+10s/+15s/+30s/+1m/+2m/+5m event response sampling
        - Order-book imbalance / total buy-vs-sell pressure when Groww supplies it
        - VWAP continuation, reclaim and mean reversion
        - EMA 9/20 trend separation + price-structure slope
        - MACD momentum confirmation
        - 5-minute and 15-minute opening-range breakout/breakdown
        - Donchian breakout/breakdown
        - Keltner-style ATR expansion
        - Bollinger compression to volume breakout/breakdown
        - Relative-volume price impulse
        - Failed breakout / failed breakdown liquidity sweeps
        - RSI trend, exhaustion and chase filters
        - Candlestick confirmation: engulfing, pin bars, marubozu
        - Dynamic regime switch: TREND / COMPRESSION / MEAN_REVERSION / MIXED

        Risk/execution policy
        =====================
        - Multify-only fill ledger; unrelated Groww trades excluded
        - Shadow budget fixed at ₹2,00,000
        - 20-wave pivot memory: Wave 1 Up from rolling Multify history/live outcomes; all Down legs and Waves 2–20 from confirmed live pivots
        - No averaging-down, tranche adds, capital top-ups or automatic budget increases after entry
        - Later wave actions may only reuse or reduce the existing fixed campaign notional
        - ₹5,000 is a milestone, not a profit ceiling
        - -₹1,500 soft defensive band; no mechanical panic close
        - -₹2,500 hard daily cap with earlier live risk reduction for slippage
        - Targets are checkpoints; strong trends may continue with trailing protection
        - Same-symbol external MIS conflict detection
        - Broker-side OCO protection on app MIS fills
        - Hard intraday force-flat for app MIS exposure
    """.trimIndent() + "\n"

    private fun exportReadme(): String = """
        Multify Trader Pro complete diagnostic export
        =============================================

        Purpose
        -------
        This archive is designed for strategy review, shadow/live comparison, notification parsing review,
        risk-control review, and debugging across Dashboard, Signals, Strategies, Manual/Fast Track and System.

        Included
        --------
        app/current_state.json                 Safe application/runtime/settings snapshot and NSE-master status
        app/audit_timeline.jsonl               System/auth/toggle/safety/engine audit events recorded by v3.1+
        signals/signal_events.csv              Captured recognized Multify notifications and final engine decisions
        shadow/open_and_historical_positions.csv  Shadow position records
        shadow/closed_trades.csv               Shadow fills, costs, P&L, MFE/MAE, strategy/regime/confidence
        managed/open_and_historical_positions.csv App-owned LIVE / FAST_TRACK / FAST_SHORT position records
        managed/closed_trades.csv              App-owned closed real-order ledger only; unrelated Groww trades excluded
        analysis/strategy_performance.csv      Aggregated strategy/regime/symbol performance
        analysis/daily_pnl.csv                 Per-day app-only P&L by engine
        analysis/decision_timeline.csv         Time-ordered signals and closed trade outcomes

        Security
        --------
        This export NEVER includes the Groww TOTP token, TOTP secret, access token, authorization headers,
        UCC, or exact configured/current public IP addresses. Do not share screenshots that reveal credentials.

        Note
        ----
        Audit timeline recording begins with v3.1. Historical signal/trade tables retained from earlier versions
        are still exported in full, subject to the app's local retention policy.
    """.trimIndent() + "\n"

    private fun csvCell(v: Any?): String {
        val raw = when (v) { null -> ""; else -> v.toString() }
        return if (raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }

    private fun row(vararg values: Any?): String = values.joinToString(",") { csvCell(it) } + "\n"

    private fun signalCsv(items: List<SignalEventEntity>): String = buildString {
        append(row("id","received_at_ms","posted_at_ms","source_package","app_label","title","text","big_text","signal_type","symbol","summary","parser_confidence","state","engine_action","engine_reason","last_error"))
        items.forEach { e -> append(row(e.id,e.receivedAtMs,e.postedAtMs,e.sourcePackage,e.appLabel,e.title,e.text,e.bigText,e.signalType,e.symbol,e.summary,e.confidence,e.forwardingState,e.backendAction,e.backendReason,e.lastError)) }
    }

    private fun shadowPositionCsv(items: List<ShadowPositionEntity>): String = buildString {
        append(row("id","symbol","side","quantity","entry","stop","target","strategy","regime","confidence","source_event_id","opened_at_ms","last_price","max_favourable_price","max_adverse_price","last_evaluated_at_ms","status"))
        items.forEach { p -> append(row(p.id,p.symbol,p.side,p.quantity,p.entryPrice,p.stopPrice,p.targetPrice,p.strategy,p.regime,p.confidence,p.sourceEventId,p.openedAtMs,p.lastPrice,p.maxFavourablePrice,p.maxAdversePrice,p.lastEvaluatedAtMs,p.status)) }
    }

    private fun shadowTradeCsv(items: List<ShadowTradeEntity>): String = buildString {
        append(row("id","engine","symbol","side","quantity","entry","exit","gross_pnl","estimated_costs","net_pnl","exit_reason","strategy","regime","confidence","mfe_rupees","mae_rupees","source_event_id","opened_at_ms","closed_at_ms"))
        items.forEach { t -> append(row(t.id,"SHADOW",t.symbol,t.side,t.quantity,t.entryPrice,t.exitPrice,t.grossPnl,t.estimatedCosts,t.netPnl,t.exitReason,t.strategy,t.regime,t.confidence,t.mfeRupees,t.maeRupees,t.sourceEventId,t.openedAtMs,t.closedAtMs)) }
    }

    private fun managedPositionCsv(items: List<ManagedPositionEntity>): String = buildString {
        append(row("id","engine","symbol","product","side","quantity","entry","stop","target","strategy","regime","confidence","source_event_id","open_order_id","open_reference_id","smart_order_id","opened_at_ms","last_price","max_favourable_price","max_adverse_price","last_evaluated_at_ms","status"))
        items.forEach { p -> append(row(p.id,p.engine,p.symbol,p.product,p.side,p.quantity,p.entryPrice,p.stopPrice,p.targetPrice,p.strategy,p.regime,p.confidence,p.sourceEventId,p.openOrderId,p.openReferenceId,p.smartOrderId,p.openedAtMs,p.lastPrice,p.maxFavourablePrice,p.maxAdversePrice,p.lastEvaluatedAtMs,p.status)) }
    }

    private fun managedTradeCsv(items: List<ManagedTradeEntity>): String = buildString {
        append(row("id","engine","symbol","product","side","quantity","entry","exit","gross_pnl","estimated_costs","net_pnl","exit_reason","strategy","regime","confidence","mfe_rupees","mae_rupees","source_event_id","open_order_id","close_order_id","opened_at_ms","closed_at_ms"))
        items.forEach { t -> append(row(t.id,t.engine,t.symbol,t.product,t.side,t.quantity,t.entryPrice,t.exitPrice,t.grossPnl,t.estimatedCosts,t.netPnl,t.exitReason,t.strategy,t.regime,t.confidence,t.mfeRupees,t.maeRupees,t.sourceEventId,t.openOrderId,t.closeOrderId,t.openedAtMs,t.closedAtMs)) }
    }

    private data class PerfTrade(val engine:String,val symbol:String,val strategy:String,val regime:String,val net:Double,val gross:Double,val costs:Double,val mfe:Double,val mae:Double)

    private fun strategyPerformanceCsv(shadow: List<ShadowTradeEntity>, managed: List<ManagedTradeEntity>): String {
        val trades = shadow.map { PerfTrade("SHADOW",it.symbol,it.strategy,it.regime,it.netPnl,it.grossPnl,it.estimatedCosts,it.mfeRupees,it.maeRupees) } +
            managed.map { PerfTrade(it.engine,it.symbol,it.strategy,it.regime,it.netPnl,it.grossPnl,it.estimatedCosts,it.mfeRupees,it.maeRupees) }
        return buildString {
            append(row("engine","symbol","strategy","regime","trades","wins","losses","win_rate","gross_pnl","costs","net_pnl","avg_net","avg_mfe","avg_mae","profit_factor"))
            trades.groupBy { listOf(it.engine,it.symbol,it.strategy,it.regime) }.toSortedMap(compareBy { it.joinToString("|") }).forEach { (k,v) ->
                val wins=v.count { it.net>0 }; val losses=v.count { it.net<0 }; val gross=v.sumOf{it.gross}; val costs=v.sumOf{it.costs}; val net=v.sumOf{it.net}
                val winGross=v.filter{it.net>0}.sumOf{it.net}; val lossGross=-v.filter{it.net<0}.sumOf{it.net}; val pf=if(lossGross>0) winGross/lossGross else if(winGross>0) 999.0 else 0.0
                append(row(k[0],k[1],k[2],k[3],v.size,wins,losses,if(v.isNotEmpty()) wins.toDouble()/v.size else 0.0,gross,costs,net,if(v.isNotEmpty()) net/v.size else 0.0,v.map{it.mfe}.averageOrZero(),v.map{it.mae}.averageOrZero(),pf))
            }
        }
    }

    private data class DayTrade(val engine:String,val closedAtMs:Long,val net:Double,val gross:Double,val costs:Double)
    private fun dailyPnlCsv(shadow: List<ShadowTradeEntity>, managed: List<ManagedTradeEntity>, zone: ZoneId): String {
        val trades=shadow.map{DayTrade("SHADOW",it.closedAtMs,it.netPnl,it.grossPnl,it.estimatedCosts)}+managed.map{DayTrade(it.engine,it.closedAtMs,it.netPnl,it.grossPnl,it.estimatedCosts)}
        return buildString {
            append(row("date_ist","engine","trades","wins","gross_pnl","costs","net_pnl"))
            trades.groupBy { Pair(Instant.ofEpochMilli(it.closedAtMs).atZone(zone).toLocalDate().toString(),it.engine) }.toSortedMap(compareBy<Pair<String,String>>{it.first}.thenBy{it.second}).forEach { (k,v) ->
                append(row(k.first,k.second,v.size,v.count{it.net>0},v.sumOf{it.gross},v.sumOf{it.costs},v.sumOf{it.net}))
            }
        }
    }

    private fun decisionTimelineCsv(signals: List<SignalEventEntity>, shadow: List<ShadowTradeEntity>, managed: List<ManagedTradeEntity>): String {
        data class T(val at:Long,val section:String,val symbol:String?,val action:String,val detail:String,val strategy:String?,val net:Double?)
        val all = signals.map { T(it.receivedAtMs,"SIGNAL",it.symbol,it.backendAction ?: it.signalType,it.backendReason ?: it.summary,null,null) } +
            shadow.map { T(it.closedAtMs,"SHADOW",it.symbol,"CLOSE_${it.side}",it.exitReason,it.strategy,it.netPnl) } +
            managed.map { T(it.closedAtMs,it.engine,it.symbol,"CLOSE_${it.side}",it.exitReason,it.strategy,it.netPnl) }
        return buildString {
            append(row("at_ms","section","symbol","action","detail","strategy","net_pnl"))
            all.sortedBy { it.at }.forEach { t -> append(row(t.at,t.section,t.symbol,t.action,t.detail,t.strategy,t.net)) }
        }
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    suspend fun pruneLocalHistory(retainDays: Int = 60) {
        dao.deleteOlderThan(System.currentTimeMillis() - retainDays * 86_400_000L)
    }

    /**
     * A new Multify recommendation is the highest-priority first-wave event.
     *
     * Existing first-wave positions in another symbol are handled deterministically:
     * - net green after estimated costs -> exit and release focus/capital for the new call;
     * - net red -> keep the broker protection/monitoring alive, but stop adding exposure, flipping and
     *   strategy churn for that symbol for the rest of the call cycle;
     * - wave 2+ positions may continue as one secondary symbol. Any additional wave 2+ symbol is
     *   converted to prediction-only so the engine concentrates on at most two actively-managed names.
     *
     * "Do not monitor" therefore means no new trading decisions. Safety/OCO/trailing reconciliation
     * remains active so a losing position is never left unmanaged.
     */
    private suspend fun prioritizeNewRecommendation(newSymbol: String, token: String, settings: AppSettings) {
        if (!settings.liveExecutionEffective && !settings.fastTrackEffective) return
        val existing = managedDao.openPositions().filter { !it.symbol.equals(newSymbol, true) }
        if (existing.isEmpty()) return

        var keptSecondary = false
        existing.sortedByDescending { it.openedAtMs }.forEach { p ->
            val mark = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = p.symbol)
                    .requirePayload("Priority quote ${p.symbol}").lastPrice
            }.getOrNull() ?: p.lastPrice
            val net = calculatePnl(p.side, p.quantity, p.entryPrice, mark).net
            val isFirstWave = p.addCount <= 0 || p.engine == ENGINE_FAST_TRACK || p.engine == ENGINE_FAST_SHORT

            if (isFirstWave) {
                if (net > 0.0) {
                    if (p.product == "MIS") assertManagedMisStillOwned(token, p)
                    closeManaged(token, p, mark, "NEW_MULTIFY_PRIORITY_GREEN_EXIT")
                    auditLogger.log("PRIORITY", "FIRST_WAVE_GREEN_EXIT", mapOf(
                        "old_symbol" to p.symbol, "new_symbol" to newSymbol, "side" to p.side, "net_before_exit" to net
                    ))
                } else {
                    val held = p.copy(
                        regime = REGIME_RECOVERY_HOLD,
                        strategy = "RECOVERY HOLD after new Multify call · ${p.strategy}",
                        lastEvaluatedAtMs = System.currentTimeMillis()
                    )
                    managedDao.updatePosition(held)
                    auditLogger.log("PRIORITY", "FIRST_WAVE_RED_RECOVERY_HOLD", mapOf(
                        "old_symbol" to p.symbol, "new_symbol" to newSymbol, "side" to p.side, "net_mark" to net,
                        "safety_monitoring" to true, "new_trading_decisions" to false
                    ))
                }
                return@forEach
            }

            // After wave 1, exactly one older symbol may keep executing alongside the new priority stock.
            if (!keptSecondary) {
                keptSecondary = true
                if (p.regime == REGIME_SECONDARY_PREVIEW) {
                    managedDao.updatePosition(p.copy(regime = "WAVE_ACTIVE", lastEvaluatedAtMs = System.currentTimeMillis()))
                }
                auditLogger.log("PRIORITY", "SECONDARY_WAVE_CONTINUES", mapOf(
                    "symbol" to p.symbol, "new_symbol" to newSymbol, "wave" to (p.addCount + 1)
                ))
            } else {
                managedDao.updatePosition(p.copy(
                    regime = REGIME_SECONDARY_PREVIEW,
                    strategy = "PREDICTION ONLY secondary limit · ${p.strategy}",
                    lastEvaluatedAtMs = System.currentTimeMillis()
                ))
                auditLogger.log("PRIORITY", "SECONDARY_LIMIT_PREVIEW_ONLY", mapOf(
                    "symbol" to p.symbol, "new_symbol" to newSymbol, "wave" to (p.addCount + 1)
                ))
            }
        }
    }

    suspend fun waveStats(): List<WaveStatDto> {
        ensureHistoricalSeed()
        val cutoff = LocalDate.now(INDIA).minusDays(29)
        val pivots = learningDao.allWaveObservations().filter { row ->
            runCatching { !LocalDate.parse(row.callDate).isBefore(cutoff) }.getOrDefault(false)
        }
        val learned = learningStats()
        return (1..20).map { wave ->
            val upRows = pivots.filter { it.wave == wave && it.direction.equals("UP", true) }
            val downRows = pivots.filter { it.wave == wave && it.direction.equals("DOWN", true) }
            val averageUp = if (wave == 1) learned.longAveragePct else upRows.map { it.movePct }.takeIf { it.isNotEmpty() }?.average()
            WaveStatDto(
                wave = wave,
                averageUpPct = averageUp,
                averageDownPct = downRows.map { it.movePct }.takeIf { it.isNotEmpty() }?.average(),
                upSamples = if (wave == 1) learned.rollingCalls else upRows.size,
                downSamples = downRows.size,
                upSource = if (wave == 1) "30D_EXCEL_LIVE" else "LIVE_PIVOT",
                downSource = "LIVE_PIVOT"
            )
        }
    }

    private suspend fun waveLongArmPct(wave: Int): Double =
        waveStats().firstOrNull { it.wave == wave }?.averageUpPct?.takeIf { it > 0.0 }
            ?: DEFAULT_UNTRAINED_WAVE_ARM_PCT

    private suspend fun waveShortArmPct(wave: Int): Double =
        waveStats().firstOrNull { it.wave == wave }?.averageDownPct?.takeIf { it > 0.0 }
            ?: DEFAULT_UNTRAINED_WAVE_ARM_PCT

    private suspend fun startOrReanchorWaveCampaign(eventId: Long, symbol: String, price: Double, source: String) {
        if (price <= 0.0) return
        val now = System.currentTimeMillis()
        val existing = learningDao.waveCampaignForEvent(eventId)
        if (existing == null) {
            learningDao.activeWaveCampaigns().filter { it.symbol.equals(symbol, true) }.forEach { old ->
                learningDao.updateWaveCampaign(old.copy(active = false, completedAtMs = now, updatedAtMs = now))
            }
            learningDao.insertWaveCampaign(
                WaveCampaignEntity(
                    eventId = eventId,
                    symbol = symbol.uppercase(Locale.US),
                    callDate = LocalDate.now(INDIA).toString(),
                    startPrice = price,
                    startAtMs = now,
                    startSource = source,
                    wave = 1,
                    leg = "UP",
                    legStartPrice = price,
                    legStartAtMs = now,
                    extremePrice = price,
                    extremeAtMs = now,
                    legArmed = false,
                    initialAdversePrice = price,
                    lastPrice = price,
                    active = true,
                    updatedAtMs = now
                )
            )
            auditLogger.log("WAVES", "CAMPAIGN_STARTED", mapOf(
                "event_id" to eventId, "symbol" to symbol, "price" to price,
                "source" to source, "pivot_pct" to (WAVE_PIVOT_FRACTION * 100.0)
            ))
            return
        }
        if (source == "LIVE_FILL" && existing.startSource != "LIVE_FILL") {
            learningDao.updateWaveCampaign(existing.copy(
                startPrice = price, startAtMs = now, startSource = source,
                wave = 1, leg = "UP", legStartPrice = price, legStartAtMs = now,
                extremePrice = price, extremeAtMs = now, legArmed = false,
                initialAdversePrice = price, lastPrice = price, active = true,
                completedAtMs = null, updatedAtMs = now
            ))
            auditLogger.log("WAVES", "CAMPAIGN_REANCHORED_TO_LIVE_FILL", mapOf(
                "event_id" to eventId, "symbol" to symbol, "price" to price
            ))
        }
    }

    private suspend fun updateWaveCampaignTick(campaign: WaveCampaignEntity, price: Double, maxWaves: Int): WaveCampaignEntity {
        if (!campaign.active || price <= 0.0) return campaign
        val now = System.currentTimeMillis()
        val direction = if (campaign.leg.equals("DOWN", true)) "DOWN" else "UP"
        val nextExtreme = if (direction == "UP") max(campaign.extremePrice, price) else min(campaign.extremePrice, price)
        val extremeAt = if (nextExtreme != campaign.extremePrice) now else campaign.extremeAtMs
        val armPct = if (direction == "UP") waveLongArmPct(campaign.wave) else waveShortArmPct(campaign.wave)
        val armFraction = (armPct / 100.0).coerceAtLeast(WAVE_PIVOT_FRACTION)
        val armed = campaign.legArmed || WavePivotMath.shouldArm(direction, campaign.legStartPrice, nextExtreme, armFraction)
        val initialAdverse = if (direction == "UP") min(campaign.initialAdversePrice, price) else max(campaign.initialAdversePrice, price)

        if (armed && WavePivotMath.shouldConfirm(direction, nextExtreme, price, WAVE_PIVOT_FRACTION)) {
            val movePct = WavePivotMath.movePct(campaign.legStartPrice, nextExtreme)
            val reversalPct = WavePivotMath.reversalFraction(direction, nextExtreme, price) * 100.0
            learningDao.insertWaveObservation(
                WaveObservationEntity(
                    campaignId = campaign.id, eventId = campaign.eventId, symbol = campaign.symbol,
                    callDate = campaign.callDate, wave = campaign.wave, direction = direction,
                    startPrice = campaign.legStartPrice, extremePrice = nextExtreme, confirmationPrice = price,
                    startAtMs = campaign.legStartAtMs, extremeAtMs = extremeAt, confirmedAtMs = now,
                    movePct = movePct, reversalPct = reversalPct, source = "LIVE_PIVOT"
                )
            )
            val completed = direction == "DOWN" && campaign.wave >= maxWaves.coerceIn(1, 20)
            val nextWave = if (direction == "DOWN") (campaign.wave + 1).coerceAtMost(20) else campaign.wave
            val nextLeg = if (direction == "UP") "DOWN" else "UP"
            val updated = campaign.copy(
                wave = nextWave, leg = nextLeg, legStartPrice = price, legStartAtMs = now,
                extremePrice = price, extremeAtMs = now, legArmed = false,
                initialAdversePrice = price, lastPrice = price, active = !completed,
                completedAtMs = if (completed) now else null, updatedAtMs = now
            )
            learningDao.updateWaveCampaign(updated)
            auditLogger.log("WAVES", if (direction == "UP") "UP_CONFIRMED" else "DOWN_CONFIRMED", mapOf(
                "symbol" to campaign.symbol, "wave" to campaign.wave, "move_pct" to movePct,
                "confirm" to price, "next_leg" to nextLeg, "active" to !completed
            ))
            return updated
        }

        val updated = campaign.copy(
            extremePrice = nextExtreme, extremeAtMs = extremeAt, legArmed = armed,
            initialAdversePrice = initialAdverse, lastPrice = price, updatedAtMs = now
        )
        learningDao.updateWaveCampaign(updated)
        return updated
    }

    suspend fun monitorWaveCampaigns(): Int {
        val active = learningDao.activeWaveCampaigns()
        if (active.isEmpty()) return 0
        val token = ensureToken() ?: return active.size
        val settings = preferences.settings.first()
        active.forEach { campaign ->
            runCatching {
                val price = apiFactory.groww.quote(bearer(token), tradingSymbol = campaign.symbol)
                    .requirePayload("Wave pivot quote " + campaign.symbol).lastPrice ?: campaign.lastPrice
                updateWaveCampaignTick(campaign, price, settings.waveCount)
            }.onFailure {
                auditLogger.log("WAVES", "TICK_ERROR", mapOf("symbol" to campaign.symbol, "message" to (it.message ?: "")))
            }
        }
        return learningDao.activeWaveCampaigns().size
    }

    suspend fun submitManualSignal(symbol: String, action: String, observedPrice: Double? = null): Long {
        val normalizedSymbol = symbol.trim().uppercase(Locale.US)
        require(normalizedSymbol.isNotBlank()) { "Enter an NSE symbol" }
        val normalizedAction = action.trim().uppercase(Locale.US).replace(' ', '_')
        require(normalizedAction in setOf("BUY", "BOOK_PROFIT")) { "Choose BUY or BOOK PROFIT" }
        if (!nseSymbols.isKnown(normalizedSymbol) && !nseSymbols.isStale()) error("$normalizedSymbol is not in the current NSE equity master")
        val now = System.currentTimeMillis()
        val signalType = if (normalizedAction == "BUY") SignalType.TRADE_RELEASE else SignalType.BOOK_PROFIT
        val id = dao.insert(
            SignalEventEntity(
                fingerprint = sha256("$MANUAL_SOURCE|$normalizedSymbol|$normalizedAction|$now"),
                receivedAtMs = now, postedAtMs = now, sourcePackage = MANUAL_SOURCE, appLabel = "Manual signal",
                title = normalizedAction.replace('_', ' '), text = normalizedSymbol,
                bigText = observedPrice?.let { "$normalizedSymbol @ ₹${fmt(it)}" } ?: normalizedSymbol,
                signalType = signalType.name, symbol = normalizedSymbol,
                summary = "Manual $normalizedAction · $normalizedSymbol",
                confidence = 1.0
            )
        )
        require(id > 0L) { "Manual signal was not stored" }
        val token = ensureToken()
        if (token == null) {
            dao.updateForwarding(id, "CAPTURED", "AUTH_REQUIRED", "Manual signal stored. Authenticate Groww to process market data.", null)
            return id
        }
        val settings = preferences.settings.first()
        val parsed = ParsedSignal(
            type = signalType, symbol = normalizedSymbol,
            exitPrice = if (signalType == SignalType.BOOK_PROFIT) observedPrice else null,
            rawText = "manual:$normalizedAction:$normalizedSymbol", confidence = 1.0
        )
        if (signalType == SignalType.TRADE_RELEASE) {
            prioritizeNewRecommendation(normalizedSymbol, token, settings)
            handleBuyRelease(id, parsed, token, settings)
        } else {
            handleBookProfit(id, parsed, token, settings)
        }
        auditLogger.log("SIGNAL", "MANUAL_SIGNAL", mapOf("event_id" to id, "symbol" to normalizedSymbol, "action" to normalizedAction, "observed_price" to observedPrice))
        return id
    }

    private suspend fun buildLiveWaveSignals(token: String, settings: AppSettings, learning: LearningStatsDto): List<WaveSignalDto> {
        val candidates = managedDao.openPositions()
            .filter { it.engine == ENGINE_INTRADAY }
            .sortedByDescending { it.openedAtMs }
            .take(2)
        return candidates.mapNotNull { p ->
            runCatching {
                val (decision, longEval, _) = assessAutoWave(p.symbol, token, p.side, FixedCapitalPolicy.cap(p.campaignBudget, p.capitalDeployed, p.entryPrice * p.quantity))
                val nextWave = (p.addCount + 2).coerceIn(2, WaveDirectionMath.MAX_WAVES)
                val executeEligible = nextWave <= settings.activeWaveCount.toInt() &&
                    p.regime != REGIME_RECOVERY_HOLD && p.regime != REGIME_SECONDARY_PREVIEW
                WaveSignalDto(
                    symbol = p.symbol,
                    currentSide = p.side,
                    nextWave = nextWave,
                    triggerDistancePct = (nextWave - 1) * WaveDirectionMath.WAVE_FRACTION * 100.0,
                    greenSide = decision.action,
                    longProbability = decision.longProbability,
                    shortProbability = decision.shortProbability,
                    executionMode = if (executeEligible) "AUTO_ELIGIBLE" else "PREVIEW_ONLY",
                    focusState = when (p.regime) {
                        REGIME_RECOVERY_HOLD -> "RECOVERY_HOLD"
                        REGIME_SECONDARY_PREVIEW -> "SECONDARY_PREVIEW"
                        else -> if (p.addCount == 0) "FIRST_WAVE_PRIORITY" else "SECONDARY_ACTIVE"
                    },
                    firstWaveMode = settings.firstWaveMode,
                    learnedLongAveragePct = learning.longAveragePct,
                    learnedShortRetracementPct = learning.shortRetracementPct,
                    reason = decision.reason + " · LTP ₹${fmt(longEval.features.ltp)}"
                )
            }.onFailure {
                auditLogger.log("AUTO_WAVE", "LIVE_TABLE_PREVIEW_ERROR", mapOf("symbol" to p.symbol, "message" to (it.message ?: "")))
            }.getOrNull()
        }
    }

    private suspend fun handleBuyRelease(eventId: Long, signal: ParsedSignal, token: String, settings: AppSettings) {
        val symbol = signal.symbol ?: error("Trade release has no symbol")
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
        val ltp = quote.lastPrice ?: error("Groww quote does not contain LTP")
        recordLiveBuy(eventId, signal, ltp)
        startOrReanchorWaveCampaign(eventId, symbol, ltp, "MULTIFY_NOTIFICATION")
        val learned = learningStats()

        // SHADOW is a faithful Multify-following simulator first: every paid BUY creates a virtual long immediately.
        // Strategy analysis runs as attribution/research and is not allowed to make the shadow dataset disappear.
        if (!settings.liveExecutionEffective) {
            val risk = shadowRiskState(token, settings)
            if (!risk.hardStop) {
                shadowDao.openPosition(symbol)?.takeIf { it.side == "SHORT" }?.let { closeShadow(it, ltp, "MULTIFY_LONG_REVERSAL") }
                if (shadowDao.openPosition(symbol) == null) {
                    val initialCapital = SHADOW_BUDGET
                    val qty = floor(initialCapital / ltp).toInt().coerceAtLeast(1)
                    val target = ltp * (1.0 + learned.longAveragePct / 100.0)
                    val stop = max(.05, ltp * .88)
                    openShadowDirect(
                        symbol = symbol, side = "LONG", quantity = qty, entry = ltp, stop = stop, target = target,
                        strategy = "Multify follow + rolling ${fmt(learned.longAveragePct)}% target", regime = "FOLLOW",
                        confidence = 1.0, sourceEventId = eventId, campaignBudget = SHADOW_BUDGET, capitalDeployed = qty * ltp
                    )
                    dao.updateForwarding(
                        eventId, "ANALYZED", "PAPER_BUY_OPEN",
                        "Immediate fixed-capital ₹2L shadow campaign · virtual $qty shares @ ₹${fmt(ltp)} · rolling 30-day long target ${fmt(learned.longAveragePct)}% · no averaging or capital top-ups.", null
                    )
                }
            } else {
                dao.updateForwarding(eventId, "ANALYZED", "PAPER_RISK_HALT", risk.reason, null)
            }
            runCatching { analyze(symbol, token, longSide = true, signal = signal, eventId = eventId) }
            return
        }

        if (settings.firstWaveMode == "SHORT") {
            dao.updateForwarding(eventId, "ANALYZED", "FIRST_WAVE_SHORT_ONLY", "SHORT-only mode: first-wave long is monitored but not executed.", null)
            runCatching { analyze(symbol, token, longSide = true, signal = signal, eventId = eventId) }
            return
        }
        val analysis = analyze(symbol, token, longSide = true, signal = signal, eventId = eventId)
        val f = analysis.features
        val target = signal.target ?: f.ltp * (1.0 + learned.longAveragePct / 100.0)
        val stop = signal.stopLoss ?: max(.05, f.ltp * .97)
        val entryHigh = signal.entryHigh ?: f.ltp
        val atr = f.atr14 ?: max(f.ltp * .004, .05)
        if (f.ltp <= stop || target <= f.ltp) {
            dao.updateForwarding(eventId, "ANALYZED", "WAIT", "Invalid/expired entry geometry at current LTP ₹${fmt(f.ltp)}.", null)
            return
        }
        val extension = if (f.ltp > entryHigh) (f.ltp - entryHigh) / atr else 0.0
        if (extension > .65) {
            dao.updateForwarding(eventId, "ANALYZED", "WAIT_RETEST", "Price is ${fmt(extension)} ATR above the Multify entry range; no chase. Confidence ${pct(analysis.confidence)}.", null)
            return
        }
        if ((f.spreadBps ?: 0.0) > 25.0) {
            dao.updateForwarding(eventId, "ANALYZED", "WAIT_SPREAD", "Spread ${fmt(f.spreadBps ?: 0.0)} bps exceeds cap.", null)
            return
        }
        val liveRisk = liveRiskState(token, settings)
        if (!liveRisk.allowNewRisk) {
            dao.updateForwarding(eventId, "ANALYZED", "LIVE_DEFENSIVE_WAIT", liveRisk.reason, null)
            return
        }
        if (managedDao.openPosition(ENGINE_INTRADAY, symbol) != null) {
            dao.updateForwarding(eventId, "ANALYZED", "HOLD_LONG", "Multify intraday engine already owns $symbol; no duplicate app lot.", null)
            return
        }
        assertNoExternalMisConflict(token, symbol)
        val campaignBudget = settings.dailyBudgetRupees.toDouble()
        val initialBudget = campaignBudget
        val allocation = BudgetAllocator.allocate(
            budgetRupees = initialBudget, confidence = analysis.confidence, price = f.ltp,
            stopDistance = f.ltp - stop, targetDistance = target - f.ltp, existingNetQuantity = 0
        )
        val reason = "${analysis.strategy} · ${analysis.regime} · confidence ${pct(analysis.confidence)} · ${allocation.reason}"
        if (!allocation.allowed || analysis.confidence < settings.minLiveConfidence) {
            dao.updateForwarding(eventId, "ANALYZED", "LIVE_WAIT", reason, null)
            return
        }
        preLiveGuard(settings)
        val reference = stableRef("MFI", "$eventId-$symbol-B")
        val order = placeMarket(token, symbol, "BUY", allocation.quantity, "MIS", reference)
        val qty = order.filledQuantity?.takeIf { it > 0 } ?: allocation.quantity
        val entry = order.averageFillPrice?.takeIf { it > 0 } ?: f.ltp
        startOrReanchorWaveCampaign(eventId, symbol, entry, "LIVE_FILL")
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_INTRADAY, symbol = symbol, product = "MIS", side = "LONG", quantity = qty,
                entryPrice = entry, stopPrice = stop, targetPrice = target, strategy = analysis.strategy,
                regime = analysis.regime, confidence = analysis.confidence, sourceEventId = eventId,
                openOrderId = order.growwOrderId, openReferenceId = reference, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry, capitalDeployed = entry * qty, campaignBudget = entry * qty
            )
        )
        val managed = managedDao.openPosition(ENGINE_INTRADAY, symbol) ?: error("App ledger failed to record live position")
        val smart = protectManagedPosition(token, managed)
        managedDao.updatePosition(managed.copy(smartOrderId = smart))
        dao.updateForwarding(eventId, "DELIVERED", "LIVE_BUY_PROTECTED", "$reason · app-owned qty $qty · order ${order.growwOrderId}", null)
    }

    private suspend fun handleBookProfit(eventId: Long, signal: ParsedSignal, token: String, settings: AppSettings) {
        val symbol = signal.symbol ?: error("Book-profit signal has no symbol")
        val quoteNow = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol").lastPrice
            ?: signal.exitPrice ?: error("No exit quote")
        recordLiveSell(eventId, symbol, quoteNow)
        val learned = learningStats()

        if (!settings.liveExecutionEffective) {
            val existingShort = shadowDao.openPosition(symbol)?.takeIf { it.side == "SHORT" }
            if (existingShort != null) {
                dao.updateForwarding(eventId, "ANALYZED", "PAPER_HOLD_SHORT", "A post-long shadow short is already open. Multify sell confirms the downside phase; the existing learned target remains active.", null)
                runCatching { analyze(symbol, token, longSide = false, signal = signal, eventId = eventId) }
                return
            }
            val openLong = shadowDao.openPosition(symbol)?.takeIf { it.side == "LONG" }
            var longTrade = shadowDao.latestTrade(symbol, "LONG", startOfIndiaDayMs())
            if (openLong != null) {
                if (quoteNow >= openLong.entryPrice) {
                    closeShadow(openLong, quoteNow, "MULTIFY_BOOK_PROFIT_GREEN")
                    longTrade = shadowDao.latestTrade(symbol, "LONG", startOfIndiaDayMs())
                } else {
                    dao.updateForwarding(eventId, "ANALYZED", "PAPER_HOLD_RED", "Multify sell arrived while shadow long is red. Recovery monitoring continues without averaging or added capital; no same-day short is opened.", null)
                    runCatching { analyze(symbol, token, longSide = false, signal = signal, eventId = eventId) }
                    return
                }
            }
            val prior = longTrade
            if (prior != null && prior.exitPrice > prior.entryPrice && paperRiskAllowsNewTrade(token, settings, symbol)) {
                shadowDao.openPosition(symbol)?.let { closeShadow(it, quoteNow, "MULTIFY_SELL_REVERSAL") }
                val longMove = prior.exitPrice - prior.entryPrice
                val target = AdaptiveLearningMath.shortTarget(quoteNow, prior.entryPrice, prior.exitPrice, learned.shortRetracementFraction)
                val stop = quoteNow * (1.0 + FAST_SHORT_STOP_PCT)
                val fixedCapitalCap = prior.entryPrice * prior.quantity
                val qty = floor(fixedCapitalCap / quoteNow).toInt()
                if (qty <= 0) {
                    dao.updateForwarding(eventId, "ANALYZED", "PAPER_NO_SHORT_FIXED_CAP", "The fixed campaign capital cannot open one share at the current price; no capital increase is allowed.", null)
                    return
                }
                openShadowDirect(
                    symbol, "SHORT", qty, quoteNow, stop, target,
                    "Post-Multify sell · ${fmt(learned.shortRetracementPct)}% of preceding long move", "POST_SELL",
                    1.0, eventId, fixedCapitalCap, qty * quoteNow
                )
                dao.updateForwarding(eventId, "ANALYZED", "PAPER_SHORT_OPEN", "Immediate shadow short @ ₹${fmt(quoteNow)} · preceding long move ₹${fmt(longMove)} · learned retracement ${fmt(learned.shortRetracementPct)}% · target ₹${fmt(target)}.", null)
            } else {
                dao.updateForwarding(eventId, "ANALYZED", "PAPER_NO_SHORT", "No profitable preceding shadow long is available for the post-sell short.", null)
            }
            runCatching { analyze(symbol, token, longSide = false, signal = signal, eventId = eventId) }
            return
        }

        val liveLong = managedDao.openPosition(ENGINE_INTRADAY, symbol)?.takeIf { it.side == "LONG" }
        val fixedPostSellCap = liveLong?.let {
            FixedCapitalPolicy.cap(it.campaignBudget, it.capitalDeployed, it.entryPrice * it.quantity)
        } ?: settings.dailyBudgetRupees.toDouble()
        liveLong?.let {
            assertManagedMisStillOwned(token, it)
            closeManaged(token, it, quoteNow, "MULTIFY_BOOK_PROFIT")
        }
        if (settings.firstWaveMode == "LONG" || !settings.postSellShortEnabled) {
            dao.updateForwarding(eventId, "ANALYZED", "FIRST_WAVE_LONG_ONLY", "LONG-only mode: Multify book-profit closed the long; post-sell first-wave short is monitored but not executed.", null)
            runCatching { analyze(symbol, token, longSide = false, signal = signal, eventId = eventId) }
            return
        }
        val analysis = analyze(symbol, token, longSide = false, signal = signal, eventId = eventId)
        val f = analysis.features
        val atr = f.atr14 ?: max(f.ltp * .004, .05)
        val stop = f.ltp + atr * .90
        val target = max(.05, f.ltp - atr * 1.55)
        val shortGate = min(settings.minLiveConfidence, MULTIFY_SELL_SHORT_GATE)
        val liveRisk = liveRiskState(token, settings)
        if (!liveRisk.allowNewRisk) {
            dao.updateForwarding(eventId, "ANALYZED", "LIVE_WAIT_SHORT", liveRisk.reason, null)
            return
        }
        assertNoExternalMisConflict(token, symbol)
        val allocation = BudgetAllocator.allocate(fixedPostSellCap, max(analysis.confidence, .76), f.ltp, stop - f.ltp, f.ltp - target, 0)
        val reason = "${analysis.strategy} · ${analysis.regime} · short confidence ${pct(analysis.confidence)} · Multify sell prior · ${allocation.reason}"
        if (!allocation.allowed || analysis.confidence < shortGate) {
            dao.updateForwarding(eventId, "ANALYZED", "LIVE_WAIT_SHORT", reason, null)
            return
        }
        preLiveGuard(settings)
        val reference = stableRef("MFI", "$eventId-$symbol-S")
        val order = placeMarket(token, symbol, "SELL", allocation.quantity, "MIS", reference)
        val qty = order.filledQuantity?.takeIf { it > 0 } ?: allocation.quantity
        val entry = order.averageFillPrice?.takeIf { it > 0 } ?: f.ltp
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_INTRADAY, symbol = symbol, product = "MIS", side = "SHORT", quantity = qty,
                entryPrice = entry, stopPrice = stop, targetPrice = target, strategy = analysis.strategy,
                regime = analysis.regime, confidence = max(analysis.confidence, .76), sourceEventId = eventId,
                openOrderId = order.growwOrderId, openReferenceId = reference, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry, capitalDeployed = entry * qty, campaignBudget = min(fixedPostSellCap, entry * qty)
            )
        )
        val managed = managedDao.openPosition(ENGINE_INTRADAY, symbol) ?: error("App ledger failed to record short")
        val smart = protectManagedPosition(token, managed)
        managedDao.updatePosition(managed.copy(smartOrderId = smart))
        dao.updateForwarding(eventId, "DELIVERED", "LIVE_SHORT_PROTECTED", "$reason · app-owned qty $qty", null)
    }

    private suspend fun handleFastTrackBuy(eventId: Long, signal: ParsedSignal, token: String, settings: AppSettings) {
        val symbol = signal.symbol ?: return
        if (!settings.fastTrackEffective) return
        if (managedDao.openPosition(ENGINE_FAST_TRACK, symbol) != null) return
        require(settings.brokerDdpiEnabled) { "Fast Track requires Groww DDPI for unattended delivery exits" }
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
        val ltp = quote.lastPrice ?: return
        val learned = learningStats()
        val totalBudget = settings.fastTrackBudgetRupees.toDouble()
        val initialCapital = totalBudget
        val qty = floor(initialCapital / ltp).toInt()
        if (qty <= 0) return
        val margin = apiFactory.groww.margins(bearer(token)).requirePayload("Groww margin")
        val cash = margin.equity?.cncBalanceAvailable ?: margin.clearCash
        require(cash >= qty * ltp) { "Fast Track CNC requires ₹${(qty * ltp).toInt()} but available CNC cash is about ₹${cash.toInt()}" }
        val ref = stableRef("MFM", "$eventId-$symbol-B")
        val order = placeMarket(token, symbol, "BUY", qty, "CNC", ref)
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0 } ?: ltp
        val target = entry * (1.0 + learned.longAveragePct / 100.0)
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FAST_TRACK, symbol = symbol, product = "CNC", side = "LONG", quantity = filled,
                entryPrice = entry, stopPrice = null, targetPrice = target, strategy = "Multify notification follow · rolling ${fmt(learned.longAveragePct)}% exit",
                regime = "DELIVERY", confidence = 1.0, sourceEventId = eventId, openOrderId = order.growwOrderId,
                openReferenceId = ref, openedAtMs = System.currentTimeMillis(), lastPrice = entry,
                maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = entry, capitalDeployed = entry * filled, campaignBudget = entry * filled, addCount = 0
            )
        )
        auditLogger.log("FAST_TRACK", "CNC_BUY_FILLED", mapOf(
            "symbol" to symbol, "entry" to entry, "quantity" to filled, "configured_cap" to totalBudget,
            "fixed_capital" to (entry * filled), "capital_top_up_allowed" to false,
            "rolling_long_target_pct" to learned.longAveragePct
        ))
    }

    private suspend fun handleFastTrackSell(eventId: Long, signal: ParsedSignal, token: String, settings: AppSettings) {
        val symbol = signal.symbol ?: return
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = symbol).requirePayload("Quote $symbol")
        val ltp = quote.lastPrice ?: signal.exitPrice ?: return
        var longTrade = managedDao.latestTrade(ENGINE_FAST_TRACK, symbol, "LONG", startOfIndiaDayMs())
        val holding = managedDao.openPosition(ENGINE_FAST_TRACK, symbol)
        if (holding != null) {
            val brokerHolding = apiFactory.groww.holdings(bearer(token)).requirePayload("Groww holdings")
                .holdings.firstOrNull { it.tradingSymbol.equals(symbol, true) }
            require((brokerHolding?.quantity ?: 0) >= holding.quantity) {
                "Fast Track owns ${holding.quantity} $symbol shares in its ledger, but Groww holdings are lower. External/manual activity detected; automatic sell blocked."
            }
            if (ltp < holding.entryPrice) {
                auditLogger.log("FAST_TRACK", "MULTIFY_SELL_HELD_RED", mapOf("symbol" to symbol, "ltp" to ltp, "average_entry" to holding.entryPrice, "pnl_pct" to ((ltp/holding.entryPrice-1)*100)))
                return
            }
            longTrade = closeManaged(token, holding, ltp, "MULTIFY_BOOK_PROFIT_GREEN")
        }
        val closed = longTrade ?: return
        if (closed.exitPrice <= closed.entryPrice) return
        openFastTrackShortFromLong(token, closed, ltp, eventId, settings, "MULTIFY_BOOK_PROFIT")
    }

    private suspend fun openShadowPostLongShort(
        closed: ShadowTradeEntity, entryPrice: Double, sourceEventId: Long?, token: String, settings: AppSettings, trigger: String
    ) {
        if (closed.side != "LONG" || closed.exitPrice <= closed.entryPrice) return
        if (shadowDao.openPosition(closed.symbol) != null) return
        if (!paperRiskAllowsNewTrade(token, settings, closed.symbol)) return
        val learned = learningStats()
        val target = AdaptiveLearningMath.shortTarget(entryPrice, closed.entryPrice, closed.exitPrice, learned.shortRetracementFraction)
        val stop = entryPrice * (1.0 + FAST_SHORT_STOP_PCT)
        val fixedCapitalCap = closed.entryPrice * closed.quantity
        val qty = floor(fixedCapitalCap / entryPrice).toInt()
        if (qty <= 0) return
        openShadowDirect(
            symbol = closed.symbol, side = "SHORT", quantity = qty, entry = entryPrice, stop = stop, target = target,
            strategy = "Adaptive post-long short · ${fmt(learned.shortRetracementPct)}% retracement", regime = "POST_SELL",
            confidence = 1.0, sourceEventId = sourceEventId, campaignBudget = fixedCapitalCap, capitalDeployed = qty * entryPrice
        )
        auditLogger.log("SHADOW", "ADAPTIVE_SHORT_OPEN", mapOf(
            "symbol" to closed.symbol, "trigger" to trigger, "entry" to entryPrice, "long_entry" to closed.entryPrice,
            "long_exit" to closed.exitPrice, "retracement_pct" to learned.shortRetracementPct, "target" to target
        ))
    }

    private suspend fun openFastTrackShortFromLong(
        token: String, closed: ManagedTradeEntity, entryPrice: Double, sourceEventId: Long?, settings: AppSettings, trigger: String
    ) {
        if (closed.side != "LONG" || closed.exitPrice <= closed.entryPrice) return
        if (!settings.postSellShortEnabled || marketSession() != "OPEN") return
        if (ZonedDateTime.now(INDIA).toLocalTime().isAfter(LocalTime.of(15, 5))) return
        if (settings.safetyHalt || managedDao.openPosition(ENGINE_FAST_SHORT, closed.symbol) != null) return
        val risk = liveRiskState(token, settings)
        if (!risk.allowNewRisk) return
        assertNoExternalMisConflict(token, closed.symbol)
        val learned = learningStats()
        val target = AdaptiveLearningMath.shortTarget(entryPrice, closed.entryPrice, closed.exitPrice, learned.shortRetracementFraction)
        val stop = entryPrice * (1.0 + FAST_SHORT_STOP_PCT)
        val fixedCapitalCap = closed.entryPrice * closed.quantity
        val allocation = BudgetAllocator.allocate(fixedCapitalCap, .90, entryPrice, stop - entryPrice, entryPrice - target, 0)
        if (!allocation.allowed) return
        auditLogger.log("FAST_TRACK", "POST_LONG_SHORT_PLAN", mapOf(
            "symbol" to closed.symbol, "trigger" to trigger, "long_entry" to closed.entryPrice, "long_exit" to closed.exitPrice,
            "long_price_gain_per_share" to (closed.exitPrice - closed.entryPrice), "learned_retracement_fraction" to learned.shortRetracementFraction,
            "planned_short_entry" to entryPrice, "planned_short_target" to target
        ))
        val ref = stableRef("MFS", "${sourceEventId ?: closed.id}-${closed.symbol}-${System.currentTimeMillis()}")
        val order = placeMarket(token, closed.symbol, "SELL", allocation.quantity, "MIS", ref)
        val qty = order.filledQuantity?.takeIf { it > 0 } ?: allocation.quantity
        val actualEntry = order.averageFillPrice?.takeIf { it > 0 } ?: entryPrice
        val actualTarget = AdaptiveLearningMath.shortTarget(actualEntry, closed.entryPrice, closed.exitPrice, learned.shortRetracementFraction)
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_FAST_SHORT, symbol = closed.symbol, product = "MIS", side = "SHORT", quantity = qty,
                entryPrice = actualEntry, stopPrice = actualEntry * (1.0 + FAST_SHORT_STOP_PCT), targetPrice = actualTarget,
                strategy = "Adaptive post-long short · ${fmt(learned.shortRetracementPct)}% of preceding long move", regime = "FAST_TRACK", confidence = .90,
                sourceEventId = sourceEventId, openOrderId = order.growwOrderId, openReferenceId = ref,
                openedAtMs = System.currentTimeMillis(), lastPrice = actualEntry, maxFavourablePrice = actualEntry,
                maxAdversePrice = actualEntry, lastEvaluatedAtMs = System.currentTimeMillis(), anchorPrice = actualEntry,
                capitalDeployed = actualEntry * qty, campaignBudget = min(fixedCapitalCap, actualEntry * qty)
            )
        )
        val short = managedDao.openPosition(ENGINE_FAST_SHORT, closed.symbol) ?: return
        val smart = protectManagedPosition(token, short)
        managedDao.updatePosition(short.copy(smartOrderId = smart))
    }

    private suspend fun assessAutoWave(symbol: String, token: String, currentSide: String, exposureRupees: Double): Triple<WaveDirectionDecision, StrategyEvaluation, StrategyEvaluation> {
        val auth = bearer(token)
        val quote = apiFactory.groww.quote(auth, tradingSymbol = symbol).requirePayload("Wave quote $symbol")
        val now = ZonedDateTime.now(INDIA)
        val start = now.toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        var intervalMinutes = 5
        var historical = apiFactory.groww.historicalCandles(
            authorization = auth, growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "5minute"
        ).requirePayload("Wave candles $symbol")
        if (now.toLocalTime().isBefore(LocalTime.of(9, 40)) || historical.candles.size < 5) {
            intervalMinutes = 1
            historical = apiFactory.groww.historicalCandles(
                authorization = auth, growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "1minute"
            ).requirePayload("Wave 1-minute candles $symbol")
        }
        require(historical.candles.size >= 3) { "Not enough candles for wave direction" }
        val features = LocalStrategyEngine.buildFeatures(quote, historical, intervalMinutes)
        val synthetic = ParsedSignal(SignalType.TRADE_RELEASE, symbol = symbol, rawText = "auto-wave-neutral", confidence = 1.0)
        // Deliberately remove Multify BUY/SELL event priors here. From wave 2 onward the market path decides the side.
        val longEval = LocalStrategyEngine.evaluateLong(synthetic, features, includeEventPrior = false)
        val shortEval = LocalStrategyEngine.evaluateShort(features, includeEventPrior = false)
        val trajectory = MarketTrajectoryMath.classify(
            features = features, longScore = longEval.directionalScore, shortScore = shortEval.directionalScore
        )
        val rawDecision = WaveDirectionMath.decide(
            currentSide = currentSide, exposureRupees = max(exposureRupees, features.ltp), price = features.ltp,
            atr = features.atr14 ?: max(features.ltp * .004, .05), spreadBps = features.spreadBps,
            longDirectionalScore = longEval.directionalScore, longConfidence = longEval.confidence,
            shortDirectionalScore = shortEval.directionalScore, shortConfidence = shortEval.confidence
        )
        // In a clean trend, do not let a marginal counter-trend wave vote override the broader trajectory.
        // In OSCILLATING mode both sides remain eligible at different waves; the current snapshot still chooses only one side.
        val decision = if (trajectory.regime != "OSCILLATING" && rawDecision.action != "HOLD" && rawDecision.action != trajectory.preferredSide && trajectory.confidence >= .66) {
            rawDecision.copy(action = "HOLD", reason = "Trajectory veto: ${trajectory.reason}; raw=${rawDecision.reason}")
        } else rawDecision.copy(reason = "${rawDecision.reason}; ${trajectory.reason}")
        auditLogger.log("AUTO_WAVE", "DIRECTION_DECISION", mapOf(
            "symbol" to symbol, "current_side" to currentSide, "action" to decision.action,
            "long_probability" to decision.longProbability, "short_probability" to decision.shortProbability,
            "long_ev_rupees" to decision.longExpectedValueRupees, "short_ev_rupees" to decision.shortExpectedValueRupees,
            "ev_gap_rupees" to decision.expectedValueGapRupees, "reason" to decision.reason,
            "ltp" to features.ltp, "vwap" to features.vwap, "ema9" to features.ema9, "ema20" to features.ema20,
            "atr14" to features.atr14, "rsi14" to features.rsi14, "rvol" to features.rvol,
            "macd_histogram" to features.macdHistogram, "trend_slope_atr" to features.trendSlopeAtr,
            "order_book_imbalance" to features.orderBookImbalance, "day_change_pct" to features.dayChangePct,
            "market_cap" to features.marketCap, "range_52_position" to features.range52Position,
            "trajectory_regime" to trajectory.regime, "trajectory_score" to trajectory.composite,
            "trajectory_waveiness" to trajectory.waveiness, "trajectory_preferred" to trajectory.preferredSide
        ))
        return Triple(decision, longEval, shortEval)
    }

    private suspend fun applyAutoWaveDecision(
        token: String, position: ManagedPositionEntity, ltp: Double, waveIndex: Int, risk: RiskState
    ): ManagedPositionEntity? {
        val waveNumber = waveIndex + 1 // wave 1 is the initial Multify entry; ±2% is wave 2.
        val (decision, longEval, shortEval) = assessAutoWave(position.symbol, token, position.side, FixedCapitalPolicy.cap(position.campaignBudget, position.capitalDeployed, position.entryPrice * position.quantity))
        val chosen = if (decision.action == "SHORT") shortEval else longEval
        val processed = position.copy(addCount = waveIndex, lastEvaluatedAtMs = System.currentTimeMillis())
        val fixedCapitalCap = FixedCapitalPolicy.cap(position.campaignBudget, position.capitalDeployed, position.entryPrice * position.quantity)

        if (decision.action == "HOLD") {
            managedDao.updatePosition(processed)
            auditLogger.log("AUTO_WAVE", "HOLD", mapOf("symbol" to position.symbol, "wave" to waveNumber, "ltp" to ltp, "reason" to decision.reason))
            return processed
        }
        if (!risk.allowNewRisk) {
            managedDao.updatePosition(processed)
            auditLogger.log("AUTO_WAVE", "RISK_VETO", mapOf("symbol" to position.symbol, "wave" to waveNumber, "wanted" to decision.action, "reason" to risk.reason))
            return processed
        }

        if (decision.action.equals(position.side, true)) {
            val updated = processed.copy(
                strategy = "AUTO wave $waveNumber continuation · ${chosen.strategy}",
                regime = chosen.regime,
                confidence = decision.confidence
            )
            managedDao.updatePosition(updated)
            auditLogger.log("AUTO_WAVE", "SAME_SIDE_HOLD_FIXED_CAPITAL", mapOf(
                "symbol" to position.symbol, "wave" to waveNumber, "side" to position.side,
                "fixed_capital_cap" to fixedCapitalCap, "capital_added" to 0.0, "decision" to decision.reason
            ))
            return updated
        }

        // Opposite side won decisively. Flatten first, then reopen only within the existing fixed capital cap.
        val anchor = position.anchorPrice.takeIf { it > 0.0 } ?: position.entryPrice
        val entryPx = chosen.features.ltp
        val atr = chosen.features.atr14 ?: max(entryPx * .004, .05)
        val stopPx = if (decision.action == "LONG") max(.05, entryPx - atr * .95) else entryPx + atr * .95
        val targetPx = if (decision.action == "LONG") entryPx + atr * 1.55 else max(.05, entryPx - atr * 1.55)
        val allocation = BudgetAllocator.allocate(
            fixedCapitalCap, decision.confidence, entryPx, abs(entryPx - stopPx), abs(targetPx - entryPx), 0
        )
        if (!allocation.allowed) {
            managedDao.updatePosition(processed)
            auditLogger.log("AUTO_WAVE", "FLIP_REJECTED_FIXED_CAPITAL", mapOf(
                "symbol" to position.symbol, "wave" to waveNumber, "wanted" to decision.action,
                "fixed_capital_cap" to fixedCapitalCap, "reason" to allocation.reason
            ))
            return processed
        }

        closeManaged(token, position, ltp, "AUTO_WAVE_${waveNumber}_FLIP_TO_${decision.action}")
        val refreshed = preferences.settings.first()
        val after = liveRiskState(token, refreshed)
        if (!after.allowNewRisk || !refreshed.liveExecutionEffective) return null
        assertNoExternalMisConflict(token, position.symbol)
        openManagedReversal(
            token, position, decision.action, allocation.quantity, entryPx, stopPx, targetPx, chosen,
            waveAnchor = anchor, waveIndex = waveIndex, campaignBudget = fixedCapitalCap
        )
        val reopened = managedDao.openPosition(ENGINE_INTRADAY, position.symbol)
        auditLogger.log("AUTO_WAVE", "ONE_SIDE_FLIP_FIXED_CAPITAL", mapOf(
            "symbol" to position.symbol, "wave" to waveNumber, "from" to position.side, "to" to decision.action,
            "fixed_capital_cap" to fixedCapitalCap, "capital_added" to 0.0, "decision" to decision.reason
        ))
        return reopened
    }

    private suspend fun analyze(
        symbol: String, token: String, longSide: Boolean, signal: ParsedSignal, eventId: Long? = null, includeEventPrior: Boolean = true
    ): StrategyEvaluation {
        val auth = bearer(token)
        val quote = apiFactory.groww.quote(auth, tradingSymbol = symbol).requirePayload("Quote $symbol")
        val now = ZonedDateTime.now(INDIA)
        val start = now.toLocalDate().atTime(9, 15).format(HIST_FORMAT)
        val end = now.toLocalDateTime().format(HIST_FORMAT)
        var intervalMinutes = 5
        var historical = apiFactory.groww.historicalCandles(
            authorization = auth, growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "5minute"
        ).requirePayload("Historical candles $symbol")
        if (now.toLocalTime().isBefore(LocalTime.of(9, 40)) || historical.candles.size < 5) {
            intervalMinutes = 1
            historical = apiFactory.groww.historicalCandles(
                authorization = auth, growwSymbol = "NSE-$symbol", startTime = start, endTime = end, candleInterval = "1minute"
            ).requirePayload("1-minute historical candles $symbol")
        }
        require(historical.candles.size >= 3) { "Not enough intraday candles yet; signal retained" }
        val features = LocalStrategyEngine.buildFeatures(quote, historical, intervalMinutes)
        val evaluation = if (longSide) LocalStrategyEngine.evaluateLong(signal, features, includeEventPrior) else LocalStrategyEngine.evaluateShort(features, includeEventPrior)
        auditLogger.log("STRATEGY", "EVALUATION", mapOf(
            "symbol" to symbol,
            "side" to evaluation.side,
            "regime" to evaluation.regime,
            "selected_strategy" to evaluation.strategy,
            "directional_score" to evaluation.directionalScore,
            "confidence" to evaluation.confidence,
            "reason" to evaluation.reason,
            "interval_minutes" to intervalMinutes,
            "candle_count" to historical.candles.size,
            "ltp" to features.ltp,
            "bid" to features.bid,
            "ask" to features.ask,
            "spread_bps" to features.spreadBps,
            "vwap" to features.vwap,
            "ema9" to features.ema9,
            "ema20" to features.ema20,
            "atr14" to features.atr14,
            "rsi14" to features.rsi14,
            "bollinger_width" to features.bollWidth,
            "macd_histogram" to features.macdHistogram,
            "trend_slope_atr" to features.trendSlopeAtr,
            "relative_volume" to features.rvol,
            "donchian_high" to features.donchianHigh,
            "donchian_low" to features.donchianLow,
            "orb5_high" to features.orb5High,
            "orb5_low" to features.orb5Low,
            "orb15_high" to features.orb15High,
            "orb15_low" to features.orb15Low,
            "order_book_imbalance" to features.orderBookImbalance,
            "day_change_pct" to features.dayChangePct,
            "market_cap" to features.marketCap,
            "range_52_position" to features.range52Position,
            "votes" to evaluation.votes.joinToString(" || ") { "${it.name}=${String.format(Locale.US, "%.4f", it.score)} (${it.note})" }
        ))
        learningDao.insertStrategySnapshot(
            StrategySnapshotEntity(
                atMs = System.currentTimeMillis(), eventId = eventId, symbol = symbol, side = evaluation.side,
                regime = evaluation.regime, strategy = evaluation.strategy, directionalScore = evaluation.directionalScore, confidence = evaluation.confidence,
                ltp = features.ltp, vwap = features.vwap, ema9 = features.ema9, ema20 = features.ema20, atr14 = features.atr14,
                rsi14 = features.rsi14, rvol = features.rvol, macdHistogram = features.macdHistogram, spreadBps = features.spreadBps,
                orderBookImbalance = features.orderBookImbalance, dayChangePct = features.dayChangePct, marketCap = features.marketCap,
                votes = evaluation.votes.joinToString(" || ") { "${it.name}=${String.format(Locale.US, "%.4f", it.score)} (${it.note})" }
            )
        )
        return evaluation
    }

    suspend fun monitorShadowPositions(): Int {
        val settings = preferences.settings.first()
        val open = shadowDao.openPositions()
        if (open.isEmpty()) return 0
        val token = ensureToken() ?: return open.size
        val now = ZonedDateTime.now(INDIA)
        val risk = shadowRiskState(token, settings)
        if (risk.hardStop) {
            open.forEach { p ->
                val ltp = runCatching { apiFactory.groww.quote(bearer(token), tradingSymbol = p.symbol).requirePayload("Quote").lastPrice }.getOrNull() ?: p.lastPrice
                closeShadow(p, ltp, "HARD_DAILY_LOSS_CAP")
            }
            return 0
        }
        for (position in open) runCatching { monitorOneShadow(position, token, settings, now, risk) }
        return shadowDao.openPositions().size
    }

    suspend fun monitorManagedPositions(): Int {
        val open = managedDao.openPositions()
        if (open.isEmpty()) return 0
        val token = ensureToken() ?: return open.size
        val settings = preferences.settings.first()
        val now = ZonedDateTime.now(INDIA)
        val misRisk = liveRiskState(token, settings)
        if (misRisk.hardStop) {
            preferences.setSafetyHalt(true)
            managedDao.openPositions().forEach { p ->
                runCatching {
                    if (p.product == "MIS") assertManagedMisStillOwned(token, p)
                    val ltp = apiFactory.groww.quote(bearer(token), tradingSymbol = p.symbol).requirePayload("Quote ${p.symbol}").lastPrice ?: p.lastPrice
                    closeManaged(token, p, ltp, "HARD_DAILY_LOSS_PREEMPT")
                }
            }
            return managedDao.openPositions().size
        }
        val ordered = open.sortedWith(compareBy<ManagedPositionEntity> {
            when {
                it.addCount <= 0 && it.regime != REGIME_RECOVERY_HOLD -> 0 // active first wave: highest priority
                it.regime == REGIME_RECOVERY_HOLD -> 2               // safety-only recovery hold
                else -> 1                                             // wave 2+ secondary
            }
        }.thenBy { it.openedAtMs })
        for (position in ordered) runCatching { monitorOneManaged(position, token, settings, now, misRisk) }
        return managedDao.openPositions().size
    }

    private suspend fun monitorOneShadow(position: ShadowPositionEntity, token: String, settings: AppSettings, now: ZonedDateTime, risk: RiskState) {
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = position.symbol).requirePayload("Quote ${position.symbol}")
        val ltp = quote.lastPrice ?: return
        var updated = updateShadowMark(position, ltp)
        val followLong = updated.side == "LONG" && updated.regime == "FOLLOW"
        val followShort = updated.side == "SHORT" && updated.regime == "POST_SELL"

        if (followLong) {
            val learned = learningStats()
            val learnedTarget = updated.entryPrice * (1.0 + learned.longAveragePct / 100.0)
            if (kotlin.math.abs(updated.targetPrice - learnedTarget) > .01) {
                updated = updated.copy(targetPrice = learnedTarget)
                shadowDao.updatePosition(updated)
            }
            if (ltp >= learnedTarget && ltp > updated.entryPrice) {
                val closed = closeShadow(updated, ltp, "ROLLING_30D_LONG_TARGET")
                recordAdaptiveExit(updated.symbol, closed.exitPrice)
                openShadowPostLongShort(closed, ltp, updated.sourceEventId, token, settings, "ADAPTIVE_LONG_TARGET")
                return
            }
            // Multify-following longs are allowed to remain virtual holdings overnight when red.
            // The portfolio hard loss cap still overrides this rule.
            if (now.toLocalTime() >= LocalTime.of(15, 22)) return
            if (System.currentTimeMillis() - updated.openedAtMs >= 75_000L) {
                val synthetic = ParsedSignal(SignalType.TRADE_RELEASE, symbol = updated.symbol, rawText = "shadow-follow-monitor", confidence = 1.0)
                runCatching { analyze(updated.symbol, token, longSide = true, signal = synthetic) }
            }
            return
        }

        if (followShort) {
            val forceFlat = now.dayOfWeek.value >= 6 || !now.toLocalTime().isBefore(LocalTime.of(15, 20))
            val stopHit = ltp >= updated.stopPrice
            val targetHit = ltp <= updated.targetPrice
            when {
                forceFlat -> closeShadow(updated, ltp, "POST_SELL_FORCE_FLAT")
                stopHit -> closeShadow(updated, ltp, "POST_SELL_PROTECTIVE_STOP")
                targetHit -> closeShadow(updated, ltp, "LEARNED_POST_SELL_TARGET")
            }
            if (System.currentTimeMillis() - updated.lastEvaluatedAtMs >= 75_000L) {
                val synthetic = ParsedSignal(SignalType.BOOK_PROFIT, symbol = updated.symbol, rawText = "post-sell-shadow-monitor", confidence = 1.0)
                runCatching { analyze(updated.symbol, token, longSide = false, signal = synthetic) }
                shadowDao.openPosition(updated.symbol)?.let { p ->
                    if (p.id == updated.id) shadowDao.updatePosition(p.copy(lastEvaluatedAtMs = System.currentTimeMillis()))
                }
            }
            return
        }

        val forceFlat = now.dayOfWeek.value >= 6 || !now.toLocalTime().isBefore(LocalTime.of(15, 20))
        val stopHit = if (updated.side == "LONG") ltp <= updated.stopPrice else ltp >= updated.stopPrice
        if (forceFlat) { closeShadow(updated, ltp, "FORCE_FLAT_15_20"); return }
        if (stopHit) { closeShadow(updated, ltp, "PROTECTIVE_STOP"); return }
        if (System.currentTimeMillis() - updated.openedAtMs < 75_000L) return

        val synthetic = ParsedSignal(SignalType.TRADE_RELEASE, symbol = updated.symbol, rawText = "shadow-monitor", confidence = 1.0)
        val same = analyze(updated.symbol, token, longSide = updated.side == "LONG", signal = synthetic, includeEventPrior = false)
        val opposite = analyze(updated.symbol, token, longSide = updated.side == "SHORT", signal = synthetic, includeEventPrior = false)
        val atr = same.features.atr14 ?: max(ltp * .004, .05)
        val targetHit = if (updated.side == "LONG") ltp >= updated.targetPrice else ltp <= updated.targetPrice
        if (targetHit) {
            if (same.confidence >= RUNNER_CONFIDENCE && same.directionalScore >= .08) {
                val newStop = if (updated.side == "LONG") max(updated.stopPrice, max(updated.entryPrice + atr * .20, ltp - atr * .85)) else min(updated.stopPrice, min(updated.entryPrice - atr * .20, ltp + atr * .85))
                val newTarget = if (updated.side == "LONG") ltp + atr * 1.25 else max(.05, ltp - atr * 1.25)
                updated = updated.copy(stopPrice = newStop, targetPrice = newTarget)
                shadowDao.updatePosition(updated)
            } else {
                closeShadow(updated, ltp, "TARGET_CHECKPOINT_WEAKENING")
                return
            }
        }
        if (risk.defensive && same.confidence < DEFENSIVE_HOLD_CONFIDENCE) {
            closeShadow(updated, ltp, "DEFENSIVE_DAILY_RISK_EXIT")
            return
        }
        if (risk.profitProtect && same.confidence < RUNNER_CONFIDENCE) {
            closeShadow(updated, ltp, "PROFIT_HIGH_WATER_PROTECT")
            return
        }
        if (opposite.confidence >= PAPER_REVERSAL_CONFIDENCE && opposite.directionalScore >= .12 && same.confidence < .62) {
            closeShadow(updated, ltp, "STRATEGY_REVERSAL")
            if (!paperRiskAllowsNewTrade(token, settings, updated.symbol)) return
            val side = if (updated.side == "LONG") "SHORT" else "LONG"
            val stop = if (side == "LONG") ltp - atr * .95 else ltp + atr * .95
            val target = if (side == "LONG") ltp + atr * 1.55 else max(.05, ltp - atr * 1.55)
            val fixedCapitalCap = FixedCapitalPolicy.cap(updated.campaignBudget, updated.capitalDeployed, updated.entryPrice * updated.quantity)
            val allocation = BudgetAllocator.allocate(fixedCapitalCap, opposite.confidence, ltp, abs(ltp - stop), abs(target - ltp), 0)
            if (allocation.allowed) openShadow(updated.symbol, side, allocation.quantity, ltp, stop, target, opposite, updated.sourceEventId, fixedCapitalCap)
        }
    }

    private suspend fun monitorOneManaged(position: ManagedPositionEntity, token: String, settings: AppSettings, now: ZonedDateTime, risk: RiskState) {
        val quote = apiFactory.groww.quote(bearer(token), tradingSymbol = position.symbol).requirePayload("Quote ${position.symbol}")
        val ltp = quote.lastPrice ?: return
        var updated = updateManagedMark(position, ltp)
        if (position.product == "CNC") {
            if (updated.engine == ENGINE_FAST_TRACK && updated.side == "LONG") {
                if (updated.regime != REGIME_RECOVERY_HOLD) {
                }
                val learned = learningStats()
                val target = updated.entryPrice * (1.0 + learned.longAveragePct / 100.0)
                if (updated.targetPrice == null || kotlin.math.abs((updated.targetPrice ?: target) - target) > .01) {
                    updated = updated.copy(targetPrice = target)
                    managedDao.updatePosition(updated)
                }
                if (ltp >= target && ltp > updated.entryPrice) {
                    val recoveryOnly = updated.regime == REGIME_RECOVERY_HOLD
                    val closed = closeManaged(token, updated, ltp, if (recoveryOnly) "RECOVERY_HOLD_GREEN_EXIT" else "ROLLING_30D_LONG_TARGET")
                    recordAdaptiveExit(updated.symbol, closed.exitPrice)
                    if (!recoveryOnly) {
                        openFastTrackShortFromLong(token, closed, closed.exitPrice, updated.sourceEventId, settings, "ADAPTIVE_LONG_TARGET")
                    }
                }
            }
            return
        }

        assertManagedMisStillOwned(token, updated)
        if (!updated.smartOrderId.isNullOrBlank()) {
            val smart = runCatching { apiFactory.groww.smartOrderStatus(bearer(token), smartOrderId = updated.smartOrderId).requirePayload("OCO status") }.getOrNull()
            if (smart != null && smart.status.equals("COMPLETED", true)) {
                closeManagedLedgerOnly(updated, ltp, "OCO_COMPLETED_RECONCILED")
                return
            }
        }
        if (now.dayOfWeek.value >= 6 || !now.toLocalTime().isBefore(LocalTime.of(15, 20))) {
            closeManaged(token, updated, ltp, "FORCE_FLAT_15_20")
            return
        }
        val stop = updated.stopPrice
        if (stop != null && ((updated.side == "LONG" && ltp <= stop) || (updated.side == "SHORT" && ltp >= stop))) {
            closeManaged(token, updated, ltp, "PROTECTIVE_STOP")
            return
        }
        val tradingDecisionsEnabled = updated.regime != REGIME_RECOVERY_HOLD && updated.regime != REGIME_SECONDARY_PREVIEW
        if (updated.engine == ENGINE_INTRADAY && tradingDecisionsEnabled) {
            val anchor = updated.anchorPrice.takeIf { it > 0.0 } ?: updated.entryPrice
            val waveIndex = WaveDirectionMath.waveIndex(anchor, ltp)
            if (waveIndex >= 1 && waveIndex > updated.addCount) {
                val waveNumber = waveIndex + 1
                if (waveNumber > settings.activeWaveCount.toInt()) {
                    val (preview, _, _) = assessAutoWave(updated.symbol, token, updated.side, FixedCapitalPolicy.cap(updated.campaignBudget, updated.capitalDeployed, updated.entryPrice * updated.quantity))
                    auditLogger.log("AUTO_WAVE", "PREVIEW_ONLY", mapOf(
                        "symbol" to updated.symbol, "wave" to waveNumber, "configured_waves" to settings.activeWaveCount,
                        "green_side" to preview.action, "long_probability" to preview.longProbability,
                        "short_probability" to preview.shortProbability, "reason" to preview.reason
                    ))
                    updated = updated.copy(addCount = waveIndex, lastEvaluatedAtMs = System.currentTimeMillis())
                    managedDao.updatePosition(updated)
                } else {
                    val afterWave = applyAutoWaveDecision(token, updated, ltp, waveIndex, risk) ?: return
                    updated = afterWave
                }
            }
        }
        if (System.currentTimeMillis() - updated.openedAtMs < 75_000L) return
        val synthetic = ParsedSignal(SignalType.TRADE_RELEASE, symbol = updated.symbol, rawText = "live-monitor", confidence = 1.0)
        val same = analyze(updated.symbol, token, longSide = updated.side == "LONG", signal = synthetic)
        val opposite = analyze(updated.symbol, token, longSide = updated.side == "SHORT", signal = synthetic)
        val atr = same.features.atr14 ?: max(ltp * .004, .05)
        // First-wave protection is always active in AUTO/LONG/SHORT modes. Trail only in the favourable direction.
        val favourableAtr = if (updated.side == "LONG") (ltp - updated.entryPrice) / atr else (updated.entryPrice - ltp) / atr
        if (favourableAtr >= 0.60) {
            val currentStop = updated.stopPrice ?: updated.entryPrice
            val candidateStop = if (updated.side == "LONG") {
                max(updated.entryPrice, ltp - atr * if (favourableAtr >= 1.50) 0.70 else 0.95)
            } else {
                min(updated.entryPrice, ltp + atr * if (favourableAtr >= 1.50) 0.70 else 0.95)
            }
            val improves = if (updated.side == "LONG") candidateStop > currentStop + 0.01 else candidateStop < currentStop - 0.01
            if (improves) {
                updated = updated.copy(stopPrice = candidateStop, lastEvaluatedAtMs = System.currentTimeMillis())
                managedDao.updatePosition(updated)
                modifyOcoIfPossible(token, updated)
                auditLogger.log("TRAILING_STOP", "RATCHET", mapOf("symbol" to updated.symbol, "side" to updated.side, "ltp" to ltp, "atr" to atr, "new_stop" to candidateStop, "favourable_atr" to favourableAtr))
            }
        }
        val targetHit = updated.targetPrice?.let { target -> if (updated.side == "LONG") ltp >= target else ltp <= target } ?: false
        if (targetHit) {
            if (same.confidence >= RUNNER_CONFIDENCE && same.directionalScore >= .08) {
                val currentStop = updated.stopPrice ?: updated.entryPrice
                val newStop = if (updated.side == "LONG") max(currentStop, max(updated.entryPrice + atr * .20, ltp - atr * .85)) else min(currentStop, min(updated.entryPrice - atr * .20, ltp + atr * .85))
                val newTarget = if (updated.side == "LONG") ltp + atr * 1.25 else max(.05, ltp - atr * 1.25)
                updated = updated.copy(stopPrice = newStop, targetPrice = newTarget)
                managedDao.updatePosition(updated)
                modifyOcoIfPossible(token, updated)
            } else {
                closeManaged(token, updated, ltp, "TARGET_CHECKPOINT_WEAKENING")
                return
            }
        }
        if (risk.defensive && same.confidence < DEFENSIVE_HOLD_CONFIDENCE) {
            closeManaged(token, updated, ltp, "DEFENSIVE_DAILY_RISK_EXIT")
            return
        }
        if (risk.profitProtect && same.confidence < RUNNER_CONFIDENCE) {
            closeManaged(token, updated, ltp, "PROFIT_HIGH_WATER_PROTECT")
            return
        }
        if (updated.engine == ENGINE_INTRADAY && tradingDecisionsEnabled && opposite.confidence >= PAPER_REVERSAL_CONFIDENCE && opposite.directionalScore >= .12 && same.confidence < .60) {
            closeManaged(token, updated, ltp, "STRATEGY_REVERSAL")
            val refreshed = preferences.settings.first()
            val after = liveRiskState(token, refreshed)
            if (!after.allowNewRisk || !refreshed.liveExecutionEffective) return
            assertNoExternalMisConflict(token, updated.symbol)
            val side = if (updated.side == "LONG") "SHORT" else "LONG"
            val stopPx = if (side == "LONG") ltp - atr * .95 else ltp + atr * .95
            val targetPx = if (side == "LONG") ltp + atr * 1.55 else max(.05, ltp - atr * 1.55)
            val fixedCapitalCap = FixedCapitalPolicy.cap(updated.campaignBudget, updated.capitalDeployed, updated.entryPrice * updated.quantity)
            val allocation = BudgetAllocator.allocate(fixedCapitalCap, opposite.confidence, ltp, abs(ltp - stopPx), abs(targetPx - ltp), 0)
            if (allocation.allowed) openManagedReversal(token, updated, side, allocation.quantity, ltp, stopPx, targetPx, opposite, campaignBudget = fixedCapitalCap)
        }
    }

    private suspend fun openManagedReversal(
        token: String, old: ManagedPositionEntity, side: String, qty: Int, ltp: Double, stop: Double, target: Double, analysis: StrategyEvaluation,
        waveAnchor: Double = old.anchorPrice.takeIf { it > 0.0 } ?: old.entryPrice,
        waveIndex: Int = old.addCount, campaignBudget: Double = old.campaignBudget.takeIf { it > 0.0 } ?: 0.0
    ) {
        val tx = if (side == "LONG") "BUY" else "SELL"
        val ref = stableRef("MIR", "${old.id}-${System.currentTimeMillis()}-$side")
        val order = placeMarket(token, old.symbol, tx, qty, "MIS", ref)
        val filled = order.filledQuantity?.takeIf { it > 0 } ?: qty
        val entry = order.averageFillPrice?.takeIf { it > 0 } ?: ltp
        managedDao.insertPosition(
            ManagedPositionEntity(
                engine = ENGINE_INTRADAY, symbol = old.symbol, product = "MIS", side = side, quantity = filled,
                entryPrice = entry, stopPrice = stop, targetPrice = target, strategy = analysis.strategy,
                regime = analysis.regime, confidence = analysis.confidence, sourceEventId = old.sourceEventId,
                openOrderId = order.growwOrderId, openReferenceId = ref, openedAtMs = System.currentTimeMillis(),
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = System.currentTimeMillis(),
                anchorPrice = waveAnchor, capitalDeployed = entry * filled, campaignBudget = campaignBudget, addCount = waveIndex
            )
        )
        val p = managedDao.openPosition(ENGINE_INTRADAY, old.symbol) ?: return
        val smart = protectManagedPosition(token, p)
        managedDao.updatePosition(p.copy(smartOrderId = smart))
    }

    private suspend fun openShadowDirect(
        symbol: String, side: String, quantity: Int, entry: Double, stop: Double, target: Double,
        strategy: String, regime: String, confidence: Double, sourceEventId: Long?,
        campaignBudget: Double, capitalDeployed: Double
    ) {
        if (quantity <= 0 || shadowDao.openPosition(symbol) != null) return
        val now = System.currentTimeMillis()
        shadowDao.insertPosition(
            ShadowPositionEntity(
                symbol = symbol, side = side, quantity = quantity, entryPrice = entry,
                stopPrice = stop, targetPrice = target, strategy = strategy, regime = regime,
                confidence = confidence, sourceEventId = sourceEventId, openedAtMs = now,
                lastPrice = entry, maxFavourablePrice = entry, maxAdversePrice = entry, lastEvaluatedAtMs = now,
                anchorPrice = entry, capitalDeployed = capitalDeployed, campaignBudget = min(campaignBudget, capitalDeployed), addCount = 0
            )
        )
    }

    private suspend fun openShadow(symbol: String, side: String, quantity: Int, entry: Double, stop: Double, target: Double, analysis: StrategyEvaluation, sourceEventId: Long?, capitalCap: Double) {
        if (quantity <= 0 || shadowDao.openPosition(symbol) != null) return
        val now = System.currentTimeMillis()
        shadowDao.insertPosition(
            ShadowPositionEntity(
                symbol = symbol, side = side, quantity = quantity, entryPrice = entry,
                stopPrice = stop, targetPrice = target, strategy = analysis.strategy,
                regime = analysis.regime, confidence = analysis.confidence, sourceEventId = sourceEventId,
                openedAtMs = now, lastPrice = entry, maxFavourablePrice = entry,
                maxAdversePrice = entry, lastEvaluatedAtMs = now, anchorPrice = entry,
                capitalDeployed = entry * quantity, campaignBudget = min(capitalCap, entry * quantity)
            )
        )
    }

    private suspend fun updateShadowMark(position: ShadowPositionEntity, ltp: Double): ShadowPositionEntity {
        val favourable = if (position.side == "LONG") max(position.maxFavourablePrice, ltp) else min(position.maxFavourablePrice, ltp)
        val adverse = if (position.side == "LONG") min(position.maxAdversePrice, ltp) else max(position.maxAdversePrice, ltp)
        val updated = position.copy(lastPrice = ltp, maxFavourablePrice = favourable, maxAdversePrice = adverse, lastEvaluatedAtMs = System.currentTimeMillis())
        shadowDao.updatePosition(updated)
        return updated
    }

    private suspend fun updateManagedMark(position: ManagedPositionEntity, ltp: Double): ManagedPositionEntity {
        val favourable = if (position.side == "LONG") max(position.maxFavourablePrice, ltp) else min(position.maxFavourablePrice, ltp)
        val adverse = if (position.side == "LONG") min(position.maxAdversePrice, ltp) else max(position.maxAdversePrice, ltp)
        val updated = position.copy(lastPrice = ltp, maxFavourablePrice = favourable, maxAdversePrice = adverse, lastEvaluatedAtMs = System.currentTimeMillis())
        managedDao.updatePosition(updated)
        return updated
    }

    private suspend fun closeShadow(position: ShadowPositionEntity, exit: Double, reason: String): ShadowTradeEntity {
        val pnl = calculatePnl(position.side, position.quantity, position.entryPrice, exit)
        val trade = ShadowTradeEntity(
            symbol = position.symbol, side = position.side, quantity = position.quantity,
            entryPrice = position.entryPrice, exitPrice = exit, grossPnl = pnl.gross,
            estimatedCosts = pnl.costs, netPnl = pnl.net, exitReason = reason,
            strategy = position.strategy, regime = position.regime, confidence = position.confidence,
            mfeRupees = mfe(position.side, position.quantity, position.entryPrice, position.maxFavourablePrice),
            maeRupees = mae(position.side, position.quantity, position.entryPrice, position.maxAdversePrice),
            sourceEventId = position.sourceEventId, openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
        )
        shadowDao.insertTrade(trade)
        shadowDao.deletePosition(position.id)
        return trade
    }

    private suspend fun closeManaged(token: String, position: ManagedPositionEntity, mark: Double, reason: String): ManagedTradeEntity {
        position.smartOrderId?.let { runCatching { apiFactory.groww.cancelSmartOrder(bearer(token), smartOrderId = it) } }
        val tx = if (position.side == "LONG") "SELL" else "BUY"
        val expectedAfter = if (position.product == "MIS") {
            managedDao.openPositionsForProduct(position.symbol, "MIS")
                .filter { it.id != position.id }
                .sumOf { signedQty(it) }
        } else null
        val ref = stableRef(if (position.product == "CNC") "MFX" else "MFC", "${position.id}-${System.currentTimeMillis()}-$reason")
        val order = placeMarket(token, position.symbol, tx, position.quantity, position.product, ref)
        val exit = order.averageFillPrice?.takeIf { it > 0 } ?: mark
        val trade = closeManagedLedger(position, exit, reason, order.growwOrderId)
        if (expectedAfter != null) {
            val broker = waitForMisPositionQty(token, position.symbol, expectedAfter)
            if (broker.quantity != expectedAfter) {
                preferences.setSafetyHalt(true)
                error("MIS reconciliation failed after closing ${position.symbol}: expected app net $expectedAfter, broker net ${broker.quantity}. Live engines halted before any reversal/new order.")
            }
        }
        return trade
    }

    private suspend fun closeManagedLedgerOnly(position: ManagedPositionEntity, exit: Double, reason: String): ManagedTradeEntity {
        return closeManagedLedger(position, exit, reason, "OCO:${position.smartOrderId.orEmpty()}")
    }

    private suspend fun closeManagedLedger(position: ManagedPositionEntity, exit: Double, reason: String, closeOrderId: String): ManagedTradeEntity {
        val pnl = calculatePnl(position.side, position.quantity, position.entryPrice, exit)
        val trade = ManagedTradeEntity(
            engine = position.engine, symbol = position.symbol, product = position.product, side = position.side,
            quantity = position.quantity, entryPrice = position.entryPrice, exitPrice = exit,
            grossPnl = pnl.gross, estimatedCosts = pnl.costs, netPnl = pnl.net, exitReason = reason,
            strategy = position.strategy, regime = position.regime, confidence = position.confidence,
            mfeRupees = mfe(position.side, position.quantity, position.entryPrice, position.maxFavourablePrice),
            maeRupees = mae(position.side, position.quantity, position.entryPrice, position.maxAdversePrice),
            sourceEventId = position.sourceEventId, openOrderId = position.openOrderId, closeOrderId = closeOrderId,
            openedAtMs = position.openedAtMs, closedAtMs = System.currentTimeMillis()
        )
        managedDao.insertTrade(trade)
        managedDao.deletePosition(position.id)
        return trade
    }

    private data class PnlCalc(val gross: Double, val costs: Double, val net: Double)
    private fun calculatePnl(side: String, quantity: Int, entry: Double, exit: Double): PnlCalc {
        val gross = if (side == "LONG") (exit - entry) * quantity else (entry - exit) * quantity
        val entryNotional = entry * quantity
        val exitNotional = exit * quantity
        val brokerage = min(20.0, max(1.0, entryNotional * .001)) + min(20.0, max(1.0, exitNotional * .001))
        val costs = brokerage + (entryNotional + exitNotional) * .00045
        return PnlCalc(gross, costs, gross - costs)
    }

    private fun mfe(side: String, q: Int, entry: Double, favourable: Double): Double = if (side == "LONG") (favourable - entry) * q else (entry - favourable) * q
    private fun mae(side: String, q: Int, entry: Double, adverse: Double): Double = if (side == "LONG") (adverse - entry) * q else (entry - adverse) * q

    private data class Snapshot(
        val summary: DaySummaryDto,
        val positions: List<PositionDto>,
        val recentTrades: List<RecentDecisionDto>
    ) { val totalPnl: Double get() = summary.totalPnl }

    private suspend fun paperSnapshot(): Snapshot {
        val open = shadowDao.openPositions()
        val trades = shadowDao.tradesSince(startOfIndiaDayMs())
        val positions = open.map { p ->
            val pnl = if (p.side == "LONG") (p.lastPrice - p.entryPrice) * p.quantity else (p.entryPrice - p.lastPrice) * p.quantity
            PositionDto(
                symbol = p.symbol, side = p.side, quantity = p.quantity, averagePrice = p.entryPrice,
                ltp = p.lastPrice, pnl = pnl, stopPrice = p.stopPrice, targetPrice = p.targetPrice,
                strategy = "SHADOW ₹2L · ${p.strategy} · ${(p.confidence * 100).toInt()}%"
            )
        }
        return Snapshot(
            summary = DaySummaryDto(
                realisedPnl = trades.sumOf { it.netPnl }, unrealisedPnl = positions.sumOf { it.pnl },
                trades = trades.size, wins = trades.count { it.netPnl > 0 }, losses = trades.count { it.netPnl < 0 },
                grossExposure = positions.sumOf { it.quantity * (it.ltp ?: it.averagePrice) }
            ),
            positions = positions,
            recentTrades = trades.take(6).map { t ->
                RecentDecisionDto(formatEventTime(t.closedAtMs), t.symbol, "PAPER_${t.side}_CLOSED", "${t.exitReason} · net ₹${fmt(t.netPnl)} · costs ₹${fmt(t.estimatedCosts)}", t.strategy, t.quantity)
            }
        )
    }

    private suspend fun managedSnapshot(token: String, engines: Set<String>): Snapshot {
        val allOpen = managedDao.openPositions().filter { it.engine in engines }
        val trades = managedDao.tradesSince(startOfIndiaDayMs()).filter { it.engine in engines }
        val positions = allOpen.map { p ->
            val ltp = runCatching { apiFactory.groww.quote(bearer(token), tradingSymbol = p.symbol).requirePayload("Quote").lastPrice }.getOrNull() ?: p.lastPrice
            val pnl = if (p.side == "LONG") (ltp - p.entryPrice) * p.quantity else (p.entryPrice - ltp) * p.quantity
            PositionDto(
                symbol = p.symbol, side = p.side, quantity = p.quantity, averagePrice = p.entryPrice, ltp = ltp, pnl = pnl,
                stopPrice = p.stopPrice, targetPrice = p.targetPrice,
                strategy = "${p.engine} · APP-OWNED · ${p.strategy}"
            )
        }
        return Snapshot(
            summary = DaySummaryDto(
                realisedPnl = trades.sumOf { it.netPnl }, unrealisedPnl = positions.sumOf { it.pnl }, trades = trades.size,
                wins = trades.count { it.netPnl > 0 }, losses = trades.count { it.netPnl < 0 },
                grossExposure = positions.sumOf { it.quantity * (it.ltp ?: it.averagePrice) }
            ),
            positions = positions,
            recentTrades = trades.take(6).map { t ->
                RecentDecisionDto(formatEventTime(t.closedAtMs), t.symbol, "${t.engine}_${t.side}_CLOSED", "${t.exitReason} · net ₹${fmt(t.netPnl)} · app-owned qty ${t.quantity}", t.strategy, t.quantity)
            }
        )
    }

    private data class RiskState(val totalPnl: Double, val defensive: Boolean, val hardStop: Boolean, val profitProtect: Boolean, val allowNewRisk: Boolean, val reason: String)

    private suspend fun shadowRiskState(token: String, settings: AppSettings): RiskState {
        val s = paperSnapshot()
        val total = s.totalPnl
        preferences.updateShadowPeakPnl(total)
        val peak = preferences.settings.first().shadowPeakPnl
        val hard = total <= -HARD_LOSS_CAP
        val defensive = total <= -SOFT_LOSS_LEVEL
        val profitProtect = peak >= PROFIT_MILESTONE && total <= max(PROFIT_MILESTONE * .70, peak * .70)
        return RiskState(total, defensive, hard, profitProtect, !hard && !defensive && !profitProtect,
            when { hard -> "Shadow hard loss cap reached"; defensive -> "Shadow P&L is below -₹1,500; new risk paused while strong existing positions may be held"; profitProtect -> "Shadow high-water profit protection is active"; else -> "Normal" })
    }

    private suspend fun liveRiskState(token: String, settings: AppSettings): RiskState {
        val live = managedSnapshot(token, setOf(ENGINE_INTRADAY, ENGINE_FAST_TRACK, ENGINE_FAST_SHORT))
        val total = live.totalPnl
        preferences.updateLivePeakPnl(total)
        val peak = preferences.settings.first().livePeakPnl
        val hard = total <= -LIVE_EMERGENCY_TRIGGER
        val defensive = total <= -SOFT_LOSS_LEVEL
        val profitProtect = peak >= PROFIT_MILESTONE && total <= max(PROFIT_MILESTONE * .70, peak * .70)
        return RiskState(total, defensive, hard, profitProtect, !hard && !defensive && !profitProtect,
            when { hard -> "App-only P&L is near the -₹2,500 hard cap; emergency risk reduction active"; defensive -> "App-only P&L is below -₹1,500; no new risk"; profitProtect -> "Profit high-water protection is active"; else -> "Normal" })
    }

    private suspend fun paperRiskAllowsNewTrade(token: String, settings: AppSettings, symbol: String): Boolean {
        val risk = shadowRiskState(token, settings)
        if (!risk.allowNewRisk) return false
        return shadowDao.tradeCountSince(symbol, startOfIndiaDayMs()) < MAX_PAPER_TRADES_PER_SYMBOL
    }

    private suspend fun shadowQualificationDays(): Int {
        val since = LocalDate.now(INDIA).minusDays(20).atStartOfDay(INDIA).toInstant().toEpochMilli()
        val trades = shadowDao.tradesSince(since)
        val totals = trades.groupBy { java.time.Instant.ofEpochMilli(it.closedAtMs).atZone(INDIA).toLocalDate() }
            .mapValues { (_, xs) -> xs.sumOf { it.netPnl } }
        var streak = 0
        val days = totals.keys.sortedDescending()
        for (day in days) {
            if (day.dayOfWeek.value >= 6) continue
            if ((totals[day] ?: 0.0) >= PROFIT_MILESTONE) streak++ else break
            if (streak >= 5) break
        }
        return streak
    }

    private suspend fun placeMarket(token: String, symbol: String, transaction: String, qty: Int, product: String, reference: String): OrderPayload {
        require(qty > 0) { "Order quantity is zero" }
        val auth = bearer(token)
        val response = apiFactory.groww.placeOrder(
            auth,
            OrderCreateRequest(tradingSymbol = symbol, quantity = qty, product = product, transactionType = transaction, orderReferenceId = reference)
        ).requirePayload("Place $transaction $symbol")
        val id = response.growwOrderId
        if (id.isBlank()) error("Groww did not return an order ID")
        repeat(10) {
            delay(450)
            val status = apiFactory.groww.orderStatus(auth, id).requirePayload("Order status")
            if (status.orderStatus.equals("EXECUTED", true) || (status.filledQuantity ?: 0) >= qty) return status.copy(growwOrderId = id, orderReferenceId = reference)
            if (status.orderStatus.equals("REJECTED", true) || status.orderStatus.equals("FAILED", true) || status.orderStatus.equals("CANCELLED", true)) error("Groww order ${status.orderStatus}: ${status.remark.orEmpty()}")
        }
        error("Groww order $id was not confirmed filled; no duplicate retry was attempted")
    }

    private suspend fun protectManagedPosition(token: String, position: ManagedPositionEntity): String {
        val stop = position.stopPrice ?: error("MIS protection requires stop")
        val target = position.targetPrice ?: error("MIS protection requires target")
        val expected = managedDao.openPositionsForProduct(position.symbol, "MIS").sumOf { signedQty(it) }
        val broker = waitForMisPositionQty(token, position.symbol, expected)
        require(broker.quantity == expected) { "External/manual MIS quantity detected for ${position.symbol}; expected app net $expected, broker net ${broker.quantity}" }
        val transaction = if (position.side == "LONG") "SELL" else "BUY"
        try {
            return apiFactory.groww.createOco(
                bearer(token),
                OcoCreateRequest(
                    referenceId = stableRef("MFO", "${position.openOrderId}-${position.symbol}"),
                    tradingSymbol = position.symbol, quantity = position.quantity, netPositionQuantity = broker.quantity,
                    transactionType = transaction, target = OcoLeg(fmt(target), "LIMIT", fmt(target)),
                    stopLoss = OcoLeg(fmt(stop), "SL_M", null)
                )
            ).requirePayload("Create OCO protection").smartOrderId.also { require(it.isNotBlank()) { "Groww did not return OCO id" } }
        } catch (t: Throwable) {
            val tx = if (position.side == "LONG") "SELL" else "BUY"
            val emergency = runCatching { placeMarket(token, position.symbol, tx, position.quantity, "MIS", stableRef("MFE", position.openOrderId)) }.getOrNull()
            if (emergency != null) closeManagedLedger(position, emergency.averageFillPrice ?: position.lastPrice, "PROTECTION_FAILED_EMERGENCY_FLATTEN", emergency.growwOrderId)
            preferences.setSafetyHalt(true)
            error("Protection failed after fill; emergency flatten requested and all live engines halted: ${t.message}")
        }
    }

    private suspend fun modifyOcoIfPossible(token: String, position: ManagedPositionEntity) {
        val id = position.smartOrderId ?: return
        val stop = position.stopPrice ?: return
        val target = position.targetPrice ?: return
        runCatching {
            apiFactory.groww.modifyOco(
                bearer(token), id,
                OcoModifyRequest(quantity = position.quantity, target = OcoModifyLeg(fmt(target)), stopLoss = OcoModifyLeg(fmt(stop)))
            ).requirePayload("Modify OCO")
        }
    }

    private suspend fun assertNoExternalMisConflict(token: String, symbol: String) {
        val broker = currentPosition(token, symbol, "MIS").quantity
        val app = managedDao.openPositionsForProduct(symbol, "MIS").sumOf { signedQty(it) }
        require(broker == app) { "EXTERNAL POSITION DETECTED for $symbol MIS: broker net $broker vs app-owned net $app. New app orders are blocked." }
    }

    private suspend fun assertManagedMisStillOwned(token: String, position: ManagedPositionEntity) {
        if (position.product != "MIS") return
        val broker = currentPosition(token, position.symbol, "MIS").quantity
        val app = managedDao.openPositionsForProduct(position.symbol, "MIS").sumOf { signedQty(it) }
        if (broker != app) {
            preferences.setSafetyHalt(true)
            error("EXTERNAL POSITION DETECTED for ${position.symbol}: broker MIS net $broker vs app-owned net $app. Live engines halted to avoid touching manual trades.")
        }
    }

    private fun signedQty(p: ManagedPositionEntity): Int = if (p.side == "LONG") p.quantity else -p.quantity

    private suspend fun waitForMisPositionQty(token: String, symbol: String, expected: Int): GrowwPosition {
        var latest = currentPosition(token, symbol, "MIS")
        if (latest.quantity == expected) return latest
        repeat(9) {
            delay(400)
            latest = currentPosition(token, symbol, "MIS")
            if (latest.quantity == expected) return latest
        }
        return latest
    }

    private suspend fun currentPosition(token: String, symbol: String, product: String): GrowwPosition {
        return apiFactory.groww.positions(bearer(token)).requirePayload("Groww positions")
            .positions.firstOrNull { it.tradingSymbol.equals(symbol, true) && it.product.equals(product, true) }
            ?: GrowwPosition(tradingSymbol = symbol, quantity = 0, product = product)
    }

    private suspend fun ensureToken(): String? {
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

    private fun preLiveGuard(settings: AppSettings) {
        require(settings.liveExecutionEffective) { "Live execution is OFF" }
        require(settings.brokerAuthenticated) { "Groww is not authenticated" }
        require(settings.staticIpMatched) { "Static IP verification failed" }
        require(!settings.safetyHalt) { "Safety halt is active" }
        require(marketSession() == "OPEN") { "NSE regular market session is not open" }
    }

    private fun disconnectedDashboard(settings: AppSettings) = DashboardDto(
        serviceStatus = "device", mode = if (settings.liveExecutionEffective) "live" else "paper",
        armed = settings.liveExecutionEffective, halted = settings.safetyHalt, marketSession = marketSession(),
        asOf = ZonedDateTime.now(INDIA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        broker = BrokerStatusDto(configured = hasBrokerCredentials(), connected = false, name = "Groww", detail = "Refresh & Authenticate required"),
        risk = riskStatus(settings, 0.0, if (settings.liveExecutionEffective) settings.livePeakPnl else settings.shadowPeakPnl), summary = DaySummaryDto(), fastTrackSummary = DaySummaryDto(),
        combinedAppPnl = 0.0, shadowQualificationDays = 0, positions = emptyList(), recentDecisions = emptyList()
    )

    private fun riskStatus(s: AppSettings, currentPnl: Double, peakPnl: Double) = RiskStatusDto(
        maxDailyLoss = HARD_LOSS_CAP,
        softDailyLoss = SOFT_LOSS_LEVEL,
        profitMilestone = PROFIT_MILESTONE,
        riskPerTrade = max(150.0, s.dailyBudgetRupees * .005),
        maxExposure = s.dailyBudgetRupees.toDouble(),
        riskMode = when {
            currentPnl <= -LIVE_EMERGENCY_TRIGGER -> "HARD STOP"
            currentPnl <= -SOFT_LOSS_LEVEL -> "DEFENSIVE"
            peakPnl >= PROFIT_MILESTONE && currentPnl <= peakPnl * .70 -> "PROFIT PROTECT"
            currentPnl >= PROFIT_MILESTONE -> "MILESTONE+"
            else -> "NORMAL"
        },
        dailyPeakPnl = peakPnl
    )

    private fun marketSession(): String {
        val now = ZonedDateTime.now(INDIA)
        if (now.dayOfWeek.value >= 6) return "CLOSED"
        val t = now.toLocalTime()
        return if (!t.isBefore(LocalTime.of(9, 15)) && t.isBefore(LocalTime.of(15, 25))) "OPEN" else "CLOSED"
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
        return "Groww TOTP authentication failed (HTTP ${e.code()}): $detail. Verify the TOTP token and TOTP secret, and keep Automatic date & time enabled."
    }

    private fun startOfIndiaDayMs(): Long = LocalDate.now(INDIA).atStartOfDay(INDIA).toInstant().toEpochMilli()
    private fun bearer(token: String) = "Bearer $token"
    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun stableRef(prefix: String, seed: String): String = (prefix + sha256(seed).take(14)).take(18)
    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun pct(v: Double): String = String.format(Locale.US, "%.0f%%", v * 100)
    private fun normalizeIp(v: String) = v.trim().lowercase(Locale.US)
    private fun isValidIp(v: String): Boolean = v.trim().matches(Regex("^[0-9a-fA-F:.]+$")) && v.length in 3..45
    private fun formatEventTime(ms: Long): String = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ms))

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private val HIST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val MIN_BUDGET = 10_000L
        private const val MAX_BUDGET = 200_000L
        private const val SHADOW_BUDGET = 200_000.0
        private const val PROFIT_MILESTONE = 5_000.0
        private const val SOFT_LOSS_LEVEL = 1_500.0
        private const val HARD_LOSS_CAP = 2_500.0
        // Live flattening starts before the hard cap because real fills can slip.
        private const val LIVE_EMERGENCY_TRIGGER = 2_250.0
        private const val PAPER_REVERSAL_CONFIDENCE = 0.76
        private const val RUNNER_CONFIDENCE = 0.72
        private const val DEFENSIVE_HOLD_CONFIDENCE = 0.80
        private const val MULTIFY_SELL_SHORT_GATE = 0.66
        private const val MAX_PAPER_TRADES_PER_SYMBOL = 10
        private const val DEFAULT_UNTRAINED_WAVE_ARM_PCT = 0.40
        private const val WAVE_PIVOT_FRACTION = 0.004
        private const val MANUAL_SOURCE = "com.multify.traderpro.manual"
        // Supplied workbook (2026-06-30 through 2026-09-30 BUY calls): entry-to-target mean = 3.68097%.
        private const val UPLOADED_THREE_MONTH_TARGET_PCT = 3.680970873786408
        // Rolling newest 30 recommendation trading dates after the appended Oct 1/Oct 5 records.
        private const val DEFAULT_LONG_TARGET_PCT = 2.983877551020408
        private const val HISTORICAL_SHORT_BACKFILL_INTERVAL_MS = 30L * 60L * 1000L
        private const val HISTORICAL_SHORT_BACKFILL_REQUEST_DELAY_MS = 350L
        private const val MAX_HISTORICAL_SHORT_BACKFILLS_PER_PASS = 8
        private const val FAST_SHORT_STOP_PCT = 0.0035
        const val ENGINE_INTRADAY = "INTRADAY"
        const val ENGINE_FAST_TRACK = "FAST_TRACK"
        const val ENGINE_FAST_SHORT = "FAST_SHORT"
        private const val REGIME_RECOVERY_HOLD = "RECOVERY_HOLD_NEW_CALL"
        private const val REGIME_SECONDARY_PREVIEW = "SECONDARY_PREDICTION_ONLY"
    }
}
