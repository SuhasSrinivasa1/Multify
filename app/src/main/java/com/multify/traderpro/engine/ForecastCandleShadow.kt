package com.multify.traderpro.engine
import com.multify.traderpro.data.network.HistoricalPayload
object ForecastCandleShadow {
    data class FiveMinuteSummary(val completeBars:Int,val lastClose:Double?)
    fun aggregateFiveMinute(history:HistoricalPayload,asOfMs:Long=Long.MAX_VALUE):FiveMinuteSummary{
        val bars=history.candles.mapNotNull{c->
            if(c.size<6)return@mapNotNull null
            val at=MarketCandleTimestampParser.toEpochMillis(c[0])?:return@mapNotNull null
            val o=runCatching{c[1].asDouble}.getOrNull()?:return@mapNotNull null
            val h=runCatching{c[2].asDouble}.getOrNull()?:return@mapNotNull null
            val l=runCatching{c[3].asDouble}.getOrNull()?:return@mapNotNull null
            val close=runCatching{c[4].asDouble}.getOrNull()?:return@mapNotNull null
            val v=runCatching{c[5].asDouble}.getOrNull()?:return@mapNotNull null
            MarketCandleBar(at,o,h,l,close,v)
        }
        val complete=FiveMinuteCandleAggregator.aggregate(bars,asOfMs)
        return FiveMinuteSummary(complete.size,complete.lastOrNull()?.close)
    }
}
