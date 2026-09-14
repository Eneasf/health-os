package com.healthdashboard.companion.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.healthdashboard.companion.data.models.SyncPayloadEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

class PayloadExporter(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Computes the SHA-256 hex string for a given UTF-8 input string.
     */
    fun computeSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Writes the SyncPayloadEnvelope atomically to the exports directory.
     * Rule: Write to NAME.json.tmp first, then atomic-rename to NAME.json.
     */
    fun exportPayloadAtomically(envelope: SyncPayloadEnvelope): File {
        val exportDir = File(context.filesDir, "payload_inbox").apply { mkdirs() }
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            .replace(":", "")
            .replace("-", "")
        val baseName = "samsung_health_sync_${timestamp}"
        val tempFile = File(exportDir, "$baseName.json.tmp")
        val finalFile = File(exportDir, "$baseName.json")

        val jsonString = json.encodeToString(envelope)

        FileOutputStream(tempFile).use { output ->
            output.write(jsonString.toByteArray(Charsets.UTF_8))
            output.flush()
        }

        // Atomic rename
        if (!tempFile.renameTo(finalFile)) {
            // Fallback copy if renameTo fails across file systems
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        return finalFile
    }

    /**
     * Creates a share intent for exporting the JSON file via Android Share Sheet.
     */
    fun createShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Lists all generated JSON sync files in the inbox queue.
     */
    fun listPendingExports(): List<File> {
        val exportDir = File(context.filesDir, "payload_inbox")
        if (!exportDir.exists()) return emptyList()
        return exportDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Calculates the total size in bytes occupied by all pending JSON export files.
     */
    fun getTotalPayloadSizeBytes(): Long {
        val exportDir = File(context.filesDir, "payload_inbox")
        if (!exportDir.exists()) return 0L
        return exportDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sumOf { it.length() }
            ?: 0L
    }

    /**
     * Formats bytes into human-readable representation (e.g. 42.6 MB, 1.1 MB, 172 KB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "%.1f %s".format(value, units[digitGroups])
    }

    /**
     * Deletes all pending JSON payload files from local storage.
     * Returns the count of deleted files.
     */
    fun deleteAllPayloads(): Int {
        val exportDir = File(context.filesDir, "payload_inbox")
        if (!exportDir.exists()) return 0
        val files = exportDir.listFiles { file -> file.isFile && (file.extension == "json" || file.extension == "tmp") }
            ?: return 0
        var deletedCount = 0
        for (file in files) {
            if (file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Deletes older JSON payload files from local storage, keeping the [keepCount] most recent files.
     * Returns the count of deleted files.
     */
    fun deleteOldestPayloads(keepCount: Int = 3): Int {
        val files = listPendingExports()
        if (files.size <= keepCount) return 0
        val toDelete = files.drop(keepCount)
        var deletedCount = 0
        for (file in toDelete) {
            if (file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Deletes a single payload file.
     */
    fun deletePayload(file: File): Boolean {
        return if (file.exists() && file.isFile) file.delete() else false
    }
}
