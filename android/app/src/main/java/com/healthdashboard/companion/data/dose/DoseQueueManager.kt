package com.healthdashboard.companion.data.dose

import android.content.Context
import android.util.Log
import com.healthdashboard.companion.data.models.DoseLogPayload
import com.healthdashboard.companion.data.network.SyncNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

data class FlushResult(
    val attempted: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null
)

class DoseQueueManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val queueDir: File
        get() = File(context.filesDir, "pending_doses").apply { mkdirs() }

    /**
     * Atomically stores an un-synced dose log payload in the offline queue directory.
     * Uses write-to-tmp then atomic rename to prevent half-written payloads.
     */
    fun queueDose(payload: DoseLogPayload): File {
        val dir = queueDir
        val safeCompound = payload.compoundId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val timestamp = System.currentTimeMillis()
        val baseName = "dose_${timestamp}_${safeCompound}"
        val tmpFile = File(dir, "$baseName.json.tmp")
        val finalFile = File(dir, "$baseName.json")

        val jsonStr = json.encodeToString(payload)
        FileOutputStream(tmpFile).use { fos ->
            fos.write(jsonStr.toByteArray(Charsets.UTF_8))
            fos.flush()
        }

        if (!tmpFile.renameTo(finalFile)) {
            tmpFile.copyTo(finalFile, overwrite = true)
            tmpFile.delete()
        }
        Log.i("DoseQueueManager", "Queued dose offline: ${finalFile.name}")
        return finalFile
    }

    /**
     * Lists all queued dose items paired with their backing file.
     */
    fun listPendingDoses(): List<Pair<File, DoseLogPayload>> {
        val dir = queueDir
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: return emptyList()

        return files.sortedBy { it.lastModified() }.mapNotNull { f ->
            try {
                val text = f.readText(Charsets.UTF_8)
                val parsed = json.decodeFromString<DoseLogPayload>(text)
                Pair(f, parsed)
            } catch (e: Exception) {
                Log.w("DoseQueueManager", "Failed to decode pending dose file ${f.name}: ${e.message}")
                null
            }
        }
    }

    /**
     * Returns count of queued dose files currently pending sync.
     */
    fun getPendingCount(): Int {
        val dir = queueDir
        if (!dir.exists()) return 0
        return dir.listFiles { f -> f.isFile && f.extension == "json" }?.size ?: 0
    }

    /**
     * Deletes a specific pending dose file upon confirmed server ingestion.
     */
    fun removeDose(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.w("DoseQueueManager", "Failed to delete synced dose file ${file.name}: ${e.message}")
            false
        }
    }

    /**
     * Clears all pending dose payloads (e.g. user manual purge).
     */
    fun clearAllPending(): Int {
        val dir = queueDir
        if (!dir.exists()) return 0
        val files = dir.listFiles { f -> f.isFile && (f.extension == "json" || f.extension == "tmp") } ?: return 0
        var count = 0
        for (f in files) {
            if (f.delete()) count++
        }
        return count
    }

    /**
     * Iterates over queued offline doses and flushes them to the server via fallback endpoints.
     */
    suspend fun flushQueue(
        networkClient: SyncNetworkClient,
        primaryHost: String,
        primaryPort: Int,
        fallbackHost: String?,
        fallbackPort: Int
    ): FlushResult = withContext(Dispatchers.IO) {
        val pending = listPendingDoses()
        if (pending.isEmpty()) {
            return@withContext FlushResult()
        }

        var succeeded = 0
        var failed = 0
        var lastError: String? = null

        for ((file, payload) in pending) {
            val result = networkClient.logDoseWithFallback(
                primaryHost = primaryHost,
                primaryPort = primaryPort,
                fallbackHost = fallbackHost,
                fallbackPort = fallbackPort,
                payload = payload
            )

            if (result.isSuccess) {
                removeDose(file)
                succeeded++
            } else {
                failed++
                lastError = result.exceptionOrNull()?.message ?: "Unknown upload failure"
                // If the server is unreachable, stop churning to avoid prolonged blocking
                break
            }
        }

        FlushResult(
            attempted = succeeded + failed,
            succeeded = succeeded,
            failed = failed,
            lastError = lastError
        )
    }
}
