package com.healthdashboard.companion.collector

import android.content.Context
import android.os.Build
import com.healthdashboard.companion.data.export.PayloadExporter
import com.healthdashboard.companion.data.mappers.RecordMappers
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.SyncPayloadEnvelope
import com.healthdashboard.companion.data.models.TimeWindow
import com.healthdashboard.companion.data.network.PushResult
import com.healthdashboard.companion.data.network.SyncNetworkClient
import com.healthdashboard.companion.data.token.TokenRepository
import com.healthdashboard.companion.sdk.SamsungHealthClient
import com.healthdashboard.companion.sdk.SamsungHealthPermissionManager
import com.healthdashboard.companion.sdk.SdkDataType
import com.healthdashboard.companion.sdk.readers.ReaderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import com.healthdashboard.companion.data.network.ServerSyncResponse

sealed interface CollectionResult {
    data object Idle : CollectionResult
    data object InProgress : CollectionResult
    data class Success(
        val file: File,
        val recordCount: Int,
        val sha256: String,
        val pushStatus: String? = null,
        val serverResponse: ServerSyncResponse? = null,
        val localBreakdown: Map<String, Int> = emptyMap()
    ) : CollectionResult
    data class Error(val message: String) : CollectionResult
}

enum class BackfillHorizon(val days: Int, val label: String) {
    DAYS_30(30, "30 Days"),
    DAYS_90(90, "90 Days"),
    YEAR_1(365, "1 Year"),
    ALL_TIME(2190, "All History (2020+)")
}

class HealthDataCollector(
    private val context: Context,
    private val client: SamsungHealthClient,
    private val permissionManager: SamsungHealthPermissionManager,
    private val tokenRepository: TokenRepository,
    private val payloadExporter: PayloadExporter
) {
    private val _collectionState = MutableStateFlow<CollectionResult>(CollectionResult.Idle)
    val collectionState: StateFlow<CollectionResult> = _collectionState.asStateFlow()

    private val readerRegistry = ReaderRegistry(context)
    private val networkClient = SyncNetworkClient(context)
    private val json = Json { encodeDefaults = true }

    /**
     * Executes a full or incremental collection pass across all 25 Samsung Health Data SDK types.
     */
    suspend fun runCollectionPass(forceFullSync: Boolean = false, daysLookback: Int = 7): CollectionResult = withContext(Dispatchers.IO) {
        _collectionState.value = CollectionResult.InProgress

        try {
            val now = Instant.now()
            val fromTime = now.minus(daysLookback.toLong(), ChronoUnit.DAYS)
            val windowFromIso = fromTime.toString()
            val windowToIso = now.toString()

            val prevToken = if (forceFullSync) null else tokenRepository.getLatestToken()
            val nextToken = "tok_envelope_" + UUID.randomUUID().toString().replace("-", "").take(16)

            val rawRecords = mutableListOf<HealthRecordPayload>()
            // Per-type change tokens read this pass, held back from tokenRepository until
            // the push outcome is known — advancing them on a failed push would silently
            // skip these records on the next sync (the server never received them).
            val pendingTypeTokens = mutableListOf<Pair<String, String>>()

            // 1. Process all 25 readers
            for (type in SdkDataType.entries) {
                val reader = readerRegistry.getReader(type)
                if (type.isChangeTracked) {
                    val storedTypeToken = if (forceFullSync) null else tokenRepository.getLatestToken(type.key)
                    val readResult = reader.readChanges(storedTypeToken)
                    if (readResult.records.isNotEmpty()) {
                        rawRecords.addAll(readResult.records)
                        readResult.nextToken?.let { pendingTypeTokens.add(type.key to it) }
                    } else {
                        val windowRecords = reader.readWindow(windowFromIso, windowToIso)
                        rawRecords.addAll(windowRecords)
                    }
                } else {
                    val windowRecords = reader.readWindow(windowFromIso, windowToIso)
                    rawRecords.addAll(windowRecords)
                }
            }

            // 2. Apply multi-origin deduplication
            val deduplicatedRecords = RecordMappers.deduplicateRecords(rawRecords)

            // 3. Compute SHA-256 over canonical record list
            val recordsJsonString = json.encodeToString(deduplicatedRecords)
            val contentSha256 = payloadExporter.computeSha256(recordsJsonString)

            // 4. Assemble the self-validating JSON envelope with local timezone offset
            val utcOffsetMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            val envelope = SyncPayloadEnvelope(
                schemaVersion = 1,
                source = "samsung_health_sdk",
                deviceId = "${Build.MANUFACTURER}-${Build.MODEL}",
                collectedAt = windowToIso,
                utcOffset = utcOffsetMinutes,
                window = TimeWindow(from = windowFromIso, to = windowToIso),
                changesTokenPrev = prevToken,
                changesTokenNext = nextToken,
                recordCount = deduplicatedRecords.size,
                contentSha256 = contentSha256,
                records = deduplicatedRecords
            )

            // 5. Atomic write (.json.tmp -> .json)
            val exportedFile = payloadExporter.exportPayloadAtomically(envelope)

            val localBreakdown = deduplicatedRecords.groupBy { it.sdkType }.mapValues { it.value.size }
            var serverResponse: ServerSyncResponse? = null

            // 6. Direct Wi-Fi push to Mac/NAS with failover if configured
            val primaryHost = tokenRepository.getPrimaryHost()
            val primaryPort = tokenRepository.getPrimaryPort()
            val fallbackHost = tokenRepository.getFallbackHost()
            val fallbackPort = tokenRepository.getFallbackPort()

            var shouldPersistTokens = primaryHost.isBlank() && fallbackHost.isBlank()
            val pushStatus = if (primaryHost.isNotBlank() || fallbackHost.isNotBlank()) {
                val envelopeJson = json.encodeToString(envelope)
                when (val pushResult = networkClient.pushWithFallback(
                    primaryHost = primaryHost,
                    fallbackHost = fallbackHost,
                    port = primaryPort,
                    payloadJson = envelopeJson,
                    primaryPort = primaryPort,
                    fallbackPort = fallbackPort
                )) {
                    is PushResult.Success -> {
                        serverResponse = pushResult.response
                        shouldPersistTokens = true
                        val usedEndpoint = pushResult.endpointUsed ?: primaryHost
                        tokenRepository.recordSuccessfulSync(deduplicatedRecords.size, usedEndpoint)
                        tokenRepository.recordSyncAttempt(
                            endpoint = usedEndpoint,
                            recordCount = deduplicatedRecords.size,
                            status = "success",
                            latencyMs = 0,
                            details = pushResult.message
                        )
                        "Direct Sync: ${pushResult.message} ✅"
                    }
                    is PushResult.Failed -> {
                        tokenRepository.recordSyncAttempt(
                            endpoint = primaryHost,
                            recordCount = deduplicatedRecords.size,
                            status = "failed",
                            latencyMs = 0,
                            details = pushResult.error
                        )
                        "Direct Sync Offline: ${pushResult.error} (Saved locally)"
                    }
                    is PushResult.Skipped -> null
                }
            } else {
                "Direct Sync: Configure NAS / Mac IP in Tokens & Sync tab"
            }

            // 7. Persist token chain state — only once the push outcome confirms the
            // records these tokens gate were actually delivered (or never needed to be).
            // On PushResult.Failed / Skipped, tokens are left untouched so the next sync
            // re-reads the same changes; idempotent server-side UPSERTs absorb the re-send.
            if (shouldPersistTokens) {
                pendingTypeTokens.forEach { (typeKey, token) -> tokenRepository.saveToken(token, typeKey) }
                tokenRepository.updateTokens(
                    prevToken = prevToken,
                    nextToken = nextToken,
                    syncTimestamp = windowToIso
                )
            }

            val result = CollectionResult.Success(
                file = exportedFile,
                recordCount = deduplicatedRecords.size,
                sha256 = contentSha256,
                pushStatus = pushStatus,
                serverResponse = serverResponse,
                localBreakdown = localBreakdown
            )
            _collectionState.value = result
            result
        } catch (e: Exception) {
            val error = CollectionResult.Error(e.message ?: "Unknown collection error occurred")
            _collectionState.value = error
            error
        }
    }

    /**
     * Collects and pushes a single window slice (e.g. 7 days / 30 days) with chunk metadata.
     */
    suspend fun collectAndPushSlice(
        windowFromIso: String,
        windowToIso: String,
        batchId: String,
        chunkIndex: Int,
        totalChunks: Int
    ): SliceResult = withContext(Dispatchers.IO) {
        try {
            val rawRecords = mutableListOf<HealthRecordPayload>()
            for (type in SdkDataType.entries) {
                val reader = readerRegistry.getReader(type)
                val windowRecords = reader.readWindow(windowFromIso, windowToIso)
                rawRecords.addAll(windowRecords)
            }

            val deduplicatedRecords = RecordMappers.deduplicateRecords(rawRecords)
            val recordsJsonString = json.encodeToString(deduplicatedRecords)
            val contentSha256 = payloadExporter.computeSha256(recordsJsonString)

            val utcOffsetMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            val envelope = SyncPayloadEnvelope(
                schemaVersion = 1,
                source = "samsung_health_sdk",
                deviceId = "${Build.MANUFACTURER}-${Build.MODEL}",
                collectedAt = Instant.now().toString(),
                utcOffset = utcOffsetMinutes,
                window = TimeWindow(from = windowFromIso, to = windowToIso),
                batchId = batchId,
                chunkIndex = chunkIndex,
                totalChunks = totalChunks,
                recordCount = deduplicatedRecords.size,
                contentSha256 = contentSha256,
                records = deduplicatedRecords
            )

            // Save chunk archive locally
            payloadExporter.exportPayloadAtomically(envelope)

            val primaryHost = tokenRepository.getPrimaryHost()
            val primaryPort = tokenRepository.getPrimaryPort()
            val fallbackHost = tokenRepository.getFallbackHost()
            val fallbackPort = tokenRepository.getFallbackPort()

            if (primaryHost.isBlank() && fallbackHost.isBlank()) {
                return@withContext SliceResult.Error("Mac/NAS server host not configured")
            }

            val envelopeJson = json.encodeToString(envelope)
            when (val pushResult = networkClient.pushWithFallback(
                primaryHost = primaryHost,
                fallbackHost = fallbackHost,
                port = primaryPort,
                payloadJson = envelopeJson,
                primaryPort = primaryPort,
                fallbackPort = fallbackPort
            )) {
                is PushResult.Success -> {
                    val usedEndpoint = pushResult.endpointUsed ?: primaryHost
                    tokenRepository.recordSuccessfulSync(deduplicatedRecords.size, usedEndpoint)
                    SliceResult.Success(deduplicatedRecords.size)
                }
                is PushResult.Failed -> SliceResult.Error(pushResult.error)
                is PushResult.Skipped -> SliceResult.Error("Sync skipped")
            }
        } catch (e: Exception) {
            SliceResult.Error(e.message ?: "Unknown slice collection error")
        }
    }
}

sealed interface SliceResult {
    data class Success(val recordCount: Int) : SliceResult
    data class Error(val message: String) : SliceResult
}
