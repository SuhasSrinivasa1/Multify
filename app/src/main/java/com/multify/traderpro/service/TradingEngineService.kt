package com.multify.traderpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.multify.traderpro.R
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.GrowwApiUsageLedger
import com.multify.traderpro.data.network.GrowwRecoveryGate
import com.multify.traderpro.data.repository.TradingRepository
import com.multify.traderpro.engine.ReleaseIntegrityManifest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TradingEngineService : Service() {
    @Inject lateinit var repository: TradingRepository
    @Inject lateinit var auditLogger: AuditLogger
    @Inject lateinit var recoveryGate: GrowwRecoveryGate

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engineJob: Job? = null
    private var positionMonitorJob: Job? = null
    private var fixedScanJob: Job? = null
    private var forecastScanJob: Job? = null
    private var positionMonitoringWakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastKnownManagedCount = 0
    @Volatile private var lastManagedMonitorSucceeded = true
    private var lastAfterHoursResearchAtMs = 0L
    private var lastAfterHoursMaintenanceAtMs = 0L
    private var lastApiUsageAuditAtMs = 0L
    private var positionProfileWindowStartedAtMs = 0L
    private var positionProfilePasses = 0
    private var positionProfileFailures = 0
    private var positionProfileElapsedMs = 0L
    private var positionProfileMaxMs = 0L
    private var positionProfileUsageBaseline: GrowwApiUsageLedger.Snapshot? = null
    @Volatile private var lastForecastOutcomeCount = 0
    @Volatile private var lastFixedOutcomeCount = 0

    override fun onCreate() {
        super.onCreate()
        createChannels()
        check(ReleaseIntegrityManifest.verifyRuntimeConstants()) { "Fixed/trailing release integrity mismatch" }
        auditLogger.log("INTEGRITY", "RELEASE_INVARIANTS_VERIFIED", mapOf(
            "release_family" to ReleaseIntegrityManifest.RELEASE_FAMILY,
            "fixed_contract" to ReleaseIntegrityManifest.FIXED_CONTRACT_ID,
            "fixed_source_commit" to ReleaseIntegrityManifest.FIXED_SOURCE_COMMIT,
            "trailing_arm_pct" to ReleaseIntegrityManifest.TRAIL_ARM_PCT,
            "trailing_initial_gain_pct" to ReleaseIntegrityManifest.TRAIL_INITIAL_PROTECTED_GAIN_PCT,
            "trailing_step_pct" to ReleaseIntegrityManifest.TRAIL_STEP_PCT,
            "hard_stop_loss_pct" to -ReleaseIntegrityManifest.HARD_STOP_LOSS_PCT
        ))
        startForeground(FOREGROUND_NOTIFICATION_ID, engineNotification("Starting production engine…"))
        // Keep the CPU awake until the first successful pass confirms whether holdings need monitoring.
        repository.setTradingRecoveryReady(false)
        updatePositionMonitoringWakeLock(true, "INITIAL_POSITION_DISCOVERY")
        startEngineLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startEngineLoop()
        if (intent?.action == ACTION_SAMPLE_SIGNAL) {
            val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
            val symbol = intent.getStringExtra(EXTRA_SYMBOL).orEmpty()
            if (eventId > 0L) {
                scope.launch {
                    runCatching { repository.sampleSignal(eventId) }
                        .onFailure {
                            if (it !is CancellationException) {
                                auditLogger.log("MULTIFY_RESEARCH", "POST_SIGNAL_SAMPLE_ERROR", mapOf(
                                    "event_id" to eventId,
                                    "symbol" to symbol,
                                    "message" to (it.message ?: it.javaClass.simpleName)
                                ))
                            }
                        }
                }
            }
            if (eventId > 0L && symbol.isNotBlank()) {
                scope.launch {
                    runCatching { repository.captureMultifyResearchSnapshot(eventId, symbol) }
                        .onFailure {
                            if (it !is CancellationException) {
                                auditLogger.log("MULTIFY_RESEARCH", "FEATURE_CAPTURE_ERROR", mapOf(
                                    "event_id" to eventId,
                                    "symbol" to symbol,
                                    "message" to (it.message ?: it.javaClass.simpleName)
                                ))
                            }
                        }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updatePositionMonitoringWakeLock(false, "SERVICE_DESTROYED")
        positionMonitorJob?.cancel()
        fixedScanJob?.cancel()
        forecastScanJob?.cancel()
        engineJob?.cancel()
        scope.cancel()
        auditLogger.log("RUNTIME", "FOREGROUND_ENGINE_DESTROYED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startEngineLoop() {
        startPositionMonitorWorker()
        startFixedScanWorker()
        startForecastScanWorker()
        if (engineJob?.isActive == true) return
        engineJob = scope.launch {
            auditLogger.log("RUNTIME", "FOREGROUND_ENGINE_STARTED", mapOf(
                "position_monitor_independent" to true,
                "fixed_scan_independent" to true,
                "forecast_scan_independent" to true,
                "fixed_not_blocked_by_forecast" to true
            ))
            // Capture the immutable Fixed reference state before adaptive writes.
            runCatching { repository.ensureFixedEngineSnapshot() }
                .onFailure { auditLogger.log("RUNTIME", "FIXED_SNAPSHOT_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
            runCatching { repository.ensureHistoricalSeed() }

            var lastHeartbeatAtMs = 0L
            while (isActive) {
                try {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastHeartbeatAtMs >= HEARTBEAT_INTERVAL_MS) {
                        repository.recordServiceHeartbeat()
                        lastHeartbeatAtMs = nowMs
                    }

                    runCatching { repository.maintainArmHotPath() }
                        .onFailure { auditLogger.log("RUNTIME", "ARM_HOT_PATH_MAINTAIN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }

                    val profileNowMs = System.currentTimeMillis()
                    if (profileNowMs - positionProfileWindowStartedAtMs >= POSITION_PROFILE_INTERVAL_MS) {
                        val before = positionProfileUsageBaseline
                        val after = GrowwApiUsageLedger.snapshot()
                        fun delta(category: GrowwApiUsageLedger.Category, selector: (GrowwApiUsageLedger.CategorySnapshot) -> Long): Long {
                            if (before == null) return 0L
                            val b = before.categories.first { it.category == category }
                            val a = after.categories.first { it.category == category }
                            return (selector(a) - selector(b)).coerceAtLeast(0L)
                        }
                        auditLogger.logAsync("RUNTIME", "TRAILING_MONITOR_PROFILE", mapOf(
                            "window_ms" to (profileNowMs - positionProfileWindowStartedAtMs),
                            "passes" to positionProfilePasses,
                            "failures" to positionProfileFailures,
                            "average_pass_ms" to if (positionProfilePasses > 0) positionProfileElapsedMs / positionProfilePasses else 0L,
                            "max_pass_ms" to positionProfileMaxMs,
                            "last_known_managed_count" to lastKnownManagedCount,
                            "live_network_requests" to delta(GrowwApiUsageLedger.Category.LIVE_DATA) { it.totalRequests },
                            "order_network_requests" to delta(GrowwApiUsageLedger.Category.ORDERS) { it.totalRequests },
                            "non_trading_network_requests" to delta(GrowwApiUsageLedger.Category.NON_TRADING) { it.totalRequests },
                            "live_429s" to delta(GrowwApiUsageLedger.Category.LIVE_DATA) { it.rateLimitResponses },
                            "order_429s" to delta(GrowwApiUsageLedger.Category.ORDERS) { it.rateLimitResponses },
                            "non_trading_429s" to delta(GrowwApiUsageLedger.Category.NON_TRADING) { it.rateLimitResponses },
                            "governor_wait_ms" to (
                                delta(GrowwApiUsageLedger.Category.LIVE_DATA) { it.permitWaitMs } +
                                delta(GrowwApiUsageLedger.Category.ORDERS) { it.permitWaitMs } +
                                delta(GrowwApiUsageLedger.Category.NON_TRADING) { it.permitWaitMs }
                            )
                        ))
                        positionProfileWindowStartedAtMs = profileNowMs
                        positionProfilePasses = 0
                        positionProfileFailures = 0
                        positionProfileElapsedMs = 0L
                        positionProfileMaxMs = 0L
                        positionProfileUsageBaseline = after
                    }

                    val now = ZonedDateTime.now(INDIA)
                    val regularSession = now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() < LocalTime.of(15, 30)

                    if (regularSession) {
                        // Market hours are intentionally clean: recommendation delivery lives behind
                        // each source's order path (Fixed worker / Forecast worker). This maintenance
                        // loop never races ahead and notifies before an enabled auto-buy attempt.
                    } else if (nowMs - lastAfterHoursMaintenanceAtMs >= AFTER_HOURS_MAINTENANCE_INTERVAL_MS) {
                        lastAfterHoursMaintenanceAtMs = nowMs
                        runCatching { repository.reconcileForecastAutoBuyOrders(true, "AFTER_HOURS_MAINTENANCE") }
                            .onFailure { auditLogger.log("RUNTIME", "FORECAST_AUTO_BUY_RECONCILE_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        runCatching { repository.reconcileFixedAutoBuyOrders(true, "AFTER_HOURS_MAINTENANCE") }
                            .onFailure { auditLogger.log("RUNTIME", "FIXED_AUTO_BUY_RECONCILE_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        lastForecastOutcomeCount = runCatching { repository.monitorForecastOutcomes() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FORECAST_OUTCOME_MONITOR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }.getOrDefault(lastForecastOutcomeCount)
                        lastFixedOutcomeCount = runCatching { repository.monitorFixedOutcomes() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_OUTCOME_MONITOR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }.getOrDefault(lastFixedOutcomeCount)
                    }

                    if (nowMs - lastApiUsageAuditAtMs >= API_USAGE_AUDIT_INTERVAL_MS) {
                        lastApiUsageAuditAtMs = nowMs
                        val usage = GrowwApiUsageLedger.snapshot()
                        auditLogger.logAsync("API_BUDGET", "ROLLING_USAGE", mapOf(
                            "summary" to usage.compact(),
                            "categories" to usage.categories.associate { row ->
                                row.category.name to mapOf(
                                    "used_1s" to row.usedLastSecond,
                                    "used_60s" to row.usedLastMinute,
                                    "app_limit_1s" to row.appPerSecondLimit,
                                    "app_limit_60s" to row.appPerMinuteLimit,
                                    "headroom_60s" to row.minuteHeadroom,
                                    "utilization_pct" to row.minuteUtilizationPct,
                                    "429s" to row.rateLimitResponses,
                                    "last_429_at_ms" to row.lastRateLimitAtMs,
                                    "governor_wait_count" to row.permitWaitCount,
                                    "governor_wait_ms_total" to row.permitWaitMs,
                                    "governor_wait_ms_max" to row.maxPermitWaitMs,
                                    "network_ms_total" to row.networkMs
                                )
                            },
                            "cache_hits_total" to GrowwApiUsageLedger.cacheHitCount(),
                            "cache_misses_total" to GrowwApiUsageLedger.cacheMissCount(),
                            "async_audit_dropped_total" to auditLogger.asyncDroppedCount()
                        ))
                    }

                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(15, 35) &&
                        nowMs - lastAfterHoursResearchAtMs >= AFTER_HOURS_RESEARCH_INTERVAL_MS
                    ) {
                        lastAfterHoursResearchAtMs = nowMs
                        runCatching { repository.runAfterHoursResearch(false) }
                            .onFailure { auditLogger.log("RUNTIME", "AFTER_HOURS_RESEARCH_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                    }

                    updateForegroundNotification(
                        lastKnownManagedCount,
                        lastForecastOutcomeCount,
                        lastFixedOutcomeCount,
                        lastManagedMonitorSucceeded
                    )
                    delay(MAINTENANCE_LOOP_INTERVAL_MS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    auditLogger.log("RUNTIME", "FOREGROUND_ENGINE_LOOP_ERROR", mapOf(
                        "type" to t.javaClass.simpleName,
                        "message" to (t.message ?: t.javaClass.simpleName)
                    ))
                    delay(ERROR_BACKOFF_MS)
                }
            }
        }
    }

    private fun startPositionMonitorWorker() {
        if (positionMonitorJob?.isActive == true) return
        positionMonitorJob = scope.launch {
            auditLogger.log("RUNTIME", "POSITION_MONITOR_WORKER_STARTED")
            while (isActive) {
                try {
                    val passStartedAtMs = System.currentTimeMillis()
                    if (positionProfileWindowStartedAtMs <= 0L) {
                        positionProfileWindowStartedAtMs = passStartedAtMs
                        positionProfileUsageBaseline = GrowwApiUsageLedger.snapshot()
                    }
                    val result = runCatching { repository.monitorManagedPositions() }
                    val passElapsedMs = (System.currentTimeMillis() - passStartedAtMs).coerceAtLeast(0L)
                    positionProfilePasses++
                    positionProfileElapsedMs += passElapsedMs
                    positionProfileMaxMs = maxOf(positionProfileMaxMs, passElapsedMs)
                    val failure = result.exceptionOrNull()
                    val succeeded = failure == null
                    lastManagedMonitorSucceeded = succeeded
                    if (succeeded) {
                        val count = result.getOrThrow().coerceAtLeast(0)
                        lastKnownManagedCount = count
                        if (!repository.brokerSessionHealthy()) {
                            // Local-only portfolio accounting is NOT successful broker reconciliation.
                            if (repository.isTradingRecoveryReady()) repository.setTradingRecoveryReady(false)
                            lastManagedMonitorSucceeded = false
                        } else if (!repository.isTradingRecoveryReady()) {
                            repository.setTradingRecoveryReady(true)
                        }
                        updatePositionMonitoringWakeLock(
                            shouldHold = count > 0,
                            reason = if (count > 0) "ACTIVE_MANAGED_HOLDINGS" else "NO_OPEN_MANAGED_HOLDINGS"
                        )
                    } else {
                        positionProfileFailures++
                        if (failure is CancellationException) throw failure
                        updatePositionMonitoringWakeLock(true, "MONITOR_ERROR_UNKNOWN_POSITION_STATE")
                        auditLogger.log("RUNTIME", "MANAGED_MONITOR_ERROR", mapOf(
                            "type" to (failure?.javaClass?.simpleName ?: "UnknownError"),
                            "message" to (failure?.message ?: "Managed position monitor failed"),
                            "last_known_managed_count" to lastKnownManagedCount,
                            "wake_lock_held" to (positionMonitoringWakeLock?.isHeld == true)
                        ))
                    }

                    val now = ZonedDateTime.now(INDIA)
                    val regularSession = now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() < LocalTime.of(15, 30)
                    delay(
                        when {
                            !regularSession -> CLOSED_SESSION_LOOP_INTERVAL_MS
                            !succeeded -> POSITION_MONITOR_ERROR_RETRY_MS
                            lastKnownManagedCount > 1 -> PORTFOLIO_POSITION_LOOP_INTERVAL_MS
                            lastKnownManagedCount > 0 -> POSITION_LOOP_INTERVAL_MS
                            else -> IDLE_POSITION_LOOP_INTERVAL_MS
                        }
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    lastManagedMonitorSucceeded = false
                    updatePositionMonitoringWakeLock(true, "POSITION_WORKER_ERROR")
                    auditLogger.log("RUNTIME", "POSITION_MONITOR_WORKER_ERROR", mapOf(
                        "type" to t.javaClass.simpleName,
                        "message" to (t.message ?: t.javaClass.simpleName)
                    ))
                    delay(POSITION_MONITOR_ERROR_RETRY_MS)
                }
            }
        }
    }

    private fun startFixedScanWorker() {
        if (fixedScanJob?.isActive == true) return
        fixedScanJob = scope.launch {
            auditLogger.log("RUNTIME", "FIXED_SCAN_WORKER_STARTED", mapOf(
                "priority" to "FIXED_FIRST",
                "blocked_by_adaptive_forecast" to false
            ))
            while (isActive) {
                try {
                    val now = ZonedDateTime.now(INDIA)
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 0) &&
                        now.toLocalTime() < LocalTime.of(9, 15)
                    ) {
                        runCatching { repository.prepareFixedPreMarketWarmup() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_PREMARKET_WARMUP_ERROR", mapOf(
                                        "message" to (it.message ?: it.javaClass.simpleName)
                                    ))
                                }
                            }
                    }
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() < LocalTime.of(15, 30)
                    ) {
                        runCatching { repository.maintainFixedHotDataCache() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_HOT_CACHE_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }
                        val usageBefore = GrowwApiUsageLedger.snapshot()
                        val cacheHitsBefore = GrowwApiUsageLedger.cacheHitCount()
                        val startedAt = System.currentTimeMillis()
                        val scanned = runCatching { repository.runFixedPriorityCycle() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }.getOrDefault(false)
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (scanned) {
                            val usageAfter = GrowwApiUsageLedger.snapshot()
                            fun requestDelta(category: GrowwApiUsageLedger.Category): Long {
                                val before = usageBefore.categories.first { it.category == category }.totalRequests
                                val after = usageAfter.categories.first { it.category == category }.totalRequests
                                return (after - before).coerceAtLeast(0L)
                            }
                            auditLogger.logAsync("RUNTIME", "FIXED_SCAN_PROFILE", mapOf(
                                "elapsed_ms" to elapsed,
                                "live_network_requests" to requestDelta(GrowwApiUsageLedger.Category.LIVE_DATA),
                                "historical_network_requests" to requestDelta(GrowwApiUsageLedger.Category.HISTORICAL),
                                "non_trading_network_requests" to requestDelta(GrowwApiUsageLedger.Category.NON_TRADING),
                                "live_429s" to (
                                    usageAfter.categories.first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }.rateLimitResponses -
                                        usageBefore.categories.first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }.rateLimitResponses
                                    ).coerceAtLeast(0L),
                                "historical_429s" to (
                                    usageAfter.categories.first { it.category == GrowwApiUsageLedger.Category.HISTORICAL }.rateLimitResponses -
                                        usageBefore.categories.first { it.category == GrowwApiUsageLedger.Category.HISTORICAL }.rateLimitResponses
                                    ).coerceAtLeast(0L),
                                "governor_wait_ms" to (
                                    (usageAfter.categories.first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }.permitWaitMs -
                                        usageBefore.categories.first { it.category == GrowwApiUsageLedger.Category.LIVE_DATA }.permitWaitMs).coerceAtLeast(0L) +
                                    (usageAfter.categories.first { it.category == GrowwApiUsageLedger.Category.HISTORICAL }.permitWaitMs -
                                        usageBefore.categories.first { it.category == GrowwApiUsageLedger.Category.HISTORICAL }.permitWaitMs).coerceAtLeast(0L)
                                    ),
                                "cache_hits" to (GrowwApiUsageLedger.cacheHitCount() - cacheHitsBefore).coerceAtLeast(0L),
                                "evaluation_concurrency_high_end" to 8,
                                "fixed_formula_changed" to false
                            ))
                        }
                        if (scanned && elapsed >= SCAN_DURATION_AUDIT_THRESHOLD_MS) {
                            auditLogger.log("RUNTIME", "FIXED_SCAN_DURATION", mapOf(
                                "elapsed_ms" to elapsed,
                                "parallel_scheduler" to true,
                                "forecast_blocking_ms" to 0
                            ))
                        }
                        // runFixedPriorityCycle returns only after Fixed auto-buy has been attempted.
                        // Presentation therefore remains strictly behind the order path.
                        if (scanned) {
                            scope.launch {
                                notifyNewFixedRecommendationsAfterOrderPath()
                            }
                        }
                    }
                    delay(SCAN_WORKER_POLL_MS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    auditLogger.log("RUNTIME", "FIXED_SCAN_WORKER_ERROR", mapOf(
                        "type" to t.javaClass.simpleName,
                        "message" to (t.message ?: t.javaClass.simpleName)
                    ))
                    delay(ERROR_BACKOFF_MS)
                }
            }
        }
    }

    private fun startForecastScanWorker() {
        if (forecastScanJob?.isActive == true) return
        forecastScanJob = scope.launch {
            auditLogger.log("RUNTIME", "FORECAST_SCAN_WORKER_STARTED", mapOf(
                "parallel_with_fixed" to false,
                "default_state" to "OPPORTUNISTIC_FIXED_PRIORITY"
            ))
            while (isActive) {
                try {
                    // Forecast discovery is always present but strictly opportunistic. There is no
                    // fourth trading toggle: Multify ARM, Fixed auto-buy and Forecast auto-buy are
                    // the only execution permissions. MarketPriorityPolicy keeps Forecast behind Fixed.
                    val now = ZonedDateTime.now(INDIA)
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() <= LocalTime.of(15, 0)
                    ) {
                        val usageBefore = GrowwApiUsageLedger.snapshot()
                        val cacheHitsBefore = GrowwApiUsageLedger.cacheHitCount()
                        val startedAt = System.currentTimeMillis()
                        val scanned = runCatching { repository.runForecastOpportunisticCycle() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FORECAST_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }.getOrDefault(false)
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (scanned) {
                            val usageAfter = GrowwApiUsageLedger.snapshot()
                            fun delta(category: GrowwApiUsageLedger.Category, selector: (GrowwApiUsageLedger.CategorySnapshot) -> Long): Long {
                                val before = usageBefore.categories.first { it.category == category }
                                val after = usageAfter.categories.first { it.category == category }
                                return (selector(after) - selector(before)).coerceAtLeast(0L)
                            }
                            auditLogger.logAsync("RUNTIME", "FORECAST_SCAN_PROFILE", mapOf(
                                "elapsed_ms" to elapsed,
                                "live_network_requests" to delta(GrowwApiUsageLedger.Category.LIVE_DATA) { it.totalRequests },
                                "historical_network_requests" to delta(GrowwApiUsageLedger.Category.HISTORICAL) { it.totalRequests },
                                "non_trading_network_requests" to delta(GrowwApiUsageLedger.Category.NON_TRADING) { it.totalRequests },
                                "live_429s" to delta(GrowwApiUsageLedger.Category.LIVE_DATA) { it.rateLimitResponses },
                                "historical_429s" to delta(GrowwApiUsageLedger.Category.HISTORICAL) { it.rateLimitResponses },
                                "governor_wait_ms" to (
                                    delta(GrowwApiUsageLedger.Category.LIVE_DATA) { it.permitWaitMs } +
                                    delta(GrowwApiUsageLedger.Category.HISTORICAL) { it.permitWaitMs } +
                                    delta(GrowwApiUsageLedger.Category.NON_TRADING) { it.permitWaitMs }
                                ),
                                "cache_hits" to (GrowwApiUsageLedger.cacheHitCount() - cacheHitsBefore).coerceAtLeast(0L),
                                "fixed_priority_architecture" to true
                            ))
                        }
                        if (scanned && elapsed >= SCAN_DURATION_AUDIT_THRESHOLD_MS) {
                            auditLogger.log("RUNTIME", "FORECAST_SCAN_DURATION", mapOf(
                                "elapsed_ms" to elapsed,
                                "parallel_scheduler" to true
                            ))
                        }
                        // Presentation is strictly behind the Forecast order path. If Multify or
                        // Fixed takes priority during the order pass, repository defers delivery.
                        runCatching { repository.pendingForecastNotificationsAfterOrderPath() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FORECAST_ORDER_FIRST_DELIVERY_ERROR", mapOf(
                                        "message" to (it.message ?: it.javaClass.simpleName)
                                    ))
                                }
                            }
                            .getOrDefault(emptyList())
                            .forEach(::notifyForecast)
                    }
                    delay(SCAN_WORKER_POLL_MS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    auditLogger.log("RUNTIME", "FORECAST_SCAN_WORKER_ERROR", mapOf(
                        "type" to t.javaClass.simpleName,
                        "message" to (t.message ?: t.javaClass.simpleName)
                    ))
                    delay(ERROR_BACKOFF_MS)
                }
            }
        }
    }

    private fun notifyNewFixedRecommendationsAfterOrderPath() {
        val rows = repository.fixedRecommendationsForNotification()
        if (rows.isEmpty()) return
        val prefs = getSharedPreferences(FIXED_NOTIFICATION_PREFS, Context.MODE_PRIVATE)
        val today = ZonedDateTime.now(INDIA).toLocalDate().toString()
        val storedDate = prefs.getString(FIXED_NOTIFICATION_DATE_KEY, "").orEmpty()
        val seen = if (storedDate == today) {
            prefs.getStringSet(FIXED_NOTIFICATION_KEYS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        } else {
            mutableSetOf()
        }

        var changed = storedDate != today
        rows.forEach { row ->
            val key = "${row.symbol.uppercase(Locale.US)}|${row.generatedAtMs}"
            if (!seen.add(key)) return@forEach
            changed = true
            notifyFixed(row)
        }
        if (changed) {
            prefs.edit()
                .putString(FIXED_NOTIFICATION_DATE_KEY, today)
                .putStringSet(FIXED_NOTIFICATION_KEYS_KEY, seen)
                .apply()
        }
    }

    private fun notifyFixed(f: ForecastDto) {
        val manager = getSystemService(NotificationManager::class.java)
        val postedAtMs = System.currentTimeMillis()
        val message = buildString {
            append("PRIMARY · entry ₹")
            append(String.format(Locale.US, "%.2f", f.entryPrice))
            append(" · target +")
            append(String.format(Locale.US, "%.2f", f.targetPct))
            append("% · ")
            append(String.format(Locale.US, "%.0f%% confidence", f.confidence * 100.0))
            append(" · auto-buy path processed first")
        }
        val notification = NotificationCompat.Builder(this, FIXED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trader)
            .setContentTitle("Fixed BUY · " + f.symbol)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message + "\n" + f.reason))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        manager.notify((f.symbol.hashCode() xor f.generatedAtMs.toInt() xor FIXED_NOTIFICATION_ID_SALT).and(Int.MAX_VALUE), notification)
        auditLogger.logAsync("EXECUTION_LATENCY", "FIXED_NOTIFICATION_POSTED", mapOf(
            "symbol" to f.symbol,
            "recommendation_generated_at_ms" to f.generatedAtMs,
            "notification_posted_at_ms" to postedAtMs,
            "recommendation_to_notification_ms" to (postedAtMs - f.generatedAtMs).coerceAtLeast(0L),
            "posted_after_fixed_priority_cycle" to true,
            "posted_after_auto_buy_attempt" to true,
            "fixed_formula_changed" to false
        ))
    }

    private fun notifyForecast(f: ForecastDto) {
        val manager = getSystemService(NotificationManager::class.java)
        val postedAtMs = System.currentTimeMillis()
        val message = buildString {
            append(f.qualityTier)
            append(" · entry ₹")
            append(String.format(Locale.US, "%.2f", f.entryPrice))
            append(" · target +")
            append(String.format(Locale.US, "%.2f", f.targetPct))
            append("% · ")
            append(String.format(Locale.US, "%.0f%% confidence", f.confidence * 100.0))
            if (!f.executionEligible) append(" · observation only")
        }
        val notification = NotificationCompat.Builder(this, FORECAST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trader)
            .setContentTitle("LONG forecast · " + f.symbol)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message + "\n" + f.reason))
            .setAutoCancel(true)
            .build()
        manager.notify((f.symbol.hashCode() xor f.generatedAtMs.toInt()).and(Int.MAX_VALUE), notification)
        auditLogger.logAsync("EXECUTION_LATENCY", "FORECAST_NOTIFICATION_POSTED", mapOf(
            "symbol" to f.symbol,
            "quality_tier" to f.qualityTier,
            "recommendation_generated_at_ms" to f.generatedAtMs,
            "notification_posted_at_ms" to postedAtMs,
            "recommendation_to_notification_ms" to (postedAtMs - f.generatedAtMs).coerceAtLeast(0L),
            "execution_eligible" to f.executionEligible,
            "fixed_formula_changed" to false
        ))
    }

    private fun updatePositionMonitoringWakeLock(shouldHold: Boolean, reason: String) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (shouldHold) {
            val wakeLock = positionMonitoringWakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:PositionMonitoring"
            ).apply { setReferenceCounted(false) }.also { positionMonitoringWakeLock = it }
            if (!wakeLock.isHeld) {
                runCatching { wakeLock.acquire() }
                    .onSuccess {
                        auditLogger.log("RUNTIME", "POSITION_WAKE_LOCK_ACQUIRED", mapOf("reason" to reason))
                    }
                    .onFailure {
                        auditLogger.log("RUNTIME", "POSITION_WAKE_LOCK_ACQUIRE_FAILED", mapOf(
                            "reason" to reason,
                            "message" to (it.message ?: it.javaClass.simpleName)
                        ))
                    }
            }
        } else {
            val wakeLock = positionMonitoringWakeLock
            if (wakeLock?.isHeld == true) {
                runCatching { wakeLock.release() }
                    .onSuccess {
                        auditLogger.log("RUNTIME", "POSITION_WAKE_LOCK_RELEASED", mapOf("reason" to reason))
                    }
                    .onFailure {
                        auditLogger.log("RUNTIME", "POSITION_WAKE_LOCK_RELEASE_FAILED", mapOf(
                            "reason" to reason,
                            "message" to (it.message ?: it.javaClass.simpleName)
                        ))
                    }
            }
        }
    }

    private fun updateForegroundNotification(
        managedCount: Int,
        forecastOutcomeCount: Int,
        fixedOutcomeCount: Int,
        managedMonitorSucceeded: Boolean
    ) {
        val monitorStatus = when {
            recoveryGate.requiresReconnect() -> "Groww login expired — RECONNECT in Settings; broker orders paused · " +
                "$managedCount last-known holding(s); verify GTT in Groww"
            !managedMonitorSucceeded -> "Monitor error · $managedCount last-known holding(s) · " +
                if (positionMonitoringWakeLock?.isHeld == true) "CPU awake; retrying" else "CPU wake lock unavailable"
            managedCount <= 0 -> "No open holdings"
            positionMonitoringWakeLock?.isHeld == true -> "Monitoring $managedCount holding(s) · CPU awake"
            else -> "Monitoring $managedCount holding(s) · CPU wake lock unavailable"
        }
        val text = monitorStatus + " · " + forecastOutcomeCount + " adaptive + " + fixedOutcomeCount + " fixed outcomes" +
            " · " + GrowwApiUsageLedger.snapshot().compact()
        getSystemService(NotificationManager::class.java)
            .notify(FOREGROUND_NOTIFICATION_ID, engineNotification(text))
    }

    private fun engineNotification(text: String): Notification =
        NotificationCompat.Builder(this, ENGINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trader)
            .setContentTitle("Multify Trader Pro")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ENGINE_CHANNEL_ID,
                "Production trading engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps holdings monitoring, forecasting and research alive independently of the notification listener." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FIXED_CHANNEL_ID,
                "Fixed BUY recommendations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Frozen Fixed PRIMARY recommendations, posted only after the Fixed auto-buy path has been processed." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FORECAST_CHANNEL_ID,
                "Forecast opportunities",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Production-qualified PRIMARY forecast opportunities only." }
        )
    }

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private const val ENGINE_CHANNEL_ID = "production_engine"
        private const val FIXED_CHANNEL_ID = "fixed_buy_recommendations_v773"
        private const val FORECAST_CHANNEL_ID = "forecast_opportunities"
        private const val FIXED_NOTIFICATION_PREFS = "fixed_notification_v773"
        private const val FIXED_NOTIFICATION_DATE_KEY = "date"
        private const val FIXED_NOTIFICATION_KEYS_KEY = "keys"
        private const val FIXED_NOTIFICATION_ID_SALT = 0x773
        private const val FOREGROUND_NOTIFICATION_ID = 68090
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val API_USAGE_AUDIT_INTERVAL_MS = 30_000L
        private const val POSITION_LOOP_INTERVAL_MS = 1_000L
        private const val PORTFOLIO_POSITION_LOOP_INTERVAL_MS = 1_000L
        private const val POSITION_MONITOR_ERROR_RETRY_MS = 1_000L
        private const val POSITION_PROFILE_INTERVAL_MS = 30_000L
        private const val IDLE_POSITION_LOOP_INTERVAL_MS = 5_000L
        private const val SCAN_WORKER_POLL_MS = 1_000L
        private const val FORECAST_PAUSED_POLL_MS = 5_000L
        private const val SCAN_DURATION_AUDIT_THRESHOLD_MS = 2_000L
        private const val MAINTENANCE_LOOP_INTERVAL_MS = 5_000L
        private const val CLOSED_SESSION_LOOP_INTERVAL_MS = 60_000L
        private const val ERROR_BACKOFF_MS = 10_000L
        private const val AFTER_HOURS_RESEARCH_INTERVAL_MS = 15L * 60L * 1000L
        private const val AFTER_HOURS_MAINTENANCE_INTERVAL_MS = 60_000L
        private const val ACTION_SAMPLE_SIGNAL = "com.multify.traderpro.action.SAMPLE_SIGNAL"
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_SYMBOL = "symbol"

        fun start(context: Context): Boolean = runCatching {
            val intent = Intent(context, TradingEngineService::class.java)
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrDefault(false)

        fun sampleSignal(context: Context, eventId: Long, symbol: String?): Boolean = runCatching {
            val intent = Intent(context, TradingEngineService::class.java)
                .setAction(ACTION_SAMPLE_SIGNAL)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_SYMBOL, symbol.orEmpty())
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrDefault(false)
    }
}
