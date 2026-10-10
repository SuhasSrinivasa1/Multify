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
        ExecutionSampleEntity::class,
        LearningCallEntity::class,
        PriceObservationEntity::class,
        LongForecastEntity::class,
        ForecastChampionEntity::class,
        ResearchReportEntity::class
    ],
    version = 11,
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

                db.execSQL("DELETE FROM intraday_forecasts WHERE side!='LONG'")
                db.execSQL("DELETE FROM forecast_champions WHERE side!='LONG'")
                db.execSQL("DELETE FROM managed_trades WHERE side!='LONG'")
                db.execSQL("DELETE FROM price_observations WHERE phase='POST_SELL'")

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
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE long_forecasts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    forecastDate TEXT NOT NULL,
                    rank INTEGER NOT NULL,
                    symbol TEXT NOT NULL,
                    entryPrice REAL NOT NULL,
                    targetPct REAL NOT NULL,
                    targetPrice REAL NOT NULL,
                    confidence REAL NOT NULL,
                    score REAL NOT NULL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    marketRegime TEXT NOT NULL,
                    sectorRegime TEXT NOT NULL,
                    championKey TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    generatedAtMs INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    targetHitAtMs INTEGER,
                    lastPrice REAL NOT NULL,
                    maxFavourablePct REAL NOT NULL,
                    maxAdversePct REAL NOT NULL,
                    lastObservedAtMs INTEGER NOT NULL,
                    notified INTEGER NOT NULL,
                    multifyMatched INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO long_forecasts (
                    id,forecastDate,rank,symbol,entryPrice,targetPct,targetPrice,confidence,score,strategy,regime,
                    marketRegime,sectorRegime,championKey,reason,generatedAtMs,status,targetHitAtMs,lastPrice,
                    maxFavourablePct,maxAdversePct,lastObservedAtMs,notified,multifyMatched
                ) SELECT
                    id,forecastDate,rank,symbol,entryPrice,targetPct,targetPrice,confidence,score,strategy,regime,
                    marketRegime,sectorRegime,championKey,reason,generatedAtMs,status,targetHitAtMs,lastPrice,
                    maxFavourablePct,maxAdversePct,lastObservedAtMs,notified,multifyMatched
                FROM intraday_forecasts WHERE side='LONG'""")
                db.execSQL("DROP TABLE intraday_forecasts")
                db.execSQL("CREATE UNIQUE INDEX index_long_forecasts_forecastDate_rank ON long_forecasts(forecastDate,rank)")
                db.execSQL("CREATE UNIQUE INDEX index_long_forecasts_forecastDate_symbol ON long_forecasts(forecastDate,symbol)")
                db.execSQL("CREATE INDEX index_long_forecasts_status_forecastDate ON long_forecasts(status,forecastDate)")

                db.execSQL("""CREATE TABLE forecast_champions_new (
                    key TEXT PRIMARY KEY NOT NULL,
                    marketRegime TEXT NOT NULL,
                    sectorRegime TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    wins INTEGER NOT NULL,
                    losses INTEGER NOT NULL,
                    sampleCount INTEGER NOT NULL,
                    distinctDays INTEGER NOT NULL,
                    distinctSymbols INTEGER NOT NULL,
                    frozen INTEGER NOT NULL,
                    firstSeenAtMs INTEGER NOT NULL,
                    lastUpdatedAtMs INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT OR REPLACE INTO forecast_champions_new (
                    key,marketRegime,sectorRegime,regime,strategy,wins,losses,sampleCount,distinctDays,distinctSymbols,
                    frozen,firstSeenAtMs,lastUpdatedAtMs
                ) SELECT
                    key,marketRegime,sectorRegime,regime,strategy,wins,losses,sampleCount,distinctDays,distinctSymbols,
                    frozen,firstSeenAtMs,lastUpdatedAtMs
                FROM forecast_champions WHERE side='LONG'""")
                db.execSQL("DROP TABLE forecast_champions")
                db.execSQL("ALTER TABLE forecast_champions_new RENAME TO forecast_champions")
                db.execSQL("CREATE UNIQUE INDEX index_forecast_champions_marketRegime_regime_strategy ON forecast_champions(marketRegime,regime,strategy)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE managed_positions ADD COLUMN trailingArmPct REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE managed_positions ADD COLUMN trailingLastRatchetPrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """UPDATE managed_positions
                       SET trailingArmPct = CASE
                           WHEN targetPrice IS NOT NULL AND entryPrice > 0.0 AND targetPrice > entryPrice
                           THEN (targetPrice / entryPrice - 1.0) * 100.0
                           ELSE 0.0
                       END"""
                )
                db.execSQL(
                    """UPDATE managed_positions
                       SET trailingLastRatchetPrice = CASE
                           WHEN stopPrice IS NOT NULL AND stopPrice > entryPrice THEN maxFavourablePrice
                           ELSE 0.0
                       END"""
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE execution_samples (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    transactionType TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    product TEXT NOT NULL,
                    orderId TEXT NOT NULL,
                    submittedAtMs INTEGER NOT NULL,
                    filledAtMs INTEGER NOT NULL,
                    totalExecutionMs INTEGER NOT NULL,
                    appDispatchMicros INTEGER NOT NULL,
                    brokerAckMs INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX index_execution_samples_transactionType_filledAtMs ON execution_samples(transactionType, filledAtMs)")
                db.execSQL("CREATE INDEX index_execution_samples_filledAtMs ON execution_samples(filledAtMs)")
            }
        }


    }
}
