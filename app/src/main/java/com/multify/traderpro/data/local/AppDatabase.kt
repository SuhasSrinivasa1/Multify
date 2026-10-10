package com.multify.traderpro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SignalEventEntity::class,
        ManagedPositionEntity::class,
        ManagedTradeEntity::class,
        LearningCallEntity::class,
        PriceObservationEntity::class,
        IntradayForecastEntity::class,
        ForecastChampionEntity::class,
        ResearchReportEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalEventDao(): SignalEventDao
    abstract fun managedTradeDao(): ManagedTradeDao
    abstract fun learningDao(): LearningDao

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE learning_calls_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    callDate TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    source TEXT NOT NULL,
                    action TEXT NOT NULL,
                    entryPrice REAL NOT NULL,
                    targetPrice REAL,
                    targetPct REAL,
                    multifyExitPrice REAL,
                    longRealizedPct REAL,
                    buyEventId INTEGER,
                    sellEventId INTEGER,
                    buyAtMs INTEGER NOT NULL,
                    sellAtMs INTEGER,
                    updatedAtMs INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO learning_calls_new (
                    id,callDate,symbol,source,action,entryPrice,targetPrice,targetPct,multifyExitPrice,longRealizedPct,
                    buyEventId,sellEventId,buyAtMs,sellAtMs,updatedAtMs
                ) SELECT
                    id,callDate,symbol,source,action,entryPrice,targetPrice,targetPct,multifyExitPrice,longRealizedPct,
                    buyEventId,sellEventId,buyAtMs,sellAtMs,updatedAtMs
                FROM learning_calls""")
                db.execSQL("DROP TABLE learning_calls")
                db.execSQL("ALTER TABLE learning_calls_new RENAME TO learning_calls")
                db.execSQL("CREATE INDEX index_learning_calls_callDate ON learning_calls(callDate)")
                db.execSQL("CREATE INDEX index_learning_calls_symbol_callDate ON learning_calls(symbol,callDate)")
                db.execSQL("CREATE INDEX index_learning_calls_source ON learning_calls(source)")

                db.execSQL("DROP TABLE IF EXISTS forecasts")
                db.execSQL("DROP TABLE IF EXISTS strategy_snapshots")
                db.execSQL("DROP TABLE IF EXISTS wave_outcomes")
                db.execSQL("DROP TABLE IF EXISTS wave_decisions")
                db.execSQL("DROP TABLE IF EXISTS wave_observations")
                db.execSQL("DROP TABLE IF EXISTS wave_campaigns")
                db.execSQL("DROP TABLE IF EXISTS shadow_trades")
                db.execSQL("DROP TABLE IF EXISTS shadow_positions")
            }
        }
    }
}
