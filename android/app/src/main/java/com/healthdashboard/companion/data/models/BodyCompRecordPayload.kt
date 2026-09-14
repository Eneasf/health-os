package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BodyCompRecordPayload(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("body_fat_pct") val bodyFatPct: Double? = null,
    @SerialName("body_fat_mass_kg") val bodyFatMassKg: Double? = null,
    @SerialName("fat_free_mass_kg") val fatFreeMassKg: Double? = null,
    @SerialName("skeletal_muscle_mass_kg") val skeletalMuscleMassKg: Double? = null,
    @SerialName("muscle_mass_kg") val muscleMassKg: Double? = null,
    @SerialName("total_body_water_l") val totalBodyWaterL: Double? = null,
    @SerialName("bmr_kcal") val bmrKcal: Double? = null,
    @SerialName("bmi") val bmi: Double? = null
)
