package com.healthdashboard.companion.sdk.readers

import android.content.Context
import com.healthdashboard.companion.sdk.SdkDataType

class ReaderRegistry(private val context: Context) {

    private val readers: Map<SdkDataType, HealthTypeReader> = mapOf(
        SdkDataType.SLEEP to SleepReader(context),
        SdkDataType.HEART_RATE to HeartRateReader(context),
        SdkDataType.NUTRITION to NutritionReader(context),
        SdkDataType.EXERCISE to ExerciseReader(context),
        SdkDataType.BODY_COMPOSITION to BodyCompositionReader(context),
        SdkDataType.STEPS to StepsReader(context),
        SdkDataType.FLOORS_CLIMBED to FloorsClimbedReader(context),
        SdkDataType.ACTIVITY_SUMMARY to ActivitySummaryReader(context),
        SdkDataType.BLOOD_OXYGEN to BloodOxygenReader(context),
        SdkDataType.SKIN_TEMPERATURE to SkinTemperatureReader(context),
        SdkDataType.SLEEP_APNEA to SleepApneaReader(context),
        SdkDataType.ENERGY_SCORE to EnergyScoreReader(context),
        SdkDataType.WATER_INTAKE to WaterIntakeReader(context),
        SdkDataType.IRREGULAR_HEART_RHYTHM_NOTIFICATION to IrregularHeartRhythmReader(context),
        SdkDataType.USER_PROFILE to UserProfileReader(context),
        SdkDataType.BLOOD_GLUCOSE to BloodGlucoseReader(context),
        SdkDataType.BLOOD_PRESSURE to BloodPressureReader(context),
        SdkDataType.BODY_TEMPERATURE to BodyTemperatureReader(context),
        SdkDataType.EXERCISE_LOCATION to ExerciseLocationReader(context),
        SdkDataType.SLEEP_GOAL to GoalReader(context, SdkDataType.SLEEP_GOAL, 480.0, "MINUTES"),
        SdkDataType.STEPS_GOAL to GoalReader(context, SdkDataType.STEPS_GOAL, 10000.0, "STEPS"),
        SdkDataType.WATER_INTAKE_GOAL to GoalReader(context, SdkDataType.WATER_INTAKE_GOAL, 3000.0, "ML"),
        SdkDataType.NUTRITION_GOAL to GoalReader(context, SdkDataType.NUTRITION_GOAL, 2500.0, "KCAL"),
        SdkDataType.ACTIVE_CALORIES_BURNED_GOAL to GoalReader(context, SdkDataType.ACTIVE_CALORIES_BURNED_GOAL, 600.0, "KCAL"),
        SdkDataType.ACTIVE_TIME_GOAL to GoalReader(context, SdkDataType.ACTIVE_TIME_GOAL, 60.0, "MINUTES")
    )

    fun getReader(type: SdkDataType): HealthTypeReader {
        return readers[type] ?: throw IllegalArgumentException("No reader registered for $type")
    }

    fun getAllReaders(): List<HealthTypeReader> {
        return SdkDataType.entries.map { getReader(it) }
    }

    fun getChangeTrackedReaders(): List<HealthTypeReader> {
        return SdkDataType.entries.filter { it.isChangeTracked }.map { getReader(it) }
    }
}
