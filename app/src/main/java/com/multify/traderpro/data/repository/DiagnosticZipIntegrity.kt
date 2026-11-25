package com.multify.traderpro.data.repository

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Validates the closed ZIP central directory, every entry stream, size and CRC before export. */
internal object DiagnosticZipIntegrity {
    data class Summary(val entries: Int, val uncompressedBytes: Long)

    fun verify(
        file: File,
        expectedEntries: Int? = null,
        requiredEntry: String = "export_manifest.json"
    ): Summary {
        require(file.isFile && file.length() > 22L) { "Diagnostic ZIP is missing or empty" }
        ZipFile(file).use { zip ->
            val names = linkedSetOf<String>()
            var count = 0
            var totalBytes = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(names.add(entry.name)) { "Duplicate ZIP entry: ${entry.name}" }
                count++
                if (entry.isDirectory) continue
                val crc = CRC32()
                var bytes = 0L
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        crc.update(buffer, 0, read)
                        bytes += read
                    }
                }
                if (entry.size >= 0L) require(bytes == entry.size) {
                    "ZIP entry size mismatch: ${entry.name}"
                }
                if (entry.crc >= 0L) require(crc.value == entry.crc) {
                    "ZIP entry checksum mismatch: ${entry.name}"
                }
                totalBytes += bytes
            }
            require(count > 0) { "Diagnostic ZIP contains no entries" }
            require(requiredEntry in names) { "Diagnostic ZIP is missing $requiredEntry" }
            if (expectedEntries != null) require(count == expectedEntries) {
                "Diagnostic ZIP entry count mismatch: $count/$expectedEntries"
            }
            return Summary(count, totalBytes)
        }
    }
}
