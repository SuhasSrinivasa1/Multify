package com.multify.traderpro.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules one NSE EOD archive refresh after market close; never a market-hours polling job. */
object SixMonthLowScheduler {
    private val INDIA = ZoneId.of("Asia/Kolkata")
    private val RUN_AT = LocalTime.of(18, 15)

    fun scheduleNext(context: Context, now: ZonedDateTime = ZonedDateTime.now(INDIA)) {
        var target = now.toLocalDate().atTime(RUN_AT).atZone(INDIA)
        if (!now.toLocalTime().isBefore(RUN_AT)) target = target.plusDays(1)
        while (target.dayOfWeek.value >= 6) target = target.plusDays(1)
        val delay = Duration.between(now.toInstant(), target.toInstant()).toMillis().coerceAtLeast(0L)
        val name = "nse-six-month-low-eod-${target.toLocalDate()}"
        val request = OneTimeWorkRequestBuilder<SixMonthLowRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag("nse-six-month-low-eod")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }
}
