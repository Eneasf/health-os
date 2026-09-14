package com.healthdashboard.companion.sdk.readers

import android.content.Context
import android.util.Log
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.HeartRateBin
import com.healthdashboard.companion.data.models.HeartRateRecordPayload
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

class HeartRateReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.HEART_RATE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.HEART_RATE.changedDataRequestBuilder
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
            Log.e("HeartRateReader", "readChanges error: ${e.message}", e)
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
                val nextEnd = if (currentStart.plusDays(7).isBefore(endLdt)) currentStart.plusDays(7) else endLdt
                try {
                    val request = DataTypes.HEART_RATE.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    Log.w("HeartRateReader", "Chunk query error ($currentStart to $nextEnd): ${e.message}")
                }
                currentStart = nextEnd
            }
            records
        } catch (e: Exception) {
            Log.e("HeartRateReader", "readWindow error: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: return null
        val endInstant = point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val meanBpm = point.getValue(DataType.HeartRateType.HEART_RATE)
        val minBpm = point.getValue(DataType.HeartRateType.MIN_HEART_RATE)
        val maxBpm = point.getValue(DataType.HeartRateType.MAX_HEART_RATE)
        val series = point.getValue(DataType.HeartRateType.SERIES_DATA) ?: emptyList()

        val seriesData = series.map { entry ->
            HeartRateBin(
                startTime = entry.startTime.toString(),
                endTime = entry.endTime.toString(),
                bpm = entry.heartRate,
                minBpm = entry.min,
                maxBpm = entry.max
            )
        }

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, startInstant.toString(), dataOrigin)
        val naturalKey = ReaderUtils.heartRateNaturalKey(date, startInstant.toString(), meanBpm ?: 0f, dataOrigin)

        val hrPayload = HeartRateRecordPayload(
            startTime = startInstant.toString(),
            endTime = endInstant.toString(),
            date = date,
            meanBpm = meanBpm ?: 60.0f,
            minBpm = minBpm,
            maxBpm = maxBpm,
            seriesData = seriesData
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: endInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(hrPayload).jsonObject
        )
    }
}
