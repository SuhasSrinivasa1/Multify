package com.multify.traderpro.data.network

import android.os.SystemClock
import kotlinx.coroutines.delay
import java.util.ArrayDeque
import java.util.EnumMap

/**
 * Single process-wide admission budget shared by Alpha and Beta READ-ONLY market data.
 * Existing per-account governors still apply. ORDERS, AUTH and NON_TRADING are not
 * part of this budget. A broker 429 on either account closes the relevant shared
 * market-data lane; no cross-account quota bypass occurs.
 */
internal object GrowwSharedMarketDataBudget {
    enum class Lane { LIVE, HISTORICAL }
    enum class State { NORMAL, COOLDOWN, HALF_OPEN, RECOVERING }
    data class Snapshot(
        val lane: Lane, val state: State, val usedLastMinute: Int,
        val minuteLimit: Int, val cooldownRemainingMs: Long,
        val alphaGranted: Long, val betaGranted: Long, val total429: Long
    )
    private data class Window(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var minuteLimit: Int,
        var cooldownUntilMs: Long = 0L,
        var state: State = State.NORMAL,
        var probeInFlight: Boolean = false,
        var last429Ms: Long = 0L,
        var lastRecoveryIncreaseMs: Long = 0L,
        var alphaGranted: Long = 0L,
        var betaGranted: Long = 0L,
        var total429: Long = 0L
    )
    private val lock = Any()
    private val windows = EnumMap<Lane, Window>(Lane::class.java).apply {
        put(Lane.LIVE, Window(minuteLimit = 120))
        put(Lane.HISTORICAL, Window(minuteLimit = 48))
    }
    private fun prune(w: Window, now: Long) {
        while (w.second.isNotEmpty() && w.second.first() <= now - 1_000L) w.second.removeFirst()
        while (w.minute.isNotEmpty() && w.minute.first() <= now - 60_000L) w.minute.removeFirst()
    }

    /**
     * Admission is suspending and never occupies OkHttp dispatcher threads.
     * Rate ceilings are deliberately not added across accounts.
     * On cooldown expiration, ONE probe (Alpha or Beta) is admitted; other
     * requests wait until that probe succeeds.
     */
    suspend fun acquire(lane: Lane, account: String, protection: Boolean = false) {
        while (true) {
            val wait = synchronized(lock) {
                val w = windows.getValue(lane)
                val now = SystemClock.elapsedRealtime()
                prune(w, now)
                if (w.state == State.COOLDOWN && now >= w.cooldownUntilMs) w.state = State.HALF_OPEN
                if (w.state == State.RECOVERING && now - w.lastRecoveryIncreaseMs >= 60_000L) {
                    w.minuteLimit = (w.minuteLimit + if (lane == Lane.LIVE) 5 else 2)
                        .coerceAtMost(if (lane == Lane.LIVE) 120 else 48)
                    w.lastRecoveryIncreaseMs = now
                    if (w.minuteLimit == (if (lane == Lane.LIVE) 120 else 48)) w.state = State.NORMAL
                }
                val cooldown = (w.cooldownUntilMs - now).coerceAtLeast(0L)
                val probeWait = if (w.state == State.HALF_OPEN && w.probeInFlight) 100L else 0L
                // Reserve capacity for SELL/holdings protection when market data is busy.
                // This is a shared market-data ceiling, not a cap on order submission.
                val reserve = if (lane == Lane.LIVE &&
                    com.multify.traderpro.engine.MarketDataPriorityAdmissionPolicy.hasManagedHoldings &&
                    !protection) 12 else 0
                val minuteCap = (w.minuteLimit - reserve).coerceAtLeast(1)
                val minuteWait = if (w.minute.size >= minuteCap)
                    (w.minute.first() + 60_000L - now).coerceAtLeast(1L) else 0L
                val secCap = if (lane == Lane.LIVE) 4 else 2
                val secondWait = if (w.second.size >= secCap)
                    (w.second.first() + 1_000L - now).coerceAtLeast(1L) else 0L
                val waitMs = maxOf(cooldown, probeWait, minuteWait, secondWait)
                if (waitMs <= 0L) {
                    w.second.addLast(now)
                    w.minute.addLast(now)
                    if (w.state == State.HALF_OPEN) w.probeInFlight = true
                    if (account == "BETA") w.betaGranted++ else w.alphaGranted++
                }
                waitMs
            }
            if (wait <= 0L) return
            // Fixed scans should fail quickly and retry on the next scheduled cycle
            // rather than block an entire 90-second cycle behind one upstream 429.
            if (wait > 10_000L && !protection) {
                throw java.io.IOException("SHARED_GROWW_${lane.name}_COOLDOWN_OR_QUOTA")
            }
            delay(wait.coerceAtLeast(5L))
        }
    }

    fun on429(lane: Lane, retryAfterMs: Long = 0L) = synchronized(lock) {
        val w = windows.getValue(lane)
        val now = SystemClock.elapsedRealtime()
        val repeated = now - w.last429Ms < 5_000L
        w.total429++
        w.last429Ms = now
        // Conservatively reduce admission pressure after broker rejection; respect
        // any longer Retry-After. Existing Alpha/Beta category cooldowns still apply.
        if (!repeated) w.minuteLimit = (w.minuteLimit * 3 / 4)
            .coerceAtLeast(if (lane == Lane.LIVE) 35 else 12)
        w.cooldownUntilMs = maxOf(w.cooldownUntilMs, now + maxOf(90_000L, retryAfterMs))
        w.probeInFlight = false
        w.state = State.COOLDOWN
    }

    fun completed(lane: Lane, responseCode: Int) = synchronized(lock) {
        val w = windows.getValue(lane)
        if (responseCode == 429) return@synchronized
        if (w.state == State.HALF_OPEN) {
            w.probeInFlight = false
            if (responseCode in 200..399) {
                w.state = State.RECOVERING
                w.lastRecoveryIncreaseMs = SystemClock.elapsedRealtime()
            } else {
                // Unsuccessful half-open probes do not trigger a request storm.
                w.cooldownUntilMs = SystemClock.elapsedRealtime() + 10_000L
                w.state = State.COOLDOWN
            }
        }
    }

    fun isCoolingDown(lane: Lane): Boolean = synchronized(lock) {
        val w = windows.getValue(lane)
        w.state == State.COOLDOWN && w.cooldownUntilMs > SystemClock.elapsedRealtime()
    }

    fun snapshot(lane: Lane): Snapshot = synchronized(lock) {
        val w = windows.getValue(lane)
        prune(w, SystemClock.elapsedRealtime())
        Snapshot(lane, w.state, w.minute.size, w.minuteLimit,
            (w.cooldownUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
            w.alphaGranted, w.betaGranted, w.total429)
    }
}
