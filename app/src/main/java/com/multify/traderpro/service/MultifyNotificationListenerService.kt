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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@AndroidEntryPoint
class MultifyNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var parser: NotificationParser
    @Inject lateinit var dao: SignalEventDao
    @Inject lateinit var repository: TradingRepository
    @Inject lateinit var auditLogger: AuditLogger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Critical Multify BUY/Book Profit events remain serialized and are never queued behind research.
    private val priorityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var listenerHeartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createStatusChannel()
        val engineStarted = TradingEngineService.start(this)
        auditLogger.log("RUNTIME", "LISTENER_CREATED", mapOf("foreground_engine_start_requested" to engineStarted))
    }

    override fun onDestroy() {
        listenerHeartbeatJob?.cancel()
        priorityScope.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val now = System.currentTimeMillis()
        if (claimThrottle(LAST_CONNECTED_AUDIT_MS, now, LISTENER_AUDIT_THROTTLE_MS)) {
            auditLogger.log("RUNTIME", "NOTIFICATION_LISTENER_CONNECTED")
        }
        TradingEngineService.start(this)
        listenerHeartbeatJob?.cancel()
        listenerHeartbeatJob = scope.launch {
            while (isActive) {
                runCatching { repository.recordListenerConnected() }
                delay(LISTENER_HEARTBEAT_MS)
            }
        }
        notifyStatus("Signal capture active", "Multify listener active · production engine runs independently")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerHeartbeatJob?.cancel()
        listenerHeartbeatJob = null
        val now = System.currentTimeMillis()
        if (claimThrottle(LAST_DISCONNECTED_AUDIT_MS, now, LISTENER_AUDIT_THROTTLE_MS)) {
            auditLogger.log("RUNTIME", "NOTIFICATION_LISTENER_DISCONNECTED")
        }
        scope.launch { repository.recordListenerReconnect() }
        if (claimThrottle(LAST_REBIND_REQUEST_MS, now, LISTENER_REBIND_MIN_INTERVAL_MS)) {
            runCatching {
                requestRebind(android.content.ComponentName(this, MultifyNotificationListenerService::class.java))
            }
        }
        // The foreground TradingEngineService deliberately remains alive during listener churn.
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

        val latencyCritical = parsed.type == SignalType.TRADE_RELEASE
        val researchRelevant = parsed.type == SignalType.TRADE_RELEASE || parsed.type == SignalType.BOOK_PROFIT
        val executor = if (latencyCritical) priorityScope else scope
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
                    notificationStartedNs = if (latencyCritical) notificationStartedNs else null
                )
            }
            if (processed.isFailure) {
                val error = processed.exceptionOrNull()
                val message = error?.message ?: error?.javaClass?.simpleName ?: "Unknown processing failure"
                dao.updateForwarding(
                    id, "ERROR", "PROCESSING_FAILED",
                    "Signal processing failed; review Groww and the exported audit log.",
                    message.take(500)
                )
                auditLogger.log("SIGNAL", "PROCESSING_FAILED", mapOf(
                    "event_id" to id,
                    "signal_type" to parsed.type.name,
                    "symbol" to parsed.symbol,
                    "message" to message.take(500)
                ))
                // Critical broker operations are never blindly replayed after ambiguous failure.
                if (!latencyCritical) enqueueForward(id)
            }

            scope.launch {
                runCatching { repository.recordNotificationReceived() }
                runCatching { repository.recordEventProcessingLatency(System.currentTimeMillis() - processStarted) }
                auditLogger.log("SIGNAL", "CAPTURED", mapOf(
                    "event_id" to id,
                    "signal_type" to parsed.type.name,
                    "symbol" to parsed.symbol,
                    "source_package" to sbn.packageName,
                    "parser_confidence" to parsed.confidence,
                    "processed_ok" to processed.isSuccess
                ))
                if (parsed.type == SignalType.AUTO_PAUSED) {
                    notifyStatus("ARM disabled", "Multify reported a safety pause. Review broker state before resetting the halt.")
                }
                if (researchRelevant && processed.isSuccess) {
                    // Do not spend market-hours API capacity on post-signal research. The call and
                    // result are persisted locally; MFE/positive-transfer learning is backfilled
                    // after market close from historical data.
                    auditLogger.log("MULTIFY_RESEARCH", "POST_SIGNAL_RESEARCH_DEFERRED_AFTER_HOURS", mapOf(
                        "event_id" to id,
                        "symbol" to parsed.symbol,
                        "critical_execution_completed" to latencyCritical
                    ))
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
            ).apply { description = "Safety and signal-capture status from Multify Trader Pro" }
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

    private fun claimThrottle(clock: AtomicLong, now: Long, interval: Long): Boolean {
        while (true) {
            val previous = clock.get()
            if (now - previous < interval) return false
            if (clock.compareAndSet(previous, now)) return true
        }
    }

    private fun String?.ifNullOrBlank(fallback: String): String = if (this.isNullOrBlank()) fallback else this

    companion object {
        private const val CHANNEL_ID = "trading_engine_status"
        private const val LISTENER_AUDIT_THROTTLE_MS = 30L * 60L * 1000L
        private const val LISTENER_REBIND_MIN_INTERVAL_MS = 60_000L
        private const val LISTENER_HEARTBEAT_MS = 20_000L
        private val LAST_CONNECTED_AUDIT_MS = AtomicLong(0L)
        private val LAST_DISCONNECTED_AUDIT_MS = AtomicLong(0L)
        private val LAST_REBIND_REQUEST_MS = AtomicLong(0L)
    }
}
