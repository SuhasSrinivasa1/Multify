package com.multify.traderpro.data.network

import com.multify.traderpro.data.logging.AuditLogger
import android.os.SystemClock
import com.multify.traderpro.engine.V9EndpointPressureWindow
import okhttp3.Interceptor
import okhttp3.Response

/** Passive Beta HTTP observer. Installed after the cache and read-only authorization checks. */
internal class BetaHttpTraceInterceptor(private val audit: AuditLogger) : Interceptor {
    private val v9EndpointPressure = V9EndpointPressureWindow()
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val kind = when {
            path.startsWith("/v1/live-data/") -> "LIVE_DATA"
            path.contains("historical", ignoreCase = true) -> "HISTORICAL"
            else -> "NON_TRADING"
        }
        val pressure = v9EndpointPressure.admitted(
            request.method, path, kind, SystemClock.elapsedRealtime()
        )
        if (pressure.emitMinuteSample) audit.logAsync(
            "V9_MASTER_API", "BETA_ENDPOINT_MINUTE_SAMPLE", mapOf(
                "path" to path, "method" to request.method,
                "kind_label_only" to kind,
                "endpoint_1s" to pressure.endpoint1s,
                "endpoint_60s" to pressure.endpoint60s,
                "category_60s" to pressure.category60s,
                "all_beta_60s" to pressure.alphaAll60s,
                "changes_throttling" to false
            )
        )
        val ticket = GrowwHttpTraceLedger.start(
            "BETA", request.method, request.url.encodedPath)
        return try {
            val response = chain.proceed(request)
            GrowwHttpTraceLedger.finish(ticket, audit, response.code, response.header("Retry-After"))
            if (response.code == 429) audit.logAsync(
                "V9_MASTER_API", "BETA_ENDPOINT_429_PRESSURE", mapOf(
                    "path" to path, "method" to request.method,
                    "endpoint_1s_at_dispatch" to pressure.endpoint1s,
                    "endpoint_60s_at_dispatch" to pressure.endpoint60s,
                    "category_60s_at_dispatch" to pressure.category60s,
                    "all_beta_60s_at_dispatch" to pressure.alphaAll60s,
                    "broker_quota_unverified" to true,
                    "no_extra_requests" to true
                )
            )
            response
        } catch (failure: Exception) {
            GrowwHttpTraceLedger.finish(ticket, audit, null, failureType = failure.javaClass.simpleName)
            throw failure
        }
    }
}
