package com.multify.traderpro.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Single recurring OS worker. All remote requests remain guarded by IST and host cooldown. */
object EquityEodRefreshScheduler {
    @Suppress("UNUSED_PARAMETER")
    fun scheduleNext(context: Context, afterRun: Boolean = false) {
        if (afterRun) return
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelAllWorkByTag("eod-off-market-only")
        wm.enqueueUniquePeriodicWork(
            "phayra-research-refresh-v9054", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EquityEodRefreshWorker>(30, TimeUnit.MINUTES)
                .addTag("phayra-research-refresh-v9054").build()
        )
    }
}
