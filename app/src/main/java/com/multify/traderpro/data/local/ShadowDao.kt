package com.multify.traderpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ShadowDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPosition(position: ShadowPositionEntity): Long

    @Update
    suspend fun updatePosition(position: ShadowPositionEntity)

    @Query("SELECT * FROM shadow_positions WHERE status='OPEN' ORDER BY openedAtMs ASC")
    suspend fun openPositions(): List<ShadowPositionEntity>

    @Query("SELECT * FROM shadow_positions ORDER BY openedAtMs ASC")
    suspend fun allPositions(): List<ShadowPositionEntity>

    @Query("SELECT * FROM shadow_positions WHERE status='OPEN' AND symbol=:symbol LIMIT 1")
    suspend fun openPosition(symbol: String): ShadowPositionEntity?

    @Query("DELETE FROM shadow_positions WHERE id=:id")
    suspend fun deletePosition(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrade(trade: ShadowTradeEntity): Long

    @Query("SELECT * FROM shadow_trades WHERE closedAtMs >= :sinceMs ORDER BY closedAtMs DESC")
    suspend fun tradesSince(sinceMs: Long): List<ShadowTradeEntity>

    @Query("SELECT * FROM shadow_trades ORDER BY closedAtMs ASC")
    suspend fun allTrades(): List<ShadowTradeEntity>

    @Query("SELECT COUNT(*) FROM shadow_trades WHERE symbol=:symbol AND closedAtMs >= :sinceMs")
    suspend fun tradeCountSince(symbol: String, sinceMs: Long): Int

    @Query("SELECT * FROM shadow_trades WHERE symbol=:symbol AND side=:side AND closedAtMs >= :sinceMs ORDER BY closedAtMs DESC LIMIT 1")
    suspend fun latestTrade(symbol: String, side: String, sinceMs: Long): ShadowTradeEntity?
}
