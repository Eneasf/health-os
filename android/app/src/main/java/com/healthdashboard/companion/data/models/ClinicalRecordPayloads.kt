package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BloodPressureRecordPayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("systolic_mmhg") val systolicMmHg: Float,
    @SerialName("diastolic_mmhg") val diastolicMmHg: Float,
    @SerialName("mean_mmhg") val meanMmHg: Float? = null,
    @SerialName("pulse_rate_bpm") val pulseRateBpm: Float? = null,
    @SerialName("medication_taken") val medicationTaken: Boolean? = null
)

@Serializable
data class BloodGlucoseRecordPayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("glucose_level_mg_dl") val glucoseLevelMgDl: Float,
    @SerialName("measurement_type") val measurementType: String? = null,
    @SerialName("meal_time") val mealTime: String? = null,
    @SerialName("meal_status") val mealStatus: String? = null,
    @SerialName("insulin_injected") val insulinInjected: Float? = null,
    @SerialName("medication_taken") val medicationTaken: Boolean? = null
)

@Serializable
data class BodyTemperaturePayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("temperature_celsius") val temperatureCelsius: Float
)

@Serializable
data class SkinTemperaturePoint(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("temperature_celsius") val temperatureCelsius: Float
)

@Serializable
data class SkinTemperaturePayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("skin_temperature_celsius") val skinTemperatureCelsius: Float,
    @SerialName("min_skin_temperature_celsius") val minSkinTemperatureCelsius: Float? = null,
    @SerialName("max_skin_temperature_celsius") val maxSkinTemperatureCelsius: Float? = null,
    @SerialName("series_data") val seriesData: List<SkinTemperaturePoint> = emptyList()
)

@Serializable
data class SleepApneaPayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("detected_sign") val detectedSign: String
)

@Serializable
data class WaterIntakePayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("amount_ml") val amountMl: Float
)

@Serializable
data class IrregularHeartRhythmPayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("status") val status: String
)
