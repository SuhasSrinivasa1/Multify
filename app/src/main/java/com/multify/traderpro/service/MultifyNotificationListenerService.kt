package com.multify.traderpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.multify.traderpro.R
import com.multify.traderpro.data.local.SignalEventDao
import com.multify.traderpro.data.local.SignalEventEntity
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.repository.TradingRepository
import com.multify.traderpro.domain.NotificationParser
import com.multify.traderpro.domain.SignalType
import com.multify.traderpro.worker.SignalForwardWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@AndroidEntryPoint
class MultifyNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var parser: NotificationParser
    @Inject lateinit var dao: SignalEventDao
    @Inject lateinit var repository: TradingRepository
    @Inject lateinit var auditLogger: AuditLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Multify buy and book-profit events use a dedicated serialized lane so holdings actions
    // cannot queue behind forecast scans or research work.
    private val priorityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var shadowMonitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createStatusChannel()
    }

    override fun onDestroy() {
        priorityScope.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        auditLogger.log("RUNTIME", "NOTIFICATION_LISTENER_CONNECTED")
        scope.launch { repository.recordListenerConnected() }
        notifyStatus("Signal capture active", "Listening for Multify ARM + LONG-only holding forecasts")
        if (shadowMonitorJob?.isActive != true) {
            shadowMonitorJob = scope.launch {
                runCatching { repository.ensureHistoricalSeed() }
                while (isActive) {
                    runCatching { repository.recordServiceHeartbeat() }
                    runCatching { repository.maintainArmHotPath() }
                    val managedCount = runCatching { repository.monitorManagedPositions() }.getOrDefault(0)
                    val forecastOutcomeCount = runCatching { repository.monitorForecastOutcomes() }.getOrDefault(0)
                    val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                    if (now.dayOfWeek.value < 6 &&
                        now.toLocalTime() >= java.time.LocalTime.of(9, 15) &&
                        now.toLocalTime() <= java.time.LocalTime.of(15, 0)
                    ) {
                        runCatching { repository.generateDailyForecasts(false) }
                        runCatching { repository.pendingLongForecastNotifications() }.getOrDefault(emptyList()).forEach { f ->
                            val message = f.symbol + " · entry ₹" + String.format(java.util.Locale.US, "%.2f", f.entryPrice) +
                                " · target +" + String.format(java.util.Locale.US, "%.2f", f.targetPct) + "% · " +
                                String.format(java.util.Locale.US, "%.0f%% confidence", f.confidence * 100.0)
                            notifyStatus("LONG holding opportunity · " + f.symbol, message)
                        }
                    }
                    if (now.dayOfWeek.value < 6 && now.toLocalTime() >= java.time.LocalTime.of(15, 35)) {
                        runCatching { repository.runAfterHoursResearch(false) }
                    }
                    delay(if (managedCount > 0) 1_000L else if (forecastOutcomeCount > 0) 2_000L else 10_000L)
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        auditLogger.log("RUNTIME", "NOTIFICATION_LISTENER_DISCONNECTED")
        scope.launch { repository.recordListenerReconnect() }
        // Vivo/Funtouch OS can aggressively reclaim background components. Ask Android to
        // re-bind the notification listener instead of waiting for the app to be reopened.
        runCatching { requestRebind(android.content.ComponentName(this, MultifyNotificationListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        val notificationStartedNs = SystemClock.elapsedRealtimeNanos()
        val receivedAtMs = System.currentTimeMillis()

        val extras = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().ifNullOrBlank(text)
        val parsed = parser.parse(title, text, bigText)
        if (parsed.type == SignalType.UNKNOWN) return

        val critical = parsed.type == SignalType.TRADE_RELEASE || parsed.type == SignalType.BOOK_PROFIT
        val executor = if (critical) priorityScope else scope
        executor.launch {
            if (!repository.shouldCapturePackage(sbn.packageName)) return@launch

            val fingerprint = fingerprint(sbn.packageName, title, bigText, sbn.postTime)
            val id = dao.insert(
                SignalEventEntity(
                    fingerprint = fingerprint,
                    receivedAtMs = receivedAtMs,
                    postedAtMs = sbn.postTime,
                    sourcePackage = sbn.packageName,
                    appLabel = sbn.packageName,
                    title = title,
                    text = text,
                    bigText = bigText,
                    signalType = parsed.type.name,
                    symbol = parsed.symbol,
                    summary = parsed.summary(),
                    confidence = parsed.confidence
                )
            )
            if (id <= 0L) return@launch

            val processStarted = System.currentTimeMillis()
            val processed = runCatching {
                repository.processEvent(
                    eventId = id,
                    parsedOverride = parsed,
                    notificationStartedNs = if (critical) notificationStartedNs else null
                )
            }
            if (processed.isFailure) enqueueForward(id)

            scope.launch {
                runCatching { repository.recordNotificationReceived() }
                runCatching { repository.recordEventProcessingLatency(System.currentTimeMillis() - processStarted) }
                auditLogger.log("SIGNAL", "CAPTURED", mapOf(
                    "event_id" to id,
                    "signal_type" to parsed.type.name,
                    "symbol" to parsed.symbol,
                    "source_package" to sbn.packageName,
                    "parser_confidence" to parsed.confidence
                ))
                if (parsed.type == SignalType.AUTO_PAUSED) {
                    notifyStatus("ARM disabled", "Multify reported a safety pause. Review broker state before resetting the halt.")
                }
                if (critical) runCatching { repository.sampleSignal(id) }
            }
        }
    }

    private fun enqueueForward(eventId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SignalForwardWorker>()
            .setInputData(Data.Builder().putLong(SignalForwardWorker.KEY_EVENT_ID, eventId).build())
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "signal-forward-$eventId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun createStatusChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Trading engine status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Safety and execution status from Multify Trader Pro" }
        )
    }

    private fun notifyStatus(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trader)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun fingerprint(pkg: String, title: String, body: String, postedAtMs: Long): String {
        val minuteBucket = postedAtMs / 60_000L
        val bytes = "$pkg\n$title\n$body\n$minuteBucket".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun String?.ifNullOrBlank(fallback: String): String = if (this.isNullOrBlank()) fallback else this

    companion object { private const val CHANNEL_ID = "trading_engine_status" }
}
