package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimeWindow(
    @SerialName("from") val from: String,
    @SerialName("to") val to: String
)

@Serializable
data class SyncPayloadEnvelope(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("app_version") val appVersion: String = "1.2.0",
    @SerialName("source") val source: String = "samsung_health_sdk",
    @SerialName("device_id") val deviceId: String,
    @SerialName("collected_at") val collectedAt: String,
    @SerialName("utc_offset") val utcOffset: Int = 0,
    @SerialName("window") val window: TimeWindow,
    @SerialName("changes_token_prev") val changesTokenPrev: String? = null,
    @SerialName("changes_token_next") val changesTokenNext: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("chunk_index") val chunkIndex: Int? = null,
    @SerialName("total_chunks") val totalChunks: Int? = null,
    @SerialName("record_count") val recordCount: Int,
    @SerialName("content_sha256") val contentSha256: String,
    @SerialName("records") val records: List<HealthRecordPayload>
)
