package com.multify.traderpro.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.engine.NseEquitySessionCalendar
import com.multify.traderpro.service.TradingEngineService
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reschedules the next weekday 09:00 IST start (or one-off 2026-11-08
 * 14:00 IST start), then attempts to ensure the existing foreground engine
 * runs. No buying setting is changed; no broker order is created.
 */
class TradingSessionStartWorker(
    appContext: Context, params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        TradingSessionStartScheduler.scheduleNext(applicationContext, now)
        val eligible = NseEquitySessionCalendar.isWarmupWindow(now) ||
            NseEquitySessionCalendar.isFixedScanWindow(now) ||
            NseEquitySessionCalendar.isOpen(now)
        if (!eligible) return Result.success()
        val started = TradingEngineService.start(applicationContext)
        if (!started) {
            Log.w("MultifySessionStart", "Foreground start deferred or denied by Android; attempting OS-managed retry")
            return Result.retry()
        }
        Log.i("MultifySessionStart", "Foreground engine start requested for ${now.toLocalDate()}")
        return Result.success()
    }
}
