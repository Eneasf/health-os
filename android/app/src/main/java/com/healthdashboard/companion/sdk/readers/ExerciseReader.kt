package com.healthdashboard.companion.sdk.readers

import android.content.Context
import android.util.Log
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.ExerciseRecordPayload
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExerciseReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.EXERCISE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.EXERCISE.changedDataRequestBuilder
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
                            dataOrigin = "com.sec.android.app.shealth",
                            payload = JsonObject(emptyMap())
                        )
                    )
                }
            }
            ReadResult(records, response.pageToken)
        } catch (e: Exception) {
            Log.e("ExerciseReader", "readChanges error: ${e.message}", e)
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
                    val request = DataTypes.EXERCISE.readDataRequestBuilder
                        .setLocalTimeFilter(com.samsung.android.sdk.health.data.request.LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    Log.w("ExerciseReader", "Chunk query error ($currentStart to $nextEnd): ${e.message}")
                }
                currentStart = nextEnd
            }
            records
        } catch (e: Exception) {
            Log.e("ExerciseReader", "readWindow error: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: return null
        val endInstant = point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val predefinedType = point.getValue(DataType.ExerciseType.EXERCISE_TYPE)
        val customTitle = point.getValue(DataType.ExerciseType.CUSTOM_TITLE)
        val sessions = point.getValue(DataType.ExerciseType.SESSIONS) ?: emptyList()

        val firstSession = sessions.firstOrNull()
        val durationMin = firstSession?.duration?.toMinutes()?.toDouble()
            ?: java.time.Duration.between(startInstant, endInstant).toMinutes().toDouble()

        val calories = firstSession?.calories?.toDouble()
        val meanHr = firstSession?.meanHeartRate?.toFloat()
        val maxHr = firstSession?.maxHeartRate?.toFloat()
        val minHr = firstSession?.minHeartRate?.toFloat()
        val count = firstSession?.count
        val countType = firstSession?.countType?.name
        val meanPower = firstSession?.meanPower?.toFloat()
        val maxPower = firstSession?.maxPower?.toFloat()
        val meanCadence = firstSession?.meanCadence?.toFloat()
        val distance = firstSession?.distance?.toDouble()
        val autoDetected = firstSession?.autoDetected ?: false

        val exerciseTypeStr = predefinedType?.name ?: "OTHER"
        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "${exerciseTypeStr}_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.exerciseNaturalKey(date, exerciseTypeStr, durationMin, calories ?: 0.0, dataOrigin)

        val workoutPayload = ExerciseRecordPayload(
            exerciseType = exerciseTypeStr,
            customTitle = customTitle ?: exerciseTypeStr,
            startTime = startInstant.toString(),
            endTime = endInstant.toString(),
            durationMinutes = durationMin,
            caloriesKcal = calories,
            meanHeartRateBpm = meanHr,
            maxHeartRateBpm = maxHr,
            minHeartRateBpm = minHr,
            count = count,
            countType = countType,
            meanPowerWatts = meanPower,
            maxPowerWatts = maxPower,
            meanCadenceRpm = meanCadence,
            distanceMeters = distance,
            autoDetected = autoDetected
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: endInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(workoutPayload).jsonObject
        )
    }
}
