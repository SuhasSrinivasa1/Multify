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
import com.multify.traderpro.data.repository.TradingRepository
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engineJob: Job? = null
    private var positionMonitorJob: Job? = null
    private var fixedScanJob: Job? = null
    private var forecastScanJob: Job? = null
    private var positionMonitoringWakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastKnownManagedCount = 0
    @Volatile private var lastManagedMonitorSucceeded = true
    private var lastAfterHoursResearchAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(FOREGROUND_NOTIFICATION_ID, engineNotification("Starting production engine…"))
        // Keep the CPU awake until the first successful pass confirms whether holdings need monitoring.
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

                    runCatching { repository.reconcileForecastAutoBuyOrders() }
                        .onFailure { auditLogger.log("RUNTIME", "FORECAST_AUTO_BUY_RECONCILE_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                    runCatching { repository.reconcileFixedAutoBuyOrders() }
                        .onFailure { auditLogger.log("RUNTIME", "FIXED_AUTO_BUY_RECONCILE_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }

                    val forecastOutcomeCount = runCatching { repository.monitorForecastOutcomes() }
                        .onFailure {
                            if (it !is CancellationException) {
                                auditLogger.log("RUNTIME", "FORECAST_OUTCOME_MONITOR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                            }
                        }.getOrDefault(0)

                    val fixedOutcomeCount = runCatching { repository.monitorFixedOutcomes() }
                        .onFailure {
                            if (it !is CancellationException) {
                                auditLogger.log("RUNTIME", "FIXED_OUTCOME_MONITOR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                            }
                        }.getOrDefault(0)

                    runCatching { repository.pendingLongForecastNotifications() }
                        .getOrDefault(emptyList())
                        .filter { it.qualityTier == "PRIMARY" && it.executionEligible }
                        .forEach(::notifyForecast)

                    val now = ZonedDateTime.now(INDIA)
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
                        forecastOutcomeCount,
                        fixedOutcomeCount,
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
                    val result = runCatching { repository.monitorManagedPositions() }
                    val failure = result.exceptionOrNull()
                    val succeeded = failure == null
                    lastManagedMonitorSucceeded = succeeded
                    if (succeeded) {
                        val count = result.getOrThrow().coerceAtLeast(0)
                        lastKnownManagedCount = count
                        updatePositionMonitoringWakeLock(
                            shouldHold = count > 0,
                            reason = if (count > 0) "ACTIVE_MANAGED_HOLDINGS" else "NO_OPEN_MANAGED_HOLDINGS"
                        )
                    } else {
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
                "priority" to "INDEPENDENT",
                "blocked_by_adaptive_forecast" to false
            ))
            while (isActive) {
                try {
                    val now = ZonedDateTime.now(INDIA)
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() < LocalTime.of(15, 30)
                    ) {
                        val startedAt = System.currentTimeMillis()
                        runCatching { repository.generateFixedForecasts(false) }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (elapsed >= SCAN_DURATION_AUDIT_THRESHOLD_MS) {
                            auditLogger.log("RUNTIME", "FIXED_SCAN_DURATION", mapOf(
                                "elapsed_ms" to elapsed,
                                "parallel_scheduler" to true,
                                "forecast_blocking_ms" to 0
                            ))
                        }
                        runCatching { repository.autoBuyFixedRecommendations() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FIXED_AUTO_BUY_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
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
                "parallel_with_fixed" to true,
                "default_state" to "PAUSED_FOR_FIXED_PRIORITY"
            ))
            var pausedAuditEmitted = false
            while (isActive) {
                try {
                    val forecastEnabled = repository.settings.first().forecastScannerEnabled
                    if (!forecastEnabled) {
                        if (!pausedAuditEmitted) {
                            auditLogger.log("RUNTIME", "FORECAST_SCAN_PAUSED_FOR_FIXED_PRIORITY")
                            pausedAuditEmitted = true
                        }
                        delay(FORECAST_PAUSED_POLL_MS)
                        continue
                    }
                    pausedAuditEmitted = false
                    val now = ZonedDateTime.now(INDIA)
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() <= LocalTime.of(15, 0)
                    ) {
                        val startedAt = System.currentTimeMillis()
                        runCatching { repository.generateDailyForecasts(false) }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FORECAST_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (elapsed >= SCAN_DURATION_AUDIT_THRESHOLD_MS) {
                            auditLogger.log("RUNTIME", "FORECAST_SCAN_DURATION", mapOf(
                                "elapsed_ms" to elapsed,
                                "parallel_scheduler" to true
                            ))
                        }
                        runCatching { repository.autoBuyForecastRecommendations() }
                            .onFailure {
                                if (it !is CancellationException) {
                                    auditLogger.log("RUNTIME", "FORECAST_AUTO_BUY_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                                }
                            }
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

    private fun notifyForecast(f: ForecastDto) {
        val manager = getSystemService(NotificationManager::class.java)
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
            !managedMonitorSucceeded -> "Monitor error · $managedCount last-known holding(s) · " +
                if (positionMonitoringWakeLock?.isHeld == true) "CPU awake; retrying" else "CPU wake lock unavailable"
            managedCount <= 0 -> "No open holdings"
            positionMonitoringWakeLock?.isHeld == true -> "Monitoring $managedCount holding(s) · CPU awake"
            else -> "Monitoring $managedCount holding(s) · CPU wake lock unavailable"
        }
        val text = monitorStatus + " · " + forecastOutcomeCount + " adaptive + " + fixedOutcomeCount + " fixed outcomes"
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
                FORECAST_CHANNEL_ID,
                "Forecast opportunities",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Production-qualified PRIMARY forecast opportunities only." }
        )
    }

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private const val ENGINE_CHANNEL_ID = "production_engine"
        private const val FORECAST_CHANNEL_ID = "forecast_opportunities"
        private const val FOREGROUND_NOTIFICATION_ID = 68090
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val POSITION_LOOP_INTERVAL_MS = 350L
        private const val PORTFOLIO_POSITION_LOOP_INTERVAL_MS = 1_000L
        private const val POSITION_MONITOR_ERROR_RETRY_MS = 1_000L
        private const val IDLE_POSITION_LOOP_INTERVAL_MS = 5_000L
        private const val SCAN_WORKER_POLL_MS = 1_000L
        private const val FORECAST_PAUSED_POLL_MS = 5_000L
        private const val SCAN_DURATION_AUDIT_THRESHOLD_MS = 2_000L
        private const val MAINTENANCE_LOOP_INTERVAL_MS = 5_000L
        private const val CLOSED_SESSION_LOOP_INTERVAL_MS = 60_000L
        private const val ERROR_BACKOFF_MS = 10_000L
        private const val AFTER_HOURS_RESEARCH_INTERVAL_MS = 15L * 60L * 1000L
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
