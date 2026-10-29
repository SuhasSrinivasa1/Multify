package com.multify.traderpro.data.network

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/** Non-blocking account-scoped API governor; no Alpha resources or rate state shared. */
@Singleton
class BetaResearchGate @Inject constructor() {
    enum class Type { LIVE, HISTORICAL }
    private data class Window(val second: ArrayDeque<Long> = ArrayDeque(),
                              val minute: ArrayDeque<Long> = ArrayDeque(), var cooldownUntil: Long = 0L)
    private val lock = Mutex()
    private val windows = Type.values().associateWith { Window() }
    private val liveSec = 9 // 90% of Groww's documented 10/s
    private val liveMinute = 285 // 95% of Groww's documented 300/min; not a request target
    // Groww does not publish a distinct historical ceiling in the main limits table.
    private val histSec = 1
    private val histMinute = 42
    suspend fun <T> read(type: Type, block: suspend () -> T): T {
        while (true) {
            val wait = lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                while (w.second.isNotEmpty() && w.second.first() <= now - 1_000L) w.second.removeFirst()
                while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
                val sec = if (type == Type.LIVE) liveSec else histSec
                val min = if (type == Type.LIVE) liveMinute else histMinute
                val waitMs = maxOf((w.cooldownUntil - now).coerceAtLeast(0L),
                    if (w.second.size >= sec) (w.second.first() + 1_000L - now).coerceAtLeast(1L) else 0L,
                    if (w.minute.size >= min) (w.minute.first() + 60_000L - now).coerceAtLeast(1L) else 0L)
                if (waitMs == 0L) { w.second.addLast(now); w.minute.addLast(now) }
                waitMs
            }
            if (wait <= 0L) break
            delay(wait.coerceAtLeast(5L))
        }
        return try { block() } catch (t: Throwable) {
            if (t is HttpException && t.code() == 429) {
                lock.withLock {
                    val w = windows.getValue(type)
                    w.cooldownUntil = maxOf(w.cooldownUntil, SystemClock.elapsedRealtime() + 60_000L)
                }
            }
            throw t
        }
    }
    fun configuredLiveLimitPerMinute() = liveMinute
    fun configuredHistoricalLimitPerMinute() = histMinute
}
