package com.healthdashboard.companion

import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.SyncPayloadEnvelope
import com.healthdashboard.companion.data.models.TimeWindow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SyncPayloadEnvelopeTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testEnvelopeSerializationMatchesSpec() {
        val samplePayload = buildJsonObject {
            put("wake_date", "2026-08-27")
            put("sleep_score", 90)
            put("total_sleep_minutes", 432.5)
            put("duration_minutes", 461.0)
        }

        val record = HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = "SLEEP",
            dataUid = "a4f2-901b-c3d4-e5f6",
            naturalKey = "nat_sleep_2026-08-27",
            lastModified = "2026-08-27T05:58:11Z",
            dataOrigin = "com.sec.android.app.shealth",
            payload = samplePayload
        )

        val records = listOf(record)
        val recordsJson = json.encodeToString(records)

        val digest = MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(recordsJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val envelope = SyncPayloadEnvelope(
            schemaVersion = 1,
            source = "samsung_health_sdk",
            deviceId = "galaxy-watch-6",
            collectedAt = "2026-08-27T06:12:00Z",
            utcOffset = 60,
            window = TimeWindow(from = "2026-08-26T00:00:00Z", to = "2026-08-27T00:00:00Z"),
            changesTokenPrev = "token_prev_123",
            changesTokenNext = "token_next_456",
            recordCount = 1,
            contentSha256 = sha256,
            records = records
        )

        val serialized = json.encodeToString(envelope)
        assertNotNull(serialized)
        assertTrue(serialized.contains("\"schema_version\": 1"))
        assertTrue(serialized.contains("\"source\": \"samsung_health_sdk\""))
        assertTrue(serialized.contains("\"device_id\": \"galaxy-watch-6\""))
        assertTrue(serialized.contains("\"utc_offset\": 60"))
        assertTrue(serialized.contains("\"changes_token_prev\": \"token_prev_123\""))
        assertTrue(serialized.contains("\"changes_token_next\": \"token_next_456\""))
        assertTrue(serialized.contains("\"record_count\": 1"))
        assertTrue(serialized.contains("\"change_type\": \"upsert\""))
        assertTrue(serialized.contains("\"sdk_type\": \"SLEEP\""))
        assertTrue(serialized.contains("\"natural_key\": \"nat_sleep_2026-08-27\""))
        assertTrue(serialized.contains("\"sleep_score\": 90"))

        val deserialized = json.decodeFromString<SyncPayloadEnvelope>(serialized)
        assertEquals(envelope.schemaVersion, deserialized.schemaVersion)
        assertEquals(envelope.source, deserialized.source)
        assertEquals(envelope.utcOffset, deserialized.utcOffset)
        assertEquals(envelope.contentSha256, deserialized.contentSha256)
        assertEquals(envelope.records.size, deserialized.records.size)
        assertEquals("a4f2-901b-c3d4-e5f6", deserialized.records[0].dataUid)
        assertEquals("nat_sleep_2026-08-27", deserialized.records[0].naturalKey)
    }
}
