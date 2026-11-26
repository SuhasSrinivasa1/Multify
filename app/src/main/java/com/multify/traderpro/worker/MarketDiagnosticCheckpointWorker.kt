package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.repository.DiagnosticZipIntegrity
import com.multify.traderpro.data.repository.FixedRecommendationStore
import com.multify.traderpro.data.repository.TradingRepository
import com.multify.traderpro.engine.NseEquitySessionCalendar
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Verified diagnostics-only checkpoint. It never truncates logs or clears app/broker state. */
@HiltWorker
class MarketDiagnosticCheckpointWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TradingRepository,
    private val fixedStore: FixedRecommendationStore,
    private val audit: AuditLogger
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.now(zone)
        val prefs = applicationContext.getSharedPreferences(
            MarketDiagnosticCheckpointScheduler.PREFS, Context.MODE_PRIVATE
        )
        val trigger = inputData.getString("trigger") ?: "TIME"
        val today = now.toLocalDate().toString()
        val currentCount = fixedStore.recommendations(today).size
        val archivedCount = prefs.getInt("last_checkpoint_recommendation_count", 0)
        val lastAt = prefs.getLong("last_checkpoint_at_ms", 0L)
        val due = when (trigger) {
            "RECOMMENDATIONS" -> currentCount - archivedCount >=
                MarketDiagnosticCheckpointScheduler.MIN_RECOMMENDATIONS
            else -> NseEquitySessionCalendar.isOpen(now) &&
                (lastAt == 0L || System.currentTimeMillis() - lastAt >=
                    MarketDiagnosticCheckpointScheduler.CHECKPOINT_INTERVAL_MS)
        }
        if (!due) {
            MarketDiagnosticCheckpointScheduler.scheduleNextTimeCheckpoint(applicationContext)
            return Result.success()
        }

        var prepared: TradingRepository.PreparedLogExport? = null
        val directory = File(applicationContext.filesDir, "market_diagnostic_checkpoints")
        val stamp = System.currentTimeMillis()
        val staging = File(directory, "Phayra-$today-$stamp.zip.part")
        val destination = File(directory, "Phayra-$today-$stamp.zip")
        return try {
            check(directory.mkdirs() || directory.isDirectory) {
                "Unable to create market checkpoint directory"
            }
            // Market-hours checkpoint deliberately omits research DB backup. It includes
            // diagnostics, recommendation history and current summaries only; no broker calls.
            prepared = repository.prepareCompleteLogs(includeResearchBackup = false)
            val source = requireNotNull(prepared)
            source.file.copyTo(staging, overwrite = true)
            check(staging.isFile && staging.length() == source.sizeBytes && staging.length() > 22L) {
                "Checkpoint staging copy is incomplete"
            }
            val staged = DiagnosticZipIntegrity.verify(staging, source.result.entries)
            check(staged.entries == source.result.entries) { "Checkpoint staging ZIP verification failed" }
            if (!staging.renameTo(destination)) {
                staging.copyTo(destination, overwrite = true)
                check(staging.delete()) { "Unable to finalize checkpoint archive" }
            }
            val verified = DiagnosticZipIntegrity.verify(destination, source.result.entries)
            check(destination.length() == source.sizeBytes && verified.entries == source.result.entries) {
                "Checkpoint final ZIP verification failed"
            }

            val savedAt = System.currentTimeMillis()
            check(prefs.edit()
                .putLong("last_checkpoint_at_ms", savedAt)
                .putInt("last_checkpoint_recommendation_count", currentCount)
                .putString("last_checkpoint_file", destination.name)
                .putLong("last_checkpoint_bytes", destination.length())
                .putString("last_checkpoint_trigger", trigger)
                .commit()) { "Unable to persist checkpoint status" }

            val archives = directory.listFiles()
                ?.filter { it.isFile && it.extension.equals("zip", true) }
                ?.sortedByDescending { it.lastModified() }.orEmpty()
            archives.drop(MAX_CHECKPOINTS).forEach { it.delete() }
            audit.logAsync("DIAGNOSTIC_CHECKPOINT", "VERIFIED_CHECKPOINT_COMPLETED", mapOf(
                "file" to destination.name, "bytes" to destination.length(),
                "entries" to verified.entries, "trigger" to trigger,
                "recommendations_in_snapshot" to currentCount,
                "retained_checkpoints" to minOf(archives.size, MAX_CHECKPOINTS),
                "research_backup_included" to false, "raw_logs_deleted" to false,
                "broker_state_cleared" to false, "groww_api_calls" to 0
            ))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            staging.delete()
            audit.logAsync("DIAGNOSTIC_CHECKPOINT", "CHECKPOINT_FAILED", mapOf(
                "trigger" to trigger,
                "reason" to (error.message ?: error.javaClass.simpleName).take(160),
                "recommendations" to currentCount, "raw_logs_deleted" to false,
                "groww_api_calls" to 0
            ))
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            prepared?.file?.delete()
            MarketDiagnosticCheckpointScheduler.scheduleNextTimeCheckpoint(applicationContext)
        }
    }

    companion object {
        private const val MAX_CHECKPOINTS = 8
    }
}
