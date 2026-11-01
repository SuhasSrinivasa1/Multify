package com.multify.traderpro.data.repository

import com.google.gson.JsonParser
import com.multify.traderpro.data.logging.AuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** NSE-only public quote enrichment for news. Never sends Groww tokens or places orders.
 * Missing / inaccessible public data remains null; no estimated market cap or BSE fallback.
 */
@Singleton
class NseMarketCapResearch @Inject constructor(private val audit: AuditLogger) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(4)).build()
    private data class Entry(val attemptedAtMs: Long, val cap: Double?)
    private val cache = ConcurrentHashMap<String, Entry>()

    suspend fun marketCapRupees(symbol: String): Double? = withContext(Dispatchers.IO) {
        if (!symbol.matches(Regex("[A-Z0-9&._-]{1,32}"))) return@withContext null
        val now = System.currentTimeMillis()
        cache[symbol]?.takeIf { now - it.attemptedAtMs < 6L * 60 * 60_000L }
            ?.let { return@withContext it.cap }
        val cap = runCatching {
            val request = Request.Builder()
                .url("https://www.nseindia.com/api/quote-equity?symbol=$symbol")
                .header("Accept", "application/json")
                .header("Referer", "https://www.nseindia.com/")
                .header("User-Agent", "Mozilla/5.0 (compatible; MultifyForecastNews/1.0)")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP_${response.code}")
                val body = response.body?.string().orEmpty()
                if (body.length !in 10..300_000) error("INVALID_RESPONSE_SIZE")
                parseOfficialMarketCap(body)
            }
        }.onFailure { cause ->
            audit.logAsync("FORECAST_NEWS", "NSE_MARKET_CAP_SOURCE_FAILURE", mapOf(
                "symbol" to symbol, "source" to "NSE_OFFICIAL_QUOTE_EQUITY",
                "error" to (cause.message ?: cause.javaClass.simpleName).take(90),
                "market_cap_not_guessed" to true))
        }.getOrNull()
        cache[symbol] = Entry(now, cap)
        audit.logAsync("FORECAST_NEWS", "NSE_MARKET_CAP_LOOKUP", mapOf(
            "symbol" to symbol, "source" to "NSE_OFFICIAL_QUOTE_EQUITY",
            "market_cap_rupees" to cap, "found" to (cap != null),
            "cache_ttl_hours" to 6, "bse_requests" to 0))
        cap
    }

    internal fun parseOfficialMarketCap(body: String): Double? = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        val trade = root.getAsJsonObject("marketDeptOrderBook")?.getAsJsonObject("tradeInfo")
            ?: root.getAsJsonObject("tradeInfo")
        val number = trade?.get("totalMarketCap")?.takeUnless { it.isJsonNull }?.asDouble
        number?.takeIf { it.isFinite() && it in 100_000_000.0..100_000_000_000_000_000.0 }
    }.getOrNull()
}
