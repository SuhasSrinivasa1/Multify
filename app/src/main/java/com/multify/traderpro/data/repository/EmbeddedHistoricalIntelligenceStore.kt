package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.local.ResearchReportEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

internal data class EmbeddedHistoricalIntelligencePayload(
    val schemaVersion: Int,
    val sourceExports: List<String>,
    val latestExportAtMs: Long,
    val fixedSnapshot: FixedEngineSnapshotPayload,
    val reports: List<ResearchReportEntity>
)

/**
 * Read-only bundle assembled from every Multify diagnostic export available when v7.5.1 was built.
 *
 * The payload is split into binary asset parts only to keep repository/build tooling reliable.
 * At runtime the parts are concatenated, SHA-256 verified, gunzipped, then parsed.
 */
@Singleton
class EmbeddedHistoricalIntelligenceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var cached: EmbeddedHistoricalIntelligencePayload? = null

    @Synchronized
    internal fun load(): EmbeddedHistoricalIntelligencePayload {
        cached?.let { return it }

        val compressed = ByteArrayOutputStream()
        PARTS.forEach { name ->
            context.assets.open(name).use { it.copyTo(compressed) }
        }
        val bytes = compressed.toByteArray()
        require(sha256(bytes) == COMPRESSED_SHA256) {
            "Embedded Multify intelligence checksum mismatch"
        }

        val jsonBytes = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        require(sha256(jsonBytes) == JSON_SHA256) {
            "Embedded Multify intelligence JSON checksum mismatch"
        }

        val payload = GsonBuilder().disableHtmlEscaping().create()
            .fromJson(String(jsonBytes, Charsets.UTF_8), EmbeddedHistoricalIntelligencePayload::class.java)

        require(payload.schemaVersion == SCHEMA_VERSION) {
            "Unsupported embedded intelligence schema ${payload.schemaVersion}"
        }
        // This legacy embedded bundle remains the adaptive Forecast history seed.
        // The final Fixed engine derives its own immutable reference snapshot from the verified
        // learning rows and intentionally does not inherit adaptive Forecast outcomes/champions.
        require(payload.fixedSnapshot.snapshotId == LEGACY_EMBEDDED_SNAPSHOT_ID) {
            "Embedded adaptive-history snapshot id mismatch"
        }
        require(payload.fixedSnapshot.learningCalls.isNotEmpty()) {
            "Embedded Multify learning history is empty"
        }

        return payload.also { cached = it }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val SCHEMA_VERSION = 2
        const val LEGACY_EMBEDDED_SNAPSHOT_ID = "FIXED-V741-2026-10-21"
        const val COMPRESSED_SHA256 = "8cc0cd5accc08921fd710af4ecd4f58ab1b9b5b41bc686f8713e6a7ea1267738"
        const val JSON_SHA256 = "c5d23a7d14ef413c25f63d43672d8729c77469c589e1c4e36dd2f209ec7f304b"

        val PARTS = listOf("embedded_historic_intelligence_v2.json.gz")
    }
}
