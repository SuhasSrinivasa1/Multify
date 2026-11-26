package com.multify.traderpro.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.multify.traderpro.engine.NseEquitySessionCalendar
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Best-effort diagnostic snapshots. Never clears app data or broker/protection state. */
object MarketDiagnosticCheckpointScheduler {
    internal val india: ZoneId = ZoneId.of("Asia/Kolkata")
    internal const val PREFS = "phayra_market_diagnostic_checkpoints"
    internal const val MIN_RECOMMENDATIONS = com.multify.traderpro.engine.MarketDiagnosticCheckpointPolicy.RECOMMENDATION_BATCH_SIZE
    internal const val CHECKPOINT_INTERVAL_MS = com.multify.traderpro.engine.MarketDiagnosticCheckpointPolicy.TIME_INTERVAL_MS

    fun onAppStarted(context: Context, recommendationCount: Int) {
        maybeScheduleForRecommendations(context, recommendationCount)
        scheduleNextTimeCheckpoint(context)
    }

    fun onRecommendationCountChanged(context: Context, recommendationCount: Int) {
        maybeScheduleForRecommendations(context, recommendationCount)
    }

    private fun maybeScheduleForRecommendations(context: Context, recommendationCount: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val archivedCount = prefs.getInt("last_checkpoint_recommendation_count", 0)
        if (recommendationCount - archivedCount < MIN_RECOMMENDATIONS) return
        val bucket = recommendationCount / MIN_RECOMMENDATIONS
        val request = OneTimeWorkRequestBuilder<MarketDiagnosticCheckpointWorker>()
            .setInputData(Data.Builder().putString("trigger", "RECOMMENDATIONS").build())
            .addTag("phayra-market-diagnostic-checkpoint")
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "phayra-market-checkpoint-count-$bucket", ExistingWorkPolicy.KEEP, request
        )
    }

    fun scheduleNextTimeCheckpoint(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = ZonedDateTime.now(india)
        val last = prefs.getLong("last_checkpoint_at_ms", 0L)
        val marketOpen = NseEquitySessionCalendar.isOpen(now)
        val target = if (marketOpen) {
            val due = if (last > 0L)
                java.time.Instant.ofEpochMilli(last).atZone(india).plusHours(3)
            else now.plusHours(3)
            val latestToday = now.toLocalDate().atTime(LocalTime.of(15, 15)).atZone(india)
            if (due.isAfter(latestToday)) latestToday else due
        } else {
            val openToday = now.toLocalDate().atTime(LocalTime.of(9, 15)).atZone(india)
            if (now.isBefore(openToday)) openToday
            else now.toLocalDate().plusDays(1).atTime(LocalTime.of(9, 15)).atZone(india)
        }
        val delay = Duration.between(now, target).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<MarketDiagnosticCheckpointWorker>()
            .setInputData(Data.Builder().putString("trigger", "TIME").build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("phayra-market-diagnostic-checkpoint")
            .build()
        // Time-keyed unique work permits a threshold-triggered checkpoint to move
        // the next three-hour deadline forward without cancelling a running worker.
        WorkManager.getInstance(app).enqueueUniqueWork(
            "phayra-market-checkpoint-time-${target.toEpochSecond()}",
            ExistingWorkPolicy.KEEP, request
        )
    }
}
