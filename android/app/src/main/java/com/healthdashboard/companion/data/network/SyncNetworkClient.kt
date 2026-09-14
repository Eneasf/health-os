package com.healthdashboard.companion.data.network

import android.content.Context
import com.healthdashboard.companion.data.models.ActiveProtocol
import com.healthdashboard.companion.data.models.ActiveProtocolResponse
import com.healthdashboard.companion.data.models.DoseLogPayload
import com.healthdashboard.companion.data.models.DoseLogResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ScanUploadPayload(
    val filename: String,
    val image_base64: String,
    val note: String? = null,
    val client_timestamp: String
)

@Serializable
data class IngestSummaryMetrics(
    val status: String = "success",
    val processed_payloads: Int = 0,
    val device_id: String? = null,
    val collected_at: String? = null,
    val records_received: Int = 0,
    val replays_rejected: Int = 0,
    val breakdown: Map<String, Int> = emptyMap()
)

@Serializable
data class ServerSyncResponse(
    val status: String = "success",
    val server_version: String? = null,
    val message: String = "",
    val record_count: Int = 0,
    val device_id: String? = null,
    val processed_at: String? = null,
    val ingest_summary: IngestSummaryMetrics? = null
)

@Serializable
data class FreshnessSensorInfo(
    val data_current_through: String? = null,
    val unit: String? = null
)

@Serializable
data class FreshnessSyncInfo(
    val last_companion_sync: String? = null,
    val companion_device: String? = null,
    val last_withings_sync: String? = null,
    val last_server_contact: String? = null,
    val contact_age_hours: Double? = null,
    val pipeline_status: String = "idle" // "active", "delayed", "stalled", "unconnected"
)

@Serializable
data class FreshnessPayload(
    val sensor_freshness: FreshnessSensorInfo? = null,
    val sync_freshness: FreshnessSyncInfo? = null
)

@Serializable
data class ServerStatusPayload(
    val status: String = "online",
    val server_version: String? = null,
    val authority: String? = null,
    val telemetry_freshness: FreshnessPayload? = null
)

sealed interface ConnectionTestResult {
    data class Success(val latencyMs: Long, val serverVersion: String, val message: String) : ConnectionTestResult
    data class Failed(val error: String) : ConnectionTestResult
}

sealed interface PushResult {
    data class Success(
        val message: String,
        val response: ServerSyncResponse? = null,
        val endpointUsed: String? = null
    ) : PushResult
    data class Skipped(val reason: String) : PushResult
    data class Failed(val error: String) : PushResult
}

class SyncNetworkClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun normalizeEndpointUrl(hostOrIp: String, port: Int = 8765, path: String = "/api/sync"): String {
        val clean = hostOrIp.trim()
        if (clean.isBlank()) return ""
        val withoutProto = clean.removePrefix("http://").removePrefix("https://")
        val hostPart = withoutProto.split("/")[0]
        val hostOnly = hostPart.split(":")[0]
        val portToUse = if (hostPart.contains(":")) {
            hostPart.split(":")[1].toIntOrNull() ?: port
        } else {
            port
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "http://$hostOnly:$portToUse$cleanPath"
    }

    suspend fun testEndpoint(hostOrIp: String, port: Int = 8765): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (hostOrIp.isBlank()) {
            return@withContext ConnectionTestResult.Failed("Host or IP is empty")
        }
        val targetUrl = normalizeEndpointUrl(hostOrIp, port, "/api/status")
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                val code = connection.responseCode
                val latency = System.currentTimeMillis() - startTime
                if (code in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val version = try {
                        val parsed = json.decodeFromString<ServerSyncResponse>(text)
                        parsed.server_version ?: "1.2.0"
                    } catch (_: Exception) {
                        "1.2.0"
                    }
                    ConnectionTestResult.Success(latency, version, "Online (${latency}ms)")
                } else {
                    ConnectionTestResult.Failed("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            ConnectionTestResult.Failed(e.message ?: "Connection timeout / unreachable")
        }
    }

    suspend fun pushWithFallback(
        primaryHost: String,
        fallbackHost: String?,
        port: Int = 8088,
        payloadJson: String,
        primaryPort: Int = port,
        fallbackPort: Int = if (port == 8088) 8765 else port
    ): PushResult = withContext(Dispatchers.IO) {
        if (primaryHost.isBlank() && fallbackHost.isNullOrBlank()) {
            return@withContext PushResult.Skipped("No Mac/NAS sync host configured")
        }

        var primaryError: String? = null
        if (primaryHost.isNotBlank()) {
            val primaryUrl = normalizeEndpointUrl(primaryHost, primaryPort, "/api/sync")
            when (val res = pushPayloadToMac(primaryUrl, payloadJson)) {
                is PushResult.Success -> return@withContext res.copy(endpointUsed = primaryHost)
                is PushResult.Failed -> primaryError = res.error
                is PushResult.Skipped -> {}
            }
        }

        if (!fallbackHost.isNullOrBlank()) {
            val fallbackUrl = normalizeEndpointUrl(fallbackHost, fallbackPort, "/api/sync")
            when (val res = pushPayloadToMac(fallbackUrl, payloadJson)) {
                is PushResult.Success -> return@withContext PushResult.Success(
                    message = "Synced via Fallback ($fallbackHost) ✅",
                    response = res.response,
                    endpointUsed = fallbackHost
                )
                is PushResult.Failed -> {
                    val combinedErr = if (primaryError != null) "Primary ($primaryError); Fallback (${res.error})" else res.error
                    return@withContext PushResult.Failed(combinedErr)
                }
                is PushResult.Skipped -> {}
            }
        }

        PushResult.Failed(primaryError ?: "Sync failed (No reachable host)")
    }

    suspend fun checkServerFreshness(hostOrIp: String, port: Int = 8088): ServerStatusPayload? = withContext(Dispatchers.IO) {
        if (hostOrIp.isBlank()) return@withContext null
        val targetUrl = normalizeEndpointUrl(hostOrIp, port, "/api/status")
        try {
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                if (connection.responseCode in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    json.decodeFromString<ServerStatusPayload>(text)
                } else null
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun triggerMacDevSync(macHost: String, macPort: Int = 8765): Result<String> = withContext(Dispatchers.IO) {
        if (macHost.isBlank()) return@withContext Result.failure(IllegalArgumentException("Mac host is blank"))
        val targetUrl = normalizeEndpointUrl(macHost, macPort, "/api/dev/sync_from_nas")
        try {
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 4000
                connection.readTimeout = 8000
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write("{}") }
                val code = connection.responseCode
                if (code in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    Result.success(text)
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Result.failure(Exception("Mac returned $code: $err"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadScan(
        hostOrIp: String,
        port: Int,
        imageBase64: String,
        filename: String,
        note: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (hostOrIp.isBlank()) return@withContext Result.failure(IllegalArgumentException("Host is blank"))
        val targetUrl = normalizeEndpointUrl(hostOrIp, port, "/api/upload_scan")
        try {
            val payload = json.encodeToString(
                ScanUploadPayload(
                    filename = filename,
                    image_base64 = imageBase64,
                    note = note,
                    client_timestamp = java.time.Instant.now().toString()
                )
            )
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 25000
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(payload) }
                val code = connection.responseCode
                if (code in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    Result.success(text)
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Result.failure(Exception("Upload returned $code: $err"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadScanWithFallback(
        primaryHost: String,
        primaryPort: Int = 8088,
        fallbackHost: String? = null,
        fallbackPort: Int = 8765,
        imageBase64: String,
        filename: String,
        note: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        var primaryError: String? = null

        if (primaryHost.isNotBlank()) {
            val primaryResult = uploadScan(
                hostOrIp = primaryHost,
                port = primaryPort,
                imageBase64 = imageBase64,
                filename = filename,
                note = note
            )
            if (primaryResult.isSuccess) {
                return@withContext primaryResult
            }
            primaryError = primaryResult.exceptionOrNull()?.message ?: "Primary upload failed"
        }

        if (!fallbackHost.isNullOrBlank()) {
            val fallbackResult = uploadScan(
                hostOrIp = fallbackHost,
                port = fallbackPort,
                imageBase64 = imageBase64,
                filename = filename,
                note = note
            )
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }
            val fallbackError = fallbackResult.exceptionOrNull()?.message ?: "Fallback upload failed"
            val combinedError = if (primaryError != null) {
                "Primary ($primaryHost:$primaryPort): $primaryError | Fallback ($fallbackHost:$fallbackPort): $fallbackError"
            } else {
                "Fallback ($fallbackHost:$fallbackPort): $fallbackError"
            }
            return@withContext Result.failure(Exception(combinedError))
        }

        Result.failure(Exception(primaryError ?: "No reachable upload endpoint configured"))
    }


    suspend fun pushPayloadToMac(serverUrl: String, payloadJson: String): PushResult = withContext(Dispatchers.IO) {
        if (serverUrl.isBlank()) {
            return@withContext PushResult.Skipped("No Mac server URL configured")
        }

        val targetUrl = if (serverUrl.endsWith("/api/sync")) serverUrl else "$serverUrl/api/sync"

        try {
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(payloadJson)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = try {
                        json.decodeFromString<ServerSyncResponse>(responseText)
                    } catch (_: Exception) {
                        ServerSyncResponse(
                            status = "success",
                            server_version = "1.2.0",
                            message = "Synced to Mac ($responseCode) cleanly",
                            record_count = 0
                        )
                    }
                    PushResult.Success("Synced to Mac ($responseCode)", parsed, serverUrl)
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    PushResult.Failed("Server returned $responseCode: $errorText")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            PushResult.Failed(e.message ?: "Connection failed (Mac server offline)")
        }
    }

    suspend fun getActiveProtocol(hostOrIp: String, port: Int = 8765): Result<ActiveProtocol?> = withContext(Dispatchers.IO) {
        if (hostOrIp.isBlank()) return@withContext Result.failure(IllegalArgumentException("Host is blank"))
        val targetUrl = normalizeEndpointUrl(hostOrIp, port, "/api/protocol/active")
        try {
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                val code = connection.responseCode
                if (code in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val resp = json.decodeFromString<ActiveProtocolResponse>(text)
                    Result.success(resp.activeProtocol)
                } else {
                    Result.failure(Exception("HTTP $code fetching active protocol"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveProtocolWithFallback(
        primaryHost: String,
        primaryPort: Int = 8088,
        fallbackHost: String? = null,
        fallbackPort: Int = 8765
    ): Result<ActiveProtocol?> = withContext(Dispatchers.IO) {
        if (primaryHost.isNotBlank()) {
            val primaryRes = getActiveProtocol(primaryHost, primaryPort)
            if (primaryRes.isSuccess) return@withContext primaryRes
        }
        if (!fallbackHost.isNullOrBlank()) {
            val fallbackRes = getActiveProtocol(fallbackHost, fallbackPort)
            if (fallbackRes.isSuccess) return@withContext fallbackRes
        }
        Result.failure(Exception("Active protocol fetch failed on all endpoints"))
    }

    suspend fun logDose(
        hostOrIp: String,
        port: Int = 8765,
        payload: DoseLogPayload
    ): Result<DoseLogResponse> = withContext(Dispatchers.IO) {
        if (hostOrIp.isBlank()) return@withContext Result.failure(IllegalArgumentException("Host is blank"))
        val targetUrl = normalizeEndpointUrl(hostOrIp, port, "/api/log_dose")
        try {
            val payloadJson = json.encodeToString(payload)
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 6000
                connection.readTimeout = 12000
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(payloadJson) }
                val code = connection.responseCode
                if (code in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val resp = try {
                        json.decodeFromString<DoseLogResponse>(text)
                    } catch (_: Exception) {
                        DoseLogResponse(status = "success", message = text)
                    }
                    Result.success(resp)
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Result.failure(Exception("Log dose returned $code: $err"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logDoseWithFallback(
        primaryHost: String,
        primaryPort: Int = 8088,
        fallbackHost: String? = null,
        fallbackPort: Int = 8765,
        payload: DoseLogPayload
    ): Result<DoseLogResponse> = withContext(Dispatchers.IO) {
        var primaryErr: String? = null
        if (primaryHost.isNotBlank()) {
            val primaryRes = logDose(primaryHost, primaryPort, payload)
            if (primaryRes.isSuccess) return@withContext primaryRes
            primaryErr = primaryRes.exceptionOrNull()?.message ?: "Primary endpoint failed"
        }
        if (!fallbackHost.isNullOrBlank()) {
            val fallbackRes = logDose(fallbackHost, fallbackPort, payload)
            if (fallbackRes.isSuccess) return@withContext fallbackRes
            val fallbackErr = fallbackRes.exceptionOrNull()?.message ?: "Fallback endpoint failed"
            return@withContext Result.failure(Exception("Primary: $primaryErr; Fallback: $fallbackErr"))
        }
        Result.failure(Exception(primaryErr ?: "No endpoint available to log dose"))
    }
}
