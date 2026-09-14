package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeartRateBin(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("bpm") val bpm: Float,
    @SerialName("min_bpm") val minBpm: Float? = null,
    @SerialName("max_bpm") val maxBpm: Float? = null
)

@Serializable
data class HeartRateRecordPayload(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("date") val date: String,
    @SerialName("mean_bpm") val meanBpm: Float,
    @SerialName("min_bpm") val minBpm: Float? = null,
    @SerialName("max_bpm") val maxBpm: Float? = null,
    @SerialName("series_data") val seriesData: List<HeartRateBin> = emptyList()
)
