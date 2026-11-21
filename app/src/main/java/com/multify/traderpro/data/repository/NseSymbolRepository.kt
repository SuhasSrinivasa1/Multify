package com.multify.traderpro.data.repository

import android.content.Context
import com.multify.traderpro.engine.NseInstrumentMasterParser
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broker-independent NSE equity universe. NSE's archive may return HTTP 403 on
 * mobile networks: first obtain an NSE homepage cookie session, then use the
 * documented Groww instruments CSV as a separate official fallback.
 *
 * None of these symbols are permission to BUY: execution still requires fresh
 * Alpha quote, account cash, inventory, Fixed confirmation, and verified GTT.
 * Never overwrite the last valid master with an empty/partial/HTML response.
 */
@Singleton
class NseSymbolRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("nse_symbol_master", Context.MODE_PRIVATE)
    private val cookieLock = Any()
    private val cookies = mutableListOf<Cookie>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(12))
        .readTimeout(Duration.ofSeconds(30))
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
                synchronized(cookieLock) {
                    for (cookie in incoming) {
                        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain &&
                            it.path == cookie.path }
                        cookies.add(cookie)
                    }
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookieLock) {
                cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
                cookies.filter { it.matches(url) }
            }
        })
        .build()

    @Volatile private var cachedSymbols: Set<String>? = null
    @Volatile private var cachedInstruments: List<NseInstrumentMasterParser.ListedEquity>? = null

    data class Status(
        val count: Int,
        val updatedAtMs: Long,
        val source: String = "",
        val lastError: String = "",
        val lastAttemptAtMs: Long = 0L,
        val nsePrimaryError: String = ""
    )

    fun status(): Status = Status(
        count = prefs.getInt(KEY_COUNT, 0),
        updatedAtMs = prefs.getLong(KEY_UPDATED, 0L),
        source = prefs.getString(KEY_SOURCE, "").orEmpty(),
        lastError = prefs.getString(KEY_ERROR, "").orEmpty(),
        lastAttemptAtMs = prefs.getLong(KEY_ATTEMPT, 0L),
        nsePrimaryError = prefs.getString(KEY_NSE_ERROR, "").orEmpty()
    )

    private fun symbolSet(): Set<String> {
        cachedSymbols?.let { return it }
        val packed = prefs.getString(KEY_SYMBOLS, "").orEmpty()
        val loaded = if (packed.isBlank()) emptySet() else packed.split('|')
            .asSequence().filter { it.isNotBlank() }.toSet()
        cachedSymbols = loaded
        return loaded
    }

    fun symbols(): List<String> = symbolSet().toList()

    /** Fast, wholly local autocomplete: never fetch a quote while the user types. */
    fun instruments(): List<NseInstrumentMasterParser.ListedEquity> {
        cachedInstruments?.let { return it }
        val packed = prefs.getString(KEY_INSTRUMENTS, "").orEmpty()
        val loaded = if (packed.isBlank()) symbols().map {
            NseInstrumentMasterParser.ListedEquity(it, it)
        } else packed.split('|').mapNotNull { row ->
            val divider = row.indexOf('\t')
            if (divider < 1) return@mapNotNull null
            val symbol = NseInstrumentMasterParser.validSymbol(row.substring(0, divider))
                ?: return@mapNotNull null
            NseInstrumentMasterParser.ListedEquity(symbol, row.substring(divider + 1).ifBlank { symbol })
        }
        cachedInstruments = loaded
        return loaded
    }

    fun search(query: String): List<NseInstrumentMasterParser.ListedEquity> {
        val q = query.trim().uppercase(Locale.US)
        if (q.isBlank()) return emptyList()
        return instruments().asSequence().filter {
            it.symbol.contains(q, ignoreCase = true) ||
                it.companyName.contains(q, ignoreCase = true) ||
                ((q == "TATA" || q == "TATA GROUP") && it.symbol in TATA_GROUP_ALIASES)
        }.sortedWith(compareBy<NseInstrumentMasterParser.ListedEquity> {
            if (it.symbol.startsWith(q, ignoreCase = true)) 0
            else if (it.companyName.startsWith(q, ignoreCase = true)) 1
            else 2
        }.thenBy { it.companyName }).toList()
    }

    fun hasFullInstrumentMaster(): Boolean =
        status().count > MIN_SYMBOL_COUNT && !isStale() &&
            instruments().size > MIN_SYMBOL_COUNT

    fun isKnown(symbol: String): Boolean {
        val symbols = symbolSet()
        if (symbols.isEmpty()) return true
        return symbol.trim().uppercase(Locale.US) in symbols
    }

    fun isStale(maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000): Boolean {
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        return updated == 0L || System.currentTimeMillis() - updated > maxAgeMs ||
            prefs.getString(KEY_INSTRUMENTS, "").isNullOrBlank()
    }

    private fun request(url: String, csv: Boolean): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", if (csv) "text/csv,text/plain,*/*" else
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
        // Do not send an NSE referrer to Groww's independent official CDN.
        if (url.contains("nseindia.com")) builder.header("Referer", NSE_HOME_URL)
        return builder.build()
    }

    private fun nseSymbols(): List<NseInstrumentMasterParser.ListedEquity> {
        // Warm the in-memory cookie jar before reaching the separate NSE archive host.
        client.newCall(request(NSE_HOME_URL, csv = false)).execute().use { response ->
            check(response.isSuccessful) { "NSE homepage HTTP ${response.code}" }
        }
        return client.newCall(request(EQUITY_URL, csv = true)).execute().use { response ->
            check(response.isSuccessful) { "NSE equity CSV HTTP ${response.code}" }
            val stream = response.body?.charStream() ?: error("NSE equity CSV response empty")
            NseInstrumentMasterParser.parseNseInstruments(stream)
        }
    }

    private fun growwSymbols(): List<NseInstrumentMasterParser.ListedEquity> =
        client.newCall(request(GROWW_INSTRUMENT_URL, csv = true)).execute().use { response ->
            check(response.isSuccessful) { "Groww instrument CSV HTTP ${response.code}" }
            val stream = response.body?.charStream() ?: error("Groww instrument CSV response empty")
            NseInstrumentMasterParser.parseGrowwInstruments(stream)
        }

    /** Download once per scheduled sync, with bounded official fallback, no 403 retry storm. */
    @Synchronized
    fun refresh(): Status {
        val attemptedAt = System.currentTimeMillis()
        prefs.edit().putLong(KEY_ATTEMPT, attemptedAt).apply()
        var nseFailure = ""
        try {
            val primary = runCatching { nseSymbols() }
            val symbols: List<NseInstrumentMasterParser.ListedEquity>
            val source: String
            if (primary.isSuccess && primary.getOrThrow().size > MIN_SYMBOL_COUNT) {
                symbols = primary.getOrThrow()
                source = "NSE_OFFICIAL_CSV"
            } else {
                nseFailure = primary.exceptionOrNull()?.message?.take(180)
                    ?: "NSE CSV below ${MIN_SYMBOL_COUNT + 1} validated symbols"
                symbols = growwSymbols()
                source = "GROWW_OFFICIAL_NSE_CASH"
            }
            check(symbols.size > MIN_SYMBOL_COUNT && symbols.size < MAX_SYMBOL_COUNT) {
                "Instrument master candidate count ${symbols.size} outside safe validated range"
            }
            val now = System.currentTimeMillis()
            check(prefs.edit()
                .putString(KEY_SYMBOLS, symbols.joinToString("|") { it.symbol })
                .putString(KEY_INSTRUMENTS, symbols.joinToString("|") { "${it.symbol}\t${it.companyName}" })
                .putInt(KEY_COUNT, symbols.size)
                .putLong(KEY_UPDATED, now)
                .putString(KEY_SOURCE, source)
                .putString(KEY_ERROR, "")
                .putString(KEY_NSE_ERROR, nseFailure)
                .putLong(KEY_ATTEMPT, attemptedAt)
                .commit()) { "Unable to persist validated symbol master; previous snapshot preserved" }
            cachedSymbols = symbols.map { it.symbol }.toSet()
            cachedInstruments = symbols
            return status()
        } catch (error: Exception) {
            // Retain last-known-good data and timestamp. Do not accept partial universes.
            prefs.edit().putString(KEY_ERROR, (error.message ?: error.javaClass.simpleName).take(180))
                .putString(KEY_NSE_ERROR, nseFailure)
                .putLong(KEY_ATTEMPT, attemptedAt).apply()
            throw error
        }
    }

    companion object {
        // Corporate-family aliases make searches such as "Tata" include listed
        // NSE affiliates whose legal names omit "Tata" (Titan, Trent, Voltas,
        // Indian Hotels, Rallis, etc.). Intersect with the live NSE master;
        // aliases are display-only and never authorize an unlisted BUY.
        // Membership is curated from Tata's public company/stock-data pages;
        // names/tickers still refresh weekly from official instrument files.
        private val TATA_GROUP_ALIASES = setOf(
            "TCS", "TATAMOTORS", "TMPV", "TMLCV", "TATASTEEL", "TATAPOWER",
            "TATACHEM", "TATACOMM", "TATACONSUM", "TATAELXSI", "TATATECH",
            "TATAINVEST", "TATACAP", "TTML", "TITAN", "TRENT", "VOLTAS",
            "INDHOTEL", "RALLIS", "TEJASNET", "NELCO", "TRF", "ASAL",
            "ARTSONENG", "TAYO", "TATACOFFEE", "TATAMETALI"
        )
        const val EQUITY_URL = "https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv"
        const val NSE_HOME_URL = "https://www.nseindia.com/"
        const val GROWW_INSTRUMENT_URL = "https://growwapi-assets.groww.in/instruments/instrument.csv"
        const val MIN_SYMBOL_COUNT = 500
        const val MAX_SYMBOL_COUNT = 10_000
        private const val KEY_SYMBOLS = "symbols"
        private const val KEY_INSTRUMENTS = "instruments_with_company_names_v9028"
        private const val KEY_COUNT = "count"
        private const val KEY_UPDATED = "updated_at"
        private const val KEY_SOURCE = "source"
        private const val KEY_ERROR = "last_error"
        private const val KEY_ATTEMPT = "last_attempt_at"
        private const val KEY_NSE_ERROR = "last_nse_primary_error"
    }
}
