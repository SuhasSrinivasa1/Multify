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
    private var positionMonitoringWakeLock: PowerManager.WakeLock? = null
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
        engineJob?.cancel()
        scope.cancel()
        auditLogger.log("RUNTIME", "FOREGROUND_ENGINE_DESTROYED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startEngineLoop() {
        if (engineJob?.isActive == true) return
        engineJob = scope.launch {
            auditLogger.log("RUNTIME", "FOREGROUND_ENGINE_STARTED")
            // Capture the immutable Fixed reference state before v7.5 performs any adaptive writes.
            runCatching { repository.ensureFixedEngineSnapshot() }
                .onFailure { auditLogger.log("RUNTIME", "FIXED_SNAPSHOT_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
            runCatching { repository.ensureHistoricalSeed() }
            var lastHeartbeatAtMs = 0L
            var lastKnownManagedCount = 0
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

                    val managedMonitorResult = runCatching { repository.monitorManagedPositions() }
                    val managedMonitorFailure = managedMonitorResult.exceptionOrNull()
                    val managedMonitorSucceeded = managedMonitorFailure == null
                    val managedCount = if (managedMonitorSucceeded) {
                        managedMonitorResult.getOrThrow().coerceAtLeast(0).also { count ->
                            lastKnownManagedCount = count
                            updatePositionMonitoringWakeLock(
                                shouldHold = count > 0,
                                reason = if (count > 0) "ACTIVE_MANAGED_HOLDINGS" else "NO_OPEN_MANAGED_HOLDINGS"
                            )
                        }
                    } else {
                        if (managedMonitorFailure is CancellationException) throw managedMonitorFailure
                        // The position state is unknown after a monitor failure. Acquire/retain the
                        // wake lock and retry quickly rather than treating the error as zero holdings.
                        updatePositionMonitoringWakeLock(true, "MONITOR_ERROR_UNKNOWN_POSITION_STATE")
                        auditLogger.log("RUNTIME", "MANAGED_MONITOR_ERROR", mapOf(
                            "type" to (managedMonitorFailure?.javaClass?.simpleName ?: "UnknownError"),
                            "message" to (managedMonitorFailure?.message ?: "Managed position monitor failed"),
                            "last_known_managed_count" to lastKnownManagedCount,
                            "wake_lock_held" to (positionMonitoringWakeLock?.isHeld == true)
                        ))
                        lastKnownManagedCount
                    }

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

                    val now = ZonedDateTime.now(INDIA)
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() <= LocalTime.of(15, 0)
                    ) {
                        runCatching { repository.generateDailyForecasts(false) }
                            .onFailure { auditLogger.log("RUNTIME", "FORECAST_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        runCatching { repository.generateFixedForecasts(false) }
                            .onFailure { auditLogger.log("RUNTIME", "FIXED_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        // Adaptive Forecast and immutable Fixed execution are independent under the same ARM master switch.
                        runCatching { repository.autoBuyForecastRecommendations() }
                            .onFailure { auditLogger.log("RUNTIME", "FORECAST_AUTO_BUY_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        runCatching { repository.autoBuyFixedRecommendations() }
                            .onFailure { auditLogger.log("RUNTIME", "FIXED_AUTO_BUY_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        runCatching { repository.pendingLongForecastNotifications() }
                            .getOrDefault(emptyList())
                            .filter { it.qualityTier == "PRIMARY" && it.executionEligible }
                            .forEach(::notifyForecast)
                    }

                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(15, 35) &&
                        nowMs - lastAfterHoursResearchAtMs >= AFTER_HOURS_RESEARCH_INTERVAL_MS
                    ) {
                        lastAfterHoursResearchAtMs = nowMs
                        runCatching { repository.runAfterHoursResearch(false) }
                            .onFailure { auditLogger.log("RUNTIME", "AFTER_HOURS_RESEARCH_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                    }

                    updateForegroundNotification(managedCount, forecastOutcomeCount, fixedOutcomeCount, managedMonitorSucceeded)
                    val regularSession = now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() < LocalTime.of(15, 30)
                    delay(
                        when {
                            !regularSession -> CLOSED_SESSION_LOOP_INTERVAL_MS
                            !managedMonitorSucceeded -> POSITION_MONITOR_ERROR_RETRY_MS
                            managedCount > 1 -> PORTFOLIO_POSITION_LOOP_INTERVAL_MS
                            managedCount > 0 -> POSITION_LOOP_INTERVAL_MS
                            forecastOutcomeCount > 0 || fixedOutcomeCount > 0 -> FORECAST_LOOP_INTERVAL_MS
                            else -> IDLE_LOOP_INTERVAL_MS
                        }
                    )
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
        private const val FORECAST_LOOP_INTERVAL_MS = 5_000L
        private const val IDLE_LOOP_INTERVAL_MS = 10_000L
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
