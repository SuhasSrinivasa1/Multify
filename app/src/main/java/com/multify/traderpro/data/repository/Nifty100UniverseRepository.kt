package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.Nifty100Constituent
import com.multify.traderpro.engine.Nifty100UniverseParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Nifty100UniverseRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val audit: AuditLogger
) {
    private val prefs = context.getSharedPreferences("nifty100_universe_v9033", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(8))
        .readTimeout(Duration.ofSeconds(15))
        .build()

    data class Status(
        val count: Int,
        val updatedAtMs: Long,
        val source: String,
        val lastError: String
    )

    fun status(): Status = Status(
        count = constituents().size,
        updatedAtMs = prefs.getLong(KEY_UPDATED, 0L),
        source = prefs.getString(KEY_SOURCE, "").orEmpty(),
        lastError = prefs.getString(KEY_ERROR, "").orEmpty()
    )

    fun constituents(): List<Nifty100Constituent> {
        val packed = prefs.getString(KEY_ROWS, "").orEmpty()
        if (packed.isBlank()) return emptyList()
        val type = object : TypeToken<List<Nifty100Constituent>>() {}.type
        return runCatching { gson.fromJson<List<Nifty100Constituent>>(packed, type).orEmpty() }
            .getOrDefault(emptyList())
    }

    fun isStale(maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000): Boolean {
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        return updated <= 0L || System.currentTimeMillis() - updated > maxAgeMs ||
            constituents().size !in 80..150
    }

    suspend fun refreshIfStale(force: Boolean = false): Status = withContext(Dispatchers.IO) {
        if (!force && !isStale()) return@withContext status()
        val response = client.newCall(
            Request.Builder().url(CONSTITUENT_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/csv,text/plain,*/*")
                .header("Referer", CONSTITUENT_REFERER)
                .build()
        ).execute().use { result ->
            check(result.isSuccessful) { "Nifty 100 constituent CSV HTTP ${result.code}" }
            result.body?.string()?.takeIf { it.length in 500..500_000 }
                ?: error("Nifty 100 constituent CSV empty or outside size bounds")
        }
        val parsed = Nifty100UniverseParser.parse(StringReader(response))
        check(parsed.size in 80..150) { "Nifty 100 constituent count outside validated bounds" }
        val now = System.currentTimeMillis()
        check(prefs.edit()
            .putString(KEY_ROWS, gson.toJson(parsed))
            .putLong(KEY_UPDATED, now)
            .putString(KEY_SOURCE, "NSE_INDICES_NIFTY_100_CONSTITUENTS")
            .putString(KEY_ERROR, "")
            .commit()) { "Unable to persist Nifty 100 universe" }
        audit.logAsync("LARGE_CAP", "NIFTY100_UNIVERSE_REFRESHED", mapOf(
            "count" to parsed.size,
            "source" to "NSE_INDICES_OFFICIAL_CONSTITUENT_CSV",
            "cache_ttl_days" to 7,
            "market_hours_network_calls" to 0
        ))
        status()
    }

    suspend fun refreshSafely(): Status = runCatching { refreshIfStale() }
        .onFailure {
            prefs.edit().putString(KEY_ERROR, (it.message ?: it.javaClass.simpleName).take(180)).apply()
            audit.logAsync("LARGE_CAP", "NIFTY100_UNIVERSE_REFRESH_FAILED", mapOf(
                "error" to (it.message ?: it.javaClass.simpleName).take(180),
                "last_good_universe_preserved" to true
            ))
        }.getOrElse { status() }

    companion object {
        const val CONSTITUENT_URL = "https://www.niftyindices.com/IndexConstituent/ind_nifty100list.csv"
        const val CONSTITUENT_REFERER = "https://www.niftyindices.com/indices/equity/broad-based-indices/nifty-100"
        private const val KEY_ROWS = "rows_json"
        private const val KEY_UPDATED = "updated_at_ms"
        private const val KEY_SOURCE = "source"
        private const val KEY_ERROR = "last_error"
    }
}
