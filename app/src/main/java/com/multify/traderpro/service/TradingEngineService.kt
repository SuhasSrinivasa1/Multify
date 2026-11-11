package com.multify.traderpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.multify.traderpro.R
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.network.ForecastDto
import com.multify.traderpro.data.network.GrowwApiUsageLedger
import com.multify.traderpro.data.network.GrowwHttpTraceLedger
import com.multify.traderpro.data.network.FixedScanTrendStore
import com.multify.traderpro.data.network.GrowwRecoveryGate
import com.multify.traderpro.data.network.BetaResearchGate
import com.multify.traderpro.engine.FixedDataPipelinePolicy
import com.multify.traderpro.engine.FixedPublicationTimingPolicy
import com.multify.traderpro.engine.MobileEngineHealthPolicy
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
import kotlinx.coroutines.withTimeoutOrNull
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
    @Inject lateinit var betaResearchGate: BetaResearchGate

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engineJob: Job? = null
    private var mobileHealthJob: Job? = null
    private var positionMonitorJob: Job? = null
    private var fixedScanJob: Job? = null
    private var forecastScanJob: Job? = null
    private var forecastNewsJob: Job? = null
    private var positionMonitoringWakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastKnownManagedCount = 0
    @Volatile private var lastManagedMonitorSucceeded = true
    private var lastAfterHoursResearchAtMs = 0L
    private var lastAfterHoursMaintenanceAtMs = 0L
    private var lastFixedOutcomePollAtMs = 0L
    private var lastForecastOutcomePollAtMs = 0L
    private var lastNewsDailyReportDate = ""
    private var lastApiUsageAuditAtMs = 0L
    private var positionProfileWindowStartedAtMs = 0L
    private var positionProfilePasses = 0
    private var positionProfileFailures = 0
    private var positionProfileElapsedMs = 0L
    private var positionProfileMaxMs = 0L
    private var positionProfileUsageBaseline: GrowwApiUsageLedger.Snapshot? = null
    @Volatile private var lastForecastOutcomeCount = 0
    @Volatile private var lastFixedOutcomeCount = 0
    @Volatile private var lastFixedCycleCompletedAtMs = 0L
    @Volatile private var lastPositionPassSucceededAtMs = 0L
    @Volatile private var mobileHealthStatus = "INITIALIZING"
    private var lastListenerRebindAtMs = 0L

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
            // Post-signal discretionary research is frozen; the notification listener
            // still independently processes the live Multify BUY/order path.
            auditLogger.logAsync("RUNTIME", "POST_SIGNAL_RESEARCH_FROZEN")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updatePositionMonitoringWakeLock(false, "SERVICE_DESTROYED")
        positionMonitorJob?.cancel()
        fixedScanJob?.cancel()
        forecastScanJob?.cancel()
        forecastNewsJob?.cancel()
        engineJob?.cancel()
        mobileHealthJob?.cancel()
        scope.cancel()
        auditLogger.log("RUNTIME", "FOREGROUND_ENGINE_DESTROYED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Independent Forecast-only news I/O. SELL/Fixed keep priority; no new order path. */
    private fun startForecastNewsWorker() {
        if (forecastNewsJob?.isActive == true) return
        forecastNewsJob = scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            auditLogger.logAsync("FORECAST_NEWS", "BACKGROUND_MONITOR_STARTED",
                mapOf("requested_cadence_ms" to 60_000L))
            while (isActive) {
                runCatching {
                    repository.monitorForecastNews()
                    repository.monitorForecastNewsOutcomes()
                    val studyDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()
                    if (studyDate != lastNewsDailyReportDate) {
                        repository.auditForecastNewsLearning()
                        lastNewsDailyReportDate = studyDate
                    }
                }.onFailure { if (it !is CancellationException) {
                    auditLogger.logAsync("FORECAST_NEWS", "BACKGROUND_CYCLE_ERROR", mapOf(
                        "type" to it.javaClass.simpleName, "message" to (it.message ?: "").take(160)))
                } }
                delay(60_000L)
            }
        }
    }

    private fun startEngineLoop() {
        startMobileHealthWorker()
        startPositionMonitorWorker()
        startFixedScanWorker()
        // Forecast is completely frozen in v8.3. Alpha/Beta belong to Fixed;
        // neither Forecast scanner nor news worker is started.
        forecastScanJob?.cancel()
        forecastNewsJob?.cancel()
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
            auditLogger.log("RUNTIME", "FORECAST_HARD_FROZEN", mapOf(
                "forecast_scans" to false, "forecast_news" to false,
                "forecast_replay" to false, "forecast_auto_buy" to false,
                "beta_reserved_for_fixed" to true
            ))

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
                            // Ledger is process-wide, including Fixed and other readers.
                            // These are NOT position-monitor-owned HTTP calls.
                            "api_request_count_scope" to "PROCESS_WIDE_ALPHA_NOT_TRAILING_OWNED",
                            "no_app_owned_positions_at_profile_time" to (lastKnownManagedCount == 0),
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
                    val regularSession = com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(now)

                    if (regularSession) {
                        // Market hours are intentionally clean: recommendation delivery lives behind
                        // each source's order path (Fixed worker / Forecast worker). This maintenance
                        // loop never races ahead and notifies before an enabled auto-buy attempt.
                    } else if (nowMs - lastAfterHoursMaintenanceAtMs >= AFTER_HOURS_MAINTENANCE_INTERVAL_MS) {
                        lastAfterHoursMaintenanceAtMs = nowMs
                        // No Forecast order placement; existing Alpha-managed holdings
                        // continue through the independent portfolio/GTT worker.
                        runCatching { repository.reconcileFixedAutoBuyOrders(true, "AFTER_HOURS_MAINTENANCE") }
                            .onFailure { auditLogger.log("RUNTIME", "FIXED_AUTO_BUY_RECONCILE_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        lastForecastOutcomeCount = 0
                    }

                    if (nowMs - lastFixedOutcomePollAtMs >= 15_000L) {
                        lastFixedOutcomePollAtMs = nowMs
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
                        auditLogger.logAsync("API_HTTP_TRACE", "ACCOUNT_MINUTE_SUMMARY", mapOf(
                            "source" to "ACTUAL_NETWORK_DISPATCH_AFTER_CACHE_AND_ADMISSION",
                            "account_usage" to GrowwHttpTraceLedger.summary(),
                            "beta_gate_usage" to betaResearchGate.diagnosticStatus(),
                            "beta_gate_totals" to betaResearchGate.snapshot(),
                            "audit_dropped_total" to auditLogger.asyncDroppedCount()
                        ))
                    }

                    // Mature completed-date research can catch up before market open and
                    // on closed days, not only while the app happens to run after 15:35.
                    // No replay during trading; Alpha orders/Fixed retain all priority.
                    // Forecast replay & backfill fully frozen. Preserve local records.
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

    /**
     * Foreground-service watchdog is independent of trading workers. It can restart an
     * *exited* worker, but NEVER cancels a slow or active SELL/GTT reconciliation or
     * forces a Fixed scan across the original cadence/cooldown. No broker calls.
     */
    private fun startMobileHealthWorker() {
        if (mobileHealthJob?.isActive == true) return
        mobileHealthJob = scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            var lastAuditedAtMs = 0L
            var previousStatus = ""
            while (isActive) {
                try {
                    val nowMs = System.currentTimeMillis()
                    val clock = ZonedDateTime.now(INDIA)
                    val marketOpen = com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(clock)
                    val connected = runCatching {
                        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        manager.getNetworkCapabilities(manager.activeNetwork)
                            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    }.getOrDefault(false)
                    val brokerCooldown = recoveryGate.status(nowMs).categoryCooldownUntilMs
                        .filterKeys { it in setOf(
                            GrowwApiUsageLedger.Category.LIVE_DATA,
                            GrowwApiUsageLedger.Category.HISTORICAL,
                            GrowwApiUsageLedger.Category.NON_TRADING) }
                        .values.any { it > nowMs }
                    val snapshot = MobileEngineHealthPolicy.assess(
                        marketOpen, fixedScanJob?.isActive == true,
                        positionMonitorJob?.isActive == true, connected,
                        nowMs, lastFixedCycleCompletedAtMs, lastPositionPassSucceededAtMs,
                        lastKnownManagedCount, brokerCooldownActive = brokerCooldown
                    )
                    val listenerGranted = NotificationManagerCompat
                        .getEnabledListenerPackages(this@TradingEngineService).contains(packageName)
                    val listenerPrefs = getSharedPreferences("notification_listener_state_v801", MODE_PRIVATE)
                    val listenerConnected = listenerPrefs.getBoolean("connected", false)
                    mobileHealthStatus = when {
                        !listenerGranted -> snapshot.status + " / NOTIFICATION_ACCESS_OFF"
                        !listenerConnected -> snapshot.status + " / LISTENER_DISCONNECTED"
                        else -> snapshot.status
                    }
                    if (mobileHealthStatus != previousStatus ||
                        nowMs - lastAuditedAtMs >= MOBILE_HEALTH_AUDIT_INTERVAL_MS) {
                        auditLogger.logAsync("MOBILE_HEALTH", "ENGINE_WATCHDOG", mapOf(
                            "status" to snapshot.status,
                            "market_open" to marketOpen, "network_validated" to connected,
                            "app_owned_holdings" to lastKnownManagedCount,
                            "fixed_scan_progress_age_ms" to snapshot.fixedProgressAgeMs,
                            "position_monitor_success_age_ms" to snapshot.holdingMonitorAgeMs,
                            "fixed_worker_running" to snapshot.fixedWorkerRunning,
                            "position_worker_running" to snapshot.positionWorkerRunning,
                            "fixed_stalled_only_diagnostic" to snapshot.fixedScanStalled,
                            "broker_cooldown_active" to brokerCooldown,
                            "holding_monitor_stale" to snapshot.protectionMonitorStalled,
                            "cpu_wake_lock_held" to (positionMonitoringWakeLock?.isHeld == true),
                            "screen_interactive" to
                                (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive,
                            "notification_access_granted" to listenerGranted,
                            "notification_listener_connected" to listenerConnected,
                            "listener_last_connected_ms" to listenerPrefs.getLong("last_connected_ms", 0L),
                            "listener_last_notification_ms" to listenerPrefs.getLong("last_notification_ms", 0L),
                            "notification_rebind_requires_user_permission" to true,
                            "fixed_formula_changed" to false
                        ))
                        previousStatus = mobileHealthStatus
                        lastAuditedAtMs = nowMs
                    }
                    // Android controls notification access. Never claim successful
                    // signal capture when permission is missing. Rebind only when
                    // access exists and a previous connection is absent.
                    if (listenerGranted && !listenerConnected &&
                        nowMs - lastListenerRebindAtMs >= 5L * 60_000L) {
                        lastListenerRebindAtMs = nowMs
                        runCatching {
                            NotificationListenerService.requestRebind(ComponentName(
                                this@TradingEngineService,
                                MultifyNotificationListenerService::class.java))
                        }.onFailure {
                            auditLogger.logAsync("MOBILE_HEALTH", "LISTENER_REBIND_ERROR",
                                mapOf("error" to it.javaClass.simpleName))
                        }
                    }
                    // Only exited jobs are restarted. Active scans/SELLs may be
                    // broker-blocked and MUST NOT be cancelled or duplicated.
                    if (positionMonitorJob?.isActive != true) startPositionMonitorWorker()
                    if (fixedScanJob?.isActive != true) startFixedScanWorker()
                    if (engineJob?.isActive != true) startEngineLoop()
                    // Watchdog deliberately does not restart frozen Forecast jobs.
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    auditLogger.logAsync("MOBILE_HEALTH", "WATCHDOG_ERROR",
                        mapOf("error" to t.javaClass.simpleName))
                }
                delay(MOBILE_HEALTH_INTERVAL_MS)
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
                        lastPositionPassSucceededAtMs = System.currentTimeMillis()
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
                    val regularSession = com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(now)
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

    // Persistent per-day scan ordinal survives foreground-service restarts.
    private fun nextFixedShadowScanOrdinal(): Int {
        val day = ZonedDateTime.now(INDIA).toLocalDate().toString()
        val prefs = getSharedPreferences("fixed_shadow_sampling_schedule_v1", Context.MODE_PRIVATE)
        val sameDay = prefs.getString("date", "") == day
        val next = (if (sameDay) prefs.getInt("completed_fixed_scans", 0) else 0) + 1
        prefs.edit().putString("date", day).putInt("completed_fixed_scans", next).commit()
        return next
    }

    private fun startFixedScanWorker() {
        if (fixedScanJob?.isActive == true) return
        fixedScanJob = scope.launch {
            var previousFixedPause: FixedDataPipelinePolicy.Readiness? = null
            var lastFixedPauseAuditMs = 0L
            var lastSuccessfulFixedScanStartedAtMs = 0L
            auditLogger.log("FIXED_DATA_PIPELINE", "SPECULATIVE_HISTORICAL_PREWARM_DISABLED", mapOf(
                "reason" to "0 useful cache hits in v7.8.2; protect broker historical-candle quota",
                "fixed_algorithm_changed" to false,
                "frozen_history_rows" to 107
            ))
            auditLogger.log("RUNTIME", "FIXED_SCAN_WORKER_STARTED", mapOf(
                "priority" to "FIXED_FIRST",
                "blocked_by_adaptive_forecast" to false
            ))
            while (isActive) {
                try {
                    val now = ZonedDateTime.now(INDIA)
                    if (com.multify.traderpro.engine.NseEquitySessionCalendar.isWarmupWindow(now)) {
                        runCatching { repository.prepareFixedPreMarketWarmup() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_PREMARKET_WARMUP_ERROR", mapOf(
                                        "message" to (it.message ?: it.javaClass.simpleName)
                                    ))
                                }
                            }
                    }
                    if (com.multify.traderpro.engine.NseEquitySessionCalendar.isPreOpenAuctionWindow(now)) {
                        // Auction research is strictly read-only. Groww LTP is not a verified
                        // NSE indicative equilibrium price; official Fixed confirmation
                        // begins only with live continuous-trading data at 09:15/14:20.
                        // Hard-stop pre-open network I/O before continuous trading starts
                        // so the trusted 40-factor market scan retains priority.
                        val auctionClose = if (com.multify.traderpro.engine.NseEquitySessionCalendar.isSpecialMuhurat2026(now))
                            LocalTime.of(14, 20) else LocalTime.of(9, 15)
                        val remaining = java.time.Duration.between(now.toLocalTime(), auctionClose)
                            .toMillis() - 45_000L
                        if (remaining > 60_000L) runCatching {
                            withTimeoutOrNull(remaining) { repository.captureFixedPreOpenResearchOnly() }
                        }.onFailure {
                            if (it !is CancellationException) auditLogger.logAsync(
                                "FIXED_PREOPEN", "RESEARCH_WORKER_ERROR",
                                mapOf("error" to (it.message ?: it.javaClass.simpleName))
                            )
                        }
                    }
                    if (com.multify.traderpro.engine.NseEquitySessionCalendar.isMuhuratResearchTail(now)) {
                        runCatching { repository.captureMuhuratClosingResearchOnly() }
                            .onFailure { error ->
                                if (error !is CancellationException) auditLogger.logAsync(
                                    "FIXED_MUHURAT", "TAIL_WORKER_ERROR",
                                    mapOf("error" to (error.message ?: error.javaClass.simpleName))
                                )
                            }
                    }
                    if (com.multify.traderpro.engine.NseEquitySessionCalendar.isFixedScanWindow(now)) {
                        // Speculative historical prewarming is intentionally disabled. The
                        // 22-second verified cache remains unchanged; the old prewarm calls
                        // regularly expired before Fixed used them and consumed broker quota.
                        val gateState = recoveryGate.status()
                        val historicalCooldownUntilMs = gateState.categoryCooldownUntilMs[
                            GrowwApiUsageLedger.Category.HISTORICAL
                        ] ?: 0L
                        val liveCooldownUntilMs = gateState.categoryCooldownUntilMs[
                            GrowwApiUsageLedger.Category.LIVE_DATA
                        ] ?: 0L
                        val cooldownUntilMs = maxOf(historicalCooldownUntilMs, liveCooldownUntilMs)
                        val nowMs = System.currentTimeMillis()
                        val readiness = FixedDataPipelinePolicy.readiness(
                            gateState.reconnectRequired,
                            historicalCooldownUntilMs,
                            liveCooldownUntilMs,
                            nowMs
                        )
                        if (readiness != FixedDataPipelinePolicy.Readiness.READY) {
                            if (FixedDataPipelinePolicy.shouldAuditSuppression(
                                previousFixedPause, readiness, lastFixedPauseAuditMs, nowMs
                            )) {
                                auditLogger.log("FIXED_DATA_PIPELINE", "FIXED_SCAN_DEFERRED_BROKER_UNAVAILABLE", mapOf(
                                    "reason" to readiness.name,
                                    "blocked_api_categories" to listOfNotNull(
                                        "HISTORICAL".takeIf { historicalCooldownUntilMs > nowMs },
                                        "LIVE_DATA".takeIf { liveCooldownUntilMs > nowMs }
                                    ),
                                    "historical_cooldown_until_ms" to historicalCooldownUntilMs,
                                    "live_ltp_cooldown_until_ms" to liveCooldownUntilMs,
                                    "remaining_cooldown_ms" to (cooldownUntilMs - nowMs).coerceAtLeast(0L),
                                    "scan_outcome" to "DEFERRED_NOT_FAILED",
                                    "scan_attempt_id" to ("FX-DEFER-" + nowMs),
                                    "account_http_usage" to GrowwHttpTraceLedger.summary(),
                                    "forecast_formula_unchanged" to true,
                                    "existing_fixed_recommendations_preserved" to true
                                ))
                                FixedScanTrendStore.record(this@TradingEngineService,
                                    FixedScanTrendStore.Point("FX-DEFER-" + nowMs, nowMs, 0L,
                                        "DEFERRED_" + readiness.name, 0L, 0L, 0L, 0L, 0L, 0L))
                                lastFixedPauseAuditMs = nowMs
                            }
                            previousFixedPause = readiness
                            // Wait out the known LIVE/HISTORICAL broker block rather than
                            // retrying the Fixed scan every second. No complete scan is lost.
                            val remaining = (cooldownUntilMs - nowMs).coerceAtLeast(0L)
                            delay(if (remaining > 0L) remaining.coerceAtMost(30_000L) else SCAN_WORKER_POLL_MS)
                            continue
                        }
                        if (previousFixedPause != null) {
                            auditLogger.log("FIXED_DATA_PIPELINE", "FIXED_SCAN_RESUMED_AFTER_BROKER_RECOVERY", mapOf(
                                "previous_reason" to previousFixedPause!!.name,
                                "cooldown_expired" to true
                            ))
                            previousFixedPause = null
                        }
                        val scanId = "FX-" + System.currentTimeMillis()
                        val alphaLiveBefore = GrowwHttpTraceLedger.counters("ALPHA", "LIVE_DATA")
                        val betaLiveBefore = GrowwHttpTraceLedger.counters("BETA", "LIVE_DATA")
                        val alphaHistBefore = GrowwHttpTraceLedger.counters("ALPHA", "HISTORICAL")
                        val betaHistBefore = GrowwHttpTraceLedger.counters("BETA", "HISTORICAL")
                        val usageBefore = GrowwApiUsageLedger.snapshot()
                        val cacheHitsBefore = GrowwApiUsageLedger.cacheHitCount()
                        val historicalBefore = repository.fixedHistoricalRequestCounters()
                        val startedAt = System.currentTimeMillis()
                        GrowwHttpTraceLedger.beginFixedScan(scanId)
                        var scanFailure: Throwable? = null
                        val scanned = try {
                            try {
                                repository.runFixedPriorityCycle()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Throwable) {
                                scanFailure = failure
                                auditLogger.log("RUNTIME", "FIXED_SCAN_ERROR", mapOf(
                                    "scan_id" to scanId,
                                    "type" to failure.javaClass.simpleName,
                                    "message" to (failure.message ?: failure.javaClass.simpleName).take(160)))
                                false
                            }
                        } finally {
                            GrowwHttpTraceLedger.endFixedScan(scanId)
                        }
                        val elapsed = System.currentTimeMillis() - startedAt
                        val scanResult = when {
                            scanned -> "COMPLETED"
                            scanFailure != null -> "FAILED"
                            else -> "DEFERRED_NOT_COMPLETED"
                        }
                        val alphaLiveAfter = GrowwHttpTraceLedger.counters("ALPHA", "LIVE_DATA")
                        val betaLiveAfter = GrowwHttpTraceLedger.counters("BETA", "LIVE_DATA")
                        val alphaHistAfter = GrowwHttpTraceLedger.counters("ALPHA", "HISTORICAL")
                        val betaHistAfter = GrowwHttpTraceLedger.counters("BETA", "HISTORICAL")
                        fun increase(start: Long, end: Long) = (end - start).coerceAtLeast(0L)
                        val scanPoint = FixedScanTrendStore.Point(
                            scanId, startedAt, elapsed, scanResult,
                            increase(alphaLiveBefore.requests, alphaLiveAfter.requests),
                            increase(betaLiveBefore.requests, betaLiveAfter.requests),
                            increase(alphaHistBefore.requests, alphaHistAfter.requests),
                            increase(betaHistBefore.requests, betaHistAfter.requests),
                            increase(alphaLiveBefore.rateLimits + alphaHistBefore.rateLimits,
                                alphaLiveAfter.rateLimits + alphaHistAfter.rateLimits),
                            increase(betaLiveBefore.rateLimits + betaHistBefore.rateLimits,
                                betaLiveAfter.rateLimits + betaHistAfter.rateLimits)
                        )
                        FixedScanTrendStore.record(this@TradingEngineService, scanPoint)
                        auditLogger.logAsync("FIXED_PERFORMANCE", "EVERY_SCAN_ATTEMPT", mapOf(
                            "scan_id" to scanId, "result" to scanPoint.result,
                            "failure_type" to scanFailure?.javaClass?.simpleName,
                            "failure_message" to scanFailure?.message?.take(160),
                            "start_at_ms" to startedAt, "duration_ms" to elapsed,
                            "alpha_live_http" to scanPoint.alphaLive,
                            "beta_live_http" to scanPoint.betaLive,
                            "alpha_historical_http" to scanPoint.alphaHistorical,
                            "beta_historical_http" to scanPoint.betaHistorical,
                            "alpha_429" to scanPoint.alpha429,
                            "beta_429" to scanPoint.beta429,
                            "request_attribution" to "TIME_WINDOW_INCLUDES_OVERLAPPING_WORKERS",
                            "strategy_changed" to false
                        ))
                        // Diagnostic-only measured start-to-start cadence.
                        val actualStartSpacingMs = if (scanned && lastSuccessfulFixedScanStartedAtMs > 0L)
                            (startedAt - lastSuccessfulFixedScanStartedAtMs).coerceAtLeast(0L) else null
                        if (scanned) {
                            lastSuccessfulFixedScanStartedAtMs = startedAt
                            val usageAfter = GrowwApiUsageLedger.snapshot()
                            fun requestDelta(category: GrowwApiUsageLedger.Category): Long {
                                val before = usageBefore.categories.first { it.category == category }.totalRequests
                                val after = usageAfter.categories.first { it.category == category }.totalRequests
                                return (after - before).coerceAtLeast(0L)
                            }
                            val historicalAfter = repository.fixedHistoricalRequestCounters()
                            auditLogger.logAsync("RUNTIME", "FIXED_SCAN_PROFILE", mapOf(
                                "elapsed_ms" to elapsed,
                                "scan_id" to scanId,
                                "scan_started_at_ms" to startedAt,
                                "scan_completed_at_ms" to (startedAt + elapsed),
                                "target_scan_start_spacing_ms" to 90_000L,
                                "actual_completed_scan_start_spacing_ms" to actualStartSpacingMs,
                                "spacing_above_target_ms" to actualStartSpacingMs?.let {
                                    (it - 90_000L).coerceAtLeast(0L)
                                },
                                "previous_successful_scan_age_unknown_after_service_restart" to
                                    (actualStartSpacingMs == null),
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
                                "fixed_incremental_history_requests" to (historicalAfter.second - historicalBefore.second).coerceAtLeast(0L),
                                "fixed_full_history_requests" to (historicalAfter.first - historicalBefore.first).coerceAtLeast(0L),
                                "fixed_history_stitch_fallbacks" to (historicalAfter.third - historicalBefore.third).coerceAtLeast(0L),
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
                        // Preserve order-first: this timestamp is AFTER any Fixed auto-buy attempt.
                        // Historical generatedAtMs is the scan start, not the notification-ready time.
                        if (scanned) {
                            lastFixedCycleCompletedAtMs = System.currentTimeMillis()
                            auditLogger.logAsync("FIXED_LATENCY", "FIXED_READY_AFTER_ORDER_PATH", mapOf(
                                "scan_start_at_ms" to startedAt,
                                "ready_at_ms" to lastFixedCycleCompletedAtMs,
                                "elapsed_ms" to elapsed,
                                "order_path_completed" to true,
                                "fixed_rest_batch_window" to com.multify.traderpro.engine.FixedDiscoveryWindowScheduler.WINDOW_SIZE,
                                "groww_ltp_min_gap_ms" to com.multify.traderpro.engine.AlphaLtpPacingPolicy.MIN_GAP_MS,
                                "groww_ltp_app_max_per_minute" to com.multify.traderpro.engine.AlphaLtpPacingPolicy.MAX_PER_MINUTE,
                                "fixed_model_changed" to false
                            ))
                            // No Early Watch/Pulse/Fast Watch background research.
                            // Prepare NEXT official NSE sweep, using no more full-market
                            // LTP batches per successful cycle than original v8.
                            scope.launch(Dispatchers.IO) {
                                runCatching { repository.prepareNextFixedDiscoverySweep() }
                                    .onFailure { error ->
                                        if (error !is CancellationException) auditLogger.logAsync(
                                            "FIXED_PERFORMANCE", "NEXT_FULL_SWEEP_WORKER_ERROR",
                                            mapOf("error" to (error.message ?: error.javaClass.simpleName))
                                        )
                                    }
                            }
                            scope.launch {
                                notifyNewFixedRecommendationsAfterOrderPath()
                            }
                            // Discretionary Forecast research is not launched immediately
                            // after Fixed: broker capacity is reserved for the next sweep.
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
                    // Default Fixed-only mode pauses only opportunistic Forecast discovery.
                    // SELL monitoring and all three execution permissions stay independent.
                    if (!repository.forecastBackgroundScanningEnabled()) {
                        delay(SCAN_WORKER_POLL_MS)
                        continue
                    }
                    val cooldownRemainingMs = repository.forecastBrokerCooldownRemainingMs()
                    if (cooldownRemainingMs > 0L) {
                        // News polling and existing-position monitoring have their own workers.
                        // This only defers the discretionary Forecast discovery loop.
                        if (cooldownRemainingMs > 20_000L) auditLogger.logAsync(
                            "FORECAST_SCHEDULER", "COOLDOWN_PACED", mapOf(
                                "remaining_ms" to cooldownRemainingMs,
                                "fixed_logic_changed" to false,
                                "news_monitoring_paused" to false))
                        delay(cooldownRemainingMs.coerceIn(5_000L,30_000L))
                        continue
                    }
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
        // Event identity is SYMBOL|scanStartedAt, not SYMBOL|date. This permits
        // a genuinely distinct, freshly confirmed PRIMARY for the same stock.
        // Migrate v8.0.5 bare-symbol keys to the oldest locally known event,
        // allowing a newer event to notify independently.
        val seen = if (storedDate == today) {
            prefs.getStringSet(FIXED_NOTIFICATION_KEYS_KEY, emptySet())
                .orEmpty().toMutableSet()
        } else mutableSetOf()
        val legacySymbols = seen.filter { !it.contains("|") }
        if (legacySymbols.isNotEmpty()) {
            val migrated = com.multify.traderpro.engine.FixedNotificationEventKeyPolicy.migrateLegacy(
                seen, rows.map { it.symbol to it.generatedAtMs })
            seen.clear()
            seen.addAll(migrated)
        }

        var changed = storedDate != today || legacySymbols.isNotEmpty()
        rows.forEach { row ->
            val normalizedSymbol = row.symbol.uppercase(Locale.US)
            val key = com.multify.traderpro.engine.FixedNotificationEventKeyPolicy.key(
                normalizedSymbol, row.generatedAtMs)
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
        val clientQuoteReceivedAtMs = repository.fixedQuoteReceivedAtForNotification(
            f.symbol, f.generatedAtMs)
        val actionable = f.executionEligible
        val message = buildString {
            append(if (actionable) "PRIMARY · entry ₹" else "RESEARCH ONLY · not buyable · observed entry ₹")
            append(String.format(Locale.US, "%.2f", f.entryPrice))
            append(" · target +")
            append(String.format(Locale.US, "%.2f", f.targetPct))
            append("% · ")
            append(String.format(Locale.US, "%.0f%% confidence", f.confidence * 100.0))
            append(if (actionable) " · auto-buy path processed first"
                else " · execution held for entry quality or session timing")
        }
        val notification = NotificationCompat.Builder(this, FIXED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trader)
            .setContentTitle((if (actionable) "Fixed BUY · " else "Fixed WATCH · ") + f.symbol)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message + "\n" + f.reason))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        manager.notify((f.symbol.hashCode() xor f.generatedAtMs.toInt() xor FIXED_NOTIFICATION_ID_SALT).and(Int.MAX_VALUE), notification)
        auditLogger.logAsync("EXECUTION_LATENCY", "FIXED_NOTIFICATION_POSTED", mapOf(
            "symbol" to f.symbol,
            "actionable_at_post_time" to actionable,
            "notification_label" to if (actionable) "FIXED_BUY" else "FIXED_RESEARCH_ONLY",
            "recommendation_generated_at_ms" to f.generatedAtMs,
            "matched_client_quote_http_response_at_ms" to clientQuoteReceivedAtMs,
            "matched_client_quote_to_notification_ms" to clientQuoteReceivedAtMs?.let {
                (postedAtMs - it).coerceAtLeast(0L)
            },
            "matched_quote_source" to "HTTP_CLIENT_RESPONSE_NOT_EXCHANGE_TIMESTAMP",
            "exchange_quote_timestamp_verified" to false,
            "notification_posted_at_ms" to postedAtMs,
            // Compatibility field retains its prior value. It includes the whole scan,
            // because Fixed generatedAtMs was assigned at the start of that scan.
            "recommendation_to_notification_ms" to (postedAtMs - f.generatedAtMs).coerceAtLeast(0L),
            "scan_start_to_notification_ms" to (postedAtMs - f.generatedAtMs).coerceAtLeast(0L),
            "fixed_cycle_ready_at_ms" to lastFixedCycleCompletedAtMs,
            "cycle_ready_to_notification_ms" to
                if (lastFixedCycleCompletedAtMs > 0L) (postedAtMs - lastFixedCycleCompletedAtMs).coerceAtLeast(0L) else null,
            "generated_at_semantics" to "SCAN_START_NOT_PUBLICATION_TIME",
            // Publication clock is independent of Fixed's frozen scan-start timestamp.
            // These read-only diagnostics show when a stock actually became actionable.
            "publication_time_bucket" to FixedPublicationTimingPolicy.bucket(postedAtMs).name,
            "minutes_to_market_close" to FixedPublicationTimingPolicy.remainingMinutes(postedAtMs),
            "publication_time_is_actual_notification" to true,
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
        val text = monitorStatus + " · engine " + mobileHealthStatus + " · " + forecastOutcomeCount + " adaptive + " + fixedOutcomeCount + " fixed outcomes" +
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
        private const val MOBILE_HEALTH_INTERVAL_MS = 15_000L
        private const val MOBILE_HEALTH_AUDIT_INTERVAL_MS = 5L * 60_000L
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
