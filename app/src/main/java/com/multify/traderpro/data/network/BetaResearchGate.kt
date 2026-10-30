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

/** Independent, non-blocking governor for Beta's read-only Forecast research traffic. */
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
        var cooldownUntil: Long = 0L
    )
    private val lock = Mutex()
    private val windows = Type.values().associateWith { Window() }
    private val liveSec = 9
    private val liveMinute = 285
    private val histSec = 1
    // Historical endpoint-specific ceilings are not published in Groww's main rate-limit table.
    // The former 42/min profile produced replay 429s; hold 20/min until telemetry proves headroom.
    private val histMinute = 20
    private val liveRequests = AtomicLong()
    private val historicalRequests = AtomicLong()
    private val liveFailures = AtomicLong()
    private val historicalFailures = AtomicLong()
    private val liveRateLimits = AtomicLong()
    private val historicalRateLimits = AtomicLong()
    private val lastLatencyMs = AtomicLong()

    suspend fun <T> read(type: Type, block: suspend () -> T): T {
        while (true) {
            val waitMs = lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                while (w.second.isNotEmpty() && w.second.first() <= now - 1_000L) w.second.removeFirst()
                while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
                val perSecond = if (type == Type.LIVE) liveSec else histSec
                val perMinute = if (type == Type.LIVE) liveMinute else histMinute
                val wait = maxOf(
                    (w.cooldownUntil - now).coerceAtLeast(0L),
                    if (w.second.size >= perSecond) (w.second.first() + 1_000L - now).coerceAtLeast(1L) else 0L,
                    if (w.minute.size >= perMinute) (w.minute.first() + 60_000L - now).coerceAtLeast(1L) else 0L
                )
                if (wait == 0L) { w.second.addLast(now); w.minute.addLast(now) }
                wait
            }
            if (waitMs <= 0L) break
            delay(waitMs.coerceAtLeast(5L))
        }
        val requestStarted = SystemClock.elapsedRealtime()
        if (type == Type.LIVE) liveRequests.incrementAndGet() else historicalRequests.incrementAndGet()
        return try {
            block()
        } catch (failure: Throwable) {
            if (type == Type.LIVE) liveFailures.incrementAndGet() else historicalFailures.incrementAndGet()
            if (failure is HttpException && failure.code() == 429) {
                if (type == Type.LIVE) liveRateLimits.incrementAndGet() else historicalRateLimits.incrementAndGet()
                val retryAfterMs = failure.response()?.headers()?.get("Retry-After")?.toLongOrNull()?.times(1_000L) ?: 0L
                val cooldownMs = maxOf(60_000L, retryAfterMs)
                lock.withLock {
                    val w = windows.getValue(type)
                    w.cooldownUntil = maxOf(w.cooldownUntil, SystemClock.elapsedRealtime() + cooldownMs)
                }
            }
            throw failure
        } finally {
            lastLatencyMs.set((SystemClock.elapsedRealtime() - requestStarted).coerceAtLeast(0L))
        }
    }

    fun snapshot() = UsageSnapshot(
        liveRequests.get(), historicalRequests.get(), liveFailures.get(), historicalFailures.get(),
        liveRateLimits.get(), historicalRateLimits.get(), lastLatencyMs.get()
    )
    fun configuredLiveLimitPerMinute() = liveMinute
    fun configuredHistoricalLimitPerMinute() = histMinute
}
