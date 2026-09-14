package com.healthdashboard.companion.sdk

/**
 * Authoritative capability map for Samsung Health Data SDK (1.1.0).
 * Matches docs/SAMSUNG_SDK_CONTRACT.md.
 */
enum class SdkDataType(
    val key: String,
    val isChangeTracked: Boolean,
    val isWritable: Boolean,
    val displayName: String,
    val description: String
) {
    SLEEP(
        key = "SLEEP",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Sleep & Stages",
        description = "Sleep sessions, stages (deep/REM/light/awake), sleep score & nocturnal SpO2"
    ),
    HEART_RATE(
        key = "HEART_RATE",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Heart Rate & Series",
        description = "Hourly HR summaries with intra-hour per-minute series data"
    ),
    NUTRITION(
        key = "NUTRITION",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Nutrition & Meals",
        description = "Itemized meal logs, macros, fiber, sugar, sodium & micronutrients"
    ),
    EXERCISE(
        key = "EXERCISE",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Exercise & Workouts",
        description = "Workout sessions, active calories, mean/max HR, and rep counts"
    ),
    BODY_COMPOSITION(
        key = "BODY_COMPOSITION",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Body Composition",
        description = "Weight, skeletal muscle mass, body fat %, BMR, and total body water"
    ),
    STEPS(
        key = "STEPS",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Steps",
        description = "Daily aggregate step counts"
    ),
    FLOORS_CLIMBED(
        key = "FLOORS_CLIMBED",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Floors Climbed",
        description = "Total floors climbed tracking"
    ),
    ACTIVITY_SUMMARY(
        key = "ACTIVITY_SUMMARY",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Activity Summary",
        description = "Total calories burned, active time, and total distance"
    ),
    BLOOD_OXYGEN(
        key = "BLOOD_OXYGEN",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Blood Oxygen (SpO2)",
        description = "Resting and sleep-associated oxygen saturation series"
    ),
    SKIN_TEMPERATURE(
        key = "SKIN_TEMPERATURE",
        isChangeTracked = true,
        isWritable = false,
        displayName = "Skin Temperature",
        description = "Nocturnal and baseline skin temperature"
    ),
    SLEEP_APNEA(
        key = "SLEEP_APNEA",
        isChangeTracked = true,
        isWritable = false,
        displayName = "Sleep Apnea Signs",
        description = "Detected sleep apnea indicators and status"
    ),
    ENERGY_SCORE(
        key = "ENERGY_SCORE",
        isChangeTracked = true,
        isWritable = false,
        displayName = "Energy Score",
        description = "Daily Samsung Galaxy AI energy score"
    ),
    WATER_INTAKE(
        key = "WATER_INTAKE",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Water Intake",
        description = "Daily hydration volume in ml"
    ),
    IRREGULAR_HEART_RHYTHM_NOTIFICATION(
        key = "IRREGULAR_HEART_RHYTHM_NOTIFICATION",
        isChangeTracked = true,
        isWritable = false,
        displayName = "IHRN Notifications",
        description = "Irregular heart rhythm notification events"
    ),
    USER_PROFILE(
        key = "USER_PROFILE",
        isChangeTracked = false,
        isWritable = false,
        displayName = "User Profile",
        description = "Birth date, height, baseline demographics"
    ),
    BLOOD_GLUCOSE(
        key = "BLOOD_GLUCOSE",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Blood Glucose",
        description = "Continuous / spot blood glucose readings"
    ),
    BLOOD_PRESSURE(
        key = "BLOOD_PRESSURE",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Blood Pressure",
        description = "Systolic, diastolic, mean, and pulse rate"
    ),
    BODY_TEMPERATURE(
        key = "BODY_TEMPERATURE",
        isChangeTracked = true,
        isWritable = true,
        displayName = "Body Temperature",
        description = "Core body temperature measurements"
    ),
    EXERCISE_LOCATION(
        key = "EXERCISE_LOCATION",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Exercise Route",
        description = "GPS path coordinates for outdoor workouts"
    ),
    SLEEP_GOAL(
        key = "SLEEP_GOAL",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Sleep Goal",
        description = "Target sleep duration"
    ),
    STEPS_GOAL(
        key = "STEPS_GOAL",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Steps Goal",
        description = "Daily step target"
    ),
    WATER_INTAKE_GOAL(
        key = "WATER_INTAKE_GOAL",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Water Goal",
        description = "Daily water consumption target"
    ),
    NUTRITION_GOAL(
        key = "NUTRITION_GOAL",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Nutrition Goal",
        description = "Daily calorie and macro targets"
    ),
    ACTIVE_CALORIES_BURNED_GOAL(
        key = "ACTIVE_CALORIES_BURNED_GOAL",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Active Calorie Goal",
        description = "Daily active burn target"
    ),
    ACTIVE_TIME_GOAL(
        key = "ACTIVE_TIME_GOAL",
        isChangeTracked = false,
        isWritable = false,
        displayName = "Active Time Goal",
        description = "Daily active duration target"
    );

    companion object {
        val PRIMARY_COLLECTION_TYPES = listOf(
            SLEEP,
            HEART_RATE,
            NUTRITION,
            EXERCISE,
            BODY_COMPOSITION,
            STEPS,
            FLOORS_CLIMBED,
            ACTIVITY_SUMMARY,
            BLOOD_OXYGEN,
            ENERGY_SCORE,
            USER_PROFILE
        )
    }
}
