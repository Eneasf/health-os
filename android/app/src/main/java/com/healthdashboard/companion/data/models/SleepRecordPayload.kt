package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SleepStageType {
    @SerialName("AWAKE") AWAKE,
    @SerialName("LIGHT") LIGHT,
    @SerialName("DEEP") DEEP,
    @SerialName("REM") REM
}

@Serializable
data class SleepStagePayload(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("stage") val stage: SleepStageType,
    @SerialName("duration_seconds") val durationSeconds: Long
)

@Serializable
data class NocturnalOxygenSample(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("spo2_pct") val spo2Pct: Float
)

@Serializable
data class SleepSessionPayload(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("wake_date") val wakeDate: String,
    @SerialName("duration_minutes") val durationMinutes: Double,
    @SerialName("total_sleep_minutes") val totalSleepMinutes: Double,
    @SerialName("sleep_score") val sleepScore: Int? = null,
    @SerialName("deep_sleep_minutes") val deepSleepMinutes: Double = 0.0,
    @SerialName("rem_sleep_minutes") val remSleepMinutes: Double = 0.0,
    @SerialName("light_sleep_minutes") val lightSleepMinutes: Double = 0.0,
    @SerialName("awake_minutes") val awakeMinutes: Double = 0.0,
    @SerialName("efficiency_pct") val efficiencyPct: Double = 0.0,
    @SerialName("stages") val stages: List<SleepStagePayload> = emptyList(),
    @SerialName("spo2_samples") val spo2Samples: List<NocturnalOxygenSample> = emptyList()
)
