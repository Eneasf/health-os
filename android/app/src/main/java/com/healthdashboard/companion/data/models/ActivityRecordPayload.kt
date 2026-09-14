package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StepRecordPayload(
    @SerialName("date") val date: String,
    @SerialName("total_steps") val totalSteps: Long,
    @SerialName("calories_burned_kcal") val caloriesBurnedKcal: Double? = null,
    @SerialName("distance_meters") val distanceMeters: Double? = null
)

@Serializable
data class FloorsClimbedPayload(
    @SerialName("date") val date: String,
    @SerialName("floors") val floors: Double
)

@Serializable
data class ActivitySummaryPayload(
    @SerialName("date") val date: String,
    @SerialName("total_calories_burned_kcal") val totalCaloriesBurnedKcal: Double = 0.0,
    @SerialName("total_active_calories_kcal") val totalActiveCaloriesKcal: Double = 0.0,
    @SerialName("total_active_time_minutes") val totalActiveTimeMinutes: Double = 0.0,
    @SerialName("total_distance_meters") val totalDistanceMeters: Double = 0.0
)

@Serializable
data class EnergyScorePayload(
    @SerialName("date") val date: String,
    @SerialName("score") val score: Float
)

@Serializable
data class BloodOxygenRecordPayload(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("spo2_pct") val spo2Pct: Float,
    @SerialName("min_spo2_pct") val minSpo2Pct: Float? = null,
    @SerialName("max_spo2_pct") val maxSpo2Pct: Float? = null
)
