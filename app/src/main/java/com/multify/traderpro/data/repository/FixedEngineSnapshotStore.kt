package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.local.ForecastChampionEntity
import com.multify.traderpro.data.local.ForecastResearchEntity
import com.multify.traderpro.data.local.LearningCallEntity
import com.multify.traderpro.data.local.LongForecastEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

internal data class FixedEngineSnapshotPayload(
    val snapshotId: String,
    val frozenAtMs: Long,
    val sourceVersion: String,
    val learningCalls: List<LearningCallEntity>,
    val longForecasts: List<LongForecastEntity>,
    val researchSnapshots: List<ForecastResearchEntity>,
    val champions: List<ForecastChampionEntity>
)

internal data class FixedEngineSnapshot(
    val payload: FixedEngineSnapshotPayload,
    val sha256: String
)

/**
 * Immutable v7.4.1 reference snapshot for the Fixed engine.
 * The file is created once on first launch after upgrade and is never rewritten automatically.
 */
@Singleton
class FixedEngineSnapshotStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val meta = context.getSharedPreferences("fixed_engine_snapshot_meta_v754_final", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    @Volatile private var cached: FixedEngineSnapshot? = null

    @Synchronized
    internal fun loadOrNull(): FixedEngineSnapshot? {
        cached?.let { return it }
        if (!file.exists()) return null
        val compressed = file.readBytes()
        val json = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        val payload = gson.fromJson(String(json, Charsets.UTF_8), FixedEngineSnapshotPayload::class.java)
        require(payload.snapshotId == SNAPSHOT_ID) {
            "Fixed engine snapshot id mismatch: ${payload.snapshotId}"
        }
        return FixedEngineSnapshot(payload, sha256(json)).also { cached = it }
    }

    @Synchronized
    internal fun captureIfAbsent(
        learningCalls: List<LearningCallEntity>,
        longForecasts: List<LongForecastEntity>,
        researchSnapshots: List<ForecastResearchEntity>,
        champions: List<ForecastChampionEntity>
    ): FixedEngineSnapshot {
        loadOrNull()?.let { return it }
        val payload = FixedEngineSnapshotPayload(
            snapshotId = SNAPSHOT_ID,
            frozenAtMs = System.currentTimeMillis(),
            sourceVersion = SOURCE_VERSION,
            learningCalls = learningCalls,
            longForecasts = longForecasts,
            researchSnapshots = researchSnapshots,
            champions = champions
        )
        val json = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        writeSnapshot(json)
        clearAwaitingRestore()
        return FixedEngineSnapshot(payload, sha256(json)).also { cached = it }
    }

    internal fun isAwaitingRestore(): Boolean = meta.getBoolean(KEY_AWAITING_RESTORE, false)

    internal fun markAwaitingRestore() {
        meta.edit().putBoolean(KEY_AWAITING_RESTORE, true).commit()
    }

    internal fun clearAwaitingRestore() {
        meta.edit().putBoolean(KEY_AWAITING_RESTORE, false).commit()
    }

    @Synchronized
    internal fun restoreExactIfAbsent(payload: FixedEngineSnapshotPayload): FixedEngineSnapshot {
        loadOrNull()?.let { return it }
        require(payload.snapshotId == SNAPSHOT_ID) { "Unsupported Fixed snapshot ${payload.snapshotId}" }
        val json = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        writeSnapshot(json)
        clearAwaitingRestore()
        return FixedEngineSnapshot(payload, sha256(json)).also { cached = it }
    }

    internal fun payloadJson(snapshot: FixedEngineSnapshot): String = gson.toJson(snapshot.payload)

    private fun writeSnapshot(json: ByteArray) {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(out.toByteArray())
        if (file.exists()) {
            tmp.delete()
            return
        }
        check(tmp.renameTo(file) || runCatching {
            tmp.copyTo(file, overwrite = false)
            tmp.delete()
            true
        }.getOrDefault(false)) { "Unable to persist immutable Fixed engine snapshot" }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val SNAPSHOT_ID = "FIXED-V741-20261021-190239-FINAL"
        const val SOURCE_VERSION = "7.4.1-forecast-gtt-autobuy-debug-20261021-190239-final"
        private const val FILE_NAME = "fixed_engine_snapshot_v741_20261021_190239_final.json.gz"
        private const val KEY_AWAITING_RESTORE = "awaiting_restore"
    }
}
