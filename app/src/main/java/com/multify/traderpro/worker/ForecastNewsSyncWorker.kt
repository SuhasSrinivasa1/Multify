package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.repository.TradingRepository
import com.multify.traderpro.data.logging.AuditLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** 15-minute Android recovery fallback; 60s cadence requires the foreground service. */
@HiltWorker
class ForecastNewsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TradingRepository,
    private val audit: AuditLogger
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        repository.monitorForecastNews()
        repository.monitorForecastNewsOutcomes()
        Result.success()
    }.getOrElse {
        audit.logAsync("FORECAST_NEWS", "FALLBACK_WORKER_FAILED", mapOf(
            "error_type" to it.javaClass.simpleName))
        Result.retry()
    }
}
