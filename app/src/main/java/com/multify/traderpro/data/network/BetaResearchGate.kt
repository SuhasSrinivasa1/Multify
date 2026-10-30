package com.multify.traderpro.data.network

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BetaResearchGate @Inject constructor() {
    enum class Type { LIVE, HISTORICAL }
    data class UsageSnapshot(
        val liveRequests:Long,val historicalRequests:Long,val liveFailures:Long,val historicalFailures:Long,
        val liveRateLimits:Long,val historicalRateLimits:Long,val lastLatencyMs:Long
    )
    private data class Window(val second:ArrayDeque<Long> = ArrayDeque(),val minute:ArrayDeque<Long> = ArrayDeque(),var cooldownUntil:Long=0L)
    private val lock=Mutex()
    private val windows=Type.values().associateWith{Window()}
    private val liveSec=9; private val liveMinute=285; private val histSec=1; private val histMinute=42
    private val liveRequests=AtomicLong(); private val historicalRequests=AtomicLong()
    private val liveFailures=AtomicLong(); private val historicalFailures=AtomicLong()
    private val liveRateLimits=AtomicLong(); private val historicalRateLimits=AtomicLong()
    private val lastLatencyMs=AtomicLong()

    suspend fun <T> read(type:Type,block:suspend()->T):T {
        val started=SystemClock.elapsedRealtime()
        while(true){
            val wait=lock.withLock{
                val w=windows.getValue(type);val now=SystemClock.elapsedRealtime()
                while(w.second.isNotEmpty()&&w.second.first()<=now-1_000L)w.second.removeFirst()
                while(w.minute.isNotEmpty()&&w.minute.first()<=now-60_000L)w.minute.removeFirst()
                val sec=if(type==Type.LIVE)liveSec else histSec
                val min=if(type==Type.LIVE)liveMinute else histMinute
                val ms=maxOf((w.cooldownUntil-now).coerceAtLeast(0L),
                    if(w.second.size>=sec)(w.second.first()+1_000L-now).coerceAtLeast(1L) else 0L,
                    if(w.minute.size>=min)(w.minute.first()+60_000L-now).coerceAtLeast(1L) else 0L)
                if(ms==0L){w.second.addLast(now);w.minute.addLast(now)}
                ms
            }
            if(wait<=0L)break
            delay(wait.coerceAtLeast(5L))
        }
        return try{
            val result=block()
            if(type==Type.LIVE)liveRequests.incrementAndGet() else historicalRequests.incrementAndGet()
            lastLatencyMs.set((SystemClock.elapsedRealtime()-started).coerceAtLeast(0L))
            result
        }catch(t:Throwable){
            if(type==Type.LIVE){liveRequests.incrementAndGet();liveFailures.incrementAndGet()}
            else{historicalRequests.incrementAndGet();historicalFailures.incrementAndGet()}
            lastLatencyMs.set((SystemClock.elapsedRealtime()-started).coerceAtLeast(0L))
            if(t is HttpException&&t.code()==429){
                if(type==Type.LIVE)liveRateLimits.incrementAndGet() else historicalRateLimits.incrementAndGet()
                lock.withLock{val w=windows.getValue(type);w.cooldownUntil=maxOf(w.cooldownUntil,SystemClock.elapsedRealtime()+60_000L)}
            }
            throw t
        }
    }
    fun snapshot()=UsageSnapshot(liveRequests.get(),historicalRequests.get(),liveFailures.get(),historicalFailures.get(),
        liveRateLimits.get(),historicalRateLimits.get(),lastLatencyMs.get())
    fun configuredLiveLimitPerMinute()=liveMinute
    fun configuredHistoricalLimitPerMinute()=histMinute
}
