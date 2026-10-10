package com.multify.traderpro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ManagedTradeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPosition(position: ManagedPositionEntity): Long

    @Update
    suspend fun updatePosition(position: ManagedPositionEntity)

    @Query("SELECT * FROM managed_positions WHERE status='OPEN' ORDER BY openedAtMs ASC")
    suspend fun openPositions(): List<ManagedPositionEntity>

    @Query("SELECT * FROM managed_positions ORDER BY openedAtMs ASC")
    suspend fun allPositions(): List<ManagedPositionEntity>

    @Query("SELECT * FROM managed_positions WHERE status='OPEN' AND engine=:engine ORDER BY openedAtMs ASC")
    suspend fun openPositions(engine: String): List<ManagedPositionEntity>

    @Query("SELECT * FROM managed_positions WHERE status='OPEN' AND engine=:engine AND symbol=:symbol LIMIT 1")
    suspend fun openPosition(engine: String, symbol: String): ManagedPositionEntity?

    @Query("SELECT * FROM managed_positions WHERE status='OPEN' AND symbol=:symbol LIMIT 1")
    suspend fun openPositionForSymbol(symbol: String): ManagedPositionEntity?

    @Query("SELECT * FROM managed_positions WHERE status='OPEN' AND symbol=:symbol AND product=:product ORDER BY openedAtMs ASC")
    suspend fun openPositionsForProduct(symbol: String, product: String): List<ManagedPositionEntity>

    @Query("DELETE FROM managed_positions WHERE id=:id")
    suspend fun deletePosition(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrade(trade: ManagedTradeEntity): Long

    @Query("SELECT * FROM managed_trades WHERE closedAtMs >= :sinceMs ORDER BY closedAtMs DESC")
    suspend fun tradesSince(sinceMs: Long): List<ManagedTradeEntity>

    @Query("SELECT * FROM managed_trades ORDER BY closedAtMs ASC")
    suspend fun allTrades(): List<ManagedTradeEntity>

    @Query("SELECT * FROM managed_trades WHERE engine=:engine AND closedAtMs >= :sinceMs ORDER BY closedAtMs DESC")
    suspend fun tradesSince(engine: String, sinceMs: Long): List<ManagedTradeEntity>

    @Query("SELECT COUNT(*) FROM managed_trades WHERE engine=:engine AND symbol=:symbol AND closedAtMs >= :sinceMs")
    suspend fun tradeCountSince(engine: String, symbol: String, sinceMs: Long): Int

    @Query("SELECT * FROM managed_trades WHERE engine=:engine AND symbol=:symbol AND side=:side AND closedAtMs >= :sinceMs ORDER BY closedAtMs DESC LIMIT 1")
    suspend fun latestTrade(engine: String, symbol: String, side: String, sinceMs: Long): ManagedTradeEntity?
}
