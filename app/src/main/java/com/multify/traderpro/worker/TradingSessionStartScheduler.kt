package com.multify.traderpro.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.multify.traderpro.engine.NseEquitySessionCalendar
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Best-effort OS-managed restart when no foreground engine is alive.
 * WorkManager may defer execution (Doze/force-stop/OEM policies), so the
 * persistent foreground service remains the primary scan clock.
 * One unique work ID per session date avoids an active worker cancelling itself.
 */
object TradingSessionStartScheduler {
    private val INDIA = ZoneId.of("Asia/Kolkata")

    fun scheduleNext(context: Context, now: ZonedDateTime = ZonedDateTime.now(INDIA)) {
        val startup = NseEquitySessionCalendar.nextStartupAfter(now)
        val delay = java.time.Duration.between(now.toInstant(), startup.toInstant())
            .toMillis().coerceAtLeast(0L)
        val name = "nse-trading-start-${startup.toLocalDate()}"
        val request = OneTimeWorkRequestBuilder<TradingSessionStartWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("session-preopen-start")
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }
}
