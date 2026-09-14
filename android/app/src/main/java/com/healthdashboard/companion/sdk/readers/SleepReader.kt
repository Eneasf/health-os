package com.healthdashboard.companion.sdk.readers

import android.content.Context
import android.util.Log
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.NocturnalOxygenSample
import com.healthdashboard.companion.data.models.SleepSessionPayload
import com.healthdashboard.companion.data.models.SleepStagePayload
import com.healthdashboard.companion.data.models.SleepStageType
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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
import java.time.ZoneId
import java.time.ZoneOffset

class SleepReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.SLEEP
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.SLEEP.changedDataRequestBuilder
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
            Log.e("SleepReader", "readChanges error: ${e.message}", e)
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
                    val request = DataTypes.SLEEP.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    Log.w("SleepReader", "Chunk query error ($currentStart to $nextEnd): ${e.message}")
                }
                currentStart = nextEnd
            }
            records
        } catch (e: Exception) {
            Log.e("SleepReader", "readWindow error: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: return null
        val endInstant = point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val durationObj = point.getValue(DataType.SleepType.DURATION)
        val durationMinutes = if (durationObj != null) durationObj.toMinutes().toDouble() else java.time.Duration.between(startInstant, endInstant).toMinutes().toDouble()
        val sleepScore = point.getValue(DataType.SleepType.SLEEP_SCORE)
        val sessions = point.getValue(DataType.SleepType.SESSIONS) ?: emptyList()

        var deepMin = 0.0
        var remMin = 0.0
        var lightMin = 0.0
        var awakeMin = 0.0
        val stagePayloads = mutableListOf<SleepStagePayload>()

        for (session in sessions) {
            for (st in session.stages ?: emptyList()) {
                val durSec = java.time.Duration.between(st.startTime, st.endTime).seconds
                val durMin = durSec / 60.0
                val mappedStage = when (st.stage) {
                    DataType.SleepType.StageType.DEEP -> {
                        deepMin += durMin
                        SleepStageType.DEEP
                    }
                    DataType.SleepType.StageType.REM -> {
                        remMin += durMin
                        SleepStageType.REM
                    }
                    DataType.SleepType.StageType.LIGHT -> {
                        lightMin += durMin
                        SleepStageType.LIGHT
                    }
                    DataType.SleepType.StageType.AWAKE -> {
                        awakeMin += durMin
                        SleepStageType.AWAKE
                    }
                    else -> SleepStageType.LIGHT
                }
                stagePayloads.add(
                    SleepStagePayload(
                        startTime = st.startTime.toString(),
                        endTime = st.endTime.toString(),
                        stage = mappedStage,
                        durationSeconds = durSec
                    )
                )
            }
        }

        val totalSleepMin = deepMin + remMin + lightMin
        val totalSleepCalc = if (totalSleepMin > 0) totalSleepMin else (durationMinutes - awakeMin)
        val efficiency = if (durationMinutes > 0) ((totalSleepCalc / durationMinutes) * 100.0) else 100.0
        val wakeDate = endInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, wakeDate, startInstant.toString(), dataOrigin)
        val naturalKey = ReaderUtils.sleepNaturalKey(wakeDate, durationMinutes, totalSleepCalc, deepMin, remMin, dataOrigin)

        val sleepPayload = SleepSessionPayload(
            startTime = startInstant.toString(),
            endTime = endInstant.toString(),
            wakeDate = wakeDate,
            durationMinutes = durationMinutes,
            totalSleepMinutes = totalSleepCalc,
            sleepScore = sleepScore,
            deepSleepMinutes = deepMin,
            remSleepMinutes = remMin,
            lightSleepMinutes = lightMin,
            awakeMinutes = awakeMin,
            efficiencyPct = efficiency,
            stages = stagePayloads,
            spo2Samples = emptyList()
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: endInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(sleepPayload).jsonObject
        )
    }
}
