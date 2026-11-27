package com.multify.traderpro.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.multify.traderpro.MainActivity
import com.multify.traderpro.R
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.LiveNewsSentimentPolicy
import com.multify.traderpro.engine.NseEquitySessionCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Notification-only exchange news capture; no Groww calls or order client. */
@Singleton
class LiveNewsIntelligence @Inject constructor(
    @ApplicationContext private val context:Context,private val monitor:ForecastNewsMonitor,
    private val journal:ForecastNewsJournal,private val sweepStore:FixedMarketDiscoveryStateStore,
    private val audit:AuditLogger
) {
    data class Pattern(val key:String,val samples:Int,val days:Int,val correct:Int)
    data class Snapshot(val events:List<ForecastNewsEvent>,val patterns:List<Pattern>,val lastPollMs:Long,val sourceStatus:String)
    private val india=ZoneId.of("Asia/Kolkata")
    @Volatile private var lastPollMs=0L
    private var lastSweepMs=0L
    private val prefs=context.getSharedPreferences("live_news_intelligence_v1",Context.MODE_PRIVATE)
    suspend fun cycle() {
        fun processAvailable(){
            val now=ZonedDateTime.now(india)
            if(NseEquitySessionCalendar.isOpen(now)){
                observeSweep(now)
                alertFresh(now)
            }
        }
        processAvailable()
        monitor.poll { processAvailable() }
        lastPollMs=System.currentTimeMillis()
        processAvailable()
    }
    fun snapshot():Snapshot {
        val cutoff=ZonedDateTime.now(india).toLocalDate().atStartOfDay(india).toInstant().toEpochMilli()
        val rows=journal.recent(10000).filter{it.liveNews!=null}
        fun day(row:ForecastNewsEvent)=Instant.ofEpochMilli(row.publishedAtMs).atZone(india).toLocalDate().toString()
        val groups=rows.filter{it.publishedAtMs<cutoff && it.liveNews?.outcome60Pct!=null && (it.liveNews?.outcome60AtMs?:Long.MAX_VALUE)<cutoff}
            .distinctBy{listOf(it.symbol,day(it),it.category,it.liveNews?.sentiment).joinToString("|")}
            .groupBy{it.category+" · "+it.liveNews?.sentiment}
        val patterns=groups.mapNotNull{(key,items)->
            val samples=items.mapNotNull{r->LiveNewsSentimentPolicy.directionalReturn(r.liveNews?.sentiment.orEmpty(),r.liveNews?.outcome60Pct?:return@mapNotNull null)}
            if(samples.isEmpty())null else Pattern(key,samples.size,items.map{day(it)}.distinct().size,samples.count{it>0})
        }.sortedByDescending{it.samples}
        return Snapshot(rows.sortedByDescending{it.publishedAtMs}.take(100),patterns,lastPollMs,monitor.liveFeedStatus())
    }
    private fun observeSweep(now:ZonedDateTime) {
        val day=now.toLocalDate().toString()
        val health=sweepStore.loadHealth(day)?:return
        if(health.abortedForNetwork||health.batchErrors!=0||health.coverageRatio<.85)return
        val sweep=sweepStore.load(day)?:return
        val at=sweep.updatedAtMs
        if(at<=lastSweepMs||System.currentTimeMillis()-at !in 0L..180_000L)return
        lastSweepMs=at
        for(row in journal.recent(3000)) {
            val m=row.liveNews?:continue
            if(row.symbol.isBlank()||row.symbol.startsWith("BSE:")||at<row.firstSeenAtMs||
                at-row.firstSeenAtMs>100*60_000L||m.outcome60Pct!=null)continue
            val p=sweep.previousLtps[row.symbol]?.takeIf{it.isFinite()&&it>0}?:continue
            if(m.baseline==null) {
                if(at-row.firstSeenAtMs<=5*60_000L)
                    journal.update(row.copy(liveNews=m.copy(baseline=p,baselineAtMs=at)))
                continue
            }
            val base=m.baseline.takeIf{it.isFinite()&&it>0}?:continue
            val elapsed=at-(m.baselineAtMs?:continue)
            if(elapsed<=0)continue
            val change=(p/base-1)*100
            val next=m.copy(
                outcome15Pct=m.outcome15Pct?:change.takeIf{elapsed in 15*60_000L..20*60_000L},
                outcome30Pct=m.outcome30Pct?:change.takeIf{elapsed in 30*60_000L..35*60_000L},
                outcome60Pct=m.outcome60Pct?:change.takeIf{elapsed in 60*60_000L..65*60_000L},
                outcome60AtMs=m.outcome60AtMs?:at.takeIf{elapsed in 60*60_000L..65*60_000L})
            if(next!=m)journal.update(row.copy(liveNews=next))
        }
    }
    private fun alertFresh(now:ZonedDateTime) {
        val at=System.currentTimeMillis()
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled())return
        manager.createNotificationChannel(NotificationChannel("live_stock_news_v1","Stock news · sentiment alerts",NotificationManager.IMPORTANCE_HIGH))
        val intent=PendingIntent.getActivity(context,9058,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for(row in journal.recent(1000).filter{r->
            val m=r.liveNews
            m!=null&&m.sentiment in setOf("POSITIVE","NEGATIVE")&&r.symbol.isNotBlank()&&m.alertedAtMs==null&&
                at-r.publishedAtMs in 0L..15*60_000L&&at-r.firstSeenAtMs in 0L..5*60_000L&&
                Instant.ofEpochMilli(r.publishedAtMs).atZone(india).toLocalDate()==now.toLocalDate()
        }.sortedBy{it.publishedAtMs}) {
            val m=row.liveNews?:continue
            val symbol=row.symbol
            val key=symbol+"|"+m.sentiment+"|"+row.category
            val quiet=at-prefs.getLong(key,0L) in 0L..5*60_000L
            val name=if(symbol.startsWith("BSE:"))row.headline.substringBefore(" - ").take(60)+" · "+symbol else symbol
            val details=row.headline+"\n"+m.evidence+"\nCatalysts 1h / 3h / 5h: "+m.cluster1h+" / "+m.cluster3h+" / "+m.cluster5h+"\n"+row.source+" · sentiment estimate, not a trade instruction"
            val n=NotificationCompat.Builder(context,"live_stock_news_v1")
                .setSmallIcon(R.drawable.ic_stat_trader).setColor(if(m.sentiment=="POSITIVE")0xff15803d.toInt() else 0xffb91c1c.toInt())
                .setContentTitle(name+" · "+m.sentiment).setContentText(row.headline.take(160))
                .setStyle(NotificationCompat.BigTextStyle().bigText(details)).setSubText("Phayra News Intelligence")
                .setContentIntent(intent).setAutoCancel(true).setOnlyAlertOnce(quiet).setSilent(quiet).setPriority(NotificationCompat.PRIORITY_HIGH).build()
            manager.notify("phayra_news_"+symbol,9058,n)
            prefs.edit().putLong(key,at).apply()
            journal.update(row.copy(liveNews=m.copy(alertedAtMs=at)))
            audit.logAsync("LIVE_NEWS","SENTIMENT_NOTIFICATION",mapOf("symbol" to symbol,"direction" to m.sentiment,"source" to row.source,"no_orders" to true,"groww_requests" to 0))
        }
    }
}
