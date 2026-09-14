package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NutritionRecordPayload(
    @SerialName("meal_type") val mealType: String,
    @SerialName("title") val title: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("calories_kcal") val caloriesKcal: Double,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbohydrates_g") val carbohydratesG: Double = 0.0,
    @SerialName("total_fat_g") val totalFatG: Double = 0.0,
    @SerialName("saturated_fat_g") val saturatedFatG: Double? = null,
    @SerialName("polysaturated_fat_g") val polysaturatedFatG: Double? = null,
    @SerialName("monosaturated_fat_g") val monosaturatedFatG: Double? = null,
    @SerialName("trans_fat_g") val transFatG: Double? = null,
    @SerialName("dietary_fiber_g") val dietaryFiberG: Double? = null,
    @SerialName("sugar_g") val sugarG: Double? = null,
    @SerialName("cholesterol_mg") val cholesterolMg: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    @SerialName("potassium_mg") val potassiumMg: Double? = null,
    @SerialName("vitamin_a_ug") val vitaminAUg: Double? = null,
    @SerialName("vitamin_c_mg") val vitaminCMg: Double? = null,
    @SerialName("calcium_mg") val calciumMg: Double? = null,
    @SerialName("iron_mg") val ironMg: Double? = null
)
