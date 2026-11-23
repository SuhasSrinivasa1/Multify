package com.multify.traderpro.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local, separate research DB. Does NOT touch the trading Room schema.
 * One validated NSE PE report and a separately sourced industry subset are retained.
 */
internal class EquityLowValuationStore(context: Context) {
    private class Helper(context: Context) :
        SQLiteOpenHelper(context, "equity_pe_research_v1.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE pe(symbol TEXT PRIMARY KEY, value REAL NOT NULL)")
            db.execSQL("CREATE TABLE industry(symbol TEXT PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            error("PE research DB migration must be explicit")
    }

    data class Snapshot(
        val peBySymbol: Map<String, Double>,
        val industryBySymbol: Map<String, String>,
        val peDate: String,
        val industrySource: String
    )

    private val helper = Helper(context)
    private val lock = Any()

    private fun setMeta(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("meta", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun savePe(day: String, peBySymbol: Map<String, Double>) = synchronized(lock) {
        require(day.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(peBySymbol.size >= 600)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("pe", null, null)
            for ((symbol, pe) in peBySymbol) {
                db.insertOrThrow("pe", null, ContentValues().apply {
                    put("symbol", symbol); put("value", pe)
                })
            }
            setMeta(db, "pe_day", day)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveIndustries(source: String, values: Map<String, String>) = synchronized(lock) {
        require(values.size >= 400)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("industry", null, null)
            for ((symbol, industry) in values) {
                db.insertOrThrow("industry", null, ContentValues().apply {
                    put("symbol", symbol); put("name", industry)
                })
            }
            setMeta(db, "industry_source", source)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun readMeta(db: SQLiteDatabase, key: String): String =
        db.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }

    fun peDate(): String = synchronized(lock) { readMeta(helper.readableDatabase, "pe_day") }

    fun snapshot(): Snapshot = synchronized(lock) {
        val db = helper.readableDatabase
        val pe = linkedMapOf<String, Double>()
        db.rawQuery("SELECT symbol,value FROM pe", null).use { c ->
            while (c.moveToNext()) pe[c.getString(0)] = c.getDouble(1)
        }
        val sectors = linkedMapOf<String, String>()
        db.rawQuery("SELECT symbol,name FROM industry", null).use { c ->
            while (c.moveToNext()) sectors[c.getString(0)] = c.getString(1)
        }
        Snapshot(pe, sectors, readMeta(db,"pe_day"), readMeta(db,"industry_source"))
    }
}
