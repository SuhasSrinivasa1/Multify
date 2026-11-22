package com.multify.traderpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NseEodBarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBars(rows: List<NseEodBarEntity>)

    @Query("SELECT DISTINCT tradeDate FROM nse_eod_bars ORDER BY tradeDate DESC LIMIT :limit")
    suspend fun recentTradeDates(limit: Int): List<String>

    @Query("SELECT * FROM nse_eod_bars WHERE tradeDate >= :fromDate ORDER BY symbol ASC, tradeDate ASC")
    suspend fun barsSince(fromDate: String): List<NseEodBarEntity>

    @Query("SELECT MAX(tradeDate) FROM nse_eod_bars")
    suspend fun latestTradeDate(): String?

    @Query("SELECT COUNT(DISTINCT tradeDate) FROM nse_eod_bars")
    suspend fun tradeDateCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM nse_eod_bars WHERE tradeDate=:date)")
    suspend fun hasTradeDate(date: String): Boolean

    @Query("DELETE FROM nse_eod_bars WHERE tradeDate < :beforeDate")
    suspend fun deleteBefore(beforeDate: String)
}
