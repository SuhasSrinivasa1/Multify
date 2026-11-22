package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.repository.LargeCapResearchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@HiltWorker
class SixMonthLowRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val research: LargeCapResearchRepository,
    private val audit: AuditLogger
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        SixMonthLowScheduler.scheduleNext(applicationContext, now)
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY ||
            now.toLocalTime().isBefore(LocalTime.of(16, 0)) ||
            com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(now)
        ) {
            audit.log("SIX_MONTH_LOW", "SCHEDULED_RUN_SKIPPED_OUTSIDE_AFTER_HOURS", mapOf(
                "local_time" to now.toString(),
                "network_calls" to 0,
                "market_hours_requests" to 0
            ))
            return Result.success()
        }
        return try {
            val status = research.refreshAfterHours()
            audit.log("SIX_MONTH_LOW", "WORKER_SUCCESS", mapOf(
                "state" to status.state,
                "latest_trade_date" to status.latestTradeDate,
                "history_sessions" to status.historySessions,
                "symbols_stored" to status.symbolsStored,
                "attempt" to runAttemptCount
            ))
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            audit.log("SIX_MONTH_LOW", "WORKER_FAILED", mapOf(
                "type" to failure.javaClass.simpleName,
                "message" to (failure.message ?: "").take(180),
                "attempt" to runAttemptCount,
                "last_good_snapshot_preserved" to true
            ))
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
