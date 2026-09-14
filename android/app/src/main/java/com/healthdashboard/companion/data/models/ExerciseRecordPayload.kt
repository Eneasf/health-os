package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseRecordPayload(
    @SerialName("exercise_type") val exerciseType: String,
    @SerialName("custom_title") val customTitle: String? = null,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_minutes") val durationMinutes: Double,
    @SerialName("calories_kcal") val caloriesKcal: Double? = null,
    @SerialName("mean_heart_rate_bpm") val meanHeartRateBpm: Float? = null,
    @SerialName("max_heart_rate_bpm") val maxHeartRateBpm: Float? = null,
    @SerialName("min_heart_rate_bpm") val minHeartRateBpm: Float? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("count_type") val countType: String? = null,
    @SerialName("mean_power_watts") val meanPowerWatts: Float? = null,
    @SerialName("max_power_watts") val maxPowerWatts: Float? = null,
    @SerialName("mean_cadence_rpm") val meanCadenceRpm: Float? = null,
    @SerialName("distance_meters") val distanceMeters: Double? = null,
    @SerialName("auto_detected") val autoDetected: Boolean = false
)
