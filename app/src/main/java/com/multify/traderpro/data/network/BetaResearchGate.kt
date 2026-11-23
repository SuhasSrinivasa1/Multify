package com.multify.traderpro.data.network

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.BetaLtpAdaptivePacingPolicy
import com.multify.traderpro.engine.BetaRecoveryStatePolicy
import com.multify.traderpro.engine.DualMinuteAdmissionPolicy
import com.multify.traderpro.engine.FixedDualAccountPolicy
import com.multify.traderpro.engine.GrowwRecoveryPolicy
import com.multify.traderpro.engine.GrowwRetryAfterPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    @ApplicationContext context: Context,
    private val auditLogger: AuditLogger
) {
    private val recoveryPreferences = context.getSharedPreferences("groww_beta_rate_state_v1", Context.MODE_PRIVATE)
    enum class Type { LIVE, HISTORICAL }
    enum class Priority(val rank: Int) {
        MARKET_DISCOVERY(0), DETAIL_RESEARCH(10), HISTORICAL_RESEARCH(20)
    }
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
        val activeGapMs: Long = BetaLtpAdaptivePacingPolicy.START_GAP_MS,
        val fixedMinuteBucket: Long = Long.MIN_VALUE,
        val fixedMinuteUsed: Int = 0,
        val fixedMinuteRemaining: Int = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE,
        val rollingMinuteRemaining: Int = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE
    )
    private data class Window(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        val admissionTimesEpochMs: ArrayDeque<Long> = ArrayDeque(),
        val rateLimitIncidents: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntil: Long = 0L,
        var lastGrantedAt: Long = 0L,
        var probeRequired: Boolean = false,
        var probeInFlight: Boolean = false,
        var calendar: DualMinuteAdmissionPolicy.CalendarState =
            DualMinuteAdmissionPolicy.CalendarState()
    )
    private val lock = Mutex()
    private val historicalInFlight = Mutex()
    private val livePriorityQueue = BetaPriorityPermitQueue()
    private val windows = Type.values().associateWith { Window() }
    // Conservative per-endpoint limits observed necessary after 429s. Groww
    // documents 300 LIVE category requests/min per account; that is not a
    // promise that one /ltp endpoint can safely sustain 300 requests/min.
    private val liveSec = FixedDualAccountPolicy.BETA_LIVE_PER_SECOND
    private val liveMinute = FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE
    private val histSec = 1
    private val histMinute = FixedDualAccountPolicy.BETA_HISTORICAL_PER_MINUTE
    private var livePace = BetaLtpAdaptivePacingPolicy.State(
        BetaLtpAdaptivePacingPolicy.START_GAP_MS, SystemClock.elapsedRealtime()
    )

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

    init {
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        Type.values().forEach { type ->
            val window = windows.getValue(type)
            val remaining = BetaRecoveryStatePolicy.remainingCooldownMs(
                recoveryPreferences.getLong(cooldownKey(type), 0L), wallNow
            )
            window.cooldownUntil = elapsedNow + remaining
            window.probeRequired = recoveryPreferences.getBoolean(probeKey(type), false)
            BetaRecoveryStatePolicy.parseRecentIncidents(
                recoveryPreferences.getString(incidentsKey(type), null), wallNow
            ).forEach { window.rateLimitIncidents.addLast(it) }
            BetaRecoveryStatePolicy.parseRecentAdmissions(
                recoveryPreferences.getString(admissionsKey(type), null), wallNow
            ).forEach { admittedAt ->
                val age = (wallNow - admittedAt).coerceAtLeast(0L)
                val elapsedAt = (elapsedNow - age).coerceAtLeast(0L)
                window.admissionTimesEpochMs.addLast(admittedAt)
                window.minute.addLast(elapsedAt)
                if (age < 1_000L) window.second.addLast(elapsedAt)
                window.lastGrantedAt = maxOf(window.lastGrantedAt, elapsedAt)
            }
            val bucket = DualMinuteAdmissionPolicy.minuteId(wallNow)
            window.calendar = DualMinuteAdmissionPolicy.CalendarState(
                bucket, window.admissionTimesEpochMs.count {
                    DualMinuteAdmissionPolicy.minuteId(it) == bucket
                }
            )
        }
        val restoredGap = recoveryPreferences.getLong(LIVE_GAP_KEY, BetaLtpAdaptivePacingPolicy.START_GAP_MS)
            .coerceIn(BetaLtpAdaptivePacingPolicy.MIN_GAP_MS, BetaLtpAdaptivePacingPolicy.MAX_GAP_MS)
        livePace = BetaLtpAdaptivePacingPolicy.State(restoredGap, elapsedNow)
        updateLiveStatus(elapsedNow, windows.getValue(Type.LIVE))
        auditLogger.logAsync("API_RATE_LIMIT", "BETA_RECOVERY_STATE_RESTORED", mapOf(
            "account" to "BETA",
            "live_cooldown_remaining_ms" to (windows.getValue(Type.LIVE).cooldownUntil - elapsedNow).coerceAtLeast(0L),
            "historical_cooldown_remaining_ms" to (windows.getValue(Type.HISTORICAL).cooldownUntil - elapsedNow).coerceAtLeast(0L),
            "live_adaptive_gap_ms" to livePace.gapMs,
            "live_recent_admissions_restored" to windows.getValue(Type.LIVE).minute.size,
            "historical_recent_admissions_restored" to windows.getValue(Type.HISTORICAL).minute.size,
            "beta_read_only" to true
        ))
    }

    suspend fun <T> read(
        type: Type,
        priority: Priority = if (type == Type.LIVE) Priority.DETAIL_RESEARCH else Priority.HISTORICAL_RESEARCH,
        block: suspend () -> T
    ): T {
        if (type == Type.HISTORICAL) return historicalInFlight.withLock {
            readRateLimited(type, priority, 0L, block)
        }
        val queuedAt = SystemClock.elapsedRealtime()
        return livePriorityQueue.withPermit(priority.rank) {
            readRateLimited(type, priority,
                (SystemClock.elapsedRealtime() - queuedAt).coerceAtLeast(0L), block)
        }
    }

    private fun updateLiveStatus(now: Long, w: Window) {
        liveStatusCapturedAtElapsedMs = now
        liveStatusSnapshot = LiveStatus(
            usedLastMinute = w.minute.size,
            endpointLimitPerMinute = liveMinute,
            cooldownRemainingMs = (w.cooldownUntil - now).coerceAtLeast(0L),
            halfOpenProbeInFlight = w.probeInFlight,
            rateLimitResponses = liveRateLimits.get(),
            activeGapMs = livePace.gapMs,
            fixedMinuteBucket = DualMinuteAdmissionPolicy.minuteId(System.currentTimeMillis()),
            fixedMinuteUsed = DualMinuteAdmissionPolicy.normalize(
                w.calendar, System.currentTimeMillis()).used,
            fixedMinuteRemaining = DualMinuteAdmissionPolicy.headroom(
                w.calendar, System.currentTimeMillis(), liveMinute),
            rollingMinuteRemaining = (liveMinute - w.minute.size).coerceAtLeast(0)
        )
    }

    private suspend fun <T> readRateLimited(
        type: Type, priority: Priority, priorityQueueWaitMs: Long, block: suspend () -> T
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
                while (w.admissionTimesEpochMs.isNotEmpty() &&
                    w.admissionTimesEpochMs.first() <= wallNow - 60_000L) w.admissionTimesEpochMs.removeFirst()
                w.calendar = DualMinuteAdmissionPolicy.normalize(w.calendar, wallNow)
                val fixedMinuteWait = DualMinuteAdmissionPolicy.calendarWaitMs(
                    w.calendar, wallNow, perMinute)
                val spacingWait = if (w.lastGrantedAt > 0L)
                    (w.lastGrantedAt + (if (type == Type.LIVE) livePace.gapMs else 1_500L) - now)
                        .coerceAtLeast(0L) else 0L
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
                    w.lastGrantedAt = now
                    w.calendar = DualMinuteAdmissionPolicy.reserve(w.calendar, wallNow)
                    w.admissionTimesEpochMs.addLast(wallNow)
                    if (w.probeRequired) w.probeInFlight = true
                    if (!persistRecoveryState(type, now)) throw IOException("BETA_RECOVERY_STATE_PERSIST_FAILED")
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
        val combinedWaitMs = totalWaitMs + priorityQueueWaitMs
        if (combinedWaitMs > 0L) {
            if (type == Type.LIVE) {
                livePermitWaitRequests.incrementAndGet()
                livePermitWaitMs.addAndGet(combinedWaitMs)
            } else {
                historicalPermitWaitRequests.incrementAndGet()
                historicalPermitWaitMs.addAndGet(combinedWaitMs)
            }
            auditLogger.logAsync("API_RATE_LIMIT", "BETA_CLIENT_GOVERNOR_WAIT", mapOf(
                "account" to "BETA", "category" to if (type == Type.LIVE) "LIVE_DATA" else "HISTORICAL",
                "priority" to priority.name, "wait_ms" to combinedWaitMs,
                "pacing_wait_ms" to totalWaitMs, "priority_queue_wait_ms" to priorityQueueWaitMs,
                "wait_iterations" to waitIterations,
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
                val now = SystemClock.elapsedRealtime()
                val wasProbe = w.probeInFlight
                if (wasProbe) { w.probeInFlight = false; w.probeRequired = false }
                if (type == Type.LIVE) {
                    val priorPace = livePace
                    livePace = BetaLtpAdaptivePacingPolicy.onSuccess(livePace, now)
                    if (wasProbe || priorPace != livePace) persistRecoveryState(type, now)
                    updateLiveStatus(now, w)
                } else if (wasProbe) persistRecoveryState(type, now)
            }
            result
        } catch (failure: Throwable) {
            if (type == Type.LIVE) liveFailures.incrementAndGet() else historicalFailures.incrementAndGet()
            var appliedCooldownMs = 0L
            var retryAfterRaw: String? = null
            var incidentCount = 0
            var endpointPath = ""
            var cooldownUntilWallMs = 0L
            withContext(NonCancellable) {
                lock.withLock {
                val w = windows.getValue(type)
                val now = SystemClock.elapsedRealtime()
                val wallNow = System.currentTimeMillis()
                var persistState = false
                if (failure is HttpException && failure.code() == 429) {
                    endpointPath = failure.response()?.raw()?.request?.url?.encodedPath.orEmpty()
                    if (type == Type.LIVE) {
                        liveRateLimits.incrementAndGet()
                        livePace = BetaLtpAdaptivePacingPolicy.on429(livePace, now)
                    } else historicalRateLimits.incrementAndGet()
                    while (w.rateLimitIncidents.isNotEmpty() &&
                        w.rateLimitIncidents.first() <= wallNow - BetaRecoveryStatePolicy.INCIDENT_WINDOW_MS)
                        w.rateLimitIncidents.removeFirst()
                    if (w.rateLimitIncidents.lastOrNull()?.let { wallNow - it < 5_000L } != true)
                        w.rateLimitIncidents.addLast(wallNow)
                    incidentCount = w.rateLimitIncidents.size
                    retryAfterRaw = failure.response()?.headers()?.get("Retry-After")
                    val retryAfter = GrowwRetryAfterPolicy.retryAfterMs(retryAfterRaw, wallNow)
                    appliedCooldownMs = GrowwRecoveryPolicy.cooldownMs(incidentCount, retryAfter)
                    w.cooldownUntil = maxOf(w.cooldownUntil, now + appliedCooldownMs)
                    w.probeRequired = true
                    persistState = true
                } else if (w.probeInFlight) {
                    w.cooldownUntil = maxOf(w.cooldownUntil, now + 10_000L)
                    w.probeRequired = true
                    persistState = true
                }
                w.probeInFlight = false
                if (persistState) {
                    persistRecoveryState(type, now)
                    cooldownUntilWallMs = BetaRecoveryStatePolicy.cooldownUntilEpochMs(
                        wallNow, (w.cooldownUntil - now).coerceAtLeast(0L))
                }
                if (type == Type.LIVE) updateLiveStatus(now, w)
                }
            }
            if (failure is HttpException && failure.code() == 429) {
                val category = if (type == Type.LIVE) "LIVE_DATA" else "HISTORICAL"
                val wire = GrowwHttpTraceLedger.counters("BETA", category)
                auditLogger.logAsync("API_RATE_LIMIT", "BETA_HTTP_429", mapOf(
                    "account" to "BETA", "category" to category, "endpoint" to endpointPath,
                    "http_status" to 429, "retry_after_raw" to retryAfterRaw,
                    "applied_local_cooldown_ms" to appliedCooldownMs,
                    "cooldown_until_epoch_ms" to cooldownUntilWallMs,
                    "incident_count_last_hour" to incidentCount,
                    "active_gap_ms" to if (type == Type.LIVE) livePace.gapMs else 0L,
                    "local_per_second_limit" to if (type == Type.LIVE) liveSec else histSec,
                    "local_per_minute_limit" to if (type == Type.LIVE) liveMinute else histMinute,
                    "category_wire_requests_total" to wire.requests,
                    "category_wire_429_total" to wire.rateLimits,
                    "broker_category_limit_proven" to false,
                    "beta_read_only" to true, "alpha_cooldown_independent" to true
                ))
            }
            throw failure
        } finally {
            lastLatencyMs.set((SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
        }
    }

    private suspend fun persistRecoveryState(type: Type, nowElapsedMs: Long): Boolean {
        val wallNow = System.currentTimeMillis()
        val window = windows.getValue(type)
        val remaining = (window.cooldownUntil - nowElapsedMs).coerceAtLeast(0L)
        val editor = recoveryPreferences.edit()
            .putLong(cooldownKey(type), BetaRecoveryStatePolicy.cooldownUntilEpochMs(wallNow, remaining))
            .putBoolean(probeKey(type), window.probeRequired)
            .putString(incidentsKey(type), BetaRecoveryStatePolicy.serializeIncidents(window.rateLimitIncidents, wallNow))
            .putString(admissionsKey(type), BetaRecoveryStatePolicy.serializeAdmissions(window.admissionTimesEpochMs, wallNow))
        if (type == Type.LIVE) editor.putLong(LIVE_GAP_KEY, livePace.gapMs)
        val started = SystemClock.elapsedRealtime()
        val committed = withContext(Dispatchers.IO) { editor.commit() }
        val persistMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        if (!committed || persistMs >= 50L) auditLogger.logAsync("API_RATE_LIMIT",
            if (committed) "BETA_RECOVERY_PERSIST_SLOW" else "BETA_RECOVERY_STATE_PERSIST_FAILED",
            mapOf("account" to "BETA", "category" to type.name,
                "cooldown_remaining_ms" to remaining, "admission_count_last_minute" to window.minute.size,
                "persistence_elapsed_ms" to persistMs, "persist_succeeded" to committed))
        return committed
    }
    private fun cooldownKey(type: Type) = "cooldown_until_epoch_${type.name}"
    private fun probeKey(type: Type) = "probe_required_${type.name}"
    private fun incidentsKey(type: Type) = "incidents_epoch_${type.name}"
    private fun admissionsKey(type: Type) = "admissions_epoch_${type.name}"

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
        private const val LIVE_GAP_KEY = "live_adaptive_gap_ms"
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
