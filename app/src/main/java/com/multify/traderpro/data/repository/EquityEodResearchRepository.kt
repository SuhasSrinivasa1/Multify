package com.multify.traderpro.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import com.multify.traderpro.engine.NseArchiveRecoveryPolicy
import android.os.SystemClock
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    val archiveProgress: String = "",
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
    val liveLargeCapBreadthProxyPct: Double? = null,
    val institutional: com.multify.traderpro.engine.InstitutionalSnapshot = com.multify.traderpro.engine.InstitutionalSnapshot(),
    val lowCapAudit3: String = "",
    val lowCapAudit6: String = "",
    val lastSource: String = "",
    val lastSuccessfulImportAtMs: Long = 0L
)
data class EquityEodRefreshProgress(
    val importedDays: Int,
    val missingWeekdays: Int,
    val sourceFailure: Boolean,
    val requestedDays: Int = 0,
    val retryPending: Boolean = false
)
data class EquityEodLocalImportResult(
    val importedSessions: Int,
    val importedRows: Int,
    val skippedFiles: Int,
    val failedFiles: List<String>
)
data class PortableResearchBackup(
    val databaseEntries: Map<String, ByteArray>,
    val preferenceEntries: Map<String, String>,
    val manifestJson: String
)
data class PortableResearchRestoreResult(
    val eodRowsMerged: Int = 0,
    val eodSessionsMerged: Int = 0,
    val otherResearchRowsMerged: Int = 0,
    val preferenceKeysMerged: Int = 0,
    val databasesMerged: Int = 0
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
        private const val EOD_DATABASE_NAME = "suhas_shree_eod_research_v1.db"
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
        context, EOD_DATABASE_NAME, null, 2
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
    private val institutionalStore = InstitutionalDisclosureStore(context)
    private var lastUniverseCapturedDay = ""
    private val prefs = context.getSharedPreferences("suhas_shree_eod_state", Context.MODE_PRIVATE)
    // AMFI classification is bundled as a build-time asset. No runtime market-cap
    // requests are added, and missing/unmatched symbols remain explicitly unclassified.
    private val amfiMarketCapDataset: AmfiMarketCapDataset by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            context.assets.open("amfi_market_cap_categories.csv").bufferedReader().use {
                AmfiMarketCapClassificationParser.parse(it.readText()).also { data ->
                    require(data.period.isNotBlank() && data.labels.size>=250 &&
                        data.labels.values.map{it.category}.toSet().containsAll(
                            listOf("LARGE_CAP","MID_CAP","SMALL_CAP"))) { "AMFI_INCOMPLETE_ASSET" }
                    audit.logAsync("EOD_RESEARCH","AMFI_MARKET_CAP_ASSET_READY",mapOf(
                        "period" to data.period,"symbols" to data.labels.size,
                        "large" to data.labels.values.count{it.category=="LARGE_CAP"},
                        "mid" to data.labels.values.count{it.category=="MID_CAP"},
                        "small" to data.labels.values.count{it.category=="SMALL_CAP"}))
                }
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
        .retryOnConnectionFailure(false)
        .build()
    private val lock = Any()
    private val refreshMutex = Mutex()

    private data class Pulse(val at:ZonedDateTime,val current:Map<String,Double>,val open:Map<String,Double>)
    // Bounded and non-blocking: no research database lock or preference serialization
    // may delay the Fixed discovery/Working Range evaluation path.
    private val pulseQueue=Channel<Pulse>(capacity=2)
    private val pulseScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    init {
        pulseScope.launch {
            for(pulse in pulseQueue) {
                runCatching { persistFixedLivePulse(pulse) }.onFailure { err ->
                    audit.logAsync("LARGE_CAP_SHADOW","PULSE_PERSIST_FAILED",mapOf("type" to err.javaClass.simpleName,"groww_api_calls" to 0))
                }
            }
        }
    }

    private fun request(url: String): Request = Request.Builder().url(url)
        .header("Accept", "text/csv,application/zip,application/octet-stream,*/*")
        .header("Referer", if(url.contains("niftyindices.com")) "https://www.niftyindices.com/" else "https://www.nseindia.com/")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
        .build()

    private val archiveHost="nsearchives.nseindia.com"
    private val lastWire=mutableMapOf<String,Long>()
    private fun cooldownUntil(host:String)=prefs.getLong("nse_cooldown_until_"+host,0L)
    private fun cooldownReason(host:String)=prefs.getString("nse_cooldown_reason_"+host,"").orEmpty()
    private fun cooldownText(host:String):String {
        val until=cooldownUntil(host)
        val time=java.time.Instant.ofEpochMilli(until).atZone(INDIA).toLocalDateTime().toString().replace('T',' ')
        return "${cooldownReason(host)} · retry after $time IST"
    }
    private fun recordSourceFailure(host:String,code:Int,retryAfter:String?,reason:String) {
        val now=System.currentTimeMillis()
        val failureKey="nse_403_failures_"+host
        val failures=if(code==403)(prefs.getInt(failureKey,0)+1).coerceAtMost(9) else 1
        val wait=NseArchiveRecoveryPolicy.cooldownMs(code,retryAfter,now,failures)
        if(wait<=0)return
        val until=maxOf(cooldownUntil(host),NseArchiveRecoveryPolicy.deadline(now,wait))
        val editor=prefs.edit().putLong("nse_cooldown_until_"+host,until)
            .putString("nse_cooldown_reason_"+host,reason)
        if(code==403)editor.putInt(failureKey,failures)
        val saved=editor.commit()
        audit.logAsync("EOD_RESEARCH","SOURCE_COOLDOWN",mapOf("host" to host,"reason" to reason,
            "retry_after" to retryAfter,"consecutive_403_failures" to if(code==403)failures else null,
            "cooldown_ms" to wait,"until_ms" to until,"persisted" to saved,"groww_requests" to 0))
    }

    private fun recordSourceSuccess(host:String) {
        val failureKey="nse_403_failures_"+host
        if(prefs.getInt(failureKey,0)>0 || cooldownUntil(host)>0L) {
            prefs.edit().putInt(failureKey,0)
                .remove("nse_cooldown_until_"+host).remove("nse_cooldown_reason_"+host).commit()
        }
    }
    private fun sourceGet(url:String):okhttp3.Response {
        requireAfterHours()
        val host=url.toHttpUrl().host
        if(!NseArchiveRecoveryPolicy.allowed(System.currentTimeMillis(),cooldownUntil(host)))
            throw IOException("NSE_SOURCE_COOLDOWN "+cooldownText(host))
        val gap=(NseArchiveRecoveryPolicy.MIN_GAP_MS-(SystemClock.elapsedRealtime()-(lastWire[host]?:0L))).coerceAtLeast(0L)
        if(gap>0)Thread.sleep(gap)
        requireAfterHours()
        lastWire[host]=SystemClock.elapsedRealtime()
        val response=try{client.newCall(request(url)).execute()}catch(e:IOException){
            recordSourceFailure(host,0,null,"NETWORK "+e.javaClass.simpleName)
            throw e
        }
        if(!response.isSuccessful)recordSourceFailure(host,response.code,response.header("Retry-After"),"HTTP "+response.code)
        else recordSourceSuccess(host)
        return response
    }
    private fun warmNseSession() {
        requireAfterHours()
        val today=LocalDate.now(INDIA).toString()
        if(warmedNseDate==today) return
        warmedNseDate=today
        runCatching {
            sourceGet("https://www.nseindia.com/").use { response ->
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
        val rows = sourceGet(url).use { response ->
            if (!response.isSuccessful) error("NSE_BHAVCOPY_HTTP_${response.code}")
            val body = response.body ?: error("NSE_BHAVCOPY_EMPTY")
            val zip = ZipInputStream(body.byteStream())
            zip.use { stream ->
                val entry = stream.nextEntry ?: error("NSE_BHAVCOPY_ZIP_EMPTY")
                check(entry.name.endsWith(".csv", true) && !entry.name.contains("..") && entry.name.contains(date.format(BASIC))) {
                    "NSE_BHAVCOPY_ZIP_ENTRY_INVALID"
                }
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                val headings = csvCells(reader.readLine() ?: error("NSE_BHAVCOPY_HEADER_EMPTY"))
                    .map { it.removePrefix("\uFEFF").trim() }
                val dateIndex=headings.indexOf("TradDt")
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
                    if(dateIndex>=0)check(values.getOrNull(dateIndex)==date.toString()){"NSE_BHAVCOPY_DATE_MISMATCH"}
                    val symbol = values[sym].uppercase(Locale.US)
                    if (!SYMBOL_REGEX.matches(symbol)) continue
                    val o = values[open].toDoubleOrNull() ?: continue
                    val h = values[high].toDoubleOrNull() ?: continue
                    val l = values[low].toDoubleOrNull() ?: continue
                    val c = values[close].toDoubleOrNull() ?: continue
                    val v = values[vol].toLongOrNull() ?: 0L
                    if (!l.isFinite() || !c.isFinite() || !o.isFinite() || !h.isFinite() ||
                        l <= 0.0 || o < l || h < maxOf(o,c) || c < l || v < 0L) continue
                    check(symbol !in map){"NSE_BHAVCOPY_DUPLICATE_EQ_SYMBOL"}
                    map[symbol] = EquityEodBar(date, symbol, l, c, v, open=o, high=h)
                }
                stream.closeEntry() // finish CRC validation before committing any rows
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

    /**
     * Manual, user-selected NSE report fallback for archive-host 403s.
     * Files are parsed and validated locally; this path makes zero Groww/network requests.
     */
    suspend fun importLocalArchives(uris:List<Uri>):EquityEodLocalImportResult=withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Select at least one NSE report file" }
        require(uris.size<=100) { "Select at most 100 NSE report files per import" }
        val localNow=ZonedDateTime.now(INDIA)
        require(!NseEquitySessionCalendar.isOpen(localNow) && !NseEquitySessionCalendar.isWarmupWindow(localNow)) {
            "Manual NSE archive import is paused during market hours and the pre-open warmup to protect Fixed scan performance"
        }
        val imported=linkedMapOf<LocalDate,Int>()
        var skipped=0
        val failures=mutableListOf<String>()
        val latest=latestEligibleDate(ZonedDateTime.now(INDIA))
        val oldest=LocalDate.now(INDIA).minusDays(400)
        for(uri in uris) {
            val parsed=runCatching { NseBhavcopyFileImporter.parse(context,uri) }
            if(parsed.isFailure) {
                failures += NseBhavcopyFileImporter.displayName(context,uri) + ": " +
                    (parsed.exceptionOrNull()?.message ?: "Invalid NSE report").take(100)
                continue
            }
            val file=parsed.getOrThrow()
            try {
                check(!file.date.isAfter(latest)) { "Report date is newer than latest eligible exchange EOD" }
                check(!file.date.isBefore(oldest)) { "Report is older than the supported 400-day import window" }
                val accepted=persistManualArchive(file.date,file.rows)
                if(accepted) imported[file.date]=maxOf(imported[file.date] ?: 0,file.rows.size)
                else skipped++
            } catch(error:Exception) {
                failures += file.fileName + ": " + (error.message ?: error.javaClass.simpleName).take(100)
            }
        }
        val rowsImported=imported.values.sum()
        if(imported.isNotEmpty()) {
            prefs.edit()
                .putString("last_eod_source","USER_SELECTED_NSE_REPORT")
                .putLong("last_eod_success_at_ms",System.currentTimeMillis())
                .putString("last_eod_progress","Manual NSE import · " + imported.size + " sessions · " +
                    rowsImported + " EQ rows · " + skipped + " skipped · " + failures.size + " rejected")
                .apply()
        }
        audit.logAsync("EOD_RESEARCH","MANUAL_NSE_REPORT_IMPORT",mapOf(
            "imported_sessions" to imported.size,
            "imported_eq_rows" to rowsImported,
            "skipped_files" to skipped,
            "failed_files" to failures.take(12),
            "network_requests" to 0,
            "groww_requests" to 0,
            "trading_state_changed" to false
        ))
        EquityEodLocalImportResult(imported.size,rowsImported,skipped,failures.take(12))
    }

    private fun persistManualArchive(date:LocalDate,rows:List<EquityEodBar>):Boolean=synchronized(lock) {
        val sql=db.writableDatabase
        val existing=sql.rawQuery("SELECT symbols FROM eod_days WHERE day=?",arrayOf(date.toString())).use {
            if(it.moveToFirst())it.getInt(0) else 0
        }
        // Never replace a larger already-validated session with a smaller manual file.
        if(existing>=rows.size)return@synchronized false
        sql.beginTransaction()
        try {
            sql.delete("eod","day=?",arrayOf(date.toString()))
            for(bar in rows) {
                sql.insertOrThrow("eod",null,ContentValues().apply {
                    put("day",bar.date.toString())
                    put("symbol",bar.symbol)
                    put("low",bar.low)
                    put("close",bar.close)
                    put("volume",bar.volume)
                    bar.open?.let { put("open",it) }
                    bar.high?.let { put("high",it) }
                })
            }
            sql.insertWithOnConflict("eod_days",null,ContentValues().apply {
                put("day",date.toString())
                put("symbols",rows.size)
                put("updated_ms",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE)
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        true
    }

    private fun symbolsFromIndex(url: String): Map<String,String> {
        requireAfterHours()
        return sourceGet(url).use { r ->
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
        // A source cooldown suppresses only remote archive attempts; local EOD replay and
        // independent research stages must still run on already-validated history.
        val archiveSourceReady=NseArchiveRecoveryPolicy.allowed(
            System.currentTimeMillis(),cooldownUntil(archiveHost))
        if(archiveSourceReady) warmNseSession()
        else {
            val message=cooldownText(archiveHost)
            prefs.edit().putString("last_eod_error",message).apply()
            audit.logAsync("EOD_RESEARCH","ARCHIVE_SOURCE_COOLDOWN_LOCAL_RESEARCH_CONTINUES",mapOf(
                "retry_at_ms" to cooldownUntil(archiveHost),
                "network_requests" to 0,
                "groww_requests" to 0,
                "local_support_retest_allowed" to true
            ))
        }
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
        var blocked = !archiveSourceReady
        var lastFailure = if(archiveSourceReady) "" else cooldownText(archiveHost)
        var retryPending = false
        var missingFiles = 0
        var skipped = 0
        for (day in missing) {
            if (wireRequests >= maxFiles || !afterHours(ZonedDateTime.now(INDIA))) break
            val previous = attempts[day.toString()]
            if(!NseArchiveRecoveryPolicy.allowed(System.currentTimeMillis(),cooldownUntil(archiveHost))) {
                blocked=true; retryPending=true; lastFailure=cooldownText(archiveHost);break
            }
            val retryKey="nse_day_retry_"+day
            val next=prefs.getLong(retryKey,0L)
            if(next>System.currentTimeMillis()) {
                skipped++
                if(!day.isBefore(today.minusDays(2)))retryPending=true
                continue
            }
            if(next==0L && previous?.first==today.toString()){skipped++;continue}
            if(next==0L && previous?.second=="NSE_BHAVCOPY_HTTP_404" && day.isBefore(today.minusDays(7)) &&
                LocalDate.parse(previous.first).isAfter(today.minusDays(7))){skipped++;continue}
            var failure = ""
            wireRequests++
            try {
                val rows=importArchive(day)
                imported++
                prefs.edit().remove(retryKey).putString("last_eod_source","NSE_ARCHIVE_HTTP")
                    .putLong("last_eod_success_at_ms",System.currentTimeMillis()).apply()
                audit.logAsync("EOD_RESEARCH","BHAVCOPY_IMPORTED",mapOf(
                    "date" to day.toString(),"equity_rows" to rows,"groww_api_calls" to 0
                ))
            } catch (e:Exception) {
                failure=(e.message ?: e.javaClass.simpleName).take(90)
                val recent=!day.isBefore(today.minusDays(2))
                val missingFile=failure=="NSE_BHAVCOPY_HTTP_404"
                if(missingFile)missingFiles++
                val now=System.currentTimeMillis()
                val wait=if(missingFile)NseArchiveRecoveryPolicy.missingRetryMs(recent)
                    else if(cooldownUntil(archiveHost)>now)cooldownUntil(archiveHost)-now
                    else NseArchiveRecoveryPolicy.TRANSIENT_COOLDOWN_MS
                prefs.edit().putLong(retryKey,NseArchiveRecoveryPolicy.deadline(now,wait)).apply()
                lastFailure="$day · $failure"
                if(!missingFile || recent)retryPending=true
                if(!missingFile) {
                    blocked=true
                    if(cooldownUntil(archiveHost)<=now)
                        recordSourceFailure(archiveHost,0,null,lastFailure)
                    lastFailure="$lastFailure · "+cooldownText(archiveHost)
                }
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
        // Independent after-hours research stages run even when the bhavcopy archive is blocked.
        // Each host has its own persisted cooldown, so a 403 on one source never gates another.
        // These stages are read-only and add zero Groww calls; Fixed scoring/BUY inputs remain frozen.
        fun offHoursStep(name:String,task:()->Unit) {
            if(!afterHours(ZonedDateTime.now(INDIA)))return
            runCatching{task()}.onFailure{err->audit.logAsync("EOD_RESEARCH","RESEARCH_STAGE_FAILED",
                mapOf("stage" to name,"reason" to (err.message?:err.javaClass.simpleName).take(180),"groww_requests" to 0))}
        }
        offHoursStep("VALUATION"){refreshValuationAfterHours()}
        offHoursStep("INDEX"){refreshLargeIndexEodAfterHours()}
        offHoursStep("OHLC"){refreshExistingOhlcAfterHours(maxDays = 6)}
        offHoursStep("INSTITUTIONAL"){refreshInstitutionalAfterHours()}
        offHoursStep("SUPPORT_RETEST"){refreshAdaptiveLowsAfterHours()}
        val summary="Imported $imported · archive attempts $wireRequests · missing files $missingFiles · deferred $skipped"
        prefs.edit().putLong("last_eod_attempt_ms",System.currentTimeMillis())
            .putString("last_eod_error",lastFailure)
            .putString("last_eod_progress",summary).apply()
        audit.logAsync("EOD_RESEARCH","ARCHIVE_PASS_SUMMARY",mapOf("imported" to imported,"attempts" to wireRequests,
            "missing_files" to missingFiles,"deferred" to skipped,"source_failure" to blocked,"retry_pending" to retryPending,
            "last_failure" to lastFailure,"groww_requests" to 0))
        EquityEodRefreshProgress(imported,missing.size,blocked,wireRequests,retryPending)
        } finally {
            refreshMutex.unlock()
        }
    }

    /** Manual research refresh shares the same mutex and off-hours guard. */
    suspend fun refreshInstitutionalOnly() = withContext(Dispatchers.IO) {
        refreshMutex.lock()
        try {
            if(afterHours(ZonedDateTime.now(INDIA))) refreshInstitutionalAfterHours()
        } finally { refreshMutex.unlock() }
    }

    private fun refreshInstitutionalAfterHours() {
        requireAfterHours()
        val now=ZonedDateTime.now(INDIA)
        val day=latestEligibleDate(now)
        val last=day.toString()
        val cached=institutionalStore.snapshot()
        for(source in listOf("BULK","BLOCK")){
            if(!afterHours(ZonedDateTime.now(INDIA)))break
            val prior=if(source=="BULK")cached.bulk else cached.block
            // One attempt per source per calendar date; an early-morning retry can
            // recover a failed previous evening without creating an HTTP retry loop.
            val attempt="institutional_attempt_v2_"+source
            val key=now.toLocalDate().toString()+"|"+last
            if(prefs.getString(attempt,"")==key)continue
            if(prior!=null && prior.day>=last && prior.receivedAtMs>0 && prior.retainedAllRows)continue
            prefs.edit().putString(attempt,key).apply()
            val file=source.lowercase(Locale.US)
            runCatching {
                val url="https://nsearchives.nseindia.com/content/equities/$file.csv"
                val csv=sourceGet(url).use{resp->
                    check(resp.isSuccessful){"NSE_DEAL_HTTP_${resp.code}"}
                    val body=resp.body?:error("NSE_DEAL_EMPTY_BODY")
                    val input=body.source()
                    check(!input.request(2_000_001L)){"NSE_DEAL_OVERSIZE"}
                    input.readUtf8()
                }
                val report=com.multify.traderpro.engine.InstitutionalIntelligencePolicy.parseLatest(
                    csv,day,source,System.currentTimeMillis())
                institutionalStore.save(report)
                audit.logAsync("INSTITUTIONAL_RESEARCH","DISCLOSURES_IMPORTED",mapOf(
                    "day" to report.day,"source" to source,"disclosed_rows" to report.disclosedRows,
                    "matched_rows" to report.deals.count{it.category=="FII"||it.category=="DII"},"unmatched_rows" to report.unmatchedRows,
                    "retained_rows" to report.deals.size,"verification_coverage_pct" to (100.0*report.deals.count{it.category=="FII"||it.category=="DII"}/report.deals.size.coerceAtLeast(1)),
                    "duplicates" to report.duplicateRows,"groww_api_calls" to 0))
            }.onFailure{err->
                audit.logAsync("INSTITUTIONAL_RESEARCH","DISCLOSURES_UNAVAILABLE",mapOf(
                    "day" to last,"source" to source,"reason" to (err.message?:err.javaClass.simpleName).take(100),
                    "cached_report_preserved" to true,"groww_api_calls" to 0))
            }
        }
        if(afterHours(ZonedDateTime.now(INDIA))) matureInstitutionalOutcomes()
    }

    /** Conservative forward close study: use only observations after disclosure receipt.
     * Five-session result is not a fill, adjusted return or executable trade. */
    private fun matureInstitutionalOutcomes() {
        requireAfterHours()
        val snap=institutionalStore.snapshot()
        val done=snap.outcomes.map{it.source+"|"+it.day+"|"+it.symbol}.toHashSet()
        val observations=mutableListOf<com.multify.traderpro.engine.InstitutionalOutcome>()
        synchronized(lock) {
            val dbRead=db.readableDatabase
            for(report in snap.history.sortedByDescending{it.day}.take(60)) {
                if(report.receivedAtMs<=0)continue
                val received=java.time.Instant.ofEpochMilli(report.receivedAtMs).atZone(INDIA).toLocalDate().toString()
                val start=maxOf(report.day,received)
                val dates=mutableListOf<String>()
                dbRead.rawQuery("SELECT day FROM eod_days WHERE day>? ORDER BY day LIMIT 6",arrayOf(start)).use{c->
                    while(c.moveToNext())dates+=c.getString(0)
                }
                if(dates.size!=6)continue
                val symbols=com.multify.traderpro.engine.InstitutionalIntelligencePolicy.top(report,"BUY","ALL").map{it.symbol}
                for(symbol in symbols){
                    if(report.source+"|"+report.day+"|"+symbol in done)continue
                    val closes=mutableListOf<Pair<String,Double>>()
                    dbRead.rawQuery("SELECT day,close FROM eod WHERE symbol=? AND day>=? AND day<=? ORDER BY day",
                        arrayOf(symbol,dates.first(),dates.last())).use{c->
                        while(c.moveToNext())closes+=c.getString(0) to c.getDouble(1)
                    }
                    if(closes.map{it.first}!=dates || closes.any{!it.second.isFinite()||it.second<=0})continue
                    if(closes.zipWithNext().any{(a,b)->kotlin.math.abs(b.second/a.second-1)>=0.38})continue
                    val ret=(closes.last().second/closes.first().second-1)*100
                    observations+=com.multify.traderpro.engine.InstitutionalOutcome(report.source,report.day,symbol,dates.first(),dates.last(),ret)
                }
            }
        }
        if(observations.isNotEmpty())institutionalStore.saveOutcomes(observations)
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
            val key="ohlc_backfill_attempt_$day"
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
        val instrumentNames=symbols.instruments().associate{it.symbol to it.companyName}
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
        val do3=threeReady && (!adaptiveLowStore.hasRun(latest,3) || prefs.getString("low_cap_audit_3_day","")!=latest || prefs.getInt("low_cap_audit_3_version",0)!=2)
        val do6=sixReady && (!adaptiveLowStore.hasRun(latest,6) || prefs.getString("low_cap_audit_6_day","")!=latest || prefs.getInt("low_cap_audit_6_version",0)!=2)
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
        val audit3=linkedMapOf<String,MutableMap<String,Int>>()
        val audit6=linkedMapOf<String,MutableMap<String,Int>>()
        val large3=linkedMapOf<String,String>()
        val large6=linkedMapOf<String,String>()
        val classifiedLarge=capData.labels.filterValues{it.category=="LARGE_CAP"}.keys
        fun mark(m:MutableMap<String,MutableMap<String,Int>>,cap:String,reason:String) {
            val group=m.getOrPut(cap){linkedMapOf()}
            group[reason]=(group[reason]?:0)+1
        }
        val runStart=System.currentTimeMillis()
        fun enrich(c:AdaptiveLowCandidate):AdaptiveLowCandidate {
            val sector=valData.industryBySymbol[c.symbol]
                ?:members[c.symbol]
            val mapped=sector?.let{LargeIndexResearchPolicy.sectorIndex(it)}
            val sec=mapped?.let{indexHistory[it]?.lastOrNull()}
                ?.takeIf{it.day==day}
            return c.copy(
                companyName=instrumentNames[c.symbol].orEmpty(),
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
            val cap=capData.labels[symbol]?.category?:"UNCLASSIFIED"
            if(do3) {
                val subset=bars.filter{!it.date.isBefore(today.minusMonths(3))}
                mark(audit3,cap,"UNIVERSE")
                if(subset.size>=min3) {
                    eligible3++
                    mark(audit3,cap,"HISTORY_ELIGIBLE")
                    AdaptiveLowPatternPolicy.evaluate(symbol,3,day,subset){reason->
                        mark(audit3,cap,reason)
                        if(cap=="LARGE_CAP")large3[symbol]=reason
                    }?.let{rows3+=enrich(it);mark(audit3,cap,"MATCH");if(cap=="LARGE_CAP")large3[symbol]="MATCH"}
                } else {
                    mark(audit3,cap,"INSUFFICIENT_VALID_HISTORY")
                    if(cap=="LARGE_CAP")large3[symbol]="INSUFFICIENT_VALID_HISTORY"
                }
            }
            if(do6) {
                mark(audit6,cap,"UNIVERSE")
                if(bars.size>=min6) {
                    eligible6++
                    mark(audit6,cap,"HISTORY_ELIGIBLE")
                    AdaptiveLowPatternPolicy.evaluate(symbol,6,day,bars){reason->
                        mark(audit6,cap,reason)
                        if(cap=="LARGE_CAP")large6[symbol]=reason
                    }?.let{rows6+=enrich(it);mark(audit6,cap,"MATCH");if(cap=="LARGE_CAP")large6[symbol]="MATCH"}
                } else {
                    mark(audit6,cap,"INSUFFICIENT_VALID_HISTORY")
                    if(cap=="LARGE_CAP")large6[symbol]="INSUFFICIENT_VALID_HISTORY"
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
        fun persistCapAudit(months:Int,stats:Map<String,Map<String,Int>>,details:Map<String,String>) {
            val large=stats["LARGE_CAP"].orEmpty()
            val reasons=large.filterKeys{it !in setOf("UNIVERSE","HISTORY_ELIGIBLE","MATCH")}
                .entries.sortedByDescending{it.value}.joinToString("; "){it.key.replace('_',' ').lowercase(Locale.US)+": "+it.value}
            val missing=classifiedLarge-details.keys
            val completeDetails=details+missing.associateWith{"NO_LATEST_EOD"}
            val groups=completeDetails.entries.groupBy{it.value}.toSortedMap()
            val summary="Large-cap audit · ${latest} · ${months}M\n${large["UNIVERSE"]?:0} of ${classifiedLarge.size} classified names present; ${large["HISTORY_ELIGIBLE"]?:0} pass archive history check; ${large["MATCH"]?:0} matches. Missing latest EOD: ${missing.size}.\nFirst rejection: "+reasons.ifBlank{"none"}+"\n"+groups.entries.joinToString("\n"){(reason,entries)->reason.replace('_',' ').lowercase(Locale.US)+" ("+entries.size+"): "+entries.map{it.key}.sorted().joinToString(", ")}
            prefs.edit().putString("low_cap_audit_${months}_day",latest)
                .putString("low_cap_audit_${months}_summary",summary).putInt("low_cap_audit_${months}_version",2).apply()
            audit.logAsync("ADAPTIVE_LOWS","CAP_REJECTION_FUNNEL",mapOf(
                "day" to latest,"months" to months,"by_cap" to stats,"large_cap_by_symbol" to completeDetails,"groww_api_calls" to 0))
        }
        if(do3){adaptiveLowStore.saveDay(latest,3,exchangeUniverse,eligible3,rows3);persistCapAudit(3,audit3,large3)}
        if(do6){adaptiveLowStore.saveDay(latest,6,exchangeUniverse,eligible6,rows6);persistCapAudit(6,audit6,large6)}
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
                val csv = sourceGet(path).use { r ->
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
            val raw = sourceGet(NSE_TOTAL_MARKET_INDUSTRIES_URL).use { r ->
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
                val csv=sourceGet(url).use{resp->
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
        if (!NseEquitySessionCalendar.isFixedScanWindow(at)) return
        if(!completedSweep) {
            if(com.multify.traderpro.engine.InstitutionalHistoryPolicy.slot(at)!=null)
                audit.logAsync("INSTITUTIONAL_RESEARCH","HOURLY_SWEEP_NOT_QUALIFIED",mapOf("hour_ist" to at.hour,"reason" to "INCOMPLETE_OR_BASELINE_NOT_READY"))
            return
        }
        if(pulseQueue.trySend(Pulse(at,current.toMap(),open.toMap())).isFailure) {
            audit.logAsync("LARGE_CAP_SHADOW","PULSE_QUEUE_FULL",mapOf("hour_ist" to at.hour,"groww_api_calls" to 0,"fixed_selection_changed" to false))
        }
    }

    private fun persistFixedLivePulse(pulse:Pulse) {
        val at=pulse.at
        val current=pulse.current
        val open=pulse.open
        // Reuse the full Groww sweep BEFORE the Large-cap-only filter.
        // No new requests, no extra Alpha/Beta capacity, no execution authority.
        runCatching {
            if(institutionalStore.captureHourly(at,current,open))
                audit.logAsync("INSTITUTIONAL_RESEARCH","HOURLY_SWEEP_REUSED",mapOf(
                    "hour_ist" to at.hour,"received_at_ms" to at.toInstant().toEpochMilli(),
                    "groww_api_calls_added" to 0,"institutional_transactions_inferred" to false))
            else if(com.multify.traderpro.engine.InstitutionalHistoryPolicy.slot(at)!=null && !institutionalStore.hasTrackedSymbols())
                audit.logAsync("INSTITUTIONAL_RESEARCH","HOURLY_NO_TRACKED_DISCLOSURES",mapOf("hour_ist" to at.hour,"groww_api_calls_added" to 0))
        }
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
            .putLong("pulse_at_ms",at.toInstant().toEpochMilli()).apply()
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

    /** Export only the independent NSE/institutional research stores; never broker, Room or order state. */
    fun exportPortableResearchBackup():PortableResearchBackup=synchronized(lock) {
        val databases=linkedMapOf<String,ByteArray>()
        ResearchDatabaseBackup.exportBytes(context,db,EOD_DATABASE_NAME)?.let {
            databases[EOD_DATABASE_NAME]=it
        }
        valuationStore.exportBackupBytes()?.let { databases["equity_pe_research_v1.db"]=it }
        largeWatchStore.exportBackupBytes()?.let { databases["large_watch_learning_v1.db"]=it }
        adaptiveLowStore.exportBackupBytes()?.let { databases["adaptive_low_research_v1.db"]=it }
        val preferences=linkedMapOf(
            "eod_research_state_prefs.json" to SharedPrefsBackupCodec.encode(prefs),
            "institutional_disclosure_prefs.json" to institutionalStore.exportBackupJson()
        )
        val files=com.google.gson.JsonObject()
        databases.forEach { (name,bytes) ->
            files.add(name,com.google.gson.JsonObject().apply {
                addProperty("kind","sqlite")
                addProperty("size",bytes.size)
                addProperty("sha256",sha256(bytes))
            })
        }
        preferences.forEach { (name,value) ->
            val bytes=value.toByteArray(Charsets.UTF_8)
            files.add(name,com.google.gson.JsonObject().apply {
                addProperty("kind","preferences")
                addProperty("size",bytes.size)
                addProperty("sha256",sha256(bytes))
            })
        }
        val manifest=com.google.gson.JsonObject().apply {
            addProperty("schema","PHAYRA_RESEARCH_BACKUP_V1")
            addProperty("created_at_ms",System.currentTimeMillis())
            addProperty("scope","NSE_EOD_AND_RESEARCH_ONLY_NO_TRADING_STATE")
            add("files",files)
        }
        PortableResearchBackup(databases,preferences,com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(manifest))
    }

    /**
     * Check every manifest checksum before merging. Existing rows win; only absent research rows
     * are inserted. This never imports credentials, holdings, orders, Auto-Buy switches or GTT state.
     */
    fun restorePortableResearchBackup(
        databaseEntries:Map<String,ByteArray>,
        preferenceEntries:Map<String,String>,
        manifestJson:String?
    ):PortableResearchRestoreResult {
        if(manifestJson.isNullOrBlank())return PortableResearchRestoreResult()
        val manifest=com.google.gson.JsonParser.parseString(manifestJson).asJsonObject
        require(manifest.get("schema")?.asString=="PHAYRA_RESEARCH_BACKUP_V1") {
            "Unsupported portable research backup schema"
        }
        val files=manifest.getAsJsonObject("files")?:error("Research backup manifest has no files")
        val verifiedDatabases=linkedMapOf<String,ByteArray>()
        val verifiedPreferences=linkedMapOf<String,String>()
        for((name,metaElement) in files.entrySet()){
            val meta=metaElement.asJsonObject
            val kind=meta.get("kind").asString
            val expectedSize=meta.get("size").asLong
            val expectedHash=meta.get("sha256").asString
            when(kind){
                "sqlite"->{
                    val bytes=databaseEntries[name]?:error("Research backup is missing database "+name)
                    require(bytes.size.toLong()==expectedSize && sha256(bytes)==expectedHash) {
                        "Research database checksum mismatch: "+name
                    }
                    verifiedDatabases[name]=bytes
                }
                "preferences"->{
                    val value=preferenceEntries[name]?:error("Research backup is missing preferences "+name)
                    val bytes=value.toByteArray(Charsets.UTF_8)
                    require(bytes.size.toLong()==expectedSize && sha256(bytes)==expectedHash) {
                        "Research preference checksum mismatch: "+name
                    }
                    verifiedPreferences[name]=value
                }
                else->error("Unknown research backup entry kind")
            }
        }
        val beforeRows=countResearchRows("eod")
        val beforeSessions=countResearchRows("eod_days")
        var otherRows=0
        var databasesMerged=0
        verifiedDatabases[EOD_DATABASE_NAME]?.let {
            ResearchDatabaseBackup.mergeBytes(context,db,EOD_DATABASE_NAME,it)
            databasesMerged++
        }
        verifiedDatabases["equity_pe_research_v1.db"]?.let {
            otherRows+=valuationStore.mergeBackupBytes(it).insertedRows
            databasesMerged++
        }
        verifiedDatabases["large_watch_learning_v1.db"]?.let {
            otherRows+=largeWatchStore.mergeBackupBytes(it).insertedRows
            databasesMerged++
        }
        verifiedDatabases["adaptive_low_research_v1.db"]?.let {
            otherRows+=adaptiveLowStore.mergeBackupBytes(it).insertedRows
            databasesMerged++
        }
        var preferenceCount=0
        verifiedPreferences["eod_research_state_prefs.json"]?.let {
            preferenceCount+=SharedPrefsBackupCodec.restoreMerge(prefs,it)
        }
        verifiedPreferences["institutional_disclosure_prefs.json"]?.let {
            preferenceCount+=institutionalStore.restoreBackupJson(it)
        }
        val eodRows=(countResearchRows("eod")-beforeRows).coerceAtLeast(0)
        val eodSessions=(countResearchRows("eod_days")-beforeSessions).coerceAtLeast(0)
        audit.logAsync("MIGRATION","PORTABLE_EOD_RESEARCH_RESTORED",mapOf(
            "eod_rows_merged" to eodRows,
            "eod_sessions_merged" to eodSessions,
            "other_research_rows_merged" to otherRows,
            "preference_keys_merged" to preferenceCount,
            "databases_merged" to databasesMerged,
            "trading_state_restored" to false,
            "credentials_restored" to false,
            "broker_orders_restored" to false
        ))
        PortableResearchRestoreResult(eodRows,eodSessions,otherRows,preferenceCount,databasesMerged)
    }

    private fun countResearchRows(table:String):Int=synchronized(lock) {
        require(table in setOf("eod","eod_days"))
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM "+table,null).use {
            if(it.moveToFirst())it.getInt(0) else 0
        }
    }

    private fun sha256(bytes:ByteArray):String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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

        fun classifyCached(rows:List<AdaptiveLowCandidate>) = rows.map { row ->
            row.copy(marketCapCategory=marketCaps.labels[row.symbol]?.category ?: "UNCLASSIFIED")
        }
        val adaptive3 = if(threeMonthHistoryComplete)
            classifyCached(adaptiveLowStore.rows(3,latest)) else emptyList()
        val adaptive6 = if(spanComplete)
            classifyCached(adaptiveLowStore.rows(6,latest)) else emptyList()
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
            archiveProgress=prefs.getString("last_eod_progress","").orEmpty(),
            lastSource=prefs.getString("last_eod_source","").orEmpty(),
            lastSuccessfulImportAtMs=prefs.getLong("last_eod_success_at_ms",0L),
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
            liveLargeCapBreadthProxyPct=liveLargeBreadth,
            lowCapAudit3=prefs.getString("low_cap_audit_3_summary","").orEmpty().takeIf{prefs.getString("low_cap_audit_3_day","")==latest}.orEmpty(),
            lowCapAudit6=prefs.getString("low_cap_audit_6_summary","").orEmpty().takeIf{prefs.getString("low_cap_audit_6_day","")==latest}.orEmpty(),
            institutional=institutionalStore.snapshot().copy(
                sessions=sql.rawQuery("SELECT day FROM eod_days ORDER BY day DESC LIMIT 30",null).use{c->
                    val days=mutableListOf<String>()
                    while(c.moveToNext())days+=c.getString(0)
                    val disclosure= institutionalStore.snapshot()
                    (days+disclosure.history.map{it.day}+listOfNotNull(disclosure.bulk?.day,disclosure.block?.day))
                        .distinct().sorted().takeLast(30)
                },
                sectors=memberMap + valuation.industryBySymbol,
                swing=result.filter{it.swingResearch}.map{it.symbol}.toSet(),
                lows=(adaptive3+adaptive6).map{it.symbol}.toSet(),
                sectorMoves=indexHistories.mapNotNull{(name,series)->
                    series.lastOrNull()?.takeIf{it.day.toString()==latest}?.changePct?.let{name to it}
                }.toMap(),
                niftyEodPct=niftyToday?.changePct,contextDay=latest
            )
        )
    }
}
