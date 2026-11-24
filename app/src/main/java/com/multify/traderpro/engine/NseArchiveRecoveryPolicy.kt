package com.multify.traderpro.engine
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
/** NSE public research only. Never shares the Groww governor or trading state. */
object NseArchiveRecoveryPolicy {
    const val MIN_GAP_MS=1_500L
    const val ACCESS_COOLDOWN_MS=60*60_000L
    const val TRANSIENT_COOLDOWN_MS=15*60_000L
    fun retryAfterMs(raw:String?,now:Long):Long {
        val t=raw?.trim()?.takeIf{it.isNotEmpty()}?:return 0
        t.toLongOrNull()?.let{return it.coerceIn(0,Long.MAX_VALUE/1000)*1000}
        return runCatching{(ZonedDateTime.parse(t,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now).coerceAtLeast(0)}.getOrDefault(0)
    }
    fun cooldownMs(code:Int,retryAfter:String?,now:Long):Long {
        val local=when(code){403,429->ACCESS_COOLDOWN_MS; 408,0->TRANSIENT_COOLDOWN_MS; in 500..599->TRANSIENT_COOLDOWN_MS;else->0L}
        return if(local==0L)0L else maxOf(local,retryAfterMs(retryAfter,now))
    }
    fun deadline(now:Long,delay:Long):Long=now+delay.coerceIn(0,Long.MAX_VALUE-now)
    fun missingRetryMs(recent:Boolean):Long=if(recent)2*60*60_000L else 7*24*60*60_000L
    fun allowed(now:Long,until:Long)=now>=until
}
