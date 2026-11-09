package com.multify.traderpro.data.network

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Independent, non-blocking governor for Beta's read-only Fixed market-data traffic. */
@Singleton
class BetaResearchGate @Inject constructor() {
    enum class Type { LIVE, HISTORICAL }
    data class UsageSnapshot(
        val liveRequests: Long, val historicalRequests: Long,
        val liveFailures: Long, val historicalFailures: Long,
        val liveRateLimits: Long, val historicalRateLimits: Long,
        val lastLatencyMs: Long
    )
    private data class Window(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntil: Long = 0L,
        var lastGrantedAt: Long = 0L
    )
    private val lock = Mutex()
    private val historicalInFlight = Mutex()
    private val windows = Type.values().associateWith { Window() }
    // Deliberately far below Groww's published shared LIVE category ceiling.
    // 429 telemetry has shown tighter effective limits on individual endpoints.
    private val liveSec = 1
    private val liveMinute = com.multify.traderpro.engine.FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE
    private val histSec = 1
    private val histMinute = com.multify.traderpro.engine.FixedDualAccountPolicy.BETA_HISTORICAL_PER_MINUTE
    private val liveGapMs = com.multify.traderpro.engine.FixedDualAccountPolicy.BETA_LTP_MIN_GAP_MS

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
                    (w.lastGrantedAt + (if (type == Type.LIVE) liveGapMs else 1_500L) - now)
                        .coerceAtLeast(0L) else 0L
                val wait = maxOf(
                    spacingWait,
                    (w.cooldownUntil - now).coerceAtLeast(0L),
                    if (w.second.size >= perSecond) (w.second.first() + 1_000L - now).coerceAtLeast(1L) else 0L,
                    if (w.minute.size >= perMinute) (w.minute.first() + 60_000L - now).coerceAtLeast(1L) else 0L
                )
                if (wait == 0L) {
                    w.second.addLast(now)
                    w.minute.addLast(now)
                    w.lastGrantedAt = now
                }
                wait
            }
            if (waitMs <= 0L) break
            // Fixed has a 150-second cadence. Never let an unavailable Beta lane
            // stall an entire official scan for a 60-second broker cooldown.
            // Caller falls back to Alpha using the existing Alpha governor.
            if (waitMs > 10_000L) {
                throw java.io.IOException("BETA_FIXED_RATE_BUDGET_OR_COOLDOWN")
            }
            delay(waitMs.coerceAtLeast(5L))
        }
        // Apply the same aggregate allowance as Alpha; Beta has no independent
        // right to double the broker's possibly shared upstream quota.
        val sharedLane = if (type == Type.LIVE)
            GrowwSharedMarketDataBudget.Lane.LIVE else GrowwSharedMarketDataBudget.Lane.HISTORICAL
        GrowwSharedMarketDataBudget.acquire(sharedLane, "BETA")
        val requestStarted = SystemClock.elapsedRealtime()
        if (type == Type.LIVE) liveRequests.incrementAndGet() else historicalRequests.incrementAndGet()
        return try {
            block().also { GrowwSharedMarketDataBudget.completed(sharedLane, 200) }
        } catch (failure: Throwable) {
            if (type == Type.LIVE) liveFailures.incrementAndGet() else historicalFailures.incrementAndGet()
            if (failure is HttpException && failure.code() == 429) {
                if (type == Type.LIVE) liveRateLimits.incrementAndGet() else historicalRateLimits.incrementAndGet()
                val retryAfterMs = failure.response()?.headers()?.get("Retry-After")?.toLongOrNull()?.times(1_000L) ?: 0L
                val cooldownMs = maxOf(60_000L, retryAfterMs)
                GrowwSharedMarketDataBudget.on429(sharedLane, cooldownMs)
                lock.withLock {
                    val w = windows.getValue(type)
                    w.cooldownUntil = maxOf(w.cooldownUntil, SystemClock.elapsedRealtime() + cooldownMs)
                }
            }
            if (failure !is HttpException || failure.code() != 429) {
                GrowwSharedMarketDataBudget.completed(sharedLane, (failure as? HttpException)?.code() ?: 0)
            }
            throw failure
        } finally {
            lastLatencyMs.set((SystemClock.elapsedRealtime() - requestStarted).coerceAtLeast(0L))
        }
    }

    /** A 429 on Beta cannot borrow Alpha's trading quota to bypass cooldown. */
    suspend fun healthyForFixed(type: Type): Boolean = lock.withLock {
        windows.getValue(type).cooldownUntil <= SystemClock.elapsedRealtime()
    }

    fun snapshot() = UsageSnapshot(
        liveRequests.get(), historicalRequests.get(), liveFailures.get(), historicalFailures.get(),
        liveRateLimits.get(), historicalRateLimits.get(), lastLatencyMs.get()
    )
    fun configuredLiveLimitPerMinute() = liveMinute
    fun configuredHistoricalLimitPerMinute() = histMinute
}
