package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.BetaLiveEndpointPacingPolicy
import com.multify.traderpro.engine.DualMinuteAdmissionPolicy
import com.multify.traderpro.engine.FixedDualAccountPolicy
import com.multify.traderpro.engine.GrowwRecoveryPolicy
import com.multify.traderpro.engine.GrowwRetryAfterPolicy
import com.multify.traderpro.engine.LtpAdaptivePacingPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-account Beta broker governor. Alpha has its OWN network interceptor,
 * usage ledger, orders, and cooldown. Beta is read-only research: a Beta 429
 * must never pause Alpha. Broker category ceilings are NOT global across
 * these separately authenticated accounts.
 */
@Singleton
class BetaResearchGate @Inject constructor(
    private val auditLogger: AuditLogger
) {
    enum class Type { LIVE, HISTORICAL }
    enum class LiveEndpoint { LTP, QUOTE }
    data class UsageSnapshot(
        val liveRequests: Long, val historicalRequests: Long,
        val liveFailures: Long, val historicalFailures: Long,
        val liveRateLimits: Long, val historicalRateLimits: Long,
        val lastLatencyMs: Long,
        val livePermitWaitRequests: Long,
        val historicalPermitWaitRequests: Long,
        val livePermitWaitMs: Long,
        val historicalPermitWaitMs: Long
    )
    data class LiveStatus(
        val usedLastMinute: Int = 0,
        val endpointLimitPerMinute: Int = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE,
        val cooldownRemainingMs: Long = 0L,
        val halfOpenProbeInFlight: Boolean = false,
        val rateLimitResponses: Long = 0L,
        val activeGapMs: Long = LtpAdaptivePacingPolicy.START_GAP_MS,
        val ltpGapMs: Long = LtpAdaptivePacingPolicy.START_GAP_MS,
        val quoteGapMs: Long = LtpAdaptivePacingPolicy.START_GAP_MS,
        val fixedMinuteBucket: Long = Long.MIN_VALUE,
        val fixedMinuteUsed: Int = 0,
        val fixedMinuteRemaining: Int = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE,
        val rollingMinuteRemaining: Int = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE
    )
    private data class Window(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        val rateLimitIncidents: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntil: Long = 0L,
        var lastGrantedAt: Long = 0L,
        val lastGrantedAtByEndpoint: MutableMap<LiveEndpoint, Long> = mutableMapOf(),
        var probeRequired: Boolean = false,
        var probeInFlight: Boolean = false,
        var calendar: DualMinuteAdmissionPolicy.CalendarState =
            DualMinuteAdmissionPolicy.CalendarState()
    )
    private val lock = Mutex()
    private val historicalInFlight = Mutex()
    private val windows = Type.values().associateWith { Window() }
    // Conservative per-endpoint limits observed necessary after 429s. Groww
    // documents 300 LIVE category requests/min per account; that is not a
    // promise that one /ltp endpoint can safely sustain 300 requests/min.
    private val liveSec = FixedDualAccountPolicy.BETA_LIVE_PER_SECOND
    private val liveMinute = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE
    private val histSec = 1
    private val histMinute = FixedDualAccountPolicy.BETA_HISTORICAL_PER_MINUTE
    private var livePacing = BetaLiveEndpointPacingPolicy.initial(SystemClock.elapsedRealtime())

    private val liveRequests = AtomicLong()
    private val historicalRequests = AtomicLong()
    private val liveFailures = AtomicLong()
    private val historicalFailures = AtomicLong()
    private val liveRateLimits = AtomicLong()
    private val historicalRateLimits = AtomicLong()
    private val lastLatencyMs = AtomicLong()
    private val livePermitWaitRequests = AtomicLong()
    private val historicalPermitWaitRequests = AtomicLong()
    private val livePermitWaitMs = AtomicLong()
    private val historicalPermitWaitMs = AtomicLong()

    suspend fun <T> read(
        type: Type,
        endpoint: LiveEndpoint = LiveEndpoint.QUOTE,
        block: suspend () -> T
    ): T =
        if (type == Type.HISTORICAL) historicalInFlight.withLock {
            readRateLimited(type, endpoint, block)
        } else readRateLimited(type, endpoint, block)

    private fun updateLiveStatus(now: Long, w: Window) {
        liveStatusCapturedAtElapsedMs = now
        liveStatusSnapshot = LiveStatus(
            usedLastMinute = w.minute.size,
            endpointLimitPerMinute = liveMinute,
            cooldownRemainingMs = (w.cooldownUntil - now).coerceAtLeast(0L),
            halfOpenProbeInFlight = w.probeInFlight,
            rateLimitResponses = liveRateLimits.get(),
            activeGapMs = maxOf(
                BetaLiveEndpointPacingPolicy.gapMs(livePacing, LiveEndpoint.LTP),
                BetaLiveEndpointPacingPolicy.gapMs(livePacing, LiveEndpoint.QUOTE)
            ),
            ltpGapMs = BetaLiveEndpointPacingPolicy.gapMs(livePacing, LiveEndpoint.LTP),
            quoteGapMs = BetaLiveEndpointPacingPolicy.gapMs(livePacing, LiveEndpoint.QUOTE),
            fixedMinuteBucket = DualMinuteAdmissionPolicy.minuteId(System.currentTimeMillis()),
            fixedMinuteUsed = DualMinuteAdmissionPolicy.normalize(
                w.calendar, System.currentTimeMillis()).used,
            fixedMinuteRemaining = DualMinuteAdmissionPolicy.headroom(
                w.calendar, System.currentTimeMillis(), liveMinute),
            rollingMinuteRemaining = (liveMinute - w.minute.size).coerceAtLeast(0)
        )
    }

    private suspend fun <T> readRateLimited(
        type: Type, endpoint: LiveEndpoint, block: suspend () -> T
    ): T {
        var totalWaitMs = 0L
        var waitIterations = 0
        while (true) {
            val waitMs = lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                while (w.second.isNotEmpty() && w.second.first() <= now - 1_000L) w.second.removeFirst()
                while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
                val perSecond = if (type == Type.LIVE) liveSec else histSec
                val perMinute = if (type == Type.LIVE) liveMinute else histMinute
                val wallNow = System.currentTimeMillis()
                w.calendar = DualMinuteAdmissionPolicy.normalize(w.calendar, wallNow)
                val fixedMinuteWait = DualMinuteAdmissionPolicy.calendarWaitMs(
                    w.calendar, wallNow, perMinute)
                val endpointLastGrantedAt = if (type == Type.LIVE)
                    w.lastGrantedAtByEndpoint[endpoint] ?: 0L else w.lastGrantedAt
                val endpointGapMs = if (type == Type.LIVE)
                    BetaLiveEndpointPacingPolicy.gapMs(livePacing, endpoint) else 1_500L
                val spacingWait = if (endpointLastGrantedAt > 0L)
                    (endpointLastGrantedAt + endpointGapMs - now).coerceAtLeast(0L) else 0L
                val wait = maxOf(
                    spacingWait,
                    (w.cooldownUntil - now).coerceAtLeast(0L),
                    fixedMinuteWait,
                    if (w.probeRequired && w.probeInFlight) 100L else 0L,
                    if (w.second.size >= perSecond) (w.second.first() + 1_000L - now).coerceAtLeast(1L) else 0L,
                    if (w.minute.size >= perMinute) (w.minute.first() + 60_000L - now).coerceAtLeast(1L) else 0L
                )
                if (wait == 0L) {
                    w.second.addLast(now)
                    w.minute.addLast(now)
                    if (type == Type.LIVE) w.lastGrantedAtByEndpoint[endpoint] = now
                    else w.lastGrantedAt = now
                    w.calendar = DualMinuteAdmissionPolicy.reserve(w.calendar, wallNow)
                    if (w.probeRequired) w.probeInFlight = true
                }
                if (type == Type.LIVE) updateLiveStatus(now, w)
                wait
            }
            if (waitMs <= 0L) break
            // Waiting for a Beta-only cooldown cannot stall Fixed's official scan.
            // Calendar/rolling minute exhaustion is a bounded local pause,
            // not a broker rejection. Only genuine long cooldowns fail fast.
            val realCooldown = lock.withLock {
                val w = windows.getValue(type)
                (w.cooldownUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            }
            if (realCooldown > 10_000L)
                throw IOException("BETA_FIXED_RATE_BUDGET_OR_COOLDOWN")
            val sleepMs = waitMs.coerceIn(5L, 1_000L)
            totalWaitMs += sleepMs
            waitIterations++
            delay(sleepMs)
        }
        if (totalWaitMs > 0L) {
            if (type == Type.LIVE) {
                livePermitWaitRequests.incrementAndGet()
                livePermitWaitMs.addAndGet(totalWaitMs)
            } else {
                historicalPermitWaitRequests.incrementAndGet()
                historicalPermitWaitMs.addAndGet(totalWaitMs)
            }
            auditLogger.logAsync("API_RATE_LIMIT", "BETA_CLIENT_GOVERNOR_WAIT", mapOf(
                "account" to "BETA", "category" to if (type == Type.LIVE) "LIVE_DATA" else "HISTORICAL",
                "wait_ms" to totalWaitMs, "wait_iterations" to waitIterations,
                "endpoint_lane" to if (type == Type.LIVE) endpoint.name else "HISTORICAL",
                "endpoint_gap_ms" to if (type == Type.LIVE)
                    BetaLiveEndpointPacingPolicy.gapMs(livePacing, endpoint) else 1_500L,
                "local_per_second_limit" to if (type == Type.LIVE) liveSec else histSec,
                "local_per_minute_limit" to if (type == Type.LIVE) liveMinute else histMinute,
                "wait_is_local_not_broker_429" to true,
                "one_event_per_admitted_request_with_positive_wait" to true
            ))
        }
        val started = SystemClock.elapsedRealtime()
        if (type == Type.LIVE) liveRequests.incrementAndGet() else historicalRequests.incrementAndGet()
        return try {
            val result = block()
            lock.withLock {
                val w = windows.getValue(type)
                if (w.probeInFlight) {
                    w.probeInFlight = false
                    w.probeRequired = false
                }
                if (type == Type.LIVE) {
                    livePacing = BetaLiveEndpointPacingPolicy.onSuccess(
                        livePacing, endpoint, SystemClock.elapsedRealtime())
                    updateLiveStatus(SystemClock.elapsedRealtime(), w)
                }
            }
            result
        } catch (failure: Throwable) {
            if (type == Type.LIVE) liveFailures.incrementAndGet() else historicalFailures.incrementAndGet()
            var appliedCooldownMs = 0L
            var retryAfterRaw: String? = null
            var incidentCount = 0
            lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                if (failure is HttpException && failure.code() == 429) {
                    if (type == Type.LIVE) {
                        liveRateLimits.incrementAndGet()
                        livePacing = BetaLiveEndpointPacingPolicy.on429(livePacing, endpoint, now)
                    } else historicalRateLimits.incrementAndGet()
                    while (w.rateLimitIncidents.isNotEmpty() && w.rateLimitIncidents.first() <= now - 3_600_000L)
                        w.rateLimitIncidents.removeFirst()
                    // Multiple replies in the same 5-second window are ONE incident.
                    if (w.rateLimitIncidents.lastOrNull()?.let { now - it < 5_000L } != true)
                        w.rateLimitIncidents.addLast(now)
                    incidentCount = w.rateLimitIncidents.size
                    retryAfterRaw = failure.response()?.headers()?.get("Retry-After")
                    val retryAfter = GrowwRetryAfterPolicy.retryAfterMs(
                        retryAfterRaw, System.currentTimeMillis()
                    )
                    appliedCooldownMs = GrowwRecoveryPolicy.cooldownMs(incidentCount, retryAfter)
                    w.cooldownUntil = maxOf(w.cooldownUntil, now + appliedCooldownMs)
                    w.probeRequired = true
                } else if (w.probeInFlight) {
                    // A failed probe does not release a burst of queued Beta calls.
                    w.cooldownUntil = maxOf(w.cooldownUntil, now + 10_000L)
                    w.probeRequired = true
                }
                w.probeInFlight = false
                if (type == Type.LIVE) updateLiveStatus(now, w)
            }
            if (failure is HttpException && failure.code() == 429) {
                val category = if (type == Type.LIVE) "LIVE_DATA" else "HISTORICAL"
                val wire = GrowwHttpTraceLedger.counters("BETA", category)
                auditLogger.logAsync("API_RATE_LIMIT", "BETA_HTTP_429", mapOf(
                    "account" to "BETA", "category" to category,
                    "http_status" to 429, "retry_after_raw" to retryAfterRaw,
                    "applied_local_cooldown_ms" to appliedCooldownMs,
                    "incident_count_last_hour" to incidentCount,
                    "endpoint_lane" to endpoint.name,
                    "active_ltp_gap_ms" to BetaLiveEndpointPacingPolicy.gapMs(
                        livePacing, LiveEndpoint.LTP),
                    "active_quote_gap_ms" to BetaLiveEndpointPacingPolicy.gapMs(
                        livePacing, LiveEndpoint.QUOTE),
                    "category_wire_requests_total" to wire.requests,
                    "category_wire_429_total" to wire.rateLimits,
                    "broker_category_limit_proven" to false,
                    "beta_read_only" to true,
                    "alpha_cooldown_independent" to true
                ))
            }
            throw failure
        } finally {
            lastLatencyMs.set((SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
        }
    }

    /**
     * Non-reserving advisory headroom for the *optional* deterministic Beta
     * historical share. Actual 1/s and 8/60s enforcement is still atomic inside
     * readRateLimited(). Reserve one possible slot for Alpha-history failure.
     * When Alpha historical is already unavailable, the router may use Beta
     * irrespective of this advisory hint and must fail closed if capacity runs out.
     */
    suspend fun historicalHasSpareFixedCapacity(): Boolean = lock.withLock {
        val w = windows.getValue(Type.HISTORICAL)
        val now = SystemClock.elapsedRealtime()
        while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
        w.calendar = DualMinuteAdmissionPolicy.normalize(w.calendar, System.currentTimeMillis())
        w.cooldownUntil <= now && !w.probeInFlight &&
            !historicalInFlight.isLocked && w.minute.size < (histMinute - 1) &&
            w.calendar.used < (histMinute - 1)
    }

    /** Beta cooldown is account-local. Alpha trading and market-data are independent. */
    suspend fun healthyForFixed(type: Type): Boolean = lock.withLock {
        val w = windows.getValue(type)
        val now = SystemClock.elapsedRealtime()
        while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L)
            w.minute.removeFirst()
        w.calendar = DualMinuteAdmissionPolicy.normalize(w.calendar, System.currentTimeMillis())
        if (type == Type.LIVE) updateLiveStatus(now, w)
        w.cooldownUntil <= now && !w.probeInFlight && when (type) {
            Type.LIVE -> w.minute.size < liveMinute && w.calendar.used < liveMinute
            Type.HISTORICAL -> w.minute.size < histMinute &&
                w.calendar.used < histMinute
        }
    }

    fun snapshot() = UsageSnapshot(
        liveRequests.get(), historicalRequests.get(), liveFailures.get(), historicalFailures.get(),
        liveRateLimits.get(), historicalRateLimits.get(), lastLatencyMs.get(),
        livePermitWaitRequests.get(), historicalPermitWaitRequests.get(),
        livePermitWaitMs.get(), historicalPermitWaitMs.get()
    )
    fun configuredLiveLimitPerMinute() = liveMinute
    fun configuredHistoricalLimitPerMinute() = histMinute

    companion object {
        @Volatile private var liveStatusSnapshot = LiveStatus()
        @Volatile private var liveStatusCapturedAtElapsedMs = 0L
        fun latestLiveStatus(): LiveStatus {
            val snapshot = liveStatusSnapshot
            val age = (SystemClock.elapsedRealtime() - liveStatusCapturedAtElapsedMs)
                .coerceAtLeast(0L)
            val sameCalendarMinute =
                DualMinuteAdmissionPolicy.minuteId(System.currentTimeMillis()) ==
                    snapshot.fixedMinuteBucket
            return snapshot.copy(
                // The trace ledger remains authoritative for outbound attempts.
                usedLastMinute = if (age >= 60_000L) 0 else snapshot.usedLastMinute,
                cooldownRemainingMs = (snapshot.cooldownRemainingMs - age).coerceAtLeast(0L),
                fixedMinuteUsed = if (sameCalendarMinute) snapshot.fixedMinuteUsed else 0,
                fixedMinuteRemaining = if (sameCalendarMinute)
                    snapshot.fixedMinuteRemaining else snapshot.endpointLimitPerMinute,
                rollingMinuteRemaining = if (age >= 60_000L)
                    snapshot.endpointLimitPerMinute else snapshot.rollingMinuteRemaining
            )
        }
    }
}
