package com.multify.traderpro.data.repository

import com.multify.traderpro.engine.EquityEodBar
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Immutable per-symbol observation. Sources and absences are exported in the daily feature snapshot. */
internal data class LargeWatchEvidence(
    val features: Map<String, Double?>,
    val eodDate: String,
    val marketDate: String,
    val niftyRegime: String,
    val sectorIndex: String?,
    val sectorRegime: String,
    val indexEvidenceComplete: Boolean
)

internal object LargeWatchFeaturePolicy {
    /** 40+ deterministic snapshot fields, with null when upstream prices/fundamentals are missing. */
    val METRICS = listOf(
        "stock_close", "stock_pe", "stock_pb", "market_cap_rank",
        "return_1d_pct", "return_3d_pct", "return_5d_pct", "return_10d_pct",
        "return_20d_pct", "return_40d_pct",
        "ema9", "ema20", "ema50", "ema9_to_ema20_pct", "ema20_to_ema50_pct",
        "close_to_ema9_pct", "close_to_ema20_pct", "close_to_ema50_pct",
        "sma20", "sma50", "close_to_sma20_pct", "close_to_sma50_pct",
        "ema9_slope_5d_pct", "ema20_slope_5d_pct",
        "rsi14", "volatility_5d_pct", "volatility_20d_pct",
        "volume_today", "volume_avg_20d", "volume_today_to_avg20",
        "volume_avg5_to_avg20", "up_days_last_5_pct", "up_days_last_20_pct",
        "distance_to_20d_closing_high_pct", "distance_to_20d_closing_low_pct",
        "distance_to_50d_session_low_pct", "closing_range_20d_pct",
        "nifty_eod_move_pct", "nifty_previous_eod_move_pct", "nifty_5d_move_pct",
        "nifty_index_pe", "nifty_index_pb",
        "sector_eod_move_pct", "sector_previous_eod_move_pct", "sector_5d_move_pct",
        "sector_index_pe", "sector_index_pb",
        "stock_vs_nifty_eod_pct", "stock_vs_sector_eod_pct",
        "stock_vs_nifty_5d_pct", "stock_vs_sector_5d_pct",
        "intraday_from_open_pct", "intraday_vs_peer_pct",
        "intraday_largecap_breadth_pct", "relative_5d_vs_equity_peers_pct",
        "sector_peer_5d_pct", "sector_peer_breadth_pct",
        "volume_acceleration_5d", "swing_qualified", "intraday_qualified"
    )
    private fun percent(a: Double?, b: Double?): Double? =
        if(a == null || b == null || !a.isFinite() || !b.isFinite() || b<=0.0)
            null else (a/b-1.0)*100.0
    private fun avg(v: List<Double>) = v.takeIf{it.isNotEmpty()}?.average()
    private fun ema(prices: List<Double>, n:Int): Double? {
        if(prices.size<n)return null
        val alpha=2.0/(n+1.0)
        return prices.drop(1).fold(prices.first()) { last,p -> alpha*p+(1.0-alpha)*last }
    }
    private fun volatility(prices: List<Double>, days:Int): Double? {
        if(prices.size<=days)return null
        val daily=prices.takeLast(days+1).zipWithNext { a,b -> (b/a-1.0)*100.0 }
        val mu=daily.average()
        return sqrt(daily.map {(it-mu).pow(2)}.average())
    }
    private fun rsi(prices:List<Double>, periods:Int=14): Double? {
        if(prices.size<=periods)return null
        val d=prices.takeLast(periods+1).zipWithNext { a,b -> b-a }
        val gains=d.filter{it>0}.sum()
        val losses=-d.filter{it<0}.sum()
        return if(losses==0.0)100.0 else 100.0-100.0/(1.0+gains/losses)
    }
    private fun upDays(prices:List<Double>, days:Int):Double? {
        if(prices.size<=days)return null
        return prices.takeLast(days+1).zipWithNext { a,b -> b>a }.count{it}.toDouble()/days*100
    }
    private fun metricClose(prices:List<Double>, days:Int):Double? =
        if(prices.size>days)percent(prices.last(),prices[prices.lastIndex-days])else null

    fun build(
        bars:List<EquityEodBar>, eodDate:String, pe:Double?, rank:Int?,
        nifty:List<LargeIndexDaily>, sector:List<LargeIndexDaily>,
        sectorIndexName:String?, intradayPct:Double?, intradayRelativePct:Double?,
        intradayBreadthPct:Double?, relativeFiveDayPct:Double?,
        sectorFiveDayPct:Double?, sectorBreadthPct:Double?,
        volumeAcceleration:Double?, swing:Boolean, intraday:Boolean
    ):LargeWatchEvidence? {
        val day=runCatching{LocalDate.parse(eodDate)}.getOrNull()?:return null
        val good=bars.filter{ !it.date.isAfter(day) && it.close.isFinite() &&
            it.close>0.0 && it.low.isFinite() && it.low>0.0 && it.volume>=0L }
            .sortedBy{it.date}
        if(good.size<55 || good.last().date!=day)return null
        val prices=good.map{it.close}
        val close=prices.last()
        val e9=ema(prices,9)
        val e20=ema(prices,20)
        val e50=ema(prices,50)
        val v20=avg(good.takeLast(20).map{it.volume.toDouble()}.filter{it>0})
        val v5=avg(good.takeLast(5).map{it.volume.toDouble()}.filter{it>0})
        fun indexReturn(rows:List<LargeIndexDaily>,dayCount:Int):Double? =
            if(rows.size>dayCount)percent(rows.last().close,rows[rows.lastIndex-dayCount].close)else null
        val market = nifty.lastOrNull()?.takeIf{it.day==day}
        val sectorToday = sector.lastOrNull()?.takeIf{it.day==day}
        val niftyReturn = market?.changePct
            ?: if(market!=null)indexReturn(nifty,1)else null
        val sectorReturn = sectorToday?.changePct
            ?: if(sectorToday!=null)indexReturn(sector,1)else null
        val nifty5=if(market!=null)indexReturn(nifty,5)else null
        val sector5=if(sectorToday!=null)indexReturn(sector,5)else null
        val stock1=metricClose(prices,1)
        val stock5=metricClose(prices,5)
        val fields=linkedMapOf<String,Double?>(
            "stock_close" to close,
            "stock_pe" to pe?.takeIf {it.isFinite()&&it>0.0},
            // NSE index P/B is never misrepresented as this STOCK'S price/book.
            "stock_pb" to null,
            "market_cap_rank" to rank?.toDouble(),
            "return_1d_pct" to stock1,
            "return_3d_pct" to metricClose(prices,3),
            "return_5d_pct" to stock5,
            "return_10d_pct" to metricClose(prices,10),
            "return_20d_pct" to metricClose(prices,20),
            "return_40d_pct" to metricClose(prices,40),
            "ema9" to e9, "ema20" to e20, "ema50" to e50,
            "ema9_to_ema20_pct" to percent(e9,e20),
            "ema20_to_ema50_pct" to percent(e20,e50),
            "close_to_ema9_pct" to percent(close,e9),
            "close_to_ema20_pct" to percent(close,e20),
            "close_to_ema50_pct" to percent(close,e50),
            "sma20" to avg(prices.takeLast(20)),
            "sma50" to avg(prices.takeLast(50)),
            "close_to_sma20_pct" to percent(close,avg(prices.takeLast(20))),
            "close_to_sma50_pct" to percent(close,avg(prices.takeLast(50))),
            "ema9_slope_5d_pct" to percent(e9,ema(prices.dropLast(5),9)),
            "ema20_slope_5d_pct" to percent(e20,ema(prices.dropLast(5),20)),
            "rsi14" to rsi(prices),
            "volatility_5d_pct" to volatility(prices,5),
            "volatility_20d_pct" to volatility(prices,20),
            "volume_today" to good.last().volume.toDouble(),
            "volume_avg_20d" to v20,
            "volume_today_to_avg20" to if(v20!=null)good.last().volume/v20 else null,
            "volume_avg5_to_avg20" to if(v5!=null&&v20!=null)v5/v20 else null,
            "up_days_last_5_pct" to upDays(prices,5),
            "up_days_last_20_pct" to upDays(prices,20),
            "distance_to_20d_closing_high_pct" to percent(close,prices.takeLast(20).maxOrNull()),
            "distance_to_20d_closing_low_pct" to percent(close,prices.takeLast(20).minOrNull()),
            "distance_to_50d_session_low_pct" to percent(close,good.takeLast(50).minOf{it.low}),
            "closing_range_20d_pct" to percent(prices.takeLast(20).maxOrNull(),prices.takeLast(20).minOrNull()),
            "nifty_eod_move_pct" to niftyReturn,
            "nifty_previous_eod_move_pct" to if(market!=null)nifty.dropLast(1).lastOrNull()?.changePct else null,
            "nifty_5d_move_pct" to nifty5,
            "nifty_index_pe" to market?.pe,
            "nifty_index_pb" to market?.pb,
            "sector_eod_move_pct" to sectorReturn,
            "sector_previous_eod_move_pct" to if(sectorToday!=null)sector.dropLast(1).lastOrNull()?.changePct else null,
            "sector_5d_move_pct" to sector5,
            "sector_index_pe" to sectorToday?.pe,
            "sector_index_pb" to sectorToday?.pb,
            "stock_vs_nifty_eod_pct" to if(stock1!=null&&niftyReturn!=null)stock1-niftyReturn else null,
            "stock_vs_sector_eod_pct" to if(stock1!=null&&sectorReturn!=null)stock1-sectorReturn else null,
            "stock_vs_nifty_5d_pct" to if(stock5!=null&&nifty5!=null)stock5-nifty5 else null,
            "stock_vs_sector_5d_pct" to if(stock5!=null&&sector5!=null)stock5-sector5 else null,
            "intraday_from_open_pct" to intradayPct,
            "intraday_vs_peer_pct" to intradayRelativePct,
            "intraday_largecap_breadth_pct" to intradayBreadthPct,
            "relative_5d_vs_equity_peers_pct" to relativeFiveDayPct,
            "sector_peer_5d_pct" to sectorFiveDayPct,
            "sector_peer_breadth_pct" to sectorBreadthPct,
            "volume_acceleration_5d" to volumeAcceleration,
            "swing_qualified" to if(swing)1.0 else 0.0,
            "intraday_qualified" to if(intraday)1.0 else 0.0
        )
        check(fields.keys.toList()==METRICS) { "LARGE_WATCH_FEATURE_SCHEMA_DRIFT" }
        return LargeWatchEvidence(
            fields,eodDate,market?.day?.toString().orEmpty(),
            LargeIndexResearchPolicy.regime(niftyReturn),
            sectorIndexName,LargeIndexResearchPolicy.regime(sectorReturn),
            market!=null && sectorToday!=null
        )
    }
}
