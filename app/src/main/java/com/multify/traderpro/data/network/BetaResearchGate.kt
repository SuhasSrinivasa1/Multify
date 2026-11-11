package com.multify.traderpro.data.network

import android.os.SystemClock
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
class BetaResearchGate @Inject constructor() {
    enum class Type { LIVE, HISTORICAL }
    data class UsageSnapshot(
        val liveRequests: Long, val historicalRequests: Long,
        val liveFailures: Long, val historicalFailures: Long,
        val liveRateLimits: Long, val historicalRateLimits: Long,
        val lastLatencyMs: Long
    )
    data class LiveStatus(
        val usedLastMinute: Int = 0,
        val usedLastSecond: Int = 0,
        val inFlight: Int = 0,
        val endpointLimitPerMinute: Int = FixedDualAccountPolicy.BETA_LIVE_ENDPOINT_PER_MINUTE,
        val accountBudgetPerMinute: Int = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE,
        val cooldownRemainingMs: Long = 0L,
        val halfOpenProbeInFlight: Boolean = false,
        val rateLimitResponses: Long = 0L,
        val activeGapMs: Long = LtpAdaptivePacingPolicy.START_GAP_MS
    )
    private data class Window(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        val rateLimitIncidents: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntil: Long = 0L,
        var lastGrantedAt: Long = 0L,
        var inFlight: Int = 0,
        var probeRequired: Boolean = false,
        var probeInFlight: Boolean = false
    )
    private val lock = Mutex()
    private val historicalInFlight = Mutex()
    private val windows = Type.values().associateWith { Window() }
    // Conservative per-endpoint limits observed necessary after 429s. Groww
    // documents 300 LIVE category requests/min per account; that is not a
    // promise that one /ltp endpoint can safely sustain 300 requests/min.
    private val liveSec = 2
    private val liveMinute = FixedDualAccountPolicy.BETA_LIVE_ENDPOINT_PER_MINUTE
    private val histSec = 1
    private val histMinute = FixedDualAccountPolicy.BETA_HISTORICAL_PER_MINUTE
    private var livePace = LtpAdaptivePacingPolicy.State(LtpAdaptivePacingPolicy.START_GAP_MS, SystemClock.elapsedRealtime())

    private val liveRequests = AtomicLong()
    private val historicalRequests = AtomicLong()
    private val liveFailures = AtomicLong()
    private val historicalFailures = AtomicLong()
    private val liveRateLimits = AtomicLong()
    private val historicalRateLimits = AtomicLong()
    private val lastLatencyMs = AtomicLong()

    suspend fun <T> read(type: Type, block: suspend () -> T): T =
        if (type == Type.HISTORICAL) historicalInFlight.withLock { readRateLimited(type, block) }
        else readRateLimited(type, block)

    private fun updateLiveStatus(now: Long, w: Window) {
        liveStatusSnapshot = LiveStatus(
            usedLastMinute = w.minute.size,
            usedLastSecond = w.second.size,
            inFlight = w.inFlight,
            endpointLimitPerMinute = liveMinute,
            cooldownRemainingMs = (w.cooldownUntil - now).coerceAtLeast(0L),
            halfOpenProbeInFlight = w.probeInFlight,
            rateLimitResponses = liveRateLimits.get(),
            activeGapMs = livePace.gapMs
        )
    }

    private suspend fun <T> readRateLimited(type: Type, block: suspend () -> T): T {
        while (true) {
            val waitMs = lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                while (w.second.isNotEmpty() && w.second.first() <= now - 1_000L) w.second.removeFirst()
                while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
                val perSecond = if (type == Type.LIVE) liveSec else histSec
                val perMinute = if (type == Type.LIVE) liveMinute else histMinute
                val spacingWait = if (w.lastGrantedAt > 0L)
                    (w.lastGrantedAt + (if (type == Type.LIVE) livePace.gapMs else 1_500L) - now)
                        .coerceAtLeast(0L) else 0L
                val wait = maxOf(
                    spacingWait,
                    (w.cooldownUntil - now).coerceAtLeast(0L),
                    if (w.probeRequired && w.probeInFlight) 100L else 0L,
                    if (w.inFlight >= (if (type == Type.LIVE) 2 else 1)) 100L else 0L,
                    if (w.second.size >= perSecond) (w.second.first() + 1_000L - now).coerceAtLeast(1L) else 0L,
                    if (w.minute.size >= perMinute) (w.minute.first() + 60_000L - now).coerceAtLeast(1L) else 0L
                )
                if (wait == 0L) {
                    w.second.addLast(now)
                    w.minute.addLast(now)
                    w.lastGrantedAt = now
                    w.inFlight++
                    if (w.probeRequired) w.probeInFlight = true
                }
                if (type == Type.LIVE) updateLiveStatus(now, w)
                wait
            }
            if (waitMs <= 0L) break
            // Waiting for a Beta-only cooldown cannot stall Fixed's official scan.
            if (waitMs > 10_000L) throw IOException("BETA_FIXED_RATE_BUDGET_OR_COOLDOWN")
            delay(waitMs.coerceAtLeast(5L))
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
                    livePace = LtpAdaptivePacingPolicy.onSuccess(livePace, SystemClock.elapsedRealtime())
                    updateLiveStatus(SystemClock.elapsedRealtime(), w)
                }
            }
            result
        } catch (failure: Throwable) {
            if (type == Type.LIVE) liveFailures.incrementAndGet() else historicalFailures.incrementAndGet()
            lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                if (failure is HttpException && failure.code() == 429) {
                    if (type == Type.LIVE) {
                        liveRateLimits.incrementAndGet()
                        livePace = LtpAdaptivePacingPolicy.on429(livePace, now)
                    } else historicalRateLimits.incrementAndGet()
                    while (w.rateLimitIncidents.isNotEmpty() && w.rateLimitIncidents.first() <= now - 3_600_000L)
                        w.rateLimitIncidents.removeFirst()
                    // Multiple replies in the same 5-second window are ONE incident.
                    if (w.rateLimitIncidents.lastOrNull()?.let { now - it < 5_000L } != true)
                        w.rateLimitIncidents.addLast(now)
                    val retryAfter = GrowwRetryAfterPolicy.retryAfterMs(
                        failure.response()?.headers()?.get("Retry-After"), System.currentTimeMillis()
                    )
                    val cooldown = GrowwRecoveryPolicy.cooldownMs(w.rateLimitIncidents.size, retryAfter)
                    w.cooldownUntil = maxOf(w.cooldownUntil, now + cooldown)
                    w.probeRequired = true
                } else if (w.probeInFlight) {
                    // A failed probe does not release a burst of queued Beta calls.
                    w.cooldownUntil = maxOf(w.cooldownUntil, now + 10_000L)
                    w.probeRequired = true
                }
                w.probeInFlight = false
                if (type == Type.LIVE) updateLiveStatus(now, w)
            }
            throw failure
        } finally {
            lock.withLock {
                val w = windows.getValue(type)
                w.inFlight = (w.inFlight - 1).coerceAtLeast(0)
                if (type == Type.LIVE) updateLiveStatus(SystemClock.elapsedRealtime(), w)
            }
            lastLatencyMs.set((SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
        }
    }

    /** Beta cooldown is account-local. Alpha trading and market-data are independent. */
    suspend fun healthyForFixed(type: Type): Boolean = lock.withLock {
        val w = windows.getValue(type)
        val now = SystemClock.elapsedRealtime()
        if (type == Type.LIVE) {
            while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
            updateLiveStatus(now, w)
        }
        w.cooldownUntil <= now
    }

    fun snapshot() = UsageSnapshot(
        liveRequests.get(), historicalRequests.get(), liveFailures.get(), historicalFailures.get(),
        liveRateLimits.get(), historicalRateLimits.get(), lastLatencyMs.get()
    )
    suspend fun diagnosticStatus(): Map<String, Map<String, Any>> = lock.withLock {
        val now = SystemClock.elapsedRealtime()
        Type.values().associate { type ->
            val w = windows.getValue(type)
            while (w.second.isNotEmpty() && w.second.first() <= now - 1_000L) w.second.removeFirst()
            while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
            type.name to mapOf(
                "used_1s" to w.second.size,
                "used_60s" to w.minute.size,
                "in_flight" to w.inFlight,
                "cooldown_remaining_ms" to (w.cooldownUntil - now).coerceAtLeast(0L),
                "cooldown_until_elapsed_ms" to w.cooldownUntil,
                "probe_required" to w.probeRequired,
                "probe_in_flight" to w.probeInFlight,
                "last_granted_ago_ms" to if (w.lastGrantedAt > 0L) (now - w.lastGrantedAt).coerceAtLeast(0L) else -1L
            )
        }
    }

    fun configuredLiveLimitPerMinute() = liveMinute
    fun configuredHistoricalLimitPerMinute() = histMinute

    companion object {
        @Volatile private var liveStatusSnapshot = LiveStatus()
        fun latestLiveStatus(): LiveStatus = liveStatusSnapshot
    }
}
