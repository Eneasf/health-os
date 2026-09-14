package com.healthdashboard.companion.sdk.readers

import android.content.Context
import android.util.Log
import com.healthdashboard.companion.data.models.BodyCompRecordPayload
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.UUID

import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.LocalDateTime
import java.time.ZoneOffset

class BodyCompositionReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.BODY_COMPOSITION
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.BODY_COMPOSITION.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = mutableListOf<HealthRecordPayload>()

            for (change in response.dataList) {
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { dp ->
                        mapDataPointToPayload(dp)?.let { records.add(it) }
                    }
                } else if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.DELETE && change.deleteDataUid != null) {
                    records.add(
                        HealthRecordPayload(
                            changeType = ChangeType.DELETE,
                            sdkType = dataType.key,
                            dataUid = change.deleteDataUid!!,
                            naturalKey = null,
                            lastModified = change.changeTime?.toString() ?: Instant.now().toString(),
                            dataOrigin = "com.withings.wiscale2",
                            payload = JsonObject(emptyMap())
                        )
                    )
                }
            }
            ReadResult(records, response.pageToken)
        } catch (e: Exception) {
            Log.e("BodyCompositionReader", "readChanges error: ${e.message}", e)
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) {
                LocalDateTime.parse(startTimeIso.take(19))
            } else {
                LocalDateTime.now().minusDays(30)
            }
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) {
                LocalDateTime.parse(endTimeIso.take(19))
            } else {
                LocalDateTime.now()
            }

            val records = mutableListOf<HealthRecordPayload>()
            var currentStart = startLdt
            while (currentStart.isBefore(endLdt)) {
                val nextEnd = if (currentStart.plusDays(30).isBefore(endLdt)) currentStart.plusDays(30) else endLdt
                try {
                    val request = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    Log.w("BodyCompositionReader", "Chunk query error ($currentStart to $nextEnd): ${e.message}")
                }
                currentStart = nextEnd
            }
            records
        } catch (e: Exception) {
            Log.e("BodyCompositionReader", "readWindow error: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val weight = point.getValue(DataType.BodyCompositionType.WEIGHT)?.toDouble()
        val height = point.getValue(DataType.BodyCompositionType.HEIGHT)?.toDouble()
        val bodyFat = point.getValue(DataType.BodyCompositionType.BODY_FAT)?.toDouble()
        val fatMass = point.getValue(DataType.BodyCompositionType.BODY_FAT_MASS)?.toDouble()
        val ffm = point.getValue(DataType.BodyCompositionType.FAT_FREE_MASS)?.toDouble()
        val smm = point.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE)?.toDouble()
            ?: point.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS)?.toDouble()
        val muscle = point.getValue(DataType.BodyCompositionType.MUSCLE_MASS)?.toDouble()
        val tbw = point.getValue(DataType.BodyCompositionType.TOTAL_BODY_WATER)?.toDouble()
        val bmr = point.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE)?.toDouble()
        val bmi = point.getValue(DataType.BodyCompositionType.BODY_MASS_INDEX)?.toDouble()

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.withings.wiscale2"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "bodycomp_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.bodyCompNaturalKey(date, weight ?: 0.0, bodyFat ?: 0.0, dataOrigin)

        val bodyPayload = BodyCompRecordPayload(
            timestamp = startInstant.toString(),
            weightKg = weight ?: 75.0,
            heightCm = height,
            bodyFatPct = bodyFat,
            bodyFatMassKg = fatMass,
            fatFreeMassKg = ffm,
            skeletalMuscleMassKg = smm,
            muscleMassKg = muscle,
            totalBodyWaterL = tbw,
            bmrKcal = bmr,
            bmi = bmi
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(bodyPayload).jsonObject
        )
    }
}
