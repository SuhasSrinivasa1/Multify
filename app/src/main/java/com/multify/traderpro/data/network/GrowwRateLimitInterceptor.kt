package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque
import java.util.EnumMap

internal class GrowwRateLimitInterceptor(
    private val auditLogger: AuditLogger
) : Interceptor {
    private val stateScope = CoroutineScope(Dispatchers.IO)
    private val recoveryGate: GrowwRecoveryGate get() = requireNotNull(bootstrappedGate) { "Groww recovery gate must be installed at application startup" }
    private val appPreferences: AppPreferences get() = requireNotNull(bootstrappedPreferences) { "Groww app preferences must be installed at application startup" }
    private data class Limits(val perSecond: Int, val perMinute: Int)
    private data class State(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntilMs: Long = 0L
    )
    private data class PermitResult(val waitedMs: Long, val dominantReason: String)

    private val lock = Any()
    private val lastBlockedAuditMs = EnumMap<GrowwApiUsageLedger.Category, Long>(GrowwApiUsageLedger.Category::class.java)
    private val states = EnumMap<GrowwApiUsageLedger.Category, State>(GrowwApiUsageLedger.Category::class.java).apply {
        GrowwApiUsageLedger.Category.values().forEach { put(it, State()) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val category = classify(request.method, path)
        // Block locally rather than letting each worker retry Groww during a cooldown.
        try { recoveryGate.checkRequestAllowed(category) }
        catch (blocked: GrowwRecoveryGate.LocallyBlocked) {
            auditBlocked(category, path, blocked)
            throw blocked
        }
        val permit = awaitPermit(category)
        if (permit.waitedMs >= AUDIT_WAIT_THRESHOLD_MS) {
            val row = GrowwApiUsageLedger.snapshot().categories.first { it.category == category }
            auditLogger.logAsync("API_RATE_LIMIT", "CLIENT_GOVERNOR_WAIT", mapOf(
                "method" to request.method,
                "path" to path,
                "category" to category.name,
                "wait_ms" to permit.waitedMs,
                "reason" to permit.dominantReason,
                "used_1s" to row.usedLastSecond,
                "used_60s" to row.usedLastMinute,
                "app_limit_1s" to row.appPerSecondLimit,
                "app_limit_60s" to row.appPerMinuteLimit,
                "hard_limit_1s" to row.hardPerSecondLimit,
                "hard_limit_60s" to row.hardPerMinuteLimit
            ))
        }

        try { recoveryGate.reserveRequest(category) }
        catch (blocked: GrowwRecoveryGate.LocallyBlocked) {
            auditBlocked(category, path, blocked)
            throw blocked
        }
        GrowwApiUsageLedger.recordRequest(category)
        val started = SystemClock.elapsedRealtime()
        val response = chain.proceed(request)
        val networkMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        GrowwApiUsageLedger.recordResponse(category, networkMs, response.code)
        if (response.code == 429) {
            val rawRetryAfter = response.header("Retry-After")
            val retryAfterMs = parseRetryAfterMs(rawRetryAfter)
            val appliedCooldownMs = recoveryGate.on429(category, retryAfterMs)
            synchronized(lock) {
                val state = states.getValue(category)
                state.cooldownUntilMs = maxOf(state.cooldownUntilMs, SystemClock.elapsedRealtime() + appliedCooldownMs)
            }
            val row = GrowwApiUsageLedger.snapshot().categories.first { it.category == category }
            auditLogger.logAsync("API_RATE_LIMIT", "HTTP_429", mapOf(
                "method" to request.method,
                "path" to path,
                "category" to category.name,
                "network_ms" to networkMs,
                "retry_after_raw" to rawRetryAfter,
                "retry_after_ms" to retryAfterMs,
                "applied_cooldown_ms" to appliedCooldownMs,
                "used_1s" to row.usedLastSecond,
                "used_60s" to row.usedLastMinute,
                "app_limit_1s" to row.appPerSecondLimit,
                "app_limit_60s" to row.appPerMinuteLimit,
                "hard_limit_1s" to row.hardPerSecondLimit,
                "hard_limit_60s" to row.hardPerMinuteLimit,
                "category_429_total" to row.rateLimitResponses,
                "governor_wait_count" to row.permitWaitCount,
                "governor_wait_ms_total" to row.permitWaitMs,
                "governor_wait_ms_max" to row.maxPermitWaitMs
            ))
        }
        if (response.code == 401 && category != GrowwApiUsageLedger.Category.AUTH) {
            recoveryGate.onUnauthorized()
            stateScope.launch { appPreferences.updateAuthState(authenticated = false) }
            auditLogger.logAsync("AUTH", "GROWW_RECONNECT_REQUIRED_401", mapOf(
                "category" to category.name, "path" to path,
                "note" to "Network calls paused; explicit successful authentication required"
            ))
        } else if (category == GrowwApiUsageLedger.Category.AUTH && response.isSuccessful) {
            recoveryGate.onAccessTokenSuccess()
        }
        return response
    }

    private fun auditBlocked(category: GrowwApiUsageLedger.Category, path: String, blocked: GrowwRecoveryGate.LocallyBlocked) {
        // At most one log for each category per minute even when many local callers are waiting.
        val now = SystemClock.elapsedRealtime()
        val log = synchronized(lock) {
            val previous = lastBlockedAuditMs[category] ?: 0L
            if (previous > 0L && now - previous < 60_000L) false else { lastBlockedAuditMs[category] = now; true }
        }
        if (log) auditLogger.logAsync("API_RATE_LIMIT", "LOCAL_REQUEST_SUPPRESSED", mapOf(
            "category" to category.name, "path" to path,
            "reason" to blocked.reason, "next_allowed_at_ms" to blocked.nextAllowedAtMs
        ))
    }

    private fun awaitPermit(category: GrowwApiUsageLedger.Category): PermitResult {
        val limits = limits(category)
        var totalWaitMs = 0L
        var dominantReason = "NONE"
        while (true) {
            var reason = "NONE"
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
                reason = when (wait) {
                    cooldownWait -> if (cooldownWait > 0L) "429_COOLDOWN" else "NONE"
                    minuteWait -> if (minuteWait > 0L) "APP_PER_MINUTE" else "NONE"
                    secondWait -> if (secondWait > 0L) "APP_PER_SECOND" else "NONE"
                    else -> "NONE"
                }
                if (wait <= 0L) {
                    state.second.addLast(now)
                    state.minute.addLast(now)
                    0L
                } else wait
            }
            if (waitMs <= 0L) {
                if (totalWaitMs > 0L) GrowwApiUsageLedger.recordPermitWait(category, totalWaitMs)
                return PermitResult(totalWaitMs, dominantReason)
            }
            dominantReason = reason
            val slept = waitMs.coerceAtLeast(5L)
            Thread.sleep(slept)
            totalWaitMs += slept
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

    companion object {
        private const val AUDIT_WAIT_THRESHOLD_MS = 500L
        @Volatile private var bootstrappedGate: GrowwRecoveryGate? = null
        @Volatile private var bootstrappedPreferences: AppPreferences? = null

        fun installRecovery(gate: GrowwRecoveryGate, preferences: AppPreferences) {
            bootstrappedGate = gate
            bootstrappedPreferences = preferences
        }
    }
}
