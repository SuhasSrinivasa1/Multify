package com.multify.traderpro.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticZipIntegrityTest {
    @Test
    fun verifiesClosedArchiveAndEveryEntry() {
        val file = createZip(mapOf(
            "signals.json" to """{"rows":3}""",
            "export_manifest.json" to """{"status":"VERIFIED_COMPLETE_STAGED_ZIP"}"""
        ))
        try {
            val result = DiagnosticZipIntegrity.verify(file, expectedEntries = 2)
            assertEquals(2, result.entries)
            assertTrue(result.uncompressedBytes > 0L)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsZeroByteDestination() {
        val file = File.createTempFile("phayra-empty-", ".zip")
        try {
            expectFailure { DiagnosticZipIntegrity.verify(file) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsArchiveWithoutManifest() {
        val file = createZip(mapOf("signals.json" to "[]"))
        try {
            expectFailure { DiagnosticZipIntegrity.verify(file) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsCorruptZipBytes() {
        val file = File.createTempFile("phayra-corrupt-", ".zip")
        try {
            file.writeText("not-a-zip")
            expectFailure { DiagnosticZipIntegrity.verify(file) }
        } finally {
            file.delete()
        }
    }

    private fun createZip(entries: Map<String, String>): File {
        val file = File.createTempFile("phayra-valid-", ".zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.finish()
        }
        return file
    }

    private fun expectFailure(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Expected invalid archive to be rejected", failed)
    }
}
