package com.healthdashboard.companion

import com.healthdashboard.companion.data.models.ActiveProtocol
import com.healthdashboard.companion.data.models.ActiveProtocolResponse
import com.healthdashboard.companion.data.models.DoseLogPayload
import com.healthdashboard.companion.data.models.DoseLogResponse
import com.healthdashboard.companion.data.models.ProtocolCompound
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class DoseAttestationTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testActiveProtocolSerializationAndDeserialization() {
        val compound1 = ProtocolCompound(
            id = "creatine",
            name = "Creatine Monohydrate",
            category = "supplement",
            dose = "5 g",
            route = "oral_powder",
            cadence = "daily",
            frequencyHours = 24,
            timing = "Morning",
            halfLifeHours = 24.0,
            saturationRequirement = "Daily consistent administration with water.",
            clinicalTarget = "Intramuscular phosphocreatine saturation."
        )

        val compound2 = ProtocolCompound(
            id = "omega3",
            name = "Omega-3 EPA/DHA",
            category = "supplement",
            dose = "2000 mg",
            cadence = "daily",
            timing = "With Meals"
        )

        val protocol = ActiveProtocol(
            id = "protocol_03_hypertrophy",
            version = 1,
            name = "Protocol 03: Lean Mass Accretion",
            category = "hypertrophy",
            status = "active",
            effectiveStart = "2026-08-24",
            effectiveEnd = "2026-12-14",
            intentSummary = "16-Week clinical protocol: sports nutrition and resistance periodization.",
            compounds = listOf(compound1, compound2)
        )

        val response = ActiveProtocolResponse(activeProtocol = protocol)
        val jsonStr = json.encodeToString(response)

        assertTrue("JSON must contain active_protocol key", jsonStr.contains("\"active_protocol\""))
        assertTrue("JSON must contain creatine", jsonStr.contains("creatine"))
        assertTrue("JSON must contain daily cadence", jsonStr.contains("daily"))

        val decoded = json.decodeFromString<ActiveProtocolResponse>(jsonStr)
        assertNotNull(decoded.activeProtocol)
        assertEquals("protocol_03_hypertrophy", decoded.activeProtocol!!.id)
        assertEquals(2, decoded.activeProtocol!!.compounds.size)
        assertEquals("creatine", decoded.activeProtocol!!.compounds[0].id)
        assertEquals("Omega-3 EPA/DHA", decoded.activeProtocol!!.compounds[1].name)
    }

    @Test
    fun testDoseLogPayloadSerializationContract() {
        val payload = DoseLogPayload(
            compoundId = "creatine",
            datetime = "2026-09-13T21:30:00Z",
            divergence = "dose_adjusted",
            precision = "exact",
            confidence = "confirmed",
            notes = "Taken with post-workout meal",
            source = "android_companion"
        )

        val jsonStr = json.encodeToString(payload)

        assertTrue(jsonStr.contains("\"compound_id\": \"creatine\""))
        assertTrue(jsonStr.contains("\"datetime\": \"2026-09-13T21:30:00Z\""))
        assertTrue(jsonStr.contains("\"divergence\": \"dose_adjusted\""))
        assertTrue(jsonStr.contains("\"precision\": \"exact\""))
        assertTrue(jsonStr.contains("\"confidence\": \"confirmed\""))
        assertTrue(jsonStr.contains("\"source\": \"android_companion\""))

        val decoded = json.decodeFromString<DoseLogPayload>(jsonStr)
        assertEquals("creatine", decoded.compoundId)
        assertEquals("dose_adjusted", decoded.divergence)
        assertEquals("exact", decoded.precision)
        assertEquals("confirmed", decoded.confidence)
        assertEquals("Taken with post-workout meal", decoded.notes)
    }

    @Test
    fun testDoseLogResponseDeserialization() {
        val jsonSuccess = """
            {
                "status": "success",
                "event_id": "a1b2c3d4e5f6",
                "message": "Dose recorded cleanly"
            }
        """.trimIndent()

        val resp = json.decodeFromString<DoseLogResponse>(jsonSuccess)
        assertEquals("success", resp.status)
        assertEquals("a1b2c3d4e5f6", resp.eventId)

        val jsonError = """
            {
                "status": "error",
                "error": "Missing compound_id"
            }
        """.trimIndent()

        val errResp = json.decodeFromString<DoseLogResponse>(jsonError)
        assertEquals("error", errResp.status)
        assertEquals("Missing compound_id", errResp.error)
    }

    @Test
    fun testOfflineQueueAtomicitySimulation() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_dose_queue_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val payload = DoseLogPayload(
                compoundId = "omega3_epd_dha",
                datetime = "2026-09-13T09:00:00Z",
                divergence = "adherent"
            )

            val baseName = "dose_${System.currentTimeMillis()}_omega3_epd_dha"
            val tmpFile = File(tempDir, "$baseName.json.tmp")
            val finalFile = File(tempDir, "$baseName.json")

            val jsonStr = json.encodeToString(payload)
            FileOutputStream(tmpFile).use { fos ->
                fos.write(jsonStr.toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            assertTrue("Temp file must exist prior to rename", tmpFile.exists())
            val renamed = tmpFile.renameTo(finalFile)
            assertTrue("Rename to final JSON must succeed", renamed)
            assertTrue("Final file must exist", finalFile.exists())

            // Validate content
            val readPayload = json.decodeFromString<DoseLogPayload>(finalFile.readText(Charsets.UTF_8))
            assertEquals("omega3_epd_dha", readPayload.compoundId)
            assertEquals("adherent", readPayload.divergence)

            // Simulate removal upon sync
            finalFile.delete()
            assertTrue("File must be deleted after sync", !finalFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
