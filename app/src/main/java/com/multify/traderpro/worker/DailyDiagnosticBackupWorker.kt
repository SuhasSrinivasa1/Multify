package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.repository.DiagnosticZipIntegrity
import com.multify.traderpro.data.repository.EquityEodResearchRepository
import com.multify.traderpro.data.repository.TradingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Creates a verified local-only full research/diagnostic archive after NSE hours. */
@HiltWorker
class DailyDiagnosticBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TradingRepository,
    private val audit: AuditLogger
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val india = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.now(india)
        if (!EquityEodResearchRepository.afterHours(now)) {
            DailyDiagnosticBackupScheduler.scheduleNext(applicationContext, afterRun = true)
            return Result.success()
        }

        val preparedResult = runCatching {
            repository.prepareCompleteLogs(includeResearchBackup = true)
        }
        val prepared = preparedResult.getOrElse { error ->
            audit.logAsync("DIAGNOSTIC_BACKUP", "DAILY_ARCHIVE_FAILED", mapOf(
                "reason" to (error.message ?: error.javaClass.simpleName).take(160),
                "attempt" to runAttemptCount,
                "groww_api_calls" to 0
            ))
            if (runAttemptCount < 2) return Result.retry()
            DailyDiagnosticBackupScheduler.scheduleNext(applicationContext, afterRun = true)
            return Result.failure()
        }

        val directory = File(applicationContext.filesDir, "daily_diagnostic_backups")
        val staging = File(directory, "Phayra-${LocalDate.now(india)}-daily-backup.zip.part")
        val destination = File(directory, "Phayra-${LocalDate.now(india)}-daily-backup.zip")
        return try {
            check(directory.mkdirs() || directory.isDirectory) {
                "Unable to create daily diagnostic backup directory"
            }
            prepared.file.copyTo(staging, overwrite = true)
            check(staging.isFile && staging.length() == prepared.sizeBytes && staging.length() > 22L) {
                "Daily diagnostic backup copy is incomplete"
            }
            val stagedValidation = DiagnosticZipIntegrity.verify(staging, prepared.result.entries)
            check(stagedValidation.entries == prepared.result.entries) {
                "Daily diagnostic backup staging validation failed"
            }
            if (!staging.renameTo(destination)) {
                staging.copyTo(destination, overwrite = true)
                check(staging.delete()) { "Unable to finalize daily diagnostic backup" }
            }
            val verified = DiagnosticZipIntegrity.verify(destination, prepared.result.entries)
            check(destination.length() == prepared.sizeBytes && verified.entries == prepared.result.entries) {
                "Daily diagnostic backup final validation failed"
            }

            val archives = directory.listFiles()
                ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            archives.drop(MAX_ARCHIVES).forEach { it.delete() }

            val savedAt = System.currentTimeMillis()
            val saved = applicationContext.getSharedPreferences(
                "phayra_daily_diagnostics", Context.MODE_PRIVATE
            ).edit()
                .putLong("last_backup_ms", savedAt)
                .putString("last_backup_file", destination.name)
                .putLong("last_backup_bytes", destination.length())
                .commit()
            check(saved) { "Unable to persist daily backup status" }

            audit.logAsync("DIAGNOSTIC_BACKUP", "DAILY_ARCHIVE_COMPLETED", mapOf(
                "file" to destination.name,
                "bytes" to destination.length(),
                "entries" to verified.entries,
                "retained_archives" to minOf(archives.size, MAX_ARCHIVES),
                "research_backup_included" to true,
                "groww_api_calls" to 0
            ))
            DailyDiagnosticBackupScheduler.scheduleNext(applicationContext, afterRun = true)
            Result.success()
        } catch (error: Exception) {
            staging.delete()
            audit.logAsync("DIAGNOSTIC_BACKUP", "DAILY_ARCHIVE_FAILED", mapOf(
                "reason" to (error.message ?: error.javaClass.simpleName).take(160),
                "attempt" to runAttemptCount,
                "groww_api_calls" to 0
            ))
            if (runAttemptCount < 2) Result.retry() else {
                DailyDiagnosticBackupScheduler.scheduleNext(applicationContext, afterRun = true)
                Result.failure()
            }
        } finally {
            prepared.file.delete()
        }
    }

    companion object {
        private const val MAX_ARCHIVES = 14
    }
}
