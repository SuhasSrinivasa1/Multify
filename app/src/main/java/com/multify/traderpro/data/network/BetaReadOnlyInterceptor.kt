package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.data.logging.AuditLogger
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Defense in depth: Beta client cannot access any trading or broker inventory endpoint. */
internal class BetaReadOnlyInterceptor(
    private val auditLogger: AuditLogger
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!permits(request.method, path)) throw IOException("BETA_READ_ONLY_DENIED: ${request.method} $path")
        val started = SystemClock.elapsedRealtime()
        val response = chain.proceed(request)
        val elapsedMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        if (response.code == 429) {
            auditLogger.logAsync("BETA_API", "HTTP_429", mapOf(
                "method" to request.method,
                "path" to path,
                "network_ms" to elapsedMs,
                "retry_after_raw" to response.header("Retry-After"),
                "account" to "BETA",
                "read_only" to true
            ))
        }
        return response
    }

    companion object {
        fun permits(method: String, path: String): Boolean =
            (method == "GET" && (path.startsWith("/v1/live-data/") ||
                path == "/v1/historical/candles" || path == "/v1/user/detail")) ||
            (method == "POST" && path == "/v1/token/api/access")
    }
}
