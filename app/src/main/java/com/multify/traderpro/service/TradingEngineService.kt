package com.multify.traderpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ContextCompat
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

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(FOREGROUND_NOTIFICATION_ID, engineNotification("Starting production engine…"))
        startEngineLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startEngineLoop()
        return START_STICKY
    }

    override fun onDestroy() {
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

                    val managedCount = runCatching { repository.monitorManagedPositions() }
                        .onFailure {
                            if (it !is CancellationException) {
                                auditLogger.log("RUNTIME", "MANAGED_MONITOR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                            }
                        }.getOrDefault(0)

                    val forecastOutcomeCount = runCatching { repository.monitorForecastOutcomes() }
                        .onFailure {
                            if (it !is CancellationException) {
                                auditLogger.log("RUNTIME", "FORECAST_OUTCOME_MONITOR_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName)))
                            }
                        }.getOrDefault(0)

                    val now = ZonedDateTime.now(INDIA)
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= LocalTime.of(9, 15) &&
                        now.toLocalTime() <= LocalTime.of(15, 0)
                    ) {
                        runCatching { repository.generateDailyForecasts(false) }
                            .onFailure { auditLogger.log("RUNTIME", "FORECAST_SCAN_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                        runCatching { repository.pendingLongForecastNotifications() }
                            .getOrDefault(emptyList())
                            .filter { it.qualityTier != "RESEARCH" }
                            .forEach(::notifyForecast)
                    }

                    if (now.dayOfWeek.value < 6 && now.toLocalTime() >= LocalTime.of(15, 35)) {
                        runCatching { repository.runAfterHoursResearch(false) }
                            .onFailure { auditLogger.log("RUNTIME", "AFTER_HOURS_RESEARCH_ERROR", mapOf("message" to (it.message ?: it.javaClass.simpleName))) }
                    }

                    updateForegroundNotification(managedCount, forecastOutcomeCount)
                    delay(
                        when {
                            managedCount > 0 -> POSITION_LOOP_INTERVAL_MS
                            forecastOutcomeCount > 0 -> FORECAST_LOOP_INTERVAL_MS
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

    private fun updateForegroundNotification(managedCount: Int, forecastOutcomeCount: Int) {
        val text = "Production engine active · " + managedCount + " holdings · " +
            forecastOutcomeCount + " active forecast outcomes"
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
            ).apply { description = "PRIMARY and SECONDARY forecast opportunities." }
        )
    }

    companion object {
        private val INDIA = ZoneId.of("Asia/Kolkata")
        private const val ENGINE_CHANNEL_ID = "production_engine"
        private const val FORECAST_CHANNEL_ID = "forecast_opportunities"
        private const val FOREGROUND_NOTIFICATION_ID = 68090
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val POSITION_LOOP_INTERVAL_MS = 3_000L
        private const val FORECAST_LOOP_INTERVAL_MS = 5_000L
        private const val IDLE_LOOP_INTERVAL_MS = 10_000L
        private const val ERROR_BACKOFF_MS = 10_000L

        fun start(context: Context): Boolean = runCatching {
            val intent = Intent(context, TradingEngineService::class.java)
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrDefault(false)
    }
}
