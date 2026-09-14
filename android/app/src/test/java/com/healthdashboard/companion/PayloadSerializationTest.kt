package com.healthdashboard.companion

import com.healthdashboard.companion.data.models.ActivitySummaryPayload
import com.healthdashboard.companion.data.models.BodyCompRecordPayload
import com.healthdashboard.companion.data.models.ExerciseRecordPayload
import com.healthdashboard.companion.data.models.HeartRateBin
import com.healthdashboard.companion.data.models.HeartRateRecordPayload
import com.healthdashboard.companion.data.models.NocturnalOxygenSample
import com.healthdashboard.companion.data.models.NutritionRecordPayload
import com.healthdashboard.companion.data.models.SleepSessionPayload
import com.healthdashboard.companion.data.models.SleepStagePayload
import com.healthdashboard.companion.data.models.SleepStageType
import com.healthdashboard.companion.data.models.StepRecordPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadSerializationTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testSleepPayloadSerialization() {
        val sleep = SleepSessionPayload(
            startTime = "2026-08-26T22:15:00Z",
            endTime = "2026-08-27T05:56:00Z",
            wakeDate = "2026-08-27",
            durationMinutes = 461.0,
            totalSleepMinutes = 432.5,
            sleepScore = 90,
            deepSleepMinutes = 132.5,
            remSleepMinutes = 78.5,
            lightSleepMinutes = 221.5,
            awakeMinutes = 29.0,
            efficiencyPct = 93.8,
            stages = listOf(
                SleepStagePayload(
                    startTime = "2026-08-26T22:15:00Z",
                    endTime = "2026-08-26T22:45:00Z",
                    stage = SleepStageType.LIGHT,
                    durationSeconds = 1800
                ),
                SleepStagePayload(
                    startTime = "2026-08-26T22:45:00Z",
                    endTime = "2026-08-27T00:57:30Z",
                    stage = SleepStageType.DEEP,
                    durationSeconds = 7950
                )
            ),
            spo2Samples = listOf(
                NocturnalOxygenSample(timestamp = "2026-08-27T02:00:00Z", spo2Pct = 97.0f),
                NocturnalOxygenSample(timestamp = "2026-08-27T03:30:00Z", spo2Pct = 94.0f)
            )
        )

        val str = json.encodeToString(sleep)
        assertNotNull(str)
        assertTrue(str.contains("\"sleep_score\": 90"))
        assertTrue(str.contains("\"wake_date\": \"2026-08-27\""))

        val decoded = json.decodeFromString<SleepSessionPayload>(str)
        assertEquals(90, decoded.sleepScore)
        assertEquals(432.5, decoded.totalSleepMinutes, 0.001)
        assertEquals(2, decoded.stages.size)
        assertEquals(2, decoded.spo2Samples.size)
    }

    @Test
    fun testHeartRatePayloadSerialization() {
        val hr = HeartRateRecordPayload(
            startTime = "2026-08-27T08:00:00Z",
            endTime = "2026-08-27T09:00:00Z",
            date = "2026-08-27",
            meanBpm = 72.4f,
            minBpm = 58.0f,
            maxBpm = 112.0f,
            seriesData = listOf(
                HeartRateBin(
                    startTime = "2026-08-27T08:01:00Z",
                    endTime = "2026-08-27T08:02:00Z",
                    bpm = 70.5f,
                    minBpm = 68.0f,
                    maxBpm = 73.0f
                )
            )
        )

        val str = json.encodeToString(hr)
        val decoded = json.decodeFromString<HeartRateRecordPayload>(str)
        assertEquals(72.4f, decoded.meanBpm, 0.01f)
        assertEquals(1, decoded.seriesData.size)
        assertEquals(70.5f, decoded.seriesData[0].bpm, 0.01f)
    }

    @Test
    fun testNutritionPayloadSerialization() {
        val meal = NutritionRecordPayload(
            mealType = "Breakfast",
            title = "MyFitnessPal Oatmeal & Whey",
            timestamp = "2026-08-27T07:30:00Z",
            caloriesKcal = 540.0,
            proteinG = 48.5,
            carbohydratesG = 62.0,
            totalFatG = 8.5,
            saturatedFatG = 1.8,
            dietaryFiberG = 7.2,
            sodiumMg = 210.0,
            potassiumMg = 450.0
        )

        val str = json.encodeToString(meal)
        val decoded = json.decodeFromString<NutritionRecordPayload>(str)
        assertEquals(540.0, decoded.caloriesKcal, 0.01)
        assertEquals(48.5, decoded.proteinG, 0.01)
        assertEquals("MyFitnessPal Oatmeal & Whey", decoded.title)
    }

    @Test
    fun testExerciseAndBodyCompPayloadSerialization() {
        val exercise = ExerciseRecordPayload(
            exerciseType = "WEIGHT_MACHINE",
            customTitle = "eGym Chest Press",
            startTime = "2026-08-27T17:00:00Z",
            endTime = "2026-08-27T17:45:00Z",
            durationMinutes = 45.0,
            caloriesKcal = 320.0,
            meanHeartRateBpm = 128.5f,
            maxHeartRateBpm = 150.0f,
            count = 12,
            countType = "REPETITION"
        )

        val exerciseStr = json.encodeToString(exercise)
        val decodedExercise = json.decodeFromString<ExerciseRecordPayload>(exerciseStr)
        assertEquals(12, decodedExercise.count)
        assertEquals("WEIGHT_MACHINE", decodedExercise.exerciseType)

        val bodyComp = BodyCompRecordPayload(
            timestamp = "2026-08-27T06:45:00Z",
            weightKg = 75.0,
            heightCm = 173.0,
            bodyFatPct = 19.8,
            skeletalMuscleMassKg = 34.2,
            muscleMassKg = 56.5,
            bmrKcal = 1680.0,
            bmi = 25.06
        )

        val bodyCompStr = json.encodeToString(bodyComp)
        val decodedBodyComp = json.decodeFromString<BodyCompRecordPayload>(bodyCompStr)
        assertEquals(75.0, decodedBodyComp.weightKg, 0.01)
        assertEquals(173.0, decodedBodyComp.heightCm ?: 0.0, 0.01)
    }

    @Test
    fun testActivityPayloadSerialization() {
        val steps = StepRecordPayload(
            date = "2026-08-27",
            totalSteps = 10450,
            caloriesBurnedKcal = 480.0,
            distanceMeters = 8200.0
        )
        val summary = ActivitySummaryPayload(
            date = "2026-08-27",
            totalCaloriesBurnedKcal = 2350.0,
            totalActiveCaloriesKcal = 680.0,
            totalActiveTimeMinutes = 85.0,
            totalDistanceMeters = 9500.0
        )

        val stepsStr = json.encodeToString(steps)
        val summaryStr = json.encodeToString(summary)

        assertEquals(10450L, json.decodeFromString<StepRecordPayload>(stepsStr).totalSteps)
        assertEquals(2350.0, json.decodeFromString<ActivitySummaryPayload>(summaryStr).totalCaloriesBurnedKcal, 0.01)
    }
}
