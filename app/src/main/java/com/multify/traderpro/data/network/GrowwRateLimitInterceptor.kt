package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.Request
import java.io.IOException
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import com.multify.traderpro.engine.MarketDataPriorityAdmissionPolicy
import com.multify.traderpro.engine.GrowwRetryAfterPolicy
import com.multify.traderpro.engine.AlphaLtpPacingPolicy
import com.multify.traderpro.engine.LtpAdaptivePacingPolicy
import com.multify.traderpro.engine.AlphaReadPacingPolicy
import com.multify.traderpro.engine.FixedResearchLtpPacingPolicy
import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicInteger

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
    /** An Alpha request granted a coroutine permit before occupying an OkHttp dispatcher thread. */
    internal class PreAdmitted

    private val lock = Any()
    private val v9EndpointPressure = com.multify.traderpro.engine.V9EndpointPressureWindow()
    // Local per-endpoint Alpha /ltp pacing: shared generic LIVE_DATA budget alone
    // did not prevent HTTP 429 at 2 requests/second and 18/minute in the broker logs.
    // This never changes Fixed scoring, SELL stop triggers or order dispatch.
    private val alphaLtpMinute = ArrayDeque<Long>()
    private val pendingProtectionLtps = AtomicInteger(0)
    private val readLastGrantedMs = EnumMap<AlphaReadPacingPolicy.Lane, Long>(AlphaReadPacingPolicy.Lane::class.java)
    private val readPace = EnumMap<AlphaReadPacingPolicy.Lane, AlphaReadPacingPolicy.State>(AlphaReadPacingPolicy.Lane::class.java).apply {
        AlphaReadPacingPolicy.Lane.values().forEach { lane ->
            put(lane, AlphaReadPacingPolicy.State(AlphaReadPacingPolicy.startGapMs(lane), SystemClock.elapsedRealtime()))
        }
    }
    private var lastAlphaLtpGrantedAtMs = 0L
    private var alphaLtpPace = LtpAdaptivePacingPolicy.State(LtpAdaptivePacingPolicy.START_GAP_MS, SystemClock.elapsedRealtime())
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
        // Fail closed if somebody bypasses the asynchronous Alpha Call.Factory. This ensures
        // every outbound request passes the same rate and recovery gates, with no Thread.sleep
        // occupying OkHttp's dispatcher while waiting for permits.
        if (request.tag(PreAdmitted::class.java) == null) {
            throw IOException("Alpha call missing asynchronous API admission")
        }

        try { recoveryGate.reserveRequest(category) }
        catch (blocked: GrowwRecoveryGate.LocallyBlocked) {
            auditBlocked(category, path, blocked)
            throw blocked
        }
        GrowwApiUsageLedger.recordRequest(category)
        val started = SystemClock.elapsedRealtime()
        val requestStartedAtMs = System.currentTimeMillis()
        val endpointPressure = v9EndpointPressure.admitted(
            request.method, path, category.name, started
        )
        if (endpointPressure.emitMinuteSample) auditLogger.logAsync(
            "V9_MASTER_API", "ALPHA_ENDPOINT_MINUTE_SAMPLE", mapOf(
                "path" to path, "method" to request.method,
                "category" to category.name, "endpoint_1s" to endpointPressure.endpoint1s,
                "endpoint_60s" to endpointPressure.endpoint60s,
                "category_1s" to endpointPressure.category1s,
                "category_60s" to endpointPressure.category60s,
                "all_alpha_60s" to endpointPressure.alphaAll60s,
                "counts_admitted_http_not_governor_waiters" to true,
                "changes_throttling" to false
            )
        )
        val trace = GrowwHttpTraceLedger.start("ALPHA", request.method, path)
        // Request priority is internal scheduler metadata, never a Groww API header.
        val response = try {
            chain.proceed(request.newBuilder()
                .removeHeader(MarketDataPriorityAdmissionPolicy.HEADER).build())
        } catch (failure: Exception) {
            GrowwHttpTraceLedger.finish(trace, auditLogger, null,
                failureType = failure.javaClass.simpleName)
            throw failure
        }
        GrowwHttpTraceLedger.finish(trace, auditLogger, response.code, response.header("Retry-After"))
        val networkMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        if (path == "/v1/live-data/quote" &&
            request.url.queryParameter("exchange").equals("NSE", ignoreCase = true) &&
            request.url.queryParameter("segment").equals("CASH", ignoreCase = true)) {
            // Broker payload exposes no provider source timestamp. This records only
            // the precise client HTTP request/response timing for shadow diagnostics.
            FixedQuoteTimingRegistry.record(
                request.url.queryParameter("trading_symbol"),
                requestStartedAtMs, System.currentTimeMillis(), networkMs, response.code
            )
        }
        GrowwApiUsageLedger.recordResponse(category, networkMs, response.code)
        if (response.code == 429) {
            val rawRetryAfter = response.header("Retry-After")
            val retryAfterMs = GrowwRetryAfterPolicy.retryAfterMs(rawRetryAfter, System.currentTimeMillis())
            val appliedCooldownMs = recoveryGate.on429(category, retryAfterMs)
            synchronized(lock) {
                val state = states.getValue(category)
                state.cooldownUntilMs = maxOf(state.cooldownUntilMs, SystemClock.elapsedRealtime() + appliedCooldownMs)
                if (path == "/v1/live-data/ltp")
                    alphaLtpPace = LtpAdaptivePacingPolicy.on429(alphaLtpPace, SystemClock.elapsedRealtime())
                AlphaReadPacingPolicy.lane(request.method, path)?.let { lane ->
                    readPace[lane] = AlphaReadPacingPolicy.on429(
                        lane, readPace.getValue(lane), SystemClock.elapsedRealtime())
                }
            }
            val row = GrowwApiUsageLedger.snapshot().categories.first { it.category == category }
            auditLogger.logAsync("API_RATE_LIMIT", "HTTP_429", mapOf(
                "method" to request.method,
                "path" to path,
                "account" to "ALPHA",
                "beta_request_counters_excluded" to true,
                "separate_broker_accounts" to true,
                "alpha_cooldown_does_not_block_beta" to true,
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
                "v9_endpoint_1s_at_dispatch" to endpointPressure.endpoint1s,
                "v9_endpoint_60s_at_dispatch" to endpointPressure.endpoint60s,
                "v9_category_1s_at_dispatch" to endpointPressure.category1s,
                "v9_category_60s_at_dispatch" to endpointPressure.category60s,
                "v9_all_alpha_60s_at_dispatch" to endpointPressure.alphaAll60s,
                "v9_not_a_verified_broker_quota" to true,
                "category_429_total" to row.rateLimitResponses,
                "governor_wait_count" to row.permitWaitCount,
                "governor_wait_ms_total" to row.permitWaitMs,
                "governor_wait_ms_max" to row.maxPermitWaitMs
            ))
        }
        if (response.isSuccessful) synchronized(lock) {
            if (path == "/v1/live-data/ltp")
                alphaLtpPace = LtpAdaptivePacingPolicy.onSuccess(alphaLtpPace, SystemClock.elapsedRealtime())
            AlphaReadPacingPolicy.lane(request.method, path)?.let { lane ->
                readPace[lane] = AlphaReadPacingPolicy.onSuccess(
                    lane, readPace.getValue(lane), SystemClock.elapsedRealtime())
            }
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
        if (log) {
            auditLogger.logAsync("API_RATE_LIMIT", "LOCAL_REQUEST_SUPPRESSED", mapOf(
                "account" to "ALPHA",
                "category" to category.name, "path" to path,
                "reason" to blocked.reason, "next_allowed_at_ms" to blocked.nextAllowedAtMs,
                "actual_http_dispatched" to false,
                "broker_rejection" to false
            ))
            GrowwHttpTraceLedger.localBlock(auditLogger, "ALPHA", category.name, path, blocked.reason)
        }
    }

    /** Reserve the exact same per-second/per-minute budget *before* network dispatch. */
    internal suspend fun acquirePermit(request: Request) {
        val path = request.url.encodedPath
        val category = classify(request.method, path)
        val protection = category == GrowwApiUsageLedger.Category.LIVE_DATA &&
            MarketDataPriorityAdmissionPolicy.isProtectionHeader(
                request.header(MarketDataPriorityAdmissionPolicy.HEADER))
        val protectionLtp = protection && path == "/v1/live-data/ltp"
        val readLane = AlphaReadPacingPolicy.lane(request.method, path)
        if (protectionLtp) pendingProtectionLtps.incrementAndGet()
        var totalWaitMs = 0L
        var dominantReason = "NONE"
        try {
        while (true) {
            try { recoveryGate.checkRequestAllowed(category) }
            catch (blocked: GrowwRecoveryGate.LocallyBlocked) {
                auditBlocked(category, path, blocked)
                throw blocked
            }
            var reason = "NONE"
            val waitMs = synchronized(lock) {
                val now = SystemClock.elapsedRealtime()
                val state = states.getValue(category)
                // Centralized quota: protect one live request/second and sixty/minute
                // for existing holdings. Ordinary Fixed/Forecast research uses only
                // the residual allowance; protection still obeys Groww's hard budget.
                val limits = if (category == GrowwApiUsageLedger.Category.LIVE_DATA) {
                    val admission = MarketDataPriorityAdmissionPolicy.allowedLiveTraffic(
                        MarketDataPriorityAdmissionPolicy.hasManagedHoldings, protection)
                    Limits(admission.perSecond, admission.perMinute)
                } else limits(category)
                prune(state.second, now - 1_000L)
                prune(state.minute, now - 60_000L)
                val cooldownWait = (state.cooldownUntilMs - now).coerceAtLeast(0L)
                val secondWait = if (state.second.size >= limits.perSecond)
                    (state.second.peekFirst() + 1_000L - now).coerceAtLeast(1L) else 0L
                val minuteWait = if (state.minute.size >= limits.perMinute)
                    (state.minute.peekFirst() + 60_000L - now).coerceAtLeast(1L) else 0L
                val isAlphaLtp = path == "/v1/live-data/ltp"
                if (isAlphaLtp) prune(alphaLtpMinute, now - 60_000L)
                val ltpWait = if (isAlphaLtp) AlphaLtpPacingPolicy.waitMs(
                    now, lastAlphaLtpGrantedAtMs,
                    alphaLtpMinute.peekFirst(), alphaLtpMinute.size,
                    FixedResearchLtpPacingPolicy.effectiveGapMs(
                        alphaLtpPace.gapMs, protectionLtp)
                ) else 0L
                val readWait = readLane?.let { lane ->
                    AlphaReadPacingPolicy.waitMs(
                        now, readLastGrantedMs[lane] ?: 0L, readPace.getValue(lane).gapMs)
                } ?: 0L
                val protectionYield = MarketDataPriorityAdmissionPolicy.ltpPriorityYieldMs(
                    isAlphaLtp, protection, pendingProtectionLtps.get())
                val wait = maxOf(cooldownWait, secondWait, minuteWait, ltpWait, readWait, protectionYield)
                reason = when {
                    protectionYield > 0L && wait == protectionYield -> "YIELD_TO_HOLDING_PROTECTION"
                    cooldownWait > 0L && wait == cooldownWait -> "429_COOLDOWN"
                    ltpWait > 0L && wait == ltpWait -> "ALPHA_LTP_ENDPOINT_PACING"
                    readWait > 0L && wait == readWait -> when (readLane) {
                        AlphaReadPacingPolicy.Lane.QUOTE -> "ALPHA_QUOTE_ENDPOINT_PACING"
                        AlphaReadPacingPolicy.Lane.ACCOUNT_READ -> "ALPHA_ACCOUNT_READ_PACING"
                        null -> "NONE"
                    }
                    minuteWait > 0L && wait == minuteWait -> "APP_PER_MINUTE"
                    secondWait > 0L && wait == secondWait -> "APP_PER_SECOND"
                    else -> "NONE"
                }
                if (wait <= 0L) {
                    state.second.addLast(now)
                    state.minute.addLast(now)
                    if (isAlphaLtp) {
                        alphaLtpMinute.addLast(now)
                        lastAlphaLtpGrantedAtMs = now
                    }
                    readLane?.let { lane -> readLastGrantedMs[lane] = now }
                    0L
                } else wait
            }
            if (waitMs <= 0L) {
                if (totalWaitMs > 0L) {
                    GrowwApiUsageLedger.recordPermitWait(category, totalWaitMs)
                    if (totalWaitMs >= AUDIT_WAIT_THRESHOLD_MS) {
                        val row = GrowwApiUsageLedger.snapshot().categories.first { it.category == category }
                        auditLogger.logAsync("API_RATE_LIMIT", "CLIENT_GOVERNOR_WAIT", mapOf(
                            "method" to request.method, "path" to path,
                            "category" to category.name, "wait_ms" to totalWaitMs,
                            "reason" to dominantReason, "used_1s" to row.usedLastSecond,
                            "used_60s" to row.usedLastMinute,
                            "app_limit_1s" to row.appPerSecondLimit,
                            "app_limit_60s" to row.appPerMinuteLimit,
                            "hard_limit_1s" to row.hardPerSecondLimit,
                            "hard_limit_60s" to row.hardPerMinuteLimit
                        ))
                    }
                }
                return
            }
            dominantReason = reason
            val sleep = waitMs.coerceAtLeast(5L)
            delay(sleep) // Suspending: consumes no OkHttp dispatcher worker.
            totalWaitMs += sleep
        }
        } finally {
            if (protectionLtp) pendingProtectionLtps.decrementAndGet()
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
        GrowwApiUsageLedger.Category.HISTORICAL -> Limits(GrowwHistoricalThrottlePolicy.APP_PER_SECOND, GrowwHistoricalThrottlePolicy.APP_PER_MINUTE) // same enforced budget as telemetry
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
