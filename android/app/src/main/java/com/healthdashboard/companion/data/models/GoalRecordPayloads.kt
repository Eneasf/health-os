package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfilePayload(
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("height_cm") val heightCm: Float? = null,
    @SerialName("weight_kg") val weightKg: Float? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("nickname") val nickname: String? = null
)

@Serializable
data class RouteLocationPoint(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude_meters") val altitudeMeters: Double? = null
)

@Serializable
data class ExerciseLocationPayload(
    @SerialName("exercise_id") val exerciseId: String? = null,
    @SerialName("locations") val locations: List<RouteLocationPoint> = emptyList()
)

@Serializable
data class GoalRecordPayload(
    @SerialName("goal_type") val goalType: String,
    @SerialName("target_value") val targetValue: Double,
    @SerialName("unit") val unit: String? = null
)
