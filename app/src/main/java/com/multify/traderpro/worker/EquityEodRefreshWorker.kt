package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.repository.EquityEodResearchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

/** Bounded off-hours NSE imports; local hourly research while open; no Groww. */
@HiltWorker
class EquityEodRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eod: EquityEodResearchRepository,
    private val audit: AuditLogger
) : CoroutineWorker(appContext,params) {
    override suspend fun doWork(): Result {
        val now=ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        val prefs=applicationContext.getSharedPreferences("phayra_research_refresh_v9054",Context.MODE_PRIVATE)
        val off=EquityEodResearchRepository.afterHours(now)
        val market=com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(now)
        if(!off && !market) return Result.success()
        val slot=now.toLocalDate().toString()+"-"+now.hour
        if(market && prefs.getString("last_local_hour","")==slot) return Result.success()
        return try {
            val progress=if(off)eod.refreshAfterHours(25) else null
            if(market)eod.refreshInstitutionalOnly()
            val snapshot=withContext(Dispatchers.IO){eod.snapshot()}
            if(market) prefs.edit().putString("last_local_hour",slot).apply()
            audit.logAsync("EOD_RESEARCH","SCHEDULED_RESEARCH_REFRESH",mapOf(
                "mode" to if(off)"AFTER_HOURS" else "MARKET_HOUR_OWNERSHIP",
                "latest_eod" to snapshot.latestDay,"sessions" to snapshot.completedDays,
                "imported_days" to (progress?.importedDays?:0),
                "archive_requests" to (progress?.requestedDays?:0),
                "source_failure" to (progress?.sourceFailure?:false),"groww_requests" to 0))
            Result.success()
        } catch(e:CancellationException){throw e}
        catch(e:Exception){
            audit.logAsync("EOD_RESEARCH","SCHEDULED_RESEARCH_REFRESH_FAILED",mapOf(
                "reason" to (e.message?:e.javaClass.simpleName).take(160),"groww_requests" to 0))
            Result.success()
        }
    }
}
