package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.repository.EquityEodResearchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.ZoneId
import java.time.ZonedDateTime

/** At most 25 NSE-wide official EOD ZIP requests per work pass; NO Groww requests. */
@HiltWorker
class EquityEodRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eod: EquityEodResearchRepository,
    private val audit: AuditLogger
) : CoroutineWorker(appContext,params) {
    override suspend fun doWork(): Result {
        if (!EquityEodResearchRepository.afterHours(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")))) {
            EquityEodRefreshScheduler.scheduleNext(applicationContext,afterRun=true)
            return Result.success()
        }
        val result=runCatching { eod.refreshAfterHours(25) }
        return result.fold({ done ->
            EquityEodRefreshScheduler.scheduleNext(applicationContext,afterRun=true,retryAfterMs=done.retryAfterMs)
            audit.logAsync("EOD_RESEARCH","AFTER_HOURS_PASS",mapOf(
                "imported_days" to done.importedDays,
                "actual_archive_requests_including_ohlc_backfill" to done.requestedDays,
                "missing_weekdays_before_pass_including_possible_holidays" to done.missingWeekdays,
                "source_failure" to done.sourceFailure,
                "failure_code" to eod.snapshot().archiveFailureCode,
                "retry_after_ms" to done.retryAfterMs,
                "last_failure" to done.lastFailure,
                "groww_requests" to 0,
                "attempt" to runAttemptCount
            ))
            if(!done.sourceFailure && done.retryAfterMs==0L &&
                done.requestedDays >= 25 && done.importedDays > 0 && runAttemptCount < 7)
                Result.retry() else Result.success()
        }, { e ->
            EquityEodRefreshScheduler.scheduleNext(applicationContext,afterRun=true)
            audit.logAsync("EOD_RESEARCH","WORK_FAILED",mapOf(
                "reason" to (e.message ?: e.javaClass.simpleName).take(130),
                "groww_requests" to 0, "auto_trade_enabled" to false
            ))
            Result.success()
        })
    }
}
