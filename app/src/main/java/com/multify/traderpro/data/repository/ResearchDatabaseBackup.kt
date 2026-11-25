package com.multify.traderpro.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/** Portable, research-only SQLite backup. It never touches the Room trading database. */
internal object ResearchDatabaseBackup {
    data class MergeResult(val insertedRows: Int, val mergedTables: Int, val skippedTables: Int)

    fun exportBytes(context: Context, helper: SQLiteOpenHelper, databaseName: String): ByteArray? {
        helper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { c ->
            while (c.moveToNext()) Unit
        }
        helper.close()
        val file = context.applicationContext.getDatabasePath(databaseName)
        try {
            val wal = File(file.absolutePath + "-wal")
            check(!wal.exists() || wal.length() == 0L) {
                "Research database WAL could not be checkpointed; backup was not exported"
            }
            return file.takeIf { it.isFile }?.readBytes()
                ?.also { check(it.size <= MAX_DATABASE_BYTES) { "Research database exceeds backup size limit" } }
        } finally {
            helper.writableDatabase
        }
    }

    /** Merge missing primary-key rows. Existing device rows always win; no current research is deleted. */
    fun mergeBytes(
        context: Context,
        helper: SQLiteOpenHelper,
        databaseName: String,
        bytes: ByteArray
    ): MergeResult {
        require(bytes.isNotEmpty() && bytes.size <= MAX_DATABASE_BYTES) { "Invalid $databaseName backup size" }
        val temp = File.createTempFile("phayra-research-restore-", ".db", context.applicationContext.cacheDir)
        try {
            temp.writeBytes(bytes)
            val sourceVersion = SQLiteDatabase.openDatabase(
                temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).useDatabase { source ->
                val integrity = source.rawQuery("PRAGMA quick_check", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else "NO_RESULT"
                }
                require(integrity.equals("ok", true)) { "Research backup failed SQLite integrity check" }
                source.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }

            val target = helper.writableDatabase
            val targetVersion = target.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            require(sourceVersion == targetVersion) {
                "Research database schema mismatch for $databaseName (backup $sourceVersion, app $targetVersion)"
            }

            target.execSQL("ATTACH DATABASE ? AS research_restore", arrayOf(temp.absolutePath))
            var inTransaction = false
            var inserted = 0
            var merged = 0
            var skipped = 0
            try {
                target.beginTransaction()
                inTransaction = true
                val tables = target.rawQuery(
                    "SELECT name FROM research_restore.sqlite_master " +
                        "WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null
                ).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
                for (table in tables) {
                    val exists = target.rawQuery(
                        "SELECT 1 FROM main.sqlite_master WHERE type='table' AND name=? LIMIT 1",
                        arrayOf(table)
                    ).use { it.moveToFirst() }
                    if (!exists) { skipped++; continue }
                    val sourceColumns = columns(target, "research_restore", table)
                    val targetColumns = columns(target, "main", table)
                    if (sourceColumns.isEmpty() || sourceColumns != targetColumns) {
                        skipped++
                        continue
                    }
                    val quoted = sourceColumns.joinToString(",") { quote(it) }
                    target.execSQL(
                        "INSERT OR IGNORE INTO main." + quote(table) + " ($quoted) " +
                            "SELECT $quoted FROM research_restore." + quote(table)
                    )
                    val changed = target.rawQuery("SELECT changes()", null).use {
                        if (it.moveToFirst()) it.getInt(0) else 0
                    }
                    inserted += changed
                    merged++
                }
                target.setTransactionSuccessful()
            } finally {
                if (inTransaction) target.endTransaction()
                runCatching { target.execSQL("DETACH DATABASE research_restore") }
            }
            return MergeResult(inserted, merged, skipped)
        } finally {
            temp.delete()
        }
    }

    private fun columns(db: SQLiteDatabase, schema: String, table: String): List<String> {
        val safeSchema = if (schema == "main" || schema == "research_restore") schema else error("Invalid schema")
        val escaped = table.replace("'", "''")
        return db.rawQuery("PRAGMA $safeSchema.table_info('$escaped')", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1)) }
        }
    }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private inline fun <T> SQLiteDatabase.useDatabase(block: (SQLiteDatabase) -> T): T =
        try { block(this) } finally { close() }

    private const val MAX_DATABASE_BYTES = 128 * 1024 * 1024
}
