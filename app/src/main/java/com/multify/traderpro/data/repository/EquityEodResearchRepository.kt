package com.multify.traderpro.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.EquityEodBar
import com.multify.traderpro.engine.AdaptiveLowPatternPolicy
import com.multify.traderpro.engine.AdaptiveLowCandidate
import com.multify.traderpro.engine.LargeCapEquityObservation
import com.multify.traderpro.engine.LargeCapEquityResearchPolicy
import com.multify.traderpro.engine.NseEquitySessionCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class EquityLowObservation(
    val symbol: String,
    val close: Double,
    val periodLow: Double,
    val distancePct: Double,
    val tradingDays: Int,
    val latestDate: LocalDate,
    val periodLowDate: LocalDate,
    val oneMonthLow: Double,
    val oneMonthDistancePct: Double,
    val lookbackMonths: Int,
    val marketCapCategory: String = "UNCLASSIFIED",
    val marketCapRank: Int? = null,
    val peRatio: Double? = null,
    val industry: String? = null,
    val verifiedIndustryPeMinimum: Boolean = false
)
data class EquityResearchSnapshot(
    val threeMonthLowRows: List<EquityLowObservation> = emptyList(),
    val sixMonthLowRows: List<EquityLowObservation> = emptyList(),
    val largeCaps: List<LargeCapEquityObservation> = emptyList(),
    val completedDays: Int = 0,
    val firstDay: String = "",
    val threeMonthCompletedDays: Int = 0,
    val threeMonthFirstDay: String = "",
    val latestDay: String = "",
    val universeCount: Int = 0,
    val threeMonthHistoryComplete: Boolean = false,
    val sixMonthHistoryComplete: Boolean = false,
    val lastError: String = "",
    val memberSource: String = "",
    val currentPulseAtMs: Long = 0L,
    val refreshAtMs: Long = 0L,
    val marketCapClassificationPeriod: String = "",
    val marketCapSource: String = "",
    val marketCapClassificationCount: Int = 0,
    val officialPeDate: String = "",
    val officialPeSymbols: Int = 0,
    val industryClassifiedSymbols: Int = 0,
    val fullIndustryPeCoverage: Boolean = false,
    val adaptiveLowThreeRows: List<AdaptiveLowCandidate> = emptyList(),
    val adaptiveLowSixRows: List<AdaptiveLowCandidate> = emptyList(),
    val adaptiveLowThreeRun: AdaptiveLowRunState? = null,
    val adaptiveLowSixRun: AdaptiveLowRunState? = null,
    val adaptiveLowThreeLearning: List<AdaptiveLowResearchSummary> = emptyList(),
    val adaptiveLowSixLearning: List<AdaptiveLowResearchSummary> = emptyList(),
    val largeWatchEvidence: Map<String,LargeWatchEvidence> = emptyMap(),
    val largeWatchLearning: List<LargeWatchLearningSummary> = emptyList(),
    val niftyContextDay: String = "",
    val niftyEodMovePct: Double? = null,
    val niftyPreviousEodMovePct: Double? = null,
    val niftyFiveDayMovePct: Double? = null,
    val niftyEodRegime: String = "UNKNOWN",
    val niftyIndexPe: Double? = null,
    val niftyIndexPb: Double? = null,
    val liveLargeCapBreadthProxyPct: Double? = null
)
data class EquityEodRefreshProgress(
    val importedDays: Int,
    val missingWeekdays: Int,
    val sourceFailure: Boolean,
    val requestedDays: Int = 0
)

/**
 * NSE cash equity research ONLY. Uses official daily CM-UDiFF bhavcopy archives (ONE
 * public file per session for all EQ symbols), never Groww historical/quote/order APIs.
 * All remote calls are denied outside 18:30–08:00 IST. An unverified partial archive
 * cannot replace a previously valid trading-day snapshot.
 *
 * Separate SQLite database avoids changing the frozen trading Room schema.
 */
@Singleton
class EquityEodResearchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val symbols: NseSymbolRepository,
    private val audit: AuditLogger
) {
    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private val BASIC = DateTimeFormatter.BASIC_ISO_DATE
        private val SYMBOL_REGEX = Regex("[A-Z0-9&._-]{1,32}")
        private const val MAX_ARCHIVE_ROWS = 12000
        private const val MIN_VALID_EQ_ROWS = 1500
        private const val MAX_BARS_PER_SYMBOL = 200
        private const val SOURCE_URL = "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_%s_F_0000.csv.zip"
        private const val NIFTY100_URL = "https://www.niftyindices.com/IndexConstituent/ind_nifty100list.csv"
        private const val NSE_INDICES_EOD_URL = "https://nsearchives.nseindia.com/content/indices/ind_close_all_%s.csv"
        // Official NSE daily per-stock Symbol P/E (NOT Nifty index P/E).
        private const val NSE_PE_URL = "https://nsearchives.nseindia.com/content/equities/peDetail/PE_%s.csv"
        private const val NSE_TOTAL_MARKET_INDUSTRIES_URL = "https://nsearchives.nseindia.com/content/indices/ind_niftytotalmarket_list.csv"
        private const val NIFTYBANK_URL = "https://www.niftyindices.com/IndexConstituent/ind_niftybanklist.csv"
        private const val NIFTYIT_URL = "https://www.niftyindices.com/IndexConstituent/ind_niftyitlist.csv"
        private const val EOD_MARKET_CLOSE = 18 // first availability attempt at 18:30 IST
        private val SEEDS = mapOf(
            "HDFCBANK" to "BANK", "SBIN" to "BANK", "ICICIBANK" to "BANK",
            "AXISBANK" to "BANK", "KOTAKBANK" to "BANK", "BANKBARODA" to "BANK",
            "INDUSINDBK" to "BANK", "PNB" to "BANK",
            "TCS" to "IT", "INFY" to "IT", "HCLTECH" to "IT",
            "WIPRO" to "IT", "TECHM" to "IT", "LTIM" to "IT",
            "RELIANCE" to "ENERGY", "ONGC" to "ENERGY", "BPCL" to "ENERGY",
            "LT" to "INDUSTRIAL", "ADANIPORTS" to "INDUSTRIAL",
            "BHARTIARTL" to "TELECOM", "SUNPHARMA" to "PHARMA",
            "DRREDDY" to "PHARMA", "MARUTI" to "AUTO", "M&M" to "AUTO"
        )
        fun afterHours(at: ZonedDateTime): Boolean {
            val ist = at.withZoneSameInstant(INDIA).toLocalTime()
            return ist >= LocalTime.of(18, 30) || ist < LocalTime.of(8, 0)
        }
        fun latestEligibleDate(at: ZonedDateTime): LocalDate {
            val ist = at.withZoneSameInstant(INDIA)
            return if (ist.toLocalTime() >= LocalTime.of(18, 30)) ist.toLocalDate()
                else ist.toLocalDate().minusDays(1)
        }
        /** Three-month research unlocks only with a meaningful date span and 55 validated sessions. */
        fun threeMonthCoverageComplete(totalSessions: Int, firstSession: LocalDate?, asOf: LocalDate): Boolean =
            totalSessions >= 55 && firstSession != null &&
                !firstSession.isAfter(asOf.minusMonths(3).plusDays(14))
        /** At least 85% of observed NSE sessions, with a 50-session per-symbol floor. */
        fun minimumThreeMonthSymbolSessions(totalSessions: Int): Int =
            kotlin.math.ceil(totalSessions * 0.85).toInt().coerceAtLeast(50)
        /** Signed distance of the latest close from a historical reference low. */
        fun distanceFromReferenceLowPct(close: Double, referenceLow: Double): Double =
            if (close.isFinite() && close > 0.0 && referenceLow.isFinite() && referenceLow > 0.0)
                (close / referenceLow - 1.0) * 100.0 else Double.NaN
        /** Include every breakdown and every close no more than 10% above the prior low. */
        fun isWithinTenPctAboveOrBelow(distancePct: Double): Boolean =
            distancePct.isFinite() && distancePct <= 10.000001
        fun csvCells(line: String): List<String> {
            val out = mutableListOf<String>()
            val b = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && quoted && i+1 < line.length && line[i+1] == '"' -> {
                        b.append('"'); i++
                    }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { out += b.toString().trim(); b.setLength(0) }
                    else -> b.append(c)
                }
                i++
            }
            out += b.toString().trim()
            return out
        }
    }

    private class Store(context: Context) : SQLiteOpenHelper(
        context, "suhas_shree_eod_research_v1.db", null, 2
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE eod(day TEXT NOT NULL, symbol TEXT NOT NULL, low REAL NOT NULL, close REAL NOT NULL, volume INTEGER NOT NULL, open REAL, high REAL, PRIMARY KEY(day,symbol))")
            db.execSQL("CREATE INDEX idx_eod_symbol_day ON eod(symbol,day)")
            db.execSQL("CREATE TABLE eod_days(day TEXT PRIMARY KEY, symbols INTEGER NOT NULL, updated_ms INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE attempts(day TEXT PRIMARY KEY, attempt_day TEXT NOT NULL, failure TEXT NOT NULL)")
            db.execSQL("CREATE TABLE members(symbol TEXT PRIMARY KEY, sector TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Preserve all existing 3M/6M history, membership and broker-independent
            // research state. Old rows retain NULL for OHLC until after-hours backfill.
            if(oldVersion==1 && newVersion==2) {
                db.execSQL("ALTER TABLE eod ADD COLUMN open REAL")
                db.execSQL("ALTER TABLE eod ADD COLUMN high REAL")
            } else error("Unsupported EOD research database upgrade $oldVersion to $newVersion")
        }
    }

    private val db = Store(context)
    private val valuationStore = EquityLowValuationStore(context)
    private val largeWatchStore = LargeWatchLearningStore(context)
    private val adaptiveLowStore = AdaptiveLowResearchStore(context)
    private var lastUniverseCapturedDay = ""
    private val prefs = context.getSharedPreferences("suhas_shree_eod_state", Context.MODE_PRIVATE)
    // AMFI classification is bundled as a build-time asset. No runtime market-cap
    // requests are added, and missing/unmatched symbols remain explicitly unclassified.
    private val amfiMarketCapDataset: AmfiMarketCapDataset by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            context.assets.open("amfi_market_cap_categories.csv").bufferedReader().use {
                AmfiMarketCapClassificationParser.parse(it.readText())
            }
        }.getOrElse { cause ->
            audit.logAsync("EOD_RESEARCH", "AMFI_MARKET_CAP_ASSET_UNAVAILABLE", mapOf(
                "error" to (cause.message ?: cause.javaClass.simpleName).take(100),
                "classification_guessed" to false
            ))
            AmfiMarketCapDataset.EMPTY
        }
    }
    private val cookieLock = Any()
    private val cookies = mutableListOf<Cookie>()
    @Volatile private var warmedNseDate = ""
    private val client = OkHttpClient.Builder()
        .cookieJar(object: CookieJar {
            override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
                synchronized(cookieLock) {
                    for (cookie in incoming) {
                        cookies.removeAll { it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path }
                        cookies.add(cookie)
                    }
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookieLock) {
                cookies.removeAll { it.expiresAt<=System.currentTimeMillis() }
                cookies.filter { it.matches(url) }
            }
        })
        .connectTimeout(Duration.ofSeconds(8))
        .readTimeout(Duration.ofSeconds(18))
        .followRedirects(true)
        .build()
    private val lock = Any()
    private val refreshMutex = Mutex()

    private fun request(url: String): Request = Request.Builder().url(url)
        .header("Accept", "text/csv,application/zip,application/octet-stream,*/*")
        .header("Referer", if(url.contains("niftyindices.com")) "https://www.niftyindices.com/" else "https://www.nseindia.com/")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
        .build()

    private fun warmNseSession() {
        requireAfterHours()
        val today=LocalDate.now(INDIA).toString()
        if(warmedNseDate==today) return
        warmedNseDate=today
        runCatching {
            client.newCall(request("https://www.nseindia.com/")).execute().use { response ->
                check(response.isSuccessful) { "NSE_HOME_HTTP_${response.code}" }
            }
        }.onFailure { audit.logAsync("EOD_RESEARCH","NSE_COOKIE_PREWARM_UNAVAILABLE",mapOf(
            "reason" to (it.message ?: it.javaClass.simpleName).take(80),
            "archives_still_attempted" to true, "groww_requests" to 0
        )) }
    }

    private fun requireAfterHours() = check(afterHours(ZonedDateTime.now(INDIA))) {
        "EOD research network fetch is forbidden during NSE market or pre-close hours"
    }

    private fun importArchive(date: LocalDate): Int {
        requireAfterHours()
        val url = SOURCE_URL.format(date.format(BASIC))
        val rows = client.newCall(request(url)).execute().use { response ->
            if (!response.isSuccessful) error("NSE_BHAVCOPY_HTTP_${response.code}")
            val body = response.body ?: error("NSE_BHAVCOPY_EMPTY")
            val zip = ZipInputStream(body.byteStream())
            zip.use { stream ->
                val entry = stream.nextEntry ?: error("NSE_BHAVCOPY_ZIP_EMPTY")
                check(entry.name.endsWith(".csv", true) && !entry.name.contains("..")) {
                    "NSE_BHAVCOPY_ZIP_ENTRY_INVALID"
                }
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                val headings = csvCells(reader.readLine() ?: error("NSE_BHAVCOPY_HEADER_EMPTY"))
                    .map { it.removePrefix("\uFEFF").trim() }
                val indices = listOf("TckrSymb", "SctySrs", "OpnPric", "HghPric", "LwPric", "ClsPric", "TtlTradgVol")
                    .associateWith { field ->
                        headings.indexOf(field).takeIf { it >= 0 }
                            ?: error("NSE_BHAVCOPY_MISSING_COLUMN_$field")
                    }
                val sym = indices.getValue("TckrSymb")
                val series = indices.getValue("SctySrs")
                val open = indices.getValue("OpnPric")
                val high = indices.getValue("HghPric")
                val low = indices.getValue("LwPric")
                val close = indices.getValue("ClsPric")
                val vol = indices.getValue("TtlTradgVol")
                val map = linkedMapOf<String, EquityEodBar>()
                var read = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    read++
                    check(read <= MAX_ARCHIVE_ROWS) { "NSE_BHAVCOPY_TOO_MANY_ROWS" }
                    val values = csvCells(line)
                    if (values.size <= maxOf(sym,series,open,high,low,close,vol)) continue
                    if (values[series] != "EQ") continue
                    val symbol = values[sym].uppercase(Locale.US)
                    if (!SYMBOL_REGEX.matches(symbol)) continue
                    val o = values[open].toDoubleOrNull() ?: continue
                    val h = values[high].toDoubleOrNull() ?: continue
                    val l = values[low].toDoubleOrNull() ?: continue
                    val c = values[close].toDoubleOrNull() ?: continue
                    val v = values[vol].toLongOrNull() ?: 0L
                    if (!l.isFinite() || !c.isFinite() || !o.isFinite() || !h.isFinite() ||
                        l <= 0.0 || o < l || h < maxOf(o,c) || c < l || v < 0L) continue
                    map[symbol] = EquityEodBar(date, symbol, l, c, v, open=o, high=h)
                }
                check(map.size >= MIN_VALID_EQ_ROWS) { "NSE_BHAVCOPY_INCOMPLETE_EQ_${map.size}" }
                map.values.toList()
            }
        }
        synchronized(lock) {
            val sql = db.writableDatabase
            sql.beginTransaction()
            try {
                sql.delete("eod","day=?",arrayOf(date.toString()))
                for (bar in rows) {
                    sql.insertOrThrow("eod", null, ContentValues().apply {
                        put("day",bar.date.toString());put("symbol",bar.symbol)
                        put("low",bar.low);put("close",bar.close);put("volume",bar.volume)
                        bar.open?.let{put("open",it)}
                        bar.high?.let{put("high",it)}
                    })
                }
                sql.insertWithOnConflict("eod_days", null, ContentValues().apply {
                    put("day",date.toString());put("symbols",rows.size)
                    put("updated_ms",System.currentTimeMillis())
                },SQLiteDatabase.CONFLICT_REPLACE)
                sql.setTransactionSuccessful()
            } finally { sql.endTransaction() }
        }
        return rows.size
    }

    private fun symbolsFromIndex(url: String): Map<String,String> {
        requireAfterHours()
        return client.newCall(request(url)).execute().use { r ->
            check(r.isSuccessful) { "NIFTY_INDEX_HTTP_${r.code}" }
            val raw = r.body?.string().orEmpty()
            check(raw.length in 100..100_000) { "NIFTY_INDEX_INVALID_SIZE" }
            val lines = raw.lineSequence().filter { it.isNotBlank() }.iterator()
            val headers = csvCells(lines.next()).map { it.removePrefix("\uFEFF").trim() }
            val sym = headers.indexOfFirst { it.equals("Symbol",true) }
            val industry = headers.indexOfFirst { it.equals("Industry",true) }
            check(sym >= 0) { "NIFTY_INDEX_NO_SYMBOL_HEADER" }
            val out = linkedMapOf<String,String>()
            while (lines.hasNext() && out.size < 250) {
                val row = csvCells(lines.next())
                if (sym >= row.size) continue
                val symbol = row[sym].uppercase(Locale.US)
                if (!SYMBOL_REGEX.matches(symbol)) continue
                val sector = if (url == NIFTYBANK_URL) "BANK"
                    else if (url == NIFTYIT_URL) "IT"
                    else row.getOrNull(industry)?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
                out[symbol] = sector
            }
            check(out.size >= if (url == NIFTY100_URL) 85 else 7) {
                "NIFTY_INDEX_INCOMPLETE_${out.size}"
            }
            out
        }
    }

    private fun refreshMembers() {
        if (!afterHours(ZonedDateTime.now(INDIA))) return
        val previous = prefs.getLong("members_last_good_ms",0L)
        if (previous > 0L && System.currentTimeMillis()-previous < 7*24*60*60_000L) return
        try {
            val all = symbolsFromIndex(NIFTY100_URL).toMutableMap()
            val banks = runCatching { symbolsFromIndex(NIFTYBANK_URL) }.getOrDefault(emptyMap())
            val it = runCatching { symbolsFromIndex(NIFTYIT_URL) }.getOrDefault(emptyMap())
            all.keys.toList().forEach { symbol ->
                if (symbol in banks) all[symbol]="BANK"
                if (symbol in it) all[symbol]="IT"
            }
            synchronized(lock) {
                val sql = db.writableDatabase
                sql.beginTransaction()
                try {
                    sql.delete("members",null,null)
                    for ((symbol,sector) in all) {
                        sql.insertOrThrow("members",null,ContentValues().apply {
                            put("symbol",symbol);put("sector",sector)
                        })
                    }
                    sql.setTransactionSuccessful()
                } finally { sql.endTransaction() }
            }
            prefs.edit().putLong("members_last_good_ms",System.currentTimeMillis())
                .putString("members_source","NIFTY100_OFFICIAL").apply()
        } catch (e:Exception) {
            prefs.edit().putString("member_error",(e.message ?: e.javaClass.simpleName).take(100)).apply()
        }
    }

    /** WorkManager and manual taps share one serialized importer; every request is still time-guarded. */
    suspend fun refreshAfterHours(maxFiles: Int = 25): EquityEodRefreshProgress = withContext(Dispatchers.IO) {
        refreshMutex.lock()
        try {
            if (!afterHours(ZonedDateTime.now(INDIA))) return@withContext EquityEodRefreshProgress(0,0,false)
        // NSE's archive domain sometimes needs an NSE homepage cookie session.
        // Warm once nightly, not for every requested day, and never during the market.
        warmNseSession()
        refreshMembers()
        val today = LocalDate.now(INDIA)
        val last = latestEligibleDate(ZonedDateTime.now(INDIA))
        val first = today.minusMonths(6)
        val valid = buildList {
            var day = last
            while (!day.isBefore(first)) {
                if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) add(day)
                day = day.minusDays(1)
            }
        }
        val present = synchronized(lock) {
            val dates = mutableSetOf<String>()
            db.readableDatabase.rawQuery("SELECT day FROM eod_days WHERE day >= ?",arrayOf(first.toString())).use { c ->
                while (c.moveToNext()) dates += c.getString(0)
            }
            dates
        }
        val attempts = synchronized(lock) {
            val statuses = mutableMapOf<String,Pair<String,String>>()
            db.readableDatabase.rawQuery("SELECT day,attempt_day,failure FROM attempts",null).use { c ->
                while (c.moveToNext()) statuses[c.getString(0)] = c.getString(1) to c.getString(2)
            }
            statuses
        }
        val missing = valid.filter { it.toString() !in present }
        var imported = 0
        var wireRequests = 0
        var consecutiveNotFound = 0
        var blocked = false
        for (day in missing) {
            if (wireRequests >= maxFiles || !afterHours(ZonedDateTime.now(INDIA))) break
            val previous = attempts[day.toString()]
            // One attempt per date per calendar day: avoids 403/holiday retry storms.
            if (previous?.first == today.toString()) continue
            // Absent files older than seven days are likely exchange holidays. Retest
            // weekly rather than refetching the same missing archive every evening.
            if (previous?.second == "NSE_BHAVCOPY_HTTP_404" &&
                day.isBefore(today.minusDays(7)) &&
                (previous.first.let { LocalDate.parse(it) }).isAfter(today.minusDays(7))) continue
            var failure = ""
            wireRequests++
            try {
                val rows=importArchive(day)
                imported++
                consecutiveNotFound = 0
                audit.logAsync("EOD_RESEARCH","BHAVCOPY_IMPORTED",mapOf(
                    "date" to day.toString(),"equity_rows" to rows,"groww_api_calls" to 0
                ))
            } catch (e:Exception) {
                failure=(e.message ?: e.javaClass.simpleName).take(90)
                consecutiveNotFound = if(failure=="NSE_BHAVCOPY_HTTP_404") consecutiveNotFound+1 else 0
                if(consecutiveNotFound>=3) blocked=true
                if (failure.contains("403") || failure.contains("429") ||
                    failure.contains("INVALID") || failure.contains("MISSING_COLUMN") ||
                    failure.contains("INCOMPLETE")) blocked = true
                audit.logAsync("EOD_RESEARCH","BHAVCOPY_UNAVAILABLE",mapOf(
                    "date" to day.toString(),"reason" to failure,"groww_api_calls" to 0
                ))
            }
            synchronized(lock) {
                db.writableDatabase.insertWithOnConflict("attempts",null,ContentValues().apply {
                    put("day",day.toString());put("attempt_day",today.toString());put("failure",failure)
                },SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (blocked) break
        }
        // Read-only NSE PE and index-industry imports, after hours only, independent of Groww.
        // Neither data source changes the frozen Fixed selection/BUY path.
        refreshValuationAfterHours()
        refreshLargeIndexEodAfterHours()
        // Daily support-retest research is sourced from COMPLETE, saved NSE EQ
        // day files. The Lows screen never issues a trading-hours market request.
        refreshExistingOhlcAfterHours(maxDays = 6)
        refreshAdaptiveLowsAfterHours()
        prefs.edit().putLong("last_eod_attempt_ms",System.currentTimeMillis())
            .putString("last_eod_error",if(blocked) "Exchange archive temporarily unavailable" else "")
            .apply()
            EquityEodRefreshProgress(imported,missing.size,blocked,wireRequests)
        } finally {
            refreshMutex.unlock()
        }
    }

    /**
     * Upgrade historical L/C/volume archives to OHLC locally, in bounded nightly
     * batches. No existing daily EOD file is discarded until a full replacement
     * passes size and exchange-column validation in a database transaction.
     */
    private fun refreshExistingOhlcAfterHours(maxDays:Int) {
        requireAfterHours()
        val today=LocalDate.now(INDIA).toString()
        val days=synchronized(lock) {
            val out=mutableListOf<String>()
            db.readableDatabase.rawQuery("""
                SELECT d.day FROM eod_days d
                WHERE d.day>=? AND EXISTS (
                    SELECT 1 FROM eod e WHERE e.day=d.day AND e.open IS NULL LIMIT 1)
                ORDER BY d.day DESC LIMIT 45
            """.trimIndent(),arrayOf(LocalDate.now(INDIA).minusMonths(6).toString())).use{c->
                while(c.moveToNext())out+=c.getString(0)
            }
            out
        }
        var performed=0
        for(day in days) {
            if(performed>=maxDays || !afterHours(ZonedDateTime.now(INDIA)))break
            val key="ohlc_backfill_attempt_\$day"
            if(prefs.getString(key,"")==today)continue
            performed++
            prefs.edit().putString(key,today).apply()
            runCatching{importArchive(LocalDate.parse(day))}
                .onSuccess{count->audit.logAsync("ADAPTIVE_LOWS","NSE_OHLC_BACKFILL",mapOf(
                    "day" to day,"symbols" to count,"groww_api_calls" to 0))}
                .onFailure{err->audit.logAsync("ADAPTIVE_LOWS","NSE_OHLC_BACKFILL_FAILED",mapOf(
                    "day" to day,"reason" to (err.message?:err.javaClass.simpleName).take(120),
                    "existing_archive_preserved" to true,"groww_api_calls" to 0))}
        }
    }

    /**
     * On-device full-NSE pattern computation. Called ONLY from existing serialized
     * after-hours NSE archive importer. Stream symbol bars in order, rather than
     * loading 500k+ candles into memory or touching Alpha/Beta Groww quotas.
     */
    private fun refreshAdaptiveLowsAfterHours() {
        requireAfterHours()
        val today=LocalDate.now(INDIA)
        val valData=valuationStore.snapshot()
        val capData=amfiMarketCapDataset
        val members=members()
        val meta=synchronized(lock) {
            db.readableDatabase.rawQuery(
                "SELECT COUNT(*),MIN(day),MAX(day) FROM eod_days WHERE day>=?",
                arrayOf(today.minusMonths(6).toString())).use{c->
                c.moveToFirst()
                Triple(c.getInt(0),c.getString(1).orEmpty(),c.getString(2).orEmpty())
            }
        }
        val latest=meta.third
        if(latest.isBlank())return
        val day=LocalDate.parse(latest)
        val three=synchronized(lock) {
            db.readableDatabase.rawQuery("SELECT COUNT(*),MIN(day) FROM eod_days WHERE day>=? AND day<=?",
                arrayOf(today.minusMonths(3).toString(),latest)).use{c->
                c.moveToFirst()
                c.getInt(0) to c.getString(1).orEmpty()
            }
        }
        val threeReady=EquityEodResearchRepository.threeMonthCoverageComplete(
            three.first,three.second.takeIf{it.isNotBlank()}?.let(LocalDate::parse),today)
        val sixReady=meta.first>=105 && meta.second.isNotBlank() &&
            LocalDate.parse(meta.second).isBefore(today.minusMonths(5).plusDays(14))
        val do3=threeReady && !adaptiveLowStore.hasRun(latest,3)
        val do6=sixReady && !adaptiveLowStore.hasRun(latest,6)
        if(!do3 && !do6) {
            resolveMaturedAdaptiveLowOutcomes()
            return
        }
        val min3=kotlin.math.ceil(three.first.coerceAtLeast(1)*0.85).toInt().coerceAtLeast(50)
        val min6=kotlin.math.ceil(meta.first.coerceAtLeast(1)*0.85).toInt().coerceAtLeast(90)
        val pe=if(valData.peDate==latest)valData.peBySymbol else emptyMap()
        val indexHistory=largeWatchStore.indexHistories(latest)
        val nifty=indexHistory["NIFTY 50"].orEmpty().lastOrNull()
            ?.takeIf{it.day==day}
        val nPct=nifty?.changePct
        val nRegime=LargeIndexResearchPolicy.regime(nPct)
        val rows3=mutableListOf<AdaptiveLowCandidate>()
        val rows6=mutableListOf<AdaptiveLowCandidate>()
        var eligible3=0
        var eligible6=0
        var processed=0
        val runStart=System.currentTimeMillis()
        fun enrich(c:AdaptiveLowCandidate):AdaptiveLowCandidate {
            val sector=valData.industryBySymbol[c.symbol]
                ?:members[c.symbol]
            val mapped=sector?.let{LargeIndexResearchPolicy.sectorIndex(it)}
            val sec=mapped?.let{indexHistory[it]?.lastOrNull()}
                ?.takeIf{it.day==day}
            return c.copy(
                marketCapCategory=capData.labels[c.symbol]?.category?:"UNCLASSIFIED",
                sector=sector,
                stockPe=pe[c.symbol],
                // No historical stock P/B, financial revenue or EPS is invented.
                stockPb=null,revenueGrowthPct=null,profitGrowthPct=null,
                niftyEodPct=nPct,niftyRegime=nRegime,
                sectorIndexName=mapped,sectorEodPct=sec?.changePct,
                sectorRegime=LargeIndexResearchPolicy.regime(sec?.changePct),
                marketEvidenceDay=nifty?.day?.toString().orEmpty(),
                valuationEvidenceDay=if(pe.isNotEmpty())latest else ""
            )
        }
        fun process(symbol:String,bars:List<EquityEodBar>) {
            if(symbol.isBlank() || bars.lastOrNull()?.date!=day)return
            processed++
            if(do3) {
                val subset=bars.filter{!it.date.isBefore(today.minusMonths(3))}
                if(subset.size>=min3) {
                    eligible3++
                    AdaptiveLowPatternPolicy.evaluate(symbol,3,day,subset)?.let{
                        rows3+=enrich(it)
                    }
                }
            }
            if(do6 && bars.size>=min6) {
                eligible6++
                AdaptiveLowPatternPolicy.evaluate(symbol,6,day,bars)?.let{
                    rows6+=enrich(it)
                }
            }
        }
        synchronized(lock) {
            var activeSymbol=""
            val collected=mutableListOf<EquityEodBar>()
            db.readableDatabase.rawQuery("""
                SELECT day,symbol,low,close,volume,open,high FROM eod
                WHERE day>=? AND day<=? ORDER BY symbol,day
            """.trimIndent(),arrayOf(
                (if(do6)today.minusMonths(6) else today.minusMonths(3)).toString(),latest
            )).use{cursor->
                while(cursor.moveToNext()) {
                    val symbol=cursor.getString(1)
                    if(activeSymbol.isNotEmpty() && symbol!=activeSymbol){
                        process(activeSymbol,collected)
                        collected.clear()
                    }
                    activeSymbol=symbol
                    collected+=EquityEodBar(
                        date=LocalDate.parse(cursor.getString(0)),
                        symbol=symbol,low=cursor.getDouble(2),close=cursor.getDouble(3),
                        volume=cursor.getLong(4),
                        open=if(cursor.isNull(5))null else cursor.getDouble(5),
                        high=if(cursor.isNull(6))null else cursor.getDouble(6)
                    )
                }
                if(activeSymbol.isNotBlank())process(activeSymbol,collected)
            }
        }
        val exchangeUniverse=synchronized(lock) {
            db.readableDatabase.rawQuery("SELECT symbols FROM eod_days WHERE day=?",
                arrayOf(latest)).use{c->if(c.moveToFirst())c.getInt(0)else 0}
        }
        // Refuse a partial cached archive claiming to cover the NSE universe.
        if(processed<1500 || processed<exchangeUniverse*0.85) {
            audit.logAsync("ADAPTIVE_LOWS","UNIVERSE_INCOMPLETE_SKIP",mapOf(
                "day" to latest,"processed" to processed,"expected" to exchangeUniverse,
                "market_hours_fetch" to false))
            return
        }
        if(do3)adaptiveLowStore.saveDay(latest,3,exchangeUniverse,eligible3,rows3)
        if(do6)adaptiveLowStore.saveDay(latest,6,exchangeUniverse,eligible6,rows6)
        resolveMaturedAdaptiveLowOutcomes()
        audit.logAsync("ADAPTIVE_LOWS","FULL_NSE_EOD_SUPPORT_SCAN_COMPLETE",mapOf(
            "day" to latest,"nse_eq_universe" to exchangeUniverse,
            "symbols_with_latest_eod" to processed,
            "eligible_three_month" to eligible3,"matches_three_month" to rows3.size,
            "eligible_six_month" to eligible6,"matches_six_month" to rows6.size,
            "elapsed_ms" to (System.currentTimeMillis()-runStart),
            "source" to "NSE_BHAVCOPY_OFF_MARKET_ONLY",
            "groww_api_calls" to 0,
            "fixed_working_range_execution_unchanged" to true
        ))
    }

    private fun resolveMaturedAdaptiveLowOutcomes() {
        requireAfterHours()
        val unresolved=adaptiveLowStore.pending()
        synchronized(lock) {
            for(event in unresolved) {
                val future=mutableListOf<Pair<String,Double>>()
                db.readableDatabase.rawQuery(
                    "SELECT day,close FROM eod WHERE symbol=? AND day>? ORDER BY day ASC LIMIT 10",
                    arrayOf(event.symbol,event.day)).use{c->
                    while(c.moveToNext())future+=c.getString(0) to c.getDouble(1)
                }
                adaptiveLowStore.resolve(event,future)
            }
        }
    }

    /**
     * Fetch the NSE daily PE file once per calendar day after EOD history is available.
     * The current EOD date must match the PE report date before the screen can sort by it.
     * No quote, Groww API, order, or intraday network request is involved.
     */
    private fun refreshValuationAfterHours() {
        requireAfterHours()
        val today = LocalDate.now(INDIA).toString()
        val latestDay = synchronized(lock) {
            db.readableDatabase.rawQuery("SELECT MAX(day) FROM eod_days", null).use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }
        }
        if (latestDay.isNotBlank() && valuationStore.peDate() != latestDay &&
            prefs.getString("nse_pe_attempt_day", "") != today) {
            prefs.edit().putString("nse_pe_attempt_day", today).apply()
            runCatching {
                val date = LocalDate.parse(latestDay)
                val path = NSE_PE_URL.format(date.format(DateTimeFormatter.ofPattern("ddMMyy")))
                val csv = client.newCall(request(path)).execute().use { r ->
                    check(r.isSuccessful) { "NSE_PE_HTTP_${r.code}" }
                    r.body?.string()?.takeIf { it.length in 5_000..500_000 }
                        ?: error("NSE_PE_EMPTY_OR_OVERSIZE")
                }
                val values = NsePeReportResearchPolicy.parse(csv)
                valuationStore.savePe(latestDay, values)
                audit.logAsync("EOD_RESEARCH", "NSE_PE_IMPORT_OK", mapOf(
                    "date" to latestDay, "symbols_with_positive_pe" to values.size,
                    "source" to "NSE_PE_DAILY", "groww_api_calls" to 0
                ))
            }.onFailure { err ->
                audit.logAsync("EOD_RESEARCH", "NSE_PE_IMPORT_UNAVAILABLE", mapOf(
                    "date" to latestDay,
                    "reason" to (err.message ?: err.javaClass.simpleName).take(100),
                    "pe_guessed" to false, "groww_api_calls" to 0
                ))
            }
        }
        val industryAttempt = prefs.getLong("nse_industry_last_attempt_ms", 0L)
        if (System.currentTimeMillis() - industryAttempt < 7L * 24L * 60L * 60_000L) return
        prefs.edit().putLong("nse_industry_last_attempt_ms", System.currentTimeMillis()).apply()
        runCatching {
            val raw = client.newCall(request(NSE_TOTAL_MARKET_INDUSTRIES_URL)).execute().use { r ->
                check(r.isSuccessful) { "NSE_INDUSTRY_HTTP_${r.code}" }
                r.body?.string()?.takeIf { it.length in 10_000..500_000 }
                    ?: error("NSE_INDUSTRY_EMPTY_OR_OVERSIZE")
            }
            val classified = NseIndustryResearchPolicy.parseIndex(raw)
            // Nifty Total Market constituents do NOT cover every listed NSE stock.
            // The full-coverage test must reject promotion of speculative small caps.
            valuationStore.saveIndustries("NSE_NIFTY_TOTAL_MARKET_SUBSET", classified)
            audit.logAsync("EOD_RESEARCH", "NSE_INDUSTRY_SUBSET_IMPORTED", mapOf(
                "classified_symbols" to classified.size,
                "full_nse_industry_coverage_claimed" to false,
                "groww_api_calls" to 0
            ))
        }.onFailure { err ->
            audit.logAsync("EOD_RESEARCH", "NSE_INDUSTRY_UNAVAILABLE", mapOf(
                "reason" to (err.message ?: err.javaClass.simpleName).take(100),
                "industry_guessed" to false, "groww_api_calls" to 0
            ))
        }
    }

    /**
     * Official NSE index snapshots: 1 CSV for every index on a trading day.
     * Backfills up to three missing EOD dates per after-hours pass, independent
     * of the Groww research/order governor. No daytime HTTP calls are allowed.
     */
    private fun refreshLargeIndexEodAfterHours() {
        requireAfterHours()
        val today=LocalDate.now(INDIA).toString()
        val dates=synchronized(lock) {
            val days=mutableListOf<String>()
            db.readableDatabase.rawQuery(
                "SELECT day FROM eod_days ORDER BY day DESC LIMIT 7",null).use{c->
                while(c.moveToNext())days+=c.getString(0)
            }
            days
        }
        var attempted=0
        for(day in dates){
            if(attempted>=3 || !afterHours(ZonedDateTime.now(INDIA)))break
            if(largeWatchStore.hasIndex(day))continue
            val key="nse_index_eod_attempt_${day}"
            if(prefs.getString(key,"")==today)continue
            attempted++
            prefs.edit().putString(key,today).apply()
            runCatching {
                val localDate=LocalDate.parse(day)
                val url=NSE_INDICES_EOD_URL.format(
                    localDate.format(DateTimeFormatter.ofPattern("ddMMyyyy",Locale.US)))
                val csv=client.newCall(request(url)).execute().use{resp->
                    check(resp.isSuccessful){"NSE_INDEX_EOD_HTTP_${resp.code}"}
                    resp.body?.string()?.takeIf{it.length in 1_500..500_000}
                        ?: error("NSE_INDEX_EOD_PAYLOAD_MISSING")
                }
                val parsed=LargeIndexResearchPolicy.parseCsv(csv,localDate)
                largeWatchStore.saveIndex(day,parsed)
                audit.logAsync("LARGE_WATCH","NSE_INDEX_EOD_IMPORTED",mapOf(
                    "day" to day,"indices" to parsed.size,
                    "nifty_move_pct" to parsed.firstOrNull{it.name=="NIFTY 50"}?.changePct,
                    "groww_api_calls" to 0
                ))
            }.onFailure{err->
                audit.logAsync("LARGE_WATCH","NSE_INDEX_EOD_MISSING",mapOf(
                    "day" to day,
                    "reason" to (err.message ?: err.javaClass.simpleName).take(120),
                    "market_regime_guessed" to false,"groww_api_calls" to 0
                ))
            }
        }
    }

    /** No market-data requests: read-only copy from already completed Fixed full-market LTP sweep. */
    fun captureFixedLivePulse(
        current: Map<String,Double>,
        open: Map<String,Double>,
        completedSweep: Boolean
    ) {
        val at = ZonedDateTime.now(INDIA)
        if (!NseEquitySessionCalendar.isFixedScanWindow(at) || !completedSweep) return
        val memberSet = members().keys
        val live = current.filter { (symbol,price) ->
            symbol in memberSet && price.isFinite() && price > 0.0
        }
        val opens = open.filter { (symbol,price) ->
            symbol in memberSet && price.isFinite() && price > 0.0
        }
        if (live.size < 10 || opens.size < 10) return
        prefs.edit().putString("pulse_date",at.toLocalDate().toString())
            .putString("pulse_prices",live.entries.joinToString("|") { "${it.key}~${it.value}" })
            .putString("pulse_open",opens.entries.joinToString("|") { "${it.key}~${it.value}" })
            .putLong("pulse_at_ms",System.currentTimeMillis()).apply()
    }

    private fun members(): Map<String,String> = synchronized(lock) {
        val saved=linkedMapOf<String,String>()
        db.readableDatabase.rawQuery("SELECT symbol,sector FROM members",null).use { c ->
            while(c.moveToNext()) saved[c.getString(0)]=c.getString(1)
        }
        // Curated seeds are an explicitly marked research fallback, never an
        // assertion that all names belong to today's official Nifty-100 index.
        if (saved.isEmpty()) SEEDS else saved
    }

    private fun unpackPulse(field: String): Map<String,Double> =
        prefs.getString(field,"").orEmpty().split('|').mapNotNull { row ->
            val i=row.indexOf('~')
            if(i<=0) null else row.substring(i+1).toDoubleOrNull()?.takeIf {
                it.isFinite() && it>0.0
            }?.let { row.substring(0,i) to it }
        }.toMap()

    fun snapshot(): EquityResearchSnapshot = synchronized(lock) {
        val marketCaps = amfiMarketCapDataset
        val valuation = valuationStore.snapshot()
        val today = LocalDate.now(INDIA)
        val sixCutoff = today.minusMonths(6)
        val threeCutoff = today.minusMonths(3)
        val sql = db.readableDatabase

        data class WindowCoverage(val sessions: Int, val first: String, val latest: String)
        fun coverage(cutoff: String): WindowCoverage =
            sql.rawQuery(
                "SELECT COUNT(*),MIN(day),MAX(day) FROM eod_days WHERE day>=? AND day<=?",
                arrayOf(cutoff, today.toString())
            ).use { c ->
                if (c.moveToFirst()) WindowCoverage(c.getInt(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
                else WindowCoverage(0, "", "")
            }

        val six = coverage(sixCutoff.toString())
        val three = coverage(threeCutoff.toString())
        val totalDays = six.sessions
        val first = six.first
        val latest = six.latest
        val spanComplete = totalDays >= 105 && first.isNotBlank() &&
            LocalDate.parse(first).isBefore(today.minusMonths(5).plusDays(14))
        val threeMonthHistoryComplete =
            EquityEodResearchRepository.threeMonthCoverageComplete(
                three.sessions,
                three.first.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                today
            ) && three.latest.isNotBlank() && three.latest == latest

        fun readLowRows(cutoff: String, latestDay: String, minCount: Int, lookbackMonths: Int): List<EquityLowObservation> {
            if (latestDay.isBlank() || minCount <= 0) return emptyList()
            // The latest EOD row supplies the current close only. It must never
            // redefine the historical low against which that close is measured.
            val referenceEndDay = LocalDate.parse(latestDay).minusDays(1).toString()
            val oneMonthCutoff = LocalDate.parse(latestDay).minusMonths(1).toString()
            val query = """
                SELECT s.symbol,MIN(s.low),COUNT(*),
                    (SELECT t.close FROM eod t WHERE t.symbol=s.symbol AND t.day=?) AS last_close,
                    (SELECT l.day FROM eod l WHERE l.symbol=s.symbol AND l.day>=? AND l.day<=?
                        ORDER BY l.low ASC,l.day ASC LIMIT 1) AS low_day,
                    (SELECT MIN(m.low) FROM eod m WHERE m.symbol=s.symbol AND m.day>=? AND m.day<=?) AS month_low
                FROM eod s
                WHERE s.day>=? AND s.day<=? AND s.low>0 AND s.close>0
                GROUP BY s.symbol
                HAVING COUNT(*)>=CAST(? AS INTEGER) AND last_close IS NOT NULL
                    AND low_day IS NOT NULL AND month_low IS NOT NULL
            """.trimIndent()
            val rows = mutableListOf<EquityLowObservation>()
            sql.rawQuery(
                query,
                arrayOf(
                    latestDay, cutoff, referenceEndDay, oneMonthCutoff, referenceEndDay,
                    cutoff, referenceEndDay, minCount.toString()
                )
            ).use { c ->
                while (c.moveToNext()) {
                    val low = c.getDouble(1)
                    val close = c.getDouble(3)
                    val oneMonthLow = c.getDouble(5)
                    val distance = EquityEodResearchRepository.distanceFromReferenceLowPct(close, low)
                    val oneMonthDistance = EquityEodResearchRepository.distanceFromReferenceLowPct(close, oneMonthLow)
                    if (EquityEodResearchRepository.isWithinTenPctAboveOrBelow(distance) &&
                        oneMonthDistance.isFinite()) {
                        val cap = marketCaps.labels[c.getString(0)]
                        rows += EquityLowObservation(
                            symbol = c.getString(0),
                            close = close,
                            periodLow = low,
                            distancePct = distance,
                            tradingDays = c.getInt(2),
                            latestDate = LocalDate.parse(latestDay),
                            periodLowDate = LocalDate.parse(c.getString(4)),
                            oneMonthLow = oneMonthLow,
                            oneMonthDistancePct = oneMonthDistance,
                            lookbackMonths = lookbackMonths,
                            marketCapCategory = cap?.category ?: "UNCLASSIFIED",
                            marketCapRank = cap?.rank
                        )
                    }
                }
            }
            // Nearest to the prior low first. A breakdown is retained as a
            // negative signed percentage; at equal distance it ranks before
            // a stock still above the old low.
            return rows.sortedWith(
                compareBy<EquityLowObservation> { kotlin.math.abs(it.distancePct) }
                    .thenBy { if (it.distancePct < 0.0) 0 else 1 }
            )
        }

        // Latest EOD day is the price being screened, not a historical observation.
        val threeReferenceSessions = (three.sessions - if (three.latest == latest && latest.isNotBlank()) 1 else 0)
            .coerceAtLeast(0)
        val sixReferenceSessions = (totalDays - if (six.latest == latest && latest.isNotBlank()) 1 else 0)
            .coerceAtLeast(0)
        val threeMonthMinCount = EquityEodResearchRepository.minimumThreeMonthSymbolSessions(threeReferenceSessions)
        val peCurrent = latest.isNotBlank() && latest == valuation.peDate
        val peMap = if (peCurrent) valuation.peBySymbol else emptyMap()
        val fullIndustryPeCoverage = peCurrent && LowPeScreenResearchPolicy.marketIndustryCoverageComplete(
            peMap, valuation.industryBySymbol)
        fun enrichValuations(rows: List<EquityLowObservation>): List<EquityLowObservation> = rows.map { row ->
            row.copy(
                peRatio = peMap[row.symbol],
                industry = valuation.industryBySymbol[row.symbol],
                verifiedIndustryPeMinimum = LowPeScreenResearchPolicy.eligibleSmallCap(
                    row, peMap, valuation.industryBySymbol, peCurrent)
            )
        }
        // Legacy proximity-only near-low rows are intentionally not recalculated on
        // foreground refresh. Prospective support-retest candidates are computed
        // once after hours across ALL valid NSE EQ symbols and persisted atomically.
        val threeMonthRows: List<EquityLowObservation> = emptyList()
        val sixMonthRows: List<EquityLowObservation> = emptyList()

        val adaptive3 = if(threeMonthHistoryComplete)
            adaptiveLowStore.rows(3,latest) else emptyList()
        val adaptive6 = if(spanComplete)
            adaptiveLowStore.rows(6,latest) else emptyList()
        val adaptive3Run=adaptiveLowStore.latestRun(3)
        val adaptive6Run=adaptiveLowStore.latestRun(6)
        val adaptive3Learning=adaptiveLowStore.learning(3)
        val adaptive6Learning=adaptiveLowStore.learning(6)
        val memberMap=members()
        val history=linkedMapOf<String,MutableList<EquityEodBar>>()
        val members=memberMap.keys.toList()
        for (chunk in members.chunked(80)) {
            val markers=List(chunk.size){"?"}.joinToString(",")
            val args=arrayOf(sixCutoff.toString(),*chunk.toTypedArray())
            sql.rawQuery("SELECT day,symbol,low,close,volume FROM eod WHERE day>=? AND symbol IN ($markers) ORDER BY symbol,day",args).use { c ->
                while(c.moveToNext()) {
                    val sym=c.getString(1)
                    history.getOrPut(sym){mutableListOf()}+=EquityEodBar(
                        LocalDate.parse(c.getString(0)),sym,c.getDouble(2),c.getDouble(3),c.getLong(4)
                    )
                }
            }
        }
        // Stale pulse data never participates in an intraday recommendation.
        val age=System.currentTimeMillis()-prefs.getLong("pulse_at_ms",0)
        val fresh=NseEquitySessionCalendar.isOpen(ZonedDateTime.now(INDIA)) &&
            prefs.getString("pulse_date","")==today.toString() && age in 0..600_000L
        val pulse=if(fresh) unpackPulse("pulse_prices") else emptyMap()
        val opens=if(fresh) unpackPulse("pulse_open") else emptyMap()
        val five=history.mapValues { (_,bars) ->
            if(bars.size>=6) (bars.last().close/bars[bars.lastIndex-5].close-1)*100 else Double.NaN
        }
        val peerIntraday=memberMap.keys.mapNotNull { sym ->
            val price=pulse[sym];val open=opens[sym]
            if(price!=null && open!=null && open>0) sym to ((price/open-1)*100) else null
        }.toMap()
        val allFive=memberMap.keys.mapNotNull { five[it]?.takeIf(Double::isFinite) }
        val allIntraday=memberMap.keys.mapNotNull { peerIntraday[it] }
        // This is equal-weighted constituent pulse breadth; it is NOT the live Nifty index.
        val liveLargeBreadth=allIntraday.takeIf{it.size>=10}
            ?.let {moves -> moves.count { it>0.0 }.toDouble()/moves.size*100.0 }
        val result=members.mapNotNull { symbol ->
            val sector=memberMap[symbol].orEmpty()
            val sectorKeys=memberMap.filterValues { it==sector }.keys
            val sectorFive=sectorKeys.mapNotNull { five[it]?.takeIf(Double::isFinite) }
            val sectorIntraday=sectorKeys.mapNotNull { peerIntraday[it] }
            // Some sectors contain only 1–2 stocks in a fallback universe. Use
            // the BROAD peer proxy, explicitly labeled, rather than excluding
            // the constituent from all research opportunities.
            val broad=sectorFive.size<3 || (pulse.isNotEmpty() && sectorIntraday.size<3)
            LargeCapEquityResearchPolicy.scoreLargeCap(symbol,sector,history[symbol].orEmpty(),
                if(broad)allFive else sectorFive,
                if(broad)allIntraday else sectorIntraday,
                pulse[symbol],opens[symbol],
                comparisonBasis=if(broad)"LARGE_CAP_PEER_PROXY" else "SECTOR_PEERS")
        }.sortedWith(compareByDescending<LargeCapEquityObservation> { it.swingResearch }
            .thenByDescending { it.intradayResearch }.thenByDescending { it.relativeFiveDayPct ?: -999.0 })
        // Stock, Nifty and sector features use only data that existed as of the
        // observation. Index and sector returns remain null when EOD source is missing.
        val indexHistories=largeWatchStore.indexHistories(latest.ifBlank{"0000-01-01"})
        val niftyHistory=indexHistories["NIFTY 50"].orEmpty()
        val niftyToday=niftyHistory.lastOrNull()?.takeIf{it.day.toString()==latest}
        val evidence=linkedMapOf<String,LargeWatchEvidence>()
        for(row in result){
            val sectorIndex=LargeIndexResearchPolicy.sectorIndex(row.sector)
            val historyBySector=sectorIndex?.let{indexHistories[it]}.orEmpty()
            val item=LargeWatchFeaturePolicy.build(
                bars=history[row.symbol].orEmpty(),eodDate=latest,
                pe=peMap[row.symbol],
                rank=marketCaps.labels[row.symbol]?.rank,
                nifty=niftyHistory,sector=historyBySector,
                sectorIndexName=sectorIndex,
                intradayPct=row.intradayPct,
                intradayRelativePct=row.intradayRelativePct,
                intradayBreadthPct=liveLargeBreadth,
                relativeFiveDayPct=row.relativeFiveDayPct,
                sectorFiveDayPct=row.sectorFiveDayPct,
                sectorBreadthPct=row.sectorBreadthPct,
                volumeAcceleration=row.volumeAcceleration,
                swing=row.swingResearch,intraday=row.intradayResearch
            ) ?: continue
            evidence[row.symbol]=item
            if(row.swingResearch && latest.isNotBlank() &&
                !LocalDate.parse(latest).isBefore(today.minusDays(4))) {
                largeWatchStore.record(latest,row.symbol,"SWING",row.close,
                    System.currentTimeMillis(),item)
            }
            if(fresh && row.intradayResearch) {
                val livePrice=pulse[row.symbol]
                if(livePrice!=null && livePrice.isFinite() && livePrice>0) {
                    largeWatchStore.record(today.toString(),row.symbol,
                        "INTRADAY",livePrice,prefs.getLong("pulse_at_ms",0L),item)
                }
            }
        }
        // Preserve ALL monitored large caps in a HIDDEN control cohort once per
        // EOD day, even when they are not actionable. Without this denominator,
        // precision-only learning cannot reveal false negatives and selection bias.
        if(latest.isNotBlank() && lastUniverseCapturedDay != latest &&
            !LocalDate.parse(latest).isBefore(today.minusDays(4))) {
            for(row in result) {
                val item=evidence[row.symbol] ?: continue
                largeWatchStore.record(latest,row.symbol,"UNIVERSE",row.close,
                    System.currentTimeMillis(),item)
            }
            lastUniverseCapturedDay=latest
        }
        // EOD-close learning is local-only. Events without the full horizon are
        // censored, never recorded as failures or simulated broker profits.
        for((signalDay,symbol,kind) in largeWatchStore.unresolved().take(300)){
            if(kind=="INTRADAY"){
                sql.rawQuery(
                    "SELECT day,close FROM eod WHERE day=? AND symbol=?",
                    arrayOf(signalDay,symbol)).use{c->
                    if(c.moveToFirst() && c.getDouble(1)>0.0)
                        largeWatchStore.resolveEvent(signalDay,symbol,kind,c.getDouble(1),c.getString(0))
                }
            }else{
                val future=mutableListOf<Pair<String,Double>>()
                sql.rawQuery(
                    "SELECT day,close FROM eod WHERE day>? AND symbol=? ORDER BY day ASC LIMIT 3",
                    arrayOf(signalDay,symbol)).use{c->
                    while(c.moveToNext())future+=c.getString(0) to c.getDouble(1)
                }
                if(future.size==3 && future.last().second>0.0)
                    largeWatchStore.resolveEvent(signalDay,symbol,kind,
                        future.last().second,future.last().first)
            }
        }
        val learning=largeWatchStore.learningSummaries()
        EquityResearchSnapshot(
            threeMonthLowRows=threeMonthRows,
            sixMonthLowRows=sixMonthRows,
            largeCaps=result,
            completedDays=totalDays,
            firstDay=first,
            threeMonthCompletedDays=three.sessions,
            threeMonthFirstDay=three.first,
            latestDay=latest,
            universeCount=if(latest.isBlank()) 0 else sql.rawQuery(
                "SELECT symbols FROM eod_days WHERE day=?",arrayOf(latest)).use { c ->
                    if(c.moveToFirst())c.getInt(0) else 0
                },
            threeMonthHistoryComplete=threeMonthHistoryComplete,
            sixMonthHistoryComplete=spanComplete,
            lastError=prefs.getString("last_eod_error","").orEmpty(),
            memberSource=prefs.getString("members_source","CURATED_SEEDS_UNVERIFIED").orEmpty(),
            currentPulseAtMs=if(fresh)prefs.getLong("pulse_at_ms",0L) else 0L,
            refreshAtMs=prefs.getLong("last_eod_attempt_ms",0L),
            marketCapClassificationPeriod=marketCaps.period,
            marketCapSource=marketCaps.source,
            marketCapClassificationCount=marketCaps.labels.size,
            officialPeDate=valuation.peDate,
            officialPeSymbols=valuation.peBySymbol.size,
            industryClassifiedSymbols=valuation.industryBySymbol.size,
            fullIndustryPeCoverage=fullIndustryPeCoverage,
            adaptiveLowThreeRows=adaptive3,
            adaptiveLowSixRows=adaptive6,
            adaptiveLowThreeRun=adaptive3Run,
            adaptiveLowSixRun=adaptive6Run,
            adaptiveLowThreeLearning=adaptive3Learning,
            adaptiveLowSixLearning=adaptive6Learning,
            largeWatchEvidence=evidence,
            largeWatchLearning=learning,
            niftyContextDay=niftyToday?.day?.toString().orEmpty(),
            niftyEodMovePct=niftyToday?.changePct,
            niftyPreviousEodMovePct=if(niftyToday!=null)niftyHistory.dropLast(1).lastOrNull()?.changePct else null,
            niftyFiveDayMovePct=if(niftyToday!=null && niftyHistory.size>5)
                (niftyToday.close/niftyHistory[niftyHistory.lastIndex-5].close-1.0)*100.0 else null,
            niftyEodRegime=LargeIndexResearchPolicy.regime(niftyToday?.changePct),
            niftyIndexPe=niftyToday?.pe,
            niftyIndexPb=niftyToday?.pb,
            liveLargeCapBreadthProxyPct=liveLargeBreadth
        )
    }
}
