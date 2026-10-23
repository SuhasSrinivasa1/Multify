package com.multify.traderpro.data.network

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap

/**
 * Small RAM-only hot-response cache for repeated Groww market-data reads.
 * It never caches orders/auth/account state. Cache hits occur before the REST rate governor.
 */
internal class GrowwHotDataCacheInterceptor : Interceptor {
    private data class Entry(
        val storedAtMs: Long,
        val code: Int,
        val message: String,
        val mediaType: MediaType?,
        val body: ByteArray,
        val headers: okhttp3.Headers
    )

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        val path = request.url.encodedPath
        val ttlMs = ttlFor(path, request.url.queryParameter("candle_interval"))
        if (ttlMs <= 0L) return chain.proceed(request)

        val key = cacheKey(path, request.url)
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val cached = entries[key]
            if (cached != null && now - cached.storedAtMs <= ttlMs) {
                GrowwApiUsageLedger.recordCacheHit()
                return Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(cached.code)
                    .message(cached.message)
                    .headers(cached.headers)
                    .header("X-Multify-Hot-Cache", "HIT")
                    .body(cached.body.toResponseBody(cached.mediaType))
                    .build()
            }
            if (cached != null) entries.remove(key)
        }

        GrowwApiUsageLedger.recordCacheMiss()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response
        val source = response.body ?: return response
        val bytes = source.bytes()
        val mediaType = source.contentType()
        val rebuilt = response.newBuilder().body(bytes.toResponseBody(mediaType)).build()
        synchronized(lock) {
            entries[key] = Entry(
                storedAtMs = SystemClock.elapsedRealtime(),
                code = response.code,
                message = response.message,
                mediaType = mediaType,
                body = bytes,
                headers = response.headers
            )
        }
        return rebuilt
    }

    private fun ttlFor(path: String, interval: String?): Long = when {
        path == "/v1/historical/candles" && interval.equals("15minute", true) -> HISTORICAL_15M_TTL_MS
        path == "/v1/live-data/ltp" -> LTP_TTL_MS
        path == "/v1/live-data/quote" -> QUOTE_TTL_MS
        else -> 0L
    }

    private fun cacheKey(path: String, url: okhttp3.HttpUrl): String = when (path) {
        "/v1/historical/candles" -> {
            val end = normalizeEndBucket(url.queryParameter("end_time"))
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

    private fun normalizeEndBucket(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val parsed = runCatching { LocalDateTime.parse(raw, HIST_FORMAT) }.getOrNull() ?: return raw
        val minuteBucket = (parsed.minute / 15) * 15
        return parsed.withMinute(minuteBucket).withSecond(0).format(HIST_FORMAT)
    }

    companion object {
        private const val MAX_ENTRIES = 512
        private const val HISTORICAL_15M_TTL_MS = 22_000L
        private const val LTP_TTL_MS = 1_000L
        private const val QUOTE_TTL_MS = 350L
        private val HIST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
