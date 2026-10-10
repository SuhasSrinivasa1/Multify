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
        LongForecastEntity::class,
        LongChampionEntity::class,
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
                db.execSQL("""CREATE TABLE managed_positions_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    engine TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    product TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    entryPrice REAL NOT NULL,
                    stopPrice REAL,
                    targetPrice REAL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    sourceEventId INTEGER,
                    openOrderId TEXT NOT NULL,
                    openReferenceId TEXT NOT NULL,
                    openedAtMs INTEGER NOT NULL,
                    lastPrice REAL NOT NULL,
                    maxFavourablePrice REAL NOT NULL,
                    maxAdversePrice REAL NOT NULL,
                    lastEvaluatedAtMs INTEGER NOT NULL,
                    status TEXT NOT NULL
                )""")
                db.execSQL("""INSERT INTO managed_positions_new (
                    id,engine,symbol,product,quantity,entryPrice,stopPrice,targetPrice,strategy,regime,confidence,
                    sourceEventId,openOrderId,openReferenceId,openedAtMs,lastPrice,maxFavourablePrice,maxAdversePrice,
                    lastEvaluatedAtMs,status
                ) SELECT
                    id,CASE WHEN side='LONG' THEN engine ELSE 'LEGACY_NON_LONG' END,symbol,product,quantity,entryPrice,
                    stopPrice,targetPrice,strategy,regime,confidence,sourceEventId,openOrderId,openReferenceId,openedAtMs,
                    lastPrice,maxFavourablePrice,maxAdversePrice,lastEvaluatedAtMs,status
                FROM managed_positions""")
                db.execSQL("DROP TABLE managed_positions")
                db.execSQL("ALTER TABLE managed_positions_new RENAME TO managed_positions")
                db.execSQL("CREATE INDEX index_managed_positions_engine_symbol_status ON managed_positions(engine,symbol,status)")
                db.execSQL("CREATE INDEX index_managed_positions_product_symbol_status ON managed_positions(product,symbol,status)")
                db.execSQL("CREATE INDEX index_managed_positions_openedAtMs ON managed_positions(openedAtMs)")

                db.execSQL("""CREATE TABLE managed_trades_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    engine TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    product TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    entryPrice REAL NOT NULL,
                    exitPrice REAL NOT NULL,
                    grossPnl REAL NOT NULL,
                    estimatedCosts REAL NOT NULL,
                    netPnl REAL NOT NULL,
                    exitReason TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    regime TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    mfeRupees REAL NOT NULL,
                    maeRupees REAL NOT NULL,
                    sourceEventId INTEGER,
                    openOrderId TEXT NOT NULL,
                    closeOrderId TEXT NOT NULL,
                    openedAtMs INTEGER NOT NULL,
                    closedAtMs INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO managed_trades_new (
                    id,engine,symbol,product,quantity,entryPrice,exitPrice,grossPnl,estimatedCosts,netPnl,exitReason,
                    strategy,regime,confidence,mfeRupees,maeRupees,sourceEventId,openOrderId,closeOrderId,openedAtMs,closedAtMs
                ) SELECT
                    id,engine,symbol,product,quantity,entryPrice,exitPrice,grossPnl,estimatedCosts,netPnl,exitReason,
                    strategy,regime,confidence,mfeRupees,maeRupees,sourceEventId,openOrderId,closeOrderId,openedAtMs,closedAtMs
                FROM managed_trades WHERE side='LONG'""")
                db.execSQL("DROP TABLE managed_trades")
                db.execSQL("ALTER TABLE managed_trades_new RENAME TO managed_trades")
                db.execSQL("CREATE INDEX index_managed_trades_engine_closedAtMs ON managed_trades(engine,closedAtMs)")
                db.execSQL("CREATE INDEX index_managed_trades_symbol_closedAtMs ON managed_trades(symbol,closedAtMs)")
                db.execSQL("CREATE INDEX index_managed_trades_closedAtMs ON managed_trades(closedAtMs)")

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
                db.execSQL("CREATE UNIQUE INDEX index_long_forecasts_forecastDate_rank ON long_forecasts(forecastDate,rank)")
                db.execSQL("CREATE UNIQUE INDEX index_long_forecasts_forecastDate_symbol ON long_forecasts(forecastDate,symbol)")
                db.execSQL("CREATE INDEX index_long_forecasts_status_forecastDate ON long_forecasts(status,forecastDate)")

                db.execSQL("""CREATE TABLE long_champions (
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
                db.execSQL("""INSERT OR REPLACE INTO long_champions (
                    key,marketRegime,sectorRegime,regime,strategy,wins,losses,sampleCount,distinctDays,distinctSymbols,
                    frozen,firstSeenAtMs,lastUpdatedAtMs
                ) SELECT
                    key,marketRegime,sectorRegime,regime,strategy,wins,losses,sampleCount,distinctDays,distinctSymbols,
                    frozen,firstSeenAtMs,lastUpdatedAtMs
                FROM forecast_champions WHERE side='LONG'""")
                db.execSQL("CREATE UNIQUE INDEX index_long_champions_marketRegime_regime_strategy ON long_champions(marketRegime,regime,strategy)")

                db.execSQL("DROP TABLE IF EXISTS intraday_forecasts")
                db.execSQL("DROP TABLE IF EXISTS forecast_champions")
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
