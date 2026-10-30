package com.multify.traderpro.engine

data class MarketCandleBar(val atMs:Long,val open:Double,val high:Double,val low:Double,val close:Double,val volume:Double)

object FiveMinuteCandleAggregator {
    private const val ONE_MINUTE_MS=60_000L
    private const val FIVE_MINUTES_MS=300_000L
    fun aggregate(candles:List<MarketCandleBar>,asOfMs:Long=Long.MAX_VALUE):List<MarketCandleBar>{
        val valid=candles.filter{b->
            b.atMs>0L&&b.open>0.0&&b.high>=maxOf(b.open,b.close)&&b.low>0.0&&b.low<=minOf(b.open,b.close)&&
                b.close>0.0&&b.volume>=0.0&&listOf(b.open,b.high,b.low,b.close,b.volume).all{it.isFinite()}&&
                (asOfMs==Long.MAX_VALUE||b.atMs+ONE_MINUTE_MS<=asOfMs)
        }
        return valid.groupBy{it.atMs/FIVE_MINUTES_MS}.mapNotNull{(bucket,grouped)->
            val bucketStart=bucket*FIVE_MINUTES_MS
            val distinct=grouped.sortedBy{it.atMs}.distinctBy{((it.atMs-bucketStart)/ONE_MINUTE_MS).toInt()}
            val slots=distinct.map{((it.atMs-bucketStart)/ONE_MINUTE_MS).toInt()}.toSet()
            if(slots!=setOf(0,1,2,3,4)) return@mapNotNull null
            val ordered=distinct.sortedBy{it.atMs}
            MarketCandleBar(bucketStart,ordered.first().open,ordered.maxOf{it.high},ordered.minOf{it.low},
                ordered.last().close,ordered.sumOf{it.volume})
        }.sortedBy{it.atMs}
    }
}
