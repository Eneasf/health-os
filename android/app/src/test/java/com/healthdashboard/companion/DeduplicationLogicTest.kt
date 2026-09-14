package com.healthdashboard.companion

import com.healthdashboard.companion.data.mappers.RecordMappers
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class DeduplicationLogicTest {

    @Test
    fun testSleepDeduplicationPrefersSamsungNativeOverWithingsRelay() {
        val payloadShealth = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "SLEEP",
            dataUid = "sleep_night_20260827",
            lastModified = "2026-08-27T06:00:00Z",
            dataOrigin = "com.sec.android.app.shealth",
            payload = buildJsonObject { put("sleep_score", 90) }
        )

        val payloadWithingsRelay = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "SLEEP",
            dataUid = "sleep_night_20260827",
            lastModified = "2026-08-27T06:05:00Z",
            dataOrigin = "com.withings.wiscale2",
            payload = buildJsonObject { put("sleep_score", 0) }
        )

        val list = listOf(payloadWithingsRelay, payloadShealth)
        val deduplicated = RecordMappers.deduplicateRecords(list)

        assertEquals(1, deduplicated.size)
        assertEquals("com.sec.android.app.shealth", deduplicated[0].dataOrigin)
        assertEquals(90, deduplicated[0].payload["sleep_score"]?.toString()?.toInt())
    }

    @Test
    fun testNutritionDeduplicationPrefersMyFitnessPalOverSamsungMirror() {
        val payloadMFP = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "NUTRITION",
            dataUid = "meal_breakfast_20260827",
            lastModified = "2026-08-27T08:00:00Z",
            dataOrigin = "com.myfitnesspal.android",
            payload = buildJsonObject { put("protein_g", 48.5) }
        )

        val payloadShealthMirror = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "NUTRITION",
            dataUid = "meal_breakfast_20260827",
            lastModified = "2026-08-27T08:01:00Z",
            dataOrigin = "com.sec.android.app.shealth",
            payload = buildJsonObject { put("protein_g", 48.0) }
        )

        val list = listOf(payloadShealthMirror, payloadMFP)
        val deduplicated = RecordMappers.deduplicateRecords(list)

        assertEquals(1, deduplicated.size)
        assertEquals("com.myfitnesspal.android", deduplicated[0].dataOrigin)
    }

    @Test
    fun testExerciseDeduplicationPrefersEgymOverSamsungWatch() {
        val payloadEgym = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "EXERCISE",
            dataUid = "gym_workout_20260827",
            lastModified = "2026-08-27T18:00:00Z",
            dataOrigin = "com.egym.app",
            payload = buildJsonObject { put("load_kg", 85.0) }
        )

        val payloadWatch = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "EXERCISE",
            dataUid = "gym_workout_20260827",
            lastModified = "2026-08-27T18:00:00Z",
            dataOrigin = "com.sec.android.app.shealth",
            payload = buildJsonObject { put("calories", 320.0) }
        )

        val list = listOf(payloadWatch, payloadEgym)
        val deduplicated = RecordMappers.deduplicateRecords(list)

        assertEquals(1, deduplicated.size)
        assertEquals("com.egym.app", deduplicated[0].dataOrigin)
    }
}
