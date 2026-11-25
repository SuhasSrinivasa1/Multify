package com.multify.traderpro.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Best-effort local archive at 21:30 IST; it never calls a broker or exchange API. */
object DailyDiagnosticBackupScheduler {
    private val india = ZoneId.of("Asia/Kolkata")
    private val backupTime = LocalTime.of(21, 30)

    fun scheduleNext(context: Context, afterRun: Boolean = false) {
        val now = ZonedDateTime.now(india)
        val prefs = context.getSharedPreferences("phayra_daily_diagnostics", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_backup_ms", 0L)
        val completedToday = last > 0L &&
            java.time.Instant.ofEpochMilli(last).atZone(india).toLocalDate() == now.toLocalDate()
        val targetToday = now.toLocalDate().atTime(backupTime).atZone(india)
        val tomorrow = now.toLocalDate().plusDays(1).atTime(backupTime).atZone(india)
        val scheduled = when {
            afterRun || completedToday -> tomorrow
            now.isBefore(targetToday) -> targetToday
            else -> now.plusMinutes(1)
        }
        val delay = Duration.between(now, scheduled).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<DailyDiagnosticBackupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("phayra-daily-diagnostic-backup")
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "phayra-daily-diagnostic-backup-${scheduled.toLocalDate()}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
