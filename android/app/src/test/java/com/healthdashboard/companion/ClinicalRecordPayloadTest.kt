package com.healthdashboard.companion

import com.healthdashboard.companion.data.models.BloodGlucoseRecordPayload
import com.healthdashboard.companion.data.models.BloodPressureRecordPayload
import com.healthdashboard.companion.data.models.BodyTemperaturePayload
import com.healthdashboard.companion.data.models.SkinTemperaturePayload
import com.healthdashboard.companion.data.models.SleepApneaPayload
import com.healthdashboard.companion.data.models.WaterIntakePayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalRecordPayloadTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testBloodPressureSerialization() {
        val bp = BloodPressureRecordPayload(
            timestamp = "2026-08-27T07:15:00Z",
            systolicMmHg = 118.0f,
            diastolicMmHg = 76.0f,
            meanMmHg = 90.0f,
            pulseRateBpm = 64.0f,
            medicationTaken = false
        )
        val str = json.encodeToString(bp)
        assertNotNull(str)
        assertTrue(str.contains("\"systolic_mmhg\": 118.0"))

        val decoded = json.decodeFromString<BloodPressureRecordPayload>(str)
        assertEquals(118.0f, decoded.systolicMmHg, 0.01f)
        assertEquals(76.0f, decoded.diastolicMmHg, 0.01f)
        assertEquals(64.0f, decoded.pulseRateBpm ?: 0f, 0.01f)
    }

    @Test
    fun testBloodGlucoseSerialization() {
        val glucose = BloodGlucoseRecordPayload(
            timestamp = "2026-08-27T06:30:00Z",
            glucoseLevelMgDl = 92.5f,
            measurementType = "FASTING",
            mealStatus = "BEFORE_MEAL",
            medicationTaken = false
        )
        val str = json.encodeToString(glucose)
        val decoded = json.decodeFromString<BloodGlucoseRecordPayload>(str)
        assertEquals(92.5f, decoded.glucoseLevelMgDl, 0.01f)
        assertEquals("FASTING", decoded.measurementType)
    }

    @Test
    fun testBodyAndSkinTemperatureSerialization() {
        val bodyTemp = BodyTemperaturePayload(
            timestamp = "2026-08-27T06:30:00Z",
            temperatureCelsius = 36.6f
        )
        val skinTemp = SkinTemperaturePayload(
            timestamp = "2026-08-27T04:00:00Z",
            skinTemperatureCelsius = 34.4f,
            minSkinTemperatureCelsius = 33.9f,
            maxSkinTemperatureCelsius = 34.8f
        )

        val decodedBody = json.decodeFromString<BodyTemperaturePayload>(json.encodeToString(bodyTemp))
        val decodedSkin = json.decodeFromString<SkinTemperaturePayload>(json.encodeToString(skinTemp))

        assertEquals(36.6f, decodedBody.temperatureCelsius, 0.01f)
        assertEquals(34.4f, decodedSkin.skinTemperatureCelsius, 0.01f)
    }

    @Test
    fun testSleepApneaAndWaterIntakeSerialization() {
        val apnea = SleepApneaPayload(
            timestamp = "2026-08-27T06:00:00Z",
            detectedSign = "NOT_DETECTED"
        )
        val water = WaterIntakePayload(
            timestamp = "2026-08-27T12:00:00Z",
            amountMl = 750.0f
        )

        assertEquals("NOT_DETECTED", json.decodeFromString<SleepApneaPayload>(json.encodeToString(apnea)).detectedSign)
        assertEquals(750.0f, json.decodeFromString<WaterIntakePayload>(json.encodeToString(water)).amountMl, 0.01f)
    }
}
