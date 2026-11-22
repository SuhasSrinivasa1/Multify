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
        EquityEodRefreshScheduler.scheduleNext(applicationContext,afterRun=true)
        return result.fold({ done ->
            audit.logAsync("EOD_RESEARCH","AFTER_HOURS_PASS",mapOf(
                "imported_days" to done.importedDays,
                "actual_archive_requests" to done.requestedDays,
                "missing_weekdays_before_pass" to done.missingWeekdays,
                "source_failure" to done.sourceFailure,
                "groww_requests" to 0,
                "attempt" to runAttemptCount
            ))
            if(!done.sourceFailure && done.requestedDays >= 25 && done.importedDays > 0 && runAttemptCount < 7)
                Result.retry() else Result.success()
        }, { e ->
            audit.logAsync("EOD_RESEARCH","WORK_FAILED",mapOf(
                "reason" to (e.message ?: e.javaClass.simpleName).take(130),
                "groww_requests" to 0, "auto_trade_enabled" to false
            ))
            // No daytime retries or retry storms; next calendar night's work will catch up.
            Result.success()
        })
    }
}
