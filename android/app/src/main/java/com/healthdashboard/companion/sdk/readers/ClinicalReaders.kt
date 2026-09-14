package com.healthdashboard.companion.sdk.readers

import android.content.Context
import com.healthdashboard.companion.data.models.BloodGlucoseRecordPayload
import com.healthdashboard.companion.data.models.BloodPressureRecordPayload
import com.healthdashboard.companion.data.models.BodyTemperaturePayload
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class BloodPressureReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.BLOOD_PRESSURE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.BLOOD_PRESSURE.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val records = mutableListOf<HealthRecordPayload>()
            var currentStart = startLdt
            while (currentStart.isBefore(endLdt)) {
                val nextEnd = if (currentStart.plusDays(30).isBefore(endLdt)) currentStart.plusDays(30) else endLdt
                try {
                    val request = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    // Ignore empty or restricted chunks
                }
                currentStart = nextEnd
            }
            records
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val systolic = point.getValue(DataType.BloodPressureType.SYSTOLIC)
        val diastolic = point.getValue(DataType.BloodPressureType.DIASTOLIC)
        val mean = point.getValue(DataType.BloodPressureType.MEAN)
        val pulse = point.getValue(DataType.BloodPressureType.PULSE_RATE)

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "bp_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "bp_${systolic}_${diastolic}", dataOrigin)

        val payload = BloodPressureRecordPayload(
            timestamp = startInstant.toString(),
            systolicMmHg = systolic ?: 120.0f,
            diastolicMmHg = diastolic ?: 80.0f,
            meanMmHg = mean,
            pulseRateBpm = pulse?.toFloat(),
            medicationTaken = false
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class BloodGlucoseReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.BLOOD_GLUCOSE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.BLOOD_GLUCOSE.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val records = mutableListOf<HealthRecordPayload>()
            var currentStart = startLdt
            while (currentStart.isBefore(endLdt)) {
                val nextEnd = if (currentStart.plusDays(30).isBefore(endLdt)) currentStart.plusDays(30) else endLdt
                try {
                    val request = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    // Ignore empty or restricted chunks
                }
                currentStart = nextEnd
            }
            records
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val glucose = point.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
        val mealStatus = point.getValue(DataType.BloodGlucoseType.MEAL_STATUS)?.name
        val measurementType = point.getValue(DataType.BloodGlucoseType.MEASUREMENT_TYPE)?.name

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "glucose_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "glucose_${glucose}", dataOrigin)

        val payload = BloodGlucoseRecordPayload(
            timestamp = startInstant.toString(),
            glucoseLevelMgDl = glucose ?: 95.0f,
            measurementType = measurementType ?: "GENERAL",
            mealStatus = mealStatus ?: "GENERAL",
            medicationTaken = false
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class BodyTemperatureReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.BODY_TEMPERATURE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.BODY_TEMPERATURE.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val records = mutableListOf<HealthRecordPayload>()
            var currentStart = startLdt
            while (currentStart.isBefore(endLdt)) {
                val nextEnd = if (currentStart.plusDays(30).isBefore(endLdt)) currentStart.plusDays(30) else endLdt
                try {
                    val request = DataTypes.BODY_TEMPERATURE.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    // Ignore empty or restricted chunks
                }
                currentStart = nextEnd
            }
            records
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val temp = point.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE)

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "temp_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "temp_${temp}", dataOrigin)

        val payload = BodyTemperaturePayload(
            timestamp = startInstant.toString(),
            temperatureCelsius = temp ?: 36.6f
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}
