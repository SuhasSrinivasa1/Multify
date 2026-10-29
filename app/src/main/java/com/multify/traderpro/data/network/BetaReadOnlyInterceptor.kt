package com.multify.traderpro.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Defense in depth: Beta client cannot access any trading or broker inventory endpoint. */
internal class BetaReadOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!permits(request.method, path)) throw IOException("BETA_READ_ONLY_DENIED: ${request.method} $path")
        return chain.proceed(request)
    }
    companion object {
        fun permits(method: String, path: String): Boolean =
            (method == "GET" && (path.startsWith("/v1/live-data/") ||
                path == "/v1/historical/candles" || path == "/v1/user/detail")) ||
            (method == "POST" && path == "/v1/token/api/access")
    }
}
