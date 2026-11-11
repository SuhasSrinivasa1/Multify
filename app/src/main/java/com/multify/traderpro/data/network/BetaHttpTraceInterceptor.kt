package com.multify.traderpro.data.network

import com.multify.traderpro.data.logging.AuditLogger
import okhttp3.Interceptor
import okhttp3.Response

/** Passive Beta HTTP observer. Installed after the cache and read-only authorization checks. */
internal class BetaHttpTraceInterceptor(private val audit: AuditLogger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val ticket = GrowwHttpTraceLedger.start(
            "BETA", request.method, request.url.encodedPath, audit)
        return try {
            val response = chain.proceed(request)
            GrowwHttpTraceLedger.finish(ticket, audit, response.code, response.header("Retry-After"))
            response
        } catch (failure: Exception) {
            GrowwHttpTraceLedger.finish(ticket, audit, null, failureType = failure.javaClass.simpleName)
            throw failure
        }
    }
}
