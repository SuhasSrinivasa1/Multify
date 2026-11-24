package com.multify.traderpro.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.multify.traderpro.data.repository.EquityEodResearchRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Off-market best-effort OS scheduling; never relied on for broker protection. */
object EquityEodRefreshScheduler {
    private val india=ZoneId.of("Asia/Kolkata")
    fun scheduleNext(context: Context, afterRun: Boolean = false, retryAfterMs: Long = 0L) {
        val now=ZonedDateTime.now(india)
        val p=context.getSharedPreferences("suhas_shree_eod_state",Context.MODE_PRIVATE)
        val last=p.getLong("last_eod_attempt_ms",0L)
        val attemptedToday= last > 0 && java.time.Instant.ofEpochMilli(last).atZone(india).toLocalDate()==now.toLocalDate()
        val afterHours=EquityEodResearchRepository.afterHours(now)
        // Wait until 20:00 IST for the daily finalized bhavcopy to be published.
        val readyForDaily = now.toLocalTime() >= LocalTime.of(20,0) ||
            now.toLocalTime() < LocalTime.of(8,0)
        val runNow=afterHours && readyForDaily && !afterRun && !attemptedToday
        val todayAt=now.toLocalDate().atTime(LocalTime.of(20,0)).atZone(india)
        val normal=if(runNow)now else if(!now.isBefore(todayAt)) todayAt.plusDays(1) else todayAt
        val retry=if(retryAfterMs>now.toInstant().toEpochMilli()) Instant.ofEpochMilli(retryAfterMs).atZone(india) else null
        val scheduled=if(retry!=null && EquityEodResearchRepository.afterHours(retry)) retry else normal
        val delay=Duration.between(now,scheduled).toMillis().coerceAtLeast(0)
        val request=OneTimeWorkRequestBuilder<EquityEodRefreshWorker>()
            .setInitialDelay(delay,TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES)
            .addTag("eod-off-market-only")
            .build()
        val name="suhas-eod-${scheduled.toLocalDate()}-${scheduled.hour}${scheduled.minute}"
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(name,ExistingWorkPolicy.KEEP,request)
    }
}
