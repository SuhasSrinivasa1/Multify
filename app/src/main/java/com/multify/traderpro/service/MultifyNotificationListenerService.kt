package com.multify.traderpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
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
    // Trade release and book-profit events use a dedicated single-lane executor so the first-wave
    // BUY -> long exit -> short entry -> short exit path cannot queue behind research/monitor work.
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
        notifyStatus("Signal capture active", "Listening for paid Multify equity notifications · shadow + app-owned live monitors ready")
        if (shadowMonitorJob?.isActive != true) {
            shadowMonitorJob = scope.launch {
                runCatching { repository.ensureHistoricalSeed() }
                while (isActive) {
                    val shadowCount = runCatching { repository.monitorShadowPositions() }.getOrDefault(0)
                    val managedCount = runCatching { repository.monitorManagedPositions() }.getOrDefault(0)
                    val learningCount = runCatching { repository.monitorLearningObservations() }.getOrDefault(0)
                    val pivotCount = runCatching { repository.monitorWaveCampaigns() }.getOrDefault(0)
                    val adaptiveCount = runCatching { repository.monitorAdaptiveWaves() }.getOrDefault(0)
                    val outcomeCount = runCatching { repository.monitorWaveOutcomes() }.getOrDefault(0)
                    val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                    if (now.dayOfWeek.value < 6 && now.toLocalTime() >= java.time.LocalTime.of(9, 16) && now.toLocalTime() <= java.time.LocalTime.of(10, 0)) {
                        runCatching { repository.generateDailyForecasts(false) }
                    }
                    if (now.dayOfWeek.value < 6 && now.toLocalTime() >= java.time.LocalTime.of(15, 35)) {
                        runCatching { repository.runAfterHoursResearch(false) }
                    }
                    delay(if (shadowCount + managedCount + learningCount + pivotCount + adaptiveCount + outcomeCount > 0) 2_000L else 10_000L)
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        auditLogger.log("RUNTIME", "NOTIFICATION_LISTENER_DISCONNECTED")
        // Vivo/Funtouch OS can aggressively reclaim background components. Ask Android to
        // re-bind the notification listener instead of waiting for the app to be reopened.
        runCatching { requestRebind(android.content.ComponentName(this, MultifyNotificationListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val notification = sbn.notification ?: return

        scope.launch {
            if (!repository.shouldCapturePackage(sbn.packageName)) return@launch

            val extras = notification.extras ?: Bundle.EMPTY
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().ifNullOrBlank(text)
            val parsed = parser.parse(title, text, bigText)
            if (parsed.type == SignalType.UNKNOWN) return@launch

            val appLabel = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)
            val fingerprint = fingerprint(sbn.packageName, title, bigText, sbn.postTime)

            val id = dao.insert(
                SignalEventEntity(
                    fingerprint = fingerprint,
                    receivedAtMs = System.currentTimeMillis(),
                    postedAtMs = sbn.postTime,
                    sourcePackage = sbn.packageName,
                    appLabel = appLabel,
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
            auditLogger.log("SIGNAL", "CAPTURED", mapOf(
                "event_id" to id, "signal_type" to parsed.type.name, "symbol" to parsed.symbol,
                "source_package" to sbn.packageName, "parser_confidence" to parsed.confidence
            ))

            val isSafetyEvent = parsed.type == SignalType.AUTO_PAUSED
            if (isSafetyEvent) {
                repository.forceLocalDisarm()
                notifyStatus("Live execution disabled", "Multify reported an unprotected/paused position. Review broker state before resetting the halt.")
            }

            // First-wave lifecycle events are the top processing priority. They run on a dedicated
            // serialized lane, separate from market monitors/research, so a new Multify BUY or
            // Book Profit cannot sit behind slower background work. WorkManager remains recovery only.
            val critical = parsed.type == SignalType.TRADE_RELEASE || parsed.type == SignalType.BOOK_PROFIT
            val executor = if (critical) priorityScope else scope
            executor.launch {
                val processed = runCatching { repository.processEvent(id) }
                if (processed.isFailure) enqueueForward(id)
                if (critical) {
                    scope.launch { runCatching { repository.sampleSignal(id) } }
                }
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
