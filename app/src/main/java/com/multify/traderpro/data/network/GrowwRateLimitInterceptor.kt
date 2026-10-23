package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque
import java.util.EnumMap

internal class GrowwRateLimitInterceptor : Interceptor {
    private enum class Bucket { AUTH, ORDERS, LIVE_DATA, NON_TRADING, HISTORICAL }
    private data class Limits(val perSecond: Int, val perMinute: Int)
    private data class State(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntilMs: Long = 0L
    )

    private val lock = Any()
    private val states = EnumMap<Bucket, State>(Bucket::class.java).apply {
        Bucket.values().forEach { put(it, State()) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bucket = classify(request.method, request.url.encodedPath)
        awaitPermit(bucket)
        val response = chain.proceed(request)
        if (response.code == 429) {
            synchronized(lock) {
                val state = states.getValue(bucket)
                state.cooldownUntilMs = maxOf(
                    state.cooldownUntilMs,
                    SystemClock.elapsedRealtime() + GrowwApiBudgetPolicy.RATE_LIMIT_COOLDOWN_MS
                )
            }
        }
        return response
    }

    private fun awaitPermit(bucket: Bucket) {
        val limits = limits(bucket)
        while (true) {
            val waitMs = synchronized(lock) {
                val now = SystemClock.elapsedRealtime()
                val state = states.getValue(bucket)
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

    private fun limits(bucket: Bucket): Limits = when (bucket) {
        Bucket.AUTH -> Limits(GrowwApiBudgetPolicy.APP_AUTH_PER_SECOND, GrowwApiBudgetPolicy.APP_AUTH_PER_MINUTE)
        Bucket.ORDERS -> Limits(GrowwApiBudgetPolicy.APP_ORDER_PER_SECOND, GrowwApiBudgetPolicy.APP_ORDER_PER_MINUTE)
        Bucket.LIVE_DATA -> Limits(GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        Bucket.NON_TRADING -> Limits(GrowwApiBudgetPolicy.APP_NON_TRADING_PER_SECOND, GrowwApiBudgetPolicy.APP_NON_TRADING_PER_MINUTE)
        Bucket.HISTORICAL -> Limits(GrowwApiBudgetPolicy.APP_HISTORICAL_PER_SECOND, GrowwApiBudgetPolicy.APP_HISTORICAL_PER_MINUTE)
    }

    private fun classify(method: String, path: String): Bucket = when {
        path == "/v1/token/api/access" -> Bucket.AUTH
        path.startsWith("/v1/live-data/") -> Bucket.LIVE_DATA
        path.startsWith("/v1/historical/") -> Bucket.HISTORICAL
        method in setOf("POST", "PUT", "DELETE") &&
            (path.startsWith("/v1/order/create") ||
                path.startsWith("/v1/order/cancel") ||
                path.startsWith("/v1/order-advance/create") ||
                path.startsWith("/v1/order-advance/modify") ||
                path.startsWith("/v1/order-advance/cancel")) -> Bucket.ORDERS
        else -> Bucket.NON_TRADING
    }
}
