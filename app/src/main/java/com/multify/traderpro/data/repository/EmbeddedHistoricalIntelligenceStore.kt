package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.local.ResearchReportEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
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
 * Android's asset packaging can inflate a .gz asset and expose it under the filename without
 * the .gz suffix. Runtime therefore accepts either the verified raw JSON asset or the original
 * verified gzip asset, then parses the exact same canonical payload.
 */
@Singleton
class EmbeddedHistoricalIntelligenceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var cached: EmbeddedHistoricalIntelligencePayload? = null

    @Synchronized
    internal fun load(): EmbeddedHistoricalIntelligencePayload {
        cached?.let { return it }

        var verifiedJson: ByteArray? = null
        var assetFound = false
        for (name in ASSET_CANDIDATES) {
            val bytes = runCatching {
                context.assets.open(name).use { it.readBytes() }
            }.getOrNull() ?: continue
            assetFound = true

            when (sha256(bytes)) {
                JSON_SHA256 -> {
                    // Android packaging inflated the source .json.gz and stripped its suffix.
                    verifiedJson = bytes
                    break
                }
                COMPRESSED_SHA256 -> {
                    val inflated = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                    require(sha256(inflated) == JSON_SHA256) {
                        "Embedded Multify intelligence JSON checksum mismatch"
                    }
                    verifiedJson = inflated
                    break
                }
            }
        }
        require(assetFound) {
            "Embedded Multify intelligence asset missing (checked " + ASSET_CANDIDATES.joinToString() + ")"
        }
        val jsonBytes = requireNotNull(verifiedJson) {
            "Embedded Multify intelligence checksum mismatch"
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

        val ASSET_CANDIDATES = listOf(
            "embedded_historic_intelligence_v2.json",
            "embedded_historic_intelligence_v2.json.gz"
        )
    }
}
