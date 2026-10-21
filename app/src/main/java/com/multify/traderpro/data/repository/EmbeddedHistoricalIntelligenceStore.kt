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
        require(payload.fixedSnapshot.snapshotId == FixedEngineSnapshotStore.SNAPSHOT_ID) {
            "Embedded Fixed snapshot id mismatch"
        }
        require(payload.fixedSnapshot.learningCalls.isNotEmpty()) {
            "Embedded Multify learning history is empty"
        }

        return payload.also { cached = it }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val SCHEMA_VERSION = 1
        const val COMPRESSED_SHA256 = "654ebd723982ab0e9742b75711a1750d400f308c1e7f84a102831e5f05f2a714"
        const val JSON_SHA256 = "496e804ae84d74a56cf05135dab411dbeac0022513090bbd0fde7c506777ee36"

        val PARTS = listOf(
            "embedded_historic_intelligence_v1.part01",
            "embedded_historic_intelligence_v1.part02",
            "embedded_historic_intelligence_v1.part03",
            "embedded_historic_intelligence_v1.part04",
            "embedded_historic_intelligence_v1.part05",
            "embedded_historic_intelligence_v1.part06",
            "embedded_historic_intelligence_v1.part07",
            "embedded_historic_intelligence_v1.part08",
            "embedded_historic_intelligence_v1.part09"
        )
    }
}
