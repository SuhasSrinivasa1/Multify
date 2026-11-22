package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.multify.traderpro.data.local.NseEodBarDao
import com.multify.traderpro.data.local.NseEodBarEntity
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.LargeCapIntradayInput
import com.multify.traderpro.engine.LargeCapIntradaySignal
import com.multify.traderpro.engine.LargeCapMomentumPolicy
import com.multify.traderpro.engine.LargeCapSwingInput
import com.multify.traderpro.engine.LargeCapSwingSignal
import com.multify.traderpro.engine.NseEquitySessionCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class SixMonthLowRow(
    val symbol: String,
    val sector: String,
    val lastClose: Double,
    val sixMonthLow: Double,
    val distancePct: Double,
    val lowDate: String,
    val dataDate: String,
    val historySessions: Int,
    val corporateActionReview: Boolean
)

data class SixMonthLowStatus(
    val state: String = "NOT_INITIALIZED",
    val updatedAtMs: Long = 0L,
    val latestTradeDate: String = "",
    val historySessions: Int = 0,
    val symbolsStored: Int = 0,
    val lastError: String = ""
)

@Singleton
class LargeCapResearchRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val barsDao: NseEodBarDao,
    private val nseSymbols: NseSymbolRepository,
    private val nifty100: Nifty100UniverseRepository,
    private val audit: AuditLogger
) {
    private val prefs = context.getSharedPreferences("market_research_v9033", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun sixMonthLowStatus(): SixMonthLowStatus = SixMonthLowStatus(
        state = prefs.getString(KEY_STATE, "NOT_INITIALIZED").orEmpty(),
        updatedAtMs = prefs.getLong(KEY_UPDATED, 0L),
        latestTradeDate = prefs.getString(KEY_LATEST_DATE, "").orEmpty(),
        historySessions = prefs.getInt(KEY_HISTORY_SESSIONS, 0),
        symbolsStored = prefs.getInt(KEY_SYMBOL_COUNT, 0),
        lastError = prefs.getString(KEY_ERROR, "").orEmpty()
    )

    /**
     * Called only by the after-hours WorkManager job. It reads one NSE market-wide
     * UDiFF bhavcopy archive per trading date; it does not call Groww or fan out
     * historical requests per symbol.
     */
    suspend fun refreshAfterHours(): SixMonthLowStatus {
        val now = ZonedDateTime.now(INDIA)
        require(now.toLocalTime() >= AFTER_HOURS_START && !NseEquitySessionCalendar.isOpen(now)) {
            "EOD research is restricted to after-market hours"
        }
        require(now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY) {
            "NSE EOD refresh skipped on weekend"
        }

        nifty100.refreshSafely()
        var dates = barsDao.recentTradeDates(TRADING_SESSIONS)
        if (dates.size < TRADING_SESSIONS) {
            var cursor = now.toLocalDate()
            val oldest = now.toLocalDate().minusDays(BACKFILL_CALENDAR_DAYS)
            var attempts = 0
            while (!cursor.isBefore(oldest) && dates.size < TRADING_SESSIONS && attempts < MAX_BACKFILL_DAYS) {
                if (cursor.dayOfWeek != DayOfWeek.SATURDAY && cursor.dayOfWeek != DayOfWeek.SUNDAY &&
                    !barsDao.hasTradeDate(cursor.toString())) {
                    val dayBars = nseSymbols.downloadEodBhavcopy(cursor)
                    if (dayBars != null) {
                        barsDao.upsertBars(dayBars.map { it.toEntity() })
                        dates = (dates + cursor.toString()).distinct().sortedDescending().take(TRADING_SESSIONS)
                    } else if (cursor == now.toLocalDate()) {
                        throw IOException("Today's NSE final bhavcopy is not published yet")
                    }
                    attempts++
                    delay(350L)
                }
                cursor = cursor.minusDays(1)
            }
        }

        // Incremental catch-up handles an app that was offline for one or more sessions.
        var latest = barsDao.latestTradeDate()?.let { LocalDate.parse(it) }
        if (latest != null && latest.isBefore(now.toLocalDate())) {
            var cursor = latest.plusDays(1)
            while (!cursor.isAfter(now.toLocalDate())) {
                if (cursor.dayOfWeek != DayOfWeek.SATURDAY && cursor.dayOfWeek != DayOfWeek.SUNDAY &&
                    !barsDao.hasTradeDate(cursor.toString())) {
                    val dayBars = nseSymbols.downloadEodBhavcopy(cursor)
                    if (dayBars != null) {
                        barsDao.upsertBars(dayBars.map { it.toEntity() })
                    } else if (cursor == now.toLocalDate()) {
                        throw IOException("Today's NSE final bhavcopy is not published yet")
                    }
                    delay(350L)
                }
                cursor = cursor.plusDays(1)
            }
        }

        dates = barsDao.recentTradeDates(TRADING_SESSIONS)
        val latestDate = dates.firstOrNull().orEmpty()
        val cutoff = dates.lastOrNull()
        if (cutoff != null) barsDao.deleteBefore(cutoff)
        val allBars = if (cutoff != null) barsDao.barsSince(cutoff) else emptyList()
        val state = when {
            dates.size >= TRADING_SESSIONS -> "READY"
            dates.size >= MIN_READY_SESSIONS -> "PARTIAL_HISTORY"
            else -> "HISTORY_INCOMPLETE"
        }
        val symbolCount = allBars.map { it.symbol }.distinct().size
        val nowMs = System.currentTimeMillis()
        check(prefs.edit()
            .putString(KEY_STATE, state)
            .putLong(KEY_UPDATED, nowMs)
            .putString(KEY_LATEST_DATE, latestDate)
            .putInt(KEY_HISTORY_SESSIONS, dates.size)
            .putInt(KEY_SYMBOL_COUNT, symbolCount)
            .putString(KEY_ERROR, "")
            .commit()) { "Unable to persist six-month-low research status" }
        audit.logAsync("SIX_MONTH_LOW", "NSE_EOD_REFRESH_COMPLETED", mapOf(
            "state" to state,
            "latest_trade_date" to latestDate,
            "rolling_market_sessions" to dates.size,
            "symbols_with_eod_bars" to symbolCount,
            "rows_stored" to allBars.size,
            "source" to "NSE_CM_UDIFF_FINAL_BHAVCOPY",
            "groww_historical_calls" to 0,
            "groww_live_data_calls" to 0,
            "market_hours_network_calls" to 0,
            "corporate_action_prices_adjusted" to false
        ))
        return sixMonthLowStatus()
    }

    fun latestIntradaySignals(): List<LargeCapIntradaySignal> {
        val raw = prefs.getString(KEY_INTRADAY_JSON, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        val type = object : TypeToken<List<LargeCapIntradaySignal>>() {}.type
        return runCatching { gson.fromJson<List<LargeCapIntradaySignal>>(raw, type).orEmpty() }
            .getOrDefault(emptyList())
    }

    /** Called only after a complete existing Fixed full-market LTP sweep; adds zero broker calls. */
    fun recordIntradaySnapshot(
        date: String,
        currentLtps: Map<String, Double>,
        previousLtps: Map<String, Double>,
        sessionOpenLtps: Map<String, Double>
    ) {
        if (currentLtps.isEmpty() || nifty100.constituents().isEmpty()) return
        val current = currentLtps.mapKeys { it.key.uppercase() }
        val previous = previousLtps.mapKeys { it.key.uppercase() }
        val opens = sessionOpenLtps.mapKeys { it.key.uppercase() }
        val inputs = nifty100.constituents().mapNotNull { member ->
            val price = current[member.symbol] ?: return@mapNotNull null
            val first = opens[member.symbol] ?: return@mapNotNull null
            val prior = previous[member.symbol] ?: return@mapNotNull null
            LargeCapIntradayInput(
                member.symbol, member.companyName, member.sector,
                price, first, prior
            )
        }
        if (inputs.size < 60) {
            audit.logAsync("LARGE_CAP", "INTRADAY_SNAPSHOT_INCOMPLETE", mapOf(
                "date" to date, "valid_constituents" to inputs.size,
                "expected_constituents" to nifty100.constituents().size,
                "previous_snapshot_preserved" to true
            ))
            return
        }
        val now = System.currentTimeMillis()
        val rows = LargeCapMomentumPolicy.intraday(inputs, now)
        check(prefs.edit()
            .putString(KEY_INTRADAY_JSON, gson.toJson(rows))
            .putString(KEY_INTRADAY_DATE, date)
            .putLong(KEY_INTRADAY_AT, now)
            .commit()) { "Unable to persist Large Cap intraday snapshot" }
        audit.logAsync("LARGE_CAP", "INTRADAY_SNAPSHOT_FROM_FIXED_DISCOVERY", mapOf(
            "date" to date, "constituents_observed" to rows.size,
            "source" to "EXISTING_FIXED_FULL_NSE_LTP_SWEEP",
            "additional_groww_requests" to 0,
            "fixed_scoring_or_selection_changed" to false
        ))
    }

    suspend fun sixMonthLowWatchlist(): List<SixMonthLowRow> {
        val dates = barsDao.recentTradeDates(TRADING_SESSIONS)
        if (dates.size < MIN_READY_SESSIONS) return emptyList()
        val cutoff = dates.last()
        val sectorBySymbol = nifty100.constituents().associate { it.symbol to it.sector }
        val grouped = barsDao.barsSince(cutoff).groupBy { it.symbol }
        return grouped.mapNotNull { (symbol, rows) ->
            val ordered = rows.sortedBy { it.tradeDate }
            if (ordered.size < MIN_SYMBOL_SESSIONS) return@mapNotNull null
            val last = ordered.last()
            val low = ordered.minByOrNull { it.low } ?: return@mapNotNull null
            val distance = LargeCapMomentumPolicy.pct(last.close, low.low)
            if (!distance.isFinite() || distance < -0.01 || distance > MAX_DISTANCE_FROM_LOW_PCT) {
                return@mapNotNull null
            }
            SixMonthLowRow(
                symbol = symbol,
                sector = sectorBySymbol[symbol] ?: "OTHER NSE EQ",
                lastClose = last.close,
                sixMonthLow = low.low,
                distancePct = max(0.0, distance),
                lowDate = low.tradeDate,
                dataDate = last.tradeDate,
                historySessions = ordered.size,
                corporateActionReview = hasPossibleCorporateAction(ordered.map { it.close })
            )
        }.sortedWith(compareBy<SixMonthLowRow> { it.distancePct }.thenBy { it.symbol })
    }

    suspend fun largeCapSwingSignals(): List<LargeCapSwingSignal> {
        val dates = barsDao.recentTradeDates(TRADING_SESSIONS)
        if (dates.size < MIN_READY_SESSIONS) return emptyList()
        val cutoff = dates.last()
        val rowsBySymbol = barsDao.barsSince(cutoff).groupBy { it.symbol }
        return nifty100.constituents().mapNotNull { member ->
            val rows = rowsBySymbol[member.symbol]?.sortedBy { it.tradeDate } ?: return@mapNotNull null
            if (rows.size < MIN_SYMBOL_SESSIONS) return@mapNotNull null
            LargeCapMomentumPolicy.swing(
                LargeCapSwingInput(
                    symbol = member.symbol,
                    companyName = member.companyName,
                    sector = member.sector,
                    closes = rows.map { it.close },
                    highs = rows.map { it.high },
                    lows = rows.map { it.low },
                    volumes = rows.map { it.volume },
                    lastDate = rows.last().tradeDate
                )
            )
        }.sortedWith(compareBy<LargeCapSwingSignal> { stageRank(it.stage) }
            .thenByDescending { it.twentyDayReturnPct }
            .thenByDescending { it.fiveDayReturnPct })
    }

    private fun stageRank(stage: String): Int = when (stage) {
        "BREAKOUT" -> 0
        "EARLY MOMENTUM" -> 1
        "CONTINUATION" -> 2
        "PULLBACK" -> 3
        "EXHAUSTION" -> 4
        else -> 5
    }

    private fun hasPossibleCorporateAction(closes: List<Double>): Boolean {
        return closes.zipWithNext().any { (a, b) ->
            if (a <= 0.0 || b <= 0.0) false
            else {
                val ratio = b / a
                ratio < 0.67 || ratio > 1.50
            }
        }
    }

    private fun com.multify.traderpro.engine.NseBhavcopyParser.Bar.toEntity() =
        NseEodBarEntity(symbol, tradeDate, open, high, low, close, volume)

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private val AFTER_HOURS_START = LocalTime.of(16, 0)
        private const val TRADING_SESSIONS = 126
        private const val MIN_READY_SESSIONS = 100
        private const val MIN_SYMBOL_SESSIONS = 100
        private const val BACKFILL_CALENDAR_DAYS = 240L
        private const val MAX_BACKFILL_DAYS = 180
        private const val MAX_DISTANCE_FROM_LOW_PCT = 10.0
        private const val KEY_STATE = "six_month_low_state"
        private const val KEY_UPDATED = "six_month_low_updated_at"
        private const val KEY_LATEST_DATE = "six_month_low_latest_date"
        private const val KEY_HISTORY_SESSIONS = "six_month_low_history_sessions"
        private const val KEY_SYMBOL_COUNT = "six_month_low_symbol_count"
        private const val KEY_ERROR = "six_month_low_error"
        private const val KEY_INTRADAY_JSON = "large_cap_intraday_json"
        private const val KEY_INTRADAY_DATE = "large_cap_intraday_date"
        private const val KEY_INTRADAY_AT = "large_cap_intraday_at"
    }
}
