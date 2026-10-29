package com.multify.traderpro.data.network

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Small RAM-only hot-response cache for repeated Groww market-data reads.
 * It never caches orders/auth/account state. Cache hits occur before the REST rate governor.
 */
internal class GrowwHotDataCacheInterceptor(
    private val account: String = "ALPHA",
    private val recordAlphaStats: Boolean = true,
    private val elapsedMs: () -> Long = { SystemClock.elapsedRealtime() }
) : Interceptor {
    private data class Entry(
        val storedAtMs: Long,
        val code: Int,
        val message: String,
        val mediaType: MediaType?,
        val body: ByteArray,
        val headers: okhttp3.Headers
    )

    private data class InFlight(val completed: CountDownLatch = CountDownLatch(1),
                                @Volatile var outcome: Entry? = null)
    private val lock = Any()
    private val pending = mutableMapOf<String, InFlight>()
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        val path = request.url.encodedPath
        val ttlMs = ttlFor(path, request.url.queryParameter("candle_interval"))
        if (ttlMs <= 0L) return chain.proceed(request)

        // Scope by both logical account AND current authorization. A token rotation must
        // never allow a fresh account to consume a previous token's cached market snapshot.
        val key = keyFor(request)
        val now = elapsedMs()
        var leader = false
        val pendingRequest: InFlight
        synchronized(lock) {
            val cached = entries[key]
            if (cached != null && now - cached.storedAtMs <= ttlMs) {
                if (recordAlphaStats) GrowwApiUsageLedger.recordCacheHit()
                return replay(request, cached, "HIT")
            }
            if (cached != null) entries.remove(key)
            val existing = pending[key]
            if (existing != null) pendingRequest = existing
            else {
                pendingRequest = InFlight()
                pending[key] = pendingRequest
                leader = true
            }
        }

        if (!leader) {
            // Identical in-flight reads share one immutable body. Do not coalesce trading,
            // account, authentication, or non-GET endpoints; only paths with explicit TTL.
            if (pendingRequest.completed.await(SINGLE_FLIGHT_WAIT_MS, TimeUnit.MILLISECONDS)) {
                val result = pendingRequest.outcome
                if (result != null && elapsedMs() - result.storedAtMs <= ttlMs) {
                    if (recordAlphaStats) GrowwApiUsageLedger.recordCacheHit()
                    return replay(request, result, "SINGLE_FLIGHT")
                }
            }
            // Timed-out or failed leader: use normal broker/recovery rules for fallback.
            if (recordAlphaStats) GrowwApiUsageLedger.recordCacheMiss()
            return chain.proceed(request)
        }

        if (recordAlphaStats) GrowwApiUsageLedger.recordCacheMiss()
        try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) return response
            val source = response.body ?: return response
            val bytes = source.bytes()
            val mediaType = source.contentType()
            val rebuilt = response.newBuilder().body(bytes.toResponseBody(mediaType)).build()
            val entry = Entry(elapsedMs(), response.code, response.message,
                mediaType, bytes, response.headers)
            synchronized(lock) { entries[key] = entry }
            pendingRequest.outcome = entry
            return rebuilt
        } finally {
            // Always release followers, even after a timeout/429/network failure.
            synchronized(lock) {
                if (pending[key] === pendingRequest) pending.remove(key)
            }
            pendingRequest.completed.countDown()
        }
    }

    /** Fast-path used before Alpha's coroutine governor: preserve cache-hit latency and
     * never reserve broker permits for snapshots already held in this account's RAM. */
    internal fun freshResponse(request: okhttp3.Request): Response? {
        if (request.method != "GET") return null
        val ttl = ttlFor(request.url.encodedPath, request.url.queryParameter("candle_interval"))
        if (ttl <= 0L) return null
        val key = keyFor(request)
        val entry = synchronized(lock) {
            entries[key]?.takeIf { elapsedMs() - it.storedAtMs <= ttl }
        } ?: return null
        if (recordAlphaStats) GrowwApiUsageLedger.recordCacheHit()
        return replay(request, entry, "HIT")
    }

    private fun keyFor(request: okhttp3.Request): String {
        val auth = request.header("Authorization").orEmpty()
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(auth.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        return "$account|$fingerprint|${cacheKey(request.url.encodedPath, request.url)}"
    }

    private fun replay(request: okhttp3.Request, entry: Entry, mode: String): Response =
        Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(entry.code).message(entry.message).headers(entry.headers)
            .header("X-Multify-Hot-Cache", mode)
            .body(entry.body.toResponseBody(entry.mediaType)).build()

    private fun ttlFor(path: String, interval: String?): Long = when {
        path == "/v1/historical/candles" && interval.equals("5minute", true) -> HISTORICAL_5M_TTL_MS
        path == "/v1/historical/candles" && interval.equals("15minute", true) -> HISTORICAL_15M_TTL_MS
        path == "/v1/live-data/ltp" -> LTP_TTL_MS
        path == "/v1/live-data/quote" -> QUOTE_TTL_MS
        else -> 0L
    }

    private fun cacheKey(path: String, url: okhttp3.HttpUrl): String = when (path) {
        "/v1/historical/candles" -> {
            val end = normalizeEndBucket(url.queryParameter("end_time"), url.queryParameter("candle_interval"))
            listOf(
                path,
                url.queryParameter("exchange"),
                url.queryParameter("segment"),
                url.queryParameter("groww_symbol"),
                url.queryParameter("start_time"),
                end,
                url.queryParameter("candle_interval")
            ).joinToString("|")
        }
        else -> path + "?" + (url.encodedQuery ?: "")
    }

    private fun normalizeEndBucket(raw: String?, interval: String?): String {
        if (raw.isNullOrBlank()) return ""
        val parsed = runCatching { LocalDateTime.parse(raw, HIST_FORMAT) }.getOrNull() ?: return raw
        val size = if (interval.equals("5minute", true)) 5 else 15
        val minuteBucket = (parsed.minute / size) * size
        return parsed.withMinute(minuteBucket).withSecond(0).format(HIST_FORMAT)
    }

    companion object {
        private const val SINGLE_FLIGHT_WAIT_MS = 8_000L
        private const val MAX_ENTRIES = 512
        private const val HISTORICAL_15M_TTL_MS = 22_000L
        private const val HISTORICAL_5M_TTL_MS = 15_000L
        private const val LTP_TTL_MS = 1_000L
        private const val QUOTE_TTL_MS = 350L
        private val HIST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
