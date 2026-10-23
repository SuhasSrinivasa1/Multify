package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque
import java.util.EnumMap

internal class GrowwRateLimitInterceptor : Interceptor {
    private data class Limits(val perSecond: Int, val perMinute: Int)
    private data class State(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntilMs: Long = 0L
    )

    private val lock = Any()
    private val states = EnumMap<GrowwApiUsageLedger.Category, State>(GrowwApiUsageLedger.Category::class.java).apply {
        GrowwApiUsageLedger.Category.values().forEach { put(it, State()) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val category = classify(request.method, request.url.encodedPath)
        awaitPermit(category)
        val started = SystemClock.elapsedRealtime()
        val response = chain.proceed(request)
        GrowwApiUsageLedger.recordResponse(category, SystemClock.elapsedRealtime() - started, response.code)
        if (response.code == 429) {
            val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
            synchronized(lock) {
                val state = states.getValue(category)
                state.cooldownUntilMs = maxOf(
                    state.cooldownUntilMs,
                    SystemClock.elapsedRealtime() + maxOf(GrowwApiBudgetPolicy.RATE_LIMIT_COOLDOWN_MS, retryAfterMs)
                )
            }
        }
        return response
    }

    private fun awaitPermit(category: GrowwApiUsageLedger.Category) {
        val limits = limits(category)
        while (true) {
            val waitMs = synchronized(lock) {
                val now = SystemClock.elapsedRealtime()
                val state = states.getValue(category)
                prune(state.second, now - 1_000L)
                prune(state.minute, now - 60_000L)
                val cooldownWait = (state.cooldownUntilMs - now).coerceAtLeast(0L)
                val secondWait = if (state.second.size >= limits.perSecond)
                    (state.second.peekFirst() + 1_000L - now).coerceAtLeast(1L) else 0L
                val minuteWait = if (state.minute.size >= limits.perMinute)
                    (state.minute.peekFirst() + 60_000L - now).coerceAtLeast(1L) else 0L
                val wait = maxOf(cooldownWait, secondWait, minuteWait)
                if (wait <= 0L) {
                    state.second.addLast(now)
                    state.minute.addLast(now)
                    GrowwApiUsageLedger.recordRequest(category, now)
                    0L
                } else wait
            }
            if (waitMs <= 0L) return
            Thread.sleep(waitMs.coerceAtLeast(5L))
        }
    }

    private fun prune(queue: ArrayDeque<Long>, cutoff: Long) {
        while (queue.isNotEmpty() && queue.peekFirst() <= cutoff) queue.removeFirst()
    }

    private fun limits(category: GrowwApiUsageLedger.Category): Limits = when (category) {
        GrowwApiUsageLedger.Category.AUTH -> Limits(GrowwApiBudgetPolicy.APP_AUTH_PER_SECOND, GrowwApiBudgetPolicy.APP_AUTH_PER_MINUTE)
        GrowwApiUsageLedger.Category.ORDERS -> Limits(GrowwApiBudgetPolicy.APP_ORDER_PER_SECOND, GrowwApiBudgetPolicy.APP_ORDER_PER_MINUTE)
        GrowwApiUsageLedger.Category.LIVE_DATA -> Limits(GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        GrowwApiUsageLedger.Category.NON_TRADING -> Limits(GrowwApiBudgetPolicy.APP_NON_TRADING_PER_SECOND, GrowwApiBudgetPolicy.APP_NON_TRADING_PER_MINUTE)
        GrowwApiUsageLedger.Category.HISTORICAL -> Limits(GrowwApiBudgetPolicy.APP_HISTORICAL_PER_SECOND, GrowwApiBudgetPolicy.APP_HISTORICAL_PER_MINUTE)
    }

    private fun classify(method: String, path: String): GrowwApiUsageLedger.Category = when {
        path == "/v1/token/api/access" -> GrowwApiUsageLedger.Category.AUTH
        path.startsWith("/v1/live-data/") -> GrowwApiUsageLedger.Category.LIVE_DATA
        path.startsWith("/v1/historical/") -> GrowwApiUsageLedger.Category.HISTORICAL
        method in setOf("POST", "PUT", "DELETE") &&
            (path.startsWith("/v1/order/create") ||
                path.startsWith("/v1/order/cancel") ||
                path.startsWith("/v1/order-advance/create") ||
                path.startsWith("/v1/order-advance/modify") ||
                path.startsWith("/v1/order-advance/cancel")) -> GrowwApiUsageLedger.Category.ORDERS
        else -> GrowwApiUsageLedger.Category.NON_TRADING
    }

    private fun parseRetryAfterMs(raw: String?): Long {
        val seconds = raw?.trim()?.toLongOrNull() ?: return 0L
        return (seconds.coerceIn(0L, 120L) * 1_000L)
    }
}
