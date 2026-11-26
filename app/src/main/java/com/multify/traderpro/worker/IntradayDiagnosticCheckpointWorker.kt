package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.multify.traderpro.data.repository.TradingRepository
import com.multify.traderpro.data.repository.DiagnosticZipIntegrity
import com.multify.traderpro.data.logging.AuditLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Non-destructive cumulative checkpoints. No source or broker state removed. */
@HiltWorker
class IntradayDiagnosticCheckpointWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val repository: TradingRepository, private val audit: AuditLogger
):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val now=ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        val day=now.toLocalDate().toString()
        if(now.dayOfWeek.value>=6 || now.toLocalTime()<LocalTime.of(9,15) || now.toLocalTime()>LocalTime.of(18,0))
            return@withContext Result.success()
        val market=com.multify.traderpro.engine.NseEquitySessionCalendar.isOpen(now)
        val closed=now.toLocalTime()>=LocalTime.of(15,30)
        if(!market && !closed)return@withContext Result.success()
        val prefs=applicationContext.getSharedPreferences("intraday_diagnostic_checkpoints_v1",Context.MODE_PRIVATE)
        val same=prefs.getString("day","")==day
        val last=if(same)prefs.getLong("last",0) else 0L
        val count=repository.diagnosticCheckpointCount()
        val previous=if(same)prefs.getInt("count",0) else 0
        val open=now.toLocalDate().atTime(9,15).atZone(now.zone).toInstant().toEpochMilli()
        val due=count-previous>=20 || System.currentTimeMillis()-maxOf(last,open)>=3*60*60_000L
        val closing=closed && count>0 && (!same || !prefs.getBoolean("closed",false))
        if((!due && !closing) || (closed && !closing) || !repository.diagnosticCheckpointReady())
            return@withContext Result.success()
        var part:File?=null
        var prepared:TradingRepository.PreparedLogExport?=null
        try {
            val dir=File(applicationContext.filesDir,"intraday_diagnostic_checkpoints").apply{check(mkdirs()||isDirectory)}
            if(dir.usableSpace<512L*1024*1024)error("CHECKPOINT_STORAGE_LOW")
            val p=repository.prepareCompleteLogs(includeResearchBackup=false)
            prepared=p
            val dest=File(dir,"Phayra-$day-${System.currentTimeMillis()}-checkpoint.zip")
            val tmp=File(dir,dest.name+".part");part=tmp
            p.file.copyTo(tmp,overwrite=false)
            check(tmp.length()==p.sizeBytes)
            DiagnosticZipIntegrity.verify(tmp,p.result.entries)
            val md=MessageDigest.getInstance("SHA-256")
            tmp.inputStream().buffered().use{input->
                val buf=ByteArray(65536);while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}
            }
            val digest=md.digest().joinToString(""){"%02x".format(it)}
            check(tmp.renameTo(dest))
            check(prefs.edit().putString("day",day).putLong("last",System.currentTimeMillis())
                .putInt("count",count).putBoolean("closed",closing).putString("file",dest.name)
                .putLong("bytes",dest.length()).putString("sha256",digest).commit())
            audit.logAsync("DIAGNOSTIC_CHECKPOINT","VERIFIED",mapOf("file" to dest.name,"bytes" to dest.length(),
                "archive_sha256" to digest,"recommendations_at_start" to count,"entries" to p.result.entries,
                "source_deletion" to false,"cumulative" to true,"groww_requests" to 0))
        } catch(e:CancellationException){throw e}
        catch(e:Exception){audit.logAsync("DIAGNOSTIC_CHECKPOINT","DEFERRED_OR_FAILED",mapOf("reason" to (e.message?:e.javaClass.simpleName).take(150)))}
        finally{part?.delete();prepared?.file?.delete()}
        Result.success()
    }
    companion object {
        fun schedule(context:Context){
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                "phayra-intraday-diagnostic-checkpoint",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IntradayDiagnosticCheckpointWorker>(15,TimeUnit.MINUTES).build())
        }
    }
}
